# Evidência 2.2 — Revisão, histórico e auditoria

- `AccessContextRevisionService` garante a linha por contexto, obtém lock pessimista e incrementa revisão monotônica.
- `AccessRuleMutationService` cria ou substitui a regra corrente sob `@Transactional`, preserva a identidade, grava
  snapshot append-only e evento minimizado, e incrementa a revisão no fim da mesma transação.
- Reaplicação semanticamente idêntica não produz histórico, auditoria ou nova revisão.
- Baseline protegida rejeita bloqueio de suas próprias chaves mínimas, sem proibir bloqueios de outras chaves.
- Testes unitários comprovam validação contextual, ordem de persistência e conteúdo seguro do histórico.

`AccessRulePersistenceIT` força uma falha real de integridade no histórico e comprova que regra, histórico, auditoria e
revisão sofrem rollback. O gate foi executado com sucesso em MySQL 9.7.2 em 15/08/2026.
