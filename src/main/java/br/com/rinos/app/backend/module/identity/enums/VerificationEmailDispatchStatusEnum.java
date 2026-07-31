package br.com.rinos.app.backend.module.identity.enums;

/**
 * Classifica o resultado seguro do despacho de uma comprovação por e-mail.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public enum VerificationEmailDispatchStatusEnum {

  /** O servidor SMTP aceitou a mensagem. */
  ACCEPTED,

  /** O template não pôde ser resolvido ou renderizado. */
  TEMPLATE_FAILURE,

  /** O transporte SMTP recusou, expirou ou não concluiu o envio. */
  TRANSPORT_FAILURE,

  /** A transação proprietária foi revertida e nenhum envio foi tentado. */
  TRANSACTION_ROLLED_BACK
}
