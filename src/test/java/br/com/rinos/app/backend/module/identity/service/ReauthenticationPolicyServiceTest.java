package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.ReauthenticationOperationEnum;
import br.com.rinos.app.backend.module.identity.enums.ReauthenticationPolicyStatusEnum;
import br.com.rinos.app.backend.module.identity.vo.VerifiedAuthSessionMethodVO;
import br.com.rinos.app.config.AuthenticationSessionPropertiesConfig;

/**
 * Verifica recência, garantia e compatibilidade por operação.
 *
 * @author Rodrigo Leitão
 */
class ReauthenticationPolicyServiceTest {

  private static final Instant AUTHENTICATED_AT = Instant.parse("2026-08-09T12:00:00Z");
  private ReauthenticationPolicyService service;

  @BeforeEach
  void setUp() {
    service = new ReauthenticationPolicyService(
        new AuthenticationAssurancePolicyService(),
        new AuthenticationSessionPropertiesConfig(
            Duration.ofHours(12),
            Duration.ofMinutes(30),
            Duration.ofDays(30),
            Duration.ofDays(7),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            "RINOS_AUTH",
            true,
            "Strict"));
  }

  @Test
  void evaluate_shouldAcceptCompatibleEvidenceThroughInclusiveFifteenMinuteBoundary() {
    var decision = service.evaluate(
        ReauthenticationOperationEnum.CHANGE_PASSWORD,
        AuthenticationAssuranceEnum.SINGLE_FACTOR,
        AUTHENTICATED_AT,
        List.of(verified(AuthenticationMethodEnum.PASSWORD)),
        Set.of(AuthenticationMethodEnum.PASSWORD),
        AUTHENTICATED_AT.plus(Duration.ofMinutes(15)));

    assertThat(decision.status()).isEqualTo(ReauthenticationPolicyStatusEnum.ALREADY_RECENT);
    assertThat(decision.allowedMethods()).isEmpty();
  }

  @Test
  void evaluate_shouldRequireChallengeAfterBoundaryAndExcludeUnsupportedChannels() {
    var decision = service.evaluate(
        ReauthenticationOperationEnum.UNLINK_EXTERNAL_IDENTITY,
        AuthenticationAssuranceEnum.MULTI_FACTOR,
        AUTHENTICATED_AT,
        List.of(
            verified(AuthenticationMethodEnum.GOOGLE),
            verified(AuthenticationMethodEnum.EMAIL_CODE)),
        Set.of(
            AuthenticationMethodEnum.PASSWORD,
            AuthenticationMethodEnum.PASSKEY,
            AuthenticationMethodEnum.GOOGLE,
            AuthenticationMethodEnum.EMAIL_CODE,
            AuthenticationMethodEnum.RECOVERY_CODE),
        AUTHENTICATED_AT.plus(Duration.ofMinutes(15)).plusNanos(1));

    assertThat(decision.status())
        .isEqualTo(ReauthenticationPolicyStatusEnum.CHALLENGE_REQUIRED);
    assertThat(decision.allowedMethods())
        .containsExactlyInAnyOrder(
            AuthenticationMethodEnum.PASSWORD,
            AuthenticationMethodEnum.PASSKEY);
  }

  @Test
  void evaluate_shouldNotReuseRecentGoogleEvidenceForSensitiveOperation() {
    var decision = service.evaluate(
        ReauthenticationOperationEnum.LINK_EXTERNAL_IDENTITY,
        AuthenticationAssuranceEnum.SINGLE_FACTOR,
        AUTHENTICATED_AT,
        List.of(verified(AuthenticationMethodEnum.GOOGLE)),
        Set.of(AuthenticationMethodEnum.TOTP),
        AUTHENTICATED_AT.plus(Duration.ofMinutes(1)));

    assertThat(decision.status())
        .isEqualTo(ReauthenticationPolicyStatusEnum.CHALLENGE_REQUIRED);
    assertThat(decision.allowedMethods()).containsExactly(AuthenticationMethodEnum.TOTP);
  }

  @Test
  void evaluate_shouldDenyWhenNoCompatibleMethodRemains() {
    var decision = service.evaluate(
        ReauthenticationOperationEnum.REVOKE_ALL_SESSIONS,
        AuthenticationAssuranceEnum.SINGLE_FACTOR,
        AUTHENTICATED_AT,
        List.of(verified(AuthenticationMethodEnum.GOOGLE)),
        Set.of(AuthenticationMethodEnum.GOOGLE, AuthenticationMethodEnum.EMAIL_CODE),
        AUTHENTICATED_AT.plus(Duration.ofMinutes(20)));

    assertThat(decision.status()).isEqualTo(ReauthenticationPolicyStatusEnum.ACCESS_DENIED);
    assertThat(decision.allowedMethods()).isEmpty();
  }

  @Test
  void operationCatalog_shouldRejectUnknownIdentifiersAndExposeOnlyHumanLabelKey() {
    assertThat(ReauthenticationOperationEnum.fromOperationId("change-password"))
        .contains(ReauthenticationOperationEnum.CHANGE_PASSWORD);
    assertThat(ReauthenticationOperationEnum.fromOperationId("unknown-operation")).isEmpty();
    assertThat(ReauthenticationOperationEnum.CHANGE_PASSWORD.labelKey())
        .isEqualTo("ui.securitySettings.reauthentication.operation.changePassword");
  }

  @Test
  void accessAdministration_shouldRequireStrongTotpOrPasskeyEvidence() {
    assertThat(ReauthenticationOperationEnum.MANAGE_ACCESS.requiredAssurance())
        .isEqualTo(AuthenticationAssuranceEnum.MULTI_FACTOR);
    assertThat(ReauthenticationOperationEnum.MANAGE_ACCESS.allowedMethods())
        .containsExactlyInAnyOrder(AuthenticationMethodEnum.TOTP, AuthenticationMethodEnum.PASSKEY)
        .doesNotContain(AuthenticationMethodEnum.PASSWORD);
    assertThat(ReauthenticationOperationEnum.fromOperationId("explain-access"))
        .contains(ReauthenticationOperationEnum.EXPLAIN_ACCESS);
  }

  private static VerifiedAuthSessionMethodVO verified(AuthenticationMethodEnum method) {
    return new VerifiedAuthSessionMethodVO(method, AUTHENTICATED_AT, null);
  }
}
