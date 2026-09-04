package br.com.rinos.app.api.module.access.keys;

import java.util.LinkedHashSet;
import java.util.Set;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.access.vo.AccessKeyDescriptor;
import br.com.rinos.app.api.module.access.vo.AccessKeyRequirement;

/** Baseline v1 tipada das chaves pertencentes aos módulos consumidores. */
public final class InitialModuleAccessKeys {

  public static final AccessKeyDescriptor GLOBAL_DIRECTORY_USER_VIEW = key(
      "global.directory.user.view", "system-directory-administration", "global.platform.directory", true,
      requirements("system-directory-administration",
          "FR-SDA-CTX-001",
          "FR-SDA-CTX-002",
          "FR-SDA-CTX-003",
          "FR-SDA-CTX-004",
          "FR-SDA-CTX-005",
          "FR-SDA-CTX-006",
          "FR-SDA-CTX-007",
          "FR-SDA-CTX-008",
          "FR-SDA-CTX-009",
          "FR-SDA-DIR-001",
          "FR-SDA-DIR-002",
          "FR-SDA-DIR-003",
          "FR-SDA-DIR-004",
          "FR-SDA-DIR-005",
          "FR-SDA-DIR-006",
          "FR-SDA-DIR-007",
          "FR-SDA-DIR-008",
          "FR-SDA-DIR-009",
          "FR-SDA-DIR-010",
          "FR-SDA-DIR-011",
          "FR-SDA-DIR-012"
      ));

  public static final AccessKeyDescriptor GLOBAL_DIRECTORY_ACCOUNT_VIEW = key(
      "global.directory.account.view", "system-directory-administration", "global.platform.directory", true,
      requirements("system-directory-administration",
          "FR-SDA-CTX-001",
          "FR-SDA-CTX-002",
          "FR-SDA-CTX-003",
          "FR-SDA-CTX-004",
          "FR-SDA-CTX-005",
          "FR-SDA-CTX-006",
          "FR-SDA-CTX-007",
          "FR-SDA-CTX-008",
          "FR-SDA-CTX-009",
          "FR-SDA-DIR-001",
          "FR-SDA-DIR-002",
          "FR-SDA-DIR-003",
          "FR-SDA-DIR-004",
          "FR-SDA-DIR-005",
          "FR-SDA-DIR-006",
          "FR-SDA-DIR-007",
          "FR-SDA-DIR-008",
          "FR-SDA-DIR-009",
          "FR-SDA-DIR-010",
          "FR-SDA-DIR-011",
          "FR-SDA-DIR-012"
      ));

  public static final AccessKeyDescriptor GLOBAL_DIRECTORY_IDENTITY_BLOCK = key(
      "global.directory.identity.block", "system-directory-administration", "global.platform.directory", true,
      requirements("system-directory-administration",
          "FR-SDA-AUD-001",
          "FR-SDA-AUD-002",
          "FR-SDA-AUD-003",
          "FR-SDA-AUD-004",
          "FR-SDA-AUD-005",
          "FR-SDA-AUD-006",
          "FR-SDA-AUD-007",
          "FR-SDA-AUD-008",
          "FR-SDA-AUD-009",
          "FR-SDA-USER-001",
          "FR-SDA-USER-002",
          "FR-SDA-USER-003",
          "FR-SDA-USER-004",
          "FR-SDA-USER-005",
          "FR-SDA-USER-006",
          "FR-SDA-USER-007",
          "FR-SDA-USER-008",
          "FR-SDA-USER-009",
          "FR-SDA-USER-010",
          "FR-SDA-USER-011",
          "FR-SDA-USER-012",
          "FR-SDA-USER-013",
          "FR-SDA-USER-014",
          "FR-SDA-USER-015",
          "FR-SDA-USER-016",
          "FR-SDA-USER-017",
          "FR-SDA-USER-018",
          "FR-SDA-USER-019",
          "FR-SDA-USER-020",
          "FR-SDA-USER-021"
      ));

  public static final AccessKeyDescriptor GLOBAL_DIRECTORY_ACCOUNT_INTERVENE = key(
      "global.directory.account.intervene", "system-directory-administration", "global.platform.directory", true,
      requirements("system-directory-administration",
          "FR-SDA-ACC-001",
          "FR-SDA-ACC-002",
          "FR-SDA-ACC-003",
          "FR-SDA-ACC-004",
          "FR-SDA-ACC-005",
          "FR-SDA-ACC-006",
          "FR-SDA-ACC-007",
          "FR-SDA-ACC-008",
          "FR-SDA-ACC-009",
          "FR-SDA-ACC-010",
          "FR-SDA-ACC-011",
          "FR-SDA-ACC-012",
          "FR-SDA-AUD-001",
          "FR-SDA-AUD-002",
          "FR-SDA-AUD-003",
          "FR-SDA-AUD-004",
          "FR-SDA-AUD-005",
          "FR-SDA-AUD-006",
          "FR-SDA-AUD-007",
          "FR-SDA-AUD-008",
          "FR-SDA-AUD-009"
      ));

  public static final AccessKeyDescriptor GLOBAL_DIRECTORY_ACCOUNT_RECOVER = key(
      "global.directory.account.recover", "system-directory-administration", "global.platform.directory", true,
      requirements("system-directory-administration",
          "FR-SDA-AUD-001",
          "FR-SDA-AUD-002",
          "FR-SDA-AUD-003",
          "FR-SDA-AUD-004",
          "FR-SDA-AUD-005",
          "FR-SDA-AUD-006",
          "FR-SDA-AUD-007",
          "FR-SDA-AUD-008",
          "FR-SDA-AUD-009",
          "FR-SDA-REC-001",
          "FR-SDA-REC-002",
          "FR-SDA-REC-003",
          "FR-SDA-REC-004",
          "FR-SDA-REC-005",
          "FR-SDA-REC-006",
          "FR-SDA-REC-007",
          "FR-SDA-REC-008",
          "FR-SDA-REC-009",
          "FR-SDA-REC-010",
          "FR-SDA-REC-011",
          "FR-SDA-REC-012",
          "FR-SDA-REC-013",
          "FR-SDA-REC-014",
          "FR-SDA-REC-015",
          "FR-SDA-REC-016",
          "FR-SDA-REC-017",
          "FR-SDA-REC-018",
          "FR-SDA-REC-019",
          "FR-SDA-REC-020",
          "FR-SDA-REC-021",
          "FR-SDA-REC-022",
          "FR-SDA-REC-023",
          "FR-SDA-REC-024",
          "FR-SDA-REC-025",
          "FR-SDA-REC-026",
          "FR-SDA-REC-027",
          "FR-SDA-REC-028",
          "FR-SDA-REC-029"
      ));

  public static final AccessKeyDescriptor GLOBAL_PLATFORM_CONFIGURATION_VIEW = key(
      "global.platform.configuration.view", "platform-configuration", "global.platform.operations", true,
      requirements("platform-configuration",
          "FR-PC-DEF-001",
          "FR-PC-DEF-002",
          "FR-PC-DEF-003",
          "FR-PC-DEF-004",
          "FR-PC-DEF-005",
          "FR-PC-DEF-006",
          "FR-PC-DEF-007",
          "FR-PC-DEF-008",
          "FR-PC-DEF-009",
          "FR-PC-DEF-010",
          "FR-PC-DEF-011",
          "FR-PC-DEF-012"
      ));

