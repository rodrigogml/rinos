package br.com.rinos.app.backend.module.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.enums.AuthorizationExplanationMode;
import br.com.rinos.app.api.module.access.keys.AccessControlAccessKeys;
import br.com.rinos.app.api.module.access.vo.AuthenticationAssurance;
import br.com.rinos.app.api.module.access.vo.AuthorizationActor;
import br.com.rinos.app.api.module.access.vo.AuthorizationContext;
import br.com.rinos.app.api.module.access.vo.AuthorizationDecision;
import br.com.rinos.app.api.module.access.vo.AuthorizationGateResult;
import br.com.rinos.app.api.module.access.vo.AuthorizationKeyResult;
import br.com.rinos.app.backend.module.access.entity.AccessAuditEventEntity;
import br.com.rinos.app.backend.module.access.repository.AccessAuditEventRepository;

class AccessDecisionAuditServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");

  @Test
  void recordDeniedIfSensitive_shouldPersistOnlyMinimizedSafeData() {
    AccessAuditEventRepository repository = mock(AccessAuditEventRepository.class);
    AccessDecisionAuditService service = new AccessDecisionAuditService(repository);
    AuthorizationRequest request = request(true);
    AuthorizationDecision decision = deniedDecision();

    service.recordDeniedIfSensitive(request, decision);

    ArgumentCaptor<AccessAuditEventEntity> captor =
        ArgumentCaptor.forClass(AccessAuditEventEntity.class);
    verify(repository).save(captor.capture());
    AccessAuditEventEntity event = captor.getValue();
    assertThat(event.getEventType()).isEqualTo("SENSITIVE_AUTHORIZATION_DENIED");
    assertThat(event.getActorUserId()).isEqualTo(11L);
    assertThat(event.getCorrelationId()).isEqualTo(decision.correlationId().toString());
    assertThat(event.getSafeReasonCode()).isEqualTo("ACL_ASSURANCE_REQUIRED");
    assertThat(event.getDetails()).contains("target.operation", "requiredKeyCount")
        .doesNotContain("PASSKEY", "rule:", "membership");
  }

  @Test
  void recordDeniedIfSensitive_shouldIgnoreNonSensitiveOrAllowedDecision() {
    AccessAuditEventRepository repository = mock(AccessAuditEventRepository.class);
    AccessDecisionAuditService service = new AccessDecisionAuditService(repository);

    service.recordDeniedIfSensitive(request(false), deniedDecision());

    verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  private static AuthorizationRequest request(boolean sensitive) {
    return new AuthorizationRequest(
        AuthorizationActor.human(11L), null, AuthorizationContext.global(), "target.operation",
        Set.of(AccessControlAccessKeys.GLOBAL_RULE_VIEW), new AuthenticationAssurance(
            AuthenticationAssuranceEnum.MULTI_FACTOR, Set.of(AuthenticationMethodEnum.PASSKEY),
            NOW, NOW), sensitive, AuthorizationExplanationMode.NONE);
  }

  private static AuthorizationDecision deniedDecision() {
    AuthorizationKeyResult key = new AuthorizationKeyResult(
        AccessControlAccessKeys.GLOBAL_RULE_VIEW, false, List.of(), List.of(), true, List.of());
    return new AuthorizationDecision(
        false, AuthorizationContext.global().withRevision(3L), List.of(key),
        List.of(new AuthorizationGateResult("IDENTITY", true, null)),
        List.of(new AuthorizationGateResult("PLAN", true, null)),
        List.of(new AuthorizationGateResult(
            "ASSURANCE", false, "ACL_ASSURANCE_REQUIRED")),
        Set.of("ACL_ASSURANCE_REQUIRED", "ACL_KEY_MISSING"), 3L, NOW, UUID.randomUUID());
  }
}
