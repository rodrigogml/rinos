package br.com.rinos.app.backend.module.storage.service;

import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.module.storage.vo.TenantStorageReconciliationSnapshotVO;
import br.com.rinos.app.backend.module.storage.entity.StorageAuditEventEntity;
import br.com.rinos.app.backend.module.storage.repository.StorageAuditEventRepository;

/**
 * Executa a reconciliação administrativa como leitura auditável, sem aplicar correções implícitas.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
@Service
public class TenantStorageReconciliationAdministrationService {

  private final TenantStorageReconciliationInspectionService inspectionService;
  private final StorageAuditEventRepository auditRepository;

  /**
   * Cria a fronteira administrativa de reconciliação.
   *
   * @param inspectionService detector somente de leitura das divergências físicas
   * @param auditRepository registro sanitizado obrigatório da execução administrativa
   */
  public TenantStorageReconciliationAdministrationService(
      TenantStorageReconciliationInspectionService inspectionService,
      StorageAuditEventRepository auditRepository) {
    this.inspectionService = Objects.requireNonNull(inspectionService,
        "inspectionService must not be null");
    this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository must not be null");
  }

  /**
   * Inspeciona a divergência atual e registra a ação autorizada no catálogo global.
   *
   * @param actorUserId identidade global do administrador autorizado
   * @param correlationId correlação segura da ação
   * @param occurredAt instante UTC em que a ação foi solicitada
   * @return fotografia sanitizada que não altera registro, operação ou schema
   */
  @Transactional
  public TenantStorageReconciliationSnapshotVO reconcile(
      long actorUserId, String correlationId, Instant occurredAt) {
    if (actorUserId <= 0) {
      throw new IllegalArgumentException("actorUserId must be positive");
    }
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    TenantStorageReconciliationSnapshotVO snapshot = inspectionService.inspect();
    auditRepository.save(new StorageAuditEventEntity(
        "TENANT_STORAGE_RECONCILIATION_INSPECTED", null, null, actorUserId, null,
        correlationId, "RECONCILIATION_READ_ONLY", null, occurredAt));
    return snapshot;
  }
}
