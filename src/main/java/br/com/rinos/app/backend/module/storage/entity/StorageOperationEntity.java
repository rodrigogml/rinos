package br.com.rinos.app.backend.module.storage.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import br.com.rinos.app.backend.module.storage.enums.StorageOperationState;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Operação estrutural durável, idempotente e ordenável dentro da fila global. */
@Entity
@Table(name = "storage_operation")
public class StorageOperationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "idStorageOperation", nullable = false)
  private Long id;

  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "publicId", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
  private UUID publicId;

  @Column(name = "idTenantStorageRegistry", nullable = false, updatable = false)
  private Long tenantStorageRegistryId;

  @Enumerated(EnumType.STRING)
  @Column(name = "operationType", nullable = false, length = 24, updatable = false)
  private StorageOperationType operationType;

  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "idempotencyReference", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
  private UUID idempotencyReference;

  @Enumerated(EnumType.STRING)
  @Column(name = "operationState", nullable = false, length = 24)
  private StorageOperationState operationState;

  @Column(name = "activeMarker")
  private Boolean activeMarker;

  @Column(name = "attemptCount", nullable = false)
  private int attemptCount;

  @Column(name = "nextAttemptAt")
  private Instant nextAttemptAt;

  @Column(name = "leaseOwner", length = 100)
  private String leaseOwner;

  @Column(name = "leaseUntil")
  private Instant leaseUntil;

  @Column(name = "correlationId", nullable = false, length = 100, updatable = false)
  private String correlationId;

  @Column(name = "safeFailureCode", length = 100)
  private String safeFailureCode;

  @Column(name = "startedAt")
  private Instant startedAt;

  @Column(name = "finishedAt")
  private Instant finishedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updatedAt", nullable = false, insertable = false, updatable = false)
  private Instant updatedAt;

  protected StorageOperationEntity() {
  }

  public StorageOperationEntity(UUID publicId, Long tenantStorageRegistryId,
      StorageOperationType operationType, UUID idempotencyReference, String correlationId) {
    this.publicId = publicId;
    this.tenantStorageRegistryId = tenantStorageRegistryId;
    this.operationType = operationType;
    this.idempotencyReference = idempotencyReference;
    this.correlationId = correlationId;
    this.operationState = StorageOperationState.QUEUED;
    this.activeMarker = Boolean.TRUE;
  }

  public Long getId() { return id; }
  public UUID getPublicId() { return publicId; }
  public Long getTenantStorageRegistryId() { return tenantStorageRegistryId; }
  public StorageOperationType getOperationType() { return operationType; }
  public UUID getIdempotencyReference() { return idempotencyReference; }
  public StorageOperationState getOperationState() { return operationState; }
  public Boolean getActiveMarker() { return activeMarker; }
  public int getAttemptCount() { return attemptCount; }
  public Instant getNextAttemptAt() { return nextAttemptAt; }
  public String getLeaseOwner() { return leaseOwner; }
  public Instant getLeaseUntil() { return leaseUntil; }
  public String getCorrelationId() { return correlationId; }
  public String getSafeFailureCode() { return safeFailureCode; }
  public Instant getStartedAt() { return startedAt; }
  public Instant getFinishedAt() { return finishedAt; }
  public long getVersion() { return version; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }

  /**
   * Registra a posse temporária da operação por uma instância de manutenção.
   *
   * @param owner identidade da instância vencedora
   * @param until instante UTC de expiração do lease
   */
  public void claim(String owner, Instant until) {
    this.operationState = StorageOperationState.CLAIMED;
    this.leaseOwner = owner;
    this.leaseUntil = until;
    this.attemptCount++;
  }

  /**
   * Informa se a posse ainda pertence à instância e não expirou no instante avaliado.
   *
   * @param owner identidade da instância que pretende confirmar uma etapa
   * @param now instante UTC da confirmação
   * @return {@code true} quando a operação ainda está sob a posse informada
   */
  public boolean hasActiveLease(String owner, Instant now) {
    return owner != null && owner.equals(leaseOwner) && leaseUntil != null && leaseUntil.isAfter(now);
  }

  /**
   * Marca o começo observável da execução física já reclamada.
   *
   * @param now instante UTC do início
   */
  public void start(Instant now) {
    Objects.requireNonNull(now, "now must not be null");
    this.operationState = StorageOperationState.RUNNING;
    if (startedAt == null) {
      this.startedAt = now;
    }
  }

  /**
   * Encerra a operação depois da comprovação estrutural e libera sua exclusividade no tenant.
   *
   * @param now instante UTC da conclusão
   */
  public void complete(Instant now) {
    Objects.requireNonNull(now, "now must not be null");
    this.operationState = StorageOperationState.COMPLETED;
    this.activeMarker = null;
    this.leaseOwner = null;
    this.leaseUntil = null;
    this.nextAttemptAt = null;
    this.finishedAt = now;
  }
}
