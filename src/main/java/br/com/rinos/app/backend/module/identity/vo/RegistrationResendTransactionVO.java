package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Resultado interno exclusivo da transação de reemissão.
 *
 * @param eligible indica que a pendência ainda admite reemissão
 * @param blockedUntil fim da janela quando limitada
 * @param dispatch resultado pós-commit quando uma prova foi emitida
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record RegistrationResendTransactionVO(
    boolean eligible,
    Instant blockedUntil,
    CompletionStage<VerificationEmailDispatchResultVO> dispatch) {

  public RegistrationResendTransactionVO {
    if ((!eligible && (blockedUntil != null || dispatch != null))
        || (eligible && (blockedUntil == null) == (dispatch == null))) {
      throw new IllegalArgumentException("invalid resend transaction outcome");
    }
  }

  /**
   * Cria resultado neutro para cadastro ausente, encerrado ou expirado.
   *
   * @return resultado não elegível
   */
  public static RegistrationResendTransactionVO notEligible() {
    return new RegistrationResendTransactionVO(false, null, null);
  }

  /**
   * Cria resultado temporariamente limitado.
   *
   * @param blockedUntil fim da janela móvel
   * @return resultado elegível sem nova prova
   */
  public static RegistrationResendTransactionVO blocked(Instant blockedUntil) {
    return new RegistrationResendTransactionVO(
        true,
        Objects.requireNonNull(blockedUntil, "blockedUntil must not be null"),
        null);
  }

  /**
   * Cria resultado com despacho registrado para depois do commit.
   *
   * @param dispatch estágio pós-commit
   * @return resultado elegível com nova prova
   */
  public static RegistrationResendTransactionVO scheduled(
      CompletionStage<VerificationEmailDispatchResultVO> dispatch) {
    return new RegistrationResendTransactionVO(
        true,
        null,
        Objects.requireNonNull(dispatch, "dispatch must not be null"));
  }

  /**
   * Indica limitação temporária sem depender da presença do instante externamente.
   *
   * @return {@code true} quando nenhuma prova foi emitida por limite
   */
  public boolean blocked() {
    return blockedUntil != null;
  }
}
