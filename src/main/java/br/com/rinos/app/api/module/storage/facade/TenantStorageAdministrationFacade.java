package br.com.rinos.app.api.module.storage.facade;

import java.util.UUID;

import br.com.rinos.app.api.module.storage.dto.TenantStorageAdministrationInvocationContext;
import br.com.rinos.app.api.module.storage.vo.TenantStorageDeactivationResultVO;
import br.com.rinos.app.api.module.storage.vo.TenantStorageReconciliationSnapshotVO;

/**
 * Fronteira interna para operações administrativas globais do armazenamento de tenants.
 *
 * <p>Cada método exige autorização canônica, contexto global e garantia forte quando for sensível. Não há operações
 * de backup, restauração, migration manual ou acesso a detalhes físicos neste contrato.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
public interface TenantStorageAdministrationFacade {

  /**
   * Executa a inspeção autorizada de reconciliação e registra a consulta administrativa minimizada.
   *
   * @param context contexto confiável da sessão administrativa global
   * @return fotografia diagnóstica sanitizada, sem mudança de estado ou correção automática
   */
  TenantStorageReconciliationSnapshotVO reconcile(
      TenantStorageAdministrationInvocationContext context);

  /**
   * Solicita a desativação lógica e idempotente, sem remover dados ou schema.
   *
   * @param context contexto confiável da sessão administrativa global
   * @param tenantPublicId referência pública do tenant a desativar
   * @return resultado seguro da solicitação ou de sua repetição
   */
  TenantStorageDeactivationResultVO requestDeactivation(
      TenantStorageAdministrationInvocationContext context, UUID tenantPublicId);
}
