package br.com.rinos.app.backend.module.access.entity;

import java.time.Instant;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.access.enums.AccessRecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Grupo de acesso pertencente a exatamente um contexto. */
@Entity
@Table(name = "access_group")
public class AccessGroupEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "idAccessGroup", nullable = false)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "scopeType", nullable = false, length = 16, updatable = false)
  private AccessScope scope;

  @Column(name = "idTenant", updatable = false)
  private Long tenantId;

  @Column(name = "name", nullable = false, length = 160)
  private String name;

  @Column(name = "normalizedName", nullable = false, length = 160)
  private String normalizedName;

  @Column(name = "description", length = 500)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private AccessRecordStatus status;

  @Column(name = "protectedGroup", nullable = false)
  private boolean protectedGroup;

  @Column(name = "baselineVersion")
  private Integer baselineVersion;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updatedAt", nullable = false, insertable = false, updatable = false)
  private Instant updatedAt;

  protected AccessGroupEntity() {
  }

  public AccessGroupEntity(AccessScope scope, Long tenantId, String name, String normalizedName,
      String description, boolean protectedGroup, Integer baselineVersion) {
    this.scope = scope;
    this.tenantId = tenantId;
    this.name = name;
    this.normalizedName = normalizedName;
    this.description = description;
    this.protectedGroup = protectedGroup;
    this.baselineVersion = baselineVersion;
    this.status = AccessRecordStatus.ACTIVE;
  }

  public Long getId() { return id; }
  public AccessScope getScope() { return scope; }
  public Long getTenantId() { return tenantId; }
  public String getName() { return name; }
  public String getNormalizedName() { return normalizedName; }
  public String getDescription() { return description; }
  public AccessRecordStatus getStatus() { return status; }
  public boolean isProtectedGroup() { return protectedGroup; }
  public Integer getBaselineVersion() { return baselineVersion; }
  public long getVersion() { return version; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }

  public void update(String newName, String newNormalizedName, String newDescription) {
    name = newName;
    normalizedName = newNormalizedName;
    description = newDescription;
  }

  public void deactivate() {
    status = AccessRecordStatus.INACTIVE;
  }
}
