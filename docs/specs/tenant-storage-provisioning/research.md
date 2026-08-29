# Pesquisa — Provisionamento do Armazenamento de Tenant

Este documento fecha as decisões técnicas necessárias antes da implementação. Ele não autoriza alteração de schema,
criação de banco ou operação no Turing.

## Decisão 1: identidade física independente e schema derivado

**Decisão**: cada tenant recebe um `physicalIdentifier` aleatório, imutável e independente do UUID público e do nome
da conta. O identificador é uma sequência hexadecimal minúscula de 32 caracteres, gerada pelo sistema. O schema é
derivado exclusivamente como `rinos_<physicalIdentifier>`; seu valor não entra em contratos funcionais, logs comuns
nem telas.

**Justificativa**: a convenção preserva o prefixo `rinos_`, cabe folgadamente no limite de nomes do MySQL e separa a
identidade funcional do tenant da sua topologia atual. A derivação determinística permite reconstruir uma conexão a
partir do registro global sem aceitar qualquer fragmento vindo da UI.

**Alternativas consideradas**: usar o nome da conta foi rejeitado por colisões, alterações e injeção. Derivar do UUID
público do tenant foi rejeitado para manter independência explícita entre identidade externa e identificador físico.
Armazenar somente o nome completo do schema foi rejeitado porque o identificador físico é a unidade estável que permite
evoluir a convenção de localização futuramente.

## Decisão 2: fonte única de credenciais e criação física

**Decisão**: o provisionador reutiliza exclusivamente as credenciais já declaradas em `spring.datasource.*`; um
`TenantDataSourceFactory` deriva a URL do tenant a partir da URL global, preservando host, porta e parâmetros e
trocando apenas o catalog por um schema previamente validado. Não haverá segunda senha, URL administrativa ou
variável de ambiente para tenants. A criação usa `CREATE DATABASE` com charset `utf8mb4` e collation
`utf8mb4_unicode_ci` sobre a conexão global configurada.

**Justificativa**: configurações têm origem exclusiva no Rinos. Duplicar credenciais em propriedades específicas de
tenant criaria derivações independentes e risco operacional. O MySQL requer privilégio `CREATE` sobre o database para
`CREATE DATABASE`; o usuário operacional deve receber somente os privilégios DDL/DML necessários sobre o padrão
literal `rinos\_%`, sem `GRANT OPTION`, privilégios administrativos ou acesso a schemas alheios.

**Alternativas consideradas**: usar `root`, uma credencial embutida, uma URL administrativa em variável de ambiente ou
criar um usuário por tenant foram rejeitados por ampliarem segredo, superfície administrativa e manutenção. Criar o
diretório do schema no sistema de arquivos foi rejeitado: o MySQL não o suporta como mecanismo de criação.

## Decisão 3: init próprio do Rinos e updates explícitos pelo RFW

**Decisão**: `TenantSchemaInitializer` do Rinos cria um schema novo e executa, em ordem lexical validada, somente
`classpath:db/tenant/init/`. Depois valida a view `databaseVersion`. Para evolução de um schema existente, o worker
injeta `DatabaseUpdateOrchestratorService` da RFW e chama `updateDatabase` com o `DataSource` daquele tenant,
`classpath:db/tenant/update/` e o timeout de lock configurado.

**Justificativa**: a RFW documenta que não cria bancos nem executa init; seu orquestrador explícito mantém descoberta,
validação de catálogo, versão e lock nomeado dentro de um único `DataSource`. Assim os catálogos global e tenant nunca
se misturam. O init produz diretamente a versão vigente e as updates continuam progressivas e verificáveis.

**Alternativas consideradas**: executar init pelo atualizador RFW, usar `USE <schema>` dentro de script ou combinar
locations global e tenant foram rejeitados pelo contrato da RFW e pela arquitetura de scripts do Rinos.

## Decisão 4: fila global durável, manutenção eleita e exclusividade por tenant

