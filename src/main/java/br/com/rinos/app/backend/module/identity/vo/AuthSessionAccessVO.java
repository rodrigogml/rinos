package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.UUID;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthSessionAccessStatusEnum;

/**
 * Resultado seguro do acesso a uma sessão, sem hashes nem endereço de origem.
 *
 * @param status resultado da validação
 * @param userId identidade autenticada ou {@code null} quando rejeitada
 * @param userEmail e-mail vigente da identidade autenticada ou {@code null}
 * @param publicReference referência de gestão ou {@code null}
 * @param assuranceLevel garantia da sessão ou {@code null}
 * @param lastStrongAuthAt última autenticação forte ou {@code null}
 * @param absoluteExpiresAt limite absoluto ou {@code null}
 * @param idleExpiresAt limite por inatividade ou {@code null}
 * @param rotatedCookieValue novo cookie de entrega única ou {@code null}
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public record AuthSessionAccessVO(
    AuthSessionAccessStatusEnum status,
    Long userId,
    String userEmail,
    UUID publicReference,
    AuthenticationAssuranceEnum assuranceLevel,
    Instant lastStrongAuthAt,
    Instant absoluteExpiresAt,
    Instant idleExpiresAt,
    String rotatedCookieValue) {

  /** Cria resultado neutro para cookie ausente, malformado ou desconhecido. */
  public static AuthSessionAccessVO rejected() {
    return new AuthSessionAccessVO(
        AuthSessionAccessStatusEnum.REJECTED, null, null, null, null, null, null, null, null);
  }

  /** Impede exposição acidental do cookie rotacionado em logs. */
  @Override
  public String toString() {
    return "AuthSessionAccessVO[status=" + status + ", userId=" + userId
        + ", userEmail=<redacted>"
        + ", publicReference=" + publicReference + ", assuranceLevel=" + assuranceLevel
        + ", lastStrongAuthAt=" + lastStrongAuthAt + ", absoluteExpiresAt="
        + absoluteExpiresAt + ", idleExpiresAt=" + idleExpiresAt
        + ", rotatedCookieValue=<redacted>]";
  }
}
