# Plano Técnico — Provisionamento do Armazenamento de Tenant

**Feature**: `tenant-storage-provisioning` | **Data**: 2026-08-29 | **Spec**: [spec.md](./spec.md)

## Resumo

Implementar o plano de dados físico de cada tenant como processo global durável: o cadastro aceita a conta, a fila
reserva uma identidade física interna, cria e inicializa seu schema, valida a versão e publica somente a prontidão
segura. Atualizações globais continuam no gate de startup RFW; atualizações de tenant usam o orquestrador explícito da
RFW e isolam falhas por tenant.

## Contexto Técnico

**Linguagem/versão**: Java 25

**Dependências principais**: Spring Boot 4.0.7, Spring Data JPA, HikariCP, MySQL Connector/J, Vaadin 25.0.2 e RFW
2.0.0.

**Armazenamento**: MySQL 9; global `rinos_global` e um schema físico `rinos_<identificador>` por tenant.

**Testes**: JUnit 5, Mockito, Spring Boot Test e MySQL 9 temporário já configurado no projeto.

**Plataforma-alvo**: JAR Linux atrás de Apache/Cloudflare, inicialmente uma instância e compatível com múltiplas.

**Metas**: 95% dos provisionamentos vazios em até cinco minutos; uma operação estrutural por tenant; uma migration de
tenant simultânea por padrão.

**Restrições**: sem broker/Redis, sem SQL por entrada do usuário, sem backup/restauração na aplicação, sem rollback de
migration, sem segredo fora de `application.properties`, falha fechada para indisponibilidade e incompatibilidade.

**Escopo**: provisionamento, init, migrations, retomada, quarentena, consulta segura e desativação controlada. A
ativação de conta continua coordenada por `account-registration`; grupos, planos e contexto de usuário não são
reimplementados aqui.

## Arquitetura de Componentes

```text
account outbox dispatcher
  -> TenantProvisioningRequestPort
      -> TenantStorageProvisioningFacade
          -> registry + structural operation queue (global transaction)
              -> elected maintenance worker
                  -> operation lease / tenant guard
                      -> TenantSchemaInitializer (CREATE DATABASE + db/tenant/init)
                      -> RFW DatabaseUpdateOrchestratorService (db/tenant/update)
                      -> version/readiness validation
                          -> TenantStorageReadinessPort + safe event
                              -> account saga / tenant context gate
```

- `api.module.storage`: VOs, enums, `TenantStorageReadinessPort` e facades de consulta.
- `backend.module.storage`: registry, operações, leases, inicializador, factory de datasource, worker, transições,
  adaptação do RFW, auditoria e adapters dos ports de account.
- `backend.module.account`: continua proprietário de conta, outbox e checkpoints; consome somente ports API.
- `backend.module.platform`: fornece a eleição de manutenção; não conhece schema, entidade ou SQL de tenant.
- `ui.module.storage` e `ui.module.platform`: compõem status público/admin somente por facades API.

## Fluxos Estruturais

### Provisionamento

1. O dispatcher chama o port depois do commit da intenção, conta e outbox globais.
2. Em transação global, o adapter confere conta/tenant/protocolo, cria ou relê registry e operação idempotente.
3. A instância eleita reclama a próxima operação FIFO com lease; lock e constraint impedem concorrência por tenant.
4. A etapa reserva `physicalIdentifier`, deriva e valida o nome, cria o schema, executa init, verifica a view de
   versão e registra evidências mínimas.
5. Somente depois de `READY` e versão exata o port de prontidão muda; account ainda aguarda seus outros três
   checkpoints.

### Migration no deploy

1. O auto-updater RFW aplica e valida exclusivamente `db/global/update` antes de o contexto Spring estar pronto.
2. Depois do startup, a instância eleita identifica registries com versão diferente, enfileira migrations em ordem e
   coloca cada tenant em `MIGRATING` antes de abrir sua conexão funcional.
3. Cada operação chama o updater explícito RFW com somente `db/tenant/update` e o `DataSource` daquele schema.
4. Sucesso atualiza `observedVersion` e retorna a `READY`; falha não é repetida e coloca somente aquele tenant em
   `QUARANTINED`.

### Retomada e desativação

Após queda, lease vencido permite claim novo; o worker observa primeiro schema, versão e evidências persistidas.
Provisionamento transitório retoma até o limite; estados definitivos exigem infraestrutura. Desativação é uma operação
global autorizada e idempotente, posterior às políticas de retenção: ela impede acesso e preserva o identificador; não
há comando de backup, restauração, rollback ou repetição de migration na UI.

### Máquina de estados e projeções

A máquina de estados é validada antes de persistir cada transição e a transição/auditoria serão gravadas pela mesma
transação global nas tarefas da fila. Não há repetição de estado, regressão, salto ou promoção manual para `READY`.

| Estado atual | Próximos estados permitidos |
|---|---|
| `REQUESTED` | `PROVISIONING`, `FAILED`, `DEACTIVATING` |
| `PROVISIONING` | `INITIALIZING`, `FAILED`, `QUARANTINED`, `DEACTIVATING` |
| `INITIALIZING` | `MIGRATING`, `FAILED`, `QUARANTINED`, `DEACTIVATING` |
| `MIGRATING` | `READY`, `QUARANTINED`, `DEACTIVATING` |
| `READY` | `MIGRATING`, `QUARANTINED`, `DEACTIVATING` |
| `FAILED` | `QUARANTINED`, `DEACTIVATING` |
| `QUARANTINED` | `DEACTIVATING` |
| `DEACTIVATING` | `INACTIVE` |
| `INACTIVE` | nenhum |

