package br.com.rinos.app.backend.module.access.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.rinos.app.api.module.access.enums.AccessRuleEffect;
import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.access.entity.AccessRuleEntity;
import br.com.rinos.app.backend.module.access.enums.AccessRuleOriginType;
import br.com.rinos.app.backend.module.access.service.AccessContextRevisionService;
import br.com.rinos.app.backend.module.access.service.AccessContextCacheInvalidationService;
import br.com.rinos.app.backend.module.access.service.AccessAdministrationMutationService;
import br.com.rinos.app.backend.module.access.service.AccessAdministrationMutationResult;
import br.com.rinos.app.backend.module.access.service.AccessGroupMutationCommand;
import br.com.rinos.app.backend.module.access.service.AccessGroupSubjectMutationCommand;
import br.com.rinos.app.backend.module.access.service.AccessMutationMetadata;
import br.com.rinos.app.backend.module.access.service.AccessRuleDeactivationCommand;
import br.com.rinos.app.backend.module.access.service.AccessRuleMutationCommand;
import br.com.rinos.app.backend.module.access.service.AccessRuleMutationService;
import br.com.rinos.app.backend.module.access.service.AccessAdministrativeFactorContinuityAdapter;
import br.com.rinos.app.backend.module.access.service.AdministrativeContinuityEvaluationService;
import br.com.rinos.app.backend.module.access.service.AdministrativeContinuityEvaluator;
import br.com.rinos.app.backend.module.access.service.AccountMembershipAccessPort;
import br.com.rinos.app.backend.module.access.service.AccountMembershipAccessSnapshot;
import br.com.rinos.app.backend.module.access.service.FoundingMembershipAccessSnapshot;
import br.com.rinos.app.backend.module.access.service.AuthorizationSnapshotCache;
import br.com.rinos.app.backend.module.access.service.GlobalAccessBootstrapService;
import br.com.rinos.app.backend.module.access.enums.GlobalAccessBootstrapStatus;
import br.com.rinos.app.backend.module.access.enums.AccessAdministrationAction;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.service.AdministrativeIdentityContinuityPort;
import br.com.rinos.app.backend.module.identity.service.AuthSessionService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationMethodInventoryService;
import br.com.rinos.app.backend.module.identity.service.EmailNormalizationService;
import br.com.rinos.app.backend.module.identity.service.UserLifecycleService;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationMethodInventoryVO;
import br.com.rinos.app.api.module.plans.port.PersonalContractBootstrapPort;
import br.com.rinos.app.backend.module.account.entity.AccountEntity;
import br.com.rinos.app.backend.module.account.repository.AccountRepository;
import br.com.rinos.app.backend.module.membership.entity.AccountMembershipEntity;
import br.com.rinos.app.backend.module.membership.repository.AccountMembershipRepository;
import br.com.rinos.app.config.AccessBootstrapPropertiesConfig;
import br.com.rinos.app.config.AccessCachePropertiesConfig;
import br.com.rinos.app.testsupport.mysql.MySqlTestDatabase;

/** Gate MySQL da identidade corrente, histórico e revisão atômica de regras. */
class AccessRulePersistenceIT {

  private static MySqlTestDatabase testDatabase;
  private DataSource dataSource;

  @BeforeAll
  static void startDatabase() {
    testDatabase = MySqlTestDatabase.openIfAvailable().orElse(null);
  }

  @AfterAll
  static void stopDatabase() {
    if (testDatabase != null) {
      testDatabase.close();
    }
  }

  @BeforeEach
  void resetDatabase() throws SQLException {
    Assumptions.assumeTrue(testDatabase != null,
        "Configure o MySQL externo de testes ou disponibilize Docker para executar este gate.");
    dataSource = testDatabase.recreateSchema();
    new ResourceDatabasePopulator(new ClassPathResource("db/global/init/01-ddl.sql"))
        .execute(dataSource);
    execute("""
        INSERT INTO identity_user (email, normalizedEmail, status)
        VALUES ('admin@rinos.com.br', 'admin@rinos.com.br', 'ACTIVE'),
               ('actor@example.com', 'actor@example.com', 'ACTIVE')
        """);
    execute("""
        INSERT INTO access_keyCategory
          (categoryCode, scopeType, nameI18nKey, descriptionI18nKey, displayOrder, status)
        VALUES ('global.platform', 'GLOBAL', 'category.name', 'category.description', 0, 'ACTIVE'),
               ('tenant.foundation', 'TENANT', 'tenant.name', 'tenant.description', 0, 'ACTIVE')
        """);
    execute("""
        INSERT INTO access_key
          (accessKeyCode, scopeType, idAccessKeyCategory, ownerModule, nameI18nKey,
           descriptionI18nKey, status, descriptorVersion)
        VALUES ('global.platform.directory.view', 'GLOBAL', 1, 'test', 'key.name',
                'key.description', 'ACTIVE', 1),
               ('tenant.foundation.access.manage', 'TENANT', 2, 'test', 'tenant.key.name',
                'tenant.key.description', 'ACTIVE', 1)
        """);
  }

