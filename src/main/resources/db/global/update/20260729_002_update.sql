-- Arquivo de atualização do banco de dados, não pode ser alterado por agentes de IA.

CREATE TABLE identity_localCredential (
  id BIGINT AUTO_INCREMENT NOT NULL,
  idUser BIGINT NOT NULL,
  passwordHash VARCHAR(255) NOT NULL,
  status VARCHAR(24) NOT NULL,
  invalidatedAt TIMESTAMP(6) NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_identity_local_credential PRIMARY KEY (id),
  CONSTRAINT fk_identity_local_credential_user FOREIGN KEY (idUser)
    REFERENCES identity_user (id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,
  CONSTRAINT uk_identity_local_credential_user UNIQUE (idUser)
) ENGINE = InnoDB;

CREATE TABLE identity_verification (
  id BIGINT AUTO_INCREMENT NOT NULL,
  idRegistration BIGINT NOT NULL,
  purpose VARCHAR(32) NOT NULL,
  tokenHash BINARY(32) NOT NULL,
  status VARCHAR(24) NOT NULL,
  issuedAt TIMESTAMP(6) NOT NULL,
  expiresAt TIMESTAMP(6) NOT NULL,
  usedAt TIMESTAMP(6) NULL,
  invalidatedAt TIMESTAMP(6) NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_identity_verification PRIMARY KEY (id),
  CONSTRAINT fk_identity_verification_registration FOREIGN KEY (idRegistration)
    REFERENCES identity_registration (id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,
  CONSTRAINT uk_identity_verification_token_hash UNIQUE (tokenHash),
  INDEX idx_identity_verification_open (idRegistration, purpose, status, issuedAt),
  INDEX idx_identity_verification_expiry (status, expiresAt)
) ENGINE = InnoDB;

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260729002' AS version;
