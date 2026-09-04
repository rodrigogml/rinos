package br.com.rinos.app.backend.module.identity.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.TotpFactorStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.TotpFactorRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.config.AccessBootstrapPropertiesConfig;

/**
 * Determina, sem expor a configuração, a restrição de enrollment do fundador global.
 *
 * <p>A política é reavaliada a cada navegação: confirmação TOTP, alteração da identidade ou
 * mudança deliberada da propriedade não deixam uma permissão transitória materializada em sessão.
 *
 * @author Rodrigo Leitão
 * @since 2026-09-01
 */
@Service
@Lazy
public class FounderTotpEnrollmentPolicyService {

  private final AccessBootstrapPropertiesConfig properties;
  private final EmailNormalizationService emails;
  private final UserRepository users;
  private final TotpFactorRepository factors;

  /** Cria a política sobre a identidade configurada e o estado durável dos fatores. */
  public FounderTotpEnrollmentPolicyService(
      AccessBootstrapPropertiesConfig properties,
      EmailNormalizationService emails,
      UserRepository users,
      TotpFactorRepository factors) {
    this.properties = properties;
    this.emails = emails;
    this.users = users;
    this.factors = factors;
  }

  /**
   * Retorna a obrigação somente para a identidade ativa que corresponde ao fundador configurado.
   *
   * @param userId identidade global autenticada
   * @return obrigação de enrollment TOTP ainda não satisfeita
   */
  @Transactional(readOnly = true)
  public boolean requiresEnrollment(long userId) {
    if (userId <= 0) {
      return false;
    }
    UserEntity user = users.findById(userId).orElse(null);
    if (user == null || user.getStatus() != UserStatusEnum.ACTIVE) {
      return false;
    }
    String configured = emails.normalize(properties.administratorEmail()).normalizedEmail();
    if (!configured.equals(user.getNormalizedEmail())) {
      return false;
    }
    return factors.countByUserIdAndStatus(userId, TotpFactorStatusEnum.ACTIVE) == 0;
  }
}