**Decisão**: operações estruturais são persistidas no global em ordem de aceitação. A instância que detém o lease de
manutenção do Rinos pesquisa a fila; cada operação é reclamada com lease próprio e cada tenant possui no máximo uma
operação estrutural ativa, protegido por constraint e lock transacional. O lock nomeado da RFW serializa também o
schema físico durante atualização. O padrão é uma execução simultânea, configurável por properties fixas.

**Justificativa**: a eleição já existe para tarefas de manutenção e evita concorrência desnecessária no estágio atual.
O lease da operação e as constraints persistidas preservam recuperação após queda e protegem uma futura topologia com
múltiplas instâncias; nenhuma corretude depende do scheduler ou de memória local.

**Alternativas consideradas**: fila apenas em memória, scheduler por instância sem lease e thread da sessão criadora
foram rejeitados por perderem processos em reinício ou duplicarem efeitos. Redis, broker externo e worker separado não
fazem parte da infraestrutura inicial.

## Decisão 5: etapas idempotentes e classificação de falhas

**Decisão**: a operação avança em etapas persistidas `RESERVE`, `CREATE_SCHEMA`, `INITIALIZE`, `VALIDATE_VERSION`,
`MARK_READY`, `MIGRATE`, `DEACTIVATE` e `RECONCILE`, conforme o tipo. Antes de repetir cada etapa, o worker observa o
estado efetivo: existência do schema, versão, registro e evidência anterior. Somente falhas classificadas como
transitórias durante provisionamento recebem até três tentativas por padrão; falha de migration, integridade ou
catálogo é definitiva para a aplicação e deixa o tenant em quarentena.

**Justificativa**: DDL do MySQL não participa de uma transação distribuída com o global. Persistir a evidência antes e
depois de cada efeito permite retomar sem fingir rollback, enquanto a classificação impede repetição insegura de uma
migration parcialmente aplicada.

**Alternativas consideradas**: compensar com `DROP DATABASE`, repetir qualquer falha e marcar estado pronto
manualmente foram rejeitados por risco de perda, ocultação de inconsistência e violação da política forward-only.

## Decisão 6: contratos públicos seguros e interface composta

**Decisão**: a feature publica facades/ports Java, não REST público. O criador vê somente o resumo público
`WAITING`, `PREPARING`, `READY` ou `ATTENTION` por meio da fachada de cadastro de conta. Administradores globais
autorizados consultam inventário e histórico seguro; a composição de telas é Vaadin com componentes públicos RFW,
sem expor schema, host, script, comando, credencial ou stack trace.

**Justificativa**: a única superfície humana do MVP é `SURF-WEB-RINOS`; o cadastro já possui protocolo público e o
RFW oferece shell, filtros, grids, banner, toast, dialog e reautenticação. O domínio de storage permanece backend e
continua testável sem UI.

**Alternativas consideradas**: endpoint administrativo aberto, tela com comandos de migration e componente local que
substitua a RFW foram rejeitados por aumentarem exposição e contrariarem a constituição.

## Decisão 7: prontidão é um gate, não uma autorização

**Decisão**: `TenantStorageReadinessPort` informa somente se a localização existe, está pronta e na versão exata
esperada. `tenant-context-isolation`, account activation e operações de negócio consultam esse gate antes de abrir ou
usar o contexto; ele não concede acesso, não contém memberships e não substitui `AuthorizationFacade` ou direitos de
plano.

**Justificativa**: disponibilidade física, autorização e entitlement são condições distintas. Separá-las evita que
um estado de storage seja tratado como permissão ou que o sistema abra uma conexão antes de saber se o tenant é
compatível.

**Alternativas consideradas**: transportar o estado no `Authentication`, inferir prontidão pela conta ou permitir
modo somente leitura durante migration foram rejeitados por ficarem obsoletos, misturarem domínios e contradizerem a
especificação.