  @Test
  void apply_shouldReplaceEffectWithoutDuplicatingCurrentRule() {
    contextRunner().run(context -> {
      AccessRuleMutationService service = context.getBean(AccessRuleMutationService.class);

      service.apply(command(AccessRuleEffect.PERMITIR, "first"));
      service.apply(command(AccessRuleEffect.BLOQUEAR, "second"));

      assertThat(count("access_rule")).isOne();
      assertThat(count("access_ruleHistory")).isEqualTo(2);
      assertThat(count("access_auditEvent")).isEqualTo(2);
      assertThat(scalar("SELECT revision FROM access_contextRevision WHERE scopeType='GLOBAL'"))
          .isEqualTo(2);
    });
  }

  @Test
  void apply_shouldRollbackRuleHistoryAndRevision_whenHistoryWriteFails() throws SQLException {
    contextRunner().run(context -> {
      AccessRuleMutationService service = context.getBean(AccessRuleMutationService.class);

      assertThatThrownBy(() -> service.apply(command(
          AccessRuleEffect.PERMITIR, "x".repeat(101))))
          .isInstanceOf(DataAccessException.class);

      assertThat(count("access_rule")).isZero();
      assertThat(count("access_ruleHistory")).isZero();
      assertThat(count("access_auditEvent")).isZero();
      assertThat(scalar("SELECT revision FROM access_contextRevision WHERE scopeType='GLOBAL'"))
          .isZero();
    });
  }

  @Test
  void deactivateRule_shouldKeepCurrentRowAndAppendHistory() {
    contextRunner().run(context -> {
      AccessRuleMutationService service = context.getBean(AccessRuleMutationService.class);
      long ruleId = service.apply(command(AccessRuleEffect.PERMITIR, "create")).ruleId();

      service.deactivate(new AccessRuleDeactivationCommand(
          ruleId, AccessScope.GLOBAL, null, metadata("deactivate")));

      assertThat(count("access_rule")).isOne();
      assertThat(count("access_ruleHistory")).isEqualTo(2);
      assertThat(scalar("SELECT COUNT(*) FROM access_rule WHERE status='INACTIVE'"))
          .isOne();
      assertThat(scalar("SELECT revision FROM access_contextRevision WHERE scopeType='GLOBAL'"))
          .isEqualTo(2);
    });
  }

