# Data Model — Planos, contratos e direitos

## Agregados globais

### `Plan`

`id`, `publicId`, `scope`, `code`, textos localizados, estado, gratuidade, padrão, disponibilidade e version. Único por
`(scope, code)`; exatamente um padrão válido por escopo.

### `PlanVersion`

`id`, plano, `scope`, número, estado, publicação, vigência e version. Publicada é imutável.

### `EntitlementDefinition`

`id`, `scope`, código, módulo proprietário, tipo, unidade, semântica de contagem, estado e textos. A definição inicial é
`TENANT/membership.associated-users.limit`, tipo `MAXIMUM_QUANTITY`, unidade `DISTINCT_USER`.

### `PlanVersionEntitlement`

Versão, definição, escopo e valor tipado. Único por versão/definição. `TENANT/FREE` publica valor `10`;
`PERSONAL/FREE` não possui itens.

### `ServiceContract`

`id`, `publicId`, `scope`, estado, início, origem, correlation e version. Subtipos referenciais ligam exatamente um
`User` ou `Tenant`; há no máximo um contrato inicial por titular.

### `PlanAssignment`

Contrato, versão, escopo, início, término, estado, origem, motivo e idempotency key. Uma atribuição vigente por contrato.

### `TenantUserCapacityOccupancy`

Contrato tenant, tenant, user global, primeira associação, instante e origem. Único por `(tenantId, userId)` e nunca é
apagado por mudança de estado da membership.

### `TenantUserCapacityReservation`

Contrato tenant, intenção/convite, destinatário normalizado protegido, user quando conhecido, estado, expiração e
idempotency key. Estados: `RESERVED`, `CONVERTED`, `RELEASED`, `EXPIRED`. Conversão cria/reutiliza ocupação e não conta
duas vezes.

### Auditoria, outbox e revisão

Eventos registram escopo, contrato, ação, ator, correlação, antes/depois seguro e resultado. Payload não inclui dados de
outro titular nem e-mail em claro.

## Invariantes

- escopos de contrato, plano, versão e direito devem coincidir;
- titular pessoal nunca referencia tenant; titular tenant nunca referencia contrato pessoal;
- atribuição publicada não é editada;
- ocupação histórica não é liberada por estado da membership;
- somente reserva ainda não convertida pode ser liberada;
- ocupações distintas mais reservas efetivas não podem ultrapassar o valor do plano;
- restauração reconcilia catálogo, contratos, atribuições, ocupações e reservas antes de liberar operações.
