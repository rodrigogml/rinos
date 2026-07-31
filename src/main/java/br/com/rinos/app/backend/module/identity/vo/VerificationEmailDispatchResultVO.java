package br.com.rinos.app.backend.module.identity.vo;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

import br.com.rinos.app.backend.module.identity.enums.VerificationEmailDispatchStatusEnum;

/**
 * Retorna à orquestração somente o resultado público e dados técnicos não sensíveis.
 *
 * @param status resultado do envio
 * @param correlationId correlação técnica da tentativa
 * @param elapsed tempo entre o início pós-commit e a conclusão do dispatcher
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record VerificationEmailDispatchResultVO(
    VerificationEmailDispatchStatusEnum status,
    UUID correlationId,
    Duration elapsed) {

  /**
   * Valida o resultado antes de atravessar a fronteira do serviço.
   */
  public VerificationEmailDispatchResultVO {
    status = Objects.requireNonNull(status, "status must not be null");
    correlationId = Objects.requireNonNull(correlationId, "correlationId must not be null");
    elapsed = Objects.requireNonNull(elapsed, "elapsed must not be null");
    if (elapsed.isNegative()) {
      throw new IllegalArgumentException("elapsed must not be negative");
    }
  }

  /**
   * Indica se a interface pode afirmar que o SMTP aceitou a mensagem.
   *
   * @return {@code true} somente para aceitação comprovada
   */
  public boolean accepted() {
    return status == VerificationEmailDispatchStatusEnum.ACCEPTED;
  }

  /**
   * Indica se uma nova solicitação manual de envio deve ser oferecida.
   *
   * @return {@code true} para falhas posteriores ao commit
   */
  public boolean resendAvailable() {
    return status == VerificationEmailDispatchStatusEnum.TEMPLATE_FAILURE
        || status == VerificationEmailDispatchStatusEnum.TRANSPORT_FAILURE;
  }
}
