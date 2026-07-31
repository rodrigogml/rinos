package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Resultado interno exclusivo da transação de reemissão.
 *
 * @param eligible indica que a pendência ainda admite reemissão
 * @param blockedUntil fim da janela quando limitada
 * @param expiresAt expiração UTC da nova comprovação
 * @param dispatch resultado pós-commit quando uma prova foi emitida
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record RegistrationResendTransactionVO(
    boolean eligible,
    Instant blockedUntil,
    Instant expiresAt,
    CompletionStage<VerificationEmailDispatchResultVO> dispatch) {

  public RegistrationResendTransactionVO {
    boolean notEligible = !eligible
        && blockedUntil == null && expiresAt == null && dispatch == null;
    boolean blocked = eligible
        && blockedUntil != null && expiresAt == null && dispatch == null;
    boolean scheduled = eligible
        && blockedUntil == null && expiresAt != null && dispatch != null;
    if (!notEligible && !blocked && !scheduled) {
      throw new IllegalArgumentException("invalid resend transaction outcome");
    }
  }

  /**
   * Cria resultado neutro para cadastro ausente, encerrado ou expirado.
   *
   * @return resultado não elegível
   */
  public static RegistrationResendTransactionVO notEligible() {
    return new RegistrationResendTransactionVO(false, null, null, null);
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
        null,
        null);
  }

  /**
   * Cria resultado com despacho registrado para depois do commit.
   *
   * @param expiresAt expiração UTC da prova emitida
   * @param dispatch estágio pós-commit
   * @return resultado elegível com nova prova
   */
  public static RegistrationResendTransactionVO scheduled(
      Instant expiresAt,
      CompletionStage<VerificationEmailDispatchResultVO> dispatch) {
    return new RegistrationResendTransactionVO(
        true,
        null,
        Objects.requireNonNull(expiresAt, "expiresAt must not be null"),
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
