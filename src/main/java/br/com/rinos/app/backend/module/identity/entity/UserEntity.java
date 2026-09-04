package br.com.rinos.app.backend.module.identity.entity;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.GlobalActorRoleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * Representa a identidade global estável de um usuário, sem vínculo implícito com tenant.
 *
 * <p>A entidade preserva somente estrutura persistente. As regras de transição pertencem aos
 * serviços de lifecycle do módulo.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Entity
@Table(
    name = "identity_user",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_identity_user_normalized_email",
        columnNames = "normalizedEmail"),
    indexes = @Index(
        name = "idx_identity_user_status_created",
        columnList = "status, createdAt"))
public class UserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "email", nullable = false, length = 320)
  private String email;

  @Column(name = "normalizedEmail", nullable = false, length = 320)
  private String normalizedEmail;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private UserStatusEnum status;

  @Enumerated(EnumType.STRING)
  @Column(name = "globalActorRole", nullable = false, length = 32)
  private GlobalActorRoleType globalActorRole;

  @Column(name = "activatedAt")
  private Instant activatedAt;

  @Column(name = "blockedAt")
  private Instant blockedAt;

  @Column(name = "deactivatedAt")
  private Instant deactivatedAt;

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
  protected UserEntity() {
  }

  /**
   * Cria uma identidade ainda não persistida com os valores já normalizados pelo serviço.
   *
   * @param email e-mail preservado para apresentação
   * @param normalizedEmail chave normalizada para comparação
   * @param status estado inicial explícito
   * @throws NullPointerException quando qualquer argumento é nulo
   */
  public UserEntity(String email, String normalizedEmail, UserStatusEnum status) {
    this.email = Objects.requireNonNull(email, "email must not be null");
    this.normalizedEmail =
        Objects.requireNonNull(normalizedEmail, "normalizedEmail must not be null");
    this.status = Objects.requireNonNull(status, "status must not be null");
    this.globalActorRole = GlobalActorRoleType.USER;
  }

  /**
   * Retorna o identificador interno global.
   *
   * @return identidade gerada pelo banco ou {@code null} antes da persistência
   */
  public Long getId() {
    return id;
  }

  /**
   * Retorna o e-mail preservado para apresentação.
   *
   * @return e-mail sem espaços externos
   */
  public String getEmail() {
    return email;
  }

  /**
   * Retorna a chave normalizada do e-mail.
   *
   * @return e-mail normalizado
   */
  public String getNormalizedEmail() {
    return normalizedEmail;
  }

  /**
   * Retorna o estado persistente atual.
   *
   * @return estado da identidade
   */
  public UserStatusEnum getStatus() {
    return status;
  }

  public GlobalActorRoleType getGlobalActorRole() {
    return globalActorRole;
  }

  /** Identifica o ator para apresentação e auditoria, sem efeito autorizativo. */
  public void identifyAsSystemAdministrator() {
    globalActorRole = GlobalActorRoleType.SYSTEM_ADMINISTRATOR;
  }

  /**
   * Aplica o estado previamente validado pelo serviço de lifecycle.
   *
   * @param status novo estado obrigatório
   * @throws NullPointerException quando o estado é nulo
   */
  public void setStatus(UserStatusEnum status) {
    this.status = Objects.requireNonNull(status, "status must not be null");
  }

  /**
   * Retorna o primeiro instante de ativação.
   *
   * @return instante UTC ou {@code null} enquanto nunca ativado
   */
  public Instant getActivatedAt() {
    return activatedAt;
  }

  /**
   * Registra o primeiro instante de ativação validado pelo lifecycle.
   *
   * @param activatedAt instante UTC obrigatório
   * @throws NullPointerException quando o instante é nulo
   */
  public void setInitialActivatedAt(Instant activatedAt) {
    this.activatedAt = Objects.requireNonNull(activatedAt, "activatedAt must not be null");
  }

  /**
   * Retorna o instante do bloqueio vigente.
   *
   * @return instante UTC ou {@code null}
   */
  public Instant getBlockedAt() {
    return blockedAt;
  }

  /**
   * Retorna o instante da desativação vigente.
   *
   * @return instante UTC ou {@code null}
   */
  public Instant getDeactivatedAt() {
    return deactivatedAt;
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
