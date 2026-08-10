package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.PasskeyRiskReasonEnum;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;

@DisplayName("Auditoria isolada de risco de passkey")
class PasskeyRiskAuditServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-10T21:00:00Z");

  @Test
  void record_shouldPersistOnlyCataloguedReason_forExistingOwner() {
    UserRepository users = mock(UserRepository.class);
    IdentityAuditService audit = mock(IdentityAuditService.class);
    UserEntity user = mock(UserEntity.class);
    when(users.findById(41L)).thenReturn(Optional.of(user));
    PasskeyRiskAuditService service = new PasskeyRiskAuditService(users, audit);
    UUID correlationId = UUID.randomUUID();

    service.record(
        41L,
        PasskeyRiskReasonEnum.SIGNATURE_COUNTER_REGRESSION,
        correlationId,
        NOW);

    verify(audit).record(
        user,
        null,
        correlationId,
        IdentityEventTypeEnum.PASSKEY_RISK_DETECTED,
        null,
        null,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        "SIGNATURE_COUNTER_REGRESSION",
        NOW);
  }

  @Test
  void record_shouldFailClosed_whenOwnerNoLongerExists() {
    UserRepository users = mock(UserRepository.class);
    when(users.findById(41L)).thenReturn(Optional.empty());
    PasskeyRiskAuditService service = new PasskeyRiskAuditService(
        users, mock(IdentityAuditService.class));

    assertThatThrownBy(() -> service.record(
        41L,
        PasskeyRiskReasonEnum.CREDENTIAL_NOT_USABLE,
        UUID.randomUUID(),
        NOW))
        .isInstanceOf(EntityNotFoundException.class);
  }
}
