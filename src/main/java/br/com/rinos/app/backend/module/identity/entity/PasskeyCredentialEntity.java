package br.com.rinos.app.backend.module.identity.entity;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import br.com.rinos.app.backend.module.identity.enums.PasskeyCredentialStatusEnum;
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
 * Material público necessário para reconstruir um CredentialRecord WebAuthn sem serialização opaca.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Entity
@Table(name = "identity_passkeyCredential", uniqueConstraints = {
    @UniqueConstraint(name = "uk_identity_passkey_credential_reference", columnNames = "reference"),
    @UniqueConstraint(name = "uk_identity_passkey_credential_id", columnNames = "credentialId")},
    indexes = @Index(name = "idx_identity_passkey_credential_user_state", columnList = "idPasskeyUser, status"))
public class PasskeyCredentialEntity {
  private static final Pattern TRANSPORTS = Pattern.compile("[a-z]+(?:,[a-z]+)*");
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false) private Long id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "idPasskeyUser", nullable = false) private PasskeyUserEntity passkeyUser;
  @Column(name = "reference", nullable = false, length = 16, columnDefinition = "BINARY(16)") private byte[] reference;
  @Column(name = "credentialType", nullable = false, length = 32) private String credentialType;
  @Column(name = "credentialId", nullable = false, length = 1024, columnDefinition = "VARBINARY(1024)") private byte[] credentialId;
  @Column(name = "publicKey", nullable = false, columnDefinition = "BLOB") private byte[] publicKey;
  @Column(name = "signatureCount", nullable = false, columnDefinition = "BIGINT UNSIGNED") private long signatureCount;
  @Column(name = "uvInitialized", nullable = false) private boolean uvInitialized;
  @Column(name = "backupEligible", nullable = false) private boolean backupEligible;
  @Column(name = "backupState", nullable = false) private boolean backupState;
  @Column(name = "transports", length = 255) private String transports;
  @Column(name = "attestationObject", nullable = false, columnDefinition = "BLOB") private byte[] attestationObject;
  @Column(name = "attestationClientDataJson", nullable = false, columnDefinition = "BLOB") private byte[] attestationClientDataJson;
  @Column(name = "label", nullable = false, length = 100) private String label;
  @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 24) private PasskeyCredentialStatusEnum status;
  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false) private Instant createdAt;
  @Column(name = "lastUsedAt") private Instant lastUsedAt;
  @Column(name = "revokedAt") private Instant revokedAt;
  @Version @Column(name = "version", nullable = false) private long version;

  protected PasskeyCredentialEntity() { }

  public PasskeyCredentialEntity(PasskeyUserEntity passkeyUser, UUID reference, String credentialType,
      byte[] credentialId, byte[] publicKey, long signatureCount, boolean uvInitialized,
      boolean backupEligible, boolean backupState, String transports, byte[] attestationObject,
      byte[] attestationClientDataJson, String label) {
    this.passkeyUser = Objects.requireNonNull(passkeyUser, "passkeyUser must not be null");
    this.reference = uuidBytes(reference);
    this.credentialType = text(credentialType, 32, "credentialType");
    this.credentialId = bytes(credentialId, 1, 1024, "credentialId");
    this.publicKey = bytes(publicKey, 1, Integer.MAX_VALUE, "publicKey");
    if (signatureCount < 0) throw new IllegalArgumentException("signatureCount must not be negative");
    this.signatureCount = signatureCount;
    this.uvInitialized = uvInitialized;
    this.backupEligible = backupEligible;
    this.backupState = backupState;
    if (transports != null && (transports.length() > 255 || !TRANSPORTS.matcher(transports).matches())) throw new IllegalArgumentException("transports must be canonical");
    this.transports = transports;
    this.attestationObject = bytes(attestationObject, 1, Integer.MAX_VALUE, "attestationObject");
    this.attestationClientDataJson = bytes(attestationClientDataJson, 1, Integer.MAX_VALUE, "attestationClientDataJson");
    this.label = text(label, 100, "label");
    status = PasskeyCredentialStatusEnum.ACTIVE;
  }
  public Long getId() { return id; }
  public PasskeyUserEntity getPasskeyUser() { return passkeyUser; }
  public UUID getReference() { ByteBuffer b = ByteBuffer.wrap(reference); return new UUID(b.getLong(), b.getLong()); }
  public String getCredentialType() { return credentialType; }
  public byte[] getCredentialId() { return Arrays.copyOf(credentialId, credentialId.length); }
  public byte[] getPublicKey() { return Arrays.copyOf(publicKey, publicKey.length); }
  public long getSignatureCount() { return signatureCount; }
  public boolean isUvInitialized() { return uvInitialized; }
  public boolean isBackupEligible() { return backupEligible; }
  public boolean isBackupState() { return backupState; }
  public String getTransports() { return transports; }
  public byte[] getAttestationObject() { return Arrays.copyOf(attestationObject, attestationObject.length); }
  public byte[] getAttestationClientDataJson() { return Arrays.copyOf(attestationClientDataJson, attestationClientDataJson.length); }
  public String getLabel() { return label; }
  public PasskeyCredentialStatusEnum getStatus() { return status; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getLastUsedAt() { return lastUsedAt; }
  public Instant getRevokedAt() { return revokedAt; }
  public long getVersion() { return version; }
  public void rename(String label) { this.label = text(label, 100, "label"); }
  public void recordUse(long newSignatureCount, boolean backupState, Instant occurredAt) {
    if (status != PasskeyCredentialStatusEnum.ACTIVE || newSignatureCount < signatureCount) throw new IllegalStateException("passkey credential cannot accept this assertion state");
    signatureCount = newSignatureCount;
    this.backupState = backupState;
    lastUsedAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
  public void revoke(Instant occurredAt) {
    if (status != PasskeyCredentialStatusEnum.ACTIVE) throw new IllegalStateException("passkey credential is not active");
    status = PasskeyCredentialStatusEnum.REVOKED;
    revokedAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
  private static String text(String value, int max, String name) {
    Objects.requireNonNull(value, name + " must not be null");
    if (value.isBlank() || value.length() > max) throw new IllegalArgumentException(name + " is invalid"); return value;
  }
  private static byte[] bytes(byte[] value, int min, int max, String name) {
    Objects.requireNonNull(value, name + " must not be null");
    if (value.length < min || value.length > max) throw new IllegalArgumentException(name + " length is invalid"); return Arrays.copyOf(value, value.length);
  }
  private static byte[] uuidBytes(UUID value) {
    Objects.requireNonNull(value, "reference must not be null");
    return ByteBuffer.allocate(16).putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits()).array();
  }
}
