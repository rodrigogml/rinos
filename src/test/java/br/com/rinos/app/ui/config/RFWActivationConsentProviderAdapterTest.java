package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.rinos.app.api.dto.ActivationConsentRequestDTO;
import br.com.rinos.app.api.enums.RegistrationActivationStatusEnum;
import br.com.rinos.app.api.facade.RegistrationActivationFacade;
import br.com.rinos.app.api.vo.RegistrationActivationResultVO;
import br.eng.rodrigogml.rfw.platform.authentication.dto.RFWActivationConsentRequestDTO;
import br.eng.rodrigogml.rfw.platform.authentication.enums.RFWAccessStatusEnum;
import br.eng.rodrigogml.rfw.platform.authentication.vo.RFWAuthenticationOutcomeVO;

@DisplayName("Adapter RFW da continuação legal da ativação")
class RFWActivationConsentProviderAdapterTest {

  @Test
  void completeActivationConsent_shouldMapCompletedActivation() {
    RegistrationActivationFacade facade = mock(RegistrationActivationFacade.class);
    when(facade.completeConsent(any())).thenReturn(CompletableFuture.completedFuture(
        RegistrationActivationResultVO.of(
            RegistrationActivationStatusEnum.ACTIVATED)));
    RFWActivationConsentProviderAdapter adapter =
        new RFWActivationConsentProviderAdapter(facade);

    RFWAuthenticationOutcomeVO outcome = adapter.completeActivationConsent(
        new RFWActivationConsentRequestDTO("opaque-proof", List.of("1", "2")))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.COMPLETED);
    assertThat(outcome.messageKey()).isEqualTo("registration.activation-completed");

    ArgumentCaptor<ActivationConsentRequestDTO> command =
        ArgumentCaptor.forClass(ActivationConsentRequestDTO.class);
    verify(facade).completeConsent(command.capture());
    assertThat(command.getValue().getActivationReference()).isEqualTo("opaque-proof");
    assertThat(command.getValue().getAcceptedLegalDocumentIds())
        .containsExactly("1", "2");
    assertThat(command.getValue().getCorrelationId()).isNotNull();
  }

  @Test
  void completeActivationConsent_shouldKeepConsentOpen_whenLegalVersionChangesAgain() {
    Instant expiresAt = Instant.parse("2026-07-31T12:00:00Z");
    RegistrationActivationFacade facade = mock(RegistrationActivationFacade.class);
    when(facade.completeConsent(any())).thenReturn(CompletableFuture.completedFuture(
        RegistrationActivationResultVO.consentRequired(
            "opaque-proof",
            "verified@example.test",
            Set.of("3"),
            expiresAt)));
    RFWActivationConsentProviderAdapter adapter =
        new RFWActivationConsentProviderAdapter(facade);

    RFWAuthenticationOutcomeVO outcome = adapter.completeActivationConsent(
        new RFWActivationConsentRequestDTO("opaque-proof", List.of("1", "2")))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status())
        .isEqualTo(RFWAccessStatusEnum.ACTIVATION_CONSENT_REQUIRED);
    assertThat(outcome.activationConsent().activationReference())
        .isEqualTo("opaque-proof");
    assertThat(outcome.activationConsent().verifiedEmail())
        .isEqualTo("verified@example.test");
    assertThat(outcome.activationConsent().legalDocumentIds())
        .containsExactly("3");
    assertThat(outcome.activationConsent().expiresAt()).isEqualTo(expiresAt);
  }
}
