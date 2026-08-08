package br.com.rinos.app.backend.module.identity.entity;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import br.com.rinos.app.backend.module.identity.enums.TotpFactorStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * Mantém somente o segredo TOTP cifrado e seu estado persistente.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Entity
@Table(name = "identity_totpFactor",
    uniqueConstraints = @UniqueConstraint(name = "uk_identity_totp_factor_reference", columnNames = "reference"),
    indexes = @Index(name = "idx_identity_totp_factor_user_state", columnList = "idUser, status"))
public class TotpFactorEntity {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "idUser", nullable = false)
  private UserEntity user;
  @Column(name = "reference", nullable = false, length = 16, columnDefinition = "BINARY(16)")
  private byte[] reference;
  @Column(name = "label", nullable = false, length = 100)
  private String label;
  @Column(name = "encryptedSecret", nullable = false, length = 512, columnDefinition = "VARBINARY(512)")
  private byte[] encryptedSecret;
  @Column(name = "encryptionNonce", nullable = false, length = 12, columnDefinition = "BINARY(12)")
  private byte[] encryptionNonce;
  @Column(name = "keyVersion", nullable = false, length = 32)
  private String keyVersion;
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private TotpFactorStatusEnum status;
  @Column(name = "lastAcceptedStep")
  private Long lastAcceptedStep;
  @Column(name = "confirmedAt")
  private Instant confirmedAt;
  @Column(name = "lastUsedAt")
  private Instant lastUsedAt;
  @Column(name = "revokedAt")
  private Instant revokedAt;
  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;
  @Column(name = "updatedAt", nullable = false, insertable = false, updatable = false)
  private Instant updatedAt;
  @Version @Column(name = "version", nullable = false)
  private long version;

  protected TotpFactorEntity() { }

  public TotpFactorEntity(UserEntity user, UUID reference, String label, byte[] encryptedSecret,
      byte[] encryptionNonce, String keyVersion) {
    this.user = Objects.requireNonNull(user, "user must not be null");
    this.reference = uuidBytes(reference);
    this.label = text(label, 100, "label");
    this.encryptedSecret = bytes(encryptedSecret, 1, 512, "encryptedSecret");
    this.encryptionNonce = bytes(encryptionNonce, 12, 12, "encryptionNonce");
    this.keyVersion = text(keyVersion, 32, "keyVersion");
    status = TotpFactorStatusEnum.PENDING;
  }

  public Long getId() { return id; }
  public UserEntity getUser() { return user; }
  public UUID getReference() { return uuid(reference); }
  public String getLabel() { return label; }
  public byte[] getEncryptedSecret() { return Arrays.copyOf(encryptedSecret, encryptedSecret.length); }
  public byte[] getEncryptionNonce() { return Arrays.copyOf(encryptionNonce, encryptionNonce.length); }
  public String getKeyVersion() { return keyVersion; }
  public TotpFactorStatusEnum getStatus() { return status; }
  public Long getLastAcceptedStep() { return lastAcceptedStep; }
  public Instant getConfirmedAt() { return confirmedAt; }
  public Instant getLastUsedAt() { return lastUsedAt; }
  public Instant getRevokedAt() { return revokedAt; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public long getVersion() { return version; }

  public void confirm(long acceptedStep, Instant occurredAt) {
    if (status != TotpFactorStatusEnum.PENDING || acceptedStep < 0) {
      throw new IllegalStateException("pending TOTP factor and non-negative step are required");
    }
    status = TotpFactorStatusEnum.ACTIVE;
    lastAcceptedStep = acceptedStep;
    confirmedAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    lastUsedAt = occurredAt;
  }

  public void acceptStep(long acceptedStep, Instant occurredAt) {
    if (status != TotpFactorStatusEnum.ACTIVE || acceptedStep < 0
        || (lastAcceptedStep != null && acceptedStep <= lastAcceptedStep)) {
      throw new IllegalStateException("TOTP step must be newer on an active factor");
    }
    lastAcceptedStep = acceptedStep;
    lastUsedAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }

  public void revoke(Instant occurredAt) {
    if (status == TotpFactorStatusEnum.REVOKED) {
      throw new IllegalStateException("TOTP factor is already revoked");
    }
    status = TotpFactorStatusEnum.REVOKED;
    revokedAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }

  private static String text(String value, int max, String name) {
    Objects.requireNonNull(value, name + " must not be null");
    if (value.isBlank() || value.length() > max) throw new IllegalArgumentException(name + " is invalid");
    return value;
  }
  private static byte[] bytes(byte[] value, int min, int max, String name) {
    Objects.requireNonNull(value, name + " must not be null");
    if (value.length < min || value.length > max) throw new IllegalArgumentException(name + " length is invalid");
    return Arrays.copyOf(value, value.length);
  }
  private static byte[] uuidBytes(UUID value) {
    Objects.requireNonNull(value, "reference must not be null");
    return ByteBuffer.allocate(16).putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits()).array();
  }
  private static UUID uuid(byte[] value) {
    ByteBuffer buffer = ByteBuffer.wrap(value);
    return new UUID(buffer.getLong(), buffer.getLong());
  }
}
