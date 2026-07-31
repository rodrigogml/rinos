package br.com.rinos.app.backend.module.identity.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.dto.ActivationConsentRequestDTO;
import br.com.rinos.app.api.dto.RegistrationActivationRequestDTO;
import br.com.rinos.app.api.enums.RegistrationActivationStatusEnum;
import br.com.rinos.app.api.vo.RegistrationActivationResultVO;
import br.com.rinos.app.backend.module.identity.enums.RegistrationOperationEnum;
import br.com.rinos.app.backend.module.identity.service.RegistrationActivationService;
import br.com.rinos.app.backend.module.identity.service.RegistrationObservabilityService;

@DisplayName("Fachada pública de ativação")
class RegistrationActivationFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
  private static final UUID CORRELATION_ID =
      UUID.fromString("75507575-31b7-41b9-b103-55cdf417a622");

  @Test
  void activate_shouldRejectMissingProof_withoutCallingDomain() {
    RegistrationActivationService service = mock(RegistrationActivationService.class);
    RegistrationActivationFacadeImpl facade = facade(service);

    RegistrationActivationResultVO result = facade.activate(
        new RegistrationActivationRequestDTO(null, " ", CORRELATION_ID))
        .toCompletableFuture()
        .join();

    assertThat(result.status())
        .isEqualTo(RegistrationActivationStatusEnum.VALIDATION_REJECTED);
    assertThat(result.fieldErrors()).containsKey("proof");
    verifyNoInteractions(service);
  }

  @Test
  void completeConsent_shouldRejectNonNumericDocument_withoutCallingDomain() {
    RegistrationActivationService service = mock(RegistrationActivationService.class);
    RegistrationActivationFacadeImpl facade = facade(service);

    RegistrationActivationResultVO result = facade.completeConsent(
        new ActivationConsentRequestDTO(
            "opaque-proof",
            List.of("not-a-number"),
            CORRELATION_ID))
        .toCompletableFuture()
        .join();

    assertThat(result.status())
        .isEqualTo(RegistrationActivationStatusEnum.VALIDATION_REJECTED);
    assertThat(result.fieldErrors()).containsKey("acceptedLegalDocumentIds");
    verifyNoInteractions(service);
  }

  @Test
  void activate_shouldReturnDomainResult_whenRequestIsValid() {
    RegistrationActivationService service = mock(RegistrationActivationService.class);
    when(service.activate("opaque-proof", CORRELATION_ID, NOW))
        .thenReturn(RegistrationActivationResultVO.of(
            RegistrationActivationStatusEnum.ACTIVATED));
    RegistrationObservabilityService observability =
        mock(RegistrationObservabilityService.class);
    RegistrationActivationFacadeImpl facade = facade(service, observability);

    RegistrationActivationResultVO result = facade.activate(
        new RegistrationActivationRequestDTO(
            null,
            "opaque-proof",
            CORRELATION_ID))
        .toCompletableFuture()
        .join();

    assertThat(result.status()).isEqualTo(RegistrationActivationStatusEnum.ACTIVATED);
    org.mockito.Mockito.verify(observability).recordOperation(
        RegistrationOperationEnum.ACTIVATE,
        RegistrationActivationStatusEnum.ACTIVATED.name(),
        CORRELATION_ID,
        NOW,
        NOW);
  }

  private static RegistrationActivationFacadeImpl facade(
      RegistrationActivationService service) {
    return facade(service, mock(RegistrationObservabilityService.class));
  }

  private static RegistrationActivationFacadeImpl facade(
      RegistrationActivationService service,
      RegistrationObservabilityService observabilityService) {
    return new RegistrationActivationFacadeImpl(
        service,
        observabilityService,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }
}
