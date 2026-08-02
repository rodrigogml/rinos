package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.RegistrationLifecycleEventEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.RegistrationRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.platform.service.MaintenanceCoordinatorService;
import br.com.rinos.app.config.CleanupPropertiesConfig;

@DisplayName("Expiração coordenada de cadastros pendentes")
class RegistrationExpiryCleanupServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");

  private RegistrationRepository registrationRepository;
  private UserRepository userRepository;
  private IdentityAuditService auditService;
  private MaintenanceCoordinatorService coordinator;
  private RegistrationObservabilityService observabilityService;

  @BeforeEach
  void setUp() {
    registrationRepository = mock(RegistrationRepository.class);
    userRepository = mock(UserRepository.class);
    auditService = mock(IdentityAuditService.class);
    coordinator = mock(MaintenanceCoordinatorService.class);
    observabilityService = mock(RegistrationObservabilityService.class);
  }

  @Test
  void cleanup_shouldRemainSuspended_whenLeadershipIsNotProven() {
    when(coordinator.canStartJob()).thenReturn(false);
    RegistrationExpiryCleanupService service = service(2);

    assertThat(service.cleanup(NOW)).isZero();

    verify(registrationRepository, never())
        .findExpiredPendingBatchForUpdate(any(), any(), any(), any());
  }

  @Test
  void cleanup_shouldDeleteAllFullAndPartialBatches() {
    RegistrationEntity first = pending(1L, 11L);
    RegistrationEntity second = pending(2L, 12L);
    RegistrationEntity third = pending(3L, 13L);
    when(coordinator.canStartJob()).thenReturn(true);
    when(coordinator.executeBatch(any())).thenAnswer(invocation -> {
      invocation.getArgument(0, Runnable.class).run();
      return true;
    });
    when(registrationRepository.findExpiredPendingBatchForUpdate(
        any(), any(), any(), any()))
        .thenReturn(List.of(first, second), List.of(third));
    RegistrationExpiryCleanupService service = service(2);

    int deleted = service.cleanup(NOW);

    assertThat(deleted).isEqualTo(3);
    assertThat(List.of(first, second, third))
        .extracting(RegistrationEntity::getStatus)
        .containsOnly(RegistrationStatusEnum.EXPIRED);
    verify(registrationRepository, times(2)).flush();
    verify(registrationRepository, times(3)).delete(any(RegistrationEntity.class));
    verify(userRepository, times(3)).delete(any(UserEntity.class));
    verify(userRepository, times(2)).flush();
    verify(coordinator, times(2)).executeBatch(any());
    verify(observabilityService).recordLifecycle(
        RegistrationLifecycleEventEnum.EXPIRED,
        2);
    verify(observabilityService).recordLifecycle(
        RegistrationLifecycleEventEnum.EXPIRED,
        1);
  }

  @Test
  void cleanup_shouldNeverDeleteActivatedUser_evenIfRepositoryReturnsInconsistentRow() {
    RegistrationEntity inconsistent = pending(1L, 11L);
    inconsistent.getUser().setStatus(UserStatusEnum.ACTIVE);
    when(coordinator.canStartJob()).thenReturn(true);
    when(coordinator.executeBatch(any())).thenAnswer(invocation -> {
      invocation.getArgument(0, Runnable.class).run();
      return true;
    });
    when(registrationRepository.findExpiredPendingBatchForUpdate(
        any(), any(), any(), any()))
        .thenReturn(List.of(inconsistent));
    RegistrationExpiryCleanupService service = service(2);

    assertThat(service.cleanup(NOW)).isZero();

    verify(userRepository, never()).delete(any());
    verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void cleanup_shouldStopBeforeNextBatch_whenLeadershipIsLost() {
    when(coordinator.canStartJob()).thenReturn(true);
    when(coordinator.executeBatch(any())).thenAnswer(invocation -> {
      invocation.getArgument(0, Runnable.class).run();
      return true;
    }).thenReturn(false);
    when(registrationRepository.findExpiredPendingBatchForUpdate(
        any(), any(), any(), any()))
        .thenReturn(List.of(pending(1L, 11L), pending(2L, 12L)));
    RegistrationExpiryCleanupService service = service(2);

    assertThat(service.cleanup(NOW)).isEqualTo(2);

    verify(coordinator, times(2)).executeBatch(any());
  }

  private RegistrationExpiryCleanupService service(int batchSize) {
    return new RegistrationExpiryCleanupService(
        registrationRepository,
        userRepository,
        new RegistrationLifecycleService(),
        auditService,
        coordinator,
        new CleanupPropertiesConfig(Duration.ofHours(24), batchSize),
        observabilityService);
  }

  private static RegistrationEntity pending(long userId, long registrationId) {
    UserEntity user = new UserEntity(
        "person" + userId + "@example.com",
        "person" + userId + "@example.com",
        UserStatusEnum.PENDING_VERIFICATION);
    ReflectionTestUtils.setField(user, "id", userId);
    RegistrationEntity registration = new RegistrationEntity(
        user,
        RegistrationMethodEnum.LOCAL,
        RegistrationStatusEnum.PENDING_VERIFICATION,
        NOW.minusSeconds(1));
    ReflectionTestUtils.setField(registration, "id", registrationId);
    return registration;
  }
}
