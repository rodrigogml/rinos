package br.com.rinos.app.backend.module.identity.entity;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import br.com.rinos.app.backend.module.identity.enums.RecoveryCodeSetStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * Conjunto versionável de códigos de recuperação de uso único.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Entity
@Table(name = "identity_recoveryCodeSet", uniqueConstraints = {
    @UniqueConstraint(name = "uk_identity_recovery_code_set_reference", columnNames = "reference"),
    @UniqueConstraint(name = "uk_identity_recovery_code_set_active", columnNames = {"idUser", "activeMarker"})})
public class RecoveryCodeSetEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false) private Long id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "idUser", nullable = false) private UserEntity user;
  @Column(name = "reference", nullable = false, length = 16, columnDefinition = "BINARY(16)") private byte[] reference;
  @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 24) private RecoveryCodeSetStatusEnum status;
  @Column(name = "activeMarker") private Boolean activeMarker;
  @Column(name = "issuedAt", nullable = false) private Instant issuedAt;
  @Column(name = "invalidatedAt") private Instant invalidatedAt;
  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false) private Instant createdAt;
  @Column(name = "updatedAt", nullable = false, insertable = false, updatable = false) private Instant updatedAt;
  @Version @Column(name = "version", nullable = false) private long version;

  protected RecoveryCodeSetEntity() { }
  public RecoveryCodeSetEntity(UserEntity user, UUID reference, Instant issuedAt) {
    this.user = Objects.requireNonNull(user, "user must not be null");
    this.reference = uuidBytes(reference);
    this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    status = RecoveryCodeSetStatusEnum.ACTIVE;
    activeMarker = Boolean.TRUE;
  }
  public Long getId() { return id; }
  public UserEntity getUser() { return user; }
  public UUID getReference() { ByteBuffer b = ByteBuffer.wrap(reference); return new UUID(b.getLong(), b.getLong()); }
  public RecoveryCodeSetStatusEnum getStatus() { return status; }
  public Boolean getActiveMarker() { return activeMarker; }
  public Instant getIssuedAt() { return issuedAt; }
  public Instant getInvalidatedAt() { return invalidatedAt; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public long getVersion() { return version; }
  public void invalidate(Instant occurredAt) { close(RecoveryCodeSetStatusEnum.INVALIDATED, occurredAt); }
  public void exhaust(Instant occurredAt) { close(RecoveryCodeSetStatusEnum.EXHAUSTED, occurredAt); }
  private void close(RecoveryCodeSetStatusEnum next, Instant occurredAt) {
    if (status != RecoveryCodeSetStatusEnum.ACTIVE) throw new IllegalStateException("recovery code set is not active");
    status = next;
    activeMarker = null;
    invalidatedAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
  private static byte[] uuidBytes(UUID value) {
    Objects.requireNonNull(value, "reference must not be null");
    return ByteBuffer.allocate(16).putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits()).array();
  }
}
