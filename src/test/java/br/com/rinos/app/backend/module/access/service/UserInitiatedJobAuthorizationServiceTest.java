package br.com.rinos.app.backend.module.access.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.dto.UserInitiatedAuthorizationJob;
import br.com.rinos.app.api.module.access.facade.AuthorizationFacade;
import br.com.rinos.app.api.module.access.keys.AccessControlAccessKeys;
import br.com.rinos.app.api.module.access.vo.AuthenticationAssurance;
import br.com.rinos.app.api.module.access.vo.AuthorizationActor;
import br.com.rinos.app.api.module.access.vo.AuthorizationDecision;
import br.com.rinos.app.api.module.access.vo.AuthorizationOperation;
import br.com.rinos.app.api.module.access.vo.AuthorizationWorkspaceContext;

class UserInitiatedJobAuthorizationServiceTest {

  @Test
  void requireStart_shouldBuildFreshRequestEveryTimeAndObserveLaterRevocation() {
    AuthorizationFacade authorization = mock(AuthorizationFacade.class);
    AuthorizationDecision accepted = mock(AuthorizationDecision.class);
    when(authorization.require(org.mockito.ArgumentMatchers.any()))
        .thenReturn(accepted)
        .thenThrow(new IllegalStateException("authorization denied: ACL_KEY_BLOCKED"));
    UserInitiatedJobAuthorizationService service =
        new UserInitiatedJobAuthorizationService(authorization);
    UserInitiatedAuthorizationJob job = new UserInitiatedAuthorizationJob(
        AuthorizationActor.human(11L), AuthorizationWorkspaceContext.tenant(77L, 88L),
        new AuthorizationOperation(
            "tenant.import.start", Set.of(AccessControlAccessKeys.TENANT_RULE_VIEW), true),
        assurance());

    AtomicInteger effects = new AtomicInteger();
    service.execute(job, effects::incrementAndGet);
    assertThatThrownBy(() -> service.execute(job, effects::incrementAndGet))
        .hasMessageContaining("ACL_KEY_BLOCKED");
    org.assertj.core.api.Assertions.assertThat(effects).hasValue(1);

    ArgumentCaptor<AuthorizationRequest> captor =
        ArgumentCaptor.forClass(AuthorizationRequest.class);
    verify(authorization, times(2)).require(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getAllValues())
        .allSatisfy(request -> {
          org.assertj.core.api.Assertions.assertThat(request.context().tenantId()).isEqualTo(77L);
          org.assertj.core.api.Assertions.assertThat(request.membershipId()).isEqualTo(88L);
          org.assertj.core.api.Assertions.assertThat(request.requiredKeys())
              .containsExactly(AccessControlAccessKeys.TENANT_RULE_VIEW);
        });
  }

  private static AuthenticationAssurance assurance() {
    Instant authenticatedAt = Instant.parse("2026-08-16T11:00:00Z");
    return new AuthenticationAssurance(
        AuthenticationAssuranceEnum.MULTI_FACTOR,
        Set.of(AuthenticationMethodEnum.PASSWORD, AuthenticationMethodEnum.TOTP),
        authenticatedAt, authenticatedAt);
  }
}
