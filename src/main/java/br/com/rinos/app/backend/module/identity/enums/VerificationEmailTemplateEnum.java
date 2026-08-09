package br.com.rinos.app.backend.module.identity.enums;

/**
 * Templates fechados usados para entregar provas temporárias de identidade.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public enum VerificationEmailTemplateEnum {

  REGISTRATION_CONFIRMATION("registration-verification"),
  REGISTRATION_CANCELLATION("registration-cancellation"),
  PASSWORD_RECOVERY("password-recovery"),
  AUTHENTICATION_EMAIL_CODE("authentication-email-code");

  private final String templateName;

  VerificationEmailTemplateEnum(String templateName) {
    this.templateName = templateName;
  }

  /**
   * Retorna o nome público resolvido pelo serviço de templates do RFW.
   *
   * @return nome estável sem extensão
   */
  public String getTemplateName() {
    return templateName;
  }
}
