package br.com.rinos.app.backend.module.access.entity;

import java.time.Instant;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.access.enums.ProtectedBaselineStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Versão explícita das chaves mínimas de um grupo protegido. */
@Entity
@Table(name = "access_protectedGroupBaseline")
public class ProtectedGroupBaselineEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "idProtectedGroupBaseline", nullable = false)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "scopeType", nullable = false, length = 16, updatable = false)
  private AccessScope scope;

  @Column(name = "baselineVersion", nullable = false, updatable = false)
  private int baselineVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private ProtectedBaselineStatus status;

  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  protected ProtectedGroupBaselineEntity() {
  }

  public ProtectedGroupBaselineEntity(AccessScope scope, int baselineVersion) {
    this.scope = scope;
    this.baselineVersion = baselineVersion;
    this.status = ProtectedBaselineStatus.ACTIVE;
  }

  public Long getId() { return id; }
  public AccessScope getScope() { return scope; }
  public int getBaselineVersion() { return baselineVersion; }
  public ProtectedBaselineStatus getStatus() { return status; }
  public Instant getCreatedAt() { return createdAt; }
}
