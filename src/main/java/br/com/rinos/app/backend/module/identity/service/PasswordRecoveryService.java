package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.PasswordRecoveryEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.LocalCredentialStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.OriginOperationEnum;
import br.com.rinos.app.backend.module.identity.enums.OriginReservationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.PasswordRecoveryOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.PasswordRecoveryStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationEmailTemplateEnum;
import br.com.rinos.app.backend.module.identity.repository.LocalCredentialRepository;
import br.com.rinos.app.backend.module.identity.repository.PasswordRecoveryRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.NormalizedEmailVO;
import br.com.rinos.app.backend.module.identity.vo.OriginAddressVO;
import br.com.rinos.app.backend.module.identity.vo.OriginReservationResultVO;
import br.com.rinos.app.backend.module.identity.vo.PasswordRecoveryOperationVO;
import br.com.rinos.app.backend.module.identity.vo.VerificationEmailDispatchRequestVO;
import br.com.rinos.app.config.PasswordRecoveryPropertiesConfig;

/**
 * Emite e consome provas de recuperação mantendo identidade, origem e credencial sob lock.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-02
 */
@Service
@Lazy
public class PasswordRecoveryService {

  private final UserRepository userRepository;
  private final LocalCredentialRepository credentialRepository;
  private final PasswordRecoveryRepository recoveryRepository;
  private final EmailNormalizationService emailNormalizationService;
  private final OriginAddressService originAddressService;
  private final OriginLimitService originLimitService;
  private final VerificationTokenService tokenService;
  private final LocalCredentialService credentialService;
  private final IdentityAuditService auditService;
  private final VerificationEmailDispatchService dispatchService;
  private final PublicApplicationUriService publicUriService;
  private final PasswordRecoveryPropertiesConfig properties;

  /**
   * Cria o serviço sobre dependências globais e configurações fixas da instalação.
   */
  public PasswordRecoveryService(
      UserRepository userRepository,
      LocalCredentialRepository credentialRepository,
      PasswordRecoveryRepository recoveryRepository,
      EmailNormalizationService emailNormalizationService,
      OriginAddressService originAddressService,
      OriginLimitService originLimitService,
      VerificationTokenService tokenService,
      LocalCredentialService credentialService,
      IdentityAuditService auditService,
      VerificationEmailDispatchService dispatchService,
      PublicApplicationUriService publicUriService,
      PasswordRecoveryPropertiesConfig properties) {
    this.userRepository = userRepository;
    this.credentialRepository = credentialRepository;
    this.recoveryRepository = recoveryRepository;
    this.emailNormalizationService = emailNormalizationService;
    this.originAddressService = originAddressService;
    this.originLimitService = originLimitService;
    this.tokenService = tokenService;
    this.credentialService = credentialService;
    this.auditService = auditService;
    this.dispatchService = dispatchService;
    this.publicUriService = publicUriService;
    this.properties = properties;
  }

  /**
   * Reserva a origem e, quando elegível, emite uma única prova para o usuário local ativo.
   *
   * @param identifier e-mail informado
   * @param canonicalOrigin origem canônica
   * @param locale idioma do e-mail
   * @param correlationId correlação técnica
   * @param occurredAt instante UTC
   * @return resultado neutro e eventual despacho pós-commit
   */
  @Transactional
  public PasswordRecoveryOperationVO issue(
      String identifier,
      String canonicalOrigin,
      Locale locale,
      UUID correlationId,
      Instant occurredAt) {
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    OriginReservationResultVO originReservation = reserveOrigin(
        canonicalOrigin,
        OriginOperationEnum.PASSWORD_RECOVERY_REQUEST,
        properties.requestLimit(),
        properties.requestWindow());
    if (originReservation.status() == OriginReservationStatusEnum.BLOCKED) {
      return new PasswordRecoveryOperationVO(
          PasswordRecoveryOperationStatusEnum.RATE_LIMITED,
          originReservation.blockedUntil(),
          null);
    }

    NormalizedEmailVO normalizedEmail;
    try {
      normalizedEmail = emailNormalizationService.normalize(identifier);
    } catch (IllegalArgumentException | NullPointerException exception) {
      return acceptedWithoutDispatch();
    }
    UserEntity user = userRepository.findByNormalizedEmailForUpdate(
        normalizedEmail.normalizedEmail()).orElse(null);
    if (user == null || user.getStatus() != UserStatusEnum.ACTIVE
        || credentialRepository.findByUserIdAndStatus(
            user.getId(), LocalCredentialStatusEnum.ACTIVE).isEmpty()) {
      return acceptedWithoutDispatch();
    }
    long recentIssues = recoveryRepository.countByUserIdAndIssuedAtGreaterThanEqual(
        user.getId(),
        occurredAt.minus(properties.requestWindow()));
    if (recentIssues >= properties.requestLimit()) {
      return acceptedWithoutDispatch();
    }

    invalidateOpen(user.getId(), occurredAt);
    String token = tokenService.generate();
    Instant expiresAt = occurredAt.plus(properties.validity());
    recoveryRepository.saveAndFlush(new PasswordRecoveryEntity(
        user,
        tokenService.hash(token),
        occurredAt,
        expiresAt));
    auditService.record(
        user,
        null,
        correlationId,
        IdentityEventTypeEnum.PASSWORD_RECOVERY_REQUESTED,
        null,
        null,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        "PROOF_ISSUED",
        occurredAt);
    return new PasswordRecoveryOperationVO(
        PasswordRecoveryOperationStatusEnum.ACCEPTED,
        null,
        dispatchService.scheduleAfterCommit(new VerificationEmailDispatchRequestVO(
            user.getEmail(),
            publicUriService.passwordResetUri(token),
            null,
            expiresAt,
            locale,
            correlationId,
            VerificationEmailTemplateEnum.PASSWORD_RECOVERY)));
  }

