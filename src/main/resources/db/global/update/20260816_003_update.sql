ALTER TABLE plans_serviceContract
  ADD COLUMN idempotencyKey BINARY(32) NULL AFTER sourceType;

UPDATE plans_serviceContract
SET idempotencyKey = UNHEX(SHA2(CONCAT('legacy-contract:', idServiceContract), 256))
WHERE idempotencyKey IS NULL;

ALTER TABLE plans_serviceContract
  MODIFY COLUMN idempotencyKey BINARY(32) NOT NULL,
  ADD CONSTRAINT uk_plans_service_contract_key UNIQUE (scopeType, idempotencyKey);

INSERT IGNORE INTO plans_plan
  (publicId, scopeType, planCode, nameI18nKey, descriptionI18nKey,
   status, freePlan, defaultPlan, availableFrom, version)
VALUES
  (UUID_TO_BIN('10000000-0000-4000-8000-000000000001'),
   'PERSONAL', 'FREE', 'plans.personal.free.name', 'plans.personal.free.description',
   'ACTIVE', TRUE, TRUE, CURRENT_TIMESTAMP(6), 0),
  (UUID_TO_BIN('20000000-0000-4000-8000-000000000001'),
   'TENANT', 'FREE', 'plans.tenant.free.name', 'plans.tenant.free.description',
   'ACTIVE', TRUE, TRUE, CURRENT_TIMESTAMP(6), 0);

INSERT IGNORE INTO plans_planVersion
  (idPlan, scopeType, versionNumber, status, publishedAt, validFrom, version)
SELECT idPlan, scopeType, 1, 'PUBLISHED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 0
FROM plans_plan
WHERE (scopeType = 'PERSONAL' AND planCode = 'FREE')
   OR (scopeType = 'TENANT' AND planCode = 'FREE');

INSERT IGNORE INTO plans_entitlementDefinition
  (scopeType, entitlementCode, ownerModule, entitlementType, unitCode,
   countingSemantics, nameI18nKey, descriptionI18nKey, status, version)
VALUES
  ('TENANT', 'membership.associated-users.limit', 'membership',
   'MAXIMUM_QUANTITY', 'DISTINCT_USER', 'EVER_ASSOCIATED',
   'plans.entitlement.membership.associated-users.limit.name',
   'plans.entitlement.membership.associated-users.limit.description', 'ACTIVE', 0);

INSERT IGNORE INTO plans_planVersionEntitlement
  (idPlanVersion, idEntitlementDefinition, scopeType, quantityValue)
SELECT version.idPlanVersion, definition.idEntitlementDefinition, 'TENANT', 10
FROM plans_plan plan
JOIN plans_planVersion version
  ON version.idPlan = plan.idPlan
 AND version.scopeType = plan.scopeType
 AND version.versionNumber = 1
JOIN plans_entitlementDefinition definition
  ON definition.scopeType = 'TENANT'
 AND definition.entitlementCode = 'membership.associated-users.limit'
WHERE plan.scopeType = 'TENANT'
  AND plan.planCode = 'FREE';

INSERT IGNORE INTO plans_serviceContract
  (publicId, scopeType, status, startedAt, sourceType, idempotencyKey,
   correlationId, version)
SELECT UUID_TO_BIN(UUID()), 'PERSONAL', 'ACTIVE', CURRENT_TIMESTAMP(6), 'BACKFILL',
       UNHEX(SHA2(CONCAT('plans:personal:', user.id), 256)),
       CONCAT('plans-personal-backfill-', user.id), 0
FROM identity_user user
LEFT JOIN plans_personalContractHolder existingHolder ON existingHolder.idUser = user.id
WHERE existingHolder.idUser IS NULL;

INSERT IGNORE INTO plans_personalContractHolder
  (idServiceContract, scopeType, idUser)
SELECT contract.idServiceContract, 'PERSONAL', user.id
FROM identity_user user
JOIN plans_serviceContract contract
  ON contract.scopeType = 'PERSONAL'
 AND contract.idempotencyKey = UNHEX(SHA2(CONCAT('plans:personal:', user.id), 256));

INSERT IGNORE INTO plans_serviceContract
  (publicId, scopeType, status, startedAt, sourceType, idempotencyKey,
   correlationId, version)
SELECT UUID_TO_BIN(UUID()), 'TENANT', 'ACTIVE', CURRENT_TIMESTAMP(6), 'BACKFILL',
       UNHEX(SHA2(CONCAT('plans:tenant:', tenant.idTenant), 256)),
       CONCAT('plans-tenant-backfill-', tenant.idTenant), 0
