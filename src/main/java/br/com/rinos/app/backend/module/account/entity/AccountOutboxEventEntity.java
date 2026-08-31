package br.com.rinos.app.backend.module.account.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import br.com.rinos.app.backend.module.account.enums.AccountOutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Representa uma intenção durável de integrar a criação de conta a um módulo externo.
 *
 * <p>O evento é reclamado por lease antes da chamada externa. Seu payload é deliberadamente
 * não autoritativo: o consumidor reconstrói o contexto a partir do agregado global e da intenção
 * persistida, impedindo que conteúdo serializado antigo ou adulterado determine identidades.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-31
 */
@Entity
@Table(name = "account_outboxEvent")
public class AccountOutboxEventEntity {

  /** Tipo estável do evento que solicita a reserva de storage do tenant. */
  public static final String PROVISIONING_REQUESTED_EVENT_TYPE = "ACCOUNT_PROVISIONING_REQUESTED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "idAccountOutboxEvent")
  private Long id;

  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "eventId", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
  private UUID eventId;

  @Column(name = "aggregateType", nullable = false, updatable = false)
  private String aggregateType;

  @Column(name = "aggregateId", nullable = false, updatable = false)
  private Long aggregateId;

  @Column(name = "eventType", nullable = false, updatable = false)
  private String eventType;

  @Column(name = "payload", columnDefinition = "JSON", nullable = false, updatable = false)
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private AccountOutboxStatus status;

  @Column(name = "attemptCount", nullable = false)
  private int attemptCount;

  @Column(name = "nextAttemptAt")
  private Instant nextAttemptAt;

  @Column(name = "leaseOwner", length = 100)
  private String leaseOwner;

  @Column(name = "leaseUntil")
  private Instant leaseUntil;

  @Column(name = "publishedAt")
  private Instant publishedAt;

  @Column(name = "createdAt", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updatedAt", insertable = false, updatable = false)
  private Instant updatedAt;

  protected AccountOutboxEventEntity() {
  }

  /**
   * Cria um evento pendente associado a uma conta já persistida.
   *
   * @param eventId identificador técnico imutável do evento
   * @param aggregateId identificador interno da conta proprietária
   * @param eventType tipo estável do evento
   * @param payload conteúdo não autoritativo, sempre JSON válido
   */
  public AccountOutboxEventEntity(UUID eventId, Long aggregateId, String eventType, String payload) {
    this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
    this.aggregateType = "ACCOUNT";
    this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId must not be null");
    this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
    this.payload = Objects.requireNonNull(payload, "payload must not be null");
    status = AccountOutboxStatus.PENDING;
  }

  /** @return identificador interno persistente */
  public Long getId() {
    return id;
  }

  /** @return UUID técnico estável do evento */
  public UUID getEventId() {
    return eventId;
  }

  /** @return tipo interno do agregado, sempre {@code ACCOUNT} neste módulo */
  public String getAggregateType() {
    return aggregateType;
  }

  /** @return identificador interno da conta dona do evento */
  public Long getAggregateId() {
    return aggregateId;
  }

  /** @return tipo estável do evento */
  public String getEventType() {
    return eventType;
  }

  /** @return estado atual da entrega durável */
  public AccountOutboxStatus getStatus() {
    return status;
  }

  /** @return quantidade de leases já adquiridos */
  public int getAttemptCount() {
    return attemptCount;
  }

  /** @return próximo instante elegível, ou {@code null} quando imediato/terminal */
  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  /** @return instância que possui o lease, ou {@code null} quando livre */
  public String getLeaseOwner() {
    return leaseOwner;
  }

  /** @return expiração do lease, ou {@code null} quando livre */
  public Instant getLeaseUntil() {
    return leaseUntil;
  }

  /**
   * Reclama o evento em transação que já mantém lock pessimista da fila.
   *
   * @param owner identificador da instância eleita
   * @param until instante UTC de expiração do lease
   */
  public void claim(String owner, Instant until) {
    if (owner == null || owner.isBlank() || until == null) {
      throw new IllegalArgumentException("outbox claim is invalid");
    }
    status = AccountOutboxStatus.PROCESSING;
    attemptCount++;
    nextAttemptAt = null;
    leaseOwner = owner;
    leaseUntil = until;
  }

  /**
   * Confirma que a intenção foi aceita duravelmente pelo módulo consumidor.
   *
   * @param occurredAt instante UTC da confirmação
   */
  public void publish(Instant occurredAt) {
    status = AccountOutboxStatus.PUBLISHED;
    nextAttemptAt = null;
    leaseOwner = null;
    leaseUntil = null;
    publishedAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }

  /**
   * Libera o evento para nova tentativa após indisponibilidade confirmada.
   *
   * @param nextAttempt instante UTC no qual a nova tentativa se torna elegível
   */
  public void scheduleRetry(Instant nextAttempt) {
    status = AccountOutboxStatus.PENDING;
    nextAttemptAt = Objects.requireNonNull(nextAttempt, "nextAttempt must not be null");
    leaseOwner = null;
    leaseUntil = null;
  }

  /** Marca o evento terminalmente rejeitado, mantendo-o para auditoria. */
  public void fail() {
    status = AccountOutboxStatus.FAILED;
    nextAttemptAt = null;
    leaseOwner = null;
    leaseUntil = null;
  }

  /**
   * Confirma que o lease ainda pertence à mesma instância e não expirou.
   *
   * @param owner instância que pretende confirmar o resultado
   * @param now instante UTC atual
   * @return {@code true} somente para o claim vigente correspondente
   */
  public boolean hasActiveLease(String owner, Instant now) {
    return status == AccountOutboxStatus.PROCESSING
        && Objects.equals(leaseOwner, owner)
        && leaseUntil != null
        && leaseUntil.isAfter(Objects.requireNonNull(now, "now must not be null"));
  }
}
