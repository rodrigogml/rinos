package br.com.rinos.app.api.dto;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Transporta o pedido de cadastro da apresentação à fachada pública.
 *
 * <p>A senha é mantida em array redigido e transferida uma única vez ao preparador de credencial.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public final class RegistrationStartRequestDTO {

  private final String email;
  private final char[] password;
  private final List<String> acceptedLegalDocumentIds;
  private final String canonicalOrigin;
  private final Locale locale;
  private final UUID correlationId;

  /**
   * Cria um pedido efêmero com cópia defensiva da senha.
   */
  public RegistrationStartRequestDTO(
      String email,
      char[] password,
      List<String> acceptedLegalDocumentIds,
      String canonicalOrigin,
      Locale locale,
      UUID correlationId) {
    this.email = email;
    this.password = Arrays.copyOf(
        Objects.requireNonNull(password, "password must not be null"),
        password.length);
    this.acceptedLegalDocumentIds = acceptedLegalDocumentIds == null
        ? List.of() : List.copyOf(acceptedLegalDocumentIds);
    this.canonicalOrigin = canonicalOrigin;
    this.locale = locale;
    this.correlationId = Objects.requireNonNull(
        correlationId,
        "correlationId must not be null");
  }

  public String getEmail() {
    return email;
  }

  public List<String> getAcceptedLegalDocumentIds() {
    return acceptedLegalDocumentIds;
  }

  public String getCanonicalOrigin() {
    return canonicalOrigin;
  }

  public Locale getLocale() {
    return locale;
  }

  public UUID getCorrelationId() {
    return correlationId;
  }

  /**
   * Transfere a senha ao único consumidor e zera a cópia mantida pelo pedido.
   *
   * @return cópia cuja propriedade passa ao chamador
   */
  public char[] consumePassword() {
    char[] transferred = Arrays.copyOf(password, password.length);
    Arrays.fill(password, '\0');
    return transferred;
  }

  @Override
  public String toString() {
    return "RegistrationStartRequestDTO[email=REDACTED, password=REDACTED, "
        + "acceptedLegalDocumentCount="
        + acceptedLegalDocumentIds.size()
        + ", canonicalOrigin=REDACTED, locale="
        + locale
        + ", correlationId="
        + correlationId
        + "]";
  }
}
