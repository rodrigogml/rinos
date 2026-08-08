package br.com.rinos.app.api.vo;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.api.enums.AuthenticationOperationStatusEnum;
import br.com.rinos.app.api.enums.AuthenticationProofTypeEnum;

/**
 * Resultado sanitizado de uma prova sem digest, chave ou identificador interno.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public record AuthenticationProofResultVO(
    AuthenticationOperationStatusEnum status,
    AuthenticationProofTypeEnum type,
    int attemptCount,
    Instant expiresAt) {

  public AuthenticationProofResultVO {
    status = Objects.requireNonNull(status, "status must not be null");
    if (attemptCount < 0) {
      throw new IllegalArgumentException("attemptCount must not be negative");
    }
  }
}