  public static final AccessKeyDescriptor GLOBAL_PLATFORM_CONFIGURATION_MANAGE = key(
      "global.platform.configuration.manage", "platform-configuration", "global.platform.operations", true,
      requirements("platform-configuration",
          "FR-PC-ACT-001",
          "FR-PC-ACT-002",
          "FR-PC-ACT-003",
          "FR-PC-ACT-004",
          "FR-PC-ACT-005",
          "FR-PC-ACT-006",
          "FR-PC-ACT-007",
          "FR-PC-ACT-008",
          "FR-PC-ACT-009",
          "FR-PC-ACT-010",
          "FR-PC-ACT-011",
          "FR-PC-ACT-012",
          "FR-PC-ACT-013",
          "FR-PC-ADM-001",
          "FR-PC-ADM-002",
          "FR-PC-ADM-003",
          "FR-PC-ADM-004",
          "FR-PC-ADM-005",
          "FR-PC-ADM-006",
          "FR-PC-ADM-007",
          "FR-PC-ADM-008",
          "FR-PC-ADM-009",
          "FR-PC-ADM-010",
          "FR-PC-ADM-011",
          "FR-PC-ADM-012",
          "FR-PC-AUD-001",
          "FR-PC-AUD-002",
          "FR-PC-AUD-003",
          "FR-PC-AUD-004",
          "FR-PC-AUD-005",
          "FR-PC-AUD-006",
          "FR-PC-AUD-007",
          "FR-PC-AUD-008",
          "FR-PC-AUD-009",
          "FR-PC-AUD-010",
          "FR-PC-AUD-011",
          "FR-PC-BOUND-001",
          "FR-PC-BOUND-002",
          "FR-PC-BOUND-003",
          "FR-PC-BOUND-004",
          "FR-PC-BOUND-005",
          "FR-PC-BOUND-006",
          "FR-PC-BOUND-007",
          "FR-PC-BOUND-008",
          "FR-PC-BOUND-009",
          "FR-PC-DEF-001",
          "FR-PC-DEF-002",
          "FR-PC-DEF-003",
          "FR-PC-DEF-004",
          "FR-PC-DEF-005",
          "FR-PC-DEF-006",
          "FR-PC-DEF-007",
          "FR-PC-DEF-008",
          "FR-PC-DEF-009",
          "FR-PC-DEF-010",
          "FR-PC-DEF-011",
          "FR-PC-DEF-012",
          "FR-PC-INFRA-BACKUP",
          "FR-PC-INFRA-CLOCK",
          "FR-PC-INFRA-IDEMP",
          "FR-PC-INFRA-LOCK",
          "FR-PC-INFRA-RECOVERY",
          "FR-PC-INFRA-SCHED",
          "FR-PC-RES-001",
          "FR-PC-RES-002",
          "FR-PC-RES-003",
          "FR-PC-RES-004",
          "FR-PC-RES-005",
          "FR-PC-RES-006",
          "FR-PC-RES-007",
          "FR-PC-RES-008",
          "FR-PC-RES-009",
          "FR-PC-RES-010",
          "FR-PC-RES-011",
          "FR-PC-RES-012",
          "FR-PC-RES-013",
          "FR-PC-RES-014",
          "FR-PC-RES-015",
          "FR-PC-SCOPE-001",
          "FR-PC-SCOPE-002",
          "FR-PC-SCOPE-003",
          "FR-PC-SCOPE-004",
          "FR-PC-SCOPE-005",
          "FR-PC-SCOPE-006",
          "FR-PC-SCOPE-007",
          "FR-PC-SCOPE-008",
          "FR-PC-SCOPE-009",
          "FR-PC-SCOPE-010",
          "FR-PC-SCOPE-011",
          "FR-PC-SCOPE-012",
          "FR-PC-SCOPE-013",
          "FR-PC-SCOPE-014",
          "FR-PC-SCOPE-015",
          "FR-PC-VER-001",
          "FR-PC-VER-002",
          "FR-PC-VER-003",
          "FR-PC-VER-004",
          "FR-PC-VER-005",
          "FR-PC-VER-006",
          "FR-PC-VER-007",
          "FR-PC-VER-008",
          "FR-PC-VER-009",
          "FR-PC-VER-010",
          "FR-PC-VER-011",
          "FR-PC-VER-012"
      ));

  public static final AccessKeyDescriptor GLOBAL_PLATFORM_OPERATION_VIEW = key(
      "global.platform.operation.view", "platform-operations", "global.platform.operations", true,
      requirements("platform-operations",
          "FR-PO-CTX-001",
          "FR-PO-CTX-002",
          "FR-PO-CTX-003",
          "FR-PO-CTX-004",
          "FR-PO-CTX-005",
          "FR-PO-CTX-006",
          "FR-PO-CTX-007",
          "FR-PO-CTX-008"
      ));

  public static final AccessKeyDescriptor GLOBAL_PLATFORM_OPERATION_MANAGE = key(
      "global.platform.operation.manage", "platform-operations", "global.platform.operations", true,
      requirements("platform-operations",
          "FR-PO-ALERT-001",
          "FR-PO-ALERT-002",
          "FR-PO-ALERT-003",
          "FR-PO-ALERT-004",
          "FR-PO-ALERT-005",
          "FR-PO-ALERT-006",
          "FR-PO-ALERT-007",
          "FR-PO-ALERT-008",
          "FR-PO-ALERT-009",
          "FR-PO-ALERT-010",
          "FR-PO-ALERT-011",
          "FR-PO-ALERT-012",
          "FR-PO-ALERT-013",
          "FR-PO-ALERT-014",
          "FR-PO-ALERT-015",
          "FR-PO-ALERT-016",
          "FR-PO-ALERT-017",
          "FR-PO-BOUND-001",
          "FR-PO-BOUND-002",
          "FR-PO-BOUND-003",
          "FR-PO-BOUND-004",
          "FR-PO-BOUND-005",
          "FR-PO-BOUND-006",
          "FR-PO-BOUND-007",
          "FR-PO-BOUND-008",
          "FR-PO-BOUND-009",
          "FR-PO-BOUND-010",
          "FR-PO-CTX-001",
          "FR-PO-CTX-002",
          "FR-PO-CTX-003",
          "FR-PO-CTX-004",
          "FR-PO-CTX-005",
          "FR-PO-CTX-006",
          "FR-PO-CTX-007",
          "FR-PO-CTX-008",
          "FR-PO-HEALTH-001",
          "FR-PO-HEALTH-002",
          "FR-PO-HEALTH-003",
          "FR-PO-HEALTH-004",
          "FR-PO-HEALTH-005",
          "FR-PO-HEALTH-006",
          "FR-PO-HEALTH-007",
          "FR-PO-HEALTH-008",
          "FR-PO-HEALTH-009",
          "FR-PO-HEALTH-010",
          "FR-PO-HEALTH-011",
          "FR-PO-INFRA-CAPACITY",
          "FR-PO-INFRA-CLOCK",
          "FR-PO-INFRA-DRAIN",
          "FR-PO-INFRA-IDEMP",
          "FR-PO-INFRA-LEADER",
          "FR-PO-INFRA-LOCK",
          "FR-PO-INFRA-RECOVERY",
          "FR-PO-INFRA-SCHED",
          "FR-PO-MAINT-001",
          "FR-PO-MAINT-002",
          "FR-PO-MAINT-003",
          "FR-PO-MAINT-004",
          "FR-PO-MAINT-005",
          "FR-PO-MAINT-006",
          "FR-PO-MAINT-007",
          "FR-PO-MAINT-008",
          "FR-PO-MAINT-009",
          "FR-PO-MAINT-010",
          "FR-PO-MAINT-011",
          "FR-PO-MAINT-012",
          "FR-PO-MAINT-013",
          "FR-PO-MAINT-014",
          "FR-PO-MAINT-015",
          "FR-PO-MAINT-016",
          "FR-PO-MAINT-017",
          "FR-PO-MIG-001",
          "FR-PO-MIG-002",
          "FR-PO-MIG-003",
          "FR-PO-MIG-004",
          "FR-PO-MIG-005",
          "FR-PO-MIG-006",
          "FR-PO-MIG-007",
          "FR-PO-MIG-008",
          "FR-PO-MIG-009",
          "FR-PO-MIG-010",
          "FR-PO-MIG-011",
          "FR-PO-MIG-012",
          "FR-PO-MIG-013",
          "FR-PO-MIG-014",
          "FR-PO-MIG-015",
          "FR-PO-MIG-016",
          "FR-PO-MIG-017",
          "FR-PO-MIG-018",
          "FR-PO-MIG-019",
          "FR-PO-MIG-020",
          "FR-PO-MIG-021",
          "FR-PO-PROV-001",
          "FR-PO-PROV-002",
          "FR-PO-PROV-003",
          "FR-PO-PROV-004",
          "FR-PO-PROV-005",
          "FR-PO-PROV-006",
          "FR-PO-PROV-007",
          "FR-PO-PROV-008",
          "FR-PO-PROV-009",
          "FR-PO-PROV-010",
          "FR-PO-PROV-011",
          "FR-PO-PROV-012",
          "FR-PO-PROV-013",
          "FR-PO-PROV-014",
          "FR-PO-PROV-015",
          "FR-PO-PROV-016",
          "FR-PO-PROV-017",
          "FR-PO-PROV-018",
          "FR-PO-PROV-019",
          "FR-PO-PROV-020",
          "FR-PO-PROV-021",
          "FR-PO-PROV-022",
          "FR-PO-PROV-023"
      ));

