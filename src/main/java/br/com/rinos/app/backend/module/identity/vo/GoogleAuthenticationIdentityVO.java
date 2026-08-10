package br.com.rinos.app.backend.module.identity.vo;

import br.com.rinos.app.backend.module.identity.enums.GoogleAuthenticationIdentityStatusEnum;

/**
 * Transporta somente a decisão e, quando válida, a identidade interna localizada por issuer e subject.
 *
 * @param status decisão da localização estável
 * @param userId identificador interno presente somente em {@code MATCHED}
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public record GoogleAuthenticationIdentityVO(
    GoogleAuthenticationIdentityStatusEnum status,
    Long userId) {

  /** Protege a identidade interna contra exposição em resultados rejeitados ou ausentes. */
  public GoogleAuthenticationIdentityVO {
    if (status == null) {
      throw new IllegalArgumentException("status must not be null");
    }
    if (status == GoogleAuthenticationIdentityStatusEnum.MATCHED) {
      if (userId == null || userId <= 0) {
        throw new IllegalArgumentException("matched identity requires a positive userId");
      }
    } else if (userId != null) {
      throw new IllegalArgumentException("unmatched identity must not expose userId");
    }
  }

  /** Cria uma correspondência válida com usuário ativo. */
  public static GoogleAuthenticationIdentityVO matched(Long userId) {
    return new GoogleAuthenticationIdentityVO(
        GoogleAuthenticationIdentityStatusEnum.MATCHED, userId);
  }

  /** Cria uma decisão sem identidade interna. */
  public static GoogleAuthenticationIdentityVO of(
      GoogleAuthenticationIdentityStatusEnum status) {
    return new GoogleAuthenticationIdentityVO(status, null);
  }

  /** Não inclui o identificador interno em representações diagnósticas. */
  @Override
  public String toString() {
    return "GoogleAuthenticationIdentityVO[status=" + status + ", userId=REDACTED]";
  }
}
