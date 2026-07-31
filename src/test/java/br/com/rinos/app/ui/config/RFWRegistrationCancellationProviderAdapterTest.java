package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.enums.RegistrationCancellationConfirmationStatusEnum;
import br.com.rinos.app.api.enums.RegistrationCancellationRequestStatusEnum;
import br.com.rinos.app.api.facade.RegistrationCancellationFacade;
import br.com.rinos.app.api.vo.RegistrationCancellationConfirmationResultVO;
import br.com.rinos.app.api.vo.RegistrationCancellationRequestResultVO;
import br.eng.rodrigogml.rfw.platform.authentication.dto.RFWRegistrationCancellationConfirmationDTO;
import br.eng.rodrigogml.rfw.platform.authentication.dto.RFWRegistrationCancellationRequestDTO;
import br.eng.rodrigogml.rfw.platform.authentication.enums.RFWAccessStatusEnum;
import br.eng.rodrigogml.rfw.platform.authentication.vo.RFWAuthenticationOutcomeVO;

@DisplayName("Adapter RFW do cancelamento de cadastro")
class RFWRegistrationCancellationProviderAdapterTest {

  @Test
  void requestCancellation_shouldAlwaysOpenConfirmationForAcceptedRequest() {
    RegistrationCancellationFacade facade = mock(RegistrationCancellationFacade.class);
    when(facade.requestCancellation(any())).thenReturn(CompletableFuture.completedFuture(
        new RegistrationCancellationRequestResultVO(
            RegistrationCancellationRequestStatusEnum.REQUEST_ACCEPTED,
            "neutral-reference",
            Instant.parse("2026-07-30T12:00:00Z"),
            Map.of())));
    RFWRegistrationCancellationProviderAdapter adapter =
        new RFWRegistrationCancellationProviderAdapter(facade);

    RFWAuthenticationOutcomeVO outcome = adapter.requestCancellation(
        new RFWRegistrationCancellationRequestDTO("person@example.com", null))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status())
        .isEqualTo(RFWAccessStatusEnum.REGISTRATION_CANCELLATION_REQUIRED);
    assertThat(outcome.challenge().challengeId()).isEqualTo("neutral-reference");
    assertThat(outcome.challenge().maskedDestination()).isNull();
  }

  @Test
  void requestCancellation_shouldPreserveFacadeFieldErrors() {
    RegistrationCancellationFacade facade = mock(RegistrationCancellationFacade.class);
    when(facade.requestCancellation(any())).thenReturn(CompletableFuture.completedFuture(
        new RegistrationCancellationRequestResultVO(
            RegistrationCancellationRequestStatusEnum.VALIDATION_REJECTED,
            null,
            null,
            Map.of("identifier", "registration.error.email.invalid"))));
    RFWRegistrationCancellationProviderAdapter adapter =
        new RFWRegistrationCancellationProviderAdapter(facade);

    RFWAuthenticationOutcomeVO outcome = adapter.requestCancellation(
        new RFWRegistrationCancellationRequestDTO("invalid-email", null))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().fieldErrors())
        .containsEntry("identifier", "registration.error.email.invalid");
  }

  @Test
  void confirmCancellation_shouldMapSuccessfulRemoval() {
    RegistrationCancellationFacade facade = mock(RegistrationCancellationFacade.class);
    when(facade.confirmCancellation(any())).thenReturn(CompletableFuture.completedFuture(
        RegistrationCancellationConfirmationResultVO.of(
            RegistrationCancellationConfirmationStatusEnum.CANCELLED)));
    RFWRegistrationCancellationProviderAdapter adapter =
        new RFWRegistrationCancellationProviderAdapter(facade);

    RFWAuthenticationOutcomeVO outcome = adapter.confirmCancellation(
        new RFWRegistrationCancellationConfirmationDTO(
            "person@example.com",
            "opaque-proof"))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.COMPLETED);
    assertThat(outcome.messageKey()).isEqualTo("registration.cancellation.completed");
  }

  @Test
  void confirmCancellation_shouldMapInvalidProofToProofField() {
    RegistrationCancellationFacade facade = mock(RegistrationCancellationFacade.class);
    when(facade.confirmCancellation(any())).thenReturn(CompletableFuture.completedFuture(
        RegistrationCancellationConfirmationResultVO.of(
            RegistrationCancellationConfirmationStatusEnum.INVALID_PROOF)));
    RFWRegistrationCancellationProviderAdapter adapter =
        new RFWRegistrationCancellationProviderAdapter(facade);

    RFWAuthenticationOutcomeVO outcome = adapter.confirmCancellation(
        new RFWRegistrationCancellationConfirmationDTO(
            "person@example.com",
            "invalid-proof"))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().fieldErrors())
        .containsEntry("proof", "registration.cancellation.invalid-proof");
  }

  @Test
  void confirmCancellation_shouldMapExpiredProofToProofField() {
    RegistrationCancellationFacade facade = mock(RegistrationCancellationFacade.class);
    when(facade.confirmCancellation(any())).thenReturn(CompletableFuture.completedFuture(
        RegistrationCancellationConfirmationResultVO.of(
            RegistrationCancellationConfirmationStatusEnum.EXPIRED_PROOF)));
    RFWRegistrationCancellationProviderAdapter adapter =
        new RFWRegistrationCancellationProviderAdapter(facade);

    RFWAuthenticationOutcomeVO outcome = adapter.confirmCancellation(
        new RFWRegistrationCancellationConfirmationDTO(
            "person@example.com",
            "expired-proof"))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().fieldErrors())
        .containsEntry("proof", "registration.cancellation.expired-proof");
  }

  @Test
  void confirmCancellation_shouldPreserveFacadeFieldErrors() {
    RegistrationCancellationFacade facade = mock(RegistrationCancellationFacade.class);
    when(facade.confirmCancellation(any())).thenReturn(CompletableFuture.completedFuture(
        new RegistrationCancellationConfirmationResultVO(
            RegistrationCancellationConfirmationStatusEnum.VALIDATION_REJECTED,
            Map.of("proof", "registration.error.proof.required"))));
    RFWRegistrationCancellationProviderAdapter adapter =
        new RFWRegistrationCancellationProviderAdapter(facade);

    RFWAuthenticationOutcomeVO outcome = adapter.confirmCancellation(
        new RFWRegistrationCancellationConfirmationDTO(
            "person@example.com",
            null))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().fieldErrors())
        .containsEntry("proof", "registration.error.proof.required");
  }

  @Test
  void confirmCancellation_shouldMapUnavailableFacadeToPublicError() {
    RegistrationCancellationFacade facade = mock(RegistrationCancellationFacade.class);
    when(facade.confirmCancellation(any())).thenReturn(CompletableFuture.completedFuture(
        RegistrationCancellationConfirmationResultVO.of(
            RegistrationCancellationConfirmationStatusEnum.UNAVAILABLE)));
    RFWRegistrationCancellationProviderAdapter adapter =
        new RFWRegistrationCancellationProviderAdapter(facade);

    RFWAuthenticationOutcomeVO outcome = adapter.confirmCancellation(
        new RFWRegistrationCancellationConfirmationDTO(
            "person@example.com",
            "opaque-proof"))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().messageKey()).isEqualTo("registration.unavailable");
  }
}
