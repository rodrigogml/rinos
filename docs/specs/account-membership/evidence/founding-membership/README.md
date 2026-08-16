# Evidência — Associação Fundadora

Em 2026-08-15:

- a migration global `20260815_003` passou no MySQL 9.7.2 em banco novo, atualização incremental, banco corrente,
  versão futura e falha parcial subsequente;
- `MembershipPersistenceIT` comprovou bootstrap idempotente, convergência de duas execuções concorrentes em uma única
  associação/evento, vínculo correto entre conta, tenant e fundador e snapshot estrutural conservador;
- tenant ainda reservado mantém `tenantOperational=false`, mesmo com associação fundadora ativa;
- FKs de `access_groupSubject` e `access_rule` agora impedem referências a memberships inexistentes;
- convite, lifecycle, continuidade e interface não fazem parte desta evidência e continuam pendentes em `tasks.md`.
