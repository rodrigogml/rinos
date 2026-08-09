package br.com.rinos.app.api.vo;

import java.time.Instant;

import br.com.rinos.app.api.enums.TotpEnrollmentStatusEnum;

/**
 * Resultado público do enrollment; segredo e URI existem somente no estado {@code PENDING} inicial.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record TotpEnrollmentResultVO(
    TotpEnrollmentStatusEnum status,
    String enrollmentReference,
    Instant expiresAt,
    String provisioningUri,
    String manualSecret) {

  /** Impede material de apresentação em resultados terminais. */
  public TotpEnrollmentResultVO {
    if (status == null) {
      throw new IllegalArgumentException("status must not be null");
    }
    boolean pending = status == TotpEnrollmentStatusEnum.PENDING;
    boolean completePresentation = enrollmentReference != null && !enrollmentReference.isBlank()
        && expiresAt != null
        && provisioningUri != null && !provisioningUri.isBlank()
        && manualSecret != null && !manualSecret.isBlank();
    if (pending != completePresentation) {
      throw new IllegalArgumentException("presentation is allowed only for a complete pending result");
    }
  }

  /** Cria um resultado terminal sem transportar material do enrollment. */
  public static TotpEnrollmentResultVO terminal(TotpEnrollmentStatusEnum status) {
    return new TotpEnrollmentResultVO(status, null, null, null, null);
  }

  @Override
  public String toString() {
    return "TotpEnrollmentResultVO[status=" + status
        + ", enrollmentReference=" + enrollmentReference
        + ", expiresAt=" + expiresAt
        + ", provisioningUri=REDACTED, manualSecret=REDACTED]";
  }
}
