package br.com.rinos.app.api.module.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.module.access.vo.AuthenticationAssurance;
import br.com.rinos.app.api.module.access.vo.AuthorizationActor;
import br.com.rinos.app.api.module.access.vo.AuthorizationContext;
import br.com.rinos.app.api.module.membership.dto.MembershipInvitationRequest;
import br.com.rinos.app.api.module.membership.dto.MembershipInvocationContext;
import br.com.rinos.app.api.module.membership.dto.MembershipMutationRequest;
import br.com.rinos.app.api.module.membership.enums.MembershipMutationOperation;
import br.com.rinos.app.api.module.membership.enums.MembershipRoleType;
import br.com.rinos.app.api.module.membership.facade.MembershipFacade;

class MembershipPublicContractTest {

  @Test
  void contracts_shouldBeImmutableAndIndependentFromPersistence() {
    for (Class<?> contract : List.of(
        MembershipInvocationContext.class,
        MembershipInvitationRequest.class,
        MembershipMutationRequest.class)) {
      assertThat(contract.isRecord()).isTrue();
      assertThat(contract.getDeclaredFields()).extracting(Field::getGenericType)
          .allSatisfy(type -> assertThat(type.getTypeName())
              .doesNotContain("br.com.rinos.app.backend.", ".entity.", ".repository."));
      assertThat(contract.getRecordComponents()).extracting(RecordComponent::getGenericType)
          .allSatisfy(type -> assertThat(type.getTypeName())
              .doesNotContain("br.com.rinos.app.backend.", ".entity.", ".repository."));
    }
    assertThat(MembershipFacade.class.isInterface()).isTrue();
  }

  @Test
  void invocationContext_shouldRejectGlobalOrMissingMembershipAndRedactAuthentication() {
    Instant now = Instant.parse("2026-08-16T16:00:00Z");
    var assurance = new AuthenticationAssurance(
        AuthenticationAssuranceEnum.MULTI_FACTOR,
        Set.of(AuthenticationMethodEnum.PASSWORD, AuthenticationMethodEnum.TOTP),
        now.minusSeconds(60), now.minusSeconds(30));

    assertThatThrownBy(() -> new MembershipInvocationContext(
        AuthorizationActor.human(1L), 2L, AuthorizationContext.global(), assurance,
        "correlation", now)).isInstanceOf(IllegalArgumentException.class);
    var context = new MembershipInvocationContext(
        AuthorizationActor.human(1L), 2L, AuthorizationContext.tenant(3L), assurance,
        "correlation", now);
    assertThat(context.toString()).contains("REDACTED").doesNotContain("identityId=1");
  }

  @Test
  void browserRequests_shouldNotCarryActorTenantKeysOrAssurance() {
    var invitation = new MembershipInvitationRequest(
        UUID.randomUUID(), "person@example.test", MembershipRoleType.ACCOUNT_ADMINISTRATOR);
    var mutation = new MembershipMutationRequest(
        UUID.randomUUID(), MembershipMutationOperation.SUSPEND, null, 0L, true);

    assertThat(invitation.getClass().getRecordComponents()).extracting(RecordComponent::getName)
        .doesNotContain("actor", "tenantId", "membershipId", "assurance", "accessKey");
    assertThat(mutation.getClass().getRecordComponents()).extracting(RecordComponent::getName)
        .doesNotContain("actor", "tenantId", "membershipId", "assurance", "accessKey");
  }
}
