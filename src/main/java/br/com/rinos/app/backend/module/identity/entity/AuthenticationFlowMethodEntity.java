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
 * Método permitido e imutável de uma continuação de autenticação.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Entity
@Table(
    name = "identity_authenticationFlowMethod",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_identity_authentication_flow_method",
        columnNames = {"idAuthenticationFlow", "method"}))
public class AuthenticationFlowMethodEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "idAuthenticationFlow", nullable = false)
  private AuthenticationFlowEntity flow;

  @Enumerated(EnumType.STRING)
  @Column(name = "method", nullable = false, length = 32)
  private AuthenticationMethodEnum method;

  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  protected AuthenticationFlowMethodEntity() {
  }

  public AuthenticationFlowMethodEntity(
      AuthenticationFlowEntity flow,
      AuthenticationMethodEnum method) {
    this.flow = Objects.requireNonNull(flow, "flow must not be null");
    this.method = Objects.requireNonNull(method, "method must not be null");
  }

  public Long getId() {
    return id;
  }

  public AuthenticationFlowEntity getFlow() {
    return flow;
  }

  public AuthenticationMethodEnum getMethod() {
    return method;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
