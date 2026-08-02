package br.com.rinos.app.api.vo;

import java.time.Duration;

import br.com.rinos.app.api.enums.PasswordRecoveryRequestStatusEnum;

/**
 * Resultado público sem qualquer indicador da existência do e-mail.
 *
 * @param status estado da solicitação
 * @param retryAfter tempo restante do bloqueio por origem, quando aplicável
 * @author Rodrigo Leitão
 * @since 2026-08-02
 */
public record PasswordRecoveryRequestResultVO(
    PasswordRecoveryRequestStatusEnum status,
    Duration retryAfter) {
}
