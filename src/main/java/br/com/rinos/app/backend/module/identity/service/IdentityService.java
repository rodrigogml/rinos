package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.RegistrationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.RegistrationRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.NormalizedEmailVO;

/**
 * Mantém as operações internas básicas da identidade e de seu cadastro 1:1.
 *
 * <p>A orquestração pública, credenciais, aceites, comprovações e convergência após colisão
 * concorrente pertencem às tarefas do ciclo de cadastro. A criação é tardia para preservar os
 * diagnósticos de configuração e migration antes de qualquer consumidor da persistência.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class IdentityService {

  private final UserRepository userRepository;
  private final RegistrationRepository registrationRepository;
  private final EmailNormalizationService emailNormalizationService;

  /**
   * Cria o serviço com acesso exclusivo aos repositories internos.
   *
   * @param userRepository persistência da identidade global
   * @param registrationRepository persistência do processo temporário
   * @param emailNormalizationService normalizador canônico de e-mail
   */
  public IdentityService(
      UserRepository userRepository,
      RegistrationRepository registrationRepository,
      EmailNormalizationService emailNormalizationService) {
    this.userRepository = userRepository;
    this.registrationRepository = registrationRepository;
    this.emailNormalizationService = emailNormalizationService;
  }

  /**
   * Cria atomicamente uma identidade e seu processo pendente.
   *
   * <p>A constraint de e-mail é a autoridade final contra concorrência. A facade de cadastro
   * tratará a colisão relendo o vencedor quando implementar a convergência idempotente.
   *
   * @param email e-mail informado pela pessoa
   * @param method origem local ou Google
   * @param expiresAt limite absoluto de retenção da pendência
   * @return processo pendente persistido
   * @throws NullPointerException quando método ou expiração são nulos
   */
  @Transactional
  public RegistrationEntity createPendingIdentity(
      String email,
      RegistrationMethodEnum method,
      Instant expiresAt) {
    Objects.requireNonNull(method, "method must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    NormalizedEmailVO normalizedEmail = emailNormalizationService.normalize(email);

    UserEntity user = new UserEntity(
        normalizedEmail.email(),
        normalizedEmail.normalizedEmail(),
        UserStatusEnum.PENDING_VERIFICATION);
    UserEntity persistedUser = userRepository.save(user);
    RegistrationEntity registration = new RegistrationEntity(
        persistedUser,
        method,
        RegistrationStatusEnum.PENDING_VERIFICATION,
        expiresAt);
    return registrationRepository.save(registration);
  }

  /**
   * Localiza uma identidade sem expor diferenças de capitalização ou espaços externos.
   *
   * @param email e-mail a consultar
   * @return identidade correspondente ou vazio
   */
  @Transactional(readOnly = true)
  public Optional<UserEntity> findByEmail(String email) {
    NormalizedEmailVO normalizedEmail = emailNormalizationService.normalize(email);
    return userRepository.findByNormalizedEmail(normalizedEmail.normalizedEmail());
  }

  /**
   * Localiza e bloqueia uma identidade pelo e-mail normalizado.
   *
   * @param email e-mail já comprovado pelo provedor
   * @return identidade bloqueada ou vazio
   */
  @Transactional
  public Optional<UserEntity> findByEmailForUpdate(String email) {
    NormalizedEmailVO normalizedEmail = emailNormalizationService.normalize(email);
    return userRepository.findByNormalizedEmailForUpdate(
        normalizedEmail.normalizedEmail());
  }

  /**
   * Localiza uma pendência pelo e-mail global normalizado.
   *
   * @param email e-mail a consultar
   * @return processo pendente ou vazio
   */
  @Transactional(readOnly = true)
  public Optional<RegistrationEntity> findPendingRegistration(String email) {
    NormalizedEmailVO normalizedEmail = emailNormalizationService.normalize(email);
    return userRepository.findByNormalizedEmailAndStatus(
            normalizedEmail.normalizedEmail(),
            UserStatusEnum.PENDING_VERIFICATION)
        .flatMap(user -> registrationRepository.findByUserIdAndStatus(
            user.getId(),
            RegistrationStatusEnum.PENDING_VERIFICATION));
  }
}
