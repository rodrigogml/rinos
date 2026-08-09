package br.com.rinos.app.backend.module.identity.service;

import java.nio.CharBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalLong;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.LocalCredentialEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.LocalCredentialStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.LocalCredentialRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;

/**
 * Comprova uma credencial local com custo criptográfico também para identidade ausente.
 *
 * <p>A ordem de lock é usuário → credencial. O hash sentinela é criado pelo mesmo encoder da
 * aplicação e existe somente em memória para reduzir diferenças grosseiras entre senha errada e
 * identidade desconhecida.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Service
@Lazy
public class PasswordCredentialAuthenticationService {

  private static final String SENTINEL_INPUT =
      "Rinos password verification timing sentinel; not a user credential.";
  private static final String SENTINEL_NORMALIZED_EMAIL = "__rinos_timing_sentinel__";
  private static final long SENTINEL_USER_ID = 0L;

  private final EmailNormalizationService emailNormalizationService;
  private final UserRepository userRepository;
  private final LocalCredentialRepository credentialRepository;
  private final PasswordEncoder passwordEncoder;
  private final String sentinelHash;

  /** Cria o verificador e produz uma sentinela com os parâmetros Argon2id vigentes. */
  public PasswordCredentialAuthenticationService(
      EmailNormalizationService emailNormalizationService,
      UserRepository userRepository,
      LocalCredentialRepository credentialRepository,
      PasswordEncoder passwordEncoder) {
    this.emailNormalizationService = Objects.requireNonNull(
        emailNormalizationService, "emailNormalizationService must not be null");
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
    this.credentialRepository = Objects.requireNonNull(
        credentialRepository, "credentialRepository must not be null");
    this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
    sentinelHash = Objects.requireNonNull(
        passwordEncoder.encode(SENTINEL_INPUT), "sentinelHash must not be null");
  }

  /**
   * Valida o e-mail normalizado e a senha, apagando sempre o array recebido.
   *
   * @param identifier e-mail informado
   * @param password array cuja propriedade é transferida ao método
   * @param verifiedAt instante da prova, reservado à evolução transacional do uso da credencial
   * @return ID apenas quando identidade ativa e credencial ativa correspondem
   */
  @Transactional
  public OptionalLong verify(String identifier, char[] password, Instant verifiedAt) {
    Objects.requireNonNull(password, "password must not be null");
    Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
    try {
      UserEntity user = normalizeAndLock(identifier);
      long credentialOwnerId = user == null ? SENTINEL_USER_ID : user.getId();
      LocalCredentialEntity credential = credentialRepository
          .findByUserIdForUpdate(credentialOwnerId)
          .orElse(null);
      String comparedHash = credential == null ? sentinelHash : credential.getPasswordHash();
      boolean matches = passwordEncoder.matches(CharBuffer.wrap(password), comparedHash);
      if (!matches || user == null || user.getStatus() != UserStatusEnum.ACTIVE
          || credential == null || credential.getStatus() != LocalCredentialStatusEnum.ACTIVE
          || credential.getCompromisedAt() != null) {
        return OptionalLong.empty();
      }
      upgradeHashIfRequired(credential, password);
      return OptionalLong.of(user.getId());
    } finally {
      Arrays.fill(password, '\0');
    }
  }

  /**
   * Comprova a senha da identidade autenticada sem depender do e-mail exposto na interface.
   *
   * @param userId identidade vinculada à sessão corrente
   * @param password array cuja propriedade é transferida ao método
   * @param verifiedAt instante UTC da prova
   * @return {@code true} somente para usuário e credencial locais ativos e não comprometidos
   */
  @Transactional
  public boolean verifyUser(Long userId, char[] password, Instant verifiedAt) {
    Objects.requireNonNull(password, "password must not be null");
    Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
    try {
      UserEntity user = userId == null || userId <= 0
          ? null : userRepository.findByIdForUpdate(userId).orElse(null);
      long credentialOwnerId = user == null ? SENTINEL_USER_ID : user.getId();
      LocalCredentialEntity credential = credentialRepository
          .findByUserIdForUpdate(credentialOwnerId)
          .orElse(null);
      String comparedHash = credential == null ? sentinelHash : credential.getPasswordHash();
      boolean matches = passwordEncoder.matches(CharBuffer.wrap(password), comparedHash);
      if (!matches || user == null || user.getStatus() != UserStatusEnum.ACTIVE
          || credential == null || credential.getStatus() != LocalCredentialStatusEnum.ACTIVE
          || credential.getCompromisedAt() != null) {
        return false;
      }
      upgradeHashIfRequired(credential, password);
      return true;
    } finally {
      Arrays.fill(password, '\0');
    }
  }

  private void upgradeHashIfRequired(LocalCredentialEntity credential, char[] password) {
    if (!passwordEncoder.upgradeEncoding(credential.getPasswordHash())) {
      return;
    }
    String upgradedHash = Objects.requireNonNull(
        passwordEncoder.encode(CharBuffer.wrap(password)), "upgradedHash must not be null");
    credential.setPasswordHash(upgradedHash);
    credentialRepository.save(credential);
  }

  private UserEntity normalizeAndLock(String identifier) {
    String normalizedEmail;
    try {
      normalizedEmail = emailNormalizationService.normalize(identifier).normalizedEmail();
    } catch (NullPointerException | IllegalArgumentException invalidIdentifier) {
      normalizedEmail = SENTINEL_NORMALIZED_EMAIL;
    }
    return userRepository.findByNormalizedEmailForUpdate(normalizedEmail).orElse(null);
  }
}
