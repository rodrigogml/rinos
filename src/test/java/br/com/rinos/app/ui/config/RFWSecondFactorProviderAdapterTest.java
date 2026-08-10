package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.rinos.app.api.dto.EmailOtpEmissionRequestDTO;
import br.com.rinos.app.api.dto.SecondFactorVerificationRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.enums.AuthenticationOrchestrationStatusEnum;
import br.com.rinos.app.api.enums.EmailOtpEmissionStatusEnum;
import br.com.rinos.app.api.facade.EmailOtpFacade;
import br.com.rinos.app.api.facade.SecondFactorFacade;
import br.com.rinos.app.api.vo.AuthenticationMethodEvidenceVO;
import br.com.rinos.app.api.vo.AuthenticationOrchestrationResultVO;
import br.com.rinos.app.api.vo.EmailOtpEmissionResultVO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWSecondFactorEmissionRequestDTO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWSecondFactorRequestDTO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAccessStatusEnum;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAuthenticationMethodEnum;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationOutcomeVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWSecondFactorEmissionOutcomeVO;

@DisplayName("Provider RFW de segundo fator")
class RFWSecondFactorProviderAdapterTest {

  private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void getEmissionMethods_shouldDeclareOnlyEmailCode() {
    RFWSecondFactorProviderAdapter adapter = adapter(
        mock(EmailOtpFacade.class), mock(SecondFactorFacade.class));

    assertThat(adapter.getEmissionMethods())
        .containsExactly(RFWAuthenticationMethodEnum.EMAIL_CODE);
  }

  @Test
  void begin_shouldEmitEmailOnlyAfterExplicitRequest() {
    EmailOtpFacade email = mock(EmailOtpFacade.class);
    SecondFactorFacade factor = mock(SecondFactorFacade.class);
    when(email.begin(any())).thenReturn(CompletableFuture.completedFuture(
        new EmailOtpEmissionResultVO(
            EmailOtpEmissionStatusEnum.EMITTED,
            "opaque-flow",
            "p***@example.test",
            NOW.plusSeconds(300),
            NOW.plusSeconds(60),
            null)));
    RFWSecondFactorProviderAdapter adapter = adapter(email, factor);

    RFWSecondFactorEmissionOutcomeVO result = adapter.begin(
        new RFWSecondFactorEmissionRequestDTO(
            "opaque-flow", RFWAuthenticationMethodEnum.EMAIL_CODE, "198.51.100.12"))
        .toCompletableFuture().join();

    ArgumentCaptor<EmailOtpEmissionRequestDTO> command =
        ArgumentCaptor.forClass(EmailOtpEmissionRequestDTO.class);
    verify(email).begin(command.capture());
    assertThat(command.getValue().challengeReference()).isEqualTo("opaque-flow");
    assertThat(result.emission().maskedDestination()).isEqualTo("p***@example.test");
    verify(factor, never()).verify(any());
  }

  @Test
  void begin_shouldRejectEmissionForTotpWithoutCallingEmailFacade() {
    EmailOtpFacade email = mock(EmailOtpFacade.class);
    RFWSecondFactorProviderAdapter adapter = adapter(email, mock(SecondFactorFacade.class));

    RFWSecondFactorEmissionOutcomeVO result = adapter.begin(
        new RFWSecondFactorEmissionRequestDTO(
            "opaque-flow", RFWAuthenticationMethodEnum.TOTP, "198.51.100.12"))
        .toCompletableFuture().join();

    assertThat(result.error().messageKey())
        .isEqualTo("authentication.temporarily-unavailable");
    verify(email, never()).begin(any());
  }

  @Test
  void resend_shouldExposePositiveRetryDuration() {
    EmailOtpFacade email = mock(EmailOtpFacade.class);
    when(email.resend(any())).thenReturn(CompletableFuture.completedFuture(
        EmailOtpEmissionResultVO.rateLimited(NOW.plusSeconds(45))));
    RFWSecondFactorProviderAdapter adapter = adapter(email, mock(SecondFactorFacade.class));

    RFWSecondFactorEmissionOutcomeVO result = adapter.resend(
        new RFWSecondFactorEmissionRequestDTO(
            "opaque-flow", RFWAuthenticationMethodEnum.EMAIL_CODE, "198.51.100.12"))
        .toCompletableFuture().join();

    assertThat(result.error().retryAfter()).hasSeconds(45);
  }

  @Test
  void verify_shouldRedactProofAndMapReadyOutcome() {
    SecondFactorFacade factor = mock(SecondFactorFacade.class);
    when(factor.verify(any())).thenReturn(ready());
    RFWSecondFactorProviderAdapter adapter = adapter(mock(EmailOtpFacade.class), factor);

    RFWAuthenticationOutcomeVO result = adapter.verify(new RFWSecondFactorRequestDTO(
        "opaque-flow", RFWAuthenticationMethodEnum.RECOVERY_CODE, "SECRET-CODE"))
        .toCompletableFuture().join();

    ArgumentCaptor<SecondFactorVerificationRequestDTO> command =
        ArgumentCaptor.forClass(SecondFactorVerificationRequestDTO.class);
    verify(factor).verify(command.capture());
    assertThat(command.getValue().method()).isEqualTo(AuthenticationMethodEnum.RECOVERY_CODE);
    assertThat(command.getValue().toString())
        .doesNotContain("SECRET-CODE", "opaque-flow");
    assertThat(result.status()).isEqualTo(RFWAccessStatusEnum.AUTHENTICATED);
  }

  private static RFWSecondFactorProviderAdapter adapter(
      EmailOtpFacade email,
      SecondFactorFacade factor) {
    return new RFWSecondFactorProviderAdapter(
        email, factor, new RFWAuthenticationOutcomeAdapter(), CLOCK);
  }

  private static AuthenticationOrchestrationResultVO ready() {
    return new AuthenticationOrchestrationResultVO(
        AuthenticationOrchestrationStatusEnum.READY,
        "opaque-flow",
        new br.com.rinos.app.api.vo.RinosUserPrincipalVO(41L, "person@example.test"),
        AuthenticationAssuranceEnum.MULTI_FACTOR,
        Set.of(),
        List.of(
            new AuthenticationMethodEvidenceVO(AuthenticationMethodEnum.PASSWORD, NOW, null),
            new AuthenticationMethodEvidenceVO(
                AuthenticationMethodEnum.RECOVERY_CODE, NOW, null)),
        Set.of(),
        false,
        NOW.plusSeconds(300),
        UUID.fromString("17db3ddc-c405-48ef-87dc-9e85e910fb52"));
  }
}
