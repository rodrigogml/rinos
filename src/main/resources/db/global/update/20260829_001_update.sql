-- Arquivo de atualização do banco de dados, não pode ser alterado por agentes de IA.

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

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260829001' AS version;
