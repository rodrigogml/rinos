package br.com.rinos.app.backend.module.identity.entity;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
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
 * Continuação persistente e opaca entre um primeiro fator e a conclusão da autenticação.
 *
 * <p>As transições são fechadas nesta entidade: somente um fluxo aberto pode ser usado,
 * invalidado, expirado ou receber uma falha. A referência bruta nunca é persistida.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Entity
@Table(
    name = "identity_authenticationFlow",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_identity_authentication_flow_reference",
        columnNames = "referenceHash"),
    indexes = {
        @Index(
            name = "idx_identity_authentication_flow_user_state",
            columnList = "idUser, purpose, status, expiresAt"),
        @Index(
            name = "idx_identity_authentication_flow_expiry",
            columnList = "status, expiresAt")
    })
public class AuthenticationFlowEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "idUser")
  private UserEntity user;

  @Column(name = "referenceHash", nullable = false, length = 32, columnDefinition = "BINARY(32)")
  private byte[] referenceHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "purpose", nullable = false, length = 32)
  private AuthenticationFlowPurposeEnum purpose;

  @Enumerated(EnumType.STRING)
  @Column(name = "primaryMethod", length = 32)
  private AuthenticationMethodEnum primaryMethod;

  @Enumerated(EnumType.STRING)
  @Column(name = "requiredAssurance", nullable = false, length = 24)
  private AuthenticationAssuranceEnum requiredAssurance;

  @Column(name = "persistentLoginRequested", nullable = false)
  private boolean persistentLoginRequested;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private AuthenticationFlowStatusEnum status;

  @Column(name = "failureCount", nullable = false)
  private int failureCount;

  @Column(name = "issuedAt", nullable = false)
  private Instant issuedAt;

  @Column(name = "expiresAt", nullable = false)
  private Instant expiresAt;

  @Column(name = "usedAt")
  private Instant usedAt;

  @Column(name = "invalidatedAt")
  private Instant invalidatedAt;

  @Column(name = "correlationId", nullable = false, length = 16, columnDefinition = "BINARY(16)")
  private byte[] correlationId;

  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updatedAt", nullable = false, insertable = false, updatable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  protected AuthenticationFlowEntity() {
  }

  /**
   * Cria um fluxo aberto com valores já validados pelo serviço.
   *
   * @throws IllegalArgumentException quando hashes ou intervalo temporal são inválidos
   */
  public AuthenticationFlowEntity(
      UserEntity user,
      byte[] referenceHash,
      AuthenticationFlowPurposeEnum purpose,
      AuthenticationMethodEnum primaryMethod,
      AuthenticationAssuranceEnum requiredAssurance,
      boolean persistentLoginRequested,
      Instant issuedAt,
      Instant expiresAt,
      UUID correlationId) {
    this.user = user;
    this.referenceHash = copyWithLength(referenceHash, 32, "referenceHash");
    this.purpose = Objects.requireNonNull(purpose, "purpose must not be null");
    this.primaryMethod = primaryMethod;
    this.requiredAssurance = Objects.requireNonNull(
        requiredAssurance,
        "requiredAssurance must not be null");
    this.persistentLoginRequested = persistentLoginRequested;
    this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    if (!expiresAt.isAfter(issuedAt)) {
      throw new IllegalArgumentException("expiresAt must be after issuedAt");
    }
    this.correlationId = uuidBytes(Objects.requireNonNull(
        correlationId,
        "correlationId must not be null"));
    status = AuthenticationFlowStatusEnum.OPEN;
  }

  public Long getId() {
    return id;
  }

  public UserEntity getUser() {
    return user;
  }

  public byte[] getReferenceHash() {
    return Arrays.copyOf(referenceHash, referenceHash.length);
  }

  public AuthenticationFlowPurposeEnum getPurpose() {
    return purpose;
  }

  public AuthenticationMethodEnum getPrimaryMethod() {
    return primaryMethod;
  }

  public AuthenticationAssuranceEnum getRequiredAssurance() {
    return requiredAssurance;
  }

  public boolean isPersistentLoginRequested() {
    return persistentLoginRequested;
  }

  public AuthenticationFlowStatusEnum getStatus() {
    return status;
  }

  public int getFailureCount() {
    return failureCount;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getUsedAt() {
    return usedAt;
  }

  public Instant getInvalidatedAt() {
    return invalidatedAt;
  }

  public UUID getCorrelationId() {
    java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(correlationId);
    return new UUID(buffer.getLong(), buffer.getLong());
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public long getVersion() {
    return version;
  }

  /** Marca o fluxo aberto como consumido exatamente uma vez. */
  public void markUsed(Instant occurredAt) {
    requireOpen();
    usedAt = requireNotBeforeIssue(occurredAt);
    status = AuthenticationFlowStatusEnum.USED;
  }

  /** Invalida um fluxo ainda aberto. */
  public void invalidate(Instant occurredAt) {
    requireOpen();
    invalidatedAt = requireNotBeforeIssue(occurredAt);
    status = AuthenticationFlowStatusEnum.INVALIDATED;
  }

  /** Expira logicamente um fluxo ainda aberto. */
  public void expire(Instant occurredAt) {
    requireOpen();
    invalidatedAt = requireNotBeforeIssue(occurredAt);
    status = AuthenticationFlowStatusEnum.EXPIRED;
  }

  /** Registra uma tentativa rejeitada sem permitir overflow do contador. */
  public void registerFailure() {
    requireOpen();
    if (failureCount == Integer.MAX_VALUE) {
      throw new IllegalStateException("failureCount limit reached");
    }
    failureCount++;
  }

  private void requireOpen() {
    if (status != AuthenticationFlowStatusEnum.OPEN) {
      throw new IllegalStateException("authentication flow is not open");
    }
  }

  private Instant requireNotBeforeIssue(Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    if (occurredAt.isBefore(issuedAt)) {
      throw new IllegalArgumentException("occurredAt must not be before issuedAt");
    }
    return occurredAt;
  }

  private static byte[] copyWithLength(byte[] value, int length, String name) {
    Objects.requireNonNull(value, name + " must not be null");
    if (value.length != length) {
      throw new IllegalArgumentException(name + " must contain exactly " + length + " bytes");
    }
    return Arrays.copyOf(value, value.length);
  }

  private static byte[] uuidBytes(UUID value) {
    return java.nio.ByteBuffer.allocate(16)
        .putLong(value.getMostSignificantBits())
        .putLong(value.getLeastSignificantBits())
        .array();
  }
}
