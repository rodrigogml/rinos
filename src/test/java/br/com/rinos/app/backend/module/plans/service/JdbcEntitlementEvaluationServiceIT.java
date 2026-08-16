package br.com.rinos.app.backend.module.plans.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import br.com.rinos.app.api.module.plans.dto.EntitlementEvaluationRequest;
import br.com.rinos.app.api.module.plans.enums.ContractScope;
import br.com.rinos.app.api.module.plans.enums.EntitlementDecisionStatus;
import br.com.rinos.app.api.module.plans.vo.EntitlementRequirement;
import br.com.rinos.app.api.module.plans.vo.TenantEntitlementSubject;
import br.com.rinos.app.testsupport.mysql.MySqlTestDatabase;

@DisplayName("Avaliação persistente de direitos de plano")
class JdbcEntitlementEvaluationServiceIT {

  private static final EntitlementRequirement LIMIT = new EntitlementRequirement(
      ContractScope.TENANT, "membership.associated-users.limit");

  private static MySqlTestDatabase database;

  private DataSource dataSource;
  private JdbcTemplate jdbc;

  @BeforeAll
  static void startDatabase() {
    database = MySqlTestDatabase.openIfAvailable().orElse(null);
  }

  @AfterAll
  static void stopDatabase() {
    if (database != null) {
      database.close();
    }
  }

  @BeforeEach
  void initialize() {
    Assumptions.assumeTrue(database != null, "MySQL de testes indisponível");
    dataSource = database.recreateSchema();
    new ResourceDatabasePopulator(
        new ClassPathResource("db/global/init/01-ddl.sql"),
        new ClassPathResource("db/global/init/02-seed.sql"),
        new ClassPathResource("db/global/init/03-procedures.sql"),
        new ClassPathResource("db/global/init/99-database-version.sql"))
        .execute(dataSource);
    jdbc = new JdbcTemplate(dataSource);
    insertTenantContract();
  }

  @Test
  void shouldReadContractLiveAndCacheOnlyPublishedComposition() {
    JdbcEntitlementEvaluationService service = new JdbcEntitlementEvaluationService(dataSource);
    Instant evaluatedAt = Instant.now();

    var available = service.evaluate(request(evaluatedAt));

    assertThat(available.allowed()).isTrue();
    assertThat(available.results().getFirst().configuredLimit()).isEqualTo(10);
    assertThat(available.results().getFirst().observedUsage()).isZero();

    jdbc.update("""
        UPDATE plans_planVersionEntitlement
           SET quantityValue = 99
         WHERE idPlanVersion = 2
        """);
    insertOccupancies(10);

    var reached = service.evaluate(request(evaluatedAt.plusSeconds(1)));

    assertThat(reached.allowed()).isFalse();
    assertThat(reached.results().getFirst().status())
        .isEqualTo(EntitlementDecisionStatus.LIMIT_REACHED);
    assertThat(reached.results().getFirst().configuredLimit()).isEqualTo(10);
    assertThat(reached.results().getFirst().observedUsage()).isEqualTo(10);

    jdbc.update("UPDATE plans_serviceContract SET status = 'SUSPENDED' WHERE idServiceContract = 1");
    var unavailable = service.evaluate(request(evaluatedAt.plusSeconds(2)));

    assertThat(unavailable.results().getFirst().status())
        .isEqualTo(EntitlementDecisionStatus.UNAVAILABLE);
    assertThat(unavailable.results().getFirst().safeReasonCode())
        .isEqualTo("PLAN_CONTRACT_UNAVAILABLE");
  }

  private EntitlementEvaluationRequest request(Instant evaluatedAt) {
    return new EntitlementEvaluationRequest(
        new TenantEntitlementSubject(1), Set.of(LIMIT), "membership.invite",
        evaluatedAt, "plans-evaluation-it");
  }

  private void insertTenantContract() {
    jdbc.update("""
        INSERT INTO identity_user (email, normalizedEmail, status)
        VALUES ('founder@example.com', 'founder@example.com', 'ACTIVE')
        """);
    jdbc.update("""
        INSERT INTO account_tenant (publicId, status)
        VALUES (UUID_TO_BIN(UUID()), 'OPERATIONAL')
        """);
    jdbc.update("""
        INSERT INTO account_account
          (publicId, idTenant, founderUserId, displayName, baseCurrency, timeZoneId, status)
        VALUES (UUID_TO_BIN(UUID()), 1, 1, 'Plans evaluation', 'BRL',
                'America/Sao_Paulo', 'ACTIVE')
        """);
    jdbc.update("""
        INSERT INTO plans_serviceContract
          (publicId, scopeType, status, startedAt, sourceType, idempotencyKey, correlationId)
        VALUES (UUID_TO_BIN(UUID()), 'TENANT', 'ACTIVE', CURRENT_TIMESTAMP(6),
                'SYSTEM', UNHEX(SHA2('plans-evaluation-contract', 256)), 'plans-evaluation-it')
        """);
    jdbc.update("""
        INSERT INTO plans_tenantContractHolder (idServiceContract, idTenant)
        VALUES (1, 1)
        """);
    jdbc.update("""
        INSERT INTO plans_planAssignment
          (idServiceContract, idPlanVersion, scopeType, status, currentMarker,
           startedAt, sourceType, idempotencyKey)
        SELECT 1, idPlanVersion, 'TENANT', 'ACTIVE', 1, CURRENT_TIMESTAMP(6),
               'SYSTEM', UUID_TO_BIN(UUID())
          FROM plans_planVersion
         WHERE scopeType = 'TENANT' AND status = 'PUBLISHED'
        """);
  }

  private void insertOccupancies(int count) {
    for (int index = 0; index < count; index++) {
      int userId = index + 2;
      jdbc.update("""
          INSERT INTO identity_user (email, normalizedEmail, status)
          VALUES (?, ?, 'ACTIVE')
          """, "user" + userId + "@example.com", "user" + userId + "@example.com");
      jdbc.update("""
          INSERT INTO plans_tenantUserCapacityOccupancy
            (idServiceContract, idTenant, idAccount, idUser, occupiedAt,
             sourceType, idempotencyKey)
          VALUES (1, 1, 1, ?, CURRENT_TIMESTAMP(6), 'MANUAL', UUID_TO_BIN(UUID()))
          """, userId);
    }
  }
}
