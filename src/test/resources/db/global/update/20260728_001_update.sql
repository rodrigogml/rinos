-- Arquivo de atualização do banco de dados, não pode ser alterado por agentes de IA.

CREATE TABLE testGlobalMigrationMarker (
  id BIGINT AUTO_INCREMENT NOT NULL,
  executionCount INT NOT NULL,
  CONSTRAINT pk_test_global_migration_marker PRIMARY KEY (id)
) ENGINE = InnoDB;

INSERT INTO testGlobalMigrationMarker (executionCount)
VALUES (1);

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260728001' AS version;
