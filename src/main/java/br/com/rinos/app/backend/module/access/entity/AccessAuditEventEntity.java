package br.com.rinos.app.backend.module.access.entity;

import java.time.Instant;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Evento administrativo minimizado e imutável. */
@Entity
@Table(name = "access_auditEvent")
public class AccessAuditEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "idAccessAuditEvent", nullable = false)
  private Long id;

  @Column(name = "eventType", nullable = false, length = 80, updatable = false)
  private String eventType;

  @Enumerated(EnumType.STRING)
  @Column(name = "scopeType", nullable = false, length = 16, updatable = false)
  private AccessScope scope;

  @Column(name = "idTenant", updatable = false)
  private Long tenantId;

  @Column(name = "actorUserId", updatable = false)
  private Long actorUserId;

  @Column(name = "systemOrigin", length = 100, updatable = false)
  private String systemOrigin;

  @Column(name = "targetType", nullable = false, length = 80, updatable = false)
  private String targetType;

  @Column(name = "targetId", nullable = false, updatable = false)
  private Long targetId;

  @Column(name = "correlationId", nullable = false, length = 100, updatable = false)
  private String correlationId;

  @Column(name = "safeReasonCode", length = 100, updatable = false)
  private String safeReasonCode;

  @Column(name = "details", columnDefinition = "JSON", updatable = false)
  private String details;

  @Column(name = "occurredAt", nullable = false, updatable = false)
  private Instant occurredAt;

  protected AccessAuditEventEntity() {
  }

  public AccessAuditEventEntity(String eventType, AccessScope scope, Long tenantId,
      Long actorUserId, String systemOrigin, String targetType, Long targetId,
      String correlationId, String safeReasonCode, String details, Instant occurredAt) {
    this.eventType = eventType;
    this.scope = scope;
    this.tenantId = tenantId;
    this.actorUserId = actorUserId;
    this.systemOrigin = systemOrigin;
    this.targetType = targetType;
    this.targetId = targetId;
    this.correlationId = correlationId;
    this.safeReasonCode = safeReasonCode;
    this.details = details;
    this.occurredAt = occurredAt;
  }

  public Long getId() { return id; }
  public String getEventType() { return eventType; }
  public AccessScope getScope() { return scope; }
  public Long getTenantId() { return tenantId; }
  public Long getActorUserId() { return actorUserId; }
  public String getSystemOrigin() { return systemOrigin; }
  public String getTargetType() { return targetType; }
  public Long getTargetId() { return targetId; }
  public String getCorrelationId() { return correlationId; }
  public String getSafeReasonCode() { return safeReasonCode; }
  public String getDetails() { return details; }
  public Instant getOccurredAt() { return occurredAt; }
}