  public static final AccessKeyDescriptor GLOBAL_PLATFORM_PROVISIONING_MANAGE = key(
      "global.platform.provisioning.manage", "tenant-storage-provisioning", "global.platform.operations", true,
      requirements("tenant-storage-provisioning",
          "FR-TSP-ID-001",
          "FR-TSP-ID-002",
          "FR-TSP-ID-003",
          "FR-TSP-ID-004",
          "FR-TSP-ID-005",
          "FR-TSP-ID-006",
          "FR-TSP-ID-007",
          "FR-TSP-ID-008"
      ));

  public static final AccessKeyDescriptor GLOBAL_PLATFORM_AUDIT_VIEW = key(
      "global.platform.audit.view", "tenant-data-governance", "global.platform.operations", true,
      requirements("tenant-data-governance",
          "FR-TDG-AUD-001",
          "FR-TDG-AUD-002",
          "FR-TDG-AUD-003",
          "FR-TDG-AUD-004",
          "FR-TDG-AUD-005",
          "FR-TDG-AUD-006",
          "FR-TDG-AUD-007",
          "FR-TDG-AUD-008",
          "FR-TDG-AUD-009",
          "FR-TDG-AUD-010",
          "FR-TDG-AUD-011",
          "FR-TDG-AUD-012"
      ));

  public static final AccessKeyDescriptor GLOBAL_PLATFORM_SUPPORT_OPERATE = key(
      "global.platform.support.operate", "tenant-support-access", "global.platform.operations", true,
      requirements("tenant-support-access",
          "FR-TSA-ID-001",
          "FR-TSA-ID-002",
          "FR-TSA-ID-003",
          "FR-TSA-ID-004",
          "FR-TSA-ID-005",
          "FR-TSA-ID-006",
          "FR-TSA-ID-007"
      ));

  public static final AccessKeyDescriptor GLOBAL_PLAN_CATALOG_VIEW = key(
      "global.plan.catalog.view", "plans-entitlements", "global.platform.commercial", true,
      requirements("plans-entitlements",
          "FR-PE-001",
          "FR-PE-002",
          "FR-PE-003",
          "FR-PE-004",
          "FR-PE-005",
          "FR-PE-006",
          "FR-PE-007",
          "FR-PE-008",
          "FR-PE-009",
          "FR-PE-010",
          "FR-PE-011",
          "FR-PE-012",
          "FR-PE-013",
          "FR-PE-014",
          "FR-PE-015",
          "FR-PE-016",
          "FR-PE-017",
          "FR-PE-018",
          "FR-PE-ADM-001",
          "FR-PE-ADM-002",
          "FR-PE-ADM-003",
          "FR-PE-ADM-004",
          "FR-PE-ADM-005",
          "FR-PE-ADM-006",
          "FR-PE-ADM-007",
          "FR-PE-ADM-008",
          "FR-PE-ADM-009",
          "FR-PE-ASG-001",
          "FR-PE-ASG-002",
          "FR-PE-ASG-003",
          "FR-PE-ASG-004",
          "FR-PE-ASG-005",
          "FR-PE-ASG-006",
          "FR-PE-ASG-007",
          "FR-PE-ASG-008",
          "FR-PE-ASG-009",
          "FR-PE-ASG-010",
          "FR-PE-ASG-011",
          "FR-PE-ASG-012",
          "FR-PE-ASG-013",
          "FR-PE-ASG-014",
          "FR-PE-ASG-015",
          "FR-PE-ASG-016",
          "FR-PE-ASG-017",
          "FR-PE-ASG-018",
          "FR-PE-ASG-019",
          "FR-PE-ASG-020",
          "FR-PE-ENT-001",
          "FR-PE-ENT-002",
          "FR-PE-ENT-003",
          "FR-PE-ENT-004",
          "FR-PE-ENT-005",
          "FR-PE-ENT-006",
          "FR-PE-ENT-007",
          "FR-PE-ENT-008",
          "FR-PE-ENT-009",
          "FR-PE-ENT-010",
          "FR-PE-ENT-011",
          "FR-PE-ENT-012",
          "FR-PE-ENT-013",
          "FR-PE-EVAL-001",
          "FR-PE-EVAL-002",
          "FR-PE-EVAL-003",
          "FR-PE-EVAL-004",
          "FR-PE-EVAL-005",
          "FR-PE-EVAL-006",
          "FR-PE-EVAL-007",
          "FR-PE-EVAL-008",
          "FR-PE-EVAL-009",
          "FR-PE-EVAL-010",
          "FR-PE-EVAL-011",
          "FR-PE-EVAL-012",
          "FR-PE-EVAL-013",
          "FR-PE-EVAL-014",
          "FR-PE-EVAL-015",
          "FR-PE-EVAL-016",
          "FR-PE-EVO-001",
          "FR-PE-EVO-002",
          "FR-PE-EVO-003",
          "FR-PE-EVO-004",
          "FR-PE-EVO-005",
          "FR-PE-EVO-006",
          "FR-PE-EVO-007",
          "FR-PE-EVO-008",
          "FR-PE-INFRA-BACKUP",
          "FR-PE-INFRA-CLOCK",
          "FR-PE-INFRA-IDEMP",
          "FR-PE-INFRA-LOCK",
          "FR-PE-INFRA-SCHED",
          "FR-PE-PLAN-001",
          "FR-PE-PLAN-002",
          "FR-PE-PLAN-003",
          "FR-PE-PLAN-004",
          "FR-PE-PLAN-005",
          "FR-PE-PLAN-006",
          "FR-PE-PLAN-007",
          "FR-PE-PLAN-008",
          "FR-PE-PLAN-009",
          "FR-PE-PLAN-010",
          "FR-PE-PLAN-011",
          "FR-PE-PLAN-012",
          "FR-PE-PLAN-013",
          "FR-PE-PLAN-014",
          "FR-PE-USG-001",
          "FR-PE-USG-002",
          "FR-PE-USG-003",
          "FR-PE-USG-004",
          "FR-PE-USG-005",
          "FR-PE-USG-006",
          "FR-PE-USG-007",
          "FR-PE-USG-008",
          "FR-PE-USG-009",
          "FR-PE-USG-010",
          "FR-PE-USG-011",
          "FR-PE-USG-012",
          "FR-PE-USG-013",
          "FR-PE-USG-014",
          "FR-PE-USG-015",
          "FR-PE-USG-016",
          "FR-PE-USG-017",
          "FR-PE-USG-018",
          "FR-PE-USG-019",
          "FR-PE-USG-020",
          "FR-PE-USG-021",
          "FR-PE-USG-022",
          "FR-PE-USG-023",
          "FR-PE-USG-024",
          "FR-PE-USG-025",
          "FR-PE-USG-026",
          "FR-PE-VER-001",
          "FR-PE-VER-002",
          "FR-PE-VER-003",
          "FR-PE-VER-004",
          "FR-PE-VER-005",
          "FR-PE-VER-006",
          "FR-PE-VER-007",
          "FR-PE-VER-008",
          "FR-PE-VER-009",
          "FR-PE-VER-010",
          "FR-PE-VER-011",
          "FR-PE-VER-012"
      ));

