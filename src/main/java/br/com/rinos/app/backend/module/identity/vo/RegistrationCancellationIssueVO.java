package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.concurrent.CompletionStage;

import br.com.rinos.app.backend.module.identity.enums.RegistrationCancellationIssueStatusEnum;

/**
 * Resultado interno da emissão opcional da prova de cancelamento.
 *
 * @param status resultado interno que nunca atravessa a resposta pública neutra
 * @param expiresAt expiração da prova emitida
 * @param blockedUntil fim interno da janela quando a emissão foi limitada
 * @param dispatch despacho pós-commit, presente somente quando houve emissão
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record RegistrationCancellationIssueVO(
    RegistrationCancellationIssueStatusEnum status,
    Instant expiresAt,
    Instant blockedUntil,
    CompletionStage<VerificationEmailDispatchResultVO> dispatch) {

  /**
   * Garante que dados internos de emissão e bloqueio sejam mutuamente exclusivos.
   */
  public RegistrationCancellationIssueVO {
    if (status == null) {
      throw new IllegalArgumentException("cancellation issue status must not be null");
    }
    if (status == RegistrationCancellationIssueStatusEnum.ISSUED
        && (expiresAt == null || blockedUntil != null || dispatch == null)) {
      throw new IllegalArgumentException("issued cancellation proof requires dispatch and expiry");
    }
    if (status == RegistrationCancellationIssueStatusEnum.RATE_LIMITED
        && (expiresAt != null || blockedUntil == null || dispatch != null)) {
      throw new IllegalArgumentException("limited cancellation proof requires blockedUntil only");
    }
    if (status == RegistrationCancellationIssueStatusEnum.NOT_ELIGIBLE
        && (expiresAt != null || blockedUntil != null || dispatch != null)) {
      throw new IllegalArgumentException("ineligible cancellation proof cannot expose details");
    }
  }

  /**
   * Informa se uma pendência elegível recebeu nova prova.
   *
   * @return {@code true} somente quando a prova foi persistida
   */
  public boolean issued() {
    return status == RegistrationCancellationIssueStatusEnum.ISSUED;
  }

  /**
   * Informa se a janela móvel impediu nova emissão.
   *
   * @return {@code true} quando a franquia já foi consumida
   */
  public boolean rateLimited() {
    return status == RegistrationCancellationIssueStatusEnum.RATE_LIMITED;
  }

  /**
   * Cria o resultado neutro para cadastro inexistente ou inelegível.
   *
   * @return resultado sem prova nem despacho
   */
  public static RegistrationCancellationIssueVO notIssued() {
    return new RegistrationCancellationIssueVO(
        RegistrationCancellationIssueStatusEnum.NOT_ELIGIBLE,
        null,
        null,
        null);
  }

  /**
   * Cria o resultado interno de bloqueio sem torná-lo observável na resposta pública.
   *
   * @param blockedUntil fim da janela móvel vigente
   * @return resultado limitado sem prova nem despacho
   */
  public static RegistrationCancellationIssueVO rateLimited(Instant blockedUntil) {
    return new RegistrationCancellationIssueVO(
        RegistrationCancellationIssueStatusEnum.RATE_LIMITED,
        null,
        blockedUntil,
        null);
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
    return new RegistrationCancellationIssueVO(
        RegistrationCancellationIssueStatusEnum.ISSUED,
        expiresAt,
        null,
        dispatch);
  }
}
