package br.com.rinos.app.backend.module.identity.entity;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationProofStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationProofTypeEnum;
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
 * Evidência não recuperável e de uso único vinculada a um fluxo de autenticação.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Entity
@Table(
    name = "identity_authenticationProof",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_identity_authentication_proof_active",
        columnNames = {"idAuthenticationFlow", "type", "activeMarker"}),
    indexes = @Index(
        name = "idx_identity_authentication_proof_expiry",
        columnList = "status, expiresAt"))
public class AuthenticationProofEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "idAuthenticationFlow", nullable = false)
  private AuthenticationFlowEntity flow;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 32)
  private AuthenticationProofTypeEnum type;

  @Column(name = "proofDigest", nullable = false, length = 96, columnDefinition = "VARBINARY(96)")
  private byte[] proofDigest;

  @Column(name = "keyVersion", length = 32)
  private String keyVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private AuthenticationProofStatusEnum status;

  @Column(name = "activeMarker")
  private Boolean activeMarker;

  @Column(name = "attemptCount", nullable = false)
  private int attemptCount;

  @Column(name = "issuedAt", nullable = false)
  private Instant issuedAt;

  @Column(name = "expiresAt", nullable = false)
  private Instant expiresAt;

  @Column(name = "usedAt")
  private Instant usedAt;

  @Column(name = "invalidatedAt")
  private Instant invalidatedAt;

  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updatedAt", nullable = false, insertable = false, updatable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  protected AuthenticationProofEntity() {
  }

  /**
   * Cria uma prova aberta contendo somente seu digest protegido.
   *
   * @throws IllegalArgumentException quando digest, versão ou intervalo forem inválidos
   */
  public AuthenticationProofEntity(
      AuthenticationFlowEntity flow,
      AuthenticationProofTypeEnum type,
      byte[] proofDigest,
      String keyVersion,
      Instant issuedAt,
      Instant expiresAt) {
    this.flow = Objects.requireNonNull(flow, "flow must not be null");
    this.type = Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(proofDigest, "proofDigest must not be null");
    if (proofDigest.length == 0 || proofDigest.length > 96) {
      throw new IllegalArgumentException("proofDigest length must be between 1 and 96 bytes");
    }
    this.proofDigest = Arrays.copyOf(proofDigest, proofDigest.length);
    if (keyVersion != null && (keyVersion.isBlank() || keyVersion.length() > 32)) {
      throw new IllegalArgumentException("keyVersion must be null or contain 1 to 32 characters");
    }
    this.keyVersion = keyVersion;
    this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    if (!expiresAt.isAfter(issuedAt)) {
      throw new IllegalArgumentException("expiresAt must be after issuedAt");
    }
    status = AuthenticationProofStatusEnum.OPEN;
    activeMarker = Boolean.TRUE;
  }

  public Long getId() {
    return id;
  }

  public AuthenticationFlowEntity getFlow() {
    return flow;
  }

  public AuthenticationProofTypeEnum getType() {
    return type;
  }

  public byte[] getProofDigest() {
    return Arrays.copyOf(proofDigest, proofDigest.length);
  }

  public String getKeyVersion() {
    return keyVersion;
  }

  public AuthenticationProofStatusEnum getStatus() {
    return status;
  }

  public Boolean getActiveMarker() {
    return activeMarker;
  }

  public int getAttemptCount() {
    return attemptCount;
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

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public long getVersion() {
    return version;
  }

  /** Marca a prova aberta como consumida exatamente uma vez. */
  public void markUsed(Instant occurredAt) {
    requireOpen();
    usedAt = requireNotBeforeIssue(occurredAt);
    close(AuthenticationProofStatusEnum.USED);
  }

  /** Invalida uma prova ainda aberta. */
  public void invalidate(Instant occurredAt) {
    requireOpen();
    invalidatedAt = requireNotBeforeIssue(occurredAt);
    close(AuthenticationProofStatusEnum.INVALIDATED);
  }

  /** Expira logicamente uma prova ainda aberta. */
  public void expire(Instant occurredAt) {
    requireOpen();
    invalidatedAt = requireNotBeforeIssue(occurredAt);
    close(AuthenticationProofStatusEnum.EXPIRED);
  }

  /** Registra uma tentativa rejeitada sem alterar o estado da prova. */
  public void registerAttempt() {
    requireOpen();
    if (attemptCount == Integer.MAX_VALUE) {
      throw new IllegalStateException("attemptCount limit reached");
    }
    attemptCount++;
  }

  private void close(AuthenticationProofStatusEnum terminalStatus) {
    status = terminalStatus;
    activeMarker = null;
  }

  private void requireOpen() {
    if (status != AuthenticationProofStatusEnum.OPEN) {
      throw new IllegalStateException("authentication proof is not open");
    }
  }

  private Instant requireNotBeforeIssue(Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    if (occurredAt.isBefore(issuedAt)) {
      throw new IllegalArgumentException("occurredAt must not be before issuedAt");
    }
    return occurredAt;
  }
}
