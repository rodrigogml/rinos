package br.com.rinos.app.backend.module.identity.entity;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
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
import jakarta.persistence.UniqueConstraint;

/**
 * Evidência sanitizada de um método que contribuiu para a garantia da sessão.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Entity
@Table(
    name = "identity_authSessionMethod",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_identity_auth_session_method_order",
        columnNames = {"idAuthSession", "factorOrder"}))
public class AuthSessionMethodEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "idAuthSession", nullable = false)
  private AuthSessionEntity session;

  @Enumerated(EnumType.STRING)
  @Column(name = "method", nullable = false, length = 32)
  private AuthenticationMethodEnum method;

  @Column(name = "factorOrder", nullable = false)
  private short factorOrder;

  @Column(name = "verifiedAt", nullable = false)
  private Instant verifiedAt;

  @Column(name = "userVerification")
  private Boolean userVerification;

  /** Construtor reservado ao provedor JPA. */
  protected AuthSessionMethodEntity() {
  }

  /** Cria a evidência persistente na ordem informada. */
  public AuthSessionMethodEntity(
      AuthSessionEntity session,
      AuthenticationMethodEnum method,
      int factorOrder,
      Instant verifiedAt,
      Boolean userVerification) {
    this.session = Objects.requireNonNull(session, "session must not be null");
    this.method = Objects.requireNonNull(method, "method must not be null");
    if (factorOrder <= 0 || factorOrder > Short.MAX_VALUE) {
      throw new IllegalArgumentException("factorOrder is invalid");
    }
    this.factorOrder = (short) factorOrder;
    this.verifiedAt = Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
    this.userVerification = userVerification;
  }

  /** @return identificador interno ou {@code null} antes da persistência */
  public Long getId() {
    return id;
  }

  /** @return sessão proprietária */
  public AuthSessionEntity getSession() {
    return session;
  }

  /** @return método comprovado */
  public AuthenticationMethodEnum getMethod() {
    return method;
  }

  /** @return ordem da comprovação */
  public short getFactorOrder() {
    return factorOrder;
  }

  /** @return instante UTC da comprovação */
  public Instant getVerifiedAt() {
    return verifiedAt;
  }

  /** @return verificação local ou {@code null} quando inaplicável */
  public Boolean getUserVerification() {
    return userVerification;
  }
}
