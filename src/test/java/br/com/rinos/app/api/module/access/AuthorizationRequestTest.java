package br.com.rinos.app.api.module.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.enums.AuthorizationExplanationMode;
import br.com.rinos.app.api.module.access.keys.AccessControlAccessKeys;
import br.com.rinos.app.api.module.access.vo.AuthenticationAssurance;
import br.com.rinos.app.api.module.access.vo.AuthorizationActor;
import br.com.rinos.app.api.module.access.vo.AuthorizationContext;

class AuthorizationRequestTest {

  private static final Instant AUTHENTICATED_AT = Instant.parse("2026-08-15T12:00:00Z");
  private static final AuthenticationAssurance ASSURANCE = new AuthenticationAssurance(
      AuthenticationAssuranceEnum.MULTI_FACTOR,
      Set.of(AuthenticationMethodEnum.PASSWORD, AuthenticationMethodEnum.TOTP),
      AUTHENTICATED_AT,
      AUTHENTICATED_AT);

  @Test
  void humanTenantRequest_shouldRequireMembershipAndCopyKeys() {
    Set<br.com.rinos.app.api.module.access.vo.AccessKeyDescriptor> keys =
        new java.util.HashSet<>(Set.of(AccessControlAccessKeys.TENANT_CATALOG_VIEW));

    AuthorizationRequest request = new AuthorizationRequest(
        AuthorizationActor.human(10L),
        20L,
        AuthorizationContext.tenant(30L),
        "access.catalog.view",
        keys,
        ASSURANCE,
        false,
        AuthorizationExplanationMode.SAFE);
    keys.clear();

    assertThat(request.requiredKeys()).containsExactly(AccessControlAccessKeys.TENANT_CATALOG_VIEW);
    assertThat(request.toString()).doesNotContain("identityId=10", "membershipId=20");
  }

  @Test
  void request_shouldRejectCrossScopeEmptyKeysAndInvalidActorShape() {
    assertThatThrownBy(() -> new AuthorizationRequest(
        AuthorizationActor.human(10L),
        20L,
        AuthorizationContext.tenant(30L),
        "access.catalog.view",
        Set.of(AccessControlAccessKeys.GLOBAL_CATALOG_VIEW),
        ASSURANCE,
        false,
        AuthorizationExplanationMode.NONE)).isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new AuthorizationRequest(
        AuthorizationActor.human(10L),
        null,
        AuthorizationContext.tenant(30L),
        "access.catalog.view",
        Set.of(AccessControlAccessKeys.TENANT_CATALOG_VIEW),
        ASSURANCE,
        false,
        AuthorizationExplanationMode.NONE)).isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new AuthorizationRequest(
        AuthorizationActor.system("maintenance.cleanup"),
        null,
        AuthorizationContext.global(),
        "maintenance.cleanup",
        Set.of(),
        null,
        true,
        AuthorizationExplanationMode.NONE)).isInstanceOf(IllegalArgumentException.class);
  }
}
