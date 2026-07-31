package br.com.rinos.app.api.vo;

import java.util.UUID;

/**
 * Transporta ao domínio somente os atributos necessários de uma identidade já validada pelo RFW.
 *
 * <p>O contrato não aceita ID token, nonce ou mapa de claims.
 *
 * @param providerId identificador público do provedor validador
 * @param issuer emissor criptograficamente validado
 * @param subject identificador estável no emissor
 * @param email e-mail declarado pelo provedor
 * @param emailVerified indicação validada pelo provedor
 * @param correlationId correlação técnica sem dados pessoais
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record GoogleIdentityResolutionRequestVO(
    String providerId,
    String issuer,
    String subject,
    String email,
    boolean emailVerified,
    UUID correlationId) {

  /**
   * Evita expor identificadores externos e e-mail em diagnóstico acidental.
   *
   * @return descrição estrutural sanitizada
   */
  @Override
  public String toString() {
    return "GoogleIdentityResolutionRequestVO[providerId=" + providerId
        + ", issuer=REDACTED, subject=REDACTED, email=REDACTED, emailVerified="
        + emailVerified + ", correlationId=" + correlationId + "]";
  }
}
