# Contratos Públicos — Provisionamento do Armazenamento de Tenant

Os contratos são Java API entre módulos do mesmo artefato. Não há API REST pública nesta feature. IDs físicos,
credenciais, host, URLs JDBC, scripts, hashes e detalhes de infraestrutura não atravessam estes limites.

## Solicitação de provisionamento pelo cadastro

O contrato existente `TenantProvisioningRequestPort` permanece a entrada de `account-registration`:

```java
interface TenantProvisioningRequestPort {
  AccountBootstrapResult requestProvisioning(AccountBootstrapRequest request);
}
```

`AccountBootstrapRequest` traz apenas protocolo, UUIDs públicos de conta/tenant, fundador e correlação. O adapter de
storage resolve os IDs internos pelo global, confirma o vínculo conta-tenant e cria ou retorna a única operação
`PROVISION` associada ao protocolo.

| Resultado | Significado | Efeito no cadastro |
|---|---|---|
| `ACCEPTED` | operação foi enfileirada | checkpoint `STORAGE` permanece pendente/processando |
| `ALREADY_COMPLETED` | mesma intenção já chegou a estado terminal compatível | checkpoint pode ser reconciliado |
| `REJECTED` | vínculo, estado ou protocolo incompatível | saga permanece bloqueada com motivo seguro |
| `UNAVAILABLE` | não foi possível confirmar a fila/global | outbox mantém nova tentativa; não há sucesso artificial |

Repetição com o mesmo protocolo não cria registro físico nem outra operação. Protocolo associado a tenant divergente
é rejeitado e auditado.

## Gate de prontidão consumido por contexto e ativação

```java
interface TenantStorageReadinessPort {
  TenantStorageReadinessSnapshot inspect(UUID tenantPublicId);
}
```

| Campo | Tipo | Regra |
|---|---|---|
| `sourceAvailable` | boolean | `false` em falha de leitura; consumidores falham fechados |
| `tenantKnown` | boolean | não revela outro tenant à UI comum |
| `ready` | boolean | `true` somente com storage pronto e versão exata |
| `availability` | enum | `READY`, `WAITING`, `MIGRATING`, `ATTENTION`, `INACTIVE` |
| `safeReasonCode` | string opcional | sem detalhe técnico |
| `observedAt` | `Instant` | UTC |

O port não aceita contexto de usuário, não autoriza nem abre conexão de negócio. `tenant-context-isolation` o combina
com identidade, associação, autorização e plano; `account-registration` o combina com os quatro checkpoints antes de
ativar a conta.

## Consulta segura de status e operação

```java
interface TenantStorageStatusFacade {
  TenantStoragePublicStatus findForCreator(UUID protocolId);
  TenantStorageAdministrationPage search(TenantStorageAdministrationQuery query,
                                         AuthorizationContext context);
  TenantStorageOperationDetails details(UUID operationPublicId, AuthorizationContext context);
}
```

`findForCreator` resolve a conta exclusivamente pelo protocolo e exige que o criador autenticado seja derivado na
fachada, nunca recebido como campo livre. Retorna apenas estado público, atualização, próximo passo seguro e
protocolo. As operações administrativas exigem as chaves globais canônicas de consulta/gestão de provisionamento,
contexto explícito, reautenticação e 2FA quando a operação for sensível. O resultado administrativo pode conter
estado, versão, tentativa, etapa e motivo seguro, mas nunca localização, comando ou segredo.

## Eventos internos e correlação

| Evento | Emissor | Consumidor | Garantia |
|---|---|---|---|
| `TenantStorageOperationQueued` | storage | worker estrutural | outbox/consulta durável; perda de notificação não perde operação |
| `TenantStorageReadinessChanged` | storage | account activation, context isolation | idempotente; o gate sempre relê a fonte global |
| `TenantStorageAttentionRaised` | storage | platform operations | alerta seguro e auditável |

Eventos carregam `operationPublicId`, `tenantPublicId`, estado público, correlação e instante. Eles não carregam
schema, URLs, credenciais, SQL, hashes de conteúdo ou dados de tenant.

O evento de aceitação da fila usa o contrato Java `TenantStorageOperationQueuedEventVO`. Nesta etapa ele apenas
padroniza os quatro campos seguros para o despachante durável; a publicação confiável pela fila/outbox é introduzida
somente com os leases e o worker. Nenhuma chamada de reserva considera o evento como evidência de schema criado ou de
tenant pronto.

## Erros seguros

`TENANT_STORAGE_UNAVAILABLE`, `TENANT_STORAGE_NOT_READY`, `TENANT_STORAGE_INCOMPATIBLE`,
`TENANT_STORAGE_OPERATION_CONFLICT`, `TENANT_STORAGE_REQUIRES_INFRASTRUCTURE`,
`TENANT_STORAGE_ACCESS_DENIED` e `TENANT_STORAGE_INPUT_INVALID` são códigos iniciais. Erros RFW/MySQL são convertidos
e registrados internamente; não são propagados como conteúdo da resposta.
