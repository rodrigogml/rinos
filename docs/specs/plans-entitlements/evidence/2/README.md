# Evidência da fase 2 — schema global

Data: 2026-08-16.

## Entrega

- catálogo de planos, versões, definições e composição tipada por escopo;
- contratos pessoais e tenant com titulares referenciais separados;
- atribuições temporais com uma única atribuição vigente por contrato;
- ocupação permanente por tenant/usuário e reserva única por convite;
- FKs compostas entre contrato, tenant, conta, associação, convite e eventual ocupação convertida;
- auditoria, outbox, idempotência, checks, índices e versões otimistas;
- `entitlementScope` persistido junto de `entitlementCode` no catálogo de acesso;
- paridade entre init e update incremental `20260816_002`, com `databaseVersion = 20260816002`.

## Validação

- testes textuais de paridade dos catálogos de acesso e planos;
- catálogo de migrations descoberto em ordem e sem duplicidade;
- `GlobalDatabaseMigrationIT` executado em MySQL real para instalação limpa, evolução incremental, versão,
  constraints de escopo, defaults, titulares, atribuições, ocupações e reservas;
- `mvn verify`: 721 testes unitários e 134 testes de integração sem falhas; 18 cenários ignorados.

O seed dos planos `FREE`, contratos existentes e ocupações permanece deliberadamente na fase 3.
