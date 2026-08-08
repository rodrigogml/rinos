package br.com.rinos.app.backend.module.identity.entity;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.backend.module.identity.enums.LocalCredentialStatusEnum;
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
 * Armazena exclusivamente o hash não recuperável da credencial local de uma identidade.
 *
 * <p>A entidade permanece restrita ao backend e nunca deve atravessar facade, API ou UI.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Entity
@Table(
    name = "identity_localCredential",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_identity_local_credential_user",
        columnNames = "idUser"))
public class LocalCredentialEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "idUser", nullable = false)
  private UserEntity user;

  @Column(name = "passwordHash", nullable = false, length = 255)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private LocalCredentialStatusEnum status;

  @Column(name = "invalidatedAt")
  private Instant invalidatedAt;

  @Column(name = "passwordChangedAt", nullable = false)
  private Instant passwordChangedAt;

  @Column(name = "compromisedAt")
  private Instant compromisedAt;

  @Column(name = "lastUsedAt")
  private Instant lastUsedAt;

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
  protected LocalCredentialEntity() {
  }

  /**
   * Cria uma credencial ainda não persistida a partir de um hash já produzido.
   *
   * @param user identidade proprietária
   * @param passwordHash hash codificado, nunca a senha em claro
   * @throws NullPointerException quando qualquer argumento é nulo
   */
  public LocalCredentialEntity(UserEntity user, String passwordHash) {
    this(user, passwordHash, Instant.now());
  }

  /**
   * Cria uma credencial com o instante de substituição decidido pelo serviço.
   *
   * @param user identidade proprietária
   * @param passwordHash hash codificado, nunca a senha em claro
   * @param passwordChangedAt instante UTC da criação ou substituição
   * @throws NullPointerException quando qualquer argumento é nulo
   */
  public LocalCredentialEntity(UserEntity user, String passwordHash, Instant passwordChangedAt) {
    this.user = Objects.requireNonNull(user, "user must not be null");
    this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
    this.passwordChangedAt = Objects.requireNonNull(
        passwordChangedAt,
        "passwordChangedAt must not be null");
    status = LocalCredentialStatusEnum.ACTIVE;
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
   * Retorna a identidade proprietária.
   *
   * @return usuário global
   */
  public UserEntity getUser() {
    return user;
  }

  /**
   * Retorna o hash somente para serviços internos de autenticação.
   *
   * @return hash codificado, nunca a senha original
   */
  public String getPasswordHash() {
    return passwordHash;
  }

  /**
   * Substitui o hash conforme decisão previamente validada pelo serviço.
   *
   * @param passwordHash novo hash codificado
   * @throws NullPointerException quando o hash é nulo
   */
  public void setPasswordHash(String passwordHash) {
    this.passwordHash =
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");
  }

  /**
   * Retorna o estado atual da credencial.
   *
   * @return estado persistente
   */
  public LocalCredentialStatusEnum getStatus() {
    return status;
  }

  /**
   * Aplica o estado previamente validado pelo serviço.
   *
   * @param status novo estado obrigatório
   */
  public void setStatus(LocalCredentialStatusEnum status) {
    this.status = Objects.requireNonNull(status, "status must not be null");
  }

  /**
   * Retorna o instante da invalidação.
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
   * Limpa a invalidação quando o serviço substitui uma credencial ainda reutilizável.
   */
  public void clearInvalidatedAt() {
    invalidatedAt = null;
  }

  /**
   * Retorna o instante da última criação ou substituição da senha.
   *
   * @return instante UTC obrigatório
   */
  public Instant getPasswordChangedAt() {
    return passwordChangedAt;
  }

  /**
   * Registra uma substituição válida e remove eventual marca de comprometimento.
   *
   * @param passwordChangedAt instante UTC obrigatório
   */
  public void markPasswordChanged(Instant passwordChangedAt) {
    this.passwordChangedAt = Objects.requireNonNull(
        passwordChangedAt,
        "passwordChangedAt must not be null");
    compromisedAt = null;
  }

  /**
   * Retorna quando a credencial passou a ser recusada por comprometimento.
   *
   * @return instante UTC ou {@code null}
   */
  public Instant getCompromisedAt() {
    return compromisedAt;
  }

  /**
   * Marca a credencial para substituição segura antes de novo login por senha.
   *
   * @param compromisedAt instante UTC obrigatório
   */
  public void setCompromisedAt(Instant compromisedAt) {
    this.compromisedAt = Objects.requireNonNull(compromisedAt, "compromisedAt must not be null");
  }

  /**
   * Retorna o último uso autenticado da credencial.
   *
   * @return instante UTC ou {@code null}
   */
  public Instant getLastUsedAt() {
    return lastUsedAt;
  }

  /**
   * Registra o uso somente depois de autenticação válida.
   *
   * @param lastUsedAt instante UTC obrigatório
   */
  public void setLastUsedAt(Instant lastUsedAt) {
    this.lastUsedAt = Objects.requireNonNull(lastUsedAt, "lastUsedAt must not be null");
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
