package br.com.rinos.app.api.module.access.keys;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.membership.enums.MembershipMutationOperation;

class TenantConsumerOperationsTest {

  @Test
  void foundationOperations_shouldUseOnlyCanonicalTenantKeys() {
    var invite = TenantFoundationOperations.inviteMembership();
    var manage = TenantFoundationOperations.manageMembership(MembershipMutationOperation.SUSPEND);

    assertThat(invite.requiredKeys()).containsExactly(
        InitialModuleAccessKeys.TENANT_MEMBERSHIP_INVITE);
    assertThat(invite.sensitive()).isFalse();
    assertThat(manage.requiredKeys()).containsExactly(
        InitialModuleAccessKeys.TENANT_MEMBERSHIP_MANAGE);
    assertThat(manage.sensitive()).isTrue();
    assertThat(java.util.stream.Stream.of(
            TenantFoundationOperations.viewAccount(),
            TenantFoundationOperations.updateAccount(),
            TenantFoundationOperations.manageAccountLifecycle(),
            TenantFoundationOperations.viewMemberships(), invite, manage,
            TenantFoundationOperations.viewPlan(), TenantFoundationOperations.viewAudit())
        .flatMap(operation -> operation.requiredKeys().stream()))
        .allMatch(key -> key.scope() == AccessScope.TENANT);
  }

  @Test
  void sensitivePartyDisclosure_shouldRequireBaseReadAndSpecificReveal() {
    assertThat(TenantPartyOperations.revealPartyIdentifier().requiredKeys())
        .containsExactlyInAnyOrder(
            InitialModuleAccessKeys.TENANT_PARTY_VIEW,
            InitialModuleAccessKeys.TENANT_PARTY_IDENTIFIER_REVEAL);
    assertThat(TenantPartyOperations.revealPaymentDetails().requiredKeys())
        .containsExactlyInAnyOrder(
            InitialModuleAccessKeys.TENANT_PARTY_PAYMENT_VIEW,
            InitialModuleAccessKeys.TENANT_PARTY_PAYMENT_REVEAL);
    assertThat(TenantPartyOperations.revealPaymentDetails().sensitive()).isTrue();
  }

  @Test
  void partyMutationOperations_shouldNeverUseGlobalOrFoundationKeys() {
    var operations = java.util.List.of(
        TenantPartyOperations.viewParty(), TenantPartyOperations.createParty(),
        TenantPartyOperations.updateParty(), TenantPartyOperations.deactivateParty(),
        TenantPartyOperations.reactivateParty(), TenantPartyOperations.viewRelationships(),
        TenantPartyOperations.assignRelationship(), TenantPartyOperations.updateRelationship(),
        TenantPartyOperations.endRelationship(), TenantPartyOperations.cancelRelationship(),
        TenantPartyOperations.viewMaskedPaymentDetails(),
        TenantPartyOperations.createPaymentDetails(), TenantPartyOperations.updatePaymentDetails(),
        TenantPartyOperations.verifyPaymentDetails(), TenantPartyOperations.preferPaymentDetails(),
        TenantPartyOperations.deactivatePaymentDetails(), TenantPartyOperations.deletePaymentDetails());

    assertThat(operations).allSatisfy(operation -> assertThat(operation.requiredKeys())
        .allMatch(key -> key.scope() == AccessScope.TENANT
            && key.code().startsWith("tenant.party.")));
  }
}
