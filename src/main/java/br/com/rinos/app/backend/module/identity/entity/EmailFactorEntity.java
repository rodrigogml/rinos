package br.com.rinos.app.backend.module.identity.entity;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import br.com.rinos.app.backend.module.identity.enums.EmailFactorStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * Configuração do e-mail principal confirmado como fator adicional, sem duplicar o endereço.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Entity
@Table(name = "identity_emailFactor", uniqueConstraints = {
    @UniqueConstraint(name = "uk_identity_email_factor_user", columnNames = "idUser"),
    @UniqueConstraint(name = "uk_identity_email_factor_reference", columnNames = "reference")})
public class EmailFactorEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false)
  private Long id;
  @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "idUser", nullable = false)
  private UserEntity user;
  @Column(name = "reference", nullable = false, length = 16, columnDefinition = "BINARY(16)")
  private byte[] reference;
  @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 24)
  private EmailFactorStatusEnum status;
  @Column(name = "activatedAt", nullable = false) private Instant activatedAt;
  @Column(name = "lastUsedAt") private Instant lastUsedAt;
  @Column(name = "disabledAt") private Instant disabledAt;
  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false) private Instant createdAt;
  @Column(name = "updatedAt", nullable = false, insertable = false, updatable = false) private Instant updatedAt;
  @Version @Column(name = "version", nullable = false) private long version;

  protected EmailFactorEntity() { }
  public EmailFactorEntity(UserEntity user, UUID reference, Instant activatedAt) {
    this.user = Objects.requireNonNull(user, "user must not be null");
    this.reference = uuidBytes(reference);
    this.activatedAt = Objects.requireNonNull(activatedAt, "activatedAt must not be null");
    status = EmailFactorStatusEnum.ACTIVE;
  }
  public Long getId() { return id; }
  public UserEntity getUser() { return user; }
  public UUID getReference() { ByteBuffer b = ByteBuffer.wrap(reference); return new UUID(b.getLong(), b.getLong()); }
  public EmailFactorStatusEnum getStatus() { return status; }
  public Instant getActivatedAt() { return activatedAt; }
  public Instant getLastUsedAt() { return lastUsedAt; }
  public Instant getDisabledAt() { return disabledAt; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public long getVersion() { return version; }
  public void activate(Instant occurredAt) {
    status = EmailFactorStatusEnum.ACTIVE;
    activatedAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    disabledAt = null;
  }
  public void recordUse(Instant occurredAt) {
    if (status != EmailFactorStatusEnum.ACTIVE) throw new IllegalStateException("email factor is not active");
    lastUsedAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
  public void disable(Instant occurredAt) {
    if (status != EmailFactorStatusEnum.ACTIVE) throw new IllegalStateException("email factor is not active");
    status = EmailFactorStatusEnum.DISABLED;
    disabledAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
  private static byte[] uuidBytes(UUID value) {
    Objects.requireNonNull(value, "reference must not be null");
    return ByteBuffer.allocate(16).putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits()).array();
  }
}