  public static final AccessKeyDescriptor GLOBAL_PLAN_CATALOG_MANAGE = key(
      "global.plan.catalog.manage", "plans-entitlements", "global.platform.commercial", true,
      requirements("plans-entitlements",
          "FR-PE-001",
          "FR-PE-002",
          "FR-PE-003",
          "FR-PE-004",
          "FR-PE-005",
          "FR-PE-006",
          "FR-PE-007",
          "FR-PE-008",
          "FR-PE-009",
          "FR-PE-010",
          "FR-PE-011",
          "FR-PE-012",
          "FR-PE-013",
          "FR-PE-014",
          "FR-PE-015",
          "FR-PE-016",
          "FR-PE-017",
          "FR-PE-018",
          "FR-PE-ADM-001",
          "FR-PE-ADM-002",
          "FR-PE-ADM-003",
          "FR-PE-ADM-004",
          "FR-PE-ADM-005",
          "FR-PE-ADM-006",
          "FR-PE-ADM-007",
          "FR-PE-ADM-008",
          "FR-PE-ADM-009",
          "FR-PE-ASG-001",
          "FR-PE-ASG-002",
          "FR-PE-ASG-003",
          "FR-PE-ASG-004",
          "FR-PE-ASG-005",
          "FR-PE-ASG-006",
          "FR-PE-ASG-007",
          "FR-PE-ASG-008",
          "FR-PE-ASG-009",
          "FR-PE-ASG-010",
          "FR-PE-ASG-011",
          "FR-PE-ASG-012",
          "FR-PE-ASG-013",
          "FR-PE-ASG-014",
          "FR-PE-ASG-015",
          "FR-PE-ASG-016",
          "FR-PE-ASG-017",
          "FR-PE-ASG-018",
          "FR-PE-ASG-019",
          "FR-PE-ASG-020",
          "FR-PE-ENT-001",
          "FR-PE-ENT-002",
          "FR-PE-ENT-003",
          "FR-PE-ENT-004",
          "FR-PE-ENT-005",
          "FR-PE-ENT-006",
          "FR-PE-ENT-007",
          "FR-PE-ENT-008",
          "FR-PE-ENT-009",
          "FR-PE-ENT-010",
          "FR-PE-ENT-011",
          "FR-PE-ENT-012",
          "FR-PE-ENT-013",
          "FR-PE-EVAL-001",
          "FR-PE-EVAL-002",
          "FR-PE-EVAL-003",
          "FR-PE-EVAL-004",
          "FR-PE-EVAL-005",
          "FR-PE-EVAL-006",
          "FR-PE-EVAL-007",
          "FR-PE-EVAL-008",
          "FR-PE-EVAL-009",
          "FR-PE-EVAL-010",
          "FR-PE-EVAL-011",
          "FR-PE-EVAL-012",
          "FR-PE-EVAL-013",
          "FR-PE-EVAL-014",
          "FR-PE-EVAL-015",
          "FR-PE-EVAL-016",
          "FR-PE-EVO-001",
          "FR-PE-EVO-002",
          "FR-PE-EVO-003",
          "FR-PE-EVO-004",
          "FR-PE-EVO-005",
          "FR-PE-EVO-006",
          "FR-PE-EVO-007",
          "FR-PE-EVO-008",
          "FR-PE-INFRA-BACKUP",
          "FR-PE-INFRA-CLOCK",
          "FR-PE-INFRA-IDEMP",
          "FR-PE-INFRA-LOCK",
          "FR-PE-INFRA-SCHED",
          "FR-PE-PLAN-001",
          "FR-PE-PLAN-002",
          "FR-PE-PLAN-003",
          "FR-PE-PLAN-004",
          "FR-PE-PLAN-005",
          "FR-PE-PLAN-006",
          "FR-PE-PLAN-007",
          "FR-PE-PLAN-008",
          "FR-PE-PLAN-009",
          "FR-PE-PLAN-010",
          "FR-PE-PLAN-011",
          "FR-PE-PLAN-012",
          "FR-PE-PLAN-013",
          "FR-PE-PLAN-014",
          "FR-PE-USG-001",
          "FR-PE-USG-002",
          "FR-PE-USG-003",
          "FR-PE-USG-004",
          "FR-PE-USG-005",
          "FR-PE-USG-006",
          "FR-PE-USG-007",
          "FR-PE-USG-008",
          "FR-PE-USG-009",
          "FR-PE-USG-010",
          "FR-PE-USG-011",
          "FR-PE-USG-012",
          "FR-PE-USG-013",
          "FR-PE-USG-014",
          "FR-PE-USG-015",
          "FR-PE-USG-016",
          "FR-PE-USG-017",
          "FR-PE-USG-018",
          "FR-PE-USG-019",
          "FR-PE-USG-020",
          "FR-PE-USG-021",
          "FR-PE-USG-022",
          "FR-PE-USG-023",
          "FR-PE-USG-024",
          "FR-PE-USG-025",
          "FR-PE-USG-026",
          "FR-PE-VER-001",
          "FR-PE-VER-002",
          "FR-PE-VER-003",
          "FR-PE-VER-004",
          "FR-PE-VER-005",
          "FR-PE-VER-006",
          "FR-PE-VER-007",
          "FR-PE-VER-008",
          "FR-PE-VER-009",
          "FR-PE-VER-010",
          "FR-PE-VER-011",
          "FR-PE-VER-012"
      ));

  public static final AccessKeyDescriptor GLOBAL_PLAN_ASSIGNMENT_MANAGE = key(
      "global.plan.assignment.manage", "plans-entitlements", "global.platform.commercial", true,
      requirements("plans-entitlements",
          "FR-PE-ADM-001",
          "FR-PE-ASG-001",
          "FR-PE-ASG-002",
          "FR-PE-ASG-003",
          "FR-PE-ASG-004",
          "FR-PE-ASG-005",
          "FR-PE-ASG-006",
          "FR-PE-ASG-007",
          "FR-PE-ASG-008",
          "FR-PE-ASG-009",
          "FR-PE-ASG-010",
          "FR-PE-ASG-011",
          "FR-PE-ASG-012",
          "FR-PE-ASG-013",
          "FR-PE-ASG-014",
          "FR-PE-ASG-015",
          "FR-PE-ASG-016",
          "FR-PE-ASG-017",
          "FR-PE-ASG-018",
          "FR-PE-ASG-019",
          "FR-PE-ASG-020"
      ));

  public static final AccessKeyDescriptor TENANT_ACCOUNT_VIEW = key(
      "tenant.account.view", "account-registration", "tenant.foundation", true,
      requirements("account-registration",
          "FR-ACC-001",
          "FR-ACC-002",
          "FR-ACC-003",
          "FR-ACC-004",
          "FR-ACC-005",
          "FR-ACC-006",
          "FR-ACC-007",
          "FR-ACC-008",
          "FR-ACC-009",
          "FR-ACC-010",
          "FR-ACC-ABUSE-001",
          "FR-ACC-ABUSE-002",
          "FR-ACC-ABUSE-003",
          "FR-ACC-ABUSE-004",
          "FR-ACC-ABUSE-005",
          "FR-ACC-ABUSE-006",
          "FR-ACC-ABUSE-007",
          "FR-ACC-ABUSE-008",
          "FR-ACC-ABUSE-009",
          "FR-ACC-BOUND-001",
          "FR-ACC-BOUND-002",
          "FR-ACC-BOUND-003",
          "FR-ACC-BOUND-004",
          "FR-ACC-BOUND-005",
          "FR-ACC-BOUND-006",
          "FR-ACC-CREATE-001",
          "FR-ACC-CREATE-002",
          "FR-ACC-CREATE-003",
          "FR-ACC-CREATE-004",
          "FR-ACC-CREATE-005",
          "FR-ACC-CREATE-006",
          "FR-ACC-CREATE-007",
          "FR-ACC-CREATE-008",
          "FR-ACC-CREATE-009",
          "FR-ACC-CREATE-010",
          "FR-ACC-CREATE-011",
          "FR-ACC-CREATE-012",
          "FR-ACC-CREATE-013",
          "FR-ACC-CREATE-014",
          "FR-ACC-INFRA-BACKUP",
          "FR-ACC-INFRA-IDEMP",
          "FR-ACC-INFRA-LOCK",
          "FR-ACC-INFRA-SCHED",
          "FR-ACC-MAINT-001",
          "FR-ACC-MAINT-002",
          "FR-ACC-MAINT-003",
          "FR-ACC-MAINT-004",
          "FR-ACC-MAINT-005",
          "FR-ACC-MAINT-006",
          "FR-ACC-MAINT-007",
          "FR-ACC-MAINT-008",
          "FR-ACC-MAINT-009",
          "FR-ACC-PLAN-001",
          "FR-ACC-PLAN-002",
          "FR-ACC-PLAN-003",
          "FR-ACC-PLAN-004",
          "FR-ACC-PLAN-005",
          "FR-ACC-PLAN-006",
          "FR-ACC-PLAN-007",
          "FR-ACC-STATE-001",
          "FR-ACC-STATE-002",
          "FR-ACC-STATE-003",
          "FR-ACC-STATE-004",
          "FR-ACC-STATE-005",
          "FR-ACC-STATE-006",
          "FR-ACC-STATE-007",
          "FR-ACC-STATE-008",
          "FR-ACC-STATE-009"
      ));

  public static final AccessKeyDescriptor TENANT_ACCOUNT_UPDATE = key(
      "tenant.account.update", "account-registration", "tenant.foundation", true,
      requirements("account-registration",
          "FR-ACC-MAINT-001",
          "FR-ACC-MAINT-002",
          "FR-ACC-MAINT-003",
          "FR-ACC-MAINT-004",
          "FR-ACC-MAINT-005",
          "FR-ACC-MAINT-006",
          "FR-ACC-MAINT-007",
          "FR-ACC-MAINT-008"
      ));

