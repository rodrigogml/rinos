# Modelo de Dados — Provisionamento do Armazenamento de Tenant

Todas as entidades desta feature pertencem ao schema global `rinos_global`. Elas referenciam somente identidades
globais (`account_tenant`, `account_account` e identidade de usuário quando aplicável); o global nunca recebe FK para
uma tabela do schema físico de tenant.

## `storage_tenantRegistry`

| Campo | Tipo | Regra |
|---|---|---|
| `idTenantStorageRegistry` | BIGINT | PK interna |
| `idTenant` | BIGINT | FK única para `account_tenant` |
| `physicalIdentifier` | CHAR(32) | hexadecimal interno, único, imutável e nunca reutilizado |
| `storageState` | VARCHAR(24) | estado físico atual, controlado por transição |
| `expectedVersion` | VARCHAR(32) | versão exata esperada pelo catálogo tenant |
| `observedVersion` | VARCHAR(32) | última versão comprovada, nula enquanto desconhecida |
| `lastValidatedAt` | TIMESTAMP(6) | última validação estrutural bem-sucedida |
| `quarantineReasonCode` | VARCHAR(100) | motivo seguro opcional, sem stack trace |
| `version` | BIGINT | optimistic lock |
| `createdAt`, `updatedAt` | TIMESTAMP(6) | UTC |

`schemaName` é derivado por código de `physicalIdentifier` como `rinos_<id>` e não é duplicado no banco. A factory
aceita somente identificador validado pela expressão `[a-f0-9]{32}` antes de compor o nome SQL entre identificadores
quoted.

Estados permitidos: `REQUESTED`, `PROVISIONING`, `INITIALIZING`, `MIGRATING`, `READY`, `FAILED`, `QUARANTINED`,
`DEACTIVATING`, `INACTIVE`. Somente `READY` com `observedVersion = expectedVersion` permite prontidão.

## `storage_operation`

| Campo | Tipo | Regra |
|---|---|---|
| `idStorageOperation` | BIGINT | PK e ordem FIFO de aceitação |
| `publicId` | BINARY(16) | protocolo UUID, único e estável |
| `idTenantStorageRegistry` | BIGINT | FK obrigatória |
| `operationType` | VARCHAR(24) | `PROVISION`, `MIGRATE`, `RECONCILE`, `DEACTIVATE` |
| `idempotencyReference` | BINARY(16) | protocolo de origem, único por tipo e tenant |
| `operationState` | VARCHAR(24) | fila/execução/resultado, controlado por transição |
| `activeMarker` | BOOLEAN nulo | `TRUE` apenas para a operação ativa do tenant |
| `attemptCount` | INT | número de tentativas iniciadas |
| `nextAttemptAt` | TIMESTAMP(6) | elegibilidade para claim |
| `leaseOwner`, `leaseUntil` | VARCHAR(100), TIMESTAMP(6) | claim durável entre instâncias |
| `correlationId` | VARCHAR(100) | correlação técnica sem segredo |
| `safeFailureCode` | VARCHAR(100) | motivo seguro opcional |
| `createdAt`, `startedAt`, `finishedAt`, `updatedAt` | TIMESTAMP(6) | UTC |
| `version` | BIGINT | optimistic lock |

Unique `(idTenantStorageRegistry, operationType, idempotencyReference)` torna o pedido repetido idempotente. Unique
parcial simulada por `(idTenantStorageRegistry, activeMarker)` permite somente uma linha ativa porque histórico usa
`NULL`. A busca da fila ordena por `idStorageOperation` e filtra estados elegíveis, preservando que criações aceitas
depois de migrations já enfileiradas não as ultrapassem.

Estados: `QUEUED`, `CLAIMED`, `RUNNING`, `RETRY_WAIT`, `COMPLETED`, `FAILED_FINAL`, `CANCELLED`.

## `storage_operationStep`

| Campo | Tipo | Regra |
|---|---|---|
| `idStorageOperationStep` | BIGINT | PK |
| `idStorageOperation` | BIGINT | FK obrigatória |
| `stepType` | VARCHAR(32) | enum fechado da etapa |
| `stepState` | VARCHAR(24) | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED` |
| `attemptNumber` | INT | incremento por tentativa da etapa |
| `startedAt`, `completedAt` | TIMESTAMP(6) | UTC, nulos enquanto não aplicável |
| `evidenceHash` | BINARY(32) | hash de evidência técnica mínima quando aplicável |
| `safeFailureCode` | VARCHAR(100) | sem detalhes de conexão ou SQL |
| `version` | BIGINT | optimistic lock |

Unique `(idStorageOperation, stepType)` fixa o histórico da etapa. A evidência não armazena script, URL, credencial ou
conteúdo de dado do tenant.

## `storage_migrationExecution`

| Campo | Tipo | Regra |
|---|---|---|
| `idStorageMigrationExecution` | BIGINT | PK |
| `idTenantStorageRegistry` | BIGINT | FK obrigatória |
| `idStorageOperation` | BIGINT | FK de migração/provisionamento correlato |
| `scriptVersion` | VARCHAR(32) | versão compacta imutável |
| `scriptName` | VARCHAR(160) | nome do arquivo validado |
| `scriptHash` | BINARY(32) | SHA-256 do catálogo observado |
| `previousVersion`, `resultingVersion` | VARCHAR(32) | versão observada antes/depois |
| `executionState` | VARCHAR(24) | `STARTED`, `COMPLETED`, `FAILED` |
| `startedAt`, `finishedAt` | TIMESTAMP(6) | UTC |
| `safeFailureCode` | VARCHAR(100) | motivo seguro opcional |

Unique `(idTenantStorageRegistry, scriptVersion)` impede registrar novamente uma versão já observada, inclusive com
hash diferente. Antes de registrar ou aplicar, o worker compara o hash do catálogo com a execução conhecida: hash
divergente para versão já conhecida torna o tenant incompatível e inicia quarentena, nunca uma nova aplicação.

## `storage_stateTransition` e `storage_auditEvent`

`storage_stateTransition` é append-only e registra registry, operação opcional, estado anterior/novo, etapa, origem
`SYSTEM` ou ator global, instante, correlação e resultado. `storage_auditEvent` agrega consultas, ações administrativas
negadas, reconciliações e desativações autorizadas. Ambos rejeitam schema, host, URL, credencial, SQL, stack trace e
dados funcionais em campos de detalhes.

## Relacionamentos e invariantes

```text
account_tenant 1 -- 1 storage_tenantRegistry
storage_tenantRegistry 1 -- N storage_operation
storage_operation 1 -- N storage_operationStep
storage_tenantRegistry 1 -- N storage_migrationExecution
storage_tenantRegistry 1 -- N storage_stateTransition
```

1. O registro global é reservado antes da criação física e seu `physicalIdentifier` nunca muda nem é reutilizado.
2. Operação concluída não volta a executar; uma retomada cria nova tentativa da mesma operação somente quando a
   classificação permite.
3. `READY` exige versão esperada observada, validação concluída e nenhuma operação ativa.
4. `FAILED`/`QUARANTINED` impedem contextos e ativação; não são alterados manualmente para `READY`.
5. Cancelamento da conta não altera estado físico automaticamente; desativação depende de operação própria,
   autorização global e confirmação das políticas externas.
6. Nenhuma tabela global referencia tabelas do schema tenant.
