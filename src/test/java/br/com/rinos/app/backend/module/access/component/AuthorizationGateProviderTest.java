package br.com.rinos.app.backend.module.access.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.access.enums.AuthorizationExplanationMode;
import br.com.rinos.app.api.module.access.keys.InitialModuleAccessKeys;
import br.com.rinos.app.api.module.access.keys.AccessControlAccessKeys;
import br.com.rinos.app.api.module.access.vo.AccessKeyDescriptor;
import br.com.rinos.app.api.module.access.vo.AuthenticationAssurance;
import br.com.rinos.app.api.module.access.vo.AuthorizationActor;
import br.com.rinos.app.api.module.access.vo.AuthorizationContext;
import br.com.rinos.app.api.module.plans.enums.ContractScope;
import br.com.rinos.app.api.module.plans.vo.EntitlementRequirement;
import br.com.rinos.app.api.module.plans.vo.PersonalEntitlementSubject;
import br.com.rinos.app.api.module.plans.vo.TenantEntitlementSubject;
import br.com.rinos.app.backend.module.access.service.AccountMembershipAccessPort;
import br.com.rinos.app.backend.module.access.service.AccountMembershipAccessSnapshot;
import br.com.rinos.app.backend.module.access.service.PlanEntitlementAccessPort;
import br.com.rinos.app.backend.module.access.service.PlanEntitlementAccessSnapshot;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;

class AuthorizationGateProviderTest {

  @Test
  void structural_shouldRequireSameActiveIdentityMembershipAndOperationalTenant() {
    UserRepository users = mock(UserRepository.class);
    AccountMembershipAccessPort memberships = mock(AccountMembershipAccessPort.class);
    UserEntity user = new UserEntity("user@example.com", "user@example.com", UserStatusEnum.ACTIVE);
    when(users.findById(11L)).thenReturn(Optional.of(user));
    when(memberships.inspect(77L)).thenReturn(
        AccountMembershipAccessSnapshot.found(11L, 42L, true, true));
    DefaultAuthorizationStructuralGateProvider provider =
        new DefaultAuthorizationStructuralGateProvider(
            users, memberships, mock(br.com.rinos.app.backend.module.access.service.SystemOperationAuthorizer.class));

    assertThat(provider.evaluate(tenantRequest(InitialModuleAccessKeys.TENANT_MEMBERSHIP_VIEW)))
        .allMatch(gate -> gate.allowed());
  }

  @Test
  void structural_shouldDenyMembershipThatBelongsToAnotherIdentity() {
    UserRepository users = mock(UserRepository.class);
    AccountMembershipAccessPort memberships = mock(AccountMembershipAccessPort.class);
    UserEntity user = new UserEntity("user@example.com", "user@example.com", UserStatusEnum.ACTIVE);
    when(users.findById(11L)).thenReturn(Optional.of(user));
    when(memberships.inspect(77L)).thenReturn(
        AccountMembershipAccessSnapshot.found(12L, 42L, true, true));
    DefaultAuthorizationStructuralGateProvider provider =
        new DefaultAuthorizationStructuralGateProvider(
            users, memberships, mock(br.com.rinos.app.backend.module.access.service.SystemOperationAuthorizer.class));

    assertThat(provider.evaluate(tenantRequest(InitialModuleAccessKeys.TENANT_MEMBERSHIP_VIEW)))
        .anyMatch(gate -> !gate.allowed()
            && "ACL_ASSOCIATION_INACTIVE".equals(gate.safeReasonCode()));
  }

  @Test
  void entitlement_shouldKeepMissingRightDistinctFromUnavailableProvider() {
    PlanEntitlementAccessPort plans = mock(PlanEntitlementAccessPort.class);
    EntitlementRequirement requirement = new EntitlementRequirement(
        ContractScope.TENANT, "membership.advanced.features");
    AccessKeyDescriptor key = withEntitlement(
        InitialModuleAccessKeys.TENANT_MEMBERSHIP_VIEW, requirement);
    when(plans.inspect(new TenantEntitlementSubject(42L), Set.of(requirement))).thenReturn(
        PlanEntitlementAccessSnapshot.available(Set.of(requirement)));
    DefaultAuthorizationEntitlementGateProvider provider =
        new DefaultAuthorizationEntitlementGateProvider(plans);

    assertThat(provider.evaluate(tenantRequest(key))).singleElement()
        .satisfies(gate -> {
          assertThat(gate.allowed()).isFalse();
          assertThat(gate.safeReasonCode()).isEqualTo("ACL_PLAN_REQUIRED");
        });

    when(plans.inspect(new TenantEntitlementSubject(42L), Set.of(requirement))).thenReturn(
        PlanEntitlementAccessSnapshot.unavailable());
    assertThat(provider.evaluate(tenantRequest(key))).singleElement()
        .extracting(gate -> gate.safeReasonCode()).isEqualTo("ACL_PLAN_UNAVAILABLE");
  }

