package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.rinos.app.backend.module.identity.entity.IdentityEventEntity;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.repository.IdentityEventRepository;
import br.com.rinos.app.backend.module.identity.vo.IdentityEventReferenceVO;

@DisplayName("Auditoria da identidade")
class IdentityAuditServiceTest {

  @Test
  void recordCancellationTombstone_shouldPersistNoDirectIdentifier() {
    IdentityEventRepository repository = mock(IdentityEventRepository.class);
    when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    IdentityAuditService service = new IdentityAuditService(repository);
    UUID correlation = UUID.randomUUID();

    IdentityEventReferenceVO reference = service.recordCancellationTombstone(
        correlation,
        IdentityTransitionOriginEnum.SYSTEM,
        "EXPIRED_UNVERIFIED",
        Instant.parse("2026-07-29T18:00:00Z"));

    ArgumentCaptor<IdentityEventEntity> captor =
        ArgumentCaptor.forClass(IdentityEventEntity.class);
    org.mockito.Mockito.verify(repository).saveAndFlush(captor.capture());
    IdentityEventEntity event = captor.getValue();
    assertThat(event.getUser()).isNull();
    assertThat(event.getRegistration()).isNull();
    assertThat(event.getPreviousStatus()).isNull();
    assertThat(event.getNewStatus()).isNull();
    assertThat(reference.correlationId()).isEqualTo(correlation);
  }

  @Test
  void record_shouldRequireStatusesOnlyForTransitionEvents() {
    IdentityEventRepository repository = mock(IdentityEventRepository.class);
    IdentityAuditService service = new IdentityAuditService(repository);

    assertThatThrownBy(() -> service.record(
        null,
        null,
        UUID.randomUUID(),
        IdentityEventTypeEnum.USER_STATUS_CHANGED,
        null,
        "ACTIVE",
        IdentityTransitionOriginEnum.SYSTEM,
        null,
        Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.record(
        null,
        null,
        UUID.randomUUID(),
        IdentityEventTypeEnum.REGISTRATION_STARTED,
        "PENDING",
        "ACTIVE",
        IdentityTransitionOriginEnum.SYSTEM,
        null,
        Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repository);
  }

  @Test
  void record_shouldRejectFreeTextOrSensitiveReason() {
    IdentityEventRepository repository = mock(IdentityEventRepository.class);
    IdentityAuditService service = new IdentityAuditService(repository);

    assertThatThrownBy(() -> service.recordCancellationTombstone(
        UUID.randomUUID(),
        IdentityTransitionOriginEnum.SYSTEM,
        "user@example.com",
        Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.recordCancellationTombstone(
        UUID.randomUUID(),
        IdentityTransitionOriginEnum.SYSTEM,
        "192.0.2.10",
        Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repository);
  }
}
