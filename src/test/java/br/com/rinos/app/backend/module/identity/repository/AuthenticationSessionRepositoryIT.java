package br.com.rinos.app.backend.module.identity.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Base64;
import java.util.Map;
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
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.service.AuthenticationAssurancePolicyService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationAbuseProtectionService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationFlowService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationKeyringMacService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationKeyringService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationMethodAvailabilityService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationSessionLifecycleService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationWindowService;
import br.com.rinos.app.backend.module.identity.service.AuthSessionService;
import br.com.rinos.app.backend.module.identity.service.IdentityAuditService;
import br.com.rinos.app.backend.module.identity.service.IdentityReferenceService;
import br.com.rinos.app.backend.module.identity.service.LegalConsentService;
import br.com.rinos.app.backend.module.identity.service.LocalCredentialService;
import br.com.rinos.app.backend.module.identity.service.EmailNormalizationService;
import br.com.rinos.app.backend.module.identity.service.OriginAddressService;
import br.com.rinos.app.backend.module.identity.service.UserLifecycleService;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationAbuseDecisionVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationWindowDecisionVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowVerifiedMethodVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationSessionLifecycleVO;
import br.com.rinos.app.backend.module.identity.vo.AuthSessionSummaryVO;
import br.com.rinos.app.backend.module.identity.vo.IssuedAuthSessionVO;
import br.com.rinos.app.backend.module.identity.vo.IssuedPersistentLoginVO;
import br.com.rinos.app.backend.module.identity.vo.IssuedAuthenticationFlowVO;
import br.com.rinos.app.backend.module.identity.vo.LegalRequirementStatusVO;
import br.com.rinos.app.backend.module.identity.vo.VerifiedAuthSessionMethodVO;
import br.com.rinos.app.config.AuthenticationAbusePropertiesConfig;
import br.com.rinos.app.config.AuthenticationKeyringPropertiesConfig;
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
  void managedRevocation_shouldScopeTargetsAndPropagateAcrossInstances() {
    contextRunner().run(context -> {
      TransactionTemplate transaction = transaction(context);
      UserRepository users = context.getBean(UserRepository.class);
      AuthSessionService firstInstance = sessionService(context);
      AuthSessionService secondInstance = sessionService(context);
      Long ownerId = activeUser(transaction, users, "owner@example.test");
      Long otherUserId = activeUser(transaction, users, "other@example.test");
      IssuedAuthSessionVO current = issue(transaction, firstInstance, ownerId);
      IssuedAuthSessionVO firstRemote = issue(transaction, firstInstance, ownerId);
      IssuedAuthSessionVO secondRemote = issue(transaction, firstInstance, ownerId);
      IssuedAuthSessionVO foreign = issue(transaction, firstInstance, otherUserId);

      List<AuthSessionSummaryVO> managedSessions =
          transaction.execute(status -> firstInstance.listManaged(
              ownerId, current.publicReference(), NOW.plusSeconds(1)));
      assertThat(managedSessions)
          .extracting(session -> session.publicReference())
          .containsExactlyInAnyOrder(
              current.publicReference(), firstRemote.publicReference(), secondRemote.publicReference());

      var foreignAttempt = transaction.execute(status -> firstInstance.revokeManaged(
          ownerId,
          current.publicReference(),
          foreign.publicReference(),
          NOW.plusSeconds(2),
          UUID.randomUUID()));
      assertThat(foreignAttempt.revokedCount()).isZero();
      AuthSessionAccessStatusEnum foreignStatus = transaction.execute(status -> secondInstance.access(
          foreign.cookieValue(), false, NOW.plusSeconds(3), UUID.randomUUID()).status());
      assertThat(foreignStatus)
          .isEqualTo(AuthSessionAccessStatusEnum.ACTIVE);

      var oneRemote = transaction.execute(status -> firstInstance.revokeManaged(
          ownerId,
          current.publicReference(),
          firstRemote.publicReference(),
          NOW.plusSeconds(4),
          UUID.randomUUID()));
      assertThat(oneRemote.revokedCount()).isEqualTo(1);
      assertThat(oneRemote.currentSessionRevoked()).isFalse();
      AuthSessionAccessStatusEnum firstRemoteStatus = transaction.execute(status -> secondInstance.access(
          firstRemote.cookieValue(), false, NOW.plusSeconds(5), UUID.randomUUID()).status());
      assertThat(firstRemoteStatus)
          .isEqualTo(AuthSessionAccessStatusEnum.REVOKED);

      var others = transaction.execute(status -> secondInstance.revokeAllManaged(
          ownerId,
          current.publicReference(),
          true,
          NOW.plusSeconds(6),
          UUID.randomUUID()));
      assertThat(others.revokedCount()).isEqualTo(1);
      assertThat(others.currentSessionRevoked()).isFalse();
      AuthSessionAccessStatusEnum secondRemoteStatus = transaction.execute(status -> firstInstance.access(
          secondRemote.cookieValue(), false, NOW.plusSeconds(7), UUID.randomUUID()).status());
      assertThat(secondRemoteStatus)
          .isEqualTo(AuthSessionAccessStatusEnum.REVOKED);
      AuthSessionAccessStatusEnum currentStatus = transaction.execute(status -> firstInstance.access(
          current.cookieValue(), false, NOW.plusSeconds(7), UUID.randomUUID()).status());
      assertThat(currentStatus)
          .isEqualTo(AuthSessionAccessStatusEnum.ACTIVE);

      var all = transaction.execute(status -> firstInstance.revokeAllManaged(
          ownerId,
          current.publicReference(),
          false,
          NOW.plusSeconds(8),
          UUID.randomUUID()));
      assertThat(all.revokedCount()).isEqualTo(1);
      assertThat(all.currentSessionRevoked()).isTrue();
      AuthSessionAccessStatusEnum revokedCurrentStatus = transaction.execute(status -> secondInstance.access(
          current.cookieValue(), false, NOW.plusSeconds(9), UUID.randomUUID()).status());
      assertThat(revokedCurrentStatus)
          .isEqualTo(AuthSessionAccessStatusEnum.REVOKED);

      assertThatThrownBy(() -> transaction.execute(status -> firstInstance.listManaged(
          ownerId, current.publicReference(), NOW.plusSeconds(10))))
          .isInstanceOf(SecurityException.class);
    });
  }

  @Test
  void securityTransitions_shouldInvalidateSessionsAcrossInstances() {
    contextRunner().run(context -> {
      TransactionTemplate transaction = transaction(context);
      UserRepository users = context.getBean(UserRepository.class);
      AuthSessionService firstInstance = sessionService(context);
      AuthSessionService secondInstance = sessionService(context);

      Long blockedUserId = activeUser(transaction, users, "blocked@example.test");
      IssuedAuthSessionVO blockedSession = issue(transaction, firstInstance, blockedUserId);
      UserLifecycleService lifecycle = new UserLifecycleService(firstInstance);
      transaction.executeWithoutResult(status -> lifecycle.transition(
          users.findByIdForUpdate(blockedUserId).orElseThrow(),
          UserStatusEnum.BLOCKED,
          IdentityTransitionOriginEnum.SYSTEM,
          "RISK",
          NOW.plusSeconds(1),
          UUID.randomUUID()));

      AuthSessionAccessStatusEnum blockedStatus = transaction.execute(status -> secondInstance.access(
          blockedSession.cookieValue(), false, NOW.plusSeconds(2), UUID.randomUUID()).status());
      assertThat(blockedStatus).isEqualTo(AuthSessionAccessStatusEnum.REVOKED);

      Long passwordUserId = activeUser(transaction, users, "password-change@example.test");
      LocalCredentialService credentials = new LocalCredentialService(
          context.getBean(LocalCredentialRepository.class), firstInstance);
      transaction.executeWithoutResult(status -> credentials.replace(
          users.findByIdForUpdate(passwordUserId).orElseThrow(),
          "{argon2}old-value"));
      IssuedAuthSessionVO passwordSession = issue(transaction, firstInstance, passwordUserId);
      transaction.executeWithoutResult(status -> credentials.replaceAndInvalidateSessions(
          users.findByIdForUpdate(passwordUserId).orElseThrow(),
          "{argon2}new-value",
          NOW.plusSeconds(3),
          UUID.randomUUID()));

      AuthSessionAccessStatusEnum passwordStatus = transaction.execute(status -> secondInstance.access(
          passwordSession.cookieValue(), false, NOW.plusSeconds(4), UUID.randomUUID()).status());
      assertThat(passwordStatus).isEqualTo(AuthSessionAccessStatusEnum.REVOKED);
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
  void registerFailure_shouldSerializeIdentifierAndOriginTogetherBetweenInstances() {
    contextRunner().run(context -> {
      AuthenticationWindowRepository repository =
          context.getBean(AuthenticationWindowRepository.class);
      AuthenticationWindowService windows = new AuthenticationWindowService(
          repository, abuseProperties());
      String key = Base64.getEncoder().encodeToString(new byte[32]);
      AuthenticationAbuseProtectionService service = new AuthenticationAbuseProtectionService(
          new EmailNormalizationService(),
          new OriginAddressService(),
          new AuthenticationKeyringMacService(new AuthenticationKeyringService(
              new AuthenticationKeyringPropertiesConfig(
                  true, "v1", Map.of("v1", key)))),
          windows);

      List<AuthenticationAbuseDecisionVO> results = compete(() ->
          transaction(context).execute(status -> service.registerFailure(
              "person@example.test", "198.51.100.12", NOW)));

      assertThat(results)
          .extracting(AuthenticationAbuseDecisionVO::maximumFailureCount)
          .containsExactlyInAnyOrder(1, 2);
      assertThat(repository.findAll())
          .hasSize(2)
          .allSatisfy(window -> assertThat(window.getFailureCount()).isEqualTo(2));
    });
  }

  @Test
  void registerFailure_shouldRequireAndRenewTurnstileUntilFullQuietWindow() {
    contextRunner().run(context -> {
      TransactionTemplate transaction = transaction(context);
      AuthenticationWindowService service = new AuthenticationWindowService(
          context.getBean(AuthenticationWindowRepository.class), abuseProperties());
      byte[] digest = new byte[32];
      digest[0] = 84;

      AuthenticationWindowDecisionVO first = transaction.execute(status -> service.registerFailure(
          digest, "v1", AuthenticationWindowOperationEnum.SIGN_IN, NOW));
      AuthenticationWindowDecisionVO second = transaction.execute(status -> service.registerFailure(
          digest, "v1", AuthenticationWindowOperationEnum.SIGN_IN, NOW.plusSeconds(1)));
      AuthenticationWindowDecisionVO threshold = transaction.execute(status -> service.registerFailure(
          digest, "v1", AuthenticationWindowOperationEnum.SIGN_IN, NOW.plusSeconds(2)));
      AuthenticationWindowDecisionVO renewed = transaction.execute(status -> service.registerFailure(
          digest, "v1", AuthenticationWindowOperationEnum.SIGN_IN, NOW.plusSeconds(600)));
      AuthenticationWindowDecisionVO afterOriginalDeadline = transaction.execute(status -> service.inspect(
          digest, "v1", AuthenticationWindowOperationEnum.SIGN_IN, NOW.plusSeconds(903)));
      AuthenticationWindowDecisionVO afterQuietWindow = transaction.execute(status -> service.inspect(
          digest, "v1", AuthenticationWindowOperationEnum.SIGN_IN, NOW.plusSeconds(1_500)));

      assertThat(first.turnstileRequired()).isFalse();
      assertThat(second.turnstileRequired()).isFalse();
      assertThat(threshold.turnstileRequired()).isTrue();
      assertThat(threshold.turnstileRequiredUntil()).isEqualTo(NOW.plusSeconds(902));
      assertThat(renewed.turnstileRequiredUntil()).isEqualTo(NOW.plusSeconds(1_500));
      assertThat(afterOriginalDeadline.turnstileRequired()).isTrue();
      assertThat(afterQuietWindow.turnstileRequired()).isFalse();
      assertThat(afterQuietWindow.failureCount()).isZero();
    });
  }

  @Test
  void registerFailure_shouldRollbackIdentifierAndOriginTogether() {
    contextRunner().run(context -> {
      AuthenticationWindowRepository repository =
          context.getBean(AuthenticationWindowRepository.class);
      AuthenticationWindowService failingSecondWindow = new AuthenticationWindowService(
          repository, abuseProperties()) {
        private int invocation;

        @Override
        public AuthenticationWindowDecisionVO registerFailure(
            byte[] digest,
            String keyVersion,
            AuthenticationWindowOperationEnum operation,
            Instant occurredAt) {
          AuthenticationWindowDecisionVO result = super.registerFailure(
              digest, keyVersion, operation, occurredAt);
          if (++invocation == 2) {
            throw new IllegalStateException("simulated second dimension failure");
          }
          return result;
        }
      };
      String key = Base64.getEncoder().encodeToString(new byte[32]);
      AuthenticationAbuseProtectionService service = new AuthenticationAbuseProtectionService(
          new EmailNormalizationService(),
          new OriginAddressService(),
          new AuthenticationKeyringMacService(new AuthenticationKeyringService(
              new AuthenticationKeyringPropertiesConfig(
                  true, "v1", Map.of("v1", key)))),
          failingSecondWindow);

      assertThatThrownBy(() -> transaction(context).execute(status ->
          service.registerFailure("person@example.test", "198.51.100.12", NOW)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("second dimension");
      assertThat(repository.count()).isZero();
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
          .isEqualTo(AuthSessionAccessStatusEnum.REPLAY_DETECTED);
      AuthSessionAccessStatusEnum persistedStatus = transaction.execute(status -> service.access(
          replayed.cookieValue(), false, NOW.plusSeconds(2), UUID.randomUUID()).status());
      assertThat(persistedStatus)
          .isEqualTo(AuthSessionAccessStatusEnum.REVOKED);
    });
  }

  @Test
  void access_shouldExpireAtAbsoluteBoundary_despitePeriodicActivity() {
    contextRunner().run(context -> {
      TransactionTemplate transaction = transaction(context);
      UserRepository userRepository = context.getBean(UserRepository.class);
      AuthSessionService service = sessionService(context);
      Long userId = activeUser(transaction, userRepository, "absolute-expiry@example.test");
      IssuedAuthSessionVO issued = transaction.execute(status -> service.issue(
          userId,
          AuthenticationMethodEnum.PASSWORD,
          AuthenticationAssuranceEnum.SINGLE_FACTOR,
          List.of(new VerifiedAuthSessionMethodVO(
              AuthenticationMethodEnum.PASSWORD, NOW, null)),
          true,
          NOW,
          "Firefox on Linux",
          new byte[] {127, 0, 0, 1},
          new byte[32],
          UUID.randomUUID()));

      for (int elapsedDays : List.of(6, 12, 18, 24)) {
        AuthSessionAccessStatusEnum active = transaction.execute(status -> service.access(
            issued.cookieValue(),
            false,
            NOW.plus(Duration.ofDays(elapsedDays)),
            UUID.randomUUID()).status());
        assertThat(active).isEqualTo(AuthSessionAccessStatusEnum.ACTIVE);
      }

      AuthSessionAccessStatusEnum expired = transaction.execute(status -> service.access(
          issued.cookieValue(),
          false,
          issued.absoluteExpiresAt(),
          UUID.randomUUID()).status());

      assertThat(expired).isEqualTo(AuthSessionAccessStatusEnum.EXPIRED);
      assertThat(context.getBean(AuthSessionRepository.class).findAll())
          .singleElement()
          .satisfies(session -> {
            assertThat(session.getIdleExpiresAt()).isEqualTo(issued.absoluteExpiresAt());
            assertThat(session.getStatus()).isEqualTo(AuthSessionStatusEnum.EXPIRED);
          });
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
          true,
          NOW.minusSeconds(10),
          NOW.plusSeconds(300),
          UUID.randomUUID()));

      List<AuthenticationSessionLifecycleVO> preparations = compete(() ->
          transaction(context).execute(status -> lifecycle.prepare(
              issued.reference(),
              AuthenticationFlowPurposeEnum.SIGN_IN,
              userId,
              true,
              new byte[] {127, 0, 0, 1},
              "Browser/1.0",
              NOW)));
      AuthenticationSessionLifecycleVO prepared = preparations.getFirst();

      assertThat(preparations)
          .extracting(AuthenticationSessionLifecycleVO::sessionReference)
          .containsOnly(prepared.sessionReference());
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

      IdentityAuditService failingAudit = mock(IdentityAuditService.class, invocation -> {
        throw new IllegalStateException("simulated audit failure");
      });
      AuthenticationSessionLifecycleService failingLifecycle =
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
              failingAudit,
              sessionProperties());

      assertThatThrownBy(() -> transaction(context).execute(status ->
          failingLifecycle.publish(prepared.sessionReference(), NOW)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("simulated audit failure");
      assertThat(context.getBean(AuthSessionRepository.class).findAll())
          .singleElement()
          .satisfies(session -> assertThat(session.getStatus())
              .isEqualTo(AuthSessionStatusEnum.PREPARED));
      assertThat(context.getBean(AuthenticationFlowRepository.class).findAll())
          .singleElement()
          .satisfies(flow -> assertThat(flow.getStatus())
              .isEqualTo(AuthenticationFlowStatusEnum.OPEN));

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

      AuthSessionService persistentLogin = sessionService(context);
      IssuedPersistentLoginVO cookie = transaction.execute(status ->
          persistentLogin.issuePersistentCredential(prepared.sessionReference(), NOW.plusSeconds(1)));
      String[] credential = cookie.cookieValue().split("\\.", -1);
      assertThat(credential).hasSize(2);
      assertThat(context.getBean(AuthSessionRepository.class).findAll())
          .singleElement()
          .satisfies(session -> {
            assertThat(session.getSelectorHash()).containsExactly(tokens.hash(credential[0]));
            assertThat(session.getValidatorDigest()).containsExactly(tokens.hash(credential[1]));
          });

      var rotated = transaction.execute(status -> persistentLogin.access(
          cookie.cookieValue(), true, NOW.plusSeconds(2), UUID.randomUUID()));
      assertThat(rotated.status()).isEqualTo(AuthSessionAccessStatusEnum.ROTATED);
      assertThat(rotated.rotatedCookieValue()).isNotEqualTo(cookie.cookieValue());

      AuthSessionAccessStatusEnum replay = transaction.execute(status -> persistentLogin.access(
          cookie.cookieValue(), true, NOW.plusSeconds(3), UUID.randomUUID()).status());
      assertThat(replay).isEqualTo(AuthSessionAccessStatusEnum.REPLAY_DETECTED);
      AuthSessionAccessStatusEnum revoked = transaction.execute(status -> persistentLogin.access(
          rotated.rotatedCookieValue(), true, NOW.plusSeconds(4), UUID.randomUUID()).status());
      assertThat(revoked)
          .isEqualTo(AuthSessionAccessStatusEnum.REVOKED);
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
