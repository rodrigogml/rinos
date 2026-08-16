-- Modelo relacional global do controle de acesso.
-- Referencias a tenant e associacao permanecem logicas ate seus modulos publicarem
-- as tabelas canonicas no mesmo schema global.

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

INSERT INTO access_contextRevision (scopeType, idTenant, revision)
VALUES ('GLOBAL', NULL, 0);

INSERT INTO access_bootstrap (idAccessBootstrap, status, version)
VALUES (1, 'NEVER_COMPLETED', 0);

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260815001' AS version;
