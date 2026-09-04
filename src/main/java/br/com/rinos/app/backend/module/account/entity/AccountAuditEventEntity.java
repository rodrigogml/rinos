package br.com.rinos.app.backend.module.account.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Registra evidência minimizada de decisões humanas ou sistêmicas sobre uma conta.
 *
 * <p>O schema exige exatamente uma origem: usuário humano ou identificador sistêmico. Os dois
 * construtores preservam essa exclusividade e não recebem detalhes de infraestrutura.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-31
 */
@Entity
@Table(name = "account_auditEvent")
public class AccountAuditEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "idAccountAuditEvent")
  private Long id;

  @Column(name = "eventType", nullable = false, updatable = false)
  private String eventType;

  @Column(name = "idAccount", updatable = false)
  private Long accountId;

  @Column(name = "idTenant", updatable = false)
  private Long tenantId;

  @Column(name = "actorUserId", updatable = false)
  private Long actorUserId;

  @Column(name = "systemOrigin", updatable = false)
  private String systemOrigin;

  @Column(name = "correlationId", nullable = false, updatable = false)
  private String correlationId;

  @Column(name = "safeResultCode", nullable = false, updatable = false)
  private String safeResultCode;

  @Column(name = "details", columnDefinition = "JSON", updatable = false)
  private String details;

  @Column(name = "occurredAt", nullable = false, updatable = false)
  private Instant occurredAt;

  protected AccountAuditEventEntity() {
  }

  /**
   * Cria uma evidência associada a um ator humano autenticado.
   *
   * @param eventType tipo estável do evento
   * @param accountId conta relacionada, quando houver
   * @param tenantId tenant relacionado, quando houver
   * @param actorUserId ator humano responsável pela ação
   * @param correlationId correlação segura da solicitação
   * @param safeResultCode resultado seguro sem detalhe operacional
   * @param occurredAt instante UTC da ocorrência
   */
  public AccountAuditEventEntity(
      String eventType,
      Long accountId,
      Long tenantId,
      Long actorUserId,
      String correlationId,
      String safeResultCode,
      Instant occurredAt) {
    this.eventType = eventType;
    this.accountId = accountId;
    this.tenantId = tenantId;
    this.actorUserId = actorUserId;
    this.correlationId = correlationId;
    this.safeResultCode = safeResultCode;
    this.occurredAt = occurredAt;
  }

  /**
   * Cria uma evidência associada a uma origem automática identificável.
   *
   * @param eventType tipo estável do evento
   * @param accountId conta relacionada, quando houver
   * @param tenantId tenant relacionado, quando houver
   * @param systemOrigin identificador estável do componente automático
   * @param correlationId correlação segura da intenção
   * @param safeResultCode resultado seguro sem detalhe operacional
   * @param occurredAt instante UTC da ocorrência
   */
  public AccountAuditEventEntity(
      String eventType,
      Long accountId,
      Long tenantId,
      String systemOrigin,
      String correlationId,
      String safeResultCode,
      Instant occurredAt) {
    this.eventType = eventType;
    this.accountId = accountId;
    this.tenantId = tenantId;
    this.systemOrigin = systemOrigin;
    this.correlationId = correlationId;
    this.safeResultCode = safeResultCode;
    this.occurredAt = occurredAt;
  }

  /** @return identificador interno persistente da evidência */
  public Long getId() {
    return id;
  }
}
