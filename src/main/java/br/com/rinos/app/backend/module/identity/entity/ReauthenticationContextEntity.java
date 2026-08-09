package br.com.rinos.app.backend.module.identity.entity;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.backend.module.identity.enums.ReauthenticationOperationEnum;
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

/**
 * Vincula uma continuação opaca de reautenticação à sessão e operação originais.
 *
 * <p>O lifecycle e o consumo único permanecem no fluxo. Este contexto é imutável e impede
 * que uma prova emitida para uma sessão ou operação seja reaproveitada em outra.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Entity
@Table(
    name = "identity_reauthenticationContext",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_identity_reauthentication_context_flow",
        columnNames = "idAuthenticationFlow"),
    indexes = @Index(
        name = "idx_identity_reauthentication_context_session",
        columnList = "idAuthSession"))
public class ReauthenticationContextEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "idAuthenticationFlow", nullable = false)
  private AuthenticationFlowEntity authenticationFlow;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "idAuthSession", nullable = false)
  private AuthSessionEntity authSession;

  @Enumerated(EnumType.STRING)
  @Column(name = "operation", nullable = false, length = 48)
  private ReauthenticationOperationEnum operation;

  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  /** Construtor reservado ao provedor JPA. */
  protected ReauthenticationContextEntity() {
  }

  /** Cria um vínculo imutável com valores previamente validados. */
  public ReauthenticationContextEntity(
      AuthenticationFlowEntity authenticationFlow,
      AuthSessionEntity authSession,
      ReauthenticationOperationEnum operation) {
    this.authenticationFlow = Objects.requireNonNull(
        authenticationFlow, "authenticationFlow must not be null");
    this.authSession = Objects.requireNonNull(authSession, "authSession must not be null");
    this.operation = Objects.requireNonNull(operation, "operation must not be null");
  }

  /** @return identificador interno ou {@code null} antes da persistência */
  public Long getId() {
    return id;
  }

  /** @return fluxo opaco proprietário */
  public AuthenticationFlowEntity getAuthenticationFlow() {
    return authenticationFlow;
  }

  /** @return sessão que solicitou a operação */
  public AuthSessionEntity getAuthSession() {
    return authSession;
  }

  /** @return operação do catálogo fechado */
  public ReauthenticationOperationEnum getOperation() {
    return operation;
  }

  /** @return instante de criação produzido pelo banco */
  public Instant getCreatedAt() {
    return createdAt;
  }
}
