package br.com.rinos.app.api.vo;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.api.enums.AuthenticationMethodEnum;

/**
 * Evidência pública sanitizada que compõe a garantia alcançada.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public record AuthenticationMethodEvidenceVO(
    AuthenticationMethodEnum method,
    Instant verifiedAt,
    Boolean userVerification) {

  public AuthenticationMethodEvidenceVO {
    Objects.requireNonNull(method, "method must not be null");
    Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
  }
}
