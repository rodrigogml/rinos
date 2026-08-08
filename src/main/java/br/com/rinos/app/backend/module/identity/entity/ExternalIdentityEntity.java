package br.com.rinos.app.backend.module.identity.entity;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityProviderEnum;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityStatusEnum;
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
 * Representa o vínculo estável entre um usuário e uma identidade externa validada.
 *
 * <p>A entidade não possui e-mail, ID token, access token ou claims completos.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Entity
@Table(
    name = "identity_externalIdentity",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_identity_external_identity_issuer_subject",
        columnNames = {"issuer", "subject"}),
    indexes = @Index(name = "idx_identity_external_identity_user", columnList = "idUser"))
public class ExternalIdentityEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "idUser", nullable = false)
  private UserEntity user;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = false, length = 32)
  private ExternalIdentityProviderEnum provider;

  @Column(name = "issuer", nullable = false, length = 255)
  private String issuer;

  @Column(name = "subject", nullable = false, length = 255)
  private String subject;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private ExternalIdentityStatusEnum status;

  @Column(name = "verifiedAt", nullable = false)
  private Instant verifiedAt;

  @Column(name = "activatedAt")
  private Instant activatedAt;

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
  protected ExternalIdentityEntity() {
  }

  /**
   * Cria um vínculo pendente a partir de atributos já validados pelo provedor.
   *
   * @param user identidade proprietária
   * @param provider provedor reconhecido
   * @param issuer emissor validado
   * @param subject identificador imutável no emissor
   * @param verifiedAt instante UTC da validação criptográfica
   */
  public ExternalIdentityEntity(
      UserEntity user,
      ExternalIdentityProviderEnum provider,
      String issuer,
      String subject,
      Instant verifiedAt) {
    this.user = Objects.requireNonNull(user, "user must not be null");
    this.provider = Objects.requireNonNull(provider, "provider must not be null");
    this.issuer = Objects.requireNonNull(issuer, "issuer must not be null");
    this.subject = Objects.requireNonNull(subject, "subject must not be null");
    this.verifiedAt = Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
    status = ExternalIdentityStatusEnum.PENDING;
  }

  /**
   * Retorna o identificador interno.
   *
   * @return identidade gerada pelo banco ou {@code null}
   */
  public Long getId() {
    return id;
  }

  /**
   * Retorna o usuário proprietário.
   *
   * @return identidade global
   */
  public UserEntity getUser() {
    return user;
  }

  /**
   * Retorna o provedor externo.
   *
   * @return provedor reconhecido
   */
  public ExternalIdentityProviderEnum getProvider() {
    return provider;
  }

  /**
   * Retorna o emissor validado.
   *
   * @return issuer externo
   */
  public String getIssuer() {
    return issuer;
  }

  /**
   * Retorna o identificador estável no emissor.
   *
   * @return subject externo
   */
  public String getSubject() {
    return subject;
  }

  /**
   * Retorna o estado do vínculo.
   *
   * @return estado persistente
   */
  public ExternalIdentityStatusEnum getStatus() {
    return status;
  }

  /**
   * Aplica o estado previamente validado pelo serviço.
   *
   * @param status novo estado obrigatório
   */
  public void setStatus(ExternalIdentityStatusEnum status) {
    this.status = Objects.requireNonNull(status, "status must not be null");
  }

  /**
   * Retorna o instante da validação criptográfica.
   *
   * @return instante UTC
   */
  public Instant getVerifiedAt() {
    return verifiedAt;
  }

  /**
   * Retorna o instante da ativação.
   *
   * @return instante UTC ou {@code null}
   */
  public Instant getActivatedAt() {
    return activatedAt;
  }

  /**
   * Registra o instante de ativação definido pelo serviço.
   *
   * @param activatedAt instante UTC obrigatório
   */
  public void setActivatedAt(Instant activatedAt) {
    this.activatedAt = Objects.requireNonNull(activatedAt, "activatedAt must not be null");
  }

  /**
   * Retorna o último uso autenticado do vínculo externo.
   *
   * @return instante UTC ou {@code null}
   */
  public Instant getLastUsedAt() {
    return lastUsedAt;
  }

  /**
   * Registra o uso somente depois de validação e conclusão da autenticação.
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
   * Retorna a versão otimista.
   *
   * @return versão persistente
   */
  public long getVersion() {
    return version;
  }
}
