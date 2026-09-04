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

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260815002' AS version;
