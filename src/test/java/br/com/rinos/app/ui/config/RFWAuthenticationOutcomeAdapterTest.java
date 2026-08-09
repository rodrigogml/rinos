package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.enums.AuthenticationOrchestrationStatusEnum;
import br.com.rinos.app.api.vo.AuthenticationMethodEvidenceVO;
import br.com.rinos.app.api.vo.AuthenticationOrchestrationResultVO;
import br.com.rinos.app.api.vo.RinosAuthenticationCompletionVO;
import br.com.rinos.app.api.vo.RinosUserPrincipalVO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAccessStatusEnum;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAuthenticationMethodEnum;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationOutcomeVO;

@DisplayName("Adapter dos outcomes do orquestrador para o RFW")
class RFWAuthenticationOutcomeAdapterTest {

  private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
  private static final Instant EXPIRES_AT = NOW.plusSeconds(300);
  private static final UUID CORRELATION_ID =
      UUID.fromString("590cd2c5-5b5d-45e6-bfbf-887033f407f0");
  private final RFWAuthenticationOutcomeAdapter adapter =
      new RFWAuthenticationOutcomeAdapter();

  @Test
  void map_shouldCreateOnlyAReadyAuthenticationWithEphemeralCompletion() {
    AuthenticationOrchestrationResultVO result = new AuthenticationOrchestrationResultVO(
        AuthenticationOrchestrationStatusEnum.READY,
        "opaque-flow",
        new RinosUserPrincipalVO(41L, "person@example.test"),
        AuthenticationAssuranceEnum.MULTI_FACTOR,
        Set.of(),
        List.of(
            evidence(AuthenticationMethodEnum.PASSWORD),
            evidence(AuthenticationMethodEnum.TOTP)),
        Set.of(),
        true,
        EXPIRES_AT,
        CORRELATION_ID);

    RFWAuthenticationOutcomeVO outcome = adapter.map(
        result, AuthenticationFlowPurposeEnum.SIGN_IN);

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.AUTHENTICATED);
    Authentication authentication = outcome.authentication();
    assertThat(authentication.getPrincipal()).isEqualTo(result.principal());
    assertThat(authentication.getAuthorities()).isEmpty();
    assertThat(authentication.getDetails()).isEqualTo(
        new RinosAuthenticationCompletionVO(
            "opaque-flow", AuthenticationFlowPurposeEnum.SIGN_IN));
  }

  @Test
  void map_shouldCreateTypedChallengeWithDeterministicInitialMethod() {
    AuthenticationOrchestrationResultVO result = outcome(
        AuthenticationOrchestrationStatusEnum.CHALLENGE_REQUIRED,
        Set.of(AuthenticationMethodEnum.TOTP, AuthenticationMethodEnum.PASSKEY),
        Set.of());

    RFWAuthenticationOutcomeVO mapped = adapter.map(
        result, AuthenticationFlowPurposeEnum.SIGN_IN);

    assertThat(mapped.status()).isEqualTo(RFWAccessStatusEnum.CHALLENGE_REQUIRED);
    assertThat(mapped.challenge().challengeId()).isEqualTo("opaque-flow");
    assertThat(mapped.challenge().method()).isEqualTo(RFWAuthenticationMethodEnum.PASSKEY);
    assertThat(mapped.challenge().allowedMethods())
        .containsExactlyInAnyOrder(
            RFWAuthenticationMethodEnum.PASSKEY,
            RFWAuthenticationMethodEnum.TOTP);
  }

  @Test
  void map_shouldCreateLegalGateWithoutPartialAuthentication() {
    AuthenticationOrchestrationResultVO result = outcome(
        AuthenticationOrchestrationStatusEnum.LEGAL_CONSENT_REQUIRED,
        Set.of(),
        Set.of("11", "12"));

    RFWAuthenticationOutcomeVO mapped = adapter.map(
        result, AuthenticationFlowPurposeEnum.LEGAL_CONSENT);

    assertThat(mapped.status()).isEqualTo(
        RFWAccessStatusEnum.AUTHENTICATION_CONSENT_REQUIRED);
    assertThat(mapped.authentication()).isNull();
    assertThat(mapped.authenticationConsent().continuationReference())
        .isEqualTo("opaque-flow");
    assertThat(mapped.authenticationConsent().legalDocumentIds())
        .containsExactlyInAnyOrder("11", "12");
  }

  @Test
  void map_shouldUseStablePublicMessagesForTerminalFailures() {
    assertThat(mapTerminal(AuthenticationOrchestrationStatusEnum.REJECTED)
        .error().messageKey()).isEqualTo("authentication.credentials.invalid");
    assertThat(mapTerminal(AuthenticationOrchestrationStatusEnum.EXPIRED)
        .error().messageKey()).isEqualTo("authentication.flow.expired");
    assertThat(mapTerminal(AuthenticationOrchestrationStatusEnum.CONFLICT)
        .error().messageKey()).isEqualTo("authentication.flow.conflict");
    assertThat(mapTerminal(AuthenticationOrchestrationStatusEnum.UNAVAILABLE)
        .error().messageKey()).isEqualTo("authentication.temporarily-unavailable");
    assertThat(mapTerminal(AuthenticationOrchestrationStatusEnum.CANCELLED).status())
        .isEqualTo(RFWAccessStatusEnum.COMPLETED);
  }

  private RFWAuthenticationOutcomeVO mapTerminal(
      AuthenticationOrchestrationStatusEnum status) {
    return adapter.map(new AuthenticationOrchestrationResultVO(
        status, null, null, null, Set.of(), List.of(), Set.of(), false, null, null),
        AuthenticationFlowPurposeEnum.SIGN_IN);
  }

  private static AuthenticationOrchestrationResultVO outcome(
      AuthenticationOrchestrationStatusEnum status,
      Set<AuthenticationMethodEnum> permittedMethods,
      Set<String> missingLegalDocumentIds) {
    return new AuthenticationOrchestrationResultVO(
        status,
        "opaque-flow",
        null,
        AuthenticationAssuranceEnum.SINGLE_FACTOR,
        permittedMethods,
        List.of(evidence(AuthenticationMethodEnum.PASSWORD)),
        missingLegalDocumentIds,
        true,
        EXPIRES_AT,
        CORRELATION_ID);
  }

  private static AuthenticationMethodEvidenceVO evidence(AuthenticationMethodEnum method) {
    return new AuthenticationMethodEvidenceVO(method, NOW, null);
  }
}
