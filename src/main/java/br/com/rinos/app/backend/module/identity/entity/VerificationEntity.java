package br.com.rinos.app.backend.module.identity.entity;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

import br.com.rinos.app.backend.module.identity.enums.VerificationPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationStatusEnum;
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
 * Armazena a evidência não recuperável de uma comprovação de cadastro.
 *
 * <p>Somente o SHA-256 do token é persistido. O array é copiado em todas as fronteiras para que
 * chamadores não alterem a chave mantida pela entidade.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Entity
@Table(
    name = "identity_verification",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_identity_verification_token_hash",
        columnNames = "tokenHash"),
    indexes = {
        @Index(
            name = "idx_identity_verification_open",
            columnList = "idRegistration, purpose, status, issuedAt"),
        @Index(
            name = "idx_identity_verification_expiry",
            columnList = "status, expiresAt")
    })
public class VerificationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "idRegistration", nullable = false)
  private RegistrationEntity registration;

  @Enumerated(EnumType.STRING)
  @Column(name = "purpose", nullable = false, length = 32)
  private VerificationPurposeEnum purpose;

  @Column(name = "tokenHash", nullable = false, length = 32, columnDefinition = "BINARY(32)")
  private byte[] tokenHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private VerificationStatusEnum status;

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

  /**
   * Construtor reservado ao provedor JPA.
   */
  protected VerificationEntity() {
  }

  /**
   * Cria uma comprovação aberta usando somente o hash da prova.
   *
   * @param registration cadastro proprietário
   * @param purpose finalidade exclusiva
   * @param tokenHash SHA-256 do token bruto
   * @param issuedAt instante UTC da emissão
   * @param expiresAt instante UTC do vencimento
   */
  public VerificationEntity(
      RegistrationEntity registration,
      VerificationPurposeEnum purpose,
      byte[] tokenHash,
      Instant issuedAt,
      Instant expiresAt) {
    this.registration =
        Objects.requireNonNull(registration, "registration must not be null");
    this.purpose = Objects.requireNonNull(purpose, "purpose must not be null");
    this.tokenHash = Arrays.copyOf(
        Objects.requireNonNull(tokenHash, "tokenHash must not be null"),
        tokenHash.length);
    this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    status = VerificationStatusEnum.OPEN;
  }

  /**
   * Retorna o identificador interno.
   *
   * @return identidade gerada pelo banco ou {@code null} antes da persistência
   */
  public Long getId() {
    return id;
  }

  /**
   * Retorna o cadastro proprietário.
   *
   * @return cadastro associado
   */
  public RegistrationEntity getRegistration() {
    return registration;
  }

  /**
   * Retorna a finalidade exclusiva.
   *
   * @return propósito da prova
   */
  public VerificationPurposeEnum getPurpose() {
    return purpose;
  }

  /**
   * Retorna uma cópia do hash para comparação interna.
   *
   * @return cópia do SHA-256 persistido
   */
  public byte[] getTokenHash() {
    return Arrays.copyOf(tokenHash, tokenHash.length);
  }

  /**
   * Retorna o estado atual.
   *
   * @return estado persistente
   */
  public VerificationStatusEnum getStatus() {
    return status;
  }

  /**
   * Aplica o estado previamente validado pelo serviço.
   *
   * @param status novo estado obrigatório
   */
  public void setStatus(VerificationStatusEnum status) {
    this.status = Objects.requireNonNull(status, "status must not be null");
  }

  /**
   * Retorna o instante de emissão.
   *
   * @return instante UTC
   */
  public Instant getIssuedAt() {
    return issuedAt;
  }

  /**
   * Retorna o limite de consumo.
   *
   * @return instante UTC
   */
  public Instant getExpiresAt() {
    return expiresAt;
  }

  /**
   * Retorna o instante de uso.
   *
   * @return instante UTC ou {@code null}
   */
  public Instant getUsedAt() {
    return usedAt;
  }

  /**
   * Registra o instante de uso definido pelo serviço.
   *
   * @param usedAt instante UTC obrigatório
   */
  public void setUsedAt(Instant usedAt) {
    this.usedAt = Objects.requireNonNull(usedAt, "usedAt must not be null");
  }

  /**
   * Retorna o instante de invalidação.
   *
   * @return instante UTC ou {@code null}
   */
  public Instant getInvalidatedAt() {
    return invalidatedAt;
  }

  /**
   * Registra o instante de invalidação definido pelo serviço.
   *
   * @param invalidatedAt instante UTC obrigatório
   */
  public void setInvalidatedAt(Instant invalidatedAt) {
    this.invalidatedAt =
        Objects.requireNonNull(invalidatedAt, "invalidatedAt must not be null");
  }

  /**
   * Retorna o instante de criação.
   *
   * @return instante UTC produzido pelo MySQL
   */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /**
   * Retorna o instante da última atualização.
   *
   * @return instante UTC produzido pelo MySQL
   */
  public Instant getUpdatedAt() {
    return updatedAt;
  }

  /**
   * Retorna a versão usada no controle otimista.
   *
   * @return versão persistente atual
   */
  public long getVersion() {
    return version;
  }
}
