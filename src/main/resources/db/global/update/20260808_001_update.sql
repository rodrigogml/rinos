-- Arquivo de atualização do banco de dados, não pode ser alterado por agentes de IA.

ALTER TABLE identity_localCredential
  ADD COLUMN passwordChangedAt TIMESTAMP(6) NULL AFTER invalidatedAt,
  ADD COLUMN compromisedAt TIMESTAMP(6) NULL AFTER passwordChangedAt,
  ADD COLUMN lastUsedAt TIMESTAMP(6) NULL AFTER compromisedAt;

UPDATE identity_localCredential
SET passwordChangedAt = COALESCE(updatedAt, createdAt)
WHERE passwordChangedAt IS NULL;

ALTER TABLE identity_localCredential
  MODIFY COLUMN passwordChangedAt TIMESTAMP(6) NOT NULL;

ALTER TABLE identity_externalIdentity
  ADD COLUMN lastUsedAt TIMESTAMP(6) NULL AFTER activatedAt;

CREATE TABLE identity_authenticationFlow (
  id BIGINT AUTO_INCREMENT NOT NULL,
  idUser BIGINT NULL,
  referenceHash BINARY(32) NOT NULL,
  purpose VARCHAR(32) NOT NULL,
  primaryMethod VARCHAR(32) NULL,
  requiredAssurance VARCHAR(24) NOT NULL,
  persistentLoginRequested BOOLEAN NOT NULL,
  status VARCHAR(24) NOT NULL,
  failureCount INT NOT NULL DEFAULT 0,
  issuedAt TIMESTAMP(6) NOT NULL,
  expiresAt TIMESTAMP(6) NOT NULL,
  usedAt TIMESTAMP(6) NULL,
  invalidatedAt TIMESTAMP(6) NULL,
  correlationId BINARY(16) NOT NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_identity_authentication_flow PRIMARY KEY (id),
  CONSTRAINT fk_identity_authentication_flow_user FOREIGN KEY (idUser)
    REFERENCES identity_user (id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,
  CONSTRAINT uk_identity_authentication_flow_reference UNIQUE (referenceHash),
  CONSTRAINT ck_identity_authentication_flow_purpose
    CHECK (purpose IN ('SIGN_IN', 'REAUTHENTICATION', 'FACTOR_RECOVERY', 'LEGAL_CONSENT')),
  CONSTRAINT ck_identity_authentication_flow_primary_method
    CHECK (primaryMethod IS NULL OR primaryMethod IN
      ('PASSWORD', 'GOOGLE', 'PASSKEY', 'TOTP', 'EMAIL_CODE', 'RECOVERY_CODE')),
  CONSTRAINT ck_identity_authentication_flow_assurance
    CHECK (requiredAssurance IN ('SINGLE_FACTOR', 'MULTI_FACTOR', 'PHISHING_RESISTANT')),
  CONSTRAINT ck_identity_authentication_flow_status
    CHECK (status IN ('OPEN', 'USED', 'INVALIDATED', 'EXPIRED')),
  CONSTRAINT ck_identity_authentication_flow_counters
    CHECK (failureCount >= 0 AND version >= 0),
  CONSTRAINT ck_identity_authentication_flow_expiry CHECK (expiresAt > issuedAt),
  INDEX idx_identity_authentication_flow_user_state (idUser, purpose, status, expiresAt),
  INDEX idx_identity_authentication_flow_expiry (status, expiresAt)
) ENGINE = InnoDB;

CREATE TABLE identity_authenticationFlowMethod (
  id BIGINT AUTO_INCREMENT NOT NULL,
  idAuthenticationFlow BIGINT NOT NULL,
  method VARCHAR(32) NOT NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_identity_authentication_flow_method PRIMARY KEY (id),
  CONSTRAINT fk_identity_authentication_flow_method_flow FOREIGN KEY (idAuthenticationFlow)
    REFERENCES identity_authenticationFlow (id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,
  CONSTRAINT uk_identity_authentication_flow_method UNIQUE (idAuthenticationFlow, method),
  CONSTRAINT ck_identity_authentication_flow_method
    CHECK (method IN ('PASSWORD', 'GOOGLE', 'PASSKEY', 'TOTP', 'EMAIL_CODE', 'RECOVERY_CODE'))
) ENGINE = InnoDB;

CREATE TABLE identity_authenticationProof (
  id BIGINT AUTO_INCREMENT NOT NULL,
  idAuthenticationFlow BIGINT NOT NULL,
  type VARCHAR(32) NOT NULL,
  proofDigest VARBINARY(96) NOT NULL,
  keyVersion VARCHAR(32) NULL,
  status VARCHAR(24) NOT NULL,
  activeMarker BOOLEAN NULL,
  attemptCount INT NOT NULL DEFAULT 0,
  issuedAt TIMESTAMP(6) NOT NULL,
  expiresAt TIMESTAMP(6) NOT NULL,
  usedAt TIMESTAMP(6) NULL,
  invalidatedAt TIMESTAMP(6) NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_identity_authentication_proof PRIMARY KEY (id),
  CONSTRAINT fk_identity_authentication_proof_flow FOREIGN KEY (idAuthenticationFlow)
    REFERENCES identity_authenticationFlow (id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,
  CONSTRAINT uk_identity_authentication_proof_active UNIQUE (idAuthenticationFlow, type, activeMarker),
  CONSTRAINT ck_identity_authentication_proof_type
    CHECK (type IN ('EMAIL_OTP', 'LEGAL_CONSENT', 'FACTOR_RECOVERY')),
  CONSTRAINT ck_identity_authentication_proof_status
    CHECK (status IN ('OPEN', 'USED', 'INVALIDATED', 'EXPIRED')),
  CONSTRAINT ck_identity_authentication_proof_active
    CHECK ((status = 'OPEN' AND activeMarker = TRUE) OR (status <> 'OPEN' AND activeMarker IS NULL)),
  CONSTRAINT ck_identity_authentication_proof_counters
    CHECK (attemptCount >= 0 AND version >= 0),
  CONSTRAINT ck_identity_authentication_proof_expiry CHECK (expiresAt > issuedAt),
  INDEX idx_identity_authentication_proof_expiry (status, expiresAt)
) ENGINE = InnoDB;

CREATE TABLE identity_totpFactor (
  id BIGINT AUTO_INCREMENT NOT NULL,
  idUser BIGINT NOT NULL,
  reference BINARY(16) NOT NULL,
  label VARCHAR(100) NOT NULL,
  encryptedSecret VARBINARY(512) NOT NULL,
  encryptionNonce BINARY(12) NOT NULL,
  keyVersion VARCHAR(32) NOT NULL,
  status VARCHAR(24) NOT NULL,
  lastAcceptedStep BIGINT NULL,
  confirmedAt TIMESTAMP(6) NULL,
  lastUsedAt TIMESTAMP(6) NULL,
  revokedAt TIMESTAMP(6) NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_identity_totp_factor PRIMARY KEY (id),
  CONSTRAINT fk_identity_totp_factor_user FOREIGN KEY (idUser)
    REFERENCES identity_user (id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,
  CONSTRAINT uk_identity_totp_factor_reference UNIQUE (reference),
  CONSTRAINT ck_identity_totp_factor_status CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED')),
  CONSTRAINT ck_identity_totp_factor_step CHECK (lastAcceptedStep IS NULL OR lastAcceptedStep >= 0),
  CONSTRAINT ck_identity_totp_factor_version CHECK (version >= 0),
  INDEX idx_identity_totp_factor_user_state (idUser, status)
) ENGINE = InnoDB;

CREATE TABLE identity_emailFactor (
  id BIGINT AUTO_INCREMENT NOT NULL,
  idUser BIGINT NOT NULL,
  reference BINARY(16) NOT NULL,
  status VARCHAR(24) NOT NULL,
  activatedAt TIMESTAMP(6) NOT NULL,
  lastUsedAt TIMESTAMP(6) NULL,
  disabledAt TIMESTAMP(6) NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_identity_email_factor PRIMARY KEY (id),
  CONSTRAINT fk_identity_email_factor_user FOREIGN KEY (idUser)
    REFERENCES identity_user (id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,
  CONSTRAINT uk_identity_email_factor_user UNIQUE (idUser),
  CONSTRAINT uk_identity_email_factor_reference UNIQUE (reference),
  CONSTRAINT ck_identity_email_factor_status CHECK (status IN ('ACTIVE', 'DISABLED')),
  CONSTRAINT ck_identity_email_factor_version CHECK (version >= 0)
) ENGINE = InnoDB;

CREATE TABLE identity_recoveryCodeSet (
  id BIGINT AUTO_INCREMENT NOT NULL,
  idUser BIGINT NOT NULL,
  reference BINARY(16) NOT NULL,
  status VARCHAR(24) NOT NULL,
  activeMarker BOOLEAN NULL,
  issuedAt TIMESTAMP(6) NOT NULL,
  invalidatedAt TIMESTAMP(6) NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_identity_recovery_code_set PRIMARY KEY (id),
  CONSTRAINT fk_identity_recovery_code_set_user FOREIGN KEY (idUser)
    REFERENCES identity_user (id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,
  CONSTRAINT uk_identity_recovery_code_set_reference UNIQUE (reference),
  CONSTRAINT uk_identity_recovery_code_set_active UNIQUE (idUser, activeMarker),
  CONSTRAINT ck_identity_recovery_code_set_status
    CHECK (status IN ('ACTIVE', 'INVALIDATED', 'EXHAUSTED')),
  CONSTRAINT ck_identity_recovery_code_set_active
    CHECK ((status = 'ACTIVE' AND activeMarker = TRUE) OR (status <> 'ACTIVE' AND activeMarker IS NULL)),
  CONSTRAINT ck_identity_recovery_code_set_version CHECK (version >= 0)
) ENGINE = InnoDB;

CREATE TABLE identity_recoveryCode (
  id BIGINT AUTO_INCREMENT NOT NULL,
  idRecoveryCodeSet BIGINT NOT NULL,
  codeHash VARCHAR(255) NOT NULL,
  ordinal SMALLINT NOT NULL,
  status VARCHAR(24) NOT NULL,
  usedAt TIMESTAMP(6) NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_identity_recovery_code PRIMARY KEY (id),
  CONSTRAINT fk_identity_recovery_code_set FOREIGN KEY (idRecoveryCodeSet)
    REFERENCES identity_recoveryCodeSet (id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,
  CONSTRAINT uk_identity_recovery_code_ordinal UNIQUE (idRecoveryCodeSet, ordinal),
  CONSTRAINT ck_identity_recovery_code_ordinal CHECK (ordinal BETWEEN 1 AND 10),
  CONSTRAINT ck_identity_recovery_code_status CHECK (status IN ('AVAILABLE', 'USED', 'INVALIDATED'))
) ENGINE = InnoDB;

CREATE TABLE identity_passkeyUser (
  id BIGINT AUTO_INCREMENT NOT NULL,
  idUser BIGINT NOT NULL,
  userHandle VARBINARY(64) NOT NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_identity_passkey_user PRIMARY KEY (id),
  CONSTRAINT fk_identity_passkey_user_user FOREIGN KEY (idUser)
    REFERENCES identity_user (id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,
  CONSTRAINT uk_identity_passkey_user_user UNIQUE (idUser),
  CONSTRAINT uk_identity_passkey_user_handle UNIQUE (userHandle)
) ENGINE = InnoDB;

CREATE TABLE identity_passkeyCredential (
  id BIGINT AUTO_INCREMENT NOT NULL,
  idPasskeyUser BIGINT NOT NULL,
  reference BINARY(16) NOT NULL,
  credentialType VARCHAR(32) NOT NULL,
  credentialId VARBINARY(1024) NOT NULL,
  publicKey BLOB NOT NULL,
  signatureCount BIGINT UNSIGNED NOT NULL,
  uvInitialized BOOLEAN NOT NULL,
  backupEligible BOOLEAN NOT NULL,
  backupState BOOLEAN NOT NULL,
  transports VARCHAR(255) NULL,
  attestationObject BLOB NOT NULL,
  attestationClientDataJson BLOB NOT NULL,
  label VARCHAR(100) NOT NULL,
  status VARCHAR(24) NOT NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  lastUsedAt TIMESTAMP(6) NULL,
  revokedAt TIMESTAMP(6) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_identity_passkey_credential PRIMARY KEY (id),
  CONSTRAINT fk_identity_passkey_credential_user FOREIGN KEY (idPasskeyUser)
    REFERENCES identity_passkeyUser (id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,
  CONSTRAINT uk_identity_passkey_credential_reference UNIQUE (reference),
  CONSTRAINT uk_identity_passkey_credential_id UNIQUE (credentialId),
  CONSTRAINT ck_identity_passkey_credential_status CHECK (status IN ('ACTIVE', 'REVOKED')),
  CONSTRAINT ck_identity_passkey_credential_flags
    CHECK (uvInitialized IN (FALSE, TRUE) AND backupEligible IN (FALSE, TRUE) AND backupState IN (FALSE, TRUE)),
  CONSTRAINT ck_identity_passkey_credential_version CHECK (version >= 0),
  INDEX idx_identity_passkey_credential_user_state (idPasskeyUser, status)
) ENGINE = InnoDB;

CREATE TABLE identity_authSession (
  id BIGINT AUTO_INCREMENT NOT NULL,
  idUser BIGINT NOT NULL,
  publicReference BINARY(16) NOT NULL,
  selectorHash BINARY(32) NOT NULL,
  validatorDigest VARBINARY(96) NOT NULL,
  keyVersion VARCHAR(32) NOT NULL,
  remembered BOOLEAN NOT NULL,
  status VARCHAR(24) NOT NULL,
  primaryMethod VARCHAR(32) NOT NULL,
  assuranceLevel VARCHAR(24) NOT NULL,
  authenticatedAt TIMESTAMP(6) NOT NULL,
  lastStrongAuthAt TIMESTAMP(6) NOT NULL,
  lastActivityAt TIMESTAMP(6) NOT NULL,
  absoluteExpiresAt TIMESTAMP(6) NOT NULL,
  idleExpiresAt TIMESTAMP(6) NOT NULL,
  deviceDescription VARCHAR(255) NULL,
  originAddress VARBINARY(16) NULL,
  userAgentDigest BINARY(32) NULL,
  revokedAt TIMESTAMP(6) NULL,
  revocationReason VARCHAR(48) NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_identity_auth_session PRIMARY KEY (id),
  CONSTRAINT fk_identity_auth_session_user FOREIGN KEY (idUser)
    REFERENCES identity_user (id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,
  CONSTRAINT uk_identity_auth_session_reference UNIQUE (publicReference),
  CONSTRAINT uk_identity_auth_session_selector UNIQUE (selectorHash),
  CONSTRAINT ck_identity_auth_session_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
  CONSTRAINT ck_identity_auth_session_method
    CHECK (primaryMethod IN ('PASSWORD', 'GOOGLE', 'PASSKEY', 'TOTP', 'EMAIL_CODE', 'RECOVERY_CODE')),
  CONSTRAINT ck_identity_auth_session_assurance
    CHECK (assuranceLevel IN ('SINGLE_FACTOR', 'MULTI_FACTOR', 'PHISHING_RESISTANT')),
  CONSTRAINT ck_identity_auth_session_expiry
    CHECK (absoluteExpiresAt > authenticatedAt AND idleExpiresAt <= absoluteExpiresAt),
  CONSTRAINT ck_identity_auth_session_version CHECK (version >= 0),
  INDEX idx_identity_auth_session_user_state (idUser, status, absoluteExpiresAt),
  INDEX idx_identity_auth_session_expiry (status, idleExpiresAt, absoluteExpiresAt)
) ENGINE = InnoDB;

CREATE TABLE identity_authSessionMethod (
  id BIGINT AUTO_INCREMENT NOT NULL,
  idAuthSession BIGINT NOT NULL,
  method VARCHAR(32) NOT NULL,
  factorOrder SMALLINT NOT NULL,
  verifiedAt TIMESTAMP(6) NOT NULL,
  userVerification BOOLEAN NULL,
  CONSTRAINT pk_identity_auth_session_method PRIMARY KEY (id),
  CONSTRAINT fk_identity_auth_session_method_session FOREIGN KEY (idAuthSession)
    REFERENCES identity_authSession (id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,
  CONSTRAINT uk_identity_auth_session_method_order UNIQUE (idAuthSession, factorOrder),
  CONSTRAINT ck_identity_auth_session_method_name
    CHECK (method IN ('PASSWORD', 'GOOGLE', 'PASSKEY', 'TOTP', 'EMAIL_CODE', 'RECOVERY_CODE')),
  CONSTRAINT ck_identity_auth_session_method_order CHECK (factorOrder > 0),
  CONSTRAINT ck_identity_auth_session_method_uv
    CHECK (userVerification IS NULL OR userVerification IN (FALSE, TRUE))
) ENGINE = InnoDB;

CREATE TABLE security_authenticationWindow (
  id BIGINT AUTO_INCREMENT NOT NULL,
  identifierDigest BINARY(32) NOT NULL,
  keyVersion VARCHAR(32) NOT NULL,
  operation VARCHAR(32) NOT NULL,
  windowStartedAt TIMESTAMP(6) NOT NULL,
  windowEndsAt TIMESTAMP(6) NOT NULL,
  failureCount INT NOT NULL DEFAULT 0,
  turnstileRequiredUntil TIMESTAMP(6) NULL,
  activeMarker BOOLEAN NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_security_authentication_window PRIMARY KEY (id),
  CONSTRAINT uk_security_authentication_window_active
    UNIQUE (identifierDigest, keyVersion, operation, activeMarker),
  CONSTRAINT ck_security_authentication_window_operation
    CHECK (operation IN ('SIGN_IN', 'PASSWORD_RECOVERY')),
  CONSTRAINT ck_security_authentication_window_active CHECK (activeMarker IS NULL OR activeMarker = TRUE),
  CONSTRAINT ck_security_authentication_window_counters
    CHECK (failureCount >= 0 AND version >= 0),
  CONSTRAINT ck_security_authentication_window_expiry CHECK (windowEndsAt > windowStartedAt),
  INDEX idx_security_authentication_window_expiry (windowEndsAt)
) ENGINE = InnoDB;

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260808001' AS version;
