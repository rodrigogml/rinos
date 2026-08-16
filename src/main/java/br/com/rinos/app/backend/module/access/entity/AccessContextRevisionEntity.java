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

/** Guarda monotônica que torna snapshots obsoletos entre instâncias. */
@Entity
@Table(name = "access_contextRevision")
public class AccessContextRevisionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "idAccessContextRevision", nullable = false)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "scopeType", nullable = false, length = 16, updatable = false)
  private AccessScope scope;

  @Column(name = "idTenant", updatable = false)
  private Long tenantId;

  @Column(name = "revision", nullable = false)
  private long revision;

  @Column(name = "updatedAt", nullable = false, insertable = false, updatable = false)
  private Instant updatedAt;

  protected AccessContextRevisionEntity() {
  }

  public AccessContextRevisionEntity(AccessScope scope, Long tenantId) {
    this.scope = scope;
    this.tenantId = tenantId;
  }

  public Long getId() { return id; }
  public AccessScope getScope() { return scope; }
  public Long getTenantId() { return tenantId; }
  public long getRevision() { return revision; }
  public Instant getUpdatedAt() { return updatedAt; }

  public long increment() {
    return ++revision;
  }
}
