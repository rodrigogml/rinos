package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationProofTypeEnum;

/**
 * Visão sanitizada da prova, sem digest, versão de chave ou identificador interno.
 *
 * @param status estado público da operação
 * @param type tipo consultado, nulo em rejeição neutra
 * @param attemptCount tentativas registradas
 * @param expiresAt limite UTC, nulo em rejeição neutra
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public record AuthenticationProofInspectionVO(
    AuthenticationOperationStatusEnum status,
    AuthenticationProofTypeEnum type,
    int attemptCount,
    Instant expiresAt) {

  /** Cria uma rejeição que não confirma a existência da prova. */
  public static AuthenticationProofInspectionVO rejected() {
    return new AuthenticationProofInspectionVO(
        AuthenticationOperationStatusEnum.REJECTED,
        null,
        0,
        null);
  }
}