  public static final AccessKeyDescriptor TENANT_ACCOUNT_LIFECYCLE_MANAGE = key(
      "tenant.account.lifecycle.manage", "account-registration", "tenant.foundation", false,
      requirements("account-registration",
          "FR-ACC-STATE-001",
          "FR-ACC-STATE-002",
          "FR-ACC-STATE-003",
          "FR-ACC-STATE-004",
          "FR-ACC-STATE-005",
          "FR-ACC-STATE-006",
          "FR-ACC-STATE-007",
          "FR-ACC-STATE-008",
          "FR-ACC-STATE-009"
      ));

  public static final AccessKeyDescriptor TENANT_MEMBERSHIP_VIEW = key(
      "tenant.membership.view", "account-membership", "tenant.foundation", true,
      requirements("account-membership",
          "FR-MEM-008",
          "FR-MEM-LIFE-001"
      ));

  public static final AccessKeyDescriptor TENANT_MEMBERSHIP_INVITE = key(
      "tenant.membership.invite", "account-membership", "tenant.foundation", true,
      requirements("account-membership",
          "FR-MEM-ACCEPT-001",
          "FR-MEM-ACCEPT-002",
          "FR-MEM-ACCEPT-003",
          "FR-MEM-ACCEPT-004",
          "FR-MEM-ACCEPT-005",
          "FR-MEM-ACCEPT-006",
          "FR-MEM-ACCEPT-007",
          "FR-MEM-ACCEPT-008",
          "FR-MEM-INV-001",
          "FR-MEM-INV-002",
          "FR-MEM-INV-003",
          "FR-MEM-INV-004",
          "FR-MEM-INV-005",
          "FR-MEM-INV-006",
          "FR-MEM-INV-007",
          "FR-MEM-INV-008",
          "FR-MEM-INV-009",
          "FR-MEM-INV-010",
          "FR-MEM-INV-011",
          "FR-MEM-INV-012",
          "FR-MEM-INV-013",
          "FR-MEM-INV-014"
      ));

  public static final AccessKeyDescriptor TENANT_MEMBERSHIP_MANAGE = key(
      "tenant.membership.manage", "account-membership", "tenant.foundation", true,
      requirements("account-membership",
          "FR-MEM-LIFE-002",
          "FR-MEM-LIFE-003",
          "FR-MEM-LIFE-004",
          "FR-MEM-LIFE-005",
          "FR-MEM-LIFE-006",
          "FR-MEM-LIFE-007",
          "FR-MEM-LIFE-008",
          "FR-MEM-LIFE-009",
          "FR-MEM-LIFE-010",
          "FR-MEM-LIFE-011"
      ));

  public static final AccessKeyDescriptor TENANT_PLAN_VIEW = key(
      "tenant.plan.view", "plans-entitlements", "tenant.foundation", true,
      requirements("plans-entitlements",
          "FR-PE-ADM-005",
          "FR-PE-EVAL-001",
          "FR-PE-EVAL-002",
          "FR-PE-EVAL-003",
          "FR-PE-EVAL-004",
          "FR-PE-EVAL-005",
          "FR-PE-EVAL-006",
          "FR-PE-EVAL-007",
          "FR-PE-EVAL-008",
          "FR-PE-EVAL-009",
          "FR-PE-EVAL-010",
          "FR-PE-EVAL-011",
          "FR-PE-EVAL-012",
          "FR-PE-EVAL-013",
          "FR-PE-EVAL-014",
          "FR-PE-EVAL-015",
          "FR-PE-EVAL-016"
      ));

  public static final AccessKeyDescriptor TENANT_AUDIT_VIEW = key(
      "tenant.audit.view", "tenant-data-governance", "tenant.foundation", true,
      requirements("tenant-data-governance",
          "FR-TDG-AUD-001",
          "FR-TDG-AUD-002",
          "FR-TDG-AUD-003",
          "FR-TDG-AUD-004",
          "FR-TDG-AUD-005",
          "FR-TDG-AUD-006",
          "FR-TDG-AUD-007",
          "FR-TDG-AUD-008",
          "FR-TDG-AUD-009",
          "FR-TDG-AUD-010",
          "FR-TDG-AUD-011",
          "FR-TDG-AUD-012"
      ));

  public static final AccessKeyDescriptor TENANT_PARTY_VIEW = key(
      "tenant.party.view", "party-registration", "tenant.parties", false,
      requirements("party-registration",
          "FR-PTY-ID-001",
          "FR-PTY-SEARCH-001",
          "FR-PTY-SEARCH-002",
          "FR-PTY-SEC-002"
      ));

  public static final AccessKeyDescriptor TENANT_PARTY_CREATE = key(
      "tenant.party.create", "party-registration", "tenant.parties", false,
      requirements("party-registration",
          "FR-PTY-DUP-001",
          "FR-PTY-DUP-002",
          "FR-PTY-DUP-003",
          "FR-PTY-DUP-004",
          "FR-PTY-DUP-005",
          "FR-PTY-ID-001",
          "FR-PTY-SEC-002"
      ));

  public static final AccessKeyDescriptor TENANT_PARTY_UPDATE = key(
      "tenant.party.update", "party-registration", "tenant.parties", false,
      requirements("party-registration",
          "FR-PTY-DOC-001",
          "FR-PTY-DOC-002",
          "FR-PTY-DOC-003",
          "FR-PTY-DOC-004",
          "FR-PTY-DOC-005",
          "FR-PTY-DOC-006",
          "FR-PTY-DOC-007",
          "FR-PTY-ID-001",
          "FR-PTY-SEC-002"
      ));

  public static final AccessKeyDescriptor TENANT_PARTY_DEACTIVATE = key(
      "tenant.party.deactivate", "party-registration", "tenant.parties", false,
      requirements("party-registration",
          "FR-PTY-LIFE-001",
          "FR-PTY-LIFE-002",
          "FR-PTY-LIFE-003",
          "FR-PTY-LIFE-004",
          "FR-PTY-LIFE-005",
          "FR-PTY-LIFE-006",
          "FR-PTY-LIFE-007",
          "FR-PTY-LIFE-008",
          "FR-PTY-LIFE-009",
          "FR-PTY-LIFE-010",
          "FR-PTY-LIFE-011",
          "FR-PTY-LIFE-012",
          "FR-PTY-LIFE-013",
          "FR-PTY-SEC-002"
      ));

  public static final AccessKeyDescriptor TENANT_PARTY_REACTIVATE = key(
      "tenant.party.reactivate", "party-registration", "tenant.parties", false,
      requirements("party-registration",
          "FR-PTY-LIFE-001",
          "FR-PTY-LIFE-002",
          "FR-PTY-LIFE-003",
          "FR-PTY-LIFE-004",
          "FR-PTY-LIFE-005",
          "FR-PTY-LIFE-006",
          "FR-PTY-LIFE-007",
          "FR-PTY-LIFE-008",
          "FR-PTY-LIFE-009",
          "FR-PTY-LIFE-010",
          "FR-PTY-LIFE-011",
          "FR-PTY-LIFE-012",
          "FR-PTY-LIFE-013",
          "FR-PTY-SEC-002"
      ));

  public static final AccessKeyDescriptor TENANT_PARTY_IDENTIFIER_REVEAL = key(
      "tenant.party.identifier.reveal", "party-registration", "tenant.parties", false,
      requirements("party-registration",
          "FR-PTY-SEC-002",
          "FR-PTY-SEC-004",
          "FR-PTY-SEC-005",
          "FR-PTY-SEC-006",
          "FR-PTY-SEC-007"
      ));

  public static final AccessKeyDescriptor TENANT_PARTY_RELATIONSHIP_VIEW = key(
      "tenant.party.relationship.view", "party-relationships-roles", "tenant.parties", false,
      requirements("party-relationships-roles",
          "FR-PRR-SEARCH-002",
          "FR-PRR-SEARCH-003",
          "FR-PRR-SEARCH-004",
          "FR-PRR-SEARCH-005",
          "FR-PRR-SEARCH-006",
          "FR-PRR-SEC-002",
          "FR-PRR-SEC-003"
      ));

  public static final AccessKeyDescriptor TENANT_PARTY_RELATIONSHIP_ASSIGN = key(
      "tenant.party.relationship.assign", "party-relationships-roles", "tenant.parties", false,
      requirements("party-relationships-roles",
          "FR-PRR-ROLE-005",
          "FR-PRR-ROLE-006",
          "FR-PRR-ROLE-007",
          "FR-PRR-ROLE-008",
          "FR-PRR-ROLE-009",
          "FR-PRR-SEC-002"
      ));

  public static final AccessKeyDescriptor TENANT_PARTY_RELATIONSHIP_UPDATE = key(
      "tenant.party.relationship.update", "party-relationships-roles", "tenant.parties", false,
      requirements("party-relationships-roles",
          "FR-PRR-BOUND-004",
          "FR-PRR-SEC-002"
      ));

