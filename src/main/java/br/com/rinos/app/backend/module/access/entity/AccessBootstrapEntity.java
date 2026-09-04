package br.com.rinos.app.backend.module.access.entity;

import java.time.Instant;

import br.com.rinos.app.backend.module.access.enums.AccessBootstrapStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Singleton que diferencia instalação nunca inicializada de perda administrativa posterior. */
@Entity
@Table(name = "access_bootstrap")
public class AccessBootstrapEntity {

  @Id
  @Column(name = "idAccessBootstrap", nullable = false, updatable = false)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private AccessBootstrapStatus status;

  @Column(name = "completedByUserId")
  private Long completedByUserId;

  @Column(name = "completedAt")
  private Instant completedAt;

  @Column(name = "correlationId", length = 100)
  private String correlationId;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  protected AccessBootstrapEntity() {
  }

  public Long getId() { return id; }
  public AccessBootstrapStatus getStatus() { return status; }
  public Long getCompletedByUserId() { return completedByUserId; }
  public Instant getCompletedAt() { return completedAt; }
  public String getCorrelationId() { return correlationId; }
  public long getVersion() { return version; }

  public void complete(Long userId, Instant occurredAt, String newCorrelationId) {
    status = AccessBootstrapStatus.COMPLETED;
    completedByUserId = userId;
    completedAt = occurredAt;
    correlationId = newCorrelationId;
  }
}
