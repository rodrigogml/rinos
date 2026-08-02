package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import br.com.rinos.app.api.dto.PasswordRecoveryRequestDTO;
import br.com.rinos.app.api.dto.PasswordResetRequestDTO;
import br.com.rinos.app.api.enums.PasswordRecoveryRequestStatusEnum;
import br.com.rinos.app.api.enums.PasswordResetStatusEnum;
import br.com.rinos.app.api.facade.PasswordRecoveryFacade;
import br.com.rinos.app.api.vo.PasswordRecoveryRequestResultVO;
import br.com.rinos.app.api.vo.PasswordResetResultVO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWPasswordResetRequestDTO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWRecoveryRequestDTO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAccessStatusEnum;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationOutcomeVO;

@DisplayName("Adapter RFW da recuperação de senha")
class RFWPasswordRecoveryProviderAdapterTest {

  @AfterEach
  void clearRequest() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void requestRecovery_shouldReturnNeutralCompletion_andPrefillCommandIdentifier() {
    PasswordRecoveryFacade facade = mock(PasswordRecoveryFacade.class);
    when(facade.requestRecovery(any())).thenReturn(CompletableFuture.completedFuture(
        new PasswordRecoveryRequestResultVO(
            PasswordRecoveryRequestStatusEnum.ACCEPTED, null)));
    RFWPasswordRecoveryProviderAdapter adapter = adapter(facade);
    attachRequest();

    RFWAuthenticationOutcomeVO outcome = adapter.requestRecovery(
        new RFWRecoveryRequestDTO("person@example.test", "turnstile-token"))
        .toCompletableFuture().join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.COMPLETED);
    assertThat(outcome.messageKey()).isEqualTo(
        "authentication.recovery.request-accepted");
    ArgumentCaptor<PasswordRecoveryRequestDTO> command =
        ArgumentCaptor.forClass(PasswordRecoveryRequestDTO.class);
    verify(facade).requestRecovery(command.capture());
    assertThat(command.getValue().identifier()).isEqualTo("person@example.test");
    assertThat(command.getValue().canonicalOrigin()).isEqualTo("203.0.113.10");
  }

  @Test
  void requestRecovery_shouldMapOriginRateLimit_withoutIdentityDisclosure() {
    PasswordRecoveryFacade facade = mock(PasswordRecoveryFacade.class);
    when(facade.requestRecovery(any())).thenReturn(CompletableFuture.completedFuture(
        new PasswordRecoveryRequestResultVO(
            PasswordRecoveryRequestStatusEnum.RATE_LIMITED,
            Duration.ofMinutes(4))));
    RFWPasswordRecoveryProviderAdapter adapter = adapter(facade);
    attachRequest();

    RFWAuthenticationOutcomeVO outcome = adapter.requestRecovery(
        new RFWRecoveryRequestDTO("unknown@example.test", "turnstile-token"))
        .toCompletableFuture().join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.RATE_LIMITED);
    assertThat(outcome.error().retryAfter()).isEqualTo(Duration.ofMinutes(4));
    assertThat(outcome.error().messageKey()).isEqualTo(
        "authentication.recovery.rate-limited");
  }

  @Test
  void resetPassword_shouldMapValidationAndConsumeOnlyTransientPassword() {
    PasswordRecoveryFacade facade = mock(PasswordRecoveryFacade.class);
    when(facade.resetPassword(any())).thenReturn(CompletableFuture.completedFuture(
        new PasswordResetResultVO(
            PasswordResetStatusEnum.VALIDATION_REJECTED,
            Map.of("password", "registration.error.password.minimum-length-required"),
            null)));
    RFWPasswordRecoveryProviderAdapter adapter = adapter(facade);
    attachRequest();

    RFWAuthenticationOutcomeVO outcome = adapter.resetPassword(
        new RFWPasswordResetRequestDTO("opaque-proof", "short"))
        .toCompletableFuture().join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().fieldErrors()).containsKey("password");
    ArgumentCaptor<PasswordResetRequestDTO> command =
        ArgumentCaptor.forClass(PasswordResetRequestDTO.class);
    verify(facade).resetPassword(command.capture());
    assertThat(command.getValue().toString())
        .doesNotContain("opaque-proof")
        .doesNotContain("short")
        .doesNotContain("203.0.113.10");
  }

  @Test
  void resetPassword_shouldCompleteAndRejectReplayThroughTypedResults() {
    PasswordRecoveryFacade facade = mock(PasswordRecoveryFacade.class);
    RFWPasswordRecoveryProviderAdapter adapter = adapter(facade);
    attachRequest();
    when(facade.resetPassword(any())).thenReturn(CompletableFuture.completedFuture(
        new PasswordResetResultVO(PasswordResetStatusEnum.COMPLETED, Map.of(), null)));

    RFWAuthenticationOutcomeVO completed = adapter.resetPassword(
        new RFWPasswordResetRequestDTO("opaque-proof", "ValidPassword1!"))
        .toCompletableFuture().join();

    assertThat(completed.status()).isEqualTo(RFWAccessStatusEnum.COMPLETED);
    when(facade.resetPassword(any())).thenReturn(CompletableFuture.completedFuture(
        new PasswordResetResultVO(PasswordResetStatusEnum.INVALID_PROOF, Map.of(), null)));

    RFWAuthenticationOutcomeVO replay = adapter.resetPassword(
        new RFWPasswordResetRequestDTO("opaque-proof", "ValidPassword1!"))
        .toCompletableFuture().join();

    assertThat(replay.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(replay.error().messageKey()).isEqualTo(
        "authentication.recovery.invalid-proof");
  }

  private static RFWPasswordRecoveryProviderAdapter adapter(PasswordRecoveryFacade facade) {
    return new RFWPasswordRecoveryProviderAdapter(facade, ignored -> "203.0.113.10");
  }

  private static void attachRequest() {
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
        new MockHttpServletRequest(),
        new MockHttpServletResponse()));
  }
}