  @Test
  void entitlement_shouldNotInferPersonalContractForGlobalAdministration() {
    PlanEntitlementAccessPort plans = mock(PlanEntitlementAccessPort.class);
    DefaultAuthorizationEntitlementGateProvider provider =
        new DefaultAuthorizationEntitlementGateProvider(plans);

    assertThat(provider.evaluate(globalRequest(AccessControlAccessKeys.GLOBAL_CATALOG_VIEW)))
        .singleElement().extracting(gate -> gate.allowed()).isEqualTo(true);
    verifyNoInteractions(plans);
  }

  @Test
  void entitlement_shouldResolvePersonalSubjectOnlyWhenExplicitlyRequired() {
    PlanEntitlementAccessPort plans = mock(PlanEntitlementAccessPort.class);
    EntitlementRequirement requirement = new EntitlementRequirement(
        ContractScope.PERSONAL, "personal.files.storage");
    AccessKeyDescriptor key = withEntitlement(
        AccessControlAccessKeys.GLOBAL_CATALOG_VIEW, requirement);
    when(plans.inspect(new PersonalEntitlementSubject(11L), Set.of(requirement)))
        .thenReturn(PlanEntitlementAccessSnapshot.available(Set.of()));
    DefaultAuthorizationEntitlementGateProvider provider =
        new DefaultAuthorizationEntitlementGateProvider(plans);

    assertThat(provider.evaluate(globalRequest(key))).singleElement()
        .extracting(gate -> gate.allowed()).isEqualTo(true);
  }

  @Test
  void assurance_shouldDenySensitiveRequestWithoutRecentTotpOrPasskey() {
    DefaultAuthorizationAssuranceGateProvider provider =
        new DefaultAuthorizationAssuranceGateProvider();
    AuthorizationRequest request = new AuthorizationRequest(
        AuthorizationActor.human(11L), 77L, AuthorizationContext.tenant(42L), "tenant.test",
        Set.of(InitialModuleAccessKeys.TENANT_MEMBERSHIP_VIEW),
        new AuthenticationAssurance(
            AuthenticationAssuranceEnum.SINGLE_FACTOR,
            Set.of(AuthenticationMethodEnum.PASSWORD), Instant.now(), Instant.now()),
        true, AuthorizationExplanationMode.SAFE);

    assertThat(provider.evaluate(request)).singleElement()
        .satisfies(gate -> {
          assertThat(gate.allowed()).isFalse();
          assertThat(gate.safeReasonCode()).isEqualTo("ACL_ASSURANCE_REQUIRED");
        });
  }

  private static AuthorizationRequest tenantRequest(AccessKeyDescriptor key) {
    Instant now = Instant.now();
    return new AuthorizationRequest(
        AuthorizationActor.human(11L), 77L, AuthorizationContext.tenant(42L), "tenant.test",
        Set.of(key), new AuthenticationAssurance(
            AuthenticationAssuranceEnum.MULTI_FACTOR, Set.of(AuthenticationMethodEnum.PASSKEY),
            now.minusSeconds(30), now.minusSeconds(30)), false,
        AuthorizationExplanationMode.SAFE);
  }

  private static AuthorizationRequest globalRequest(AccessKeyDescriptor key) {
    Instant now = Instant.now();
    return new AuthorizationRequest(
        AuthorizationActor.human(11L), null, AuthorizationContext.global(), "global.test",
        Set.of(key), new AuthenticationAssurance(
            AuthenticationAssuranceEnum.MULTI_FACTOR, Set.of(AuthenticationMethodEnum.PASSKEY),
            now.minusSeconds(30), now.minusSeconds(30)), false,
        AuthorizationExplanationMode.SAFE);
  }

  private static AccessKeyDescriptor withEntitlement(
      AccessKeyDescriptor source, EntitlementRequirement entitlementRequirement) {
    return new AccessKeyDescriptor(
        source.code(), source.scope(), source.categoryCode(), source.ownerModule(),
        source.nameI18nKey(), source.descriptionI18nKey(), source.status(), entitlementRequirement,
        source.sourceRequirements(), source.minimumAdministrative());
  }
}
