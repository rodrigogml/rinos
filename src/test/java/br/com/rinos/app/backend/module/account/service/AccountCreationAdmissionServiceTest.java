package br.com.rinos.app.backend.module.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.identity.enums.OriginOperationEnum;
import br.com.rinos.app.backend.module.identity.enums.OriginReservationStatusEnum;
import br.com.rinos.app.backend.module.identity.service.OriginAddressService;
import br.com.rinos.app.backend.module.identity.service.OriginLimitService;
import br.com.rinos.app.backend.module.identity.vo.OriginAddressVO;
import br.com.rinos.app.backend.module.identity.vo.OriginReservationResultVO;
import br.com.rinos.app.config.AccountCreationPropertiesConfig;

@DisplayName("Admissão de criação de conta")
class AccountCreationAdmissionServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");
  private static final OriginAddressVO ORIGIN = new OriginAddressService().normalize("203.0.113.10");

  @Test
  void admit_shouldRequireHumanVerificationWithoutReserving_whenThresholdIsReached() {
    OriginLimitService limit = mock(OriginLimitService.class);
    when(limit.requiresTurnstile(ORIGIN, OriginOperationEnum.ACCOUNT_CREATION, 0)).thenReturn(true);
    AccountCreationAdmissionService service = service(limit);

    AccountCreationAdmissionResult result = service.admit(ORIGIN, false, NOW);

    assertThat(result.admitted()).isFalse();
    assertThat(result.safeReasonCode()).isEqualTo("ACCOUNT_HUMAN_VERIFICATION_REQUIRED");
    verify(limit).requiresTurnstile(ORIGIN, OriginOperationEnum.ACCOUNT_CREATION, 0);
    verifyNoMoreInteractions(limit);
  }

  @Test
  void admit_shouldReturnRetryAfter_whenOriginIsBlocked() {
    OriginLimitService limit = mock(OriginLimitService.class);
    Instant blockedUntil = NOW.plus(Duration.ofMinutes(7));
    when(limit.requiresTurnstile(ORIGIN, OriginOperationEnum.ACCOUNT_CREATION, 0)).thenReturn(false);
    when(limit.reserve(eq(ORIGIN), eq(OriginOperationEnum.ACCOUNT_CREATION), eq(5), any(), any()))
        .thenReturn(new OriginReservationResultVO(OriginReservationStatusEnum.BLOCKED, blockedUntil));
    AccountCreationAdmissionService service = service(limit);

    AccountCreationAdmissionResult result = service.admit(ORIGIN, true, NOW);

    assertThat(result.admitted()).isFalse();
    assertThat(result.safeReasonCode()).isEqualTo("ACCOUNT_RATE_LIMITED");
    assertThat(result.retryAfter()).isEqualTo(Duration.ofMinutes(7));
    verify(limit).reserve(
        ORIGIN,
        OriginOperationEnum.ACCOUNT_CREATION,
        5,
        Duration.ofMinutes(15),
        Duration.ofMinutes(30));
  }

  private static AccountCreationAdmissionService service(OriginLimitService limit) {
    return new AccountCreationAdmissionService(limit, new AccountCreationPropertiesConfig(
        0,
        Duration.ofMinutes(15),
        5,
        Duration.ofMinutes(30),
        Duration.ofDays(30),
        25,
        Duration.ofMinutes(2),
        Duration.ofMinutes(1),
        Duration.ofHours(1)));
  }

}
