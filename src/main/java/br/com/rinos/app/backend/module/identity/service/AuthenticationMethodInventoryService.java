package br.com.rinos.app.backend.module.identity.service;

import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import br.com.rinos.app.backend.module.identity.enums.EmailFactorStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.LocalCredentialStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.PasskeyCredentialStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.TotpFactorStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.EmailFactorRepository;
import br.com.rinos.app.backend.module.identity.repository.ExternalIdentityRepository;
import br.com.rinos.app.backend.module.identity.repository.LocalCredentialRepository;
import br.com.rinos.app.backend.module.identity.repository.PasskeyCredentialRepository;
import br.com.rinos.app.backend.module.identity.repository.TotpFactorRepository;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationMethodInventoryVO;

/**
 * Calcula invariantes sobre a identidade previamente bloqueada pelo chamador.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Service
@Lazy
public class AuthenticationMethodInventoryService {
  private final LocalCredentialRepository local;
  private final ExternalIdentityRepository external;
  private final PasskeyCredentialRepository passkeys;
  private final TotpFactorRepository totp;
  private final EmailFactorRepository email;
  public AuthenticationMethodInventoryService(LocalCredentialRepository local,
      ExternalIdentityRepository external, PasskeyCredentialRepository passkeys,
      TotpFactorRepository totp, EmailFactorRepository email) {
    this.local = local; this.external = external; this.passkeys = passkeys; this.totp = totp; this.email = email;
  }
  public AuthenticationMethodInventoryVO inspect(Long userId) {
    return new AuthenticationMethodInventoryVO(
        local.existsByUserIdAndStatusAndCompromisedAtIsNull(
            userId, LocalCredentialStatusEnum.ACTIVE),
        external.existsByUserIdAndStatus(userId, ExternalIdentityStatusEnum.ACTIVE),
        passkeys.countByPasskeyUserUserIdAndStatus(userId, PasskeyCredentialStatusEnum.ACTIVE),
        totp.countByUserIdAndStatus(userId, TotpFactorStatusEnum.ACTIVE),
        email.existsByUserIdAndStatus(userId, EmailFactorStatusEnum.ACTIVE),
        passkeys.countByPasskeyUserUserIdAndStatusAndUvInitializedTrue(
            userId, PasskeyCredentialStatusEnum.ACTIVE));
  }
}
