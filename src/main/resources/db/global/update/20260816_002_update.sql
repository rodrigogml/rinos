ALTER TABLE access_key
  ADD COLUMN entitlementScope VARCHAR(16) NULL AFTER descriptionI18nKey,
  ADD CONSTRAINT ck_access_key_entitlement CHECK (
    (entitlementScope IS NULL AND entitlementCode IS NULL)
    OR (entitlementScope IN ('PERSONAL', 'TENANT') AND entitlementCode IS NOT NULL)
  );

ALTER TABLE account_account
  ADD CONSTRAINT uk_account_account_tenant_ref UNIQUE (idAccount, idTenant);

ALTER TABLE membership_accountMembership
  ADD CONSTRAINT uk_membership_account_user_ref UNIQUE (
    idAccountMembership, idAccount, idUser
  );

ALTER TABLE membership_invitation
  ADD CONSTRAINT uk_membership_invitation_account_ref UNIQUE (
    idMembershipInvitation, idAccount
  );

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
  correlationId VARCHAR(100) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updatedAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_plans_service_contract PRIMARY KEY (idServiceContract),
  CONSTRAINT uk_plans_service_contract_public UNIQUE (publicId),
  CONSTRAINT uk_plans_service_contract_scope UNIQUE (idServiceContract, scopeType),
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

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260816002' AS version;
