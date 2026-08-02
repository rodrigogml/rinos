-- Sugestão: CREATE DATABASE rinos_global CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- A infraestrutura cria o banco global antes de executar este catálogo.

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

CREATE TABLE identity_legalDocumentVersion (
  id BIGINT AUTO_INCREMENT NOT NULL,
  documentType VARCHAR(32) NOT NULL,
  versionName VARCHAR(64) NOT NULL,
  required BOOLEAN NOT NULL,
  content LONGTEXT NOT NULL,
  contentHash BINARY(32) NOT NULL,
  effectiveAt TIMESTAMP(6) NOT NULL,
  retiredAt TIMESTAMP(6) NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_identity_legal_document_version PRIMARY KEY (id),
  CONSTRAINT uk_identity_legal_document_version UNIQUE (documentType, versionName),
  INDEX idx_identity_legal_document_effective (documentType, effectiveAt, retiredAt)
) ENGINE = InnoDB;

CREATE TABLE identity_legalConsent (
  id BIGINT AUTO_INCREMENT NOT NULL,
  idUser BIGINT NOT NULL,
  idRegistration BIGINT NULL,
  idLegalDocumentVersion BIGINT NOT NULL,
  decision VARCHAR(16) NOT NULL,
  decidedAt TIMESTAMP(6) NOT NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_identity_legal_consent PRIMARY KEY (id),
  CONSTRAINT fk_identity_legal_consent_user FOREIGN KEY (idUser)
    REFERENCES identity_user (id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,
  CONSTRAINT fk_identity_legal_consent_registration FOREIGN KEY (idRegistration)
    REFERENCES identity_registration (id)
    ON DELETE SET NULL
    ON UPDATE RESTRICT,
  CONSTRAINT fk_identity_legal_consent_document_version FOREIGN KEY (idLegalDocumentVersion)
    REFERENCES identity_legalDocumentVersion (id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,
  CONSTRAINT uk_identity_legal_consent_user_version UNIQUE (idUser, idLegalDocumentVersion)
) ENGINE = InnoDB;

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
  activeMarker BOOLEAN NULL,
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
  CONSTRAINT uk_security_origin_window_active
    UNIQUE (originAddress, operation, policy, activeMarker),
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
