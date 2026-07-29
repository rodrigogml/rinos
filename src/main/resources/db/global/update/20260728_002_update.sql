-- Arquivo de atualização do banco de dados, não pode ser alterado por agentes de IA.

CREATE TABLE platform_maintenanceLease (
  id BIGINT AUTO_INCREMENT NOT NULL,
  leaseKey VARCHAR(64) NOT NULL,
  instanceId VARCHAR(128) NOT NULL,
  sessionId CHAR(36) NOT NULL,
  epoch BIGINT NOT NULL,
  acquiredAt TIMESTAMP(6) NOT NULL,
  heartbeatAt TIMESTAMP(6) NOT NULL,
  leaseUntil TIMESTAMP(6) NOT NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_platform_maintenance_lease PRIMARY KEY (id),
  CONSTRAINT uk_platform_maintenance_lease_key UNIQUE (leaseKey)
) ENGINE = InnoDB;

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260728002' AS version;
