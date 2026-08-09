package br.com.rinos.app.api.dto;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Transporta uma tentativa efêmera de login por senha para a fachada pública interna.
 *
 * <p>A senha é copiada defensivamente, transferida uma única vez e nunca participa de
 * {@link #toString()}.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public final class PasswordAuthenticationRequestDTO {

  private final String identifier;
  private final char[] password;
  private final boolean persistentLoginRequested;
  private final String turnstileToken;
  private final String canonicalOrigin;
  private final UUID correlationId;

  /** Cria o pedido efêmero com propriedade transferível da senha. */
  public PasswordAuthenticationRequestDTO(
      String identifier,
      char[] password,
      boolean persistentLoginRequested,
      String turnstileToken,
      String canonicalOrigin,
      UUID correlationId) {
    this.identifier = identifier;
    this.password = Arrays.copyOf(
        Objects.requireNonNull(password, "password must not be null"), password.length);
    this.persistentLoginRequested = persistentLoginRequested;
    this.turnstileToken = turnstileToken;
    this.canonicalOrigin = canonicalOrigin;
    this.correlationId = Objects.requireNonNull(correlationId, "correlationId must not be null");
  }

  public String identifier() {
    return identifier;
  }

  public boolean persistentLoginRequested() {
    return persistentLoginRequested;
  }

  public String turnstileToken() {
    return turnstileToken;
  }

  public String canonicalOrigin() {
    return canonicalOrigin;
  }

  public UUID correlationId() {
    return correlationId;
  }

  /** Transfere uma cópia da senha e apaga a mantida pelo pedido. */
  public char[] consumePassword() {
    char[] transferred = Arrays.copyOf(password, password.length);
    Arrays.fill(password, '\0');
    return transferred;
  }

  /** Oculta identificador, senha, token anti-automação e origem. */
  @Override
  public String toString() {
    return "PasswordAuthenticationRequestDTO[identifier=REDACTED, password=REDACTED, "
        + "persistentLoginRequested=" + persistentLoginRequested
        + ", turnstileToken=REDACTED, canonicalOrigin=REDACTED, correlationId="
        + correlationId + "]";
  }
}
