package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Resultado interno da transação que cria uma nova pendência.
 *
 * @param blockedUntil fim do limite, ou nulo quando a criação foi confirmada
 * @param expiresAt expiração UTC da comprovação emitida
 * @param dispatch resultado pós-commit, presente somente quando houve criação
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record RegistrationCreationTransactionVO(
    Instant blockedUntil,
    Instant expiresAt,
    CompletionStage<VerificationEmailDispatchResultVO> dispatch) {

  public RegistrationCreationTransactionVO {
    boolean blocked = blockedUntil != null && expiresAt == null && dispatch == null;
    boolean scheduled = blockedUntil == null && expiresAt != null && dispatch != null;
    if (!blocked && !scheduled) {
      throw new IllegalArgumentException(
          "transaction must be either blocked or scheduled with expiration");
    }
  }

  /**
   * Cria um resultado bloqueado sem emissão de comprovação.
   *
   * @param blockedUntil fim do limite
   * @return resultado bloqueado
   */
  public static RegistrationCreationTransactionVO blocked(Instant blockedUntil) {
    return new RegistrationCreationTransactionVO(
        Objects.requireNonNull(blockedUntil, "blockedUntil must not be null"),
        null,
        null);
  }

  /**
   * Cria um resultado com comprovação e despacho pós-commit.
   *
   * @param expiresAt expiração UTC da comprovação
   * @param dispatch estágio de despacho pós-commit
   * @return resultado agendado
   */
  public static RegistrationCreationTransactionVO scheduled(
      Instant expiresAt,
      CompletionStage<VerificationEmailDispatchResultVO> dispatch) {
    return new RegistrationCreationTransactionVO(
        null,
        Objects.requireNonNull(expiresAt, "expiresAt must not be null"),
        Objects.requireNonNull(dispatch, "dispatch must not be null"));
  }

  public boolean blocked() {
    return blockedUntil != null;
  }
}
