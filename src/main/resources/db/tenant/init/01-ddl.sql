-- Sugestão: CREATE DATABASE rinos_<physicalIdentifier> CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE core_tenantBootstrap (
  id BIGINT AUTO_INCREMENT NOT NULL,
  bootstrapKey VARCHAR(100) NOT NULL,
  bootstrapValue VARCHAR(100) NOT NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_core_tenant_bootstrap PRIMARY KEY (id),
  CONSTRAINT uk_core_tenant_bootstrap_key UNIQUE (bootstrapKey)
) ENGINE = InnoDB;
