# Plano Técnico — Cadastro de Contas

**Status**: quality gate documental concluído; saga de pré-ativação implementada
**Escopo inicial implementável**: aceite durável, storage, bootstrap fundador, baseline ACL e plano padrão

## Objetivo do primeiro slice

Entregar a autoridade global de conta/tenant e aceitar uma criação idempotente sem anunciar conta ativa. O slice
termina com conta `CREATING`, protocolo consultável, auditoria e outbox pendente. Ativação somente entra em outro slice
depois que storage, membership, ACL e plano publicarem adapters concretos.

## Componentes

```text
UI/API adapter
  -> AccountCreationFacade
      -> IdentityStatePort / garantia / antiabuso
      -> AccountCreationService
          -> account + tenant + intent + audit + outbox (uma transação global)
  -> AccountCreationStatusFacade

AccountProvisioningOutboxDispatcher
  -> reclama lease FIFO sob liderança de manutenção comprovada
  -> reconstrói conta + tenant + intenção pelo global, sem confiar no JSON da outbox
  -> TenantProvisioningRequestPort
  -> confirma somente o checkpoint STORAGE como PENDING/PROCESSING/FAILED

AccountCreationSagaMaintenanceScheduler
  -> prova a liderança antes de cada avanço transacional limitado
  -> reclama uma conta CREATING com a primeira etapa elegível sob FOR UPDATE SKIP LOCKED
  -> TenantStorageReadinessPort confirma STORAGE somente quando READY
  -> FoundingMembershipBootstrapPort -> TenantAccessBootstrapPort -> DefaultPlanAssignmentPort
  -> registra checkpoint COMPLETED/FAILED ou nova tentativa, sem ativar conta/tenant
```

## Fronteiras

- `api.module.account`: facades, comandos, resultados e enums seguros.
- `backend.module.account`: entidades, repositories, serviços e adapters de infraestrutura.
- `backend.module.identity`: continua autoridade do estado global e garantia.
- `backend.module.access`: decide manutenção posterior; não persiste conta.
- `tenant-storage-provisioning`, `account-membership` e `plans-entitlements`: dependências por portas, sem imports de
  entities/repositories.

## Fluxo de aceite

1. Adapter deriva a identidade autenticada, referência de sessão e origem confiável, sem aceitar esses valores do formulário.
2. A fachada revalida identidade/sessão ativa e garantia recente para a operação catalogada `create-account`.
3. Calcula o replay antes da prova: mesmo payload retorna o protocolo; payload divergente retorna conflito; ambos não consomem cota nem token Turnstile.
4. Normaliza nome; valida ISO 4217 suportada e `ZoneId` IANA.
5. Avalia o limiar de origem e valida Turnstile no servidor quando obrigatório. Token inválido ou provider indisponível não cria estado da conta.
6. Sob a transação, reavalia o limiar e reserva a origem em `security_originWindow` com `ACCOUNT_CREATION`; assim uma corrida que tornou a prova obrigatória é negada e uma falha posterior não consome cota.
7. Ao atingir o limite rígido, registra `blockedUntil` pela duração configurada, independente da janela de contagem.
8. Cria tenant, conta `CREATING`, intenção, auditoria e evento outbox.
9. Depois do commit, dispatcher pode antecipar o processamento; a linha durável continua sendo a autoridade.

## Transações e falhas

- Nenhum registro é criado antes da prova obrigatória e das validações.
- Conta, tenant, intenção, auditoria e outbox confirmam ou revertem juntos.
- Falha de publicação não remove a outbox; backoff e retomada são persistidos. O despachante
  reclama a linha em transação curta, chama o port fora do lock e relê o mesmo lease antes de
  confirmar o resultado. Resposta perdida é repetida com o mesmo protocolo.
- A aceitação de `TenantProvisioningRequestPort` publica a outbox e deixa `STORAGE` em
  `PROCESSING`, com referência opaca da operação. Não prova schema pronto, não marca checkpoint
  como `COMPLETED` e não altera conta `CREATING` nem tenant `RESERVED`.
- `REJECTED` encerra a outbox e marca apenas o checkpoint `STORAGE` como `FAILED`; `UNAVAILABLE`,
  referência inválida ou falha de chamada devolvem ambos à nova tentativa exponencial limitada.
- Depois da aceitação de storage, somente o coordenador de saga observa a prontidão pelo
  `TenantStorageReadinessPort`. `STORAGE` somente se torna `COMPLETED` quando o snapshot é
  `READY`; espera e indisponibilidade mantêm a etapa em processamento com backoff, enquanto
  `ATTENTION`, inatividade ou tenant desconhecido são falhas terminais seguras.
- Os checkpoints posteriores são invocados estritamente na ordem `FOUNDING_MEMBERSHIP`,
  `ACCESS_BOOTSTRAP` e `DEFAULT_PLAN`. Cada porta deve devolver referência opaca válida para
  concluir sua etapa; rejeição é terminal e indisponibilidade volta a `PENDING` com backoff.
- O coordenador possui lock pessimista por conta e `SKIP LOCKED` na seleção, logo instâncias de
  manutenção não avançam a mesma saga concorrentemente. A promoção de conta e tenant permanece
  deliberadamente fora desse coordenador e exige a validação final da tarefa 4.4.
- Payload conflitante nunca revela outra conta além da associada ao próprio criador.
- Indisponibilidade interna resulta em status seguro e nenhum sucesso artificial.

## Segurança e privacidade

- Tokens, prova de reautenticação e detalhes internos de storage não entram em entidades de conta, DTOs de status ou logs. A origem binária canônica existe somente no contador global temporário `security_originWindow`, submetida à retenção de origem; ela não é copiada para a auditoria de conta.
- Auditoria registra ator, correlação, resultado, conta/tenant e campos alterados minimizados.
- UUIDs públicos evitam enumeração por IDs sequenciais.
- Consultas sempre restringem por ator autorizado ou contexto tenant explicitamente validado.

## Observabilidade

Métricas separadas: aceitação, idempotent replay, conflito, rejeição antiabuso, outbox pendente, tentativas, idade do
evento mais antigo e tempo até cada etapa. Correlação usa protocolo público; payload e prova permanecem redigidos.
O payload da outbox é deliberadamente vazio e não autoritativo; o agregado interno e a intenção
persistida são a única fonte para a reconstrução do pedido.

## Entrega incremental

1. Contratos e configuração.
2. Schema global e persistência básica.
3. Serviço idempotente de aceite e consulta de status.
4. Outbox e porta de provisionamento.
5. Integração membership + bootstrap ACL.
6. Integração plano padrão.
7. Ativação somente depois dos quatro checkpoints completos.
8. Manutenção/lifecycle autorizados.
9. Interface e validação E2E.

## Constitution check

- Isolamento: tenant é identidade explícita desde o aceite.
- Autorização: negação por padrão; papel fundador não concede chaves.
- Integridade: outbox, idempotência, histórico e migrações RFW.
- RFW: recursos públicos reutilizados; submódulo intocado.
- Qualidade: schema/código somente após checklists documentais.
