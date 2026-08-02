package br.com.rinos.app.backend.module.identity.entity;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

import br.com.rinos.app.backend.module.identity.enums.PasswordRecoveryStatusEnum;
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
 * Persiste somente a evidência não recuperável de uma redefinição de senha.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-02
 */
@Entity
@Table(
    name = "identity_passwordRecovery",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_identity_password_recovery_token_hash",
        columnNames = "tokenHash"),
    indexes = {
        @Index(
            name = "idx_identity_password_recovery_open",
            columnList = "idUser, status, issuedAt"),
        @Index(
            name = "idx_identity_password_recovery_retention",
            columnList = "updatedAt")
    })
public class PasswordRecoveryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "idUser", nullable = false)
  private UserEntity user;

  @Column(name = "tokenHash", nullable = false, length = 32, columnDefinition = "BINARY(32)")
  private byte[] tokenHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private PasswordRecoveryStatusEnum status;

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

  /** Construtor reservado ao provedor JPA. */
  protected PasswordRecoveryEntity() {
  }

  /**
   * Cria uma prova aberta a partir do hash SHA-256.
   *
   * @param user usuário proprietário
   * @param tokenHash hash não recuperável
   * @param issuedAt emissão UTC
   * @param expiresAt expiração UTC
   */
  public PasswordRecoveryEntity(
      UserEntity user,
      byte[] tokenHash,
      Instant issuedAt,
      Instant expiresAt) {
    this.user = Objects.requireNonNull(user, "user must not be null");
    this.tokenHash = Arrays.copyOf(
        Objects.requireNonNull(tokenHash, "tokenHash must not be null"),
        tokenHash.length);
    this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    status = PasswordRecoveryStatusEnum.OPEN;
  }

  /** @return identificador persistente ou {@code null} */
  public Long getId() { return id; }

  /** @return usuário proprietário */
  public UserEntity getUser() { return user; }

  /** @return cópia do hash SHA-256 */
  public byte[] getTokenHash() { return Arrays.copyOf(tokenHash, tokenHash.length); }

  /** @return estado persistente */
  public PasswordRecoveryStatusEnum getStatus() { return status; }

  /** @param status novo estado validado pelo serviço */
  public void setStatus(PasswordRecoveryStatusEnum status) {
    this.status = Objects.requireNonNull(status, "status must not be null");
  }

  /** @return emissão UTC */
  public Instant getIssuedAt() { return issuedAt; }

  /** @return expiração UTC */
  public Instant getExpiresAt() { return expiresAt; }

  /** @return consumo UTC ou {@code null} */
  public Instant getUsedAt() { return usedAt; }

  /** @param usedAt instante UTC do consumo */
  public void setUsedAt(Instant usedAt) {
    this.usedAt = Objects.requireNonNull(usedAt, "usedAt must not be null");
  }

  /** @return invalidação UTC ou {@code null} */
  public Instant getInvalidatedAt() { return invalidatedAt; }

  /** @param invalidatedAt instante UTC da invalidação */
  public void setInvalidatedAt(Instant invalidatedAt) {
    this.invalidatedAt = Objects.requireNonNull(invalidatedAt, "invalidatedAt must not be null");
  }

  /** @return criação produzida pelo banco */
  public Instant getCreatedAt() { return createdAt; }

  /** @return última atualização produzida pelo banco */
  public Instant getUpdatedAt() { return updatedAt; }

  /** @return versão otimista */
  public long getVersion() { return version; }
}
