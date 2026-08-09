package br.com.rinos.app.backend.module.identity.service;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.AuthSessionEntity;
import br.com.rinos.app.backend.module.identity.entity.AuthSessionMethodEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthSessionAccessStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthSessionRevocationReasonEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthSessionStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.AuthSessionMethodRepository;
import br.com.rinos.app.backend.module.identity.repository.AuthSessionRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.AuthSessionAccessVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationCleanupResultVO;
import br.com.rinos.app.backend.module.identity.vo.AuthSessionSummaryVO;
import br.com.rinos.app.backend.module.identity.vo.IssuedAuthSessionVO;
import br.com.rinos.app.backend.module.identity.vo.IssuedPersistentLoginVO;
import br.com.rinos.app.backend.module.identity.vo.VerifiedAuthSessionMethodVO;
import br.com.rinos.app.config.AuthenticationRetentionPropertiesConfig;
import br.com.rinos.app.config.AuthenticationSessionPropertiesConfig;
import br.eng.rodrigogml.rfw.authentication.service.RFWOpaqueTokenService;
import jakarta.persistence.EntityNotFoundException;

/**
 * Emite, valida, rotaciona, lista e revoga sessões globais opacas.
 *
 * <p>O seletor e o validator brutos existem apenas no cookie. O seletor persistido serve somente
 * para localização e a referência pública serve somente para gestão. As operações por usuário
 * bloqueiam primeiro a identidade para preservar a ordem global de locks.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Service
@Lazy
public class AuthSessionService {

  private static final String DIGEST_VERSION = "SHA256";
  private static final String COOKIE_SEPARATOR = ".";

  private final AuthSessionRepository sessionRepository;
  private final AuthSessionMethodRepository methodRepository;
  private final UserRepository userRepository;
  private final RFWOpaqueTokenService opaqueTokenService;
  private final IdentityReferenceService referenceService;
  private final IdentityAuditService auditService;
  private final AuthenticationSessionPropertiesConfig sessionProperties;
  private final AuthenticationRetentionPropertiesConfig retentionProperties;

  /** Cria o serviço com políticas fixas e persistência global. */
  public AuthSessionService(
      AuthSessionRepository sessionRepository,
      AuthSessionMethodRepository methodRepository,
      UserRepository userRepository,
      RFWOpaqueTokenService opaqueTokenService,
      IdentityReferenceService referenceService,
      IdentityAuditService auditService,
      AuthenticationSessionPropertiesConfig sessionProperties,
      AuthenticationRetentionPropertiesConfig retentionProperties) {
    this.sessionRepository = sessionRepository;
    this.methodRepository = methodRepository;
    this.userRepository = userRepository;
    this.opaqueTokenService = opaqueTokenService;
    this.referenceService = referenceService;
    this.auditService = auditService;
    this.sessionProperties = sessionProperties;
    this.retentionProperties = retentionProperties;
  }

  /**
   * Cria a sessão somente para identidade ativa e entrega o cookie bruto uma única vez.
   */
  @Transactional
  public IssuedAuthSessionVO issue(
      Long userId,
      AuthenticationMethodEnum primaryMethod,
      AuthenticationAssuranceEnum assuranceLevel,
      List<VerifiedAuthSessionMethodVO> verifiedMethods,
      boolean remembered,
      Instant authenticatedAt,
      String deviceDescription,
      byte[] originAddress,
      byte[] userAgentDigest,
      UUID correlationId) {
    Objects.requireNonNull(primaryMethod, "primaryMethod must not be null");
    Objects.requireNonNull(assuranceLevel, "assuranceLevel must not be null");
    Objects.requireNonNull(authenticatedAt, "authenticatedAt must not be null");
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    List<VerifiedAuthSessionMethodVO> methods = validateMethods(verifiedMethods, primaryMethod);
    UserEntity user = lockActiveUser(userId);
    Duration absoluteDuration = remembered
        ? sessionProperties.rememberedAbsolute() : sessionProperties.normalAbsolute();
    Duration idleDuration = remembered
        ? sessionProperties.rememberedIdle() : sessionProperties.normalIdle();
    Instant absoluteExpiresAt = authenticatedAt.plus(absoluteDuration);
    Instant idleExpiresAt = minimum(authenticatedAt.plus(idleDuration), absoluteExpiresAt);
    String selector = opaqueTokenService.generate();
    String validator = opaqueTokenService.generate();
    UUID publicReference = referenceService.generate();
    AuthSessionEntity session = sessionRepository.saveAndFlush(new AuthSessionEntity(
        user,
        referenceService.encode(publicReference),
        opaqueTokenService.hash(selector),
        opaqueTokenService.hash(validator),
        DIGEST_VERSION,
        remembered,
        primaryMethod,
        assuranceLevel,
        authenticatedAt,
        absoluteExpiresAt,
        idleExpiresAt,
        deviceDescription,
        originAddress,
        userAgentDigest));
    int factorOrder = 1;
    for (VerifiedAuthSessionMethodVO method : methods) {
      methodRepository.save(new AuthSessionMethodEntity(
          session,
          method.method(),
          factorOrder++,
          method.verifiedAt(),
          method.userVerification()));
    }
    methodRepository.flush();
    audit(
        user,
        correlationId,
        IdentityEventTypeEnum.AUTHENTICATION_SESSION_CREATED,
        primaryMethod.name(),
        authenticatedAt);
    return new IssuedAuthSessionVO(
        cookie(selector, validator), publicReference, absoluteExpiresAt, idleExpiresAt);
  }

  /**
   * Valida uma sessão, amortiza sua atividade e opcionalmente rotaciona o validator.
   *
   * <p>Um seletor conhecido com validator inválido revoga a sessão por suspeita de replay.
   */
  @Transactional
  public AuthSessionAccessVO access(
      String cookieValue,
      boolean rotateValidator,
      Instant occurredAt,
      UUID correlationId) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    String[] parts = splitCookie(cookieValue);
    if (parts == null) {
      return AuthSessionAccessVO.rejected();
    }
    byte[] selectorHash = opaqueTokenService.hash(parts[0]);
    AuthSessionEntity located = sessionRepository.findBySelectorHash(selectorHash).orElse(null);
    if (located == null) {
      return AuthSessionAccessVO.rejected();
    }
    lockUser(located.getUser().getId());
    AuthSessionEntity session = sessionRepository
        .findBySelectorHashForUpdate(selectorHash)
        .orElse(null);
    if (session == null || session.getStatus() == AuthSessionStatusEnum.PREPARED) {
      return AuthSessionAccessVO.rejected();
    }
    if (session.getStatus() == AuthSessionStatusEnum.REVOKED) {
      return view(session, AuthSessionAccessStatusEnum.REVOKED, null);
    }
    if (session.getStatus() == AuthSessionStatusEnum.EXPIRED) {
      return view(session, AuthSessionAccessStatusEnum.EXPIRED, null);
    }
    if (!opaqueTokenService.matches(parts[1], session.getValidatorDigest())) {
      revoke(session, AuthSessionRevocationReasonEnum.VALIDATOR_MISMATCH, occurredAt, correlationId);
      return view(session, AuthSessionAccessStatusEnum.REPLAY_DETECTED, null);
    }
    if (session.getUser().getStatus() != UserStatusEnum.ACTIVE) {
      revoke(session, AuthSessionRevocationReasonEnum.USER_NOT_ACTIVE, occurredAt, correlationId);
      return view(session, AuthSessionAccessStatusEnum.BLOCKED, null);
    }
    if (!occurredAt.isBefore(session.getAbsoluteExpiresAt())
        || !occurredAt.isBefore(session.getIdleExpiresAt())) {
      expire(session, occurredAt, correlationId);
      return view(session, AuthSessionAccessStatusEnum.EXPIRED, null);
    }
    if (!occurredAt.isBefore(
        session.getLastActivityAt().plus(sessionProperties.activityRefreshInterval()))) {
      Duration idleDuration = session.isRemembered()
          ? sessionProperties.rememberedIdle() : sessionProperties.normalIdle();
      session.refreshActivity(occurredAt, idleDuration);
    }
    if (!rotateValidator) {
      return view(session, AuthSessionAccessStatusEnum.ACTIVE, null);
    }
    String validator = opaqueTokenService.generate();
    session.rotateValidator(opaqueTokenService.hash(validator), DIGEST_VERSION);
    return view(
        session,
        AuthSessionAccessStatusEnum.ROTATED,
        cookie(parts[0], validator));
  }

  /**
   * Substitui os valores reservados na preparação e entrega o cookie somente para uma sessão
   * persistente, ativa e ainda vigente.
   */
  @Transactional
  public IssuedPersistentLoginVO issuePersistentCredential(
      UUID publicReference,
      Instant occurredAt) {
    Objects.requireNonNull(publicReference, "publicReference must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    byte[] encodedReference = referenceService.encode(publicReference);
    AuthSessionEntity located = sessionRepository.findByPublicReference(encodedReference)
        .orElseThrow(() -> new IllegalStateException("Authentication session is unavailable"));
    UserEntity user = lockActiveUser(located.getUser().getId());
    AuthSessionEntity session = sessionRepository.findByPublicReferenceForUpdate(encodedReference)
        .orElseThrow(() -> new IllegalStateException("Authentication session is unavailable"));
    if (session.getUser().getId() == null
        || !session.getUser().getId().equals(user.getId())
        || session.getStatus() != AuthSessionStatusEnum.ACTIVE
        || !session.isRemembered()
        || !occurredAt.isBefore(session.getAbsoluteExpiresAt())
        || !occurredAt.isBefore(session.getIdleExpiresAt())) {
      throw new IllegalStateException("Authentication session cannot issue a persistent credential");
    }
    String selector = opaqueTokenService.generate();
    String validator = opaqueTokenService.generate();
    session.replaceAuthenticator(
        opaqueTokenService.hash(selector),
        opaqueTokenService.hash(validator),
        DIGEST_VERSION);
    sessionRepository.flush();
    return new IssuedPersistentLoginVO(
        cookie(selector, validator),
        publicReference,
        session.getAbsoluteExpiresAt());
  }

  /** Lista sessões do usuário sem material autenticador ou endereço de origem. */
  @Transactional(readOnly = true)
  public List<AuthSessionSummaryVO> list(Long userId) {
    requirePositive(userId, "userId");
    return sessionRepository.findByUserIdForManagement(userId).stream()
        .map(this::summary)
        .toList();
  }

  /** Revoga uma sessão pela referência de gestão de modo idempotente. */
  @Transactional
  public boolean revoke(
      Long userId,
      UUID publicReference,
      AuthSessionRevocationReasonEnum reason,
      Instant occurredAt,
      UUID correlationId) {
    UserEntity user = lockUser(userId);
    Objects.requireNonNull(publicReference, "publicReference must not be null");
    AuthSessionEntity session = sessionRepository.findByUserIdAndPublicReferenceForUpdate(
        user.getId(), referenceService.encode(publicReference)).orElse(null);
    if (session == null || session.getStatus() != AuthSessionStatusEnum.ACTIVE) {
      return false;
    }
    revoke(session, reason, occurredAt, correlationId);
    return true;
  }

  /** Revoga todas as sessões ativas, ou todas exceto a referência preservada. */
  @Transactional
  public int revokeAll(
      Long userId,
      UUID preservedReference,
      AuthSessionRevocationReasonEnum reason,
      Instant occurredAt,
      UUID correlationId) {
    UserEntity user = lockUser(userId);
    Objects.requireNonNull(reason, "reason must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    byte[] preserved = preservedReference == null ? null : referenceService.encode(preservedReference);
    int revoked = 0;
    for (AuthSessionEntity session : sessionRepository.findByUserIdAndStatusForUpdate(
        user.getId(), AuthSessionStatusEnum.ACTIVE)) {
      if (preserved == null
          || !java.security.MessageDigest.isEqual(preserved, session.getPublicReference())) {
        revoke(session, reason, occurredAt, correlationId);
        revoked++;
      }
    }
    return revoked;
  }

  /** Expira limites vencidos e remove sessões terminais fora da retenção configurada. */
  @Transactional
  public AuthenticationCleanupResultVO cleanup(Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    List<AuthSessionEntity> expiredActive = sessionRepository.findExpiredByStatusForUpdate(
        AuthSessionStatusEnum.ACTIVE, occurredAt);
    for (AuthSessionEntity session : expiredActive) {
      expire(session, occurredAt, UUID.randomUUID());
    }
    List<AuthSessionEntity> expiredPrepared = sessionRepository.findExpiredByStatusForUpdate(
        AuthSessionStatusEnum.PREPARED, occurredAt);
    expiredPrepared.forEach(session -> session.expire(occurredAt));
    sessionRepository.flush();
    int deleted = sessionRepository.deleteTerminalBefore(
        AuthSessionStatusEnum.ACTIVE,
        occurredAt.minus(retentionProperties.terminalSessions()));
    return new AuthenticationCleanupResultVO(
        expiredActive.size() + expiredPrepared.size(), deleted);
  }

  private void revoke(
      AuthSessionEntity session,
      AuthSessionRevocationReasonEnum reason,
      Instant occurredAt,
      UUID correlationId) {
    Objects.requireNonNull(reason, "reason must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    session.revoke(occurredAt, reason.name());
    audit(
        session.getUser(),
        correlationId,
        IdentityEventTypeEnum.AUTHENTICATION_SESSION_REVOKED,
        reason.name(),
        occurredAt);
  }

  private void expire(AuthSessionEntity session, Instant occurredAt, UUID correlationId) {
    session.expire(occurredAt);
    audit(
        session.getUser(),
        correlationId,
        IdentityEventTypeEnum.AUTHENTICATION_SESSION_EXPIRED,
        "EXPIRY",
        occurredAt);
  }

  private void audit(
      UserEntity user,
      UUID correlationId,
      IdentityEventTypeEnum eventType,
      String reason,
      Instant occurredAt) {
    auditService.record(
        user,
        null,
        correlationId,
        eventType,
        null,
        null,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        reason,
        occurredAt);
  }

  private AuthSessionAccessVO view(
      AuthSessionEntity session,
      AuthSessionAccessStatusEnum status,
      String rotatedCookieValue) {
    return new AuthSessionAccessVO(
        status,
        session.getUser().getId(),
        session.getUser().getEmail(),
        decodeReference(session.getPublicReference()),
        session.getAssuranceLevel(),
        session.getLastStrongAuthAt(),
        session.getAbsoluteExpiresAt(),
        session.getIdleExpiresAt(),
        rotatedCookieValue);
  }

  private AuthSessionSummaryVO summary(AuthSessionEntity session) {
    return new AuthSessionSummaryVO(
        decodeReference(session.getPublicReference()),
        session.isRemembered(),
        session.getStatus(),
        session.getPrimaryMethod(),
        session.getAssuranceLevel(),
        session.getAuthenticatedAt(),
        session.getLastActivityAt(),
        session.getAbsoluteExpiresAt(),
        session.getDeviceDescription());
  }

  private UserEntity lockActiveUser(Long userId) {
    UserEntity user = lockUser(userId);
    if (user.getStatus() != UserStatusEnum.ACTIVE) {
      throw new IllegalStateException("Authentication session requires an active user");
    }
    return user;
  }

  private UserEntity lockUser(Long userId) {
    requirePositive(userId, "userId");
    return userRepository.findByIdForUpdate(userId)
        .orElseThrow(() -> new EntityNotFoundException("User not found for authentication session"));
  }

  private static List<VerifiedAuthSessionMethodVO> validateMethods(
      List<VerifiedAuthSessionMethodVO> verifiedMethods,
      AuthenticationMethodEnum primaryMethod) {
    Objects.requireNonNull(verifiedMethods, "verifiedMethods must not be null");
    if (verifiedMethods.isEmpty() || verifiedMethods.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("verifiedMethods must not be empty or contain null");
    }
    Set<AuthenticationMethodEnum> uniqueMethods = new HashSet<>();
    for (VerifiedAuthSessionMethodVO method : verifiedMethods) {
      if (!uniqueMethods.add(method.method())) {
        throw new IllegalArgumentException("verifiedMethods must not contain duplicates");
      }
    }
    if (!uniqueMethods.contains(primaryMethod)) {
      throw new IllegalArgumentException("verifiedMethods must contain primaryMethod");
    }
    return List.copyOf(verifiedMethods);
  }

  private static String[] splitCookie(String cookieValue) {
    if (cookieValue == null || cookieValue.isBlank()) {
      return null;
    }
    String[] parts = cookieValue.split("\\.", -1);
    if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
      return null;
    }
    return parts;
  }

  private static String cookie(String selector, String validator) {
    return selector + COOKIE_SEPARATOR + validator;
  }

  private static UUID decodeReference(byte[] reference) {
    ByteBuffer buffer = ByteBuffer.wrap(reference);
    return new UUID(buffer.getLong(), buffer.getLong());
  }

  private static Instant minimum(Instant first, Instant second) {
    return first.isBefore(second) ? first : second;
  }

  private static void requirePositive(Long value, String name) {
    if (value == null || value <= 0) {
      throw new IllegalArgumentException(name + " must be positive");
    }
  }
}
