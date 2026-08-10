package br.com.rinos.app.backend.module.identity.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.ExternalIdentityEntity;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.GoogleAuthenticationIdentityStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.vo.GoogleAuthenticationIdentityVO;

/**
 * Localiza o usuário de login exclusivamente pela chave estável {@code issuer + subject}.
 *
 * <p>O serviço propositalmente não recebe e-mail. Vínculo ausente, pendente ou pertencente a usuário não ativo não
 * produz identidade interna e nunca dispara associação automática.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
@Service
@Lazy
public class GoogleAuthenticationIdentityService {

  private final ExternalIdentityService externalIdentities;

  /** Cria o resolvedor sobre a autoridade única dos vínculos externos. */
  public GoogleAuthenticationIdentityService(ExternalIdentityService externalIdentities) {
    this.externalIdentities = externalIdentities;
  }

  /**
   * Bloqueia e avalia o vínculo estável sem consultar e-mail ou criar qualquer associação.
   *
   * @param issuer emissor já validado pelo RFW
   * @param subject identificador estável já validado pelo RFW
   * @return usuário ativo correspondente ou decisão minimizada
   */
  @Transactional
  public GoogleAuthenticationIdentityVO resolve(String issuer, String subject) {
    ExternalIdentityEntity identity = externalIdentities.findForUpdate(issuer, subject)
        .orElse(null);
    if (identity == null) {
      return GoogleAuthenticationIdentityVO.of(
          GoogleAuthenticationIdentityStatusEnum.NOT_FOUND);
    }
    if (identity.getStatus() != ExternalIdentityStatusEnum.ACTIVE
        || identity.getUser().getStatus() != UserStatusEnum.ACTIVE) {
      return GoogleAuthenticationIdentityVO.of(
          GoogleAuthenticationIdentityStatusEnum.REJECTED);
    }
    return GoogleAuthenticationIdentityVO.matched(identity.getUser().getId());
  }
}
