-- Arquivo de atualização do banco de dados, não pode ser alterado por agentes de IA.

CREATE TABLE identity_passwordRecovery (
  id BIGINT AUTO_INCREMENT NOT NULL,
  idUser BIGINT NOT NULL,
  tokenHash BINARY(32) NOT NULL,
  status VARCHAR(24) NOT NULL,
  issuedAt TIMESTAMP(6) NOT NULL,
  expiresAt TIMESTAMP(6) NOT NULL,
  usedAt TIMESTAMP(6) NULL,
  invalidatedAt TIMESTAMP(6) NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_identity_password_recovery PRIMARY KEY (id),
  CONSTRAINT fk_identity_password_recovery_user FOREIGN KEY (idUser)
    REFERENCES identity_user (id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,
  CONSTRAINT uk_identity_password_recovery_token_hash UNIQUE (tokenHash),
  INDEX idx_identity_password_recovery_open (idUser, status, issuedAt),
  INDEX idx_identity_password_recovery_retention (updatedAt)
) ENGINE = InnoDB;

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260802001' AS version;
