package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import br.com.rinos.app.api.dto.RegistrationStartRequestDTO;
import br.com.rinos.app.api.dto.RegistrationActivationRequestDTO;
import br.com.rinos.app.api.enums.RegistrationResendStatusEnum;
import br.com.rinos.app.api.enums.RegistrationActivationStatusEnum;
import br.com.rinos.app.api.enums.RegistrationStartStatusEnum;
import br.com.rinos.app.api.facade.RegistrationResendFacade;
import br.com.rinos.app.api.facade.RegistrationActivationFacade;
import br.com.rinos.app.api.facade.RegistrationStartFacade;
import br.com.rinos.app.api.vo.RegistrationResendResultVO;
import br.com.rinos.app.api.vo.RegistrationActivationResultVO;
import br.com.rinos.app.api.vo.RegistrationStartResultVO;
import br.eng.rodrigogml.rfw.platform.authentication.dto.RFWRegistrationRequestDTO;
import br.eng.rodrigogml.rfw.platform.authentication.dto.RFWActivationRequestDTO;
import br.eng.rodrigogml.rfw.platform.authentication.enums.RFWAccessStatusEnum;
import br.eng.rodrigogml.rfw.platform.authentication.vo.RFWAuthenticationOutcomeVO;
import br.eng.rodrigogml.rfw.platform.ui.access.provider.RFWRemoteAddressProvider;

@DisplayName("Adapter RFW do início do cadastro")
class RFWRegistrationProviderAdapterTest {

