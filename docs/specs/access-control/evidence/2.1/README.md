# Evidência 2.1 — Schema global e persistência

- Migration incremental: `db/global/update/20260815_001_update.sql`.
- Init consolidado equivalente: `db/global/init/01-ddl.sql` e versão `20260815001`.
- Doze estruturas ACL, guards globais iniciais, uniques e checks de contexto, origem e vigência.
- Entities e repositories para catálogo, grupos, sujeitos, regras, histórico, baseline, bootstrap, auditoria e revisão.
- `AccessControlDatabaseCatalogTest` comprova paridade textual entre init e update.
- `GlobalDatabaseMigrationIT` cobre no MySQL tabelas, dados iniciais, uniques, checks e índices.

Em 15/08/2026, o gate MySQL 9.7.2 executou com sucesso, incluindo migration incremental, init consolidado, checks,
uniques, índices, dados iniciais e histórico.
