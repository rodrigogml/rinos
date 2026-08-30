package br.com.rinos.app.backend.module.storage.service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.module.storage.enums.TenantStorageDeactivationStatusEnum;
import br.com.rinos.app.api.module.storage.vo.TenantStorageDeactivationResultVO;
import br.com.rinos.app.backend.module.account.entity.TenantEntity;
import br.com.rinos.app.backend.module.account.repository.TenantRepository;
import br.com.rinos.app.backend.module.storage.entity.StorageAuditEventEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageStateTransitionEntity;
import br.com.rinos.app.backend.module.storage.entity.TenantStorageRegistryEntity;
import br.com.rinos.app.backend.module.storage.enums.StorageTransitionOriginType;
import br.com.rinos.app.backend.module.storage.enums.TenantStorageState;
import br.com.rinos.app.backend.module.storage.repository.StorageAuditEventRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageStateTransitionRepository;
import br.com.rinos.app.backend.module.storage.repository.TenantStorageRegistryRepository;

/**
 * Solicita a desativação lógica, impedindo uso sem excluir dados nem reaproveitar a identidade física.
 *
 * <p>A liberação ou eliminação física depende da política de retenção e da execução externa previstas em
 * {@code tenant-data-governance}. Este serviço somente aplica a transição segura para {@code DEACTIVATING} e grava
 * sua evidência no catálogo global.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
@Service
public class TenantStorageDeactivationService {

  private final TenantRepository tenantRepository;
  private final TenantStorageRegistryRepository registryRepository;
  private final StorageStateTransitionRepository transitionRepository;
  private final StorageAuditEventRepository auditRepository;
  private final TenantStorageStateTransitionService transitions;

  /**
   * Cria o serviço que coordena a desativação somente dentro do catálogo global.
   *
   * @param tenantRepository resolução do identificador público do tenant
   * @param registryRepository registro físico interno protegido por lock
   * @param transitionRepository histórico append-only de estado
   * @param auditRepository auditoria sanitizada da solicitação
   * @param transitions validador da máquina de estados estrutural
   */
  public TenantStorageDeactivationService(TenantRepository tenantRepository,
      TenantStorageRegistryRepository registryRepository,
      StorageStateTransitionRepository transitionRepository,
      StorageAuditEventRepository auditRepository,
      TenantStorageStateTransitionService transitions) {
    this.tenantRepository = Objects.requireNonNull(tenantRepository, "tenantRepository must not be null");
    this.registryRepository = Objects.requireNonNull(registryRepository,
        "registryRepository must not be null");
    this.transitionRepository = Objects.requireNonNull(transitionRepository,
        "transitionRepository must not be null");
    this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository must not be null");
    this.transitions = Objects.requireNonNull(transitions, "transitions must not be null");
  }

  /**
   * Solicita a interrupção lógica idempotente de um tenant conhecido.
   *
   * @param tenantPublicId identificador público do tenant, obrigatório
   * @param actorUserId administrador global que executou a solicitação
   * @param correlationId correlação segura da ação
   * @param occurredAt instante UTC da solicitação
   * @return resultado que diferencia primeira solicitação, repetição e ausência segura do alvo
   */
  @Transactional
  public TenantStorageDeactivationResultVO requestDeactivation(UUID tenantPublicId, long actorUserId,
      String correlationId, Instant occurredAt) {
    Objects.requireNonNull(tenantPublicId, "tenantPublicId must not be null");
    if (actorUserId <= 0) {
      throw new IllegalArgumentException("actorUserId must be positive");
    }
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");

    Optional<TenantEntity> tenant = tenantRepository.findByPublicId(tenantPublicId);
    if (tenant.isEmpty()) {
      return audited(null, actorUserId, correlationId, occurredAt,
          TenantStorageDeactivationStatusEnum.TENANT_NOT_FOUND, "TENANT_STORAGE_NOT_READY");
    }
    Optional<TenantStorageRegistryEntity> registry = registryRepository.findByTenantIdForUpdate(
        tenant.get().getId());
    if (registry.isEmpty()) {
      return audited(null, actorUserId, correlationId, occurredAt,
          TenantStorageDeactivationStatusEnum.STORAGE_NOT_REGISTERED, "TENANT_STORAGE_NOT_READY");
    }

    TenantStorageRegistryEntity current = registry.get();
    if (current.getStorageState() == TenantStorageState.INACTIVE) {
      return audited(current.getId(), actorUserId, correlationId, occurredAt,
          TenantStorageDeactivationStatusEnum.ALREADY_INACTIVE, "TENANT_STORAGE_INACTIVE");
    }
    if (current.getStorageState() == TenantStorageState.DEACTIVATING) {
      return audited(current.getId(), actorUserId, correlationId, occurredAt,
          TenantStorageDeactivationStatusEnum.ALREADY_DEACTIVATING,
          "TENANT_STORAGE_DEACTIVATION_PENDING_GOVERNANCE");
    }

    TenantStorageState previous = current.getStorageState();
    transitions.transition(previous, TenantStorageState.DEACTIVATING, StorageTransitionOriginType.GLOBAL_USER);
    current.changeState(TenantStorageState.DEACTIVATING);
    registryRepository.save(current);
    transitionRepository.save(new StorageStateTransitionEntity(current.getId(), null, previous,
        TenantStorageState.DEACTIVATING, null, StorageTransitionOriginType.GLOBAL_USER, actorUserId, null,
        correlationId, "TENANT_STORAGE_DEACTIVATION_REQUESTED", occurredAt));
    return audited(current.getId(), actorUserId, correlationId, occurredAt,
        TenantStorageDeactivationStatusEnum.DEACTIVATION_REQUESTED,
        "TENANT_STORAGE_DEACTIVATION_PENDING_GOVERNANCE");
  }

  private TenantStorageDeactivationResultVO audited(Long registryId, long actorUserId,
      String correlationId, Instant occurredAt, TenantStorageDeactivationStatusEnum status,
      String safeReasonCode) {
    auditRepository.save(new StorageAuditEventEntity("TENANT_STORAGE_DEACTIVATION_REQUESTED",
        registryId, null, actorUserId, null, correlationId, safeReasonCode, null, occurredAt));
    return new TenantStorageDeactivationResultVO(status, safeReasonCode, occurredAt);
  }
}
