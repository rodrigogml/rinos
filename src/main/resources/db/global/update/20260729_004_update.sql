-- Arquivo de atualização do banco de dados, não pode ser alterado por agentes de IA.

CREATE TABLE identity_externalIdentity (
  id BIGINT AUTO_INCREMENT NOT NULL,
  idUser BIGINT NOT NULL,
  provider VARCHAR(32) NOT NULL,
  issuer VARCHAR(255) NOT NULL,
  subject VARCHAR(255) NOT NULL,
  status VARCHAR(24) NOT NULL,
  verifiedAt TIMESTAMP(6) NOT NULL,
  activatedAt TIMESTAMP(6) NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_identity_external_identity PRIMARY KEY (id),
  CONSTRAINT fk_identity_external_identity_user FOREIGN KEY (idUser)
    REFERENCES identity_user (id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,
  CONSTRAINT uk_identity_external_identity_issuer_subject UNIQUE (issuer, subject),
  INDEX idx_identity_external_identity_user (idUser)
) ENGINE = InnoDB;

CREATE TABLE security_originWindow (
  id BIGINT AUTO_INCREMENT NOT NULL,
  originAddress VARBINARY(16) NOT NULL,
  operation VARCHAR(48) NOT NULL,
  policy VARCHAR(32) NOT NULL,
  windowStartedAt TIMESTAMP(6) NOT NULL,
  windowEndsAt TIMESTAMP(6) NOT NULL,
  eventCount INT NOT NULL DEFAULT 0,
  blockedUntil TIMESTAMP(6) NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_security_origin_window PRIMARY KEY (id),
  CONSTRAINT uk_security_origin_window
    UNIQUE (originAddress, operation, policy, windowStartedAt),
  INDEX idx_security_origin_window_expiry (windowEndsAt)
) ENGINE = InnoDB;

CREATE TABLE identity_event (
  id BIGINT AUTO_INCREMENT NOT NULL,
  idUser BIGINT NULL,
  idRegistration BIGINT NULL,
  correlationId BINARY(16) NOT NULL,
  eventType VARCHAR(48) NOT NULL,
  previousStatus VARCHAR(32) NULL,
  newStatus VARCHAR(32) NULL,
  originType VARCHAR(32) NOT NULL,
  reason VARCHAR(255) NULL,
  occurredAt TIMESTAMP(6) NOT NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_identity_event PRIMARY KEY (id),
  CONSTRAINT fk_identity_event_user FOREIGN KEY (idUser)
    REFERENCES identity_user (id)
    ON DELETE SET NULL
    ON UPDATE RESTRICT,
  CONSTRAINT fk_identity_event_registration FOREIGN KEY (idRegistration)
    REFERENCES identity_registration (id)
    ON DELETE SET NULL
    ON UPDATE RESTRICT,
  INDEX idx_identity_event_correlation (correlationId),
  INDEX idx_identity_event_occurred (occurredAt)
) ENGINE = InnoDB;

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260729004' AS version;
