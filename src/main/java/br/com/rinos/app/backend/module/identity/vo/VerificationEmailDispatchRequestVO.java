package br.com.rinos.app.backend.module.identity.vo;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import br.com.rinos.app.backend.module.identity.enums.VerificationEmailTemplateEnum;

/**
 * Transporta somente em memória os dados necessários ao e-mail de comprovação.
 *
 * <p>O destinatário e a URL podem conter dados sensíveis e não devem ser persistidos,
 * registrados ou publicados em eventos operacionais.
 *
 * @param recipient e-mail imutável do cadastro
 * @param confirmationUrl URL completa contendo a prova de uso único
 * @param manualCode prova opaca exibida para cópia manual somente quando aplicável
 * @param expiresAt instante UTC exibido à pessoa
 * @param locale idioma preferencial; nulo permite o fallback do RFW
 * @param correlationId correlação técnica segura para observabilidade
 * @param template template fechado correspondente à finalidade da prova
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record VerificationEmailDispatchRequestVO(
    String recipient,
    URI confirmationUrl,
    String manualCode,
    Instant expiresAt,
    Locale locale,
    UUID correlationId,
    VerificationEmailTemplateEnum template) {

  /**
   * Rejeita pedidos incompletos antes de registrar o callback transacional.
   */
  public VerificationEmailDispatchRequestVO {
    if (recipient == null || recipient.isBlank()) {
      throw new IllegalArgumentException("recipient must not be blank");
    }
    confirmationUrl = Objects.requireNonNull(
        confirmationUrl,
        "confirmationUrl must not be null");
    String scheme = confirmationUrl.getScheme();
    if (!confirmationUrl.isAbsolute()
        || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))) {
      throw new IllegalArgumentException(
          "confirmationUrl must be an absolute HTTP or HTTPS URI");
    }
    if (template == VerificationEmailTemplateEnum.REGISTRATION_CONFIRMATION
        && (manualCode == null || manualCode.isBlank())) {
      throw new IllegalArgumentException(
          "manualCode must not be blank for registration confirmation");
    }
    if (manualCode != null
        && (!manualCode.matches("[A-Za-z0-9_-]+") || manualCode.length() > 512)) {
      throw new IllegalArgumentException(
          "manualCode must be a URL-safe opaque value");
    }
    expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    correlationId = Objects.requireNonNull(correlationId, "correlationId must not be null");
    template = Objects.requireNonNull(template, "template must not be null");
  }

  /**
   * Mantém o construtor dos fluxos de ativação usando seu template original.
   *
   * @param recipient e-mail imutável do cadastro
   * @param confirmationUrl URL completa contendo a prova
   * @param expiresAt instante UTC exibido
   * @param locale idioma preferencial
   * @param correlationId correlação técnica
   */
  public VerificationEmailDispatchRequestVO(
      String recipient,
      URI confirmationUrl,
      String manualCode,
      Instant expiresAt,
      Locale locale,
      UUID correlationId) {
    this(
        recipient,
        confirmationUrl,
        manualCode,
        expiresAt,
        locale,
        correlationId,
        VerificationEmailTemplateEnum.REGISTRATION_CONFIRMATION);
  }

  /**
   * Produz representação redigida para impedir vazamento acidental em logs.
   *
   * @return descrição sem destinatário nem URL
   */
  @Override
  public String toString() {
    return "VerificationEmailDispatchRequestVO[expiresAt="
        + expiresAt
        + ", locale="
        + locale
        + ", correlationId="
        + correlationId
        + ", template="
        + template
        + ", recipient=REDACTED, confirmationUrl=REDACTED, manualCode=REDACTED]";
  }
}
