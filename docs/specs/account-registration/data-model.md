# Modelo de Dados — Cadastro de Contas

Todos os registros deste documento pertencem ao banco global.

## `account_tenant`

| Campo | Tipo | Regra |
|---|---|---|
| `idTenant` | BIGINT | PK interna, nunca reutilizada |
| `publicId` | BINARY(16) | UUID único e imutável |
| `status` | VARCHAR(24) | `RESERVED`, `OPERATIONAL`, `SUSPENDED`, `CANCELLED` |
| `version` | BIGINT | optimistic lock |
| `createdAt`, `updatedAt` | TIMESTAMP(6) | UTC, banco como autoridade |

## `account_account`

| Campo | Tipo | Regra |
|---|---|---|
| `idAccount` | BIGINT | PK interna |
| `publicId` | BINARY(16) | UUID único e imutável |
| `idTenant` | BIGINT | FK única para `account_tenant` |
| `founderUserId` | BIGINT | FK para identidade global; não autoriza |
| `displayName` | VARCHAR(160) | não único |
| `baseCurrency` | CHAR(3) | ISO 4217 suportada |
| `timeZoneId` | VARCHAR(100) | IANA `ZoneId` |
| `status` | VARCHAR(24) | `CREATING`, `ACTIVE`, `SUSPENDED`, `CANCELLED` |
| `version` | BIGINT | optimistic lock |
| `createdAt`, `updatedAt` | TIMESTAMP(6) | UTC |

`idTenant` e os UUIDs são imutáveis. Cancelamento é lógico.

## `account_creationIntent`

| Campo | Tipo | Regra |
|---|---|---|
| `idAccountCreationIntent` | BIGINT | PK |
| `publicId` | BINARY(16) | UUID da intenção |
| `protocolId` | BINARY(16) | UUID público de acompanhamento, único |
| `creatorUserId` | BIGINT | identidade que iniciou |
| `idempotencyKey` | BINARY(16) | chave opaca fornecida pelo cliente |
| `payloadHash` | BINARY(32) | SHA-256 da representação canônica |
| `idAccount` | BIGINT | FK única |
| `status` | VARCHAR(32) | `ACCEPTED`, `PROCESSING`, `READY`, `FAILED`, `CANCELLED` |
| `publicStage` | VARCHAR(32) | `ACCEPTED`, `PREPARING`, `FINISHING`, `AVAILABLE`, `ATTENTION` |
| `failureCode` | VARCHAR(100) | motivo seguro opcional |
| `createdAt`, `updatedAt` | TIMESTAMP(6) | UTC |

Unique: `(creatorUserId, idempotencyKey)`. Mesmo par com hash diferente é conflito, nunca update.

## `account_provisioningCheckpoint`

Uma linha por conta e etapa conhecida: `STORAGE`, `FOUNDING_MEMBERSHIP`, `ACCESS_BOOTSTRAP`, `DEFAULT_PLAN`.
Campos: conta, etapa, estado (`PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`), referência externa opaca, tentativa,
próximo instante, versão e timestamps. Unique `(idAccount, stepType)`. A ordem material é fixa:
`STORAGE`, `FOUNDING_MEMBERSHIP`, `ACCESS_BOOTSTRAP`, `DEFAULT_PLAN`; uma etapa posterior nunca é elegível antes
da anterior concluir. `STORAGE` é concluída somente pela observação de `TenantStorageReadinessPort` em `READY`,
depois que a outbox já aceitou a intenção.

## `account_outboxEvent`

| Campo | Regra |
|---|---|
| UUID do evento | único, imutável |
| aggregate type/id | somente conta/intento interno |
| payload | JSON válido não autoritativo; o dispatcher nunca o usa para reconstruir IDs, protocolo ou fundador |
| lease | `leaseOwner` e `leaseUntil` são gravados juntos; a confirmação exige o mesmo owner e lease ainda vigente |
| confirmação | `PUBLISHED` somente após `TenantProvisioningRequestPort` aceitar a intenção durável; não significa storage pronto |
| indisponibilidade | volta a `PENDING` com `nextAttemptAt` exponencialmente limitado; `FAILED` é reservado para rejeição terminal |
| event type | enum fechado |
| payload JSON | identificadores e versão; sem token/IP/prova |
| status | `PENDING`, `PROCESSING`, `PUBLISHED`, `FAILED` |
| attempt/nextAttemptAt | retry durável |
| leaseOwner/leaseUntil | claim entre instâncias |
| createdAt/publishedAt | UTC |

## `account_auditEvent`

Append-only: tipo, conta, tenant, ator ou origem sistêmica, correlação, resultado seguro, detalhes JSON minimizados e
instante. Não contém token Turnstile, IP puro ou stack trace.

## `security_originWindow` — uso por criação de conta

Tabela global temporária compartilhada pelo módulo de identidade. Para esta feature, a linha usa
`operation = ACCOUNT_CREATION` e a política de limite absoluto. O endereço fica somente no formato binário canônico,
com janela de contagem, número de eventos e `blockedUntil` independente. A conta, a intenção e a auditoria não recebem
cópia desse endereço. A limpeza coordenada pela plataforma remove janelas expiradas conforme a retenção de origem.

## Invariantes

1. Cada conta referencia exatamente um tenant e cada tenant no máximo uma conta.
2. Conta `ACTIVE` exige tenant `OPERATIONAL`, quatro checkpoints `COMPLETED` e uma revalidação
   transacional atual de storage `READY`, associação/baseline ACL fundadora e contrato padrão.
3. `CANCELLED` nunca volta a `ACTIVE`; IDs nunca são reutilizados.
4. A criação inicial confirma conta, tenant, intenção, auditoria e outbox conjuntamente.
5. Apenas transições declaradas no serviço são aceitas; checks de banco protegem o vocabulário.
6. Foreign keys globais usam `RESTRICT`; limpeza e retenção são processos explícitos.
7. O primeiro dispatch de storage apenas move seu checkpoint para `PROCESSING`. O coordenador de
   pré-ativação pode concluir cada um dos quatro checkpoints, mas não altera conta ou tenant;
   somente a fase de ativação pode promover ambos depois da verificação conjunta. A mesma
   transação marca a intenção `READY` e o estágio público `AVAILABLE`.

## Índices

- conta por `publicId`, `idTenant`, `founderUserId/status`;
- intenção por `protocolId`, `(creatorUserId,idempotencyKey)`, `status/updatedAt`;
- checkpoint por `status/nextAttemptAt`;
- outbox por `status/nextAttemptAt/leaseUntil`;
- auditoria por `idAccount/occurredAt` e `idTenant/occurredAt`.

## Migração

DDL entra em `db/global/init/01-ddl.sql` e em novo update versionado posterior a `20260815_001_update.sql`, terminando
com `databaseVersion`. Testes usam MySQL real para checks, uniques, FKs, JSON, timestamps, rollback e concorrência.
