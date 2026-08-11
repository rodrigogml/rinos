package br.com.rinos.app.backend.module.identity.enums;

/**
 * Catálogo de templates de notificações de segurança sem segredos.
 *
 * <p>Os nomes são contratos estáveis com o catálogo de templates do RFW. O envio, os dados
 * temporários e os limites de cada notificação pertencem aos serviços de notificação, não a este
 * enum.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-11
 */
public enum AuthenticationNotificationTemplateEnum {

  NEW_SESSION("authentication-new-session"),
  METHOD_CHANGED("authentication-method-changed"),
  RECOVERY_COMPLETED("authentication-recovery-completed"),
  REPEATED_FAILURES("authentication-repeated-failures");

  private final String templateName;

  AuthenticationNotificationTemplateEnum(String templateName) {
    this.templateName = templateName;
  }

  /**
   * Retorna o identificador usado pelo catálogo de templates do RFW.
   *
   * @return nome estável do template, sem extensão
   */
  public String getTemplateName() {
    return templateName;
  }
}
