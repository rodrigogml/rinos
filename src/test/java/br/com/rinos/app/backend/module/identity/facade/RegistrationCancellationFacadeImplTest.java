package br.com.rinos.app.backend.module.identity.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.dto.RegistrationCancellationConfirmationDTO;
import br.com.rinos.app.api.dto.RegistrationCancellationRequestDTO;
import br.com.rinos.app.api.enums.RegistrationCancellationConfirmationStatusEnum;
import br.com.rinos.app.api.enums.RegistrationCancellationRequestStatusEnum;
import br.com.rinos.app.api.vo.RegistrationCancellationConfirmationResultVO;
import br.com.rinos.app.api.vo.RegistrationCancellationRequestResultVO;
import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.RegistrationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationOperationEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationEmailDispatchStatusEnum;
import br.com.rinos.app.backend.module.identity.service.IdentityService;
import br.com.rinos.app.backend.module.identity.service.RegistrationCancellationService;
import br.com.rinos.app.backend.module.identity.service.RegistrationObservabilityService;
import br.com.rinos.app.backend.module.identity.vo.RegistrationCancellationIssueVO;
import br.com.rinos.app.backend.module.identity.vo.VerificationEmailDispatchResultVO;
import br.com.rinos.app.config.VerificationPropertiesConfig;

@DisplayName("Fachada neutra de cancelamento")
class RegistrationCancellationFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
  private static final UUID CORRELATION_ID =
      UUID.fromString("75507575-31b7-41b9-b103-55cdf417a622");

  @Test
  void request_shouldReturnSamePublicShape_forExistingAndAbsentRegistration() {
    IdentityService identityService = mock(IdentityService.class);
    RegistrationCancellationService service =
        mock(RegistrationCancellationService.class);
    RegistrationEntity registration = registration();
    when(identityService.findPendingRegistration("existing@example.com"))
        .thenReturn(Optional.of(registration));
    when(identityService.findPendingRegistration("absent@example.com"))
        .thenReturn(Optional.empty());
    when(service.issue(any(), any(), any(), any()))
        .thenReturn(RegistrationCancellationIssueVO.issued(
            NOW.plus(Duration.ofHours(24)),
            CompletableFuture.completedFuture(new VerificationEmailDispatchResultVO(
                VerificationEmailDispatchStatusEnum.ACCEPTED,
                CORRELATION_ID,
                Duration.ZERO))));
    RegistrationObservabilityService observability =
        mock(RegistrationObservabilityService.class);
    RegistrationCancellationFacadeImpl facade =
        facade(identityService, service, observability);

    RegistrationCancellationRequestResultVO existing = facade.requestCancellation(
        request("existing@example.com")).toCompletableFuture().join();
    RegistrationCancellationRequestResultVO absent = facade.requestCancellation(
        request("absent@example.com")).toCompletableFuture().join();

    assertThat(existing.status())
        .isEqualTo(RegistrationCancellationRequestStatusEnum.REQUEST_ACCEPTED);
    assertThat(absent.status()).isEqualTo(existing.status());
    assertThat(absent.expiresAt()).isEqualTo(existing.expiresAt());
    assertThat(absent.challengeReference()).isNotBlank();
    assertThat(existing.challengeReference()).isNotBlank();
    verify(service).issue(any(), any(), any(), any());
    verify(observability, org.mockito.Mockito.times(2)).recordOperation(
        RegistrationOperationEnum.CANCELLATION_REQUEST,
        RegistrationCancellationRequestStatusEnum.REQUEST_ACCEPTED.name(),
        CORRELATION_ID,
        NOW,
        NOW);
  }

  @Test
  void request_shouldNotCallCancellationService_whenRegistrationIsAbsent() {
    IdentityService identityService = mock(IdentityService.class);
    RegistrationCancellationService service =
        mock(RegistrationCancellationService.class);
    when(identityService.findPendingRegistration("absent@example.com"))
        .thenReturn(Optional.empty());
    RegistrationCancellationFacadeImpl facade = facade(identityService, service);

    facade.requestCancellation(request("absent@example.com"))
        .toCompletableFuture()
        .join();

    verify(service, never()).issue(any(), any(), any(), any());
  }

  @Test
  void confirm_shouldMeasureOnlyThePublicResult() {
    IdentityService identityService = mock(IdentityService.class);
    RegistrationCancellationService service =
        mock(RegistrationCancellationService.class);
    RegistrationObservabilityService observability =
        mock(RegistrationObservabilityService.class);
    when(service.confirm(
        "person@example.com",
        "opaque-proof",
        CORRELATION_ID,
        NOW)).thenReturn(RegistrationCancellationConfirmationStatusEnum.CANCELLED);
    RegistrationCancellationFacadeImpl facade =
        facade(identityService, service, observability);

    RegistrationCancellationConfirmationResultVO result = facade.confirmCancellation(
        new RegistrationCancellationConfirmationDTO(
            "person@example.com",
            "opaque-proof",
            CORRELATION_ID))
        .toCompletableFuture()
        .join();

    assertThat(result.status())
        .isEqualTo(RegistrationCancellationConfirmationStatusEnum.CANCELLED);
    verify(observability).recordOperation(
        RegistrationOperationEnum.CANCELLATION_CONFIRM,
        RegistrationCancellationConfirmationStatusEnum.CANCELLED.name(),
        CORRELATION_ID,
        NOW,
        NOW);
  }

  private static RegistrationCancellationFacadeImpl facade(
      IdentityService identityService,
      RegistrationCancellationService service) {
    return facade(
        identityService,
        service,
        mock(RegistrationObservabilityService.class));
  }

  private static RegistrationCancellationFacadeImpl facade(
      IdentityService identityService,
      RegistrationCancellationService service,
      RegistrationObservabilityService observabilityService) {
    return new RegistrationCancellationFacadeImpl(
        identityService,
        service,
        new VerificationPropertiesConfig(Duration.ofHours(24)),
        observabilityService,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static RegistrationCancellationRequestDTO request(String identifier) {
    return new RegistrationCancellationRequestDTO(
        identifier,
        Locale.of("pt", "BR"),
        CORRELATION_ID);
  }

  private static RegistrationEntity registration() {
    UserEntity user = new UserEntity(
        "existing@example.com",
        "existing@example.com",
        UserStatusEnum.PENDING_VERIFICATION);
    return new RegistrationEntity(
        user,
        RegistrationMethodEnum.LOCAL,
        RegistrationStatusEnum.PENDING_VERIFICATION,
        NOW.plus(Duration.ofDays(15)));
  }
}
