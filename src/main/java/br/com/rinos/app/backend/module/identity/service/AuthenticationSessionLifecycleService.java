package br.com.rinos.app.backend.module.identity.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.AuthSessionEntity;
import br.com.rinos.app.backend.module.identity.entity.AuthSessionMethodEntity;
import br.com.rinos.app.backend.module.identity.entity.AuthenticationFlowEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationSessionLifecycleStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthSessionRevocationReasonEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthSessionStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.AuthSessionMethodRepository;
import br.com.rinos.app.backend.module.identity.repository.AuthSessionRepository;
import br.com.rinos.app.backend.module.identity.repository.AuthenticationFlowRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowSnapshotVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationSessionLifecycleVO;
import br.com.rinos.app.backend.module.identity.vo.LegalRequirementStatusVO;
import br.com.rinos.app.config.AuthenticationSessionPropertiesConfig;
import br.eng.rodrigogml.rfw.authentication.service.RFWOpaqueTokenService;

/**
 * Coordena a sessão global com a publicação do contexto local do Spring Security.
 *
 * <p>A ordem de locks é sempre usuário → fluxo → sessão. A preparação nunca concede
 * acesso; apenas a publicação consome o fluxo, ativa a sessão e registra o sucesso.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Service
@Lazy
public class AuthenticationSessionLifecycleService {

  private static final String DIGEST_VERSION = "SHA256";

  private final AuthSessionRepository sessionRepository;
  private final AuthSessionMethodRepository methodRepository;
  private final AuthenticationFlowRepository flowRepository;
  private final UserRepository userRepository;
  private final AuthenticationFlowService flowService;
  private final AuthenticationAssurancePolicyService assurancePolicy;
  private final LegalConsentService legalConsentService;
  private final RFWOpaqueTokenService opaqueTokenService;
  private final IdentityReferenceService referenceService;
  private final IdentityAuditService auditService;
  private final AuthenticationSessionPropertiesConfig sessionProperties;

  /** Cria a autoridade transacional do lifecycle persistente. */
  public AuthenticationSessionLifecycleService(
      AuthSessionRepository sessionRepository,
      AuthSessionMethodRepository methodRepository,
      AuthenticationFlowRepository flowRepository,
      UserRepository userRepository,
      AuthenticationFlowService flowService,
      AuthenticationAssurancePolicyService assurancePolicy,
      LegalConsentService legalConsentService,
      RFWOpaqueTokenService opaqueTokenService,
      IdentityReferenceService referenceService,
      IdentityAuditService auditService,
      AuthenticationSessionPropertiesConfig sessionProperties) {
    this.sessionRepository = sessionRepository;
    this.methodRepository = methodRepository;
    this.flowRepository = flowRepository;
    this.userRepository = userRepository;
    this.flowService = flowService;
    this.assurancePolicy = assurancePolicy;
    this.legalConsentService = legalConsentService;
    this.opaqueTokenService = opaqueTokenService;
    this.referenceService = referenceService;
    this.auditService = auditService;
    this.sessionProperties = sessionProperties;
  }

  /**
   * Persiste idempotentemente uma sessão preparada para um fluxo pronto.
   *
   * @throws IllegalStateException quando fluxo, identidade, garantia ou gate legal não estão prontos
   */
  @Transactional
  public AuthenticationSessionLifecycleVO prepare(
      String flowReference,
      AuthenticationFlowPurposeEnum purpose,
      long expectedUserId,
      boolean persistent,
      byte[] originAddress,
      String userAgent,
      Instant occurredAt) {
    Objects.requireNonNull(purpose, "purpose must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    Long ownerId = flowService.resolveUserId(flowReference).orElse(null);
    if (ownerId == null || ownerId != expectedUserId) {
      throw new IllegalStateException("Authentication flow does not belong to the principal");
    }
    UserEntity user = lockUser(ownerId);
    requireActive(user);
    AuthenticationFlowSnapshotVO snapshot = flowService.snapshot(
        flowReference, purpose, occurredAt);
    if (snapshot.flowId() == null) {
      throw new IllegalStateException("Authentication flow is unavailable");
    }
    AuthSessionEntity existing = sessionRepository
        .findByAuthenticationFlowIdForUpdate(snapshot.flowId())
        .orElse(null);
    if (existing != null) {
      if (existing.getStatus() == AuthSessionStatusEnum.ACTIVE) {
        return view(existing, snapshot.correlationId());
      }
      if (existing.getStatus() == AuthSessionStatusEnum.PREPARED) {
        validateReady(snapshot, user, persistent, occurredAt);
        return view(existing, snapshot.correlationId());
      }
      throw new IllegalStateException("Authentication flow already has a terminal session");
    }
    validateReady(snapshot, user, persistent, occurredAt);
    Instant authenticatedAt = snapshot.verifiedMethods().stream()
        .map(method -> method.verifiedAt())
        .max(Comparator.naturalOrder())
        .orElseThrow(() -> new IllegalStateException("Authentication evidence is required"));
    Duration absoluteDuration = persistent
        ? sessionProperties.rememberedAbsolute() : sessionProperties.normalAbsolute();
    Duration idleDuration = persistent
        ? sessionProperties.rememberedIdle() : sessionProperties.normalIdle();
    Instant absoluteExpiresAt = occurredAt.plus(absoluteDuration);
    Instant idleExpiresAt = minimum(occurredAt.plus(idleDuration), absoluteExpiresAt);
    String reservedSelector = opaqueTokenService.generate();
    String reservedValidator = opaqueTokenService.generate();
    AuthenticationFlowEntity flow = flowRepository.getReferenceById(snapshot.flowId());
    AuthSessionEntity session = sessionRepository.saveAndFlush(new AuthSessionEntity(
        user,
        flow,
        referenceService.encode(referenceService.generate()),
        opaqueTokenService.hash(reservedSelector),
        opaqueTokenService.hash(reservedValidator),
        DIGEST_VERSION,
        persistent,
        snapshot.primaryMethod(),
        assurancePolicy.calculate(snapshot.verifiedMethods()),
        authenticatedAt,
        absoluteExpiresAt,
        idleExpiresAt,
        null,
        originAddress,
        userAgent == null ? null : opaqueTokenService.hash(userAgent)));
    int factorOrder = 1;
    for (var method : snapshot.verifiedMethods()) {
      methodRepository.save(new AuthSessionMethodEntity(
          session,
          method.method(),
          factorOrder++,
          method.verifiedAt(),
          method.userVerification()));
    }
    methodRepository.flush();
    return view(session, snapshot.correlationId());
  }

  /** Consome o fluxo e torna utilizável a sessão somente após o contexto local ser salvo. */
  @Transactional
  public AuthenticationSessionLifecycleVO publish(UUID sessionReference, Instant occurredAt) {
    SessionCoordinates coordinates = resolve(sessionReference);
    if (coordinates == null) {
      return terminal(AuthenticationSessionLifecycleStatusEnum.INVALID);
    }
    UserEntity user = lockUser(coordinates.userId());
    if (coordinates.flowId() != null) {
      flowRepository.findByIdForUpdate(coordinates.flowId());
    }
    AuthSessionEntity session = lockSession(sessionReference);
    if (session == null) {
      return terminal(AuthenticationSessionLifecycleStatusEnum.INVALID);
    }
    if (session.getStatus() == AuthSessionStatusEnum.ACTIVE) {
      return view(session, coordinates.correlationId());
    }
    if (session.getStatus() != AuthSessionStatusEnum.PREPARED) {
      return terminal(map(session.getStatus()));
    }
    if (user.getStatus() != UserStatusEnum.ACTIVE || coordinates.flowId() == null) {
      abortPrepared(session, occurredAt);
      return terminal(AuthenticationSessionLifecycleStatusEnum.BLOCKED);
    }
    AuthenticationFlowSnapshotVO snapshot = flowService.snapshotById(
        coordinates.flowId(), coordinates.purpose(), occurredAt);
    validateReady(snapshot, user, session.isRemembered(), occurredAt);
    AuthenticationOperationStatusEnum consumed = flowService.consumeById(
        coordinates.flowId(), coordinates.purpose(), occurredAt).status();
    if (consumed != AuthenticationOperationStatusEnum.USED) {
      throw new IllegalStateException("Authentication flow could not be consumed");
    }
    session.activate(occurredAt);
    audit(user, snapshot.correlationId(), IdentityEventTypeEnum.AUTHENTICATION_SUCCEEDED,
        coordinates.purpose().name(), occurredAt);
    audit(user, snapshot.correlationId(), IdentityEventTypeEnum.AUTHENTICATION_SESSION_CREATED,
        session.getPrimaryMethod().name(), occurredAt);
    return view(session, snapshot.correlationId());
  }

  /** Valida o estado persistente sem confiar no contexto local. */
  @Transactional
  public AuthenticationSessionLifecycleVO validate(UUID sessionReference, Instant occurredAt) {
    SessionCoordinates coordinates = resolve(sessionReference);
    if (coordinates == null) {
      return terminal(AuthenticationSessionLifecycleStatusEnum.INVALID);
    }
    UserEntity user = lockUser(coordinates.userId());
    if (coordinates.flowId() != null) {
      flowRepository.findByIdForUpdate(coordinates.flowId());
    }
    AuthSessionEntity session = lockSession(sessionReference);
    if (session == null || session.getStatus() == AuthSessionStatusEnum.PREPARED) {
      return terminal(AuthenticationSessionLifecycleStatusEnum.INVALID);
    }
    if (session.getStatus() != AuthSessionStatusEnum.ACTIVE) {
      return terminal(map(session.getStatus()));
    }
    if (user.getStatus() != UserStatusEnum.ACTIVE) {
      revoke(session, AuthSessionRevocationReasonEnum.USER_NOT_ACTIVE, occurredAt,
          coordinates.correlationId());
      return terminal(AuthenticationSessionLifecycleStatusEnum.BLOCKED);
    }
    if (!occurredAt.isBefore(session.getAbsoluteExpiresAt())
        || !occurredAt.isBefore(session.getIdleExpiresAt())) {
      session.expire(occurredAt);
      audit(user, coordinates.correlationId(), IdentityEventTypeEnum.AUTHENTICATION_SESSION_EXPIRED,
          "EXPIRY", occurredAt);
      return terminal(AuthenticationSessionLifecycleStatusEnum.EXPIRED);
    }
    return view(session, coordinates.correlationId());
  }

  /** Compensa idempotentemente uma preparação ou publicação que falhou. */
  @Transactional
  public void abort(UUID sessionReference, Instant occurredAt) {
    SessionCoordinates coordinates = resolve(sessionReference);
    if (coordinates == null) {
      return;
    }
    UserEntity user = lockUser(coordinates.userId());
    if (coordinates.flowId() != null) {
      flowRepository.findByIdForUpdate(coordinates.flowId());
    }
    AuthSessionEntity session = lockSession(sessionReference);
    if (session == null) {
      return;
    }
    if (session.getStatus() == AuthSessionStatusEnum.PREPARED) {
      abortPrepared(session, occurredAt);
    } else if (session.getStatus() == AuthSessionStatusEnum.ACTIVE) {
      revoke(session, AuthSessionRevocationReasonEnum.PUBLICATION_ABORTED, occurredAt,
          coordinates.correlationId());
    }
  }

  /** Encerra idempotentemente a sessão atual. */
  @Transactional
  public void close(UUID sessionReference, Instant occurredAt) {
    SessionCoordinates coordinates = resolve(sessionReference);
    if (coordinates == null) {
      return;
    }
    lockUser(coordinates.userId());
    if (coordinates.flowId() != null) {
      flowRepository.findByIdForUpdate(coordinates.flowId());
    }
    AuthSessionEntity session = lockSession(sessionReference);
    if (session != null && session.getStatus() == AuthSessionStatusEnum.ACTIVE) {
      revoke(session, AuthSessionRevocationReasonEnum.USER_REQUEST, occurredAt,
          coordinates.correlationId());
    }
  }

  private void validateReady(
      AuthenticationFlowSnapshotVO snapshot,
      UserEntity user,
      boolean persistent,
      Instant occurredAt) {
    if (snapshot.status() != AuthenticationOperationStatusEnum.OPEN
        || !Objects.equals(snapshot.userId(), user.getId())
        || snapshot.primaryMethod() == null
        || snapshot.verifiedMethods().isEmpty()
        || snapshot.persistentLoginRequested() != persistent) {
      throw new IllegalStateException("Authentication flow is not ready for a session");
    }
    AuthenticationAssuranceEnum achieved = assurancePolicy.calculate(snapshot.verifiedMethods());
    if (!assurancePolicy.satisfies(achieved, snapshot.requiredAssurance())) {
      throw new IllegalStateException("Authentication assurance is insufficient");
    }
    LegalRequirementStatusVO legal = legalConsentService.evaluateRequiredConsents(
        user.getId(), occurredAt);
    if (legal.requiresConsent()) {
      throw new IllegalStateException("Required legal consent is pending");
    }
  }

  private SessionCoordinates resolve(UUID sessionReference) {
    Objects.requireNonNull(sessionReference, "sessionReference must not be null");
    AuthSessionEntity session = sessionRepository
        .findByPublicReference(referenceService.encode(sessionReference))
        .orElse(null);
    if (session == null) {
      return null;
    }
    AuthenticationFlowEntity flow = session.getAuthenticationFlow();
    return new SessionCoordinates(
        session.getUser().getId(),
        flow == null ? null : flow.getId(),
        flow == null ? null : flow.getPurpose(),
        flow == null ? UUID.randomUUID() : flow.getCorrelationId());
  }

  private AuthSessionEntity lockSession(UUID sessionReference) {
    return sessionRepository.findByPublicReferenceForUpdate(
        referenceService.encode(sessionReference)).orElse(null);
  }

  private UserEntity lockUser(Long userId) {
    return userRepository.findByIdForUpdate(userId)
        .orElseThrow(() -> new IllegalStateException("Authentication session owner is missing"));
  }

  private static void requireActive(UserEntity user) {
    if (user.getStatus() != UserStatusEnum.ACTIVE) {
      throw new IllegalStateException("Authentication session requires an active user");
    }
  }

  private void abortPrepared(AuthSessionEntity session, Instant occurredAt) {
    session.revoke(occurredAt, AuthSessionRevocationReasonEnum.PREPARATION_ABORTED.name());
    session.detachAuthenticationFlow();
  }

  private void revoke(
      AuthSessionEntity session,
      AuthSessionRevocationReasonEnum reason,
      Instant occurredAt,
      UUID correlationId) {
    session.revoke(occurredAt, reason.name());
    audit(session.getUser(), correlationId, IdentityEventTypeEnum.AUTHENTICATION_SESSION_REVOKED,
        reason.name(), occurredAt);
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

  private AuthenticationSessionLifecycleVO view(
      AuthSessionEntity session,
      UUID correlationId) {
    return new AuthenticationSessionLifecycleVO(
        session.getStatus() == AuthSessionStatusEnum.PREPARED
            ? AuthenticationSessionLifecycleStatusEnum.PREPARED
            : AuthenticationSessionLifecycleStatusEnum.ACTIVE,
        decodeReference(session.getPublicReference()),
        session.getUser().getId(),
        session.getUser().getEmail(),
        session.isRemembered(),
        session.getAbsoluteExpiresAt(),
        correlationId);
  }

  private static AuthenticationSessionLifecycleVO terminal(
      AuthenticationSessionLifecycleStatusEnum status) {
    return AuthenticationSessionLifecycleVO.terminal(status);
  }

  private static AuthenticationSessionLifecycleStatusEnum map(AuthSessionStatusEnum status) {
    return switch (status) {
      case ACTIVE -> AuthenticationSessionLifecycleStatusEnum.ACTIVE;
      case PREPARED -> AuthenticationSessionLifecycleStatusEnum.INVALID;
      case EXPIRED -> AuthenticationSessionLifecycleStatusEnum.EXPIRED;
      case REVOKED -> AuthenticationSessionLifecycleStatusEnum.REVOKED;
    };
  }

  private static UUID decodeReference(byte[] reference) {
    java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(reference);
    return new UUID(buffer.getLong(), buffer.getLong());
  }

  private static Instant minimum(Instant first, Instant second) {
    return first.isBefore(second) ? first : second;
  }

  private record SessionCoordinates(
      Long userId,
      Long flowId,
      AuthenticationFlowPurposeEnum purpose,
      UUID correlationId) {
  }
}
