package br.com.rinos.app.api.module.access.keys;

import java.util.Set;

import br.com.rinos.app.api.module.access.vo.AuthorizationOperation;

/** Operações globais fechadas do provisionamento, independentes de papel de ator. */
public final class TenantStorageProvisioningOperations {

  private TenantStorageProvisioningOperations() {
  }

  /** Retorna a operação sensível de inspeção administrativa de divergências físicas. */
  public static AuthorizationOperation reconcile() {
    return operation("global.platform.provisioning.reconcile");
  }

  /** Retorna a operação sensível que impede novo uso antes da governança de retenção. */
  public static AuthorizationOperation requestDeactivation() {
    return operation("global.platform.provisioning.deactivate");
  }

  private static AuthorizationOperation operation(String code) {
    return new AuthorizationOperation(code, Set.of(InitialModuleAccessKeys.GLOBAL_PLATFORM_PROVISIONING_MANAGE),
        true);
  }
}
