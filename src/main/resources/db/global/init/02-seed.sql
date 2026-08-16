-- Catálogo mínimo e imutável de planos para uma instalação nova.
-- Identidades, tenants e contratos continuam sendo criados pelos fluxos da aplicação.

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
