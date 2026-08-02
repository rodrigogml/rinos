package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.concurrent.CompletionStage;

import br.com.rinos.app.backend.module.identity.enums.PasswordRecoveryOperationStatusEnum;

/**
 * Resultado interno sem e-mail, token ou hash.
 *
 * @param status estado reduzido
 * @param retryAfter fim do bloqueio por origem
 * @param dispatch despacho pós-commit, quando houve emissão
 * @author Rodrigo Leitão
 * @since 2026-08-02
 */
public record PasswordRecoveryOperationVO(
    PasswordRecoveryOperationStatusEnum status,
    Instant retryAfter,
    CompletionStage<VerificationEmailDispatchResultVO> dispatch) {
}
