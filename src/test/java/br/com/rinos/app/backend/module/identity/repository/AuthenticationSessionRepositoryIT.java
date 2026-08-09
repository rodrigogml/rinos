package br.com.rinos.app.backend.module.identity.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationWindowOperationEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthSessionAccessStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthSessionRevocationReasonEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthSessionStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.service.AuthenticationAssurancePolicyService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationFlowService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationMethodAvailabilityService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationSessionLifecycleService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationWindowService;
import br.com.rinos.app.backend.module.identity.service.AuthSessionService;
import br.com.rinos.app.backend.module.identity.service.IdentityAuditService;
import br.com.rinos.app.backend.module.identity.service.IdentityReferenceService;
import br.com.rinos.app.backend.module.identity.service.LegalConsentService;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationWindowDecisionVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowVerifiedMethodVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationSessionLifecycleVO;
import br.com.rinos.app.backend.module.identity.vo.IssuedAuthSessionVO;
import br.com.rinos.app.backend.module.identity.vo.IssuedAuthenticationFlowVO;
import br.com.rinos.app.backend.module.identity.vo.LegalRequirementStatusVO;
import br.com.rinos.app.backend.module.identity.vo.VerifiedAuthSessionMethodVO;
import br.com.rinos.app.config.AuthenticationAbusePropertiesConfig;
import br.com.rinos.app.config.AuthenticationRetentionPropertiesConfig;
import br.com.rinos.app.config.AuthenticationSessionPropertiesConfig;
import br.com.rinos.app.testsupport.mysql.MySqlTestDatabase;
import br.eng.rodrigogml.rfw.authentication.service.RFWOpaqueTokenService;

