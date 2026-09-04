package br.com.rinos.app.backend.module.plans.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Garante paridade entre o schema consolidado e a migration incremental de planos. */
class PlansDatabaseCatalogTest {

  private static final String INIT = "db/global/init/01-ddl.sql";
  private static final String UPDATE = "db/global/update/20260816_002_update.sql";
  private static final String BOOTSTRAP_UPDATE = "db/global/update/20260816_003_update.sql";

  @Test
  void initAndUpdate_shouldPublishTheSamePlansSchema() throws IOException {
    String init = read(INIT);
    String update = read(UPDATE);

    String initBeforeBootstrap = plansSchema(init)
        .replace("  idempotencyKey BINARY(32) NOT NULL,\n", "")
        .replace("  CONSTRAINT uk_plans_service_contract_key UNIQUE (scopeType, idempotencyKey),\n", "");
    assertThat(initBeforeBootstrap).isEqualTo(plansSchema(update));
    assertThat(update).contains(
        "ADD COLUMN entitlementScope VARCHAR(16)",
        "uk_account_account_tenant_ref",
        "uk_membership_account_user_ref",
        "uk_membership_invitation_account_ref",
        "CREATE TABLE plans_plan",
        "CREATE TABLE plans_serviceContract",
        "CREATE TABLE plans_tenantUserCapacityOccupancy",
        "CREATE TABLE plans_tenantUserCapacityReservation",
        "CREATE TABLE plans_auditEvent",
        "CREATE TABLE plans_outboxEvent");
    assertThat(read(BOOTSTRAP_UPDATE)).contains(
        "ADD COLUMN idempotencyKey BINARY(32)",
        "'PERSONAL', 'FREE'",
        "'TENANT', 'FREE'",
        "'membership.associated-users.limit'",
        "plans_personalContractHolder",
        "plans_tenantUserCapacityOccupancy",
        "plans_tenantUserCapacityReservation",
        "SELECT '20260816003' AS version");
  }

  @Test
  void update_shouldAdvanceVersionOnlyAfterTheCompleteSchema() throws IOException {
    String update = read(UPDATE);

    assertThat(update.indexOf("CREATE TABLE plans_outboxEvent"))
        .isLessThan(update.indexOf("SELECT '20260816002' AS version"));
  }

  private static String plansSchema(String sql) {
    int start = sql.indexOf("CREATE TABLE plans_plan");
    int version = sql.indexOf("CREATE OR REPLACE", start);
    int accessSeed = sql.indexOf("INSERT INTO access_contextRevision", start);
    int storage = sql.indexOf("CREATE TABLE storage_tenantRegistry", start);
    int end = version >= 0 ? version : sql.length();
    if (accessSeed >= 0 && accessSeed < end) {
      end = accessSeed;
    }
    if (storage >= 0 && storage < end) {
      end = storage;
    }
    return sql.substring(start, end).strip();
  }

  private static String read(String location) throws IOException {
    return new ClassPathResource(location).getContentAsString(StandardCharsets.UTF_8)
        .replace("\r\n", "\n");
  }
}
