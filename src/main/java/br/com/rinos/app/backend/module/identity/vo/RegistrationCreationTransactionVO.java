package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.concurrent.CompletionStage;

/**
 * Resultado interno da transação que cria uma nova pendência.
 *
 * @param blockedUntil fim do limite, ou nulo quando a criação foi confirmada
 * @param dispatch resultado pós-commit, presente somente quando houve criação
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record RegistrationCreationTransactionVO(
    Instant blockedUntil,
    CompletionStage<VerificationEmailDispatchResultVO> dispatch) {

  public RegistrationCreationTransactionVO {
    if ((blockedUntil == null) == (dispatch == null)) {
      throw new IllegalArgumentException(
          "exactly one of blockedUntil or dispatch must be present");
    }
  }

  public boolean blocked() {
    return blockedUntil != null;
  }
}