  public static final AccessKeyDescriptor TENANT_PARTY_RELATIONSHIP_END = key(
      "tenant.party.relationship.end", "party-relationships-roles", "tenant.parties", false,
      requirements("party-relationships-roles",
          "FR-PRR-REL-011",
          "FR-PRR-ROLE-009",
          "FR-PRR-SEC-002"
      ));

  public static final AccessKeyDescriptor TENANT_PARTY_RELATIONSHIP_CANCEL = key(
      "tenant.party.relationship.cancel", "party-relationships-roles", "tenant.parties", false,
      requirements("party-relationships-roles",
          "FR-PRR-REL-011",
          "FR-PRR-ROLE-009",
          "FR-PRR-SEC-002"
      ));

  public static final AccessKeyDescriptor TENANT_PARTY_PAYMENT_VIEW = key(
      "tenant.party.payment.view", "party-payment-details", "tenant.parties", false,
      requirements("party-payment-details",
          "FR-PPD-BOUND-001",
          "FR-PPD-BOUND-002",
          "FR-PPD-BOUND-003",
          "FR-PPD-BOUND-004",
          "FR-PPD-BOUND-005",
          "FR-PPD-BOUND-006",
          "FR-PPD-BOUND-007",
          "FR-PPD-BOUND-008",
          "FR-PPD-SEC-001",
          "FR-PPD-SEC-002",
          "FR-PPD-SEC-003",
          "FR-PPD-SEC-004"
      ));

  public static final AccessKeyDescriptor TENANT_PARTY_PAYMENT_REVEAL = key(
      "tenant.party.payment.reveal", "party-payment-details", "tenant.parties", false,
      requirements("party-payment-details",
          "FR-PPD-SEC-002",
          "FR-PPD-SEC-004",
          "FR-PPD-SEC-005",
          "FR-PPD-SEC-006",
          "FR-PPD-SEC-007",
          "FR-PPD-SEC-008",
          "FR-PPD-SEC-009",
          "FR-PPD-SEC-010"
      ));

  public static final AccessKeyDescriptor TENANT_PARTY_PAYMENT_CREATE = key(
      "tenant.party.payment.create", "party-payment-details", "tenant.parties", false,
      requirements("party-payment-details",
          "FR-PPD-BOUND-001",
          "FR-PPD-BOUND-002",
          "FR-PPD-BOUND-003",
          "FR-PPD-BOUND-004",
          "FR-PPD-BOUND-005",
          "FR-PPD-BOUND-006",
          "FR-PPD-BOUND-007",
          "FR-PPD-BOUND-008",
          "FR-PPD-SEC-002"
      ));

  public static final AccessKeyDescriptor TENANT_PARTY_PAYMENT_UPDATE = key(
      "tenant.party.payment.update", "party-payment-details", "tenant.parties", false,
      requirements("party-payment-details",
          "FR-PPD-SEC-002",
          "FR-PPD-SEC-008"
      ));

  public static final AccessKeyDescriptor TENANT_PARTY_PAYMENT_VERIFY = key(
      "tenant.party.payment.verify", "party-payment-details", "tenant.parties", false,
      requirements("party-payment-details",
          "FR-PPD-SEC-002",
          "FR-PPD-SEC-008"
      ));

  public static final AccessKeyDescriptor TENANT_PARTY_PAYMENT_PREFER = key(
      "tenant.party.payment.prefer", "party-payment-details", "tenant.parties", false,
      requirements("party-payment-details",
          "FR-PPD-SEC-002",
          "FR-PPD-SEC-008"
      ));

  public static final AccessKeyDescriptor TENANT_PARTY_PAYMENT_DEACTIVATE = key(
      "tenant.party.payment.deactivate", "party-payment-details", "tenant.parties", false,
      requirements("party-payment-details",
          "FR-PPD-SEC-002",
          "FR-PPD-SEC-008"
      ));

