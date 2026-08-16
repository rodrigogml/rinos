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

ALTER TABLE access_groupSubject
  ADD CONSTRAINT fk_access_group_subject_membership
  FOREIGN KEY (idAccountMembership)
  REFERENCES membership_accountMembership (idAccountMembership)
  ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE access_rule
  ADD CONSTRAINT fk_access_rule_membership
  FOREIGN KEY (idAccountMembership)
  REFERENCES membership_accountMembership (idAccountMembership)
  ON DELETE RESTRICT ON UPDATE RESTRICT;

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260815003' AS version;
