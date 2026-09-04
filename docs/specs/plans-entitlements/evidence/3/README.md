# Evidência da fase 3 — bootstrap, backfill e readiness

Data: 2026-08-16.

## Entrega

- seed idempotente de `PERSONAL/FREE`, com composição vazia;
- seed idempotente de `TENANT/FREE`, com `membership.associated-users.limit = 10`;
- chave opaca de idempotência em `ServiceContract` para retomada segura;
- backfill de um contrato pessoal por identidade e um contrato tenant por tenant;
- atribuição inicial vigente para cada contrato criado ou reencontrado;
- ocupação única por tenant/usuário, mesmo com múltiplos registros históricos de membership;
- reserva para convites pendentes, sem reservar novamente quando a identidade já ocupa o tenant;
- readiness anterior ao catálogo de acesso, bloqueando defaults, versões ou composições inconsistentes;
- migration `20260816_003` e `databaseVersion = 20260816003`.

## Validação

- testes unitários do readiness e de sua ordem de execução;
- paridade do schema e descoberta ordenada das migrations;
- `GlobalDatabaseMigrationIT` em MySQL real, incluindo retomada idempotente do backfill, memberships históricas,
  convites pendentes, catálogo e readiness.
- `mvn verify`: 724 testes unitários e 135 testes de integração executados, 18 cenários condicionais ignorados,
  sem falhas ou erros.

As criações futuras passam a consumir as portas de bootstrap na Fase 5; a Fase 3 regulariza com segurança o estado
existente antes dessa integração.
