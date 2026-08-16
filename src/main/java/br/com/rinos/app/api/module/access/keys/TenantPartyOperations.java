package br.com.rinos.app.api.module.access.keys;

import java.util.Set;

import br.com.rinos.app.api.module.access.vo.AccessKeyDescriptor;
import br.com.rinos.app.api.module.access.vo.AuthorizationOperation;

/** Operacoes tipadas de pessoas, relacionamentos e dados de pagamento do tenant. */
public final class TenantPartyOperations {

  private TenantPartyOperations() {
  }

  public static AuthorizationOperation viewParty() {
    return operation("tenant.party.view", false, InitialModuleAccessKeys.TENANT_PARTY_VIEW);
  }

  public static AuthorizationOperation createParty() {
    return operation("tenant.party.create", false, InitialModuleAccessKeys.TENANT_PARTY_CREATE);
  }

  public static AuthorizationOperation updateParty() {
    return operation("tenant.party.update", false, InitialModuleAccessKeys.TENANT_PARTY_UPDATE);
  }

  public static AuthorizationOperation deactivateParty() {
    return operation("tenant.party.deactivate", true,
        InitialModuleAccessKeys.TENANT_PARTY_DEACTIVATE);
  }

  public static AuthorizationOperation reactivateParty() {
    return operation("tenant.party.reactivate", true,
        InitialModuleAccessKeys.TENANT_PARTY_REACTIVATE);
  }

  /** Revelacao completa exige cumulativamente consulta e revelacao. */
  public static AuthorizationOperation revealPartyIdentifier() {
    return operation("tenant.party.identifier.reveal", true,
        InitialModuleAccessKeys.TENANT_PARTY_VIEW,
        InitialModuleAccessKeys.TENANT_PARTY_IDENTIFIER_REVEAL);
  }

  public static AuthorizationOperation viewRelationships() {
    return operation("tenant.party.relationship.view", false,
        InitialModuleAccessKeys.TENANT_PARTY_RELATIONSHIP_VIEW);
  }

  public static AuthorizationOperation assignRelationship() {
    return operation("tenant.party.relationship.assign", false,
        InitialModuleAccessKeys.TENANT_PARTY_RELATIONSHIP_ASSIGN);
  }

  public static AuthorizationOperation updateRelationship() {
    return operation("tenant.party.relationship.update", false,
        InitialModuleAccessKeys.TENANT_PARTY_RELATIONSHIP_UPDATE);
  }

  public static AuthorizationOperation endRelationship() {
    return operation("tenant.party.relationship.end", true,
        InitialModuleAccessKeys.TENANT_PARTY_RELATIONSHIP_END);
  }

  public static AuthorizationOperation cancelRelationship() {
    return operation("tenant.party.relationship.cancel", true,
        InitialModuleAccessKeys.TENANT_PARTY_RELATIONSHIP_CANCEL);
  }

  public static AuthorizationOperation viewMaskedPaymentDetails() {
    return operation("tenant.party.payment.view", false,
        InitialModuleAccessKeys.TENANT_PARTY_PAYMENT_VIEW);
  }

  /** Valor completo exige cumulativamente consulta mascarada e revelacao. */
  public static AuthorizationOperation revealPaymentDetails() {
    return operation("tenant.party.payment.reveal", true,
        InitialModuleAccessKeys.TENANT_PARTY_PAYMENT_VIEW,
        InitialModuleAccessKeys.TENANT_PARTY_PAYMENT_REVEAL);
  }

  public static AuthorizationOperation createPaymentDetails() {
    return operation("tenant.party.payment.create", true,
        InitialModuleAccessKeys.TENANT_PARTY_PAYMENT_CREATE);
  }

  public static AuthorizationOperation updatePaymentDetails() {
    return operation("tenant.party.payment.update", true,
        InitialModuleAccessKeys.TENANT_PARTY_PAYMENT_UPDATE);
  }

  public static AuthorizationOperation verifyPaymentDetails() {
    return operation("tenant.party.payment.verify", true,
        InitialModuleAccessKeys.TENANT_PARTY_PAYMENT_VERIFY);
  }

  public static AuthorizationOperation preferPaymentDetails() {
    return operation("tenant.party.payment.prefer", true,
        InitialModuleAccessKeys.TENANT_PARTY_PAYMENT_PREFER);
  }

  public static AuthorizationOperation deactivatePaymentDetails() {
    return operation("tenant.party.payment.deactivate", true,
        InitialModuleAccessKeys.TENANT_PARTY_PAYMENT_DEACTIVATE);
  }

  public static AuthorizationOperation deletePaymentDetails() {
    return operation("tenant.party.payment.delete", true,
        InitialModuleAccessKeys.TENANT_PARTY_PAYMENT_DELETE);
  }

  private static AuthorizationOperation operation(
      String code, boolean sensitive, AccessKeyDescriptor... keys) {
    return new AuthorizationOperation(code, Set.of(keys), sensitive);
  }
}
