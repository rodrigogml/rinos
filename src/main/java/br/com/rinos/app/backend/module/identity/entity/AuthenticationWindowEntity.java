package br.com.rinos.app.backend.module.identity.entity;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationWindowOperationEnum;
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
 * Janela antifraude por MAC do identificador, sem conservar o e-mail normalizado.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Entity
@Table(
    name = "security_authenticationWindow",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_security_authentication_window_active",
        columnNames = {"identifierDigest", "keyVersion", "operation", "activeMarker"}),
    indexes = @Index(
        name = "idx_security_authentication_window_expiry",
        columnList = "windowEndsAt"))
public class AuthenticationWindowEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "identifierDigest", nullable = false, length = 32,
      columnDefinition = "BINARY(32)")
  private byte[] identifierDigest;

  @Column(name = "keyVersion", nullable = false, length = 32)
  private String keyVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "operation", nullable = false, length = 32)
  private AuthenticationWindowOperationEnum operation;

  @Column(name = "windowStartedAt", nullable = false)
  private Instant windowStartedAt;

  @Column(name = "windowEndsAt", nullable = false)
  private Instant windowEndsAt;

  @Column(name = "failureCount", nullable = false)
  private int failureCount;

  @Column(name = "turnstileRequiredUntil")
  private Instant turnstileRequiredUntil;

  @Column(name = "activeMarker")
  private Boolean activeMarker;

  @Column(name = "createdAt", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updatedAt", nullable = false, insertable = false, updatable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  /** Construtor reservado ao provedor JPA. */
  protected AuthenticationWindowEntity() {
  }

  /** Cria uma janela ativa vazia para a chave protegida informada. */
  public AuthenticationWindowEntity(
      byte[] identifierDigest,
      String keyVersion,
      AuthenticationWindowOperationEnum operation,
      Instant windowStartedAt,
      Instant windowEndsAt) {
    if (identifierDigest == null || identifierDigest.length != 32) {
      throw new IllegalArgumentException("identifierDigest must contain 32 bytes");
    }
    if (keyVersion == null || keyVersion.isBlank() || keyVersion.length() > 32) {
      throw new IllegalArgumentException("keyVersion is invalid");
    }
    this.identifierDigest = Arrays.copyOf(identifierDigest, identifierDigest.length);
    this.keyVersion = keyVersion;
    this.operation = Objects.requireNonNull(operation, "operation must not be null");
    this.windowStartedAt = Objects.requireNonNull(
        windowStartedAt, "windowStartedAt must not be null");
    this.windowEndsAt = Objects.requireNonNull(
        windowEndsAt, "windowEndsAt must not be null");
    if (!windowEndsAt.isAfter(windowStartedAt)) {
      throw new IllegalArgumentException("window end is invalid");
    }
    activeMarker = Boolean.TRUE;
  }

  /** @return identificador interno ou {@code null} antes da persistência */
  public Long getId() {
    return id;
  }

  /** @return cópia do MAC do identificador */
  public byte[] getIdentifierDigest() {
    return Arrays.copyOf(identifierDigest, identifierDigest.length);
  }

  /** @return versão da chave de MAC */
  public String getKeyVersion() {
    return keyVersion;
  }

  /** @return operação protegida */
  public AuthenticationWindowOperationEnum getOperation() {
    return operation;
  }

  /** @return início UTC da janela */
  public Instant getWindowStartedAt() {
    return windowStartedAt;
  }

  /** @return fim UTC da janela */
  public Instant getWindowEndsAt() {
    return windowEndsAt;
  }

  /** @return falhas acumuladas */
  public int getFailureCount() {
    return failureCount;
  }

  /** @return fim da exigência de Turnstile ou {@code null} */
  public Instant getTurnstileRequiredUntil() {
    return turnstileRequiredUntil;
  }

  /** @return {@code TRUE} enquanto ativa; {@code null} no histórico */
  public Boolean getActiveMarker() {
    return activeMarker;
  }

  /** Incrementa a falha e renova o Turnstile quando o limite foi alcançado. */
  public void registerFailure(int threshold, Instant requiredUntil) {
    if (activeMarker == null || failureCount == Integer.MAX_VALUE) {
      throw new IllegalStateException("window is not writable");
    }
    if (threshold <= 0) {
      throw new IllegalArgumentException("threshold must be positive");
    }
    failureCount++;
    if (failureCount >= threshold) {
      turnstileRequiredUntil = Objects.requireNonNull(
          requiredUntil, "requiredUntil must not be null");
    }
  }

  /** Encerra idempotentemente a janela, liberando a chave para a próxima. */
  public void close() {
    activeMarker = null;
  }
}
