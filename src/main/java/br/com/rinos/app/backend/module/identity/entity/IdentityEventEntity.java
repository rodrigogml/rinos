package br.com.rinos.app.backend.module.identity.entity;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Evento append-only do ciclo de identidade, sem PII, endereço ou evidência secreta.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Entity
@Table(name = "identity_event")
public class IdentityEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "idUser")
  private UserEntity user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "idRegistration")
  private RegistrationEntity registration;

  @Column(name = "correlationId", nullable = false, columnDefinition = "BINARY(16)")
  private byte[] correlationId;

  @Enumerated(EnumType.STRING)
  @Column(name = "eventType", nullable = false, length = 48)
  private IdentityEventTypeEnum eventType;

  @Column(name = "previousStatus", length = 32)
  private String previousStatus;

  @Column(name = "newStatus", length = 32)
  private String newStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "originType", nullable = false, length = 32)
  private IdentityTransitionOriginEnum originType;

  @Column(name = "reason", length = 255)
  private String reason;

  @Column(name = "occurredAt", nullable = false)
  private Instant occurredAt;

  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  /**
   * Construtor reservado ao provedor JPA.
   */
  protected IdentityEventEntity() {
  }

  /**
   * Cria um evento já sanitizado pelo serviço de auditoria.
   *
   * @param user identidade opcional
   * @param registration cadastro opcional
   * @param correlationId UUID convertido para 16 bytes
   * @param eventType tipo estável do evento
   * @param previousStatus estado anterior opcional
   * @param newStatus novo estado opcional
   * @param originType origem técnica
   * @param reason código de motivo opcional
   * @param occurredAt instante UTC
   */
  public IdentityEventEntity(
      UserEntity user,
      RegistrationEntity registration,
      byte[] correlationId,
      IdentityEventTypeEnum eventType,
      String previousStatus,
      String newStatus,
      IdentityTransitionOriginEnum originType,
      String reason,
      Instant occurredAt) {
    this.user = user;
    this.registration = registration;
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    if (correlationId.length != 16) {
      throw new IllegalArgumentException("correlationId must contain exactly 16 bytes");
    }
    this.correlationId = Arrays.copyOf(correlationId, correlationId.length);
    this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
    this.previousStatus = previousStatus;
    this.newStatus = newStatus;
    this.originType = Objects.requireNonNull(originType, "originType must not be null");
    this.reason = reason;
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
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
   * Retorna a identidade associada.
   *
   * @return usuário ou {@code null} em tombstone
   */
  public UserEntity getUser() {
    return user;
  }

  /**
   * Retorna o cadastro associado.
   *
   * @return cadastro ou {@code null} em tombstone
   */
  public RegistrationEntity getRegistration() {
    return registration;
  }

  /**
   * Retorna cópia da correlação binária.
   *
   * @return UUID em 16 bytes
   */
  public byte[] getCorrelationId() {
    return Arrays.copyOf(correlationId, correlationId.length);
  }

  /**
   * Retorna o tipo do evento.
   *
   * @return tipo estável
   */
  public IdentityEventTypeEnum getEventType() {
    return eventType;
  }

  /**
   * Retorna o estado anterior.
   *
   * @return código de estado ou {@code null}
   */
  public String getPreviousStatus() {
    return previousStatus;
  }

  /**
   * Retorna o novo estado.
   *
   * @return código de estado ou {@code null}
   */
  public String getNewStatus() {
    return newStatus;
  }

  /**
   * Retorna a origem técnica.
   *
   * @return origem da ação
   */
  public IdentityTransitionOriginEnum getOriginType() {
    return originType;
  }

  /**
   * Retorna o motivo técnico sanitizado.
   *
   * @return código ou {@code null}
   */
  public String getReason() {
    return reason;
  }

  /**
   * Retorna o instante do evento.
   *
   * @return instante UTC
   */
  public Instant getOccurredAt() {
    return occurredAt;
  }

  /**
   * Retorna o instante de persistência.
   *
   * @return instante UTC produzido pelo MySQL
   */
  public Instant getCreatedAt() {
    return createdAt;
  }
}
