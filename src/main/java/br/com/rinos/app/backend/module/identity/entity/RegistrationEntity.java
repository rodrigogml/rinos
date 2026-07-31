package br.com.rinos.app.backend.module.identity.entity;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.backend.module.identity.enums.RegistrationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusEnum;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * Representa o processo temporário 1:1 que conduz uma identidade pendente.
 *
 * <p>A entidade não contém credencial, comprovação ou aceite e não executa decisões de lifecycle.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Entity
@Table(
    name = "identity_registration",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_identity_registration_user",
        columnNames = "idUser"),
    indexes = @Index(
        name = "idx_identity_registration_status_expiry",
        columnList = "status, expiresAt"))
public class RegistrationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "idUser", nullable = false)
  private UserEntity user;

  @Enumerated(EnumType.STRING)
  @Column(name = "method", nullable = false, length = 24)
  private RegistrationMethodEnum method;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private RegistrationStatusEnum status;

  @Column(name = "expiresAt", nullable = false)
  private Instant expiresAt;

  @Column(name = "completedAt")
  private Instant completedAt;

  @Column(name = "cancelledAt")
  private Instant cancelledAt;

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
  protected RegistrationEntity() {
  }

  /**
   * Cria um processo ainda não persistido para uma identidade já preparada.
   *
   * @param user identidade global associada
   * @param method origem do cadastro
   * @param status estado inicial explícito
   * @param expiresAt limite absoluto do processo
   * @throws NullPointerException quando qualquer argumento é nulo
   */
  public RegistrationEntity(
      UserEntity user,
      RegistrationMethodEnum method,
      RegistrationStatusEnum status,
      Instant expiresAt) {
    this.user = Objects.requireNonNull(user, "user must not be null");
    this.method = Objects.requireNonNull(method, "method must not be null");
    this.status = Objects.requireNonNull(status, "status must not be null");
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
  }

  /**
   * Retorna o identificador interno do processo.
   *
   * @return identidade gerada pelo banco ou {@code null} antes da persistência
   */
  public Long getId() {
    return id;
  }

  /**
   * Retorna a identidade global associada.
   *
   * @return usuário proprietário do processo
   */
  public UserEntity getUser() {
    return user;
  }

  /**
   * Retorna a origem do cadastro.
   *
   * @return método local ou Google
   */
  public RegistrationMethodEnum getMethod() {
    return method;
  }

  /**
   * Retorna o estado atual do processo.
   *
   * @return estado do cadastro
   */
  public RegistrationStatusEnum getStatus() {
    return status;
  }

  /**
   * Aplica o estado previamente validado pelo serviço de lifecycle.
   *
   * @param status novo estado obrigatório
   * @throws NullPointerException quando o estado é nulo
   */
  public void setStatus(RegistrationStatusEnum status) {
    this.status = Objects.requireNonNull(status, "status must not be null");
  }

  /**
   * Retorna o limite imutável do processo.
   *
   * @return instante UTC de expiração
   */
  public Instant getExpiresAt() {
    return expiresAt;
  }

  /**
   * Retorna o instante da conclusão.
   *
   * @return instante UTC ou {@code null}
   */
  public Instant getCompletedAt() {
    return completedAt;
  }

  /**
   * Registra o instante de conclusão validado pelo lifecycle.
   *
   * @param completedAt instante UTC obrigatório
   * @throws NullPointerException quando o instante é nulo
   */
  public void setCompletedAt(Instant completedAt) {
    this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
  }

  /**
   * Retorna o instante do cancelamento.
   *
   * @return instante UTC ou {@code null}
   */
  public Instant getCancelledAt() {
    return cancelledAt;
  }

  /**
   * Registra o instante de cancelamento validado pelo lifecycle.
   *
   * @param cancelledAt instante UTC obrigatório
   * @throws NullPointerException quando o instante é nulo
   */
  public void setCancelledAt(Instant cancelledAt) {
    this.cancelledAt = Objects.requireNonNull(cancelledAt, "cancelledAt must not be null");
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
