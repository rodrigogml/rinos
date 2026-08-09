package br.com.rinos.app.backend.module.identity.vo;

import java.util.Objects;

import br.eng.rodrigogml.rfw.authentication.vo.RFWTotpEnrollmentVO;

/**
 * Une a apresentação efêmera criada pelo RFW ao envelope persistível do Rinos.
 *
 * <p>A representação textual nunca inclui segredo, URI, nonce ou ciphertext.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record ProtectedTotpEnrollmentVO(
    RFWTotpEnrollmentVO presentation,
    EncryptedAuthenticationSecretVO encryptedSecret) {

  /** Exige os dois lados inseparáveis do mesmo enrollment. */
  public ProtectedTotpEnrollmentVO {
    Objects.requireNonNull(presentation, "presentation must not be null");
    Objects.requireNonNull(encryptedSecret, "encryptedSecret must not be null");
  }

  @Override
  public String toString() {
    return "ProtectedTotpEnrollmentVO[presentation=REDACTED, encryptedSecret=REDACTED]";
  }
}