FROM account_tenant tenant
LEFT JOIN plans_tenantContractHolder existingHolder ON existingHolder.idTenant = tenant.idTenant
WHERE existingHolder.idTenant IS NULL;

INSERT IGNORE INTO plans_tenantContractHolder
  (idServiceContract, scopeType, idTenant)
SELECT contract.idServiceContract, 'TENANT', tenant.idTenant
FROM account_tenant tenant
JOIN plans_serviceContract contract
  ON contract.scopeType = 'TENANT'
 AND contract.idempotencyKey = UNHEX(SHA2(CONCAT('plans:tenant:', tenant.idTenant), 256));

INSERT IGNORE INTO plans_planAssignment
  (idServiceContract, idPlanVersion, scopeType, status, currentMarker,
   startedAt, sourceType, reasonCode, idempotencyKey, version)
SELECT holder.idServiceContract, version.idPlanVersion, 'PERSONAL', 'ACTIVE', 1,
       CURRENT_TIMESTAMP(6), 'BACKFILL', 'INITIAL_FREE',
       UNHEX(SUBSTRING(SHA2(CONCAT('plans:assignment:', holder.idServiceContract), 256), 1, 32)), 0
FROM plans_personalContractHolder holder
JOIN plans_plan plan ON plan.scopeType = 'PERSONAL' AND plan.planCode = 'FREE'
JOIN plans_planVersion version
  ON version.idPlan = plan.idPlan AND version.scopeType = 'PERSONAL'
 AND version.versionNumber = 1;

INSERT IGNORE INTO plans_planAssignment
  (idServiceContract, idPlanVersion, scopeType, status, currentMarker,
   startedAt, sourceType, reasonCode, idempotencyKey, version)
SELECT holder.idServiceContract, version.idPlanVersion, 'TENANT', 'ACTIVE', 1,
       CURRENT_TIMESTAMP(6), 'BACKFILL', 'INITIAL_FREE',
       UNHEX(SUBSTRING(SHA2(CONCAT('plans:assignment:', holder.idServiceContract), 256), 1, 32)), 0
FROM plans_tenantContractHolder holder
JOIN plans_plan plan ON plan.scopeType = 'TENANT' AND plan.planCode = 'FREE'
JOIN plans_planVersion version
  ON version.idPlan = plan.idPlan AND version.scopeType = 'TENANT'
 AND version.versionNumber = 1;

INSERT IGNORE INTO plans_tenantUserCapacityOccupancy
  (idServiceContract, idTenant, idAccount, scopeType, idUser, firstMembershipId,
   occupiedAt, sourceType, idempotencyKey, version)
SELECT holder.idServiceContract, account.idTenant, membership.idAccount, 'TENANT',
       membership.idUser, MIN(membership.idAccountMembership),
       MIN(membership.startedAt), 'BACKFILL',
       UNHEX(SUBSTRING(SHA2(CONCAT(
         'plans:occupancy:', account.idTenant, ':', membership.idUser), 256), 1, 32)), 0
FROM membership_accountMembership membership
JOIN account_account account ON account.idAccount = membership.idAccount
JOIN plans_tenantContractHolder holder ON holder.idTenant = account.idTenant
GROUP BY holder.idServiceContract, account.idTenant, membership.idAccount, membership.idUser;

INSERT IGNORE INTO plans_tenantUserCapacityReservation
  (idServiceContract, idTenant, idAccount, scopeType, idMembershipInvitation,
   recipientDigest, recipientKeyId, idUser, status, capacityMarker, expiresAt,
   idempotencyKey, version)
SELECT holder.idServiceContract, account.idTenant, invitation.idAccount, 'TENANT',
       invitation.idMembershipInvitation,
       UNHEX(SHA2(CONCAT(HEX(invitation.proofDigest), ':', invitation.normalizedEmail), 256)),
       invitation.proofKeyId, user.id, 'RESERVED', 1, invitation.expiresAt,
       UNHEX(SUBSTRING(SHA2(CONCAT(
         'plans:reservation:', invitation.idMembershipInvitation), 256), 1, 32)), 0
FROM membership_invitation invitation
JOIN account_account account ON account.idAccount = invitation.idAccount
JOIN plans_tenantContractHolder holder ON holder.idTenant = account.idTenant
LEFT JOIN identity_user user ON user.normalizedEmail = invitation.normalizedEmail
LEFT JOIN plans_tenantUserCapacityOccupancy occupancy
  ON occupancy.idTenant = account.idTenant AND occupancy.idUser = user.id
WHERE invitation.status = 'PENDING'
  AND occupancy.idTenantUserCapacityOccupancy IS NULL;

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260816003' AS version;