  /**
   * Substitui a credencial e encerra todas as provas abertas em uma única transação.
   *
   * @param proof prova opaca
   * @param encodedPassword hash Argon2id já validado
   * @param canonicalOrigin origem canônica
   * @param correlationId correlação técnica
   * @param occurredAt instante UTC
   * @return resultado sem segredo
   */
  @Transactional
  public PasswordRecoveryOperationVO reset(
      String proof,
      String encodedPassword,
      String canonicalOrigin,
      UUID correlationId,
      Instant occurredAt) {
    Objects.requireNonNull(encodedPassword, "encodedPassword must not be null");
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    OriginReservationResultVO originReservation = reserveOrigin(
        canonicalOrigin,
        OriginOperationEnum.PASSWORD_RECOVERY_ATTEMPT,
        properties.attemptLimit(),
        properties.attemptWindow());
    if (originReservation.status() == OriginReservationStatusEnum.BLOCKED) {
      return new PasswordRecoveryOperationVO(
          PasswordRecoveryOperationStatusEnum.RATE_LIMITED,
          originReservation.blockedUntil(),
          null);
    }
    if (proof == null || proof.isBlank()) {
      return status(PasswordRecoveryOperationStatusEnum.INVALID_PROOF);
    }
    PasswordRecoveryEntity recovery = recoveryRepository.findByTokenHashForUpdate(
        tokenService.hash(proof)).orElse(null);
    if (recovery == null || !tokenService.matches(proof, recovery.getTokenHash())
        || recovery.getStatus() != PasswordRecoveryStatusEnum.OPEN) {
      return status(PasswordRecoveryOperationStatusEnum.INVALID_PROOF);
    }
    if (!occurredAt.isBefore(recovery.getExpiresAt())) {
      recovery.setStatus(PasswordRecoveryStatusEnum.EXPIRED);
      recovery.setInvalidatedAt(occurredAt);
      return status(PasswordRecoveryOperationStatusEnum.EXPIRED_PROOF);
    }
    UserEntity user = recovery.getUser();
    if (user.getStatus() != UserStatusEnum.ACTIVE) {
      recovery.setStatus(PasswordRecoveryStatusEnum.INVALIDATED);
      recovery.setInvalidatedAt(occurredAt);
      return status(PasswordRecoveryOperationStatusEnum.INVALID_PROOF);
    }
    credentialService.replace(user, encodedPassword);
    recovery.setStatus(PasswordRecoveryStatusEnum.USED);
    recovery.setUsedAt(occurredAt);
    List<PasswordRecoveryEntity> open = recoveryRepository.findByUserIdAndStatusForUpdate(
        user.getId(), PasswordRecoveryStatusEnum.OPEN);
    open.stream()
        .filter(candidate -> !Objects.equals(candidate.getId(), recovery.getId()))
        .forEach(candidate -> invalidate(candidate, occurredAt));
    auditService.record(
        user,
        null,
        correlationId,
        IdentityEventTypeEnum.PASSWORD_RECOVERY_COMPLETED,
        null,
        null,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        "PASSWORD_REPLACED",
        occurredAt);
    return status(PasswordRecoveryOperationStatusEnum.COMPLETED);
  }

  private OriginReservationResultVO reserveOrigin(
      String canonicalOrigin,
      OriginOperationEnum operation,
      int limit,
      java.time.Duration window) {
    OriginAddressVO origin = originAddressService.normalize(canonicalOrigin);
    return originLimitService.reserve(origin, operation, limit, window);
  }

  private void invalidateOpen(Long userId, Instant occurredAt) {
    recoveryRepository.findByUserIdAndStatusForUpdate(
        userId, PasswordRecoveryStatusEnum.OPEN)
        .forEach(recovery -> invalidate(recovery, occurredAt));
  }

  private static void invalidate(PasswordRecoveryEntity recovery, Instant occurredAt) {
    recovery.setStatus(PasswordRecoveryStatusEnum.INVALIDATED);
    recovery.setInvalidatedAt(occurredAt);
  }

  private static PasswordRecoveryOperationVO acceptedWithoutDispatch() {
    return status(PasswordRecoveryOperationStatusEnum.ACCEPTED);
  }

  private static PasswordRecoveryOperationVO status(
      PasswordRecoveryOperationStatusEnum status) {
    return new PasswordRecoveryOperationVO(status, null, null);
  }
}
