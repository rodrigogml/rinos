package br.com.rinos.app.backend.module.plans.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import br.com.rinos.app.api.module.plans.dto.InvitationAcceptanceCapacityRequest;
import br.com.rinos.app.api.module.plans.dto.AssociationCapacityRequest;
import br.com.rinos.app.api.module.plans.dto.InvitationCapacityReleaseRequest;
import br.com.rinos.app.api.module.plans.dto.InvitationCapacityRequest;
import br.com.rinos.app.api.module.plans.dto.TenantUserCapacityRequest;
import br.com.rinos.app.api.module.plans.dto.PersonalContractBootstrapRequest;
import br.com.rinos.app.api.module.plans.dto.TenantContractBootstrapRequest;
import br.com.rinos.app.api.module.plans.enums.ContractBootstrapStatus;
import br.com.rinos.app.api.module.plans.enums.TenantUserCapacityStatus;
import br.com.rinos.app.testsupport.mysql.MySqlTestDatabase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@DisplayName("Núcleo transacional de planos")
class PlansTransactionalCoreIT {

  private static MySqlTestDatabase database;

  private DataSource dataSource;
  private JdbcTemplate jdbc;
  private JdbcContractBootstrapService contracts;
  private JdbcTenantUserCapacityService capacity;
  private UUID tenantPublicId;
  private UUID accountPublicId;

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
    var transactions = new DataSourceTransactionManager(dataSource);
    var metrics = new SimpleMeterRegistry();
    contracts = new JdbcContractBootstrapService(dataSource, transactions, metrics);
    capacity = new JdbcTenantUserCapacityService(dataSource, transactions, metrics);
    insertBaseContext();
  }

  @Test
  void shouldBootstrapAndManageCapacityIdempotently() {
    UUID personalProtocol = UUID.randomUUID();
    UUID tenantProtocol = UUID.randomUUID();

    var personal = contracts.ensure(new PersonalContractBootstrapRequest(
        personalProtocol, 1, "plans-core-it"));
    var repeatedPersonal = contracts.ensure(new PersonalContractBootstrapRequest(
        personalProtocol, 1, "plans-core-it-repeat"));
    var tenant = contracts.ensure(new TenantContractBootstrapRequest(
        tenantProtocol, accountPublicId, tenantPublicId, 1, "plans-core-it"));
    var repeatedTenant = contracts.ensure(new TenantContractBootstrapRequest(
        tenantProtocol, accountPublicId, tenantPublicId, 1, "plans-core-it-repeat"));

    assertThat(personal.status()).isEqualTo(ContractBootstrapStatus.COMPLETED);
    assertThat(repeatedPersonal.status()).isEqualTo(ContractBootstrapStatus.ALREADY_COMPLETED);
    assertThat(repeatedPersonal.contractPublicId()).isEqualTo(personal.contractPublicId());
    assertThat(tenant.status()).isEqualTo(ContractBootstrapStatus.COMPLETED);
    assertThat(repeatedTenant.status()).isEqualTo(ContractBootstrapStatus.ALREADY_COMPLETED);
    assertThat(repeatedTenant.contractPublicId()).isEqualTo(tenant.contractPublicId());
    assertThat(count("plans_tenantUserCapacityOccupancy")).isEqualTo(1);

    List<UUID> invitations = new ArrayList<>();
    for (int index = 0; index < 10; index++) {
      invitations.add(insertInvitation("invite" + index + "@example.com"));
    }
    Instant now = Instant.now();
    for (int index = 0; index < 9; index++) {
      var reserved = capacity.reserve(reservation(invitations.get(index), index, now));
      assertThat(reserved.status()).isEqualTo(TenantUserCapacityStatus.RESERVED);
    }
    var repeated = capacity.reserve(reservation(invitations.getFirst(), 0, now));
    var limited = capacity.reserve(reservation(invitations.get(9), 9, now));

    assertThat(repeated.status()).isEqualTo(TenantUserCapacityStatus.ALREADY_RESERVED);
    assertThat(limited.status()).isEqualTo(TenantUserCapacityStatus.LIMIT_REACHED);
    assertThat(limited.used()).isEqualTo(10);
    insertUser(3, "manual@example.com");
    assertThat(capacity.occupy(new AssociationCapacityRequest(
        1, 3, UUID.randomUUID(), "plans-manual-limit-it")).status())
        .isEqualTo(TenantUserCapacityStatus.LIMIT_REACHED);

    var released = capacity.releaseUnaccepted(new InvitationCapacityReleaseRequest(
        1, invitations.getFirst(), "plans-release-it"));
    assertThat(countWhere("plans_tenantUserCapacityReservation", "status = 'RESERVED'"))
        .isEqualTo(8);
    assertThat(countWhere("plans_tenantUserCapacityReservation", "status = 'RELEASED'"))
        .isOne();
    var releasedAgain = capacity.releaseUnaccepted(new InvitationCapacityReleaseRequest(
        1, invitations.getFirst(), "plans-release-repeat-it"));
    assertThat(countWhere("plans_tenantUserCapacityReservation", "status = 'RESERVED'"))
        .isEqualTo(8);
    assertThat(countWhere("plans_tenantUserCapacityReservation", "status = 'RELEASED'"))
        .isOne();
    var newlyReserved = capacity.reserve(reservation(invitations.get(9), 9, now));

    assertThat(released.status()).isEqualTo(TenantUserCapacityStatus.RELEASED);
    assertThat(releasedAgain.status()).isEqualTo(TenantUserCapacityStatus.RELEASED);
    assertThat(newlyReserved.status()).isEqualTo(TenantUserCapacityStatus.RESERVED);

    insertUser(2, "accepted@example.com");
    var converted = capacity.convert(new InvitationAcceptanceCapacityRequest(
        1, 2, invitations.get(1), "plans-convert-it"));
    var convertedAgain = capacity.convert(new InvitationAcceptanceCapacityRequest(
        1, 2, invitations.get(1), "plans-convert-repeat-it"));

    assertThat(converted.status()).isEqualTo(TenantUserCapacityStatus.OCCUPIED);
    assertThat(converted.used()).isEqualTo(10);
    assertThat(convertedAgain.status()).isEqualTo(TenantUserCapacityStatus.ALREADY_OCCUPIED);
    assertThat(convertedAgain.used()).isEqualTo(10);
    assertThat(count("plans_auditEvent")).isGreaterThanOrEqualTo(13);
    assertThat(count("plans_outboxEvent")).isGreaterThanOrEqualTo(13);
  }

  @Test
  void shouldSerializeTwoInstancesCompetingForLastCapacity() throws Exception {
    contracts.ensure(new TenantContractBootstrapRequest(
        UUID.randomUUID(), accountPublicId, tenantPublicId, 1, "plans-race-it"));
    Instant now = Instant.now();
    for (int index = 0; index < 8; index++) {
      UUID invitation = insertInvitation("reserved" + index + "@example.com");
      assertThat(capacity.reserve(reservation(invitation, index, now)).status())
          .isEqualTo(TenantUserCapacityStatus.RESERVED);
    }
    UUID first = insertInvitation("race-one@example.com");
    UUID second = insertInvitation("race-two@example.com");
    var firstService = serviceInstance();
    var secondService = serviceInstance();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var firstResult = executor.submit(() -> {
        ready.countDown();
        start.await(10, TimeUnit.SECONDS);
        return firstService.reserve(reservation(first, 101, now));
      });
      var secondResult = executor.submit(() -> {
        ready.countDown();
        start.await(10, TimeUnit.SECONDS);
        return secondService.reserve(reservation(second, 102, now));
      });
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      assertThat(List.of(firstResult.get(20, TimeUnit.SECONDS).status(),
          secondResult.get(20, TimeUnit.SECONDS).status()))
          .containsExactlyInAnyOrder(
              TenantUserCapacityStatus.RESERVED,
              TenantUserCapacityStatus.LIMIT_REACHED);
    }

    var snapshot = capacity.inspect(new br.com.rinos.app.api.module.plans.dto.TenantUserCapacityRequest(1, null));
    assertThat(snapshot.used()).isEqualTo(10);
    assertThat(snapshot.status()).isEqualTo(TenantUserCapacityStatus.LIMIT_REACHED);
  }

  @Test
  void shouldIsolatePersonalAndTenantContractsAndCapacityBetweenTenants() {
    contracts.ensure(new PersonalContractBootstrapRequest(
        UUID.randomUUID(), 1, "plans-isolation-personal"));
    contracts.ensure(new TenantContractBootstrapRequest(
        UUID.randomUUID(), accountPublicId, tenantPublicId, 1, "plans-isolation-first"));
    TenantContext second = insertTenantContext("Plans isolated tenant");
    contracts.ensure(new TenantContractBootstrapRequest(
        UUID.randomUUID(), second.accountPublicId(), second.tenantPublicId(), 1,
        "plans-isolation-second"));

    Instant now = Instant.now();
    for (int index = 0; index < 9; index++) {
      UUID invitation = insertInvitation("isolated" + index + "@example.com");
      assertThat(capacity.reserve(reservation(invitation, index, now)).status())
          .isEqualTo(TenantUserCapacityStatus.RESERVED);
    }

    var first = capacity.inspect(new TenantUserCapacityRequest(1, null));
    var secondSnapshot = capacity.inspect(new TenantUserCapacityRequest(second.tenantId(), null));

    assertThat(first.status()).isEqualTo(TenantUserCapacityStatus.LIMIT_REACHED);
    assertThat(first.used()).isEqualTo(10);
    assertThat(secondSnapshot.status()).isEqualTo(TenantUserCapacityStatus.AVAILABLE);
    assertThat(secondSnapshot.occupied()).isEqualTo(1);
    assertThat(secondSnapshot.reserved()).isZero();
    assertThat(countWhere("plans_personalContractHolder", "idUser = 1")).isOne();
    assertThat(countWhere("plans_tenantUserCapacityReservation", "idTenant = 2")).isZero();
  }

  @Test
  void shouldKeepOccupancyWhenMembershipOrIdentityStopsBeingActive() {
    contracts.ensure(new TenantContractBootstrapRequest(
        UUID.randomUUID(), accountPublicId, tenantPublicId, 1, "plans-permanent-occupancy"));

    assertPermanentOccupancy();
    jdbc.update("""
        UPDATE membership_accountMembership
           SET status = 'SUSPENDED'
         WHERE idAccount = 1 AND idUser = 1
        """);
    assertPermanentOccupancy();
    jdbc.update("""
        UPDATE membership_accountMembership
           SET status = 'REMOVED', currentMarker = NULL, endedAt = CURRENT_TIMESTAMP(6)
         WHERE idAccount = 1 AND idUser = 1
        """);
    assertPermanentOccupancy();
    jdbc.update("""
        UPDATE membership_accountMembership
           SET status = 'LEFT'
         WHERE idAccount = 1 AND idUser = 1
        """);
    jdbc.update("UPDATE identity_user SET status = 'BLOCKED' WHERE id = 1");
    assertPermanentOccupancy();
  }

  private JdbcTenantUserCapacityService serviceInstance() {
    return new JdbcTenantUserCapacityService(
        dataSource, new DataSourceTransactionManager(dataSource), new SimpleMeterRegistry());
  }

  private InvitationCapacityRequest reservation(UUID invitationId, int index, Instant now) {
    return new InvitationCapacityRequest(
        1, invitationId, "recipient-fingerprint-" + index, null,
        now, now.plus(2, ChronoUnit.DAYS), "plans-reserve-it-" + index);
  }

  private void insertBaseContext() {
    insertUser(1, "founder@example.com");
    tenantPublicId = UUID.randomUUID();
    accountPublicId = UUID.randomUUID();
    jdbc.update("INSERT INTO account_tenant (publicId, status) VALUES (UUID_TO_BIN(?), 'OPERATIONAL')",
        tenantPublicId.toString());
    jdbc.update("""
        INSERT INTO account_account
          (publicId, idTenant, founderUserId, displayName, baseCurrency, timeZoneId, status)
        VALUES (UUID_TO_BIN(?), 1, 1, 'Plans core', 'BRL', 'America/Sao_Paulo', 'ACTIVE')
        """, accountPublicId.toString());
    jdbc.update("""
        INSERT INTO membership_accountMembership
          (publicId, idAccount, idUser, roleType, originType, status,
           currentMarker, startedAt)
        VALUES (UUID_TO_BIN(UUID()), 1, 1, 'ACCOUNT_ADMINISTRATOR', 'FOUNDER',
                'ACTIVE', 1, CURRENT_TIMESTAMP(6))
        """);
  }

  private UUID insertInvitation(String email) {
    return insertInvitation(1, 1, email);
  }

  private UUID insertInvitation(long accountId, long inviterMembershipId, String email) {
    UUID publicId = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO membership_invitation
          (publicId, idAccount, inviterMembershipId, normalizedEmail,
           proposedRoleType, proofDigest, proofKeyId, status, pendingMarker, expiresAt)
        VALUES (UUID_TO_BIN(?), ?, ?, ?, 'COLLABORATOR',
                UNHEX(SHA2(?, 256)), 'test-key', 'PENDING', 1,
                TIMESTAMPADD(DAY, 3, CURRENT_TIMESTAMP(6)))
        """, publicId.toString(), accountId, inviterMembershipId, email, publicId.toString());
    return publicId;
  }

  private TenantContext insertTenantContext(String displayName) {
    UUID newTenantPublicId = UUID.randomUUID();
    UUID newAccountPublicId = UUID.randomUUID();
    jdbc.update("INSERT INTO account_tenant (publicId, status) VALUES (UUID_TO_BIN(?), 'OPERATIONAL')",
        newTenantPublicId.toString());
    Long newTenantId = jdbc.queryForObject(
        "SELECT idTenant FROM account_tenant WHERE publicId = UUID_TO_BIN(?)",
        Long.class, newTenantPublicId.toString());
    jdbc.update("""
        INSERT INTO account_account
          (publicId, idTenant, founderUserId, displayName, baseCurrency, timeZoneId, status)
        VALUES (UUID_TO_BIN(?), ?, 1, ?, 'BRL', 'America/Sao_Paulo', 'ACTIVE')
        """, newAccountPublicId.toString(), newTenantId, displayName);
    Long newAccountId = jdbc.queryForObject(
        "SELECT idAccount FROM account_account WHERE publicId = UUID_TO_BIN(?)",
        Long.class, newAccountPublicId.toString());
    jdbc.update("""
        INSERT INTO membership_accountMembership
          (publicId, idAccount, idUser, roleType, originType, status,
           currentMarker, startedAt)
        VALUES (UUID_TO_BIN(UUID()), ?, 1, 'ACCOUNT_ADMINISTRATOR', 'FOUNDER',
                'ACTIVE', 1, CURRENT_TIMESTAMP(6))
        """, newAccountId);
    return new TenantContext(newTenantId, newTenantPublicId, newAccountPublicId);
  }

  private void assertPermanentOccupancy() {
    var snapshot = capacity.inspect(new TenantUserCapacityRequest(1, 1L));
    assertThat(snapshot.status()).isEqualTo(TenantUserCapacityStatus.ALREADY_OCCUPIED);
    assertThat(snapshot.occupied()).isOne();
    assertThat(countWhere("plans_tenantUserCapacityOccupancy", "idTenant = 1 AND idUser = 1"))
        .isOne();
  }

  private void insertUser(long id, String email) {
    jdbc.update("""
        INSERT INTO identity_user (id, email, normalizedEmail, status)
        VALUES (?, ?, ?, 'ACTIVE')
        """, id, email, email);
  }

  private long count(String table) {
    Long value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    return value == null ? 0 : value;
  }

  private long countWhere(String table, String condition) {
    Long value = jdbc.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE " + condition, Long.class);
    return value == null ? 0 : value;
  }

  private record TenantContext(long tenantId, UUID tenantPublicId, UUID accountPublicId) {
  }
}
