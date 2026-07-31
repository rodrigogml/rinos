-- Arquivo de atualização do banco de dados, não pode ser alterado por agentes de IA.

CREATE TABLE identity_user (
  id BIGINT AUTO_INCREMENT NOT NULL,
  email VARCHAR(320) NOT NULL,
  normalizedEmail VARCHAR(320) NOT NULL,
  status VARCHAR(32) NOT NULL,
  activatedAt TIMESTAMP(6) NULL,
  blockedAt TIMESTAMP(6) NULL,
  deactivatedAt TIMESTAMP(6) NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_identity_user PRIMARY KEY (id),
  CONSTRAINT uk_identity_user_normalized_email UNIQUE (normalizedEmail),
  INDEX idx_identity_user_status_created (status, createdAt)
) ENGINE = InnoDB;

CREATE TABLE identity_registration (
  id BIGINT AUTO_INCREMENT NOT NULL,
  idUser BIGINT NOT NULL,
  method VARCHAR(24) NOT NULL,
  status VARCHAR(32) NOT NULL,
  expiresAt TIMESTAMP(6) NOT NULL,
  completedAt TIMESTAMP(6) NULL,
  cancelledAt TIMESTAMP(6) NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_identity_registration PRIMARY KEY (id),
  CONSTRAINT fk_identity_registration_user FOREIGN KEY (idUser)
    REFERENCES identity_user (id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,
  CONSTRAINT uk_identity_registration_user UNIQUE (idUser),
  INDEX idx_identity_registration_status_expiry (status, expiresAt)
) ENGINE = InnoDB;

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260729001' AS version;