Somente a origem sistêmica pode concluir `MIGRATING` em `READY`, depois de comprovar a versão exata. A eventual
retomada por lease ou repetição controlada seguirá a fila e não cria atalho de estado. Operações e etapas possuem
máquinas independentes: operação segue `QUEUED → CLAIMED → RUNNING` e termina, aguarda retry ou é cancelada; etapa
segue `PENDING → RUNNING → COMPLETED|FAILED`.

O gate `TenantStorageReadinessPort` lê apenas o catálogo global e responde falhado fechado. Ele retorna `READY` só
para o registro estrutural pronto com versão observada igual à esperada; não aceita identidade, não autoriza e não abre
o datasource funcional. A projeção do criador reduz a resposta interna aos quatro estados `WAITING`, `PREPARING`,
`READY` e `ATTENTION`, sem motivo técnico, versão, schema, host, URL ou credencial.

## Transações, Concorrência e Falhas

- Reserva de registry/operação/etapas, transições e auditoria usam transações do global e optimistic locking.
- `CREATE DATABASE`, DDL do init e updates não fazem parte de transação distribuída. Cada efeito recebe etapa
  durável antes/depois e é observado antes de nova tentativa.
- A fila mantém `leaseOwner`/`leaseUntil`; uma instância não confirma etapa se perdeu seu claim.
- O updater RFW acrescenta lock nomeado no `DataSource` físico. Falha ao ler registry, versão, catálogo ou lock bloqueia
  o tenant; nenhum cache amplia prontidão.
- O worker não promove estado por timeout ou por alteração manual. Ele registra um alerta seguro quando não há
  progresso além do limite configurado.

## Segurança e Privacidade

- O nome físico deriva somente de valor interno allowlisted; jamais de rota, formulário, evento ou payload.
- O `DataSource` de tenant usa a única credencial declarada no properties; logs e VOs censuram senha, URL, host, SQL e
  identificador físico.
- Ações automáticas usam origem sistêmica explícita. Consulta, reconciliação e desativação exigem contexto global,
  chave canônica e, quando sensível, reautenticação e 2FA.
- Prontidão não é autorização: identidade, associação, regra/bloqueio e entitlement continuam obrigatórios.

## Observabilidade

Métricas: fila por estado/tipo, idade da operação mais antiga, duração por etapa, tentativas, quarantines, tenants por
versão e falhas por classe segura. Auditoria registra protocolo, tenant lógico, tipo, etapa, ator/origem, transição e
resultado. A observabilidade não recebe schema, SQL, URL, credencial ou conteúdo de tenant.

## Arquitetura de Superfícies

**Catálogo**: [Interaction Surface Architecture](../../architecture/interaction-surfaces.md)

**Interface Design**: REQUIRED. A cobertura é parcial em `SURF-WEB-RINOS`: criador acompanha os quatro estados
públicos via cadastro de conta; administrador global consulta inventário/histórico seguro. Os detalhes de telas,
teclado, reflow, estados, contratos e avaliação do showroom RFW entram em `interface-spec.md`.

| Surface | Cobertura | Tecnologia | Módulo previsto | Observação |
|---|---|---|---|---|
| `SURF-WEB-RINOS` | PARTIAL | Vaadin server-side e APIs públicas RFW | `ui.module.account` e `ui.module.storage` | sem comandos estruturais, backup ou migration |

## Constituição

| Princípio | Status | Aplicação |
|---|---|---|
| I. Isolamento multi-tenant | PASS | schema exclusivo, registry global e gate antes do uso |
| II. Autorização explícita | PASS | prontidão não concede acesso; consulta/ação admin exige chaves globais |
| III. Integridade e rastreabilidade | PASS | etapas, leases, versões, auditoria e forward-only persistidos |
| IV. Arquitetura modular/RFW | PASS | RFW atualiza DataSource explícito; UI futura usa RFW, sem mudança no submódulo |
| V. Qualidade antes de escopo | PASS | tarefas incluirão MySQL real, concorrência, restart, isolamento e acessibilidade |

## Estrutura de Projeto

```text
docs/specs/tenant-storage-provisioning/
├── spec.md
├── research.md
├── plan.md
├── data-model.md
├── quickstart.md
├── contracts/tenant-storage-provisioning.md
└── interface-spec.md                 # etapa seguinte

src/main/java/br/com/rinos/app/
├── api/module/storage/
├── backend/module/storage/{entity,repository,service,facade,config}/
├── backend/module/account/           # somente adapters/consumidores de ports
└── ui/module/{account,storage}/

src/main/resources/db/
├── global/{init,update}/              # registry/fila/auditoria desta feature
└── tenant/{init,update}/              # estruturas funcionais de cada tenant
```

## Convenções de Borda

| Camada | Convenção | Validação | Fonte de verdade |
|---|---|---|---|
| Global DB | `camelCase`, tabelas `storage_<entity>` | DDL, FK, checks e testes MySQL | `db/global` e entities |
| Tenant DB | catálogo separado, `databaseVersion` próprio | init/update e RFW updater | `db/tenant` |
| API Java | records/VOs e enums tipados | construtores e testes | `api.module.storage` |
| Account ↔ storage | ports e outcomes idempotentes | testes de adapter e saga | contratos desta feature |
| UI ↔ backend | facade/VO, nunca entity/repository | testes de integração/UI | `api.module.storage` |

O mapper DB ↔ API fica em `backend.module.storage`; a UI não conhece `DataSource`, schema ou entidades. Nenhum
payload REST é exposto nesta fase.

## Próxima Etapa

Interface Design, checklists, backlog e análise cruzada foram concluídos em 2026-08-29. A implementação deve começar
pela tarefa crítica 1.1 de [tasks.md](./tasks.md), somente em MySQL controlado; schema, código ou alterações no Turing
continuam vedados antes dessa execução e das autorizações operacionais aplicáveis.
podem iniciar.
