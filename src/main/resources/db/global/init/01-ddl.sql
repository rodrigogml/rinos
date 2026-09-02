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
  globalActorRole VARCHAR(32) NOT NULL DEFAULT 'USER',
  activatedAt TIMESTAMP(6) NULL,
  blockedAt TIMESTAMP(6) NULL,
  deactivatedAt TIMESTAMP(6) NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_identity_user PRIMARY KEY (id),
  CONSTRAINT uk_identity_user_normalized_email UNIQUE (normalizedEmail),
  CONSTRAINT ck_identity_user_global_actor_role CHECK (
    globalActorRole IN ('USER', 'SYSTEM_ADMINISTRATOR')
  ),
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
  passwordChangedAt TIMESTAMP(6) NOT NULL,
  compromisedAt TIMESTAMP(6) NULL,
  lastUsedAt TIMESTAMP(6) NULL,
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
  reference BINARY(16) NOT NULL,
  provider VARCHAR(32) NOT NULL,
  issuer VARCHAR(255) NOT NULL,
  subject VARCHAR(255) NOT NULL,
  status VARCHAR(24) NOT NULL,
  verifiedAt TIMESTAMP(6) NOT NULL,
  activatedAt TIMESTAMP(6) NULL,
  lastUsedAt TIMESTAMP(6) NULL,
  revokedAt TIMESTAMP(6) NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_identity_external_identity PRIMARY KEY (id),
  CONSTRAINT fk_identity_external_identity_user FOREIGN KEY (idUser)
    REFERENCES identity_user (id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,
  CONSTRAINT uk_identity_external_identity_reference UNIQUE (reference),
  CONSTRAINT uk_identity_external_identity_issuer_subject UNIQUE (issuer, subject),
  INDEX idx_identity_external_identity_user (idUser)
) ENGINE = InnoDB;

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
    CHECK (purpose IN ('SIGN_IN', 'REAUTHENTICATION', 'FACTOR_RECOVERY', 'LEGAL_CONSENT',
        'REGISTRATION_ACTIVATION')),
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
  state VARCHAR(24) NOT NULL,
  verifiedAt TIMESTAMP(6) NULL,
  userVerification BOOLEAN NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_identity_authentication_flow_method PRIMARY KEY (id),
  CONSTRAINT fk_identity_authentication_flow_method_flow FOREIGN KEY (idAuthenticationFlow)
    REFERENCES identity_authenticationFlow (id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,
  CONSTRAINT uk_identity_authentication_flow_method UNIQUE (idAuthenticationFlow, method),
  CONSTRAINT ck_identity_authentication_flow_method
    CHECK (method IN ('PASSWORD', 'GOOGLE', 'PASSKEY', 'TOTP', 'EMAIL_CODE', 'RECOVERY_CODE')),
  CONSTRAINT ck_identity_authentication_flow_method_state
    CHECK (state IN ('PERMITTED', 'VERIFIED')),
  CONSTRAINT ck_identity_authentication_flow_method_verification
    CHECK (
      (state = 'PERMITTED' AND verifiedAt IS NULL AND userVerification IS NULL)
      OR (state = 'VERIFIED' AND verifiedAt IS NOT NULL)
    )
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
  enrollmentExpiresAt TIMESTAMP(6) NOT NULL,
  attemptCount INT NOT NULL DEFAULT 0,
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
  CONSTRAINT ck_identity_totp_factor_attempts CHECK (attemptCount >= 0),
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
  idAuthenticationFlow BIGINT NULL,
  publicReference BINARY(16) NOT NULL,
  selectorHash BINARY(32) NOT NULL,
  validatorDigest VARBINARY(96) NOT NULL,
  keyVersion VARCHAR(32) NOT NULL,
  remembered BOOLEAN NOT NULL,
  status VARCHAR(24) NOT NULL,
  primaryMethod VARCHAR(32) NOT NULL,
  assuranceLevel VARCHAR(24) NOT NULL,
  authenticatedAt TIMESTAMP(6) NOT NULL,
  activatedAt TIMESTAMP(6) NULL,
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
  CONSTRAINT fk_identity_auth_session_flow FOREIGN KEY (idAuthenticationFlow)
    REFERENCES identity_authenticationFlow (id)
    ON DELETE SET NULL
    ON UPDATE RESTRICT,
  CONSTRAINT uk_identity_auth_session_reference UNIQUE (publicReference),
  CONSTRAINT uk_identity_auth_session_selector UNIQUE (selectorHash),
  CONSTRAINT uk_identity_auth_session_flow UNIQUE (idAuthenticationFlow),
  CONSTRAINT ck_identity_auth_session_status
    CHECK (status IN ('PREPARED', 'ACTIVE', 'REVOKED', 'EXPIRED')),
  CONSTRAINT ck_identity_auth_session_activation
    CHECK (
      (status = 'PREPARED' AND activatedAt IS NULL)
      OR status IN ('REVOKED', 'EXPIRED')
      OR (status = 'ACTIVE' AND activatedAt IS NOT NULL)
    ),
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

CREATE TABLE identity_reauthenticationContext (
  id BIGINT AUTO_INCREMENT NOT NULL,
  idAuthenticationFlow BIGINT NOT NULL,
  idAuthSession BIGINT NOT NULL,
  operation VARCHAR(48) NOT NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_identity_reauthentication_context PRIMARY KEY (id),
  CONSTRAINT fk_identity_reauthentication_context_flow FOREIGN KEY (idAuthenticationFlow)
    REFERENCES identity_authenticationFlow (id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,
  CONSTRAINT fk_identity_reauthentication_context_session FOREIGN KEY (idAuthSession)
    REFERENCES identity_authSession (id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,
  CONSTRAINT uk_identity_reauthentication_context_flow UNIQUE (idAuthenticationFlow),
  CONSTRAINT ck_identity_reauthentication_context_operation
    CHECK (operation IN (
      'RENAME_PASSKEY', 'REGISTER_PASSKEY', 'CREATE_PASSWORD', 'CHANGE_PASSWORD',
      'REVOKE_PASSKEY', 'ENROLL_FACTOR', 'REMOVE_FACTOR', 'REGENERATE_RECOVERY_CODES',
      'LINK_EXTERNAL_IDENTITY', 'UNLINK_EXTERNAL_IDENTITY', 'REVOKE_SESSION',
      'REVOKE_ALL_SESSIONS'
    )),
  INDEX idx_identity_reauthentication_context_session (idAuthSession)
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

-- Modelo relacional global do controle de acesso.
-- Referencias a tenant e associacao permanecem logicas ate seus modulos publicarem
-- as tabelas canonicas no mesmo schema global.

CREATE TABLE account_tenant (
  idTenant BIGINT AUTO_INCREMENT NOT NULL,
  publicId BINARY(16) NOT NULL,
  status VARCHAR(24) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_account_tenant PRIMARY KEY (idTenant),
  CONSTRAINT uk_account_tenant_public UNIQUE (publicId),
  CONSTRAINT ck_account_tenant_status CHECK (
    status IN ('RESERVED', 'OPERATIONAL', 'SUSPENDED', 'CANCELLED')
  ),
  CONSTRAINT ck_account_tenant_version CHECK (version >= 0),
  INDEX idx_account_tenant_status (status, updatedAt)
) ENGINE = InnoDB;

CREATE TABLE account_account (
  idAccount BIGINT AUTO_INCREMENT NOT NULL,
  publicId BINARY(16) NOT NULL,
  idTenant BIGINT NOT NULL,
  founderUserId BIGINT NOT NULL,
  displayName VARCHAR(160) NOT NULL,
  baseCurrency CHAR(3) NOT NULL,
  timeZoneId VARCHAR(100) NOT NULL,
  status VARCHAR(24) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_account_account PRIMARY KEY (idAccount),
  CONSTRAINT uk_account_account_public UNIQUE (publicId),
  CONSTRAINT uk_account_account_tenant UNIQUE (idTenant),
  CONSTRAINT uk_account_account_tenant_ref UNIQUE (idAccount, idTenant),
  CONSTRAINT fk_account_account_tenant FOREIGN KEY (idTenant)
    REFERENCES account_tenant (idTenant) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_account_account_founder FOREIGN KEY (founderUserId)
    REFERENCES identity_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_account_account_status CHECK (
    status IN ('CREATING', 'ACTIVE', 'SUSPENDED', 'CANCELLED')
  ),
  CONSTRAINT ck_account_account_currency CHECK (
    CHAR_LENGTH(baseCurrency) = 3 AND BINARY baseCurrency = BINARY UPPER(baseCurrency)
  ),
  CONSTRAINT ck_account_account_version CHECK (version >= 0),
  INDEX idx_account_account_founder (founderUserId, status),
  INDEX idx_account_account_status (status, updatedAt)
) ENGINE = InnoDB;

CREATE TABLE account_creationIntent (
  idAccountCreationIntent BIGINT AUTO_INCREMENT NOT NULL,
  publicId BINARY(16) NOT NULL,
  protocolId BINARY(16) NOT NULL,
  creatorUserId BIGINT NOT NULL,
  idempotencyKey BINARY(16) NOT NULL,
  payloadHash BINARY(32) NOT NULL,
  idAccount BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  publicStage VARCHAR(32) NOT NULL,
  failureCode VARCHAR(100) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_account_creation_intent PRIMARY KEY (idAccountCreationIntent),
  CONSTRAINT uk_account_creation_intent_public UNIQUE (publicId),
  CONSTRAINT uk_account_creation_intent_protocol UNIQUE (protocolId),
  CONSTRAINT uk_account_creation_intent_actor_key UNIQUE (creatorUserId, idempotencyKey),
  CONSTRAINT uk_account_creation_intent_account UNIQUE (idAccount),
  CONSTRAINT fk_account_creation_intent_creator FOREIGN KEY (creatorUserId)
    REFERENCES identity_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_account_creation_intent_account FOREIGN KEY (idAccount)
    REFERENCES account_account (idAccount) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_account_creation_intent_status CHECK (
    status IN ('ACCEPTED', 'PROCESSING', 'READY', 'FAILED', 'CANCELLED')
  ),
  CONSTRAINT ck_account_creation_intent_stage CHECK (
    publicStage IN ('ACCEPTED', 'PREPARING', 'FINISHING', 'AVAILABLE', 'ATTENTION')
  ),
  CONSTRAINT ck_account_creation_intent_version CHECK (version >= 0),
  INDEX idx_account_creation_intent_status (status, updatedAt)
) ENGINE = InnoDB;

CREATE TABLE account_provisioningCheckpoint (
  idAccountProvisioningCheckpoint BIGINT AUTO_INCREMENT NOT NULL,
  idAccount BIGINT NOT NULL,
  stepType VARCHAR(32) NOT NULL,
  status VARCHAR(24) NOT NULL,
  externalReference VARCHAR(200) NULL,
  attemptCount INT NOT NULL DEFAULT 0,
  nextAttemptAt TIMESTAMP(6) NULL,
  failureCode VARCHAR(100) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_account_provisioning_checkpoint PRIMARY KEY (idAccountProvisioningCheckpoint),
  CONSTRAINT uk_account_provisioning_checkpoint UNIQUE (idAccount, stepType),
  CONSTRAINT fk_account_checkpoint_account FOREIGN KEY (idAccount)
    REFERENCES account_account (idAccount) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_account_checkpoint_step CHECK (
    stepType IN ('STORAGE', 'FOUNDING_MEMBERSHIP', 'ACCESS_BOOTSTRAP', 'DEFAULT_PLAN')
  ),
  CONSTRAINT ck_account_checkpoint_status CHECK (
    status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')
  ),
  CONSTRAINT ck_account_checkpoint_attempt CHECK (attemptCount >= 0),
  CONSTRAINT ck_account_checkpoint_version CHECK (version >= 0),
  INDEX idx_account_checkpoint_dispatch (status, nextAttemptAt)
) ENGINE = InnoDB;

CREATE TABLE account_outboxEvent (
  idAccountOutboxEvent BIGINT AUTO_INCREMENT NOT NULL,
  eventId BINARY(16) NOT NULL,
  aggregateType VARCHAR(40) NOT NULL,
  aggregateId BIGINT NOT NULL,
  eventType VARCHAR(80) NOT NULL,
  payload JSON NOT NULL,
  status VARCHAR(24) NOT NULL,
  attemptCount INT NOT NULL DEFAULT 0,
  nextAttemptAt TIMESTAMP(6) NULL,
  leaseOwner VARCHAR(100) NULL,
  leaseUntil TIMESTAMP(6) NULL,
  publishedAt TIMESTAMP(6) NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_account_outbox_event PRIMARY KEY (idAccountOutboxEvent),
  CONSTRAINT uk_account_outbox_event UNIQUE (eventId),
  CONSTRAINT ck_account_outbox_aggregate CHECK (aggregateType = 'ACCOUNT'),
  CONSTRAINT ck_account_outbox_status CHECK (
    status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED')
  ),
  CONSTRAINT ck_account_outbox_attempt CHECK (attemptCount >= 0),
  CONSTRAINT ck_account_outbox_lease CHECK (
    (leaseOwner IS NULL AND leaseUntil IS NULL)
    OR (leaseOwner IS NOT NULL AND leaseUntil IS NOT NULL)
  ),
  INDEX idx_account_outbox_dispatch (status, nextAttemptAt, leaseUntil)
) ENGINE = InnoDB;

CREATE TABLE account_auditEvent (
  idAccountAuditEvent BIGINT AUTO_INCREMENT NOT NULL,
  eventType VARCHAR(80) NOT NULL,
  idAccount BIGINT NULL,
  idTenant BIGINT NULL,
  actorUserId BIGINT NULL,
  systemOrigin VARCHAR(100) NULL,
  correlationId VARCHAR(100) NOT NULL,
  safeResultCode VARCHAR(100) NOT NULL,
  details JSON NULL,
  occurredAt TIMESTAMP(6) NOT NULL,
  CONSTRAINT pk_account_audit_event PRIMARY KEY (idAccountAuditEvent),
  CONSTRAINT fk_account_audit_account FOREIGN KEY (idAccount)
    REFERENCES account_account (idAccount) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_account_audit_tenant FOREIGN KEY (idTenant)
    REFERENCES account_tenant (idTenant) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_account_audit_actor FOREIGN KEY (actorUserId)
    REFERENCES identity_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_account_audit_origin CHECK (
    (actorUserId IS NOT NULL AND systemOrigin IS NULL)
    OR (actorUserId IS NULL AND systemOrigin IS NOT NULL)
  ),
  INDEX idx_account_audit_account (idAccount, occurredAt),
  INDEX idx_account_audit_tenant (idTenant, occurredAt)
) ENGINE = InnoDB;

CREATE TABLE membership_accountMembership (
  idAccountMembership BIGINT AUTO_INCREMENT NOT NULL,
  publicId BINARY(16) NOT NULL,
  idAccount BIGINT NOT NULL,
  idUser BIGINT NOT NULL,
  roleType VARCHAR(32) NOT NULL,
  originType VARCHAR(24) NOT NULL,
  status VARCHAR(24) NOT NULL,
  currentMarker TINYINT NULL,
  startedAt TIMESTAMP(6) NOT NULL,
  endedAt TIMESTAMP(6) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_membership_account_membership PRIMARY KEY (idAccountMembership),
  CONSTRAINT uk_membership_account_membership_public UNIQUE (publicId),
  CONSTRAINT uk_membership_account_membership_current UNIQUE (idAccount, idUser, currentMarker),
  CONSTRAINT uk_membership_account_user_ref UNIQUE (
    idAccountMembership, idAccount, idUser
  ),
  CONSTRAINT fk_membership_account_membership_account FOREIGN KEY (idAccount)
    REFERENCES account_account (idAccount) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_membership_account_membership_user FOREIGN KEY (idUser)
    REFERENCES identity_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_membership_role CHECK (
    roleType IN ('COLLABORATOR', 'ACCOUNTANT', 'EXTERNAL_PARTNER', 'ACCOUNT_ADMINISTRATOR')
  ),
  CONSTRAINT ck_membership_origin CHECK (originType IN ('FOUNDER', 'INVITATION')),
  CONSTRAINT ck_membership_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'LEFT', 'REMOVED')),
  CONSTRAINT ck_membership_current CHECK (
    (status IN ('ACTIVE', 'SUSPENDED') AND currentMarker = 1 AND endedAt IS NULL)
    OR (status IN ('LEFT', 'REMOVED') AND currentMarker IS NULL AND endedAt IS NOT NULL)
  ),
  CONSTRAINT ck_membership_version CHECK (version >= 0),
  INDEX idx_membership_user_active (idUser, status),
  INDEX idx_membership_account_active (idAccount, status)
) ENGINE = InnoDB;

CREATE TABLE membership_invitation (
  idMembershipInvitation BIGINT AUTO_INCREMENT NOT NULL,
  publicId BINARY(16) NOT NULL,
  idAccount BIGINT NOT NULL,
  inviterMembershipId BIGINT NOT NULL,
  normalizedEmail VARCHAR(320) NOT NULL,
  proposedRoleType VARCHAR(32) NOT NULL,
  proofDigest BINARY(32) NOT NULL,
  proofKeyId VARCHAR(100) NOT NULL,
  status VARCHAR(24) NOT NULL,
  pendingMarker TINYINT NULL,
  expiresAt TIMESTAMP(6) NOT NULL,
  consumedByUserId BIGINT NULL,
  consumedAt TIMESTAMP(6) NULL,
  sendCount INT NOT NULL DEFAULT 1,
  version BIGINT NOT NULL DEFAULT 0,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_membership_invitation PRIMARY KEY (idMembershipInvitation),
  CONSTRAINT uk_membership_invitation_public UNIQUE (publicId),
  CONSTRAINT uk_membership_invitation_pending UNIQUE (idAccount, normalizedEmail, pendingMarker),
  CONSTRAINT uk_membership_invitation_account_ref UNIQUE (
    idMembershipInvitation, idAccount
  ),
  CONSTRAINT fk_membership_invitation_account FOREIGN KEY (idAccount)
    REFERENCES account_account (idAccount) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_membership_invitation_inviter FOREIGN KEY (inviterMembershipId)
    REFERENCES membership_accountMembership (idAccountMembership)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_membership_invitation_consumer FOREIGN KEY (consumedByUserId)
    REFERENCES identity_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_membership_invitation_role CHECK (
    proposedRoleType IN ('COLLABORATOR', 'ACCOUNTANT', 'EXTERNAL_PARTNER', 'ACCOUNT_ADMINISTRATOR')
  ),
  CONSTRAINT ck_membership_invitation_status CHECK (
    status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED', 'EXPIRED', 'SUPERSEDED')
  ),
  CONSTRAINT ck_membership_invitation_pending CHECK (
    (status = 'PENDING' AND pendingMarker = 1 AND consumedAt IS NULL AND consumedByUserId IS NULL)
    OR (status <> 'PENDING' AND pendingMarker IS NULL)
  ),
  CONSTRAINT ck_membership_invitation_consumption CHECK (
    (status IN ('ACCEPTED', 'DECLINED') AND consumedAt IS NOT NULL)
    OR (status NOT IN ('ACCEPTED', 'DECLINED') AND consumedAt IS NULL AND consumedByUserId IS NULL)
  ),
  CONSTRAINT ck_membership_invitation_send_count CHECK (sendCount > 0),
  CONSTRAINT ck_membership_invitation_version CHECK (version >= 0),
  INDEX idx_membership_invitation_expiry (status, expiresAt),
  INDEX idx_membership_invitation_email (normalizedEmail, status)
) ENGINE = InnoDB;

CREATE TABLE membership_invitationRateWindow (
  idMembershipInvitationRateWindow BIGINT AUTO_INCREMENT NOT NULL,
  dimensionType VARCHAR(24) NOT NULL,
  dimensionKey VARBINARY(320) NOT NULL,
  activeMarker BOOLEAN NULL,
  windowStartedAt TIMESTAMP(6) NOT NULL,
  windowEndsAt TIMESTAMP(6) NOT NULL,
  eventCount INT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_membership_invitation_rate PRIMARY KEY (idMembershipInvitationRateWindow),
  CONSTRAINT uk_membership_invitation_rate UNIQUE (dimensionType, dimensionKey, activeMarker),
  CONSTRAINT ck_membership_invitation_rate_dimension CHECK (
    dimensionType IN ('ACCOUNT', 'INVITER', 'RECIPIENT', 'ORIGIN')
  ),
  CONSTRAINT ck_membership_invitation_rate_active CHECK (activeMarker IS NULL OR activeMarker = TRUE),
  CONSTRAINT ck_membership_invitation_rate_window CHECK (windowEndsAt > windowStartedAt),
  CONSTRAINT ck_membership_invitation_rate_count CHECK (eventCount >= 0),
  CONSTRAINT ck_membership_invitation_rate_version CHECK (version >= 0),
  INDEX idx_membership_invitation_rate_expiry (activeMarker, windowEndsAt)
) ENGINE = InnoDB;

CREATE TABLE membership_outboxEvent (
  idMembershipOutboxEvent BIGINT AUTO_INCREMENT NOT NULL,
  eventId BINARY(16) NOT NULL,
  aggregateType VARCHAR(40) NOT NULL,
  aggregateId BIGINT NOT NULL,
  eventType VARCHAR(80) NOT NULL,
  payload JSON NOT NULL,
  status VARCHAR(24) NOT NULL,
  attemptCount INT NOT NULL DEFAULT 0,
  secretCiphertext VARBINARY(512) NULL,
  secretNonce BINARY(12) NULL,
  secretKeyId VARCHAR(32) NULL,
  secretExpiresAt TIMESTAMP(6) NULL,
  nextAttemptAt TIMESTAMP(6) NULL,
  leaseOwner VARCHAR(100) NULL,
  leaseUntil TIMESTAMP(6) NULL,
  publishedAt TIMESTAMP(6) NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_membership_outbox PRIMARY KEY (idMembershipOutboxEvent),
  CONSTRAINT uk_membership_outbox_event UNIQUE (eventId),
  CONSTRAINT ck_membership_outbox_aggregate CHECK (
    aggregateType IN ('MEMBERSHIP', 'INVITATION')
  ),
  CONSTRAINT ck_membership_outbox_status CHECK (
    status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED', 'CANCELLED')
  ),
  CONSTRAINT ck_membership_outbox_attempt CHECK (attemptCount >= 0),
  CONSTRAINT ck_membership_outbox_lease CHECK (
    (leaseOwner IS NULL AND leaseUntil IS NULL)
    OR (leaseOwner IS NOT NULL AND leaseUntil IS NOT NULL)
  ),
  CONSTRAINT ck_membership_outbox_secret CHECK (
    (secretCiphertext IS NULL AND secretNonce IS NULL AND secretKeyId IS NULL AND secretExpiresAt IS NULL)
    OR (secretCiphertext IS NOT NULL AND secretNonce IS NOT NULL AND secretKeyId IS NOT NULL AND secretExpiresAt IS NOT NULL)
  ),
  INDEX idx_membership_outbox_dispatch (status, nextAttemptAt, leaseUntil)
) ENGINE = InnoDB;

CREATE TABLE membership_event (
  idMembershipEvent BIGINT AUTO_INCREMENT NOT NULL,
  eventType VARCHAR(80) NOT NULL,
  idAccount BIGINT NOT NULL,
  idAccountMembership BIGINT NULL,
  idMembershipInvitation BIGINT NULL,
  actorUserId BIGINT NULL,
  systemOrigin VARCHAR(100) NULL,
  correlationId VARCHAR(100) NOT NULL,
  safeResultCode VARCHAR(100) NOT NULL,
  details JSON NULL,
  occurredAt TIMESTAMP(6) NOT NULL,
  CONSTRAINT pk_membership_event PRIMARY KEY (idMembershipEvent),
  CONSTRAINT fk_membership_event_account FOREIGN KEY (idAccount)
    REFERENCES account_account (idAccount) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_membership_event_membership FOREIGN KEY (idAccountMembership)
    REFERENCES membership_accountMembership (idAccountMembership)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_membership_event_invitation FOREIGN KEY (idMembershipInvitation)
    REFERENCES membership_invitation (idMembershipInvitation)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_membership_event_actor FOREIGN KEY (actorUserId)
    REFERENCES identity_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_membership_event_origin CHECK (
    (actorUserId IS NOT NULL AND systemOrigin IS NULL)
    OR (actorUserId IS NULL AND systemOrigin IS NOT NULL)
  ),
  INDEX idx_membership_event_account (idAccount, occurredAt),
  INDEX idx_membership_event_membership (idAccountMembership, occurredAt)
) ENGINE = InnoDB;

CREATE TABLE access_keyCategory (
  idAccessKeyCategory BIGINT AUTO_INCREMENT NOT NULL,
  categoryCode VARCHAR(160) NOT NULL,
  parentIdAccessKeyCategory BIGINT NULL,
  scopeType VARCHAR(16) NOT NULL,
  nameI18nKey VARCHAR(200) NOT NULL,
  descriptionI18nKey VARCHAR(200) NOT NULL,
  displayOrder INT NOT NULL DEFAULT 0,
  status VARCHAR(24) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_access_key_category PRIMARY KEY (idAccessKeyCategory),
  CONSTRAINT uk_access_key_category_code UNIQUE (categoryCode),
  CONSTRAINT fk_access_key_category_parent FOREIGN KEY (parentIdAccessKeyCategory)
    REFERENCES access_keyCategory (idAccessKeyCategory)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_access_key_category_scope CHECK (scopeType IN ('GLOBAL', 'TENANT')),
  CONSTRAINT ck_access_key_category_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
  CONSTRAINT ck_access_key_category_values CHECK (displayOrder >= 0 AND version >= 0),
  INDEX idx_access_key_category_navigation (scopeType, status, parentIdAccessKeyCategory, displayOrder)
) ENGINE = InnoDB;

CREATE TABLE access_key (
  idAccessKey BIGINT AUTO_INCREMENT NOT NULL,
  accessKeyCode VARCHAR(200) NOT NULL,
  scopeType VARCHAR(16) NOT NULL,
  idAccessKeyCategory BIGINT NOT NULL,
  ownerModule VARCHAR(100) NOT NULL,
  nameI18nKey VARCHAR(200) NOT NULL,
  descriptionI18nKey VARCHAR(200) NOT NULL,
  entitlementScope VARCHAR(16) NULL,
  entitlementCode VARCHAR(200) NULL,
  status VARCHAR(24) NOT NULL,
  descriptorVersion INT NOT NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_access_key PRIMARY KEY (idAccessKey),
  CONSTRAINT uk_access_key_code UNIQUE (accessKeyCode),
  CONSTRAINT fk_access_key_category FOREIGN KEY (idAccessKeyCategory)
    REFERENCES access_keyCategory (idAccessKeyCategory)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_access_key_scope CHECK (scopeType IN ('GLOBAL', 'TENANT')),
  CONSTRAINT ck_access_key_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
  CONSTRAINT ck_access_key_entitlement CHECK (
    (entitlementScope IS NULL AND entitlementCode IS NULL)
    OR (entitlementScope IN ('PERSONAL', 'TENANT') AND entitlementCode IS NOT NULL)
  ),
  CONSTRAINT ck_access_key_descriptor_version CHECK (descriptorVersion > 0),
  INDEX idx_access_key_catalog (scopeType, status, ownerModule, idAccessKeyCategory)
) ENGINE = InnoDB;

CREATE TABLE access_keyRequirement (
  idAccessKeyRequirement BIGINT AUTO_INCREMENT NOT NULL,
  idAccessKey BIGINT NOT NULL,
  featureCode VARCHAR(100) NOT NULL,
  requirementCode VARCHAR(100) NOT NULL,
  CONSTRAINT pk_access_key_requirement PRIMARY KEY (idAccessKeyRequirement),
  CONSTRAINT uk_access_key_requirement UNIQUE (idAccessKey, featureCode, requirementCode),
  CONSTRAINT fk_access_key_requirement_key FOREIGN KEY (idAccessKey)
    REFERENCES access_key (idAccessKey)
    ON DELETE CASCADE ON UPDATE RESTRICT,
  INDEX idx_access_key_requirement_trace (featureCode, requirementCode)
) ENGINE = InnoDB;

CREATE TABLE access_contextRevision (
  idAccessContextRevision BIGINT AUTO_INCREMENT NOT NULL,
  scopeType VARCHAR(16) NOT NULL,
  idTenant BIGINT NULL,
  contextDiscriminator BIGINT GENERATED ALWAYS AS
    (CASE WHEN scopeType = 'GLOBAL' THEN 0 ELSE idTenant END) STORED,
  revision BIGINT NOT NULL DEFAULT 0,
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_access_context_revision PRIMARY KEY (idAccessContextRevision),
  CONSTRAINT uk_access_context_revision UNIQUE (scopeType, contextDiscriminator),
  CONSTRAINT ck_access_context_revision_scope CHECK (
    (scopeType = 'GLOBAL' AND idTenant IS NULL)
    OR (scopeType = 'TENANT' AND idTenant IS NOT NULL AND idTenant > 0)
  ),
  CONSTRAINT ck_access_context_revision_value CHECK (revision >= 0)
) ENGINE = InnoDB;

CREATE TABLE access_group (
  idAccessGroup BIGINT AUTO_INCREMENT NOT NULL,
  scopeType VARCHAR(16) NOT NULL,
  idTenant BIGINT NULL,
  contextDiscriminator BIGINT GENERATED ALWAYS AS
    (CASE WHEN scopeType = 'GLOBAL' THEN 0 ELSE idTenant END) STORED,
  name VARCHAR(160) NOT NULL,
  normalizedName VARCHAR(160) NOT NULL,
  description VARCHAR(500) NULL,
  status VARCHAR(24) NOT NULL,
  protectedGroup BOOLEAN NOT NULL DEFAULT FALSE,
  baselineVersion INT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_access_group PRIMARY KEY (idAccessGroup),
  CONSTRAINT uk_access_group_name UNIQUE (scopeType, contextDiscriminator, normalizedName),
  CONSTRAINT ck_access_group_scope CHECK (
    (scopeType = 'GLOBAL' AND idTenant IS NULL)
    OR (scopeType = 'TENANT' AND idTenant IS NOT NULL AND idTenant > 0)
  ),
  CONSTRAINT ck_access_group_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
  CONSTRAINT ck_access_group_baseline CHECK (
    (protectedGroup = TRUE AND baselineVersion IS NOT NULL AND baselineVersion > 0)
    OR (protectedGroup = FALSE AND baselineVersion IS NULL)
  ),
  CONSTRAINT ck_access_group_version CHECK (version >= 0),
  INDEX idx_access_group_context (scopeType, contextDiscriminator, status)
) ENGINE = InnoDB;

CREATE TABLE access_protectedGroupBaseline (
  idProtectedGroupBaseline BIGINT AUTO_INCREMENT NOT NULL,
  scopeType VARCHAR(16) NOT NULL,
  baselineVersion INT NOT NULL,
  status VARCHAR(24) NOT NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_access_protected_baseline PRIMARY KEY (idProtectedGroupBaseline),
  CONSTRAINT uk_access_protected_baseline UNIQUE (scopeType, baselineVersion),
  CONSTRAINT ck_access_protected_baseline_scope CHECK (scopeType IN ('GLOBAL', 'TENANT')),
  CONSTRAINT ck_access_protected_baseline_status CHECK (status IN ('ACTIVE', 'SUPERSEDED')),
  CONSTRAINT ck_access_protected_baseline_version CHECK (baselineVersion > 0)
) ENGINE = InnoDB;

CREATE TABLE access_protectedGroupBaselineKey (
  idProtectedGroupBaseline BIGINT NOT NULL,
  idAccessKey BIGINT NOT NULL,
  CONSTRAINT pk_access_protected_baseline_key
    PRIMARY KEY (idProtectedGroupBaseline, idAccessKey),
  CONSTRAINT fk_access_baseline_key_baseline FOREIGN KEY (idProtectedGroupBaseline)
    REFERENCES access_protectedGroupBaseline (idProtectedGroupBaseline)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_access_baseline_key_key FOREIGN KEY (idAccessKey)
    REFERENCES access_key (idAccessKey)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  INDEX idx_access_baseline_key_key (idAccessKey)
) ENGINE = InnoDB;

CREATE TABLE access_groupSubject (
  idAccessGroupSubject BIGINT AUTO_INCREMENT NOT NULL,
  idAccessGroup BIGINT NOT NULL,
  idUser BIGINT NULL,
  idAccountMembership BIGINT NULL,
  status VARCHAR(24) NOT NULL,
  validFrom TIMESTAMP(6) NULL,
  validUntil TIMESTAMP(6) NULL,
  createdByUserId BIGINT NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_access_group_subject PRIMARY KEY (idAccessGroupSubject),
  CONSTRAINT uk_access_group_subject_user UNIQUE (idAccessGroup, idUser),
  CONSTRAINT uk_access_group_subject_membership UNIQUE (idAccessGroup, idAccountMembership),
  CONSTRAINT fk_access_group_subject_group FOREIGN KEY (idAccessGroup)
    REFERENCES access_group (idAccessGroup)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_access_group_subject_user FOREIGN KEY (idUser)
    REFERENCES identity_user (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_access_group_subject_membership FOREIGN KEY (idAccountMembership)
    REFERENCES membership_accountMembership (idAccountMembership)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_access_group_subject_actor FOREIGN KEY (createdByUserId)
    REFERENCES identity_user (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_access_group_subject_origin CHECK (
    (idUser IS NOT NULL AND idAccountMembership IS NULL)
    OR (idUser IS NULL AND idAccountMembership IS NOT NULL AND idAccountMembership > 0)
  ),
  CONSTRAINT ck_access_group_subject_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ENDED')),
  CONSTRAINT ck_access_group_subject_validity CHECK (
    validFrom IS NULL OR validUntil IS NULL OR validUntil > validFrom
  ),
  CONSTRAINT ck_access_group_subject_version CHECK (version >= 0),
  INDEX idx_access_group_subject_user_resolution (idUser, status, validFrom, validUntil),
  INDEX idx_access_group_subject_member_resolution
    (idAccountMembership, status, validFrom, validUntil)
) ENGINE = InnoDB;

CREATE TABLE access_rule (
  idAccessRule BIGINT AUTO_INCREMENT NOT NULL,
  scopeType VARCHAR(16) NOT NULL,
  idTenant BIGINT NULL,
  contextDiscriminator BIGINT GENERATED ALWAYS AS
    (CASE WHEN scopeType = 'GLOBAL' THEN 0 ELSE idTenant END) STORED,
  originType VARCHAR(24) NOT NULL,
  idUser BIGINT NULL,
  idAccountMembership BIGINT NULL,
  idAccessGroup BIGINT NULL,
  idAccessKey BIGINT NOT NULL,
  effect VARCHAR(16) NOT NULL,
  status VARCHAR(24) NOT NULL,
  validFrom TIMESTAMP(6) NULL,
  validUntil TIMESTAMP(6) NULL,
  createdByUserId BIGINT NULL,
  updatedByUserId BIGINT NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_access_rule PRIMARY KEY (idAccessRule),
  CONSTRAINT uk_access_rule_user UNIQUE
    (scopeType, contextDiscriminator, idUser, idAccessKey),
  CONSTRAINT uk_access_rule_membership UNIQUE
    (scopeType, contextDiscriminator, idAccountMembership, idAccessKey),
  CONSTRAINT uk_access_rule_group UNIQUE
    (scopeType, contextDiscriminator, idAccessGroup, idAccessKey),
  CONSTRAINT fk_access_rule_user FOREIGN KEY (idUser)
    REFERENCES identity_user (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_access_rule_membership FOREIGN KEY (idAccountMembership)
    REFERENCES membership_accountMembership (idAccountMembership)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_access_rule_group FOREIGN KEY (idAccessGroup)
    REFERENCES access_group (idAccessGroup)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_access_rule_key FOREIGN KEY (idAccessKey)
    REFERENCES access_key (idAccessKey)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_access_rule_created_actor FOREIGN KEY (createdByUserId)
    REFERENCES identity_user (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_access_rule_updated_actor FOREIGN KEY (updatedByUserId)
    REFERENCES identity_user (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_access_rule_scope CHECK (
    (scopeType = 'GLOBAL' AND idTenant IS NULL)
    OR (scopeType = 'TENANT' AND idTenant IS NOT NULL AND idTenant > 0)
  ),
  CONSTRAINT ck_access_rule_origin CHECK (
    (originType = 'DIRECT_USER' AND scopeType = 'GLOBAL'
      AND idUser IS NOT NULL AND idAccountMembership IS NULL AND idAccessGroup IS NULL)
    OR (originType = 'DIRECT_MEMBERSHIP' AND scopeType = 'TENANT'
      AND idUser IS NULL AND idAccountMembership IS NOT NULL
      AND idAccountMembership > 0 AND idAccessGroup IS NULL)
    OR (originType = 'GROUP' AND idUser IS NULL
      AND idAccountMembership IS NULL AND idAccessGroup IS NOT NULL)
  ),
  CONSTRAINT ck_access_rule_effect CHECK (effect IN ('PERMITIR', 'BLOQUEAR')),
  CONSTRAINT ck_access_rule_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
  CONSTRAINT ck_access_rule_validity CHECK (
    validFrom IS NULL OR validUntil IS NULL OR validUntil > validFrom
  ),
  CONSTRAINT ck_access_rule_version CHECK (version >= 0),
  INDEX idx_access_rule_context_key
    (scopeType, contextDiscriminator, idAccessKey, status, validFrom, validUntil),
  INDEX idx_access_rule_user_resolution
    (idUser, status, validFrom, validUntil, idAccessKey),
  INDEX idx_access_rule_member_resolution
    (idAccountMembership, status, validFrom, validUntil, idAccessKey),
  INDEX idx_access_rule_group_resolution
    (idAccessGroup, status, validFrom, validUntil, idAccessKey)
) ENGINE = InnoDB;

CREATE TABLE access_ruleHistory (
  idAccessRuleHistory BIGINT AUTO_INCREMENT NOT NULL,
  idAccessRule BIGINT NOT NULL,
  changeType VARCHAR(32) NOT NULL,
  previousSnapshot JSON NULL,
  newSnapshot JSON NOT NULL,
  actorUserId BIGINT NULL,
  systemOrigin VARCHAR(100) NULL,
  reason VARCHAR(500) NULL,
  correlationId VARCHAR(100) NOT NULL,
  occurredAt TIMESTAMP(6) NOT NULL,
  CONSTRAINT pk_access_rule_history PRIMARY KEY (idAccessRuleHistory),
  CONSTRAINT fk_access_rule_history_rule FOREIGN KEY (idAccessRule)
    REFERENCES access_rule (idAccessRule)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_access_rule_history_actor FOREIGN KEY (actorUserId)
    REFERENCES identity_user (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_access_rule_history_change CHECK (
    changeType IN ('CREATE', 'EFFECT_CHANGE', 'VALIDITY_CHANGE', 'DEACTIVATE')
  ),
  CONSTRAINT ck_access_rule_history_actor CHECK (
    (actorUserId IS NOT NULL AND systemOrigin IS NULL)
    OR (actorUserId IS NULL AND systemOrigin IS NOT NULL)
  ),
  INDEX idx_access_rule_history_rule_time (idAccessRule, occurredAt),
  INDEX idx_access_rule_history_correlation (correlationId)
) ENGINE = InnoDB;

CREATE TABLE access_bootstrap (
  idAccessBootstrap BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL,
  completedByUserId BIGINT NULL,
  completedAt TIMESTAMP(6) NULL,
  correlationId VARCHAR(100) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_access_bootstrap PRIMARY KEY (idAccessBootstrap),
  CONSTRAINT fk_access_bootstrap_user FOREIGN KEY (completedByUserId)
    REFERENCES identity_user (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_access_bootstrap_singleton CHECK (idAccessBootstrap = 1),
  CONSTRAINT ck_access_bootstrap_status CHECK (status IN ('NEVER_COMPLETED', 'COMPLETED')),
  CONSTRAINT ck_access_bootstrap_state CHECK (
    (status = 'NEVER_COMPLETED' AND completedByUserId IS NULL
      AND completedAt IS NULL AND correlationId IS NULL)
    OR (status = 'COMPLETED' AND completedByUserId IS NOT NULL
      AND completedAt IS NOT NULL AND correlationId IS NOT NULL)
  ),
  CONSTRAINT ck_access_bootstrap_version CHECK (version >= 0)
) ENGINE = InnoDB;

CREATE TABLE access_auditEvent (
  idAccessAuditEvent BIGINT AUTO_INCREMENT NOT NULL,
  eventType VARCHAR(80) NOT NULL,
  scopeType VARCHAR(16) NOT NULL,
  idTenant BIGINT NULL,
  contextDiscriminator BIGINT GENERATED ALWAYS AS
    (CASE WHEN scopeType = 'GLOBAL' THEN 0 ELSE idTenant END) STORED,
  actorUserId BIGINT NULL,
  systemOrigin VARCHAR(100) NULL,
  targetType VARCHAR(80) NOT NULL,
  targetId BIGINT NOT NULL,
  correlationId VARCHAR(100) NOT NULL,
  safeReasonCode VARCHAR(100) NULL,
  details JSON NULL,
  occurredAt TIMESTAMP(6) NOT NULL,
  CONSTRAINT pk_access_audit_event PRIMARY KEY (idAccessAuditEvent),
  CONSTRAINT fk_access_audit_event_actor FOREIGN KEY (actorUserId)
    REFERENCES identity_user (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_access_audit_event_scope CHECK (
    (scopeType = 'GLOBAL' AND idTenant IS NULL)
    OR (scopeType = 'TENANT' AND idTenant IS NOT NULL AND idTenant > 0)
  ),
  CONSTRAINT ck_access_audit_event_actor CHECK (
    (actorUserId IS NOT NULL AND systemOrigin IS NULL)
    OR (actorUserId IS NULL AND systemOrigin IS NOT NULL)
  ),
  INDEX idx_access_audit_context_time (scopeType, contextDiscriminator, occurredAt),
  INDEX idx_access_audit_target_time (targetType, targetId, occurredAt),
  INDEX idx_access_audit_correlation (correlationId)
) ENGINE = InnoDB;

CREATE TABLE plans_plan (
  idPlan BIGINT AUTO_INCREMENT NOT NULL,
  publicId BINARY(16) NOT NULL,
  scopeType VARCHAR(16) NOT NULL,
  planCode VARCHAR(100) NOT NULL,
  nameI18nKey VARCHAR(200) NOT NULL,
  descriptionI18nKey VARCHAR(200) NOT NULL,
  status VARCHAR(24) NOT NULL,
  freePlan BOOLEAN NOT NULL DEFAULT FALSE,
  defaultPlan BOOLEAN NOT NULL DEFAULT FALSE,
  defaultScopeMarker VARCHAR(16) GENERATED ALWAYS AS (
    CASE WHEN defaultPlan = TRUE AND status = 'ACTIVE' THEN scopeType ELSE NULL END
  ) STORED,
  availableFrom TIMESTAMP(6) NULL,
  availableUntil TIMESTAMP(6) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_plans_plan PRIMARY KEY (idPlan),
  CONSTRAINT uk_plans_plan_public UNIQUE (publicId),
  CONSTRAINT uk_plans_plan_code UNIQUE (scopeType, planCode),
  CONSTRAINT uk_plans_plan_scope_id UNIQUE (idPlan, scopeType),
  CONSTRAINT uk_plans_plan_default UNIQUE (defaultScopeMarker),
  CONSTRAINT ck_plans_plan_scope CHECK (scopeType IN ('PERSONAL', 'TENANT')),
  CONSTRAINT ck_plans_plan_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
  CONSTRAINT ck_plans_plan_validity CHECK (
    availableFrom IS NULL OR availableUntil IS NULL OR availableUntil > availableFrom
  ),
  CONSTRAINT ck_plans_plan_version CHECK (version >= 0),
  INDEX idx_plans_plan_catalog (scopeType, status, availableFrom, availableUntil)
) ENGINE = InnoDB;

CREATE TABLE plans_planVersion (
  idPlanVersion BIGINT AUTO_INCREMENT NOT NULL,
  idPlan BIGINT NOT NULL,
  scopeType VARCHAR(16) NOT NULL,
  versionNumber INT NOT NULL,
  status VARCHAR(24) NOT NULL,
  publishedAt TIMESTAMP(6) NULL,
  validFrom TIMESTAMP(6) NULL,
  validUntil TIMESTAMP(6) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_plans_plan_version PRIMARY KEY (idPlanVersion),
  CONSTRAINT uk_plans_plan_version_number UNIQUE (idPlan, versionNumber),
  CONSTRAINT uk_plans_plan_version_scope UNIQUE (idPlanVersion, scopeType),
  CONSTRAINT fk_plans_plan_version_plan FOREIGN KEY (idPlan, scopeType)
    REFERENCES plans_plan (idPlan, scopeType) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_plans_plan_version_scope CHECK (scopeType IN ('PERSONAL', 'TENANT')),
  CONSTRAINT ck_plans_plan_version_status CHECK (
    status IN ('DRAFT', 'PUBLISHED', 'RETIRED')
  ),
  CONSTRAINT ck_plans_plan_version_publish CHECK (
    (status = 'DRAFT' AND publishedAt IS NULL)
    OR (status IN ('PUBLISHED', 'RETIRED') AND publishedAt IS NOT NULL)
  ),
  CONSTRAINT ck_plans_plan_version_validity CHECK (
    validFrom IS NULL OR validUntil IS NULL OR validUntil > validFrom
  ),
  CONSTRAINT ck_plans_plan_version_values CHECK (versionNumber > 0 AND version >= 0),
  INDEX idx_plans_plan_version_effective (idPlan, status, validFrom, validUntil)
) ENGINE = InnoDB;

CREATE TABLE plans_entitlementDefinition (
  idEntitlementDefinition BIGINT AUTO_INCREMENT NOT NULL,
  scopeType VARCHAR(16) NOT NULL,
  entitlementCode VARCHAR(200) NOT NULL,
  ownerModule VARCHAR(100) NOT NULL,
  entitlementType VARCHAR(32) NOT NULL,
  unitCode VARCHAR(40) NULL,
  countingSemantics VARCHAR(100) NULL,
  nameI18nKey VARCHAR(200) NOT NULL,
  descriptionI18nKey VARCHAR(200) NOT NULL,
  status VARCHAR(24) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_plans_entitlement_definition PRIMARY KEY (idEntitlementDefinition),
  CONSTRAINT uk_plans_entitlement_code UNIQUE (scopeType, entitlementCode),
  CONSTRAINT uk_plans_entitlement_scope UNIQUE (idEntitlementDefinition, scopeType),
  CONSTRAINT ck_plans_entitlement_scope CHECK (scopeType IN ('PERSONAL', 'TENANT')),
  CONSTRAINT ck_plans_entitlement_type CHECK (
    entitlementType IN ('AVAILABILITY', 'MAXIMUM_QUANTITY', 'PERIODIC_QUOTA')
  ),
  CONSTRAINT ck_plans_entitlement_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
  CONSTRAINT ck_plans_entitlement_unit CHECK (
    (entitlementType = 'AVAILABILITY' AND unitCode IS NULL AND countingSemantics IS NULL)
    OR (entitlementType <> 'AVAILABILITY' AND unitCode IS NOT NULL)
  ),
  CONSTRAINT ck_plans_entitlement_version CHECK (version >= 0),
  INDEX idx_plans_entitlement_catalog (scopeType, status, ownerModule)
) ENGINE = InnoDB;

CREATE TABLE plans_planVersionEntitlement (
  idPlanVersionEntitlement BIGINT AUTO_INCREMENT NOT NULL,
  idPlanVersion BIGINT NOT NULL,
  idEntitlementDefinition BIGINT NOT NULL,
  scopeType VARCHAR(16) NOT NULL,
  booleanValue BOOLEAN NULL,
  quantityValue BIGINT NULL,
  periodCode VARCHAR(32) NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_plans_plan_version_entitlement PRIMARY KEY (idPlanVersionEntitlement),
  CONSTRAINT uk_plans_plan_version_entitlement UNIQUE (
    idPlanVersion, idEntitlementDefinition
  ),
  CONSTRAINT fk_plans_pve_version FOREIGN KEY (idPlanVersion, scopeType)
    REFERENCES plans_planVersion (idPlanVersion, scopeType)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_plans_pve_definition FOREIGN KEY (idEntitlementDefinition, scopeType)
    REFERENCES plans_entitlementDefinition (idEntitlementDefinition, scopeType)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_plans_pve_scope CHECK (scopeType IN ('PERSONAL', 'TENANT')),
  CONSTRAINT ck_plans_pve_value CHECK (
    (booleanValue IS NOT NULL AND quantityValue IS NULL AND periodCode IS NULL)
    OR (booleanValue IS NULL AND quantityValue IS NOT NULL AND quantityValue >= 0)
  ),
  INDEX idx_plans_pve_definition (idEntitlementDefinition, idPlanVersion)
) ENGINE = InnoDB;

CREATE TABLE plans_serviceContract (
  idServiceContract BIGINT AUTO_INCREMENT NOT NULL,
  publicId BINARY(16) NOT NULL,
  scopeType VARCHAR(16) NOT NULL,
  status VARCHAR(24) NOT NULL,
  startedAt TIMESTAMP(6) NOT NULL,
  endedAt TIMESTAMP(6) NULL,
  sourceType VARCHAR(32) NOT NULL,
  idempotencyKey BINARY(32) NOT NULL,
  correlationId VARCHAR(100) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_plans_service_contract PRIMARY KEY (idServiceContract),
  CONSTRAINT uk_plans_service_contract_public UNIQUE (publicId),
  CONSTRAINT uk_plans_service_contract_scope UNIQUE (idServiceContract, scopeType),
  CONSTRAINT uk_plans_service_contract_key UNIQUE (scopeType, idempotencyKey),
  CONSTRAINT ck_plans_service_contract_scope CHECK (scopeType IN ('PERSONAL', 'TENANT')),
  CONSTRAINT ck_plans_service_contract_status CHECK (
    status IN ('ACTIVE', 'SUSPENDED', 'CANCELLED')
  ),
  CONSTRAINT ck_plans_service_contract_source CHECK (
    sourceType IN ('BOOTSTRAP', 'BACKFILL', 'ADMINISTRATION', 'SYSTEM')
  ),
  CONSTRAINT ck_plans_service_contract_state CHECK (
    (status IN ('ACTIVE', 'SUSPENDED') AND endedAt IS NULL)
    OR (status = 'CANCELLED' AND endedAt IS NOT NULL)
  ),
  CONSTRAINT ck_plans_service_contract_version CHECK (version >= 0),
  INDEX idx_plans_service_contract_status (scopeType, status, updatedAt)
) ENGINE = InnoDB;

CREATE TABLE plans_personalContractHolder (
  idServiceContract BIGINT NOT NULL,
  scopeType VARCHAR(16) NOT NULL DEFAULT 'PERSONAL',
  idUser BIGINT NOT NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_plans_personal_holder PRIMARY KEY (idServiceContract),
  CONSTRAINT uk_plans_personal_holder_user UNIQUE (idUser),
  CONSTRAINT fk_plans_personal_holder_contract FOREIGN KEY (idServiceContract, scopeType)
    REFERENCES plans_serviceContract (idServiceContract, scopeType)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_plans_personal_holder_user FOREIGN KEY (idUser)
    REFERENCES identity_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_plans_personal_holder_scope CHECK (scopeType = 'PERSONAL')
) ENGINE = InnoDB;

CREATE TABLE plans_tenantContractHolder (
  idServiceContract BIGINT NOT NULL,
  scopeType VARCHAR(16) NOT NULL DEFAULT 'TENANT',
  idTenant BIGINT NOT NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_plans_tenant_holder PRIMARY KEY (idServiceContract),
  CONSTRAINT uk_plans_tenant_holder_tenant UNIQUE (idTenant),
  CONSTRAINT uk_plans_tenant_holder_ref UNIQUE (idServiceContract, idTenant, scopeType),
  CONSTRAINT fk_plans_tenant_holder_contract FOREIGN KEY (idServiceContract, scopeType)
    REFERENCES plans_serviceContract (idServiceContract, scopeType)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_plans_tenant_holder_tenant FOREIGN KEY (idTenant)
    REFERENCES account_tenant (idTenant) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_plans_tenant_holder_scope CHECK (scopeType = 'TENANT')
) ENGINE = InnoDB;

CREATE TABLE plans_planAssignment (
  idPlanAssignment BIGINT AUTO_INCREMENT NOT NULL,
  idServiceContract BIGINT NOT NULL,
  idPlanVersion BIGINT NOT NULL,
  scopeType VARCHAR(16) NOT NULL,
  status VARCHAR(24) NOT NULL,
  currentMarker TINYINT NULL,
  startedAt TIMESTAMP(6) NOT NULL,
  endedAt TIMESTAMP(6) NULL,
  sourceType VARCHAR(32) NOT NULL,
  reasonCode VARCHAR(100) NULL,
  idempotencyKey BINARY(16) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_plans_plan_assignment PRIMARY KEY (idPlanAssignment),
  CONSTRAINT uk_plans_assignment_current UNIQUE (idServiceContract, currentMarker),
  CONSTRAINT uk_plans_assignment_idempotency UNIQUE (idServiceContract, idempotencyKey),
  CONSTRAINT fk_plans_assignment_contract FOREIGN KEY (idServiceContract, scopeType)
    REFERENCES plans_serviceContract (idServiceContract, scopeType)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_plans_assignment_version FOREIGN KEY (idPlanVersion, scopeType)
    REFERENCES plans_planVersion (idPlanVersion, scopeType)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_plans_assignment_scope CHECK (scopeType IN ('PERSONAL', 'TENANT')),
  CONSTRAINT ck_plans_assignment_status CHECK (status IN ('ACTIVE', 'ENDED', 'CANCELLED')),
  CONSTRAINT ck_plans_assignment_source CHECK (
    sourceType IN ('BOOTSTRAP', 'BACKFILL', 'ADMINISTRATION', 'SYSTEM')
  ),
  CONSTRAINT ck_plans_assignment_current CHECK (
    (status = 'ACTIVE' AND currentMarker = 1 AND endedAt IS NULL)
    OR (status IN ('ENDED', 'CANCELLED') AND currentMarker IS NULL AND endedAt IS NOT NULL)
  ),
  CONSTRAINT ck_plans_assignment_version CHECK (version >= 0),
  INDEX idx_plans_assignment_effective (idServiceContract, status, startedAt, endedAt)
) ENGINE = InnoDB;

CREATE TABLE plans_tenantUserCapacityOccupancy (
  idTenantUserCapacityOccupancy BIGINT AUTO_INCREMENT NOT NULL,
  idServiceContract BIGINT NOT NULL,
  idTenant BIGINT NOT NULL,
  idAccount BIGINT NOT NULL,
  scopeType VARCHAR(16) NOT NULL DEFAULT 'TENANT',
  idUser BIGINT NOT NULL,
  firstMembershipId BIGINT NULL,
  occupiedAt TIMESTAMP(6) NOT NULL,
  sourceType VARCHAR(32) NOT NULL,
  idempotencyKey BINARY(16) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_plans_tenant_capacity_occupancy PRIMARY KEY (idTenantUserCapacityOccupancy),
  CONSTRAINT uk_plans_tenant_capacity_user UNIQUE (idTenant, idUser),
  CONSTRAINT uk_plans_tenant_capacity_occupancy_key UNIQUE (idServiceContract, idempotencyKey),
  CONSTRAINT uk_plans_capacity_occupancy_ref UNIQUE (
    idTenantUserCapacityOccupancy, idServiceContract, idTenant, idAccount
  ),
  CONSTRAINT fk_plans_capacity_occupancy_holder
    FOREIGN KEY (idServiceContract, idTenant, scopeType)
    REFERENCES plans_tenantContractHolder (idServiceContract, idTenant, scopeType)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_plans_capacity_occupancy_account FOREIGN KEY (idAccount, idTenant)
    REFERENCES account_account (idAccount, idTenant)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_plans_capacity_occupancy_membership
    FOREIGN KEY (firstMembershipId, idAccount, idUser)
    REFERENCES membership_accountMembership (idAccountMembership, idAccount, idUser)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_plans_capacity_occupancy_scope CHECK (scopeType = 'TENANT'),
  CONSTRAINT ck_plans_capacity_occupancy_source CHECK (
    sourceType IN ('FOUNDER', 'INVITATION', 'MANUAL', 'IMPORT', 'BACKFILL')
  ),
  CONSTRAINT ck_plans_capacity_occupancy_version CHECK (version >= 0),
  INDEX idx_plans_capacity_occupancy_contract (idServiceContract, occupiedAt)
) ENGINE = InnoDB;

CREATE TABLE plans_tenantUserCapacityReservation (
  idTenantUserCapacityReservation BIGINT AUTO_INCREMENT NOT NULL,
  idServiceContract BIGINT NOT NULL,
  idTenant BIGINT NOT NULL,
  idAccount BIGINT NOT NULL,
  scopeType VARCHAR(16) NOT NULL DEFAULT 'TENANT',
  idMembershipInvitation BIGINT NOT NULL,
  recipientDigest BINARY(32) NOT NULL,
  recipientKeyId VARCHAR(32) NOT NULL,
  idUser BIGINT NULL,
  status VARCHAR(24) NOT NULL,
  capacityMarker TINYINT NULL,
  expiresAt TIMESTAMP(6) NOT NULL,
  convertedOccupancyId BIGINT NULL,
  idempotencyKey BINARY(16) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_plans_tenant_capacity_reservation PRIMARY KEY (idTenantUserCapacityReservation),
  CONSTRAINT uk_plans_capacity_reservation_invitation UNIQUE (idMembershipInvitation),
  CONSTRAINT uk_plans_capacity_reservation_key UNIQUE (idServiceContract, idempotencyKey),
  CONSTRAINT fk_plans_capacity_reservation_holder
    FOREIGN KEY (idServiceContract, idTenant, scopeType)
    REFERENCES plans_tenantContractHolder (idServiceContract, idTenant, scopeType)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_plans_capacity_reservation_account FOREIGN KEY (idAccount, idTenant)
    REFERENCES account_account (idAccount, idTenant)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_plans_capacity_reservation_invitation
    FOREIGN KEY (idMembershipInvitation, idAccount)
    REFERENCES membership_invitation (idMembershipInvitation, idAccount)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_plans_capacity_reservation_user FOREIGN KEY (idUser)
    REFERENCES identity_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_plans_capacity_reservation_occupancy
    FOREIGN KEY (convertedOccupancyId, idServiceContract, idTenant, idAccount)
    REFERENCES plans_tenantUserCapacityOccupancy (
      idTenantUserCapacityOccupancy, idServiceContract, idTenant, idAccount
    )
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_plans_capacity_reservation_scope CHECK (scopeType = 'TENANT'),
  CONSTRAINT ck_plans_capacity_reservation_status CHECK (
    status IN ('RESERVED', 'CONVERTED', 'RELEASED', 'EXPIRED')
  ),
  CONSTRAINT ck_plans_capacity_reservation_state CHECK (
    (status = 'RESERVED' AND capacityMarker = 1 AND convertedOccupancyId IS NULL)
    OR (status = 'CONVERTED' AND capacityMarker IS NULL AND convertedOccupancyId IS NOT NULL)
    OR (status IN ('RELEASED', 'EXPIRED') AND capacityMarker IS NULL
      AND convertedOccupancyId IS NULL)
  ),
  CONSTRAINT ck_plans_capacity_reservation_version CHECK (version >= 0),
  INDEX idx_plans_capacity_reservation_count (idServiceContract, capacityMarker, expiresAt),
  INDEX idx_plans_capacity_reservation_recipient (idTenant, recipientDigest, status)
) ENGINE = InnoDB;

CREATE TABLE plans_auditEvent (
  idPlansAuditEvent BIGINT AUTO_INCREMENT NOT NULL,
  eventType VARCHAR(80) NOT NULL,
  scopeType VARCHAR(16) NOT NULL,
  idServiceContract BIGINT NULL,
  actorUserId BIGINT NULL,
  systemOrigin VARCHAR(100) NULL,
  correlationId VARCHAR(100) NOT NULL,
  safeResultCode VARCHAR(100) NOT NULL,
  beforeState JSON NULL,
  afterState JSON NULL,
  occurredAt TIMESTAMP(6) NOT NULL,
  CONSTRAINT pk_plans_audit_event PRIMARY KEY (idPlansAuditEvent),
  CONSTRAINT fk_plans_audit_contract FOREIGN KEY (idServiceContract, scopeType)
    REFERENCES plans_serviceContract (idServiceContract, scopeType)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_plans_audit_actor FOREIGN KEY (actorUserId)
    REFERENCES identity_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_plans_audit_scope CHECK (scopeType IN ('PERSONAL', 'TENANT')),
  CONSTRAINT ck_plans_audit_origin CHECK (
    (actorUserId IS NOT NULL AND systemOrigin IS NULL)
    OR (actorUserId IS NULL AND systemOrigin IS NOT NULL)
  ),
  INDEX idx_plans_audit_contract (idServiceContract, occurredAt),
  INDEX idx_plans_audit_correlation (correlationId)
) ENGINE = InnoDB;

CREATE TABLE plans_outboxEvent (
  idPlansOutboxEvent BIGINT AUTO_INCREMENT NOT NULL,
  eventId BINARY(16) NOT NULL,
  aggregateType VARCHAR(40) NOT NULL,
  aggregateId BIGINT NOT NULL,
  eventType VARCHAR(80) NOT NULL,
  payload JSON NOT NULL,
  status VARCHAR(24) NOT NULL,
  attemptCount INT NOT NULL DEFAULT 0,
  nextAttemptAt TIMESTAMP(6) NULL,
  leaseOwner VARCHAR(100) NULL,
  leaseUntil TIMESTAMP(6) NULL,
  publishedAt TIMESTAMP(6) NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_plans_outbox_event PRIMARY KEY (idPlansOutboxEvent),
  CONSTRAINT uk_plans_outbox_event UNIQUE (eventId),
  CONSTRAINT ck_plans_outbox_aggregate CHECK (
    aggregateType IN ('PLAN', 'PLAN_VERSION', 'CONTRACT', 'ASSIGNMENT', 'CAPACITY')
  ),
  CONSTRAINT ck_plans_outbox_status CHECK (
    status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED', 'CANCELLED')
  ),
  CONSTRAINT ck_plans_outbox_attempt CHECK (attemptCount >= 0),
  CONSTRAINT ck_plans_outbox_lease CHECK (
    (leaseOwner IS NULL AND leaseUntil IS NULL)
    OR (leaseOwner IS NOT NULL AND leaseUntil IS NOT NULL)
  ),
  INDEX idx_plans_outbox_dispatch (status, nextAttemptAt, leaseUntil)
) ENGINE = InnoDB;

CREATE TABLE storage_tenantRegistry (
  idTenantStorageRegistry BIGINT AUTO_INCREMENT NOT NULL,
  idTenant BIGINT NOT NULL,
  physicalIdentifier CHAR(32) NOT NULL,
  storageState VARCHAR(24) NOT NULL,
  expectedVersion VARCHAR(32) NOT NULL,
  observedVersion VARCHAR(32) NULL,
  lastValidatedAt TIMESTAMP(6) NULL,
  quarantineReasonCode VARCHAR(100) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_storage_tenant_registry PRIMARY KEY (idTenantStorageRegistry),
  CONSTRAINT uk_storage_tenant_registry_tenant UNIQUE (idTenant),
  CONSTRAINT uk_storage_tenant_registry_physical UNIQUE (physicalIdentifier),
  CONSTRAINT fk_storage_tenant_registry_tenant FOREIGN KEY (idTenant)
    REFERENCES account_tenant (idTenant) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_storage_tenant_registry_physical CHECK (
    physicalIdentifier REGEXP '^[a-f0-9]{32}$'
  ),
  CONSTRAINT ck_storage_tenant_registry_state CHECK (
    storageState IN (
      'REQUESTED', 'PROVISIONING', 'INITIALIZING', 'MIGRATING', 'READY',
      'FAILED', 'QUARANTINED', 'DEACTIVATING', 'INACTIVE'
    )
  ),
  CONSTRAINT ck_storage_tenant_registry_version CHECK (version >= 0),
  INDEX idx_storage_tenant_registry_state (storageState, updatedAt)
) ENGINE = InnoDB;

CREATE TABLE storage_operation (
  idStorageOperation BIGINT AUTO_INCREMENT NOT NULL,
  publicId BINARY(16) NOT NULL,
  idTenantStorageRegistry BIGINT NOT NULL,
  operationType VARCHAR(24) NOT NULL,
  idempotencyReference BINARY(16) NOT NULL,
  operationState VARCHAR(24) NOT NULL,
  activeMarker BOOLEAN NULL,
  attemptCount INT NOT NULL DEFAULT 0,
  nextAttemptAt TIMESTAMP(6) NULL,
  leaseOwner VARCHAR(100) NULL,
  leaseUntil TIMESTAMP(6) NULL,
  correlationId VARCHAR(100) NOT NULL,
  safeFailureCode VARCHAR(100) NULL,
  startedAt TIMESTAMP(6) NULL,
  finishedAt TIMESTAMP(6) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_storage_operation PRIMARY KEY (idStorageOperation),
  CONSTRAINT uk_storage_operation_public UNIQUE (publicId),
  CONSTRAINT uk_storage_operation_idempotency UNIQUE (
    idTenantStorageRegistry, operationType, idempotencyReference
  ),
  CONSTRAINT uk_storage_operation_active UNIQUE (idTenantStorageRegistry, activeMarker),
  CONSTRAINT fk_storage_operation_registry FOREIGN KEY (idTenantStorageRegistry)
    REFERENCES storage_tenantRegistry (idTenantStorageRegistry)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_storage_operation_type CHECK (
    operationType IN ('PROVISION', 'MIGRATE', 'RECONCILE', 'DEACTIVATE')
  ),
  CONSTRAINT ck_storage_operation_state CHECK (
    operationState IN (
      'QUEUED', 'CLAIMED', 'RUNNING', 'RETRY_WAIT', 'COMPLETED', 'FAILED_FINAL', 'CANCELLED'
    )
  ),
  CONSTRAINT ck_storage_operation_active_marker CHECK (
    activeMarker IS NULL OR activeMarker = TRUE
  ),
  CONSTRAINT ck_storage_operation_attempt CHECK (attemptCount >= 0),
  CONSTRAINT ck_storage_operation_lease CHECK (
    (leaseOwner IS NULL AND leaseUntil IS NULL)
    OR (leaseOwner IS NOT NULL AND leaseUntil IS NOT NULL)
  ),
  CONSTRAINT ck_storage_operation_version CHECK (version >= 0),
  INDEX idx_storage_operation_queue (
    operationState, nextAttemptAt, leaseUntil, idStorageOperation
  ),
  INDEX idx_storage_operation_registry (idTenantStorageRegistry, createdAt)
) ENGINE = InnoDB;

CREATE TABLE storage_operationStep (
  idStorageOperationStep BIGINT AUTO_INCREMENT NOT NULL,
  idStorageOperation BIGINT NOT NULL,
  stepType VARCHAR(32) NOT NULL,
  stepState VARCHAR(24) NOT NULL,
  attemptNumber INT NOT NULL DEFAULT 0,
  startedAt TIMESTAMP(6) NULL,
  completedAt TIMESTAMP(6) NULL,
  evidenceHash BINARY(32) NULL,
  safeFailureCode VARCHAR(100) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_storage_operation_step PRIMARY KEY (idStorageOperationStep),
  CONSTRAINT uk_storage_operation_step UNIQUE (idStorageOperation, stepType),
  CONSTRAINT fk_storage_operation_step_operation FOREIGN KEY (idStorageOperation)
    REFERENCES storage_operation (idStorageOperation) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_storage_operation_step_type CHECK (
    stepType IN (
      'RESERVE', 'CREATE_SCHEMA', 'INITIALIZE', 'VERIFY_VERSION', 'MIGRATE',
      'VALIDATE_READINESS', 'RECONCILE', 'DEACTIVATE'
    )
  ),
  CONSTRAINT ck_storage_operation_step_state CHECK (
    stepState IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')
  ),
  CONSTRAINT ck_storage_operation_step_attempt CHECK (attemptNumber >= 0),
  CONSTRAINT ck_storage_operation_step_version CHECK (version >= 0),
  INDEX idx_storage_operation_step_operation (idStorageOperation, stepState)
) ENGINE = InnoDB;

CREATE TABLE storage_migrationExecution (
  idStorageMigrationExecution BIGINT AUTO_INCREMENT NOT NULL,
  idTenantStorageRegistry BIGINT NOT NULL,
  idStorageOperation BIGINT NULL,
  scriptVersion VARCHAR(32) NOT NULL,
  scriptName VARCHAR(160) NOT NULL,
  scriptHash BINARY(32) NOT NULL,
  previousVersion VARCHAR(32) NULL,
  resultingVersion VARCHAR(32) NULL,
  executionState VARCHAR(24) NOT NULL,
  startedAt TIMESTAMP(6) NOT NULL,
  finishedAt TIMESTAMP(6) NULL,
  safeFailureCode VARCHAR(100) NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_storage_migration_execution PRIMARY KEY (idStorageMigrationExecution),
  CONSTRAINT uk_storage_migration_execution_version UNIQUE (
    idTenantStorageRegistry, scriptVersion
  ),
  CONSTRAINT fk_storage_migration_execution_registry FOREIGN KEY (idTenantStorageRegistry)
    REFERENCES storage_tenantRegistry (idTenantStorageRegistry)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_storage_migration_execution_operation FOREIGN KEY (idStorageOperation)
    REFERENCES storage_operation (idStorageOperation) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_storage_migration_execution_state CHECK (
    executionState IN ('STARTED', 'COMPLETED', 'FAILED')
  ),
  INDEX idx_storage_migration_execution_operation (idStorageOperation, startedAt)
) ENGINE = InnoDB;

CREATE TABLE storage_stateTransition (
  idStorageStateTransition BIGINT AUTO_INCREMENT NOT NULL,
  idTenantStorageRegistry BIGINT NOT NULL,
  idStorageOperation BIGINT NULL,
  previousState VARCHAR(24) NULL,
  resultingState VARCHAR(24) NOT NULL,
  stepType VARCHAR(32) NULL,
  originType VARCHAR(24) NOT NULL,
  actorUserId BIGINT NULL,
  systemOrigin VARCHAR(100) NULL,
  correlationId VARCHAR(100) NOT NULL,
  safeResultCode VARCHAR(100) NOT NULL,
  occurredAt TIMESTAMP(6) NOT NULL,
  CONSTRAINT pk_storage_state_transition PRIMARY KEY (idStorageStateTransition),
  CONSTRAINT fk_storage_state_transition_registry FOREIGN KEY (idTenantStorageRegistry)
    REFERENCES storage_tenantRegistry (idTenantStorageRegistry)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_storage_state_transition_operation FOREIGN KEY (idStorageOperation)
    REFERENCES storage_operation (idStorageOperation) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_storage_state_transition_actor FOREIGN KEY (actorUserId)
    REFERENCES identity_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_storage_state_transition_previous CHECK (
    previousState IS NULL OR previousState IN (
      'REQUESTED', 'PROVISIONING', 'INITIALIZING', 'MIGRATING', 'READY',
      'FAILED', 'QUARANTINED', 'DEACTIVATING', 'INACTIVE'
    )
  ),
  CONSTRAINT ck_storage_state_transition_resulting CHECK (
    resultingState IN (
      'REQUESTED', 'PROVISIONING', 'INITIALIZING', 'MIGRATING', 'READY',
      'FAILED', 'QUARANTINED', 'DEACTIVATING', 'INACTIVE'
    )
  ),
  CONSTRAINT ck_storage_state_transition_origin CHECK (
    (originType = 'SYSTEM' AND actorUserId IS NULL AND systemOrigin IS NOT NULL)
    OR (originType = 'GLOBAL_USER' AND actorUserId IS NOT NULL AND systemOrigin IS NULL)
  ),
  INDEX idx_storage_state_transition_registry (idTenantStorageRegistry, occurredAt)
) ENGINE = InnoDB;

CREATE TABLE storage_auditEvent (
  idStorageAuditEvent BIGINT AUTO_INCREMENT NOT NULL,
  eventType VARCHAR(80) NOT NULL,
  idTenantStorageRegistry BIGINT NULL,
  idStorageOperation BIGINT NULL,
  actorUserId BIGINT NULL,
  systemOrigin VARCHAR(100) NULL,
  correlationId VARCHAR(100) NOT NULL,
  safeResultCode VARCHAR(100) NOT NULL,
  details JSON NULL,
  occurredAt TIMESTAMP(6) NOT NULL,
  CONSTRAINT pk_storage_audit_event PRIMARY KEY (idStorageAuditEvent),
  CONSTRAINT fk_storage_audit_event_registry FOREIGN KEY (idTenantStorageRegistry)
    REFERENCES storage_tenantRegistry (idTenantStorageRegistry)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_storage_audit_event_operation FOREIGN KEY (idStorageOperation)
    REFERENCES storage_operation (idStorageOperation) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_storage_audit_event_actor FOREIGN KEY (actorUserId)
    REFERENCES identity_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_storage_audit_event_origin CHECK (
    (actorUserId IS NOT NULL AND systemOrigin IS NULL)
    OR (actorUserId IS NULL AND systemOrigin IS NOT NULL)
  ),
  INDEX idx_storage_audit_event_registry (idTenantStorageRegistry, occurredAt),
  INDEX idx_storage_audit_event_operation (idStorageOperation, occurredAt)
) ENGINE = InnoDB;

INSERT INTO access_contextRevision (scopeType, idTenant, revision)
VALUES ('GLOBAL', NULL, 0);

INSERT INTO access_bootstrap (idAccessBootstrap, status, version)
VALUES (1, 'NEVER_COMPLETED', 0);