  @AfterEach
  void clearRequest() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void register_shouldResolveCanonicalOriginAndOpenActivation_whenEmailIsAccepted() {
    RegistrationStartFacade facade = Mockito.mock(RegistrationStartFacade.class);
    RFWRemoteAddressProvider remoteAddressProvider = ignored -> "203.0.113.10";
    when(facade.start(any())).thenReturn(CompletableFuture.completedFuture(
        RegistrationStartResultVO.of(RegistrationStartStatusEnum.EMAIL_SENT)));
    RFWRegistrationProviderAdapter adapter = adapter(facade, remoteAddressProvider);
    attachRequest();

    RFWAuthenticationOutcomeVO outcome = adapter.register(new RFWRegistrationRequestDTO(
        "person@example.test",
        "ValidPassword1!",
        List.of("1"),
        "verified-upstream")).toCompletableFuture().join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.ACTIVATION_REQUIRED);
    assertThat(outcome.messageKey()).isEqualTo("registration.email-sent");
    ArgumentCaptor<RegistrationStartRequestDTO> command =
        ArgumentCaptor.forClass(RegistrationStartRequestDTO.class);
    verify(facade).start(command.capture());
    assertThat(command.getValue().getCanonicalOrigin()).isEqualTo("203.0.113.10");
    assertThat(command.getValue().toString())
        .doesNotContain("person@example.test")
        .doesNotContain("ValidPassword1!");
  }

  @Test
  void register_shouldKeepFormRejected_whenSmtpDidNotAcceptMessage() {
    RegistrationStartFacade facade = Mockito.mock(RegistrationStartFacade.class);
    when(facade.start(any())).thenReturn(CompletableFuture.completedFuture(
        RegistrationStartResultVO.of(
            RegistrationStartStatusEnum.EMAIL_DISPATCH_FAILED)));
    RFWRegistrationProviderAdapter adapter =
        adapter(facade, ignored -> "203.0.113.10");
    attachRequest();

    RFWAuthenticationOutcomeVO outcome = adapter.register(new RFWRegistrationRequestDTO(
        "person@example.test",
        "ValidPassword1!",
        List.of("1"),
        "verified-upstream")).toCompletableFuture().join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().messageKey())
        .isEqualTo("registration.email-dispatch-failed");
  }

  @Test
  void register_shouldMapRateLimitAndFieldErrors() {
    RegistrationStartFacade facade = Mockito.mock(RegistrationStartFacade.class);
    when(facade.start(any())).thenReturn(CompletableFuture.completedFuture(
        new RegistrationStartResultVO(
            RegistrationStartStatusEnum.RATE_LIMITED,
            Map.of(),
            Duration.ofMinutes(4))));
    RFWRegistrationProviderAdapter adapter =
        adapter(facade, ignored -> "203.0.113.10");
    attachRequest();

    RFWAuthenticationOutcomeVO outcome = adapter.register(new RFWRegistrationRequestDTO(
        "person@example.test",
        "ValidPassword1!",
        List.of("1"),
        "verified-upstream")).toCompletableFuture().join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.RATE_LIMITED);
    assertThat(outcome.error().retryAfter()).isEqualTo(Duration.ofMinutes(4));
  }

  @Test
  void register_shouldExposeExistingEmailAsRecoverableFieldError() {
    RegistrationStartFacade facade = Mockito.mock(RegistrationStartFacade.class);
    when(facade.start(any())).thenReturn(CompletableFuture.completedFuture(
        RegistrationStartResultVO.of(
            RegistrationStartStatusEnum.EMAIL_ALREADY_EXISTS)));
    RFWRegistrationProviderAdapter adapter =
        adapter(facade, ignored -> "203.0.113.10");
    attachRequest();

    RFWAuthenticationOutcomeVO outcome = adapter.register(new RFWRegistrationRequestDTO(
        "person@example.test",
        "ValidPassword1!",
        List.of("1"),
        "verified-upstream")).toCompletableFuture().join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().messageKey()).isEqualTo("registration.email-exists");
    assertThat(outcome.error().fieldErrors())
        .containsEntry("email", "registration.error.email-exists");
  }

  @Test
  void register_shouldOpenActivation_whenPendingIdentityAlreadyExists() {
    RegistrationStartFacade facade = Mockito.mock(RegistrationStartFacade.class);
    when(facade.start(any())).thenReturn(CompletableFuture.completedFuture(
        RegistrationStartResultVO.of(
            RegistrationStartStatusEnum.PENDING_ALREADY_EXISTS)));
    RFWRegistrationProviderAdapter adapter =
        adapter(facade, ignored -> "203.0.113.10");
    attachRequest();

    RFWAuthenticationOutcomeVO outcome = adapter.register(new RFWRegistrationRequestDTO(
        "person@example.test",
        "ValidPassword1!",
        List.of("1"),
        "verified-upstream")).toCompletableFuture().join();

    assertThat(outcome.status())
        .isEqualTo(RFWAccessStatusEnum.ACTIVATION_REQUIRED);
    assertThat(outcome.messageKey()).isEqualTo("registration.pending-exists");
  }

  @Test
  void register_shouldPreserveBackendValidationFieldErrors() {
    RegistrationStartFacade facade = Mockito.mock(RegistrationStartFacade.class);
    when(facade.start(any())).thenReturn(CompletableFuture.completedFuture(
        new RegistrationStartResultVO(
            RegistrationStartStatusEnum.VALIDATION_REJECTED,
            Map.of("password", "registration.error.password.compromised"),
            null)));
    RFWRegistrationProviderAdapter adapter =
        adapter(facade, ignored -> "203.0.113.10");
    attachRequest();

    RFWAuthenticationOutcomeVO outcome = adapter.register(new RFWRegistrationRequestDTO(
        "person@example.test",
        "ValidPassword1!",
        List.of("1"),
        "verified-upstream")).toCompletableFuture().join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().fieldErrors())
        .containsEntry("password", "registration.error.password.compromised");
  }

  @Test
  void register_shouldFailSafely_whenNoHttpRequestExists() {
    RegistrationStartFacade facade = Mockito.mock(RegistrationStartFacade.class);
    RFWRegistrationProviderAdapter adapter =
        adapter(facade, ignored -> "203.0.113.10");

    RFWAuthenticationOutcomeVO outcome = adapter.register(new RFWRegistrationRequestDTO(
        "person@example.test",
        "ValidPassword1!",
        List.of("1"),
        "verified-upstream")).toCompletableFuture().join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().messageKey()).isEqualTo("registration.unavailable");
  }

  @Test
  void register_shouldMapUnavailableFacadeToPublicError() {
    RegistrationStartFacade facade = Mockito.mock(RegistrationStartFacade.class);
    when(facade.start(any())).thenReturn(CompletableFuture.completedFuture(
        RegistrationStartResultVO.of(RegistrationStartStatusEnum.UNAVAILABLE)));
    RFWRegistrationProviderAdapter adapter =
        adapter(facade, ignored -> "203.0.113.10");
    attachRequest();

    RFWAuthenticationOutcomeVO outcome = adapter.register(new RFWRegistrationRequestDTO(
        "person@example.test",
        "ValidPassword1!",
        List.of("1"),
        "verified-upstream")).toCompletableFuture().join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().messageKey()).isEqualTo("registration.unavailable");
  }

  @Test
  void resendActivation_shouldKeepActivationOpenAndMapRateLimit() {
    RegistrationStartFacade startFacade = Mockito.mock(RegistrationStartFacade.class);
    RegistrationResendFacade resendFacade = Mockito.mock(RegistrationResendFacade.class);
    when(resendFacade.resend(any())).thenReturn(CompletableFuture.completedFuture(
        RegistrationResendResultVO.of(RegistrationResendStatusEnum.REQUEST_ACCEPTED)));
    RFWRegistrationProviderAdapter adapter = new RFWRegistrationProviderAdapter(
        startFacade,
        resendFacade,
        Mockito.mock(RegistrationActivationFacade.class),
        ignored -> "203.0.113.10");

    RFWAuthenticationOutcomeVO accepted =
        adapter.resendActivation("person@example.test").toCompletableFuture().join();

    assertThat(accepted.status()).isEqualTo(RFWAccessStatusEnum.ACTIVATION_REQUIRED);
    assertThat(accepted.messageKey()).isEqualTo("registration.activation-resent");

    when(resendFacade.resend(any())).thenReturn(CompletableFuture.completedFuture(
        new RegistrationResendResultVO(
            RegistrationResendStatusEnum.RATE_LIMITED,
            Map.of(),
            Duration.ofMinutes(4))));

    RFWAuthenticationOutcomeVO limited =
        adapter.resendActivation("person@example.test").toCompletableFuture().join();

    assertThat(limited.status()).isEqualTo(RFWAccessStatusEnum.RATE_LIMITED);
    assertThat(limited.error().retryAfter()).isEqualTo(Duration.ofMinutes(4));
  }

  /**
   * Não converte falha de transporte em confirmação pública de que o novo e-mail foi aceito.
   */
  @Test
  void resendActivation_shouldRejectWithoutClaimingDelivery_whenSmtpFails() {
    RegistrationResendFacade resendFacade = Mockito.mock(RegistrationResendFacade.class);
    when(resendFacade.resend(any())).thenReturn(CompletableFuture.completedFuture(
        RegistrationResendResultVO.of(
            RegistrationResendStatusEnum.EMAIL_DISPATCH_FAILED)));
    RFWRegistrationProviderAdapter adapter = new RFWRegistrationProviderAdapter(
        Mockito.mock(RegistrationStartFacade.class),
        resendFacade,
        Mockito.mock(RegistrationActivationFacade.class),
        ignored -> "203.0.113.10");

    RFWAuthenticationOutcomeVO outcome =
        adapter.resendActivation("person@example.test").toCompletableFuture().join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().messageKey())
        .isEqualTo("registration.resend-email-dispatch-failed");
  }

  @Test
  void resendActivation_shouldPreserveFacadeFieldErrors() {
    RegistrationResendFacade resendFacade = Mockito.mock(RegistrationResendFacade.class);
    when(resendFacade.resend(any())).thenReturn(CompletableFuture.completedFuture(
        new RegistrationResendResultVO(
            RegistrationResendStatusEnum.VALIDATION_REJECTED,
            Map.of("identifier", "registration.error.email.invalid"),
            null)));
    RFWRegistrationProviderAdapter adapter = new RFWRegistrationProviderAdapter(
        Mockito.mock(RegistrationStartFacade.class),
        resendFacade,
        Mockito.mock(RegistrationActivationFacade.class),
        ignored -> "203.0.113.10");

    RFWAuthenticationOutcomeVO outcome =
        adapter.resendActivation("invalid-email").toCompletableFuture().join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().fieldErrors())
        .containsEntry("identifier", "registration.error.email.invalid");
  }

  @Test
  void resendActivation_shouldMapUnavailableFacadeToPublicError() {
    RegistrationResendFacade resendFacade = Mockito.mock(RegistrationResendFacade.class);
    when(resendFacade.resend(any())).thenReturn(CompletableFuture.completedFuture(
        RegistrationResendResultVO.of(RegistrationResendStatusEnum.UNAVAILABLE)));
    RFWRegistrationProviderAdapter adapter = new RFWRegistrationProviderAdapter(
        Mockito.mock(RegistrationStartFacade.class),
        resendFacade,
        Mockito.mock(RegistrationActivationFacade.class),
        ignored -> "203.0.113.10");

    RFWAuthenticationOutcomeVO outcome =
        adapter.resendActivation("person@example.test").toCompletableFuture().join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().messageKey()).isEqualTo("registration.unavailable");
  }

  @Test
  void activate_shouldMapLegalContinuationWithSameOpaqueProof() {
    RegistrationStartFacade startFacade = Mockito.mock(RegistrationStartFacade.class);
    RegistrationActivationFacade activationFacade =
        Mockito.mock(RegistrationActivationFacade.class);
    Instant expiresAt = Instant.parse("2026-07-29T13:00:00Z");
    when(activationFacade.activate(any())).thenReturn(CompletableFuture.completedFuture(
        RegistrationActivationResultVO.consentRequired(
            "opaque-proof",
            "person@example.test",
            java.util.Set.of("2"),
            expiresAt)));
    RFWRegistrationProviderAdapter adapter = new RFWRegistrationProviderAdapter(
        startFacade,
        Mockito.mock(RegistrationResendFacade.class),
        activationFacade,
        ignored -> "203.0.113.10");

    RFWAuthenticationOutcomeVO outcome = adapter.activate(
        new RFWActivationRequestDTO(null, "opaque-proof"))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status())
        .isEqualTo(RFWAccessStatusEnum.ACTIVATION_CONSENT_REQUIRED);
    assertThat(outcome.activationConsent().activationReference())
        .isEqualTo("opaque-proof");
    assertThat(outcome.activationConsent().legalDocumentIds()).containsExactly("2");
  }

  @Test
  void activate_shouldDelegateOpaqueProofAndMapSuccessfulActivation() {
    RegistrationStartFacade startFacade = Mockito.mock(RegistrationStartFacade.class);
    RegistrationActivationFacade activationFacade =
        Mockito.mock(RegistrationActivationFacade.class);
    when(activationFacade.activate(any())).thenReturn(CompletableFuture.completedFuture(
        RegistrationActivationResultVO.of(
            RegistrationActivationStatusEnum.ACTIVATED)));
    RFWRegistrationProviderAdapter adapter = new RFWRegistrationProviderAdapter(
        startFacade,
        Mockito.mock(RegistrationResendFacade.class),
        activationFacade,
        ignored -> "203.0.113.10");

    RFWAuthenticationOutcomeVO outcome = adapter.activate(
        new RFWActivationRequestDTO(null, "opaque-proof"))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.COMPLETED);
    assertThat(outcome.messageKey()).isEqualTo(
        "registration.activation-completed");
    ArgumentCaptor<RegistrationActivationRequestDTO> command =
        ArgumentCaptor.forClass(
            RegistrationActivationRequestDTO.class);
    verify(activationFacade).activate(command.capture());
    assertThat(command.getValue().getIdentifier()).isNull();
    assertThat(command.getValue().getProof()).isEqualTo("opaque-proof");
    assertThat(command.getValue().getCorrelationId()).isNotNull();
  }

  /**
   * Mantém a chave desconhecida em uma rejeição própria e acionável.
   */
  @Test
  void activate_shouldMapInvalidProofToSpecificPublicError() {
    RFWAuthenticationOutcomeVO outcome = RFWRegistrationProviderAdapter.mapActivation(
        RegistrationActivationResultVO.of(
            RegistrationActivationStatusEnum.INVALID_PROOF));

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().messageKey())
        .isEqualTo("registration.activation.invalid-proof");
  }

  /**
   * Mantém a expiração temporal separada da rejeição genérica.
   */
  @Test
  void activate_shouldMapExpiredProofToSpecificPublicError() {
    RFWAuthenticationOutcomeVO outcome = RFWRegistrationProviderAdapter.mapActivation(
        RegistrationActivationResultVO.of(
            RegistrationActivationStatusEnum.EXPIRED_PROOF));

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().messageKey())
        .isEqualTo("registration.activation.expired-proof");
  }

  /**
   * Impede que um replay produza autenticação ou repita o resultado da primeira ativação.
   */
  @Test
  void activate_shouldRejectUsedProofWithoutRepeatingActivation() {
    RFWAuthenticationOutcomeVO outcome = RFWRegistrationProviderAdapter.mapActivation(
        RegistrationActivationResultVO.of(
            RegistrationActivationStatusEnum.ALREADY_ACTIVE));

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().messageKey())
        .isEqualTo("registration.activation.used-proof");
    assertThat(outcome.authentication()).isNull();
  }

  /**
   * Orienta uma nova inscrição somente quando a prova correlaciona um processo encerrado.
   */
  @Test
  void activate_shouldMapClosedRegistrationToSpecificPublicError() {
    RFWAuthenticationOutcomeVO outcome = RFWRegistrationProviderAdapter.mapActivation(
        RegistrationActivationResultVO.of(
            RegistrationActivationStatusEnum.REGISTRATION_CLOSED));

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().messageKey())
        .isEqualTo("registration.activation.registration-closed");
  }

  @Test
  void activate_shouldPreserveFacadeFieldErrors() {
    RFWAuthenticationOutcomeVO outcome = RFWRegistrationProviderAdapter.mapActivation(
        RegistrationActivationResultVO.validationRejected(
            Map.of("proof", "registration.activation.invalid-proof")));

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().fieldErrors())
        .containsEntry("proof", "registration.activation.invalid-proof");
  }

  @Test
  void activate_shouldMapUnavailableFacadeToPublicError() {
    RFWAuthenticationOutcomeVO outcome = RFWRegistrationProviderAdapter.mapActivation(
        RegistrationActivationResultVO.of(
            RegistrationActivationStatusEnum.UNAVAILABLE));

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().messageKey()).isEqualTo("registration.unavailable");
  }

  private static RFWRegistrationProviderAdapter adapter(
      RegistrationStartFacade facade,
      RFWRemoteAddressProvider remoteAddressProvider) {
    return new RFWRegistrationProviderAdapter(
        facade,
        Mockito.mock(RegistrationResendFacade.class),
        Mockito.mock(RegistrationActivationFacade.class),
        remoteAddressProvider);
  }

  private static void attachRequest() {
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
        new MockHttpServletRequest(),
        new MockHttpServletResponse()));
  }
}
