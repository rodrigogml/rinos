package br.com.rinos.app.api.module.account.dto;

import java.time.ZoneId;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/** Dados confirmados de uma intenção; identidade e origem são derivadas pelo adapter. */
public record AccountCreationRequest(
    UUID idempotencyKey,
    String displayName,
    String baseCurrency,
    String timeZoneId,
    String humanVerificationToken,
    boolean confirmed) {

  public AccountCreationRequest {
    idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    displayName = normalizeName(displayName);
    baseCurrency = normalizeCurrency(baseCurrency);
    timeZoneId = normalizeZone(timeZoneId);
    humanVerificationToken = normalizeOptional(humanVerificationToken);
    if (!confirmed) {
      throw new IllegalArgumentException("account creation must be explicitly confirmed");
    }
  }

  @Override
  public String toString() {
    return "AccountCreationRequest[idempotencyKey=REDACTED, displayName=REDACTED, "
        + "baseCurrency=" + baseCurrency + ", timeZoneId=" + timeZoneId
        + ", humanVerificationToken=REDACTED, confirmed=" + confirmed + "]";
  }

  private static String normalizeName(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("displayName is required");
    String result = value.strip().replaceAll("\\s+", " ");
    if (result.length() > 160) throw new IllegalArgumentException("displayName is too long");
    return result;
  }

  private static String normalizeCurrency(String value) {
    try {
      return Currency.getInstance(Objects.requireNonNull(value).strip().toUpperCase()).getCurrencyCode();
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("baseCurrency is invalid", exception);
    }
  }

  private static String normalizeZone(String value) {
    try {
      return ZoneId.of(Objects.requireNonNull(value).strip()).getId();
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("timeZoneId is invalid", exception);
    }
  }

  private static String normalizeOptional(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