  @Test
  void apply_shouldRollbackEffectChangeWhenAdministrativeContinuityWouldBeLost()
      throws SQLException {
    execute("""
        INSERT INTO account_tenant (publicId, status)
        VALUES (UUID_TO_BIN(UUID()), 'OPERATIONAL')
        """);
    execute("""
        INSERT INTO account_account
          (publicId, idTenant, founderUserId, displayName, baseCurrency, timeZoneId, status)
        VALUES (UUID_TO_BIN(UUID()), 1, 1, 'Conta', 'BRL', 'America/Sao_Paulo', 'ACTIVE')
        """);
    execute("""
        INSERT INTO membership_accountMembership
          (publicId, idAccount, idUser, roleType, originType, status, currentMarker, startedAt)
        VALUES (UUID_TO_BIN(UUID()), 1, 1, 'ACCOUNT_ADMINISTRATOR', 'FOUNDER',
                'ACTIVE', 1, '2026-08-15 12:00:00')
        """);

    contextRunner().run(context -> {
      AccessRuleMutationService service = context.getBean(AccessRuleMutationService.class);
      AdministrativeContinuityEvaluator continuity =
          context.getBean(AdministrativeContinuityEvaluator.class);
      AccessRuleMutationCommand permit = tenantCommand(AccessRuleEffect.PERMITIR, "tenant-create");
      service.apply(permit);
      org.mockito.Mockito.when(continuity.evaluateContext(
          org.mockito.ArgumentMatchers.eq(AccessScope.TENANT),
          org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
          .thenReturn(br.com.rinos.app.backend.module.membership.service.MembershipContinuityDecision.deny());

      assertThatThrownBy(() -> service.apply(
          tenantCommand(AccessRuleEffect.BLOQUEAR, "tenant-block")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("continuity would be lost");

      assertThat(scalar("SELECT COUNT(*) FROM access_rule WHERE effect='PERMITIR' AND status='ACTIVE'"))
          .isOne();
      assertThat(count("access_ruleHistory")).isOne();
      assertThat(count("access_auditEvent")).isOne();
      assertThat(scalar("SELECT revision FROM access_contextRevision WHERE scopeType='TENANT' AND idTenant=1"))
          .isOne();
    });
  }

  @Test
  void administration_shouldPersistLogicalLifecycleAndRevisionAtomically() {
    contextRunner().run(context -> {
      AccessAdministrationMutationService service =
          context.getBean(AccessAdministrationMutationService.class);
      AccessAdministrationMutationResult group = service.mutateGroup(
          new AccessGroupMutationCommand(
              AccessAdministrationAction.CREATE, null, AccessScope.GLOBAL, null,
              "Operadores", "Teste", false, null, metadata("group-create")));
      AccessAdministrationMutationResult subject = service.mutateSubject(
          new AccessGroupSubjectMutationCommand(
              AccessAdministrationAction.ASSIGN, null, group.targetId(), 1L, null,
              null, null, metadata("subject-assign")));
      service.mutateSubject(new AccessGroupSubjectMutationCommand(
          AccessAdministrationAction.END, subject.targetId(), null, null, null,
          null, null, metadata("subject-end")));
      service.mutateGroup(new AccessGroupMutationCommand(
          AccessAdministrationAction.DEACTIVATE, group.targetId(), AccessScope.GLOBAL, null,
          null, null, false, null, metadata("group-deactivate")));

      assertThat(scalar("SELECT COUNT(*) FROM access_group WHERE status='INACTIVE'"))
          .isOne();
      assertThat(scalar("SELECT COUNT(*) FROM access_groupSubject WHERE status='ENDED'"))
          .isOne();
      assertThat(count("access_auditEvent")).isEqualTo(4);
      assertThat(scalar("SELECT revision FROM access_contextRevision WHERE scopeType='GLOBAL'"))
          .isEqualTo(4);
    });
  }

  @Test
  void bootstrap_shouldCompleteOnceAndIgnoreLaterAttempts() throws SQLException {
    prepareGlobalBootstrapCandidate();
    contextRunner().run(context -> {
      GlobalAccessBootstrapService service = context.getBean(GlobalAccessBootstrapService.class);
      var first = service.attempt(
          UUID.fromString("63f90467-b6b9-4d44-9722-f119863b87bd"),
          Instant.parse("2026-08-16T12:00:00Z"));

      assertThat(first.status()).isEqualTo(GlobalAccessBootstrapStatus.COMPLETED);
      assertThat(scalar("SELECT COUNT(*) FROM access_group WHERE protectedGroup=1 AND scopeType='GLOBAL'"))
          .isOne();
      assertThat(scalar("SELECT COUNT(*) FROM access_groupSubject WHERE idUser=1 AND status='ACTIVE'"))
          .isOne();
      assertThat(scalar("SELECT COUNT(*) FROM access_rule WHERE effect='PERMITIR' AND status='ACTIVE'"))
          .isOne();
      assertThat(scalar("SELECT COUNT(*) FROM identity_user WHERE id=1 AND globalActorRole='SYSTEM_ADMINISTRATOR'"))
          .isOne();
      assertThat(scalar("SELECT COUNT(*) FROM access_bootstrap WHERE status='COMPLETED' AND completedByUserId=1"))
          .isOne();
      assertThat(scalar("SELECT COUNT(*) FROM access_auditEvent WHERE eventType='ACCESS_BOOTSTRAP_COMPLETED'"))
          .isOne();
    });
    contextRunner("other-administrator@example.com").run(context -> {
      var result = context.getBean(GlobalAccessBootstrapService.class).attempt(
          UUID.fromString("d9a35f29-3916-468e-b08f-ea4c13e5f332"),
          Instant.parse("2026-08-16T12:01:00Z"));
      assertThat(result.status()).isEqualTo(GlobalAccessBootstrapStatus.ALREADY_COMPLETED);
      assertThat(result.administratorUserId()).isEqualTo(1L);
      assertThat(scalar("SELECT COUNT(*) FROM access_groupSubject WHERE idUser=1"))
          .isOne();
    });
  }

  @Test
  void bootstrap_shouldSerializeConcurrentAttempts() throws SQLException {
    prepareGlobalBootstrapCandidate();
    contextRunner().run(context -> {
      GlobalAccessBootstrapService service = context.getBean(GlobalAccessBootstrapService.class);
      var executor = Executors.newFixedThreadPool(2);
      try {
        Callable<GlobalAccessBootstrapStatus> first = () -> service.attempt(
            UUID.randomUUID(), Instant.parse("2026-08-16T12:00:00Z")).status();
        Callable<GlobalAccessBootstrapStatus> second = () -> service.attempt(
            UUID.randomUUID(), Instant.parse("2026-08-16T12:00:00Z")).status();
        List<GlobalAccessBootstrapStatus> results = executor.invokeAll(List.of(first, second))
            .stream().map(future -> {
              try {
                return future.get();
              } catch (Exception exception) {
                throw new IllegalStateException(exception);
              }
            }).toList();
        assertThat(results).containsExactlyInAnyOrder(
            GlobalAccessBootstrapStatus.COMPLETED,
            GlobalAccessBootstrapStatus.ALREADY_COMPLETED);
        assertThat(scalar("SELECT COUNT(*) FROM access_groupSubject WHERE idUser=1"))
            .isOne();
        assertThat(scalar("SELECT COUNT(*) FROM access_auditEvent WHERE eventType='ACCESS_BOOTSTRAP_COMPLETED'"))
            .isOne();
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(exception);
      } finally {
        executor.shutdownNow();
      }
    });
  }

  @Test
  void groupVersion_shouldRejectOneOfTwoConcurrentUpdates() {
    contextRunner().run(context -> {
      AccessAdministrationMutationService service =
          context.getBean(AccessAdministrationMutationService.class);
      long groupId = service.mutateGroup(new AccessGroupMutationCommand(
          AccessAdministrationAction.CREATE, null, AccessScope.GLOBAL, null,
          "Concorrente", null, false, null, metadata("group-concurrent"))).targetId();
      AccessGroupRepository groups = context.getBean(AccessGroupRepository.class);
      PlatformTransactionManager transactionManager =
          context.getBean(PlatformTransactionManager.class);
      CyclicBarrier loaded = new CyclicBarrier(2);
      var executor = Executors.newFixedThreadPool(2);
      try {
        Callable<Boolean> first = concurrentGroupUpdate(
            groups, transactionManager, loaded, groupId, "Nome A");
        Callable<Boolean> second = concurrentGroupUpdate(
            groups, transactionManager, loaded, groupId, "Nome B");
        List<Boolean> results = executor.invokeAll(List.of(first, second), 10, TimeUnit.SECONDS)
            .stream().map(future -> {
              try {
                return future.get();
              } catch (Exception exception) {
                throw new IllegalStateException(exception);
              }
            }).toList();
        assertThat(results).containsExactlyInAnyOrder(true, false);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(exception);
      } finally {
        executor.shutdownNow();
      }
    });
  }

  @Test
  void identityStateTransition_shouldSerializeWithRuleMutationAndKeepOneAdministrator() throws SQLException {
    execute("""
        INSERT INTO identity_user (email, normalizedEmail, status)
        VALUES ('administrator-two@example.com', 'administrator-two@example.com', 'ACTIVE')
        """);
    prepareGlobalBootstrapCandidate();

    contextRunnerWithActualContinuity().run(context -> {
      AccessRuleMutationService rules = context.getBean(AccessRuleMutationService.class);
      rules.apply(globalUserCommand(1L, AccessRuleEffect.PERMITIR, "first-administrator"));
      rules.apply(globalUserCommand(3L, AccessRuleEffect.PERMITIR, "second-administrator"));
      UserRepository users = context.getBean(UserRepository.class);
      PlatformTransactionManager transactions = context.getBean(PlatformTransactionManager.class);
      AdministrativeIdentityContinuityPort continuity =
          context.getBean(AdministrativeIdentityContinuityPort.class);
      UserLifecycleService lifecycle = new UserLifecycleService(
          org.mockito.Mockito.mock(AuthSessionService.class),
          org.mockito.Mockito.mock(PersonalContractBootstrapPort.class), users, continuity);
      CyclicBarrier ready = new CyclicBarrier(2);
      java.util.concurrent.ExecutorService executor = Executors.newFixedThreadPool(2);
      try {
        Callable<Boolean> blockFirstIdentity = () -> transitionIdentity(
            lifecycle, users, transactions, ready, 1L);
        Callable<Boolean> blockSecondAdministratorRule = () -> mutateRule(
            rules, ready, globalUserCommand(3L, AccessRuleEffect.BLOQUEAR, "block-second"));
        List<Boolean> results = executor.invokeAll(
            List.of(blockFirstIdentity, blockSecondAdministratorRule), 15, TimeUnit.SECONDS)
            .stream().map(future -> {
              try {
                return future.get();
              } catch (Exception exception) {
                throw new IllegalStateException(exception);
              }
            }).toList();

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(context.getBean(AdministrativeContinuityEvaluator.class)
            .inspectContext(AccessScope.GLOBAL, null, Instant.parse("2026-08-23T21:00:00Z")).allowed())
                .isTrue();
        assertThat(scalar("SELECT COUNT(*) FROM identity_user WHERE id IN (1, 3) AND status='ACTIVE'"))
            .isOne();
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(exception);
      } finally {
        executor.shutdownNow();
      }
    });
  }

  private ApplicationContextRunner contextRunner() {
    return contextRunner("admin@rinos.com.br");
  }

  private ApplicationContextRunner contextRunnerWithActualContinuity() {
    return contextRunner("admin@rinos.com.br", true);
  }

  private ApplicationContextRunner contextRunner(String bootstrapEmail) {
    return contextRunner(bootstrapEmail, false);
  }

  private ApplicationContextRunner contextRunner(String bootstrapEmail, boolean actualContinuity) {
    ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataJpaRepositoriesAutoConfiguration.class))
        .withUserConfiguration(RepositoryTestConfig.class)
        .withPropertyValues(
            "spring.jpa.hibernate.ddl-auto=none",
            "spring.jpa.hibernate.naming.physical-strategy="
                + "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
            "spring.jpa.properties.hibernate.jdbc.time_zone=UTC")
        .withBean(AuthorizationSnapshotCache.class, () -> new AuthorizationSnapshotCache(
            new AccessCachePropertiesConfig(10_000, Duration.ofMinutes(30))))
        .withBean(AccountMembershipAccessPort.class,
            () -> new AccountMembershipAccessPort() {
              @Override
              public AccountMembershipAccessSnapshot inspect(long membershipId) {
                return AccountMembershipAccessSnapshot.unavailable();
              }

              @Override
              public FoundingMembershipAccessSnapshot inspectFounder(
                  long accountId,
                  long founderUserId) {
                return FoundingMembershipAccessSnapshot.unavailable();
              }
            })
        .withBean(AccessBootstrapPropertiesConfig.class,
            () -> new AccessBootstrapPropertiesConfig(bootstrapEmail))
        .withBean(AuthenticationMethodInventoryService.class, () ->
            org.mockito.Mockito.mock(AuthenticationMethodInventoryService.class,
                invocation -> invocation.getMethod().getName().equals("inspect")
                    ? new AuthenticationMethodInventoryVO(true, 0, 0, 1, true, 0)
                    : null))
        .withBean(DataSource.class, () -> dataSource);
    if (actualContinuity) {
      return runner.withUserConfiguration(ActualContinuityTestConfig.class);
    }
    return runner.withBean(AdministrativeContinuityEvaluator.class, () ->
        org.mockito.Mockito.mock(AdministrativeContinuityEvaluator.class,
            invocation -> invocation.getMethod().getReturnType()
                == br.com.rinos.app.backend.module.membership.service.MembershipContinuityDecision.class
                    ? br.com.rinos.app.backend.module.membership.service.MembershipContinuityDecision.permit()
                    : null));
  }

  private void prepareGlobalBootstrapCandidate() throws SQLException {
    execute("UPDATE identity_user SET activatedAt='2026-08-16 11:00:00' WHERE id=1");
    execute("""
        INSERT INTO access_protectedGroupBaseline (scopeType, baselineVersion, status)
        VALUES ('GLOBAL', 1, 'ACTIVE')
        """);
    execute("""
        INSERT INTO access_protectedGroupBaselineKey (idProtectedGroupBaseline, idAccessKey)
        VALUES (1, 1)
        """);
  }

  private static AccessRuleMutationCommand command(AccessRuleEffect effect, String correlation) {
    return globalUserCommand(1L, effect, correlation);
  }

  private static AccessRuleMutationCommand globalUserCommand(
      long userId, AccessRuleEffect effect, String correlation) {
    return new AccessRuleMutationCommand(
        AccessScope.GLOBAL, null, AccessRuleOriginType.DIRECT_USER, userId, null, null,
        "global.platform.directory.view", effect, null, null, 2L, null, "test",
        correlation, Instant.parse("2026-08-15T12:00:00Z"));
  }

  private static Boolean transitionIdentity(
      UserLifecycleService lifecycle,
      UserRepository users,
      PlatformTransactionManager transactions,
      CyclicBarrier ready,
      long userId) {
    try {
      new TransactionTemplate(transactions).executeWithoutResult(status -> {
        await(ready);
        lifecycle.transition(
            users.findById(userId).orElseThrow(),
            UserStatusEnum.BLOCKED,
            IdentityTransitionOriginEnum.SYSTEM,
            "CONCURRENCY_TEST",
            Instant.parse("2026-08-23T21:00:00Z"),
            UUID.randomUUID());
      });
      return true;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private static Boolean mutateRule(
      AccessRuleMutationService rules,
      CyclicBarrier ready,
      AccessRuleMutationCommand command) {
    try {
      await(ready);
      rules.apply(command);
      return true;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private static void await(CyclicBarrier barrier) {
    try {
      barrier.await(5, TimeUnit.SECONDS);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static AccessRuleMutationCommand tenantCommand(
      AccessRuleEffect effect, String correlation) {
    return new AccessRuleMutationCommand(
        AccessScope.TENANT, 1L, AccessRuleOriginType.DIRECT_MEMBERSHIP, null, 1L, null,
        "tenant.foundation.access.manage", effect, null, null, 2L, null, "test",
        correlation, Instant.parse("2026-08-15T12:00:00Z"));
  }

  private static AccessMutationMetadata metadata(String correlation) {
    return new AccessMutationMetadata(
        2L, null, "test", correlation, Instant.parse("2026-08-15T12:00:00Z"));
  }

  private static Callable<Boolean> concurrentGroupUpdate(
      AccessGroupRepository groups,
      PlatformTransactionManager transactionManager,
      CyclicBarrier loaded,
      long groupId,
      String name) {
    return () -> {
      try {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
          var group = groups.findById(groupId).orElseThrow();
          try {
            loaded.await(5, TimeUnit.SECONDS);
          } catch (Exception exception) {
            throw new IllegalStateException(exception);
          }
          group.update(name, name.toLowerCase(java.util.Locale.ROOT), null);
          groups.saveAndFlush(group);
        });
        return true;
      } catch (RuntimeException exception) {
        return false;
      }
    };
  }

  private void execute(String sql) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private long count(String table) {
    return scalar("SELECT COUNT(*) FROM " + table);
  }

  private long scalar(String sql) {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    } catch (SQLException exception) {
      throw new IllegalStateException(exception);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement
  @EntityScan(basePackageClasses = {
      AccessRuleEntity.class, UserEntity.class, AccountEntity.class, AccountMembershipEntity.class
  })
  @EnableJpaRepositories(basePackageClasses = {
      AccessRuleRepository.class, UserRepository.class, AccountRepository.class,
      AccountMembershipRepository.class
  })
  @Import({
      AccessRuleMutationService.class,
      AccessAdministrationMutationService.class,
      GlobalAccessBootstrapService.class,
      EmailNormalizationService.class,
      AccessContextRevisionService.class,
      AccessContextCacheInvalidationService.class
  })
  static class RepositoryTestConfig {
  }

  @Configuration(proxyBeanMethods = false)
  @Import({
      AdministrativeContinuityEvaluationService.class,
      AccessAdministrativeFactorContinuityAdapter.class
  })
  static class ActualContinuityTestConfig {
  }
}
