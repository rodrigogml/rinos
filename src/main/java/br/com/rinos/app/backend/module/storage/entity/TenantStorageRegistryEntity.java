package br.com.rinos.app.backend.module.storage.entity;

import java.time.Instant;

import br.com.rinos.app.backend.module.storage.component.TenantPhysicalIdentifierConverter;
import br.com.rinos.app.backend.module.storage.enums.TenantStorageState;
import br.com.rinos.app.backend.module.storage.vo.TenantPhysicalIdentifier;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Registro global imutável que associa um tenant ao seu identificador físico interno. */
@Entity
@Table(name = "storage_tenantRegistry")
public class TenantStorageRegistryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "idTenantStorageRegistry", nullable = false)
  private Long id;

  @Column(name = "idTenant", nullable = false, updatable = false)
  private Long tenantId;

  @Convert(converter = TenantPhysicalIdentifierConverter.class)
  @Column(name = "physicalIdentifier", nullable = false, length = 32, updatable = false)
  private TenantPhysicalIdentifier physicalIdentifier;

  @Enumerated(EnumType.STRING)
  @Column(name = "storageState", nullable = false, length = 24)
  private TenantStorageState storageState;

  @Column(name = "expectedVersion", nullable = false, length = 32)
  private String expectedVersion;

  @Column(name = "observedVersion", length = 32)
  private String observedVersion;

  @Column(name = "lastValidatedAt")
  private Instant lastValidatedAt;

  @Column(name = "quarantineReasonCode", length = 100)
  private String quarantineReasonCode;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updatedAt", nullable = false, insertable = false, updatable = false)
  private Instant updatedAt;

  protected TenantStorageRegistryEntity() {
  }

  public TenantStorageRegistryEntity(Long tenantId, TenantPhysicalIdentifier physicalIdentifier,
      String expectedVersion) {
    this.tenantId = tenantId;
    this.physicalIdentifier = physicalIdentifier;
    this.expectedVersion = expectedVersion;
    this.storageState = TenantStorageState.REQUESTED;
  }

  public Long getId() { return id; }
  public Long getTenantId() { return tenantId; }
  public TenantPhysicalIdentifier getPhysicalIdentifier() { return physicalIdentifier; }
  public TenantStorageState getStorageState() { return storageState; }
  public String getExpectedVersion() { return expectedVersion; }
  public String getObservedVersion() { return observedVersion; }
  public Instant getLastValidatedAt() { return lastValidatedAt; }
  public String getQuarantineReasonCode() { return quarantineReasonCode; }
  public long getVersion() { return version; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