/**
 * Comprova no MySQL a revogação e a contagem compartilhadas entre instâncias.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@DisplayName("Concorrência persistente das sessões e janelas de autenticação")
class AuthenticationSessionRepositoryIT {

  private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");
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
  void resetDatabase() {
    Assumptions.assumeTrue(
        testDatabase != null,
        "Configure o MySQL externo de testes ou disponibilize Docker para executar este gate.");
    dataSource = testDatabase.recreateSchema();
    new ResourceDatabasePopulator(new ClassPathResource("db/global/init/01-ddl.sql"))
        .execute(dataSource);
  }

  @Test
  void revoke_shouldChooseOneWinner_betweenApplicationInstances() {
    contextRunner().run(context -> {
      TransactionTemplate transaction = transaction(context);
      UserRepository userRepository = context.getBean(UserRepository.class);
      AuthSessionService service = sessionService(context);
      Long userId = activeUser(transaction, userRepository, "session@example.test");
      IssuedAuthSessionVO issued = transaction.execute(status -> service.issue(
          userId,
          AuthenticationMethodEnum.PASSWORD,
          AuthenticationAssuranceEnum.SINGLE_FACTOR,
          List.of(new VerifiedAuthSessionMethodVO(
              AuthenticationMethodEnum.PASSWORD, NOW, null)),
          false,
          NOW,
          "Firefox on Linux",
          new byte[] {127, 0, 0, 1},
          new byte[32],
          UUID.randomUUID()));

      List<Boolean> results = compete(() -> transaction(context).execute(status -> service.revoke(
          userId,
          issued.publicReference(),
          AuthSessionRevocationReasonEnum.USER_REQUEST,
          NOW.plusSeconds(30),
          UUID.randomUUID())));

      assertThat(results).containsExactlyInAnyOrder(true, false);
      AuthSessionAccessStatusEnum revokedStatus = transaction.execute(status -> service.access(
          issued.cookieValue(), false, NOW.plusSeconds(31), UUID.randomUUID()).status());
      assertThat(revokedStatus)
          .isEqualTo(AuthSessionAccessStatusEnum.REVOKED);
    });
  }

  @Test
  void registerFailure_shouldSerializeCounters_betweenApplicationInstances() {
    contextRunner().run(context -> {
      TransactionTemplate transaction = transaction(context);
      AuthenticationWindowService service = new AuthenticationWindowService(
          context.getBean(AuthenticationWindowRepository.class), abuseProperties());
      byte[] digest = new byte[32];
      digest[0] = 42;

      List<Integer> results = compete(() -> transaction(context).execute(status ->
          service.registerFailure(
              digest,
              "v1",
              AuthenticationWindowOperationEnum.SIGN_IN,
              NOW).failureCount()));
      AuthenticationWindowDecisionVO finalDecision = transaction.execute(status -> service.inspect(
          digest,
          "v1",
          AuthenticationWindowOperationEnum.SIGN_IN,
          NOW.plusSeconds(1)));

      assertThat(results).containsExactlyInAnyOrder(1, 2);
      assertThat(finalDecision.failureCount()).isEqualTo(2);
      assertThat(finalDecision.turnstileRequired()).isFalse();
    });
  }

  @Test
  void access_shouldExpireAtIdleBoundary_andRejectValidatorReplay() {
    contextRunner().run(context -> {
      TransactionTemplate transaction = transaction(context);
      UserRepository userRepository = context.getBean(UserRepository.class);
      AuthSessionService service = sessionService(context);
      Long firstUserId = activeUser(transaction, userRepository, "expiry@example.test");
      IssuedAuthSessionVO expiring = issue(transaction, service, firstUserId);

      AuthSessionAccessStatusEnum expiredStatus = transaction.execute(status -> service.access(
          expiring.cookieValue(), false, expiring.idleExpiresAt(), UUID.randomUUID()).status());
      assertThat(expiredStatus)
          .isEqualTo(AuthSessionAccessStatusEnum.EXPIRED);

      Long secondUserId = activeUser(transaction, userRepository, "replay@example.test");
      IssuedAuthSessionVO replayed = issue(transaction, service, secondUserId);
      String knownSelector = replayed.cookieValue().substring(
          0, replayed.cookieValue().indexOf('.'));
      AuthSessionAccessStatusEnum replayStatus = transaction.execute(status -> service.access(
          knownSelector + ".invalid-validator",
          false,
          NOW.plusSeconds(1),
          UUID.randomUUID()).status());
      assertThat(replayStatus)
          .isEqualTo(AuthSessionAccessStatusEnum.REJECTED);
      AuthSessionAccessStatusEnum persistedStatus = transaction.execute(status -> service.access(
          replayed.cookieValue(), false, NOW.plusSeconds(2), UUID.randomUUID()).status());
      assertThat(persistedStatus)
          .isEqualTo(AuthSessionAccessStatusEnum.REVOKED);
    });
  }

  @Test
  void lifecycle_shouldConsumeFlowOnlyWhenPreparedSessionIsPublished() {
    contextRunner().run(context -> {
      TransactionTemplate transaction = transaction(context);
      UserRepository userRepository = context.getBean(UserRepository.class);
      Long userId = activeUser(transaction, userRepository, "lifecycle@example.test");
      RFWOpaqueTokenService tokens = new RFWOpaqueTokenService();
      IdentityAuditService audit = new IdentityAuditService(
          context.getBean(IdentityEventRepository.class));
      AuthenticationFlowService flows = new AuthenticationFlowService(
          context.getBean(AuthenticationFlowRepository.class),
          context.getBean(AuthenticationFlowMethodRepository.class),
          context.getBean(AuthenticationProofRepository.class),
          userRepository,
          tokens,
          audit);
      LegalConsentService legal = mock(LegalConsentService.class);
      AuthenticationMethodAvailabilityService availability = mock(
          AuthenticationMethodAvailabilityService.class);
      when(legal.evaluateRequiredConsents(userId, NOW))
          .thenReturn(new LegalRequirementStatusVO(List.of(1L, 2L), List.of()));
      when(availability.availableMethods(userId))
          .thenReturn(Set.of(AuthenticationMethodEnum.PASSWORD));
      AuthenticationSessionLifecycleService lifecycle =
          new AuthenticationSessionLifecycleService(
              context.getBean(AuthSessionRepository.class),
              context.getBean(AuthSessionMethodRepository.class),
              context.getBean(AuthenticationFlowRepository.class),
              userRepository,
              flows,
              new AuthenticationAssurancePolicyService(),
              availability,
              legal,
              tokens,
              new IdentityReferenceService(),
              audit,
              sessionProperties());
      IssuedAuthenticationFlowVO issued = transaction.execute(status -> flows.issue(
          userId,
          AuthenticationFlowPurposeEnum.SIGN_IN,
          AuthenticationMethodEnum.PASSWORD,
          AuthenticationAssuranceEnum.SINGLE_FACTOR,
          Set.of(),
          List.of(new AuthenticationFlowVerifiedMethodVO(
              AuthenticationMethodEnum.PASSWORD, NOW.minusSeconds(1), null)),
          false,
          NOW.minusSeconds(10),
          NOW.plusSeconds(300),
          UUID.randomUUID()));

      AuthenticationSessionLifecycleVO prepared = transaction.execute(status -> lifecycle.prepare(
          issued.reference(),
          AuthenticationFlowPurposeEnum.SIGN_IN,
          userId,
          false,
          new byte[] {127, 0, 0, 1},
          "Browser/1.0",
          NOW));

      assertThat(context.getBean(AuthSessionRepository.class).findAll())
          .singleElement()
          .satisfies(session -> assertThat(session.getStatus())
              .isEqualTo(AuthSessionStatusEnum.PREPARED));
      assertThat(context.getBean(AuthenticationFlowRepository.class).findAll())
          .singleElement()
          .satisfies(flow -> assertThat(flow.getStatus())
              .isEqualTo(AuthenticationFlowStatusEnum.OPEN));
      assertThat(context.getBean(IdentityEventRepository.class).findAll())
          .extracting(event -> event.getEventType())
          .doesNotContain(
              IdentityEventTypeEnum.AUTHENTICATION_SUCCEEDED,
              IdentityEventTypeEnum.AUTHENTICATION_SESSION_CREATED);

      transaction.executeWithoutResult(status -> lifecycle.publish(
          prepared.sessionReference(), NOW));

      assertThat(context.getBean(AuthSessionRepository.class).findAll())
          .singleElement()
          .satisfies(session -> assertThat(session.getStatus())
              .isEqualTo(AuthSessionStatusEnum.ACTIVE));
      assertThat(context.getBean(AuthenticationFlowRepository.class).findAll())
          .singleElement()
          .satisfies(flow -> assertThat(flow.getStatus())
              .isEqualTo(AuthenticationFlowStatusEnum.USED));
      assertThat(context.getBean(IdentityEventRepository.class).findAll())
          .extracting(event -> event.getEventType())
          .contains(
              IdentityEventTypeEnum.AUTHENTICATION_SUCCEEDED,
              IdentityEventTypeEnum.AUTHENTICATION_SESSION_CREATED);
    });
  }

  private AuthSessionService sessionService(ApplicationContext context) {
    return new AuthSessionService(
        context.getBean(AuthSessionRepository.class),
        context.getBean(AuthSessionMethodRepository.class),
        context.getBean(UserRepository.class),
        new RFWOpaqueTokenService(),
        new IdentityReferenceService(),
        new IdentityAuditService(context.getBean(IdentityEventRepository.class)),
        sessionProperties(),
        new AuthenticationRetentionPropertiesConfig(
            Duration.ofDays(30), Duration.ofDays(30), Duration.ofDays(365)));
  }

  private static IssuedAuthSessionVO issue(
      TransactionTemplate transaction,
      AuthSessionService service,
      Long userId) {
    return transaction.execute(status -> service.issue(
        userId,
        AuthenticationMethodEnum.PASSWORD,
        AuthenticationAssuranceEnum.SINGLE_FACTOR,
        List.of(new VerifiedAuthSessionMethodVO(AuthenticationMethodEnum.PASSWORD, NOW, null)),
        false,
        NOW,
        null,
        null,
        null,
        UUID.randomUUID()));
  }

  private static Long activeUser(
      TransactionTemplate transaction,
      UserRepository repository,
      String email) {
    return transaction.execute(status -> repository.saveAndFlush(
        new UserEntity(email, email, UserStatusEnum.ACTIVE)).getId());
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataJpaRepositoriesAutoConfiguration.class))
        .withUserConfiguration(RepositoryTestConfig.class)
        .withPropertyValues(
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.jpa.hibernate.naming.physical-strategy="
                + "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
            "spring.jpa.properties.hibernate.jdbc.time_zone=UTC")
        .withBean(DataSource.class, () -> dataSource);
  }

  private static AuthenticationSessionPropertiesConfig sessionProperties() {
    return new AuthenticationSessionPropertiesConfig(
        Duration.ofHours(12),
        Duration.ofMinutes(30),
        Duration.ofDays(30),
        Duration.ofDays(7),
        Duration.ofMinutes(5),
        Duration.ofMinutes(15),
        "RINOS_AUTH",
        true,
        "Strict");
  }

  private static AuthenticationAbusePropertiesConfig abuseProperties() {
    return new AuthenticationAbusePropertiesConfig(
        3,
        Duration.ofMinutes(15),
        Duration.ofMinutes(15),
        Duration.ofSeconds(1),
        Duration.ofMinutes(15),
        Duration.ofDays(30));
  }

  private static TransactionTemplate transaction(ApplicationContext context) {
    return new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
  }

  private static <T> List<T> compete(Callable<T> operation) {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<T> first = executor.submit(() -> {
        start.await(10, TimeUnit.SECONDS);
        return operation.call();
      });
      Future<T> second = executor.submit(() -> {
        start.await(10, TimeUnit.SECONDS);
        return operation.call();
      });
      start.countDown();
      return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
    } catch (Exception exception) {
      throw new IllegalStateException("Concurrent authentication operation failed", exception);
    } finally {
      executor.shutdownNow();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EntityScan(basePackageClasses = UserEntity.class)
  @EnableJpaRepositories(basePackageClasses = UserRepository.class)
  static class RepositoryTestConfig {
  }
}
