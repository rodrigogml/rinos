-- Arquivo de atualização do banco de dados, não pode ser alterado por agentes de IA.

CREATE TABLE testTenantMigrationMarker (
  id BIGINT AUTO_INCREMENT NOT NULL,
  CONSTRAINT pk_test_tenant_migration_marker PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260728001' AS version;
