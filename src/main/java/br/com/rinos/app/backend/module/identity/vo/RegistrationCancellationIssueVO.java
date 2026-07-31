package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.concurrent.CompletionStage;

/**
 * Resultado interno da emissão opcional da prova de cancelamento.
 *
 * @param issued indica que uma pendência elegível recebeu nova prova
 * @param expiresAt expiração da prova emitida
 * @param dispatch despacho pós-commit, presente somente quando houve emissão
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record RegistrationCancellationIssueVO(
    boolean issued,
    Instant expiresAt,
    CompletionStage<VerificationEmailDispatchResultVO> dispatch) {

  /**
   * Cria o resultado neutro para cadastro inexistente ou inelegível.
   *
   * @return resultado sem prova nem despacho
   */
  public static RegistrationCancellationIssueVO notIssued() {
    return new RegistrationCancellationIssueVO(false, null, null);
  }

  /**
   * Cria o resultado de uma prova persistida e agendada.
   *
   * @param expiresAt expiração real
   * @param dispatch despacho pós-commit
   * @return resultado emitido
   */
  public static RegistrationCancellationIssueVO issued(
      Instant expiresAt,
      CompletionStage<VerificationEmailDispatchResultVO> dispatch) {
    if (expiresAt == null || dispatch == null) {
      throw new IllegalArgumentException("issued cancellation proof requires dispatch and expiry");
    }
    return new RegistrationCancellationIssueVO(true, expiresAt, dispatch);
  }
}
