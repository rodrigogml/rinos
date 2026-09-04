package br.com.rinos.app.backend.module.access.entity;

import java.time.Instant;

import br.com.rinos.app.backend.module.access.enums.AccessRuleChangeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Evento append-only que preserva cada versão sem participar da decisão. */
@Entity
@Table(name = "access_ruleHistory")
public class AccessRuleHistoryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "idAccessRuleHistory", nullable = false)
  private Long id;

  @Column(name = "idAccessRule", nullable = false, updatable = false)
  private Long accessRuleId;

  @Enumerated(EnumType.STRING)
  @Column(name = "changeType", nullable = false, length = 32, updatable = false)
  private AccessRuleChangeType changeType;

  @Column(name = "previousSnapshot", columnDefinition = "JSON", updatable = false)
  private String previousSnapshot;

  @Column(name = "newSnapshot", nullable = false, columnDefinition = "JSON", updatable = false)
  private String newSnapshot;

  @Column(name = "actorUserId", updatable = false)
  private Long actorUserId;

  @Column(name = "systemOrigin", length = 100, updatable = false)
  private String systemOrigin;

  @Column(name = "reason", length = 500, updatable = false)
  private String reason;

  @Column(name = "correlationId", nullable = false, length = 100, updatable = false)
  private String correlationId;

  @Column(name = "occurredAt", nullable = false, updatable = false)
  private Instant occurredAt;

  protected AccessRuleHistoryEntity() {
  }

  public AccessRuleHistoryEntity(Long accessRuleId, AccessRuleChangeType changeType,
      String previousSnapshot, String newSnapshot, Long actorUserId, String systemOrigin,
      String reason, String correlationId, Instant occurredAt) {
    this.accessRuleId = accessRuleId;
    this.changeType = changeType;
    this.previousSnapshot = previousSnapshot;
    this.newSnapshot = newSnapshot;
    this.actorUserId = actorUserId;
    this.systemOrigin = systemOrigin;
    this.reason = reason;
    this.correlationId = correlationId;
    this.occurredAt = occurredAt;
  }

  public Long getId() { return id; }
  public Long getAccessRuleId() { return accessRuleId; }
  public AccessRuleChangeType getChangeType() { return changeType; }
  public String getPreviousSnapshot() { return previousSnapshot; }
  public String getNewSnapshot() { return newSnapshot; }
  public Long getActorUserId() { return actorUserId; }
  public String getSystemOrigin() { return systemOrigin; }
  public String getReason() { return reason; }
  public String getCorrelationId() { return correlationId; }
  public Instant getOccurredAt() { return occurredAt; }
}
