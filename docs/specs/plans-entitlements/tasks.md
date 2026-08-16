# Tasks — Planos, contratos e direitos

## Fase 1 — Contratos públicos

- [x] 1.1 Criar tipos `ContractScope`, `EntitlementSubject`, requirements e decisões tipadas.
- [x] 1.2 Publicar facades de avaliação, bootstrap pessoal/tenant e capacidade de usuários.
- [x] 1.3 Substituir `entitlementCode` isolado por requisito com `subjectScope`, preservando adapter compatível temporário.
- [x] 1.4 Testar que contexto global administrativo não infere plano pessoal.

Evidência: [contratos públicos e entitlement tipado](./evidence/1/README.md).

## Fase 2 — Schema global

- [x] 2.1 Criar catálogo, versões, definições e composição com integridade de escopo.
- [x] 2.2 Criar contratos, titulares e atribuições com unicidade vigente.
- [x] 2.3 Criar ocupações históricas e reservas de capacidade tenant.
- [x] 2.4 Criar auditoria, outbox, índices, checks e optimistic version.
- [x] 2.5 Atualizar init, update incremental, versão do banco e testes MySQL.

Evidência: [schema global e migração MySQL](./evidence/2/README.md).

## Fase 3 — Bootstrap e backfill

- [x] 3.1 Semear idempotentemente `PERSONAL/FREE` vazio.
- [x] 3.2 Semear `TENANT/FREE` com `membership.associated-users.limit = 10`.
- [x] 3.3 Fazer backfill de contratos pessoais e tenant existentes sem duplicar vínculo válido.
- [x] 3.4 Criar ocupação para cada identidade distinta já associada e reconciliar convites pendentes.
- [x] 3.5 Bloquear startup/readiness se padrão, versão ou composição obrigatória forem inconsistentes.

Evidência: [bootstrap, backfill e readiness](./evidence/3/README.md).

## Fase 4 — Núcleo

- [ ] 4.1 Implementar domínio de catálogo, contrato, atribuição, fallback e imutabilidade.
- [x] 4.2 Implementar avaliação fail-closed e cache somente de composição publicada. Evidência: [Fase 4](evidence/4/README.md).
- [ ] 4.3 Implementar reserva, conversão, liberação pré-aceite e ocupação permanente.
- [ ] 4.4 Serializar concorrência cross-instance e garantir idempotência.
- [ ] 4.5 Implementar auditoria, outbox, métricas e diagnóstico seguro.

## Fase 5 — Integrações

- [ ] 5.1 Tornar contrato `PERSONAL/FREE` condição da ativação de identidade.
- [ ] 5.2 Adaptar `DefaultPlanAssignmentPort` ao contrato `TENANT/FREE`.
- [ ] 5.3 Integrar reserva antes do envio de convite e liberação em revogação/expiração.
- [ ] 5.4 Integrar conversão no aceite e ocupação em toda inclusão manual/importada.
- [ ] 5.5 Revalidar capacidade em ativação e reativação sem liberar vaga por estado.
- [ ] 5.6 Integrar `PlanEntitlementAccessPort` ao sujeito explícito.

## Fase 6 — Validação

- [ ] 6.1 Testar isolamento pessoal/tenant e entre tenants.
- [ ] 6.2 Testar teto dez com convites, inclusão, aceite e concorrência real MySQL.
- [ ] 6.3 Testar que remoção, saída, suspensão e bloqueio não liberam vaga.
- [ ] 6.4 Testar que revogação/expiração pré-aceite libera exatamente uma reserva.
- [ ] 6.5 Testar backfill, restauração, fallback, indisponibilidade e repetição.
- [ ] 6.6 Executar `mvn verify` e registrar evidências.

## Fase 7 — Interface posterior

- [ ] 7.1 Apresentar análise das lacunas RFW antes de qualquer tela.
- [ ] 7.2 Obter autorização separada para eventual evolução do submódulo.
- [ ] 7.3 Implementar superfícies pessoais, tenant e administrativas usando APIs públicas aprovadas.
