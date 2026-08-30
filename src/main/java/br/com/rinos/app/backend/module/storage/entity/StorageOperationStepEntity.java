package br.com.rinos.app.backend.module.storage.entity;

import java.time.Instant;

import br.com.rinos.app.backend.module.storage.enums.StorageOperationStepState;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationStepType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Etapa persistida de uma operação, usada para observar e retomar efeitos não transacionais. */
@Entity
@Table(name = "storage_operationStep")
public class StorageOperationStepEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "idStorageOperationStep", nullable = false)
  private Long id;

  @Column(name = "idStorageOperation", nullable = false, updatable = false)
  private Long storageOperationId;

  @Enumerated(EnumType.STRING)
  @Column(name = "stepType", nullable = false, length = 32, updatable = false)
  private StorageOperationStepType stepType;

  @Enumerated(EnumType.STRING)
  @Column(name = "stepState", nullable = false, length = 24)
  private StorageOperationStepState stepState;

  @Column(name = "attemptNumber", nullable = false)
  private int attemptNumber;

  @Column(name = "startedAt")
  private Instant startedAt;

  @Column(name = "completedAt")
  private Instant completedAt;

  @Column(name = "evidenceHash", columnDefinition = "BINARY(32)")
  private byte[] evidenceHash;

  @Column(name = "safeFailureCode", length = 100)
  private String safeFailureCode;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updatedAt", nullable = false, insertable = false, updatable = false)
  private Instant updatedAt;

  protected StorageOperationStepEntity() {
  }

  public StorageOperationStepEntity(Long storageOperationId, StorageOperationStepType stepType) {
    this.storageOperationId = storageOperationId;
    this.stepType = stepType;
    this.stepState = StorageOperationStepState.PENDING;
  }

  public Long getId() { return id; }
  public Long getStorageOperationId() { return storageOperationId; }
  public StorageOperationStepType getStepType() { return stepType; }
  public StorageOperationStepState getStepState() { return stepState; }
  public int getAttemptNumber() { return attemptNumber; }
  public Instant getStartedAt() { return startedAt; }
  public Instant getCompletedAt() { return completedAt; }
  public byte[] getEvidenceHash() { return evidenceHash == null ? null : evidenceHash.clone(); }
  public String getSafeFailureCode() { return safeFailureCode; }
  public long getVersion() { return version; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
