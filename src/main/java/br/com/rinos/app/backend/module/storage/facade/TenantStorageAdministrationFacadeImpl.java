package br.com.rinos.app.backend.module.storage.facade;

import java.util.Objects;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.enums.AuthorizationExplanationMode;
import br.com.rinos.app.api.module.access.facade.AuthorizationFacade;
import br.com.rinos.app.api.module.access.keys.TenantStorageProvisioningOperations;
import br.com.rinos.app.api.module.access.vo.AuthorizationOperation;
import br.com.rinos.app.api.module.storage.dto.TenantStorageAdministrationInvocationContext;
import br.com.rinos.app.api.module.storage.facade.TenantStorageAdministrationFacade;
import br.com.rinos.app.api.module.storage.vo.TenantStorageDeactivationResultVO;
import br.com.rinos.app.api.module.storage.vo.TenantStorageReconciliationSnapshotVO;
import br.com.rinos.app.backend.module.storage.service.TenantStorageDeactivationService;
import br.com.rinos.app.backend.module.storage.service.TenantStorageReconciliationAdministrationService;

/**
 * Aplica autorização global canônica antes de cada operação administrativa de armazenamento.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
@Service
@Lazy
public class TenantStorageAdministrationFacadeImpl implements TenantStorageAdministrationFacade {

  private final AuthorizationFacade authorization;
  private final TenantStorageReconciliationAdministrationService reconciliationService;
  private final TenantStorageDeactivationService deactivationService;

  /**
   * Cria a fachada que separa autorização contextual das regras persistentes de armazenamento.
   *
   * @param authorization fronteira canônica de regras, bloqueios e garantia forte
   * @param reconciliationService inspeção e auditoria da reconciliação
   * @param deactivationService transição lógica idempotente de desativação
   */
  public TenantStorageAdministrationFacadeImpl(AuthorizationFacade authorization,
      TenantStorageReconciliationAdministrationService reconciliationService,
      TenantStorageDeactivationService deactivationService) {
    this.authorization = Objects.requireNonNull(authorization, "authorization must not be null");
    this.reconciliationService = Objects.requireNonNull(reconciliationService,
        "reconciliationService must not be null");
    this.deactivationService = Objects.requireNonNull(deactivationService,
        "deactivationService must not be null");
  }

  /** {@inheritDoc} */
  @Override
  public TenantStorageReconciliationSnapshotVO reconcile(
      TenantStorageAdministrationInvocationContext context) {
    require(context, TenantStorageProvisioningOperations.reconcile());
    return reconciliationService.reconcile(context.authorization().actor().identityId(),
        context.correlationId(), context.occurredAt());
  }

  /** {@inheritDoc} */
  @Override
  public TenantStorageDeactivationResultVO requestDeactivation(
      TenantStorageAdministrationInvocationContext context, UUID tenantPublicId) {
    Objects.requireNonNull(tenantPublicId, "tenantPublicId must not be null");
    require(context, TenantStorageProvisioningOperations.requestDeactivation());
    return deactivationService.requestDeactivation(tenantPublicId,
        context.authorization().actor().identityId(), context.correlationId(), context.occurredAt());
  }

  private void require(TenantStorageAdministrationInvocationContext context,
      AuthorizationOperation operation) {
    Objects.requireNonNull(context, "context must not be null");
    authorization.require(new AuthorizationRequest(
        context.authorization().actor(), context.authorization().membershipId(),
        context.authorization().context(), operation.code(), operation.requiredKeys(),
        context.authorization().assurance(), operation.sensitive(), AuthorizationExplanationMode.NONE));
  }
}