  public static final AccessKeyDescriptor TENANT_PARTY_PAYMENT_DELETE = key(
      "tenant.party.payment.delete", "party-payment-details", "tenant.parties", false,
      requirements("party-payment-details",
          "FR-PPD-SEC-002",
          "FR-PPD-SEC-008"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_ACCOUNT_VIEW = key(
      "tenant.financial.account.view", "financial-accounts", "tenant.financial.structure", false,
      requirements("financial-accounts",
          "FR-FAC-BOUND-001",
          "FR-FAC-BOUND-002",
          "FR-FAC-BOUND-003",
          "FR-FAC-BOUND-004",
          "FR-FAC-BOUND-005",
          "FR-FAC-BOUND-006",
          "FR-FAC-BOUND-007",
          "FR-FAC-BOUND-008",
          "FR-FAC-BOUND-009",
          "FR-FAC-BOUND-010",
          "FR-FAC-BOUND-011",
          "FR-FAC-BOUND-012",
          "FR-FAC-BOUND-013"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_ACCOUNT_CREATE = key(
      "tenant.financial.account.create", "financial-accounts", "tenant.financial.structure", false,
      requirements("financial-accounts",
          "FR-FAC-BOUND-001",
          "FR-FAC-BOUND-002",
          "FR-FAC-BOUND-003",
          "FR-FAC-BOUND-004",
          "FR-FAC-BOUND-005",
          "FR-FAC-BOUND-006",
          "FR-FAC-BOUND-007",
          "FR-FAC-BOUND-008",
          "FR-FAC-BOUND-009",
          "FR-FAC-BOUND-010",
          "FR-FAC-BOUND-011",
          "FR-FAC-BOUND-012",
          "FR-FAC-BOUND-013",
          "FR-FAC-SEC-002"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_ACCOUNT_UPDATE = key(
      "tenant.financial.account.update", "financial-accounts", "tenant.financial.structure", false,
      requirements("financial-accounts",
          "FR-FAC-SEC-002",
          "FR-FAC-SEC-003",
          "FR-FAC-SEC-004",
          "FR-FAC-SEC-005",
          "FR-FAC-SEC-006",
          "FR-FAC-SEC-007"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_ACCOUNT_DEACTIVATE = key(
      "tenant.financial.account.deactivate", "financial-accounts", "tenant.financial.structure", false,
      requirements("financial-accounts",
          "FR-FAC-SEC-002",
          "FR-FAC-SEC-003",
          "FR-FAC-SEC-004",
          "FR-FAC-SEC-005",
          "FR-FAC-SEC-006",
          "FR-FAC-SEC-007"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_CATEGORY_VIEW = key(
      "tenant.financial.category.view", "financial-categories", "tenant.financial.structure", false,
      requirements("financial-categories",
          "FR-FCAT-BOUND-001",
          "FR-FCAT-BOUND-002",
          "FR-FCAT-BOUND-003",
          "FR-FCAT-BOUND-004",
          "FR-FCAT-BOUND-005",
          "FR-FCAT-BOUND-006",
          "FR-FCAT-BOUND-007",
          "FR-FCAT-BOUND-008",
          "FR-FCAT-BOUND-009"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_CATEGORY_MANAGE = key(
      "tenant.financial.category.manage", "financial-categories", "tenant.financial.structure", false,
      requirements("financial-categories",
          "FR-FCAT-SEC-002",
          "FR-FCAT-SEC-003",
          "FR-FCAT-SEC-004",
          "FR-FCAT-SEC-005"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_DIMENSION_VIEW = key(
      "tenant.financial.dimension.view", "financial-dimensions", "tenant.financial.structure", false,
      requirements("financial-dimensions",
          "FR-FDIM-BOUND-001",
          "FR-FDIM-BOUND-002",
          "FR-FDIM-BOUND-003",
          "FR-FDIM-BOUND-004",
          "FR-FDIM-BOUND-005",
          "FR-FDIM-BOUND-006",
          "FR-FDIM-BOUND-007"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_DIMENSION_MANAGE = key(
      "tenant.financial.dimension.manage", "financial-dimensions", "tenant.financial.structure", false,
      requirements("financial-dimensions",
          "FR-FDIM-SEC-002",
          "FR-FDIM-SEC-003",
          "FR-FDIM-SEC-004",
          "FR-FDIM-SEC-005"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_TRANSACTION_VIEW = key(
      "tenant.financial.transaction.view", "financial-transactions", "tenant.financial.operations", false,
      requirements("financial-transactions",
          "FR-FTR-BOUND-001",
          "FR-FTR-BOUND-002",
          "FR-FTR-BOUND-003",
          "FR-FTR-BOUND-004",
          "FR-FTR-BOUND-005",
          "FR-FTR-BOUND-006",
          "FR-FTR-BOUND-007",
          "FR-FTR-BOUND-008"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_TRANSACTION_CREATE = key(
      "tenant.financial.transaction.create", "financial-transactions", "tenant.financial.operations", false,
      requirements("financial-transactions",
          "FR-FTR-BOUND-001",
          "FR-FTR-BOUND-002",
          "FR-FTR-BOUND-003",
          "FR-FTR-BOUND-004",
          "FR-FTR-BOUND-005",
          "FR-FTR-BOUND-006",
          "FR-FTR-BOUND-007",
          "FR-FTR-BOUND-008",
          "FR-FTR-SEC-002"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_TRANSACTION_CONFIRM = key(
      "tenant.financial.transaction.confirm", "financial-transactions", "tenant.financial.operations", false,
      requirements("financial-transactions",
          "FR-FTR-SEC-002",
          "FR-FTR-SEC-003",
          "FR-FTR-SEC-004",
          "FR-FTR-SEC-005"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_TRANSACTION_CORRECT = key(
      "tenant.financial.transaction.correct", "financial-transactions", "tenant.financial.operations", false,
      requirements("financial-transactions",
          "FR-FTR-SEC-002",
          "FR-FTR-SEC-003",
          "FR-FTR-SEC-004",
          "FR-FTR-SEC-005"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_TRANSACTION_CANCEL = key(
      "tenant.financial.transaction.cancel", "financial-transactions", "tenant.financial.operations", false,
      requirements("financial-transactions",
          "FR-FTR-SEC-002",
          "FR-FTR-SEC-003",
          "FR-FTR-SEC-004",
          "FR-FTR-SEC-005"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_TRANSFER_VIEW = key(
      "tenant.financial.transfer.view", "financial-transfers", "tenant.financial.operations", false,
      requirements("financial-transfers",
          "FR-FTF-BOUND-001",
          "FR-FTF-BOUND-002",
          "FR-FTF-BOUND-003",
          "FR-FTF-BOUND-004",
          "FR-FTF-BOUND-005",
          "FR-FTF-BOUND-006",
          "FR-FTF-BOUND-007",
          "FR-FTF-BOUND-008"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_TRANSFER_CREATE = key(
      "tenant.financial.transfer.create", "financial-transfers", "tenant.financial.operations", false,
      requirements("financial-transfers",
          "FR-FTF-BOUND-001",
          "FR-FTF-BOUND-002",
          "FR-FTF-BOUND-003",
          "FR-FTF-BOUND-004",
          "FR-FTF-BOUND-005",
          "FR-FTF-BOUND-006",
          "FR-FTF-BOUND-007",
          "FR-FTF-BOUND-008",
          "FR-FTF-SEC-002"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_TRANSFER_CONFIRM = key(
      "tenant.financial.transfer.confirm", "financial-transfers", "tenant.financial.operations", false,
      requirements("financial-transfers",
          "FR-FTF-SEC-002",
          "FR-FTF-SEC-003",
          "FR-FTF-SEC-004",
          "FR-FTF-SEC-005"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_TRANSFER_CORRECT = key(
      "tenant.financial.transfer.correct", "financial-transfers", "tenant.financial.operations", false,
      requirements("financial-transfers",
          "FR-FTF-SEC-002",
          "FR-FTF-SEC-003",
          "FR-FTF-SEC-004",
          "FR-FTF-SEC-005"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_TRANSFER_CANCEL = key(
      "tenant.financial.transfer.cancel", "financial-transfers", "tenant.financial.operations", false,
      requirements("financial-transfers",
          "FR-FTF-SEC-002",
          "FR-FTF-SEC-003",
          "FR-FTF-SEC-004",
          "FR-FTF-SEC-005"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_PAYABLE_VIEW = key(
      "tenant.financial.payable.view", "accounts-payable", "tenant.financial.operations", false,
      requirements("accounts-payable",
          "FR-AP-BOUND-001",
          "FR-AP-BOUND-002",
          "FR-AP-BOUND-003",
          "FR-AP-BOUND-004",
          "FR-AP-BOUND-005",
          "FR-AP-BOUND-006",
          "FR-AP-BOUND-007",
          "FR-AP-BOUND-008"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_PAYABLE_MANAGE = key(
      "tenant.financial.payable.manage", "accounts-payable", "tenant.financial.operations", false,
      requirements("accounts-payable",
          "FR-AP-SEC-002",
          "FR-AP-SEC-003",
          "FR-AP-SEC-004",
          "FR-AP-SEC-005",
          "FR-AP-SEC-006"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_PAYABLE_SETTLE = key(
      "tenant.financial.payable.settle", "accounts-payable", "tenant.financial.operations", false,
      requirements("accounts-payable",
          "FR-AP-BOUND-004",
          "FR-AP-SEC-002",
          "FR-AP-SEC-003",
          "FR-AP-SEC-004",
          "FR-AP-SEC-005",
          "FR-AP-SEC-006"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_RECEIVABLE_VIEW = key(
      "tenant.financial.receivable.view", "accounts-receivable", "tenant.financial.operations", false,
      requirements("accounts-receivable",
          "FR-AR-BOUND-001",
          "FR-AR-BOUND-002",
          "FR-AR-BOUND-003",
          "FR-AR-BOUND-004",
          "FR-AR-BOUND-005",
          "FR-AR-BOUND-006",
          "FR-AR-BOUND-007"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_RECEIVABLE_MANAGE = key(
      "tenant.financial.receivable.manage", "accounts-receivable", "tenant.financial.operations", false,
      requirements("accounts-receivable",
          "FR-AR-SEC-001",
          "FR-AR-SEC-002",
          "FR-AR-SEC-003"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_RECEIVABLE_SETTLE = key(
      "tenant.financial.receivable.settle", "accounts-receivable", "tenant.financial.operations", false,
      requirements("accounts-receivable",
          "FR-AR-BOUND-004",
          "FR-AR-SEC-001",
          "FR-AR-SEC-002",
          "FR-AR-SEC-003"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_RECURRENCE_VIEW = key(
      "tenant.financial.recurrence.view", "financial-recurrences", "tenant.financial.operations", false,
      requirements("financial-recurrences",
          "FR-FREC-BOUND-001",
          "FR-FREC-BOUND-002",
          "FR-FREC-BOUND-003",
          "FR-FREC-BOUND-004",
          "FR-FREC-BOUND-005",
          "FR-FREC-BOUND-006",
          "FR-FREC-BOUND-007",
          "FR-FREC-BOUND-008"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_RECURRENCE_MANAGE = key(
      "tenant.financial.recurrence.manage", "financial-recurrences", "tenant.financial.operations", false,
      requirements("financial-recurrences",
          "FR-FREC-SEC-001",
          "FR-FREC-SEC-002",
          "FR-FREC-SEC-003",
          "FR-FREC-SEC-004",
          "FR-FREC-SEC-005"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_CARD_VIEW = key(
      "tenant.financial.card.view", "credit-cards", "tenant.financial.structure", false,
      requirements("credit-cards",
          "FR-CC-BOUND-001",
          "FR-CC-BOUND-002",
          "FR-CC-BOUND-003",
          "FR-CC-BOUND-004",
          "FR-CC-BOUND-005",
          "FR-CC-BOUND-006",
          "FR-CC-BOUND-007"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_CARD_MANAGE = key(
      "tenant.financial.card.manage", "credit-cards", "tenant.financial.structure", false,
      requirements("credit-cards",
          "FR-CC-SEC-001",
          "FR-CC-SEC-002",
          "FR-CC-SEC-003",
          "FR-CC-SEC-004",
          "FR-CC-SEC-005",
          "FR-CC-SEC-006",
          "FR-CC-SEC-007"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_CLOSING_VIEW = key(
      "tenant.financial.closing.view", "financial-closing-control", "tenant.financial.control", false,
      requirements("financial-closing-control",
          "FR-FCC-BOUND-001",
          "FR-FCC-BOUND-002",
          "FR-FCC-BOUND-003",
          "FR-FCC-BOUND-004",
          "FR-FCC-BOUND-005",
          "FR-FCC-BOUND-006",
          "FR-FCC-BOUND-007",
          "FR-FCC-BOUND-008",
          "FR-FCC-BOUND-009"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_CLOSING_CLOSE = key(
      "tenant.financial.closing.close", "financial-closing-control", "tenant.financial.control", false,
      requirements("financial-closing-control",
          "FR-FCC-SEC-001",
          "FR-FCC-SEC-002",
          "FR-FCC-SEC-003",
          "FR-FCC-SEC-004"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_CLOSING_REOPEN = key(
      "tenant.financial.closing.reopen", "financial-closing-control", "tenant.financial.control", false,
      requirements("financial-closing-control",
          "FR-FCC-SEC-001",
          "FR-FCC-SEC-002",
          "FR-FCC-SEC-003",
          "FR-FCC-SEC-004",
          "FR-FCC-SEC-005"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_STATEMENT_IMPORT = key(
      "tenant.financial.statement.import", "bank-statements-reconciliation", "tenant.financial.control", false,
      requirements("bank-statements-reconciliation",
          "FR-BSR-BOUND-001",
          "FR-BSR-BOUND-002",
          "FR-BSR-BOUND-003",
          "FR-BSR-BOUND-004",
          "FR-BSR-BOUND-005",
          "FR-BSR-BOUND-006",
          "FR-BSR-BOUND-007",
          "FR-BSR-BOUND-008",
          "FR-BSR-BOUND-009",
          "FR-BSR-BOUND-010",
          "FR-BSR-SEC-001"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_RECONCILIATION_VIEW = key(
      "tenant.financial.reconciliation.view", "bank-statements-reconciliation", "tenant.financial.control", false,
      requirements("bank-statements-reconciliation",
          "FR-BSR-SEC-001",
          "FR-BSR-SEC-002",
          "FR-BSR-SEC-004",
          "FR-BSR-SEC-005",
          "FR-BSR-SEC-006"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_RECONCILIATION_MANAGE = key(
      "tenant.financial.reconciliation.manage", "bank-statements-reconciliation", "tenant.financial.control", false,
      requirements("bank-statements-reconciliation",
          "FR-BSR-SEC-001",
          "FR-BSR-SEC-002",
          "FR-BSR-SEC-003",
          "FR-BSR-SEC-004",
          "FR-BSR-SEC-005"
      ));

  public static final AccessKeyDescriptor TENANT_FINANCIAL_AUDIT_VIEW = key(
      "tenant.financial.audit.view", "tenant-data-governance", "tenant.financial.control", false,
      requirements("tenant-data-governance",
          "FR-TDG-AUD-001",
          "FR-TDG-AUD-002",
          "FR-TDG-AUD-003",
          "FR-TDG-AUD-004",
          "FR-TDG-AUD-005",
          "FR-TDG-AUD-006",
          "FR-TDG-AUD-007",
          "FR-TDG-AUD-008",
          "FR-TDG-AUD-009",
          "FR-TDG-AUD-010",
          "FR-TDG-AUD-011",
          "FR-TDG-AUD-012"
      ));

  public static final Set<AccessKeyDescriptor> ALL = Set.of(
      GLOBAL_DIRECTORY_USER_VIEW,
      GLOBAL_DIRECTORY_ACCOUNT_VIEW,
      GLOBAL_DIRECTORY_IDENTITY_BLOCK,
      GLOBAL_DIRECTORY_ACCOUNT_INTERVENE,
      GLOBAL_DIRECTORY_ACCOUNT_RECOVER,
      GLOBAL_PLATFORM_CONFIGURATION_VIEW,
      GLOBAL_PLATFORM_CONFIGURATION_MANAGE,
      GLOBAL_PLATFORM_OPERATION_VIEW,
      GLOBAL_PLATFORM_OPERATION_MANAGE,
      GLOBAL_PLATFORM_PROVISIONING_MANAGE,
      GLOBAL_PLATFORM_AUDIT_VIEW,
      GLOBAL_PLATFORM_SUPPORT_OPERATE,
      GLOBAL_PLAN_CATALOG_VIEW,
      GLOBAL_PLAN_CATALOG_MANAGE,
      GLOBAL_PLAN_ASSIGNMENT_MANAGE,
      TENANT_ACCOUNT_VIEW,
      TENANT_ACCOUNT_UPDATE,
      TENANT_ACCOUNT_LIFECYCLE_MANAGE,
      TENANT_MEMBERSHIP_VIEW,
      TENANT_MEMBERSHIP_INVITE,
      TENANT_MEMBERSHIP_MANAGE,
      TENANT_PLAN_VIEW,
      TENANT_AUDIT_VIEW,
      TENANT_PARTY_VIEW,
      TENANT_PARTY_CREATE,
      TENANT_PARTY_UPDATE,
      TENANT_PARTY_DEACTIVATE,
      TENANT_PARTY_REACTIVATE,
      TENANT_PARTY_IDENTIFIER_REVEAL,
      TENANT_PARTY_RELATIONSHIP_VIEW,
      TENANT_PARTY_RELATIONSHIP_ASSIGN,
      TENANT_PARTY_RELATIONSHIP_UPDATE,
      TENANT_PARTY_RELATIONSHIP_END,
      TENANT_PARTY_RELATIONSHIP_CANCEL,
      TENANT_PARTY_PAYMENT_VIEW,
      TENANT_PARTY_PAYMENT_REVEAL,
      TENANT_PARTY_PAYMENT_CREATE,
      TENANT_PARTY_PAYMENT_UPDATE,
      TENANT_PARTY_PAYMENT_VERIFY,
      TENANT_PARTY_PAYMENT_PREFER,
      TENANT_PARTY_PAYMENT_DEACTIVATE,
      TENANT_PARTY_PAYMENT_DELETE,
      TENANT_FINANCIAL_ACCOUNT_VIEW,
      TENANT_FINANCIAL_ACCOUNT_CREATE,
      TENANT_FINANCIAL_ACCOUNT_UPDATE,
      TENANT_FINANCIAL_ACCOUNT_DEACTIVATE,
      TENANT_FINANCIAL_CATEGORY_VIEW,
      TENANT_FINANCIAL_CATEGORY_MANAGE,
      TENANT_FINANCIAL_DIMENSION_VIEW,
      TENANT_FINANCIAL_DIMENSION_MANAGE,
      TENANT_FINANCIAL_TRANSACTION_VIEW,
      TENANT_FINANCIAL_TRANSACTION_CREATE,
      TENANT_FINANCIAL_TRANSACTION_CONFIRM,
      TENANT_FINANCIAL_TRANSACTION_CORRECT,
      TENANT_FINANCIAL_TRANSACTION_CANCEL,
      TENANT_FINANCIAL_TRANSFER_VIEW,
      TENANT_FINANCIAL_TRANSFER_CREATE,
      TENANT_FINANCIAL_TRANSFER_CONFIRM,
      TENANT_FINANCIAL_TRANSFER_CORRECT,
      TENANT_FINANCIAL_TRANSFER_CANCEL,
      TENANT_FINANCIAL_PAYABLE_VIEW,
      TENANT_FINANCIAL_PAYABLE_MANAGE,
      TENANT_FINANCIAL_PAYABLE_SETTLE,
      TENANT_FINANCIAL_RECEIVABLE_VIEW,
      TENANT_FINANCIAL_RECEIVABLE_MANAGE,
      TENANT_FINANCIAL_RECEIVABLE_SETTLE,
      TENANT_FINANCIAL_RECURRENCE_VIEW,
      TENANT_FINANCIAL_RECURRENCE_MANAGE,
      TENANT_FINANCIAL_CARD_VIEW,
      TENANT_FINANCIAL_CARD_MANAGE,
      TENANT_FINANCIAL_CLOSING_VIEW,
      TENANT_FINANCIAL_CLOSING_CLOSE,
      TENANT_FINANCIAL_CLOSING_REOPEN,
      TENANT_FINANCIAL_STATEMENT_IMPORT,
      TENANT_FINANCIAL_RECONCILIATION_VIEW,
      TENANT_FINANCIAL_RECONCILIATION_MANAGE,
      TENANT_FINANCIAL_AUDIT_VIEW
  );

  private InitialModuleAccessKeys() {
  }

  private static AccessKeyDescriptor key(
      String code,
      String ownerModule,
      String categoryCode,
      boolean minimumAdministrative,
      Set<AccessKeyRequirement> requirements) {
    AccessScope scope = code.startsWith("global.") ? AccessScope.GLOBAL : AccessScope.TENANT;
    return AccessKeyDescriptor.active(
        code, scope, categoryCode, ownerModule, requirements, minimumAdministrative);
  }

  private static Set<AccessKeyRequirement> requirements(
      String featureCode, String... requirementCodes) {
    LinkedHashSet<AccessKeyRequirement> requirements = new LinkedHashSet<>();
    for (String requirementCode : requirementCodes) {
      requirements.add(new AccessKeyRequirement(featureCode, requirementCode));
    }
    return Set.copyOf(requirements);
  }
}
