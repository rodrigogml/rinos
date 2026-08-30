package br.com.rinos.app.backend.module.storage.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Evento administrativo minimizado que não possui campos para URL, schema, SQL ou credenciais. */
@Entity
@Table(name = "storage_auditEvent")
public class StorageAuditEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "idStorageAuditEvent", nullable = false)
  private Long id;

  @Column(name = "eventType", nullable = false, length = 80, updatable = false)
  private String eventType;

  @Column(name = "idTenantStorageRegistry", updatable = false)
  private Long tenantStorageRegistryId;

  @Column(name = "idStorageOperation", updatable = false)
  private Long storageOperationId;

  @Column(name = "actorUserId", updatable = false)
  private Long actorUserId;

  @Column(name = "systemOrigin", length = 100, updatable = false)
  private String systemOrigin;

  @Column(name = "correlationId", nullable = false, length = 100, updatable = false)
  private String correlationId;

  @Column(name = "safeResultCode", nullable = false, length = 100, updatable = false)
  private String safeResultCode;

  @Column(name = "details", columnDefinition = "JSON", updatable = false)
  private String details;

  @Column(name = "occurredAt", nullable = false, updatable = false)
  private Instant occurredAt;

  protected StorageAuditEventEntity() {
  }

  public StorageAuditEventEntity(String eventType, Long tenantStorageRegistryId,
      Long storageOperationId, Long actorUserId, String systemOrigin, String correlationId,
      String safeResultCode, String details, Instant occurredAt) {
    this.eventType = eventType;
    this.tenantStorageRegistryId = tenantStorageRegistryId;
    this.storageOperationId = storageOperationId;
    this.actorUserId = actorUserId;
    this.systemOrigin = systemOrigin;
    this.correlationId = correlationId;
    this.safeResultCode = safeResultCode;
    this.details = details;
    this.occurredAt = occurredAt;
  }

  public Long getId() { return id; }
  public String getEventType() { return eventType; }
  public Long getTenantStorageRegistryId() { return tenantStorageRegistryId; }
  public Long getStorageOperationId() { return storageOperationId; }
  public Long getActorUserId() { return actorUserId; }
  public String getSystemOrigin() { return systemOrigin; }
  public String getCorrelationId() { return correlationId; }
  public String getSafeResultCode() { return safeResultCode; }
  public String getDetails() { return details; }
  public Instant getOccurredAt() { return occurredAt; }
}
