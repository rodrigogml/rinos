package br.com.rinos.app.api.module.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.module.access.dto.AccessExplanationRequest;
import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.enums.AuthorizationExplanationMode;
import br.com.rinos.app.api.module.access.keys.AccessControlAccessKeys;
import br.com.rinos.app.api.module.access.vo.AuthenticationAssurance;
import br.com.rinos.app.api.module.access.vo.AuthorizationActor;
import br.com.rinos.app.api.module.access.vo.AuthorizationContext;

class AccessExplanationRequestTest {

  private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");
  private static final AuthenticationAssurance ASSURANCE = new AuthenticationAssurance(
      AuthenticationAssuranceEnum.MULTI_FACTOR, Set.of(AuthenticationMethodEnum.PASSKEY),
      NOW, NOW);

  @Test
  void request_shouldSeparateRequesterFromTargetAndRedactIdentifiers() {
    AuthorizationRequest target = tenantTarget();

    AccessExplanationRequest request = new AccessExplanationRequest(
        AuthorizationActor.human(10L), 20L, ASSURANCE, target);

    assertThat(request.targetRequest()).isSameAs(target);
    assertThat(request.toString()).doesNotContain("identityId=10", "20L");
  }

  @Test
  void request_shouldRejectSystemRequesterAndMembershipIncompatibleWithScope() {
    assertThatThrownBy(() -> new AccessExplanationRequest(
        AuthorizationActor.system("test"), 20L, ASSURANCE, tenantTarget()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AccessExplanationRequest(
        AuthorizationActor.human(10L), null, ASSURANCE, tenantTarget()))
        .isInstanceOf(IllegalArgumentException.class);

    AuthorizationRequest globalTarget = new AuthorizationRequest(
        AuthorizationActor.human(30L), null, AuthorizationContext.global(), "target.operation",
        Set.of(AccessControlAccessKeys.GLOBAL_RULE_VIEW), ASSURANCE, false,
        AuthorizationExplanationMode.ADMINISTRATIVE);
    assertThatThrownBy(() -> new AccessExplanationRequest(
        AuthorizationActor.human(10L), 20L, ASSURANCE, globalTarget))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static AuthorizationRequest tenantTarget() {
    return new AuthorizationRequest(
        AuthorizationActor.human(30L), 40L, AuthorizationContext.tenant(50L),
        "target.operation", Set.of(AccessControlAccessKeys.TENANT_RULE_VIEW), ASSURANCE, false,
        AuthorizationExplanationMode.ADMINISTRATIVE);
  }
}
