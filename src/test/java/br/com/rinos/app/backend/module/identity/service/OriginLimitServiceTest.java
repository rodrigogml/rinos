package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import br.com.rinos.app.backend.module.identity.entity.OriginWindowEntity;
import br.com.rinos.app.backend.module.identity.enums.OriginOperationEnum;
import br.com.rinos.app.backend.module.identity.enums.OriginPolicyEnum;
import br.com.rinos.app.backend.module.identity.enums.OriginReservationStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.OriginWindowRepository;
import br.com.rinos.app.backend.module.identity.vo.OriginAddressVO;
import br.com.rinos.app.backend.module.identity.vo.OriginReservationResultVO;
import br.com.rinos.app.config.OriginPropertiesConfig;

@DisplayName("Limite atômico por origem")
class OriginLimitServiceTest {

  private final OriginAddressVO origin =
      new OriginAddressService().normalize("192.0.2.10");

  @Test
  void requiresTurnstile_shouldAlwaysRequire_whenThresholdIsZero() {
    OriginWindowRepository repository = mock(OriginWindowRepository.class);
    OriginLimitService service = service(repository, 0, 20);

    assertThat(service.requiresTurnstile(origin, OriginOperationEnum.USER_REGISTRATION))
        .isTrue();
    verifyNoInteractions(repository);
  }

  @Test
  void requiresTurnstile_shouldUseCanonicalCounter_whenThresholdIsConfigured() {
    OriginWindowRepository repository = mock(OriginWindowRepository.class);
    OriginWindowEntity window = window();
    org.springframework.test.util.ReflectionTestUtils.setField(window, "eventCount", 2);
    when(repository.findCurrent(
        origin.getAddress(),
        OriginOperationEnum.USER_REGISTRATION,
        OriginPolicyEnum.ABSOLUTE_LIMIT))
        .thenReturn(Optional.of(window));
    OriginLimitService service = service(repository, 2, 20);

    assertThat(service.requiresTurnstile(origin, OriginOperationEnum.USER_REGISTRATION))
        .isTrue();
  }

  @Test
  void reserveNewRegistration_shouldReturnReservedOrBlockedWithoutExposingCount() {
    OriginWindowRepository repository = mock(OriginWindowRepository.class);
    when(repository.findCurrentBlocked(
        origin.getAddress(), OriginOperationEnum.USER_REGISTRATION, OriginPolicyEnum.ABSOLUTE_LIMIT))
        .thenReturn(Optional.empty());
    when(repository.incrementBelowLimit(
        origin.getAddress(),
        "USER_REGISTRATION",
        "ABSOLUTE_LIMIT",
        20))
        .thenReturn(1, 0);
    when(repository.findCurrent(
        origin.getAddress(),
        OriginOperationEnum.USER_REGISTRATION,
        OriginPolicyEnum.ABSOLUTE_LIMIT))
        .thenReturn(Optional.of(window()));
    OriginLimitService service = service(repository, 0, 20);

    assertThat(service.reserveNewRegistration(
        origin,
        OriginOperationEnum.USER_REGISTRATION).status())
        .isEqualTo(OriginReservationStatusEnum.RESERVED);
    OriginReservationResultVO blocked = service.reserveNewRegistration(
        origin,
        OriginOperationEnum.USER_REGISTRATION);
    assertThat(blocked.status()).isEqualTo(OriginReservationStatusEnum.BLOCKED);
    assertThat(blocked.blockedUntil()).isEqualTo(Instant.parse("2026-07-30T18:00:00Z"));
    assertThat(blocked.toString()).doesNotContain("eventCount");
  }

  @Test
  void reserveNewRegistration_shouldCreateBeforeClosingExpiredWindow_whenWindowMayNotExist() {
    OriginWindowRepository repository = mock(OriginWindowRepository.class);
    when(repository.findCurrentBlocked(
        origin.getAddress(), OriginOperationEnum.USER_REGISTRATION, OriginPolicyEnum.ABSOLUTE_LIMIT))
        .thenReturn(Optional.empty());
    when(repository.incrementBelowLimit(
        origin.getAddress(),
        "USER_REGISTRATION",
        "ABSOLUTE_LIMIT",
        20))
        .thenReturn(1);
    OriginLimitService service = service(repository, 0, 20);

    service.reserveNewRegistration(origin, OriginOperationEnum.USER_REGISTRATION);

    InOrder order = inOrder(repository);
    order.verify(repository).createActiveIfAbsent(
        origin.getAddress(),
        "USER_REGISTRATION",
        "ABSOLUTE_LIMIT",
        Duration.ofHours(24).toSeconds() * 1_000_000L);
    order.verify(repository).closeExpired(
        origin.getAddress(),
        "USER_REGISTRATION",
        "ABSOLUTE_LIMIT");
    order.verify(repository).createActiveIfAbsent(
        origin.getAddress(),
        "USER_REGISTRATION",
        "ABSOLUTE_LIMIT",
        Duration.ofHours(24).toSeconds() * 1_000_000L);
  }

  @Test
  void reserve_shouldPersistConfiguredBlockPeriod_whenLimitIsReached() {
    OriginWindowRepository repository = mock(OriginWindowRepository.class);
    OriginWindowEntity blockedWindow = window();
    Instant blockedUntil = Instant.parse("2026-07-29T19:00:00Z");
    org.springframework.test.util.ReflectionTestUtils.setField(
        blockedWindow, "blockedUntil", blockedUntil);
    when(repository.findCurrentBlocked(
        origin.getAddress(), OriginOperationEnum.ACCOUNT_CREATION, OriginPolicyEnum.ABSOLUTE_LIMIT))
        .thenReturn(Optional.empty(), Optional.of(blockedWindow));
    when(repository.incrementBelowLimit(
        origin.getAddress(), "ACCOUNT_CREATION", "ABSOLUTE_LIMIT", 5)).thenReturn(0);
    OriginLimitService service = service(repository, 0, 20);

    OriginReservationResultVO result = service.reserve(
        origin,
        OriginOperationEnum.ACCOUNT_CREATION,
        5,
        Duration.ofMinutes(15),
        Duration.ofHours(1));

    assertThat(result.status()).isEqualTo(OriginReservationStatusEnum.BLOCKED);
    assertThat(result.blockedUntil()).isEqualTo(blockedUntil);
    verify(repository).blockCurrent(
        origin.getAddress(),
        "ACCOUNT_CREATION",
        "ABSOLUTE_LIMIT",
        Duration.ofHours(1).toSeconds() * 1_000_000L);
  }

  private static OriginLimitService service(
      OriginWindowRepository repository,
      int threshold,
      int limit) {
    return new OriginLimitService(
        repository,
        new OriginPropertiesConfig(
            threshold,
            limit,
            Duration.ofHours(24),
            Duration.ofDays(30)));
  }

  private OriginWindowEntity window() {
    return new OriginWindowEntity(
        origin.getAddress(),
        OriginOperationEnum.USER_REGISTRATION,
        OriginPolicyEnum.ABSOLUTE_LIMIT,
        Instant.parse("2026-07-29T18:00:00Z"),
        Instant.parse("2026-07-30T18:00:00Z"));
  }
}
