package br.com.rinos.app.backend.module.identity.service;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.RecoveryCodeSetEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.EmailFactorStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityProviderEnum;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.LocalCredentialStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.PasskeyCredentialStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.RecoveryCodeSetStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.RecoveryCodeStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.TotpFactorStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.EmailFactorRepository;
import br.com.rinos.app.backend.module.identity.repository.ExternalIdentityRepository;
import br.com.rinos.app.backend.module.identity.repository.LocalCredentialRepository;
import br.com.rinos.app.backend.module.identity.repository.PasskeyCredentialRepository;
import br.com.rinos.app.backend.module.identity.repository.RecoveryCodeRepository;
import br.com.rinos.app.backend.module.identity.repository.RecoveryCodeSetRepository;
import br.com.rinos.app.backend.module.identity.repository.TotpFactorRepository;

/**
 * Recompõe os métodos atualmente utilizáveis sem confiar na fotografia do fluxo.
 *
 * <p>Chamadores de transições críticas devem bloquear primeiro o usuário. Mutações futuras
 * de credenciais devem obedecer à mesma ordem para serializar revogação e autenticação.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Service
@Lazy
public class AuthenticationMethodAvailabilityService {

  private final LocalCredentialRepository localCredentialRepository;
  private final ExternalIdentityRepository externalIdentityRepository;
  private final PasskeyCredentialRepository passkeyCredentialRepository;
  private final TotpFactorRepository totpFactorRepository;
  private final EmailFactorRepository emailFactorRepository;
  private final RecoveryCodeSetRepository recoveryCodeSetRepository;
  private final RecoveryCodeRepository recoveryCodeRepository;

  /** Cria o catálogo sobre todas as fontes globais de métodos. */
  public AuthenticationMethodAvailabilityService(
      LocalCredentialRepository localCredentialRepository,
      ExternalIdentityRepository externalIdentityRepository,
      PasskeyCredentialRepository passkeyCredentialRepository,
      TotpFactorRepository totpFactorRepository,
      EmailFactorRepository emailFactorRepository,
      RecoveryCodeSetRepository recoveryCodeSetRepository,
      RecoveryCodeRepository recoveryCodeRepository) {
    this.localCredentialRepository = localCredentialRepository;
    this.externalIdentityRepository = externalIdentityRepository;
    this.passkeyCredentialRepository = passkeyCredentialRepository;
    this.totpFactorRepository = totpFactorRepository;
    this.emailFactorRepository = emailFactorRepository;
    this.recoveryCodeSetRepository = recoveryCodeSetRepository;
    this.recoveryCodeRepository = recoveryCodeRepository;
  }

  /**
   * Consulta o estado atual de todos os métodos da identidade.
   *
   * @param userId identidade global já bloqueada pelo chamador
   * @return conjunto imutável de métodos utilizáveis
   */
  @Transactional(readOnly = true)
  public Set<AuthenticationMethodEnum> availableMethods(Long userId) {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    EnumSet<AuthenticationMethodEnum> available =
        EnumSet.noneOf(AuthenticationMethodEnum.class);
    if (localCredentialRepository.existsByUserIdAndStatusAndCompromisedAtIsNull(
        userId, LocalCredentialStatusEnum.ACTIVE)) {
      available.add(AuthenticationMethodEnum.PASSWORD);
    }
    if (externalIdentityRepository.existsByUserIdAndProviderAndStatus(
        userId, ExternalIdentityProviderEnum.GOOGLE, ExternalIdentityStatusEnum.ACTIVE)) {
      available.add(AuthenticationMethodEnum.GOOGLE);
    }
    if (passkeyCredentialRepository.countByPasskeyUserUserIdAndStatus(
        userId, PasskeyCredentialStatusEnum.ACTIVE) > 0) {
      available.add(AuthenticationMethodEnum.PASSKEY);
    }
    if (totpFactorRepository.countByUserIdAndStatus(userId, TotpFactorStatusEnum.ACTIVE) > 0) {
      available.add(AuthenticationMethodEnum.TOTP);
    }
    if (emailFactorRepository.existsByUserIdAndStatus(userId, EmailFactorStatusEnum.ACTIVE)) {
      available.add(AuthenticationMethodEnum.EMAIL_CODE);
    }
    RecoveryCodeSetEntity recoveryCodes = recoveryCodeSetRepository
        .findByUserIdAndStatus(userId, RecoveryCodeSetStatusEnum.ACTIVE)
        .orElse(null);
    if (recoveryCodes != null
        && recoveryCodeRepository.countByCodeSetIdAndStatus(
            Objects.requireNonNull(recoveryCodes.getId(), "recoveryCodeSet id must not be null"),
            RecoveryCodeStatusEnum.AVAILABLE) > 0) {
      available.add(AuthenticationMethodEnum.RECOVERY_CODE);
    }
    return Set.copyOf(available);
  }
}
