package br.com.rinos.app.backend.module.storage.entity;

import java.time.Instant;

import br.com.rinos.app.backend.module.storage.enums.StorageMigrationExecutionState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Evidência imutável de um script de tenant observado durante provisionamento ou migration. */
@Entity
@Table(name = "storage_migrationExecution")
public class StorageMigrationExecutionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "idStorageMigrationExecution", nullable = false)
  private Long id;

  @Column(name = "idTenantStorageRegistry", nullable = false, updatable = false)
  private Long tenantStorageRegistryId;

  @Column(name = "idStorageOperation", updatable = false)
  private Long storageOperationId;

  @Column(name = "scriptVersion", nullable = false, length = 32, updatable = false)
  private String scriptVersion;

  @Column(name = "scriptName", nullable = false, length = 160, updatable = false)
  private String scriptName;

  @Column(name = "scriptHash", columnDefinition = "BINARY(32)", nullable = false, updatable = false)
  private byte[] scriptHash;

  @Column(name = "previousVersion", length = 32, updatable = false)
  private String previousVersion;

  @Column(name = "resultingVersion", length = 32)
  private String resultingVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "executionState", nullable = false, length = 24)
  private StorageMigrationExecutionState executionState;

  @Column(name = "startedAt", nullable = false, updatable = false)
  private Instant startedAt;

  @Column(name = "finishedAt")
  private Instant finishedAt;

  @Column(name = "safeFailureCode", length = 100)
  private String safeFailureCode;

  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  protected StorageMigrationExecutionEntity() {
  }

  public StorageMigrationExecutionEntity(Long tenantStorageRegistryId, Long storageOperationId,
      String scriptVersion, String scriptName, byte[] scriptHash, String previousVersion,
      Instant startedAt) {
    this.tenantStorageRegistryId = tenantStorageRegistryId;
    this.storageOperationId = storageOperationId;
    this.scriptVersion = scriptVersion;
    this.scriptName = scriptName;
    this.scriptHash = scriptHash.clone();
    this.previousVersion = previousVersion;
    this.startedAt = startedAt;
    this.executionState = StorageMigrationExecutionState.STARTED;
  }

  public Long getId() { return id; }
  public Long getTenantStorageRegistryId() { return tenantStorageRegistryId; }
  public Long getStorageOperationId() { return storageOperationId; }
  public String getScriptVersion() { return scriptVersion; }
  public String getScriptName() { return scriptName; }
  public byte[] getScriptHash() { return scriptHash.clone(); }
  public String getPreviousVersion() { return previousVersion; }
  public String getResultingVersion() { return resultingVersion; }
  public StorageMigrationExecutionState getExecutionState() { return executionState; }
  public Instant getStartedAt() { return startedAt; }
  public Instant getFinishedAt() { return finishedAt; }
  public String getSafeFailureCode() { return safeFailureCode; }
  public Instant getCreatedAt() { return createdAt; }
}
