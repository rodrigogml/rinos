package br.com.rinos.app.backend.module.storage.entity;

import java.time.Instant;

import br.com.rinos.app.backend.module.storage.enums.StorageOperationStepType;
import br.com.rinos.app.backend.module.storage.enums.StorageTransitionOriginType;
import br.com.rinos.app.backend.module.storage.enums.TenantStorageState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Histórico append-only das mudanças de estado do armazenamento de um tenant. */
@Entity
@Table(name = "storage_stateTransition")
public class StorageStateTransitionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "idStorageStateTransition", nullable = false)
  private Long id;

  @Column(name = "idTenantStorageRegistry", nullable = false, updatable = false)
  private Long tenantStorageRegistryId;

  @Column(name = "idStorageOperation", updatable = false)
  private Long storageOperationId;

  @Enumerated(EnumType.STRING)
  @Column(name = "previousState", length = 24, updatable = false)
  private TenantStorageState previousState;

  @Enumerated(EnumType.STRING)
  @Column(name = "resultingState", nullable = false, length = 24, updatable = false)
  private TenantStorageState resultingState;

  @Enumerated(EnumType.STRING)
  @Column(name = "stepType", length = 32, updatable = false)
  private StorageOperationStepType stepType;

  @Enumerated(EnumType.STRING)
  @Column(name = "originType", nullable = false, length = 24, updatable = false)
  private StorageTransitionOriginType originType;

  @Column(name = "actorUserId", updatable = false)
  private Long actorUserId;

  @Column(name = "systemOrigin", length = 100, updatable = false)
  private String systemOrigin;

  @Column(name = "correlationId", nullable = false, length = 100, updatable = false)
  private String correlationId;

  @Column(name = "safeResultCode", nullable = false, length = 100, updatable = false)
  private String safeResultCode;

  @Column(name = "occurredAt", nullable = false, updatable = false)
  private Instant occurredAt;

  protected StorageStateTransitionEntity() {
  }

  public StorageStateTransitionEntity(Long tenantStorageRegistryId, Long storageOperationId,
      TenantStorageState previousState, TenantStorageState resultingState,
      StorageOperationStepType stepType, StorageTransitionOriginType originType,
      Long actorUserId, String systemOrigin, String correlationId, String safeResultCode,
      Instant occurredAt) {
    this.tenantStorageRegistryId = tenantStorageRegistryId;
    this.storageOperationId = storageOperationId;
    this.previousState = previousState;
    this.resultingState = resultingState;
    this.stepType = stepType;
    this.originType = originType;
    this.actorUserId = actorUserId;
    this.systemOrigin = systemOrigin;
    this.correlationId = correlationId;
    this.safeResultCode = safeResultCode;
    this.occurredAt = occurredAt;
  }

  public Long getId() { return id; }
  public Long getTenantStorageRegistryId() { return tenantStorageRegistryId; }
  public Long getStorageOperationId() { return storageOperationId; }
  public TenantStorageState getPreviousState() { return previousState; }
  public TenantStorageState getResultingState() { return resultingState; }
  public StorageOperationStepType getStepType() { return stepType; }
  public StorageTransitionOriginType getOriginType() { return originType; }
  public Long getActorUserId() { return actorUserId; }
  public String getSystemOrigin() { return systemOrigin; }
  public String getCorrelationId() { return correlationId; }
  public String getSafeResultCode() { return safeResultCode; }
  public Instant getOccurredAt() { return occurredAt; }
}
