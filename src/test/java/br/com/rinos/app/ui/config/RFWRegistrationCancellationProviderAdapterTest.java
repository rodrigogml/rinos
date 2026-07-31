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

    var outcome = adapter.requestCancellation(
        new RFWRegistrationCancellationRequestDTO("person@example.com", null))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status())
        .isEqualTo(RFWAccessStatusEnum.REGISTRATION_CANCELLATION_REQUIRED);
    assertThat(outcome.challenge().challengeId()).isEqualTo("neutral-reference");
    assertThat(outcome.challenge().maskedDestination()).isNull();
  }

  @Test
  void confirmCancellation_shouldMapSuccessfulRemoval() {
    RegistrationCancellationFacade facade = mock(RegistrationCancellationFacade.class);
    when(facade.confirmCancellation(any())).thenReturn(CompletableFuture.completedFuture(
        RegistrationCancellationConfirmationResultVO.of(
            RegistrationCancellationConfirmationStatusEnum.CANCELLED)));
    RFWRegistrationCancellationProviderAdapter adapter =
        new RFWRegistrationCancellationProviderAdapter(facade);

    var outcome = adapter.confirmCancellation(
        new RFWRegistrationCancellationConfirmationDTO(
            "person@example.com",
            "opaque-proof"))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.COMPLETED);
    assertThat(outcome.messageKey()).isEqualTo("registration.cancellation.completed");
  }
}
