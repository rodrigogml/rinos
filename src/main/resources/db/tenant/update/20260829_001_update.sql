-- Arquivo de atualização do banco de dados, não pode ser alterado por agentes de IA.

CREATE TABLE core_tenantBootstrap (
  id BIGINT AUTO_INCREMENT NOT NULL,
  bootstrapKey VARCHAR(100) NOT NULL,
  bootstrapValue VARCHAR(100) NOT NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_core_tenant_bootstrap PRIMARY KEY (id),
  CONSTRAINT uk_core_tenant_bootstrap_key UNIQUE (bootstrapKey)
) ENGINE = InnoDB;

INSERT INTO core_tenantBootstrap (bootstrapKey, bootstrapValue)
VALUES ('tenant.schema.baseline', '20260829001');

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260829001' AS version;
