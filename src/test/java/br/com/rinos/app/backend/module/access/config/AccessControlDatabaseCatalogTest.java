package br.com.rinos.app.backend.module.access.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Garante paridade textual entre o schema ACL consolidado e sua migration incremental. */
class AccessControlDatabaseCatalogTest {

  private static final String INIT = "db/global/init/01-ddl.sql";
  private static final String UPDATE = "db/global/update/20260815_001_update.sql";
  private static final String MEMBERSHIP_UPDATE =
      "db/global/update/20260815_003_update.sql";
  private static final String PLANS_UPDATE =
      "db/global/update/20260816_002_update.sql";

  @Test
  void initAndUpdate_shouldPublishTheSameAccessControlSchema() throws IOException {
    String init = read(INIT);
    String update = read(UPDATE);

    String initAccess = accessSchema(init)
        .replace("  entitlementScope VARCHAR(16) NULL,\n", "")
        .replace("""
              CONSTRAINT ck_access_key_entitlement CHECK (
                (entitlementScope IS NULL AND entitlementCode IS NULL)
                OR (entitlementScope IN ('PERSONAL', 'TENANT') AND entitlementCode IS NOT NULL)
              ),
            """, "")
        .replace("""
              CONSTRAINT fk_access_group_subject_membership FOREIGN KEY (idAccountMembership)
                REFERENCES membership_accountMembership (idAccountMembership)
                ON DELETE RESTRICT ON UPDATE RESTRICT,
            """, "")
        .replace("""
              CONSTRAINT fk_access_rule_membership FOREIGN KEY (idAccountMembership)
                REFERENCES membership_accountMembership (idAccountMembership)
                ON DELETE RESTRICT ON UPDATE RESTRICT,
            """, "");
    String updateAccess = accessSchema(update);

    assertThat(initAccess).isEqualTo(updateAccess);
    assertThat(initAccess).contains(
        "CREATE TABLE access_keyCategory",
        "CREATE TABLE access_key",
        "CREATE TABLE access_contextRevision",
        "CREATE TABLE access_group",
        "CREATE TABLE access_groupSubject",
        "CREATE TABLE access_rule",
        "CREATE TABLE access_ruleHistory",
        "CREATE TABLE access_bootstrap",
        "CREATE TABLE access_auditEvent",
        "VALUES ('GLOBAL', NULL, 0)",
        "VALUES (1, 'NEVER_COMPLETED', 0)");
    assertThat(read(MEMBERSHIP_UPDATE)).contains(
        "ADD CONSTRAINT fk_access_group_subject_membership",
        "ADD CONSTRAINT fk_access_rule_membership");
    assertThat(read(PLANS_UPDATE)).contains(
        "ADD COLUMN entitlementScope VARCHAR(16)",
        "ADD CONSTRAINT ck_access_key_entitlement");
  }

  @Test
  void update_shouldAdvanceTheVersionOnlyAfterAllAccessStructures() throws IOException {
    String update = read(UPDATE);

    assertThat(update.indexOf("CREATE TABLE access_auditEvent"))
        .isLessThan(update.indexOf("SELECT '20260815001' AS version"));
    assertThat(read("db/global/init/99-database-version.sql"))
        .contains("SELECT '20260816002' AS version");
  }

  private static String accessSchema(String sql) {
    int start = sql.indexOf("CREATE TABLE access_keyCategory");
    int version = sql.indexOf("CREATE OR REPLACE", start);
    int plans = sql.indexOf("CREATE TABLE plans_plan", start);
    int end = version < 0 ? sql.length() : version;
    if (plans >= 0 && plans < end) {
      int accessSeed = sql.indexOf("INSERT INTO access_contextRevision", plans);
      return (sql.substring(start, plans) + sql.substring(accessSeed, end)).strip();
    }
    return sql.substring(start, end).strip();
  }

  private static String read(String location) throws IOException {
    return new ClassPathResource(location).getContentAsString(StandardCharsets.UTF_8)
        .replace("\r\n", "\n");
  }
}
