package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.AuthSessionEntity;
import br.com.rinos.app.backend.module.identity.entity.AuthSessionMethodEntity;
import br.com.rinos.app.backend.module.identity.entity.ReauthenticationContextEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthSessionStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.ReauthenticationOperationEnum;
import br.com.rinos.app.backend.module.identity.enums.ReauthenticationPolicyStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.ReauthenticationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.AuthenticationFlowRepository;
import br.com.rinos.app.backend.module.identity.repository.AuthSessionMethodRepository;
import br.com.rinos.app.backend.module.identity.repository.AuthSessionRepository;
import br.com.rinos.app.backend.module.identity.repository.ReauthenticationContextRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowInspectionVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowSnapshotVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowVerifiedMethodVO;
import br.com.rinos.app.backend.module.identity.vo.IssuedAuthenticationFlowVO;
import br.com.rinos.app.backend.module.identity.vo.ReauthenticationDecisionVO;
import br.com.rinos.app.backend.module.identity.vo.ReauthenticationPolicyDecisionVO;
import br.com.rinos.app.backend.module.identity.vo.VerifiedAuthSessionMethodVO;
import br.com.rinos.app.backend.module.identity.vo.VerifiedReauthenticationProofVO;
import br.com.rinos.app.config.AuthenticationMfaPropertiesConfig;

/**
 * Coordena uma prova recente vinculada à sessão e à operação sensível originais.
 *
 * <p>A referência do desafio é consumida uma única vez. A conclusão somente atualiza a
 * sessão corrente; a operação retomada deve executar novamente suas próprias invariantes e
 * controle de concorrência sobre o alvo concreto.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Service
@Lazy
public class ReauthenticationService {

  private final UserRepository userRepository;
  private final AuthSessionRepository sessionRepository;
  private final AuthSessionMethodRepository sessionMethodRepository;
  private final AuthenticationFlowRepository flowRepository;
  private final ReauthenticationContextRepository contextRepository;
  private final AuthenticationFlowService flowService;
  private final AuthenticationMethodAvailabilityService availabilityService;
  private final ReauthenticationProofService proofService;
  private final AuthenticationAssurancePolicyService assurancePolicy;
  private final ReauthenticationPolicyService reauthenticationPolicy;
  private final IdentityReferenceService referenceService;
  private final IdentityAuditService auditService;
  private final AuthenticationMfaPropertiesConfig mfaProperties;

  /** Cria o coordenador sobre persistência, políticas e auditoria globais. */
  public ReauthenticationService(
      UserRepository userRepository,
      AuthSessionRepository sessionRepository,
      AuthSessionMethodRepository sessionMethodRepository,
      AuthenticationFlowRepository flowRepository,
      ReauthenticationContextRepository contextRepository,
      AuthenticationFlowService flowService,
      AuthenticationMethodAvailabilityService availabilityService,
      ReauthenticationProofService proofService,
      AuthenticationAssurancePolicyService assurancePolicy,
      ReauthenticationPolicyService reauthenticationPolicy,
      IdentityReferenceService referenceService,
      IdentityAuditService auditService,
      AuthenticationMfaPropertiesConfig mfaProperties) {
    this.userRepository = userRepository;
    this.sessionRepository = sessionRepository;
    this.sessionMethodRepository = sessionMethodRepository;
    this.flowRepository = flowRepository;
    this.contextRepository = contextRepository;
    this.flowService = flowService;
    this.availabilityService = availabilityService;
    this.proofService = proofService;
    this.assurancePolicy = assurancePolicy;
    this.reauthenticationPolicy = reauthenticationPolicy;
    this.referenceService = referenceService;
    this.auditService = auditService;
    this.mfaProperties = mfaProperties;
  }

  /**
   * Verifica a garantia da sessão atual e emite um desafio somente quando necessário.
   *
   * @param userId identidade autenticada
   * @param currentSessionReference referência não autenticadora da sessão corrente
   * @param operation operação do catálogo fechado
   * @param occurredAt instante UTC da decisão
   * @return garantia já recente, desafio vinculado ou negação
   */
  @Transactional
  public ReauthenticationDecisionVO begin(
      Long userId,
      UUID currentSessionReference,
      ReauthenticationOperationEnum operation,
      Instant occurredAt) {
    Objects.requireNonNull(operation, "operation must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    UserEntity user = lockActiveUser(userId);
    if (user == null || currentSessionReference == null) {
      return terminal(ReauthenticationStatusEnum.ACCESS_DENIED);
    }
    AuthSessionEntity session = sessionRepository.findByUserIdAndPublicReferenceForUpdate(
        user.getId(), referenceService.encode(currentSessionReference)).orElse(null);
    if (!isUsable(session, occurredAt)) {
      return terminal(ReauthenticationStatusEnum.ACCESS_DENIED);
    }
    List<VerifiedAuthSessionMethodVO> sessionMethods = sessionMethods(session);
    Set<AuthenticationMethodEnum> availableMethods = availabilityService.availableMethods(user.getId())
        .stream()
        .filter(proofService.supportedMethods()::contains)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
    ReauthenticationPolicyDecisionVO decision = reauthenticationPolicy.evaluate(
        operation,
        session.getAssuranceLevel(),
        session.getLastStrongAuthAt(),
        sessionMethods,
        availableMethods,
        occurredAt);
    if (decision.status() == ReauthenticationPolicyStatusEnum.ALREADY_RECENT) {
      return terminal(ReauthenticationStatusEnum.ALREADY_RECENT);
    }
    if (decision.status() == ReauthenticationPolicyStatusEnum.ACCESS_DENIED) {
      return terminal(ReauthenticationStatusEnum.ACCESS_DENIED);
    }
    UUID correlationId = UUID.randomUUID();
    Instant expiresAt = occurredAt.plus(mfaProperties.challengeValidity());
    IssuedAuthenticationFlowVO issued = flowService.issue(
        user.getId(),
        AuthenticationFlowPurposeEnum.REAUTHENTICATION,
        null,
        operation.requiredAssurance(),
        decision.allowedMethods(),
        false,
        occurredAt,
        expiresAt,
        correlationId);
    AuthenticationFlowSnapshotVO snapshot = flowService.snapshot(
        issued.reference(), AuthenticationFlowPurposeEnum.REAUTHENTICATION, occurredAt);
    if (snapshot.flowId() == null) {
      throw new IllegalStateException("Issued reauthentication flow cannot be resolved");
    }
    contextRepository.saveAndFlush(new ReauthenticationContextEntity(
        flowRepository.getReferenceById(snapshot.flowId()), session, operation));
    audit(user, correlationId, IdentityEventTypeEnum.AUTHENTICATION_CHALLENGE_ISSUED,
        operation, occurredAt);
    return new ReauthenticationDecisionVO(
        ReauthenticationStatusEnum.CHALLENGE_REQUIRED,
        issued.reference(),
        decision.operationLabelKey(),
        expiresAt,
        decision.allowedMethods());
  }

  /**
   * Valida e consome a prova transitória, liberando uma única retomada.
   *
   * <p>O verificador do método e o consumo executam na mesma transação. O material da prova não
   * é persistido, auditado nem devolvido.
   *
   * @param userId identidade autenticada
   * @param currentSessionReference sessão que iniciou e concluirá o desafio
   * @param challengeReference referência opaca emitida em {@link #begin}
   * @param verifiedMethod método cuja prova acabou de ser validada
   * @param proof prova efêmera que será descartada depois da verificação
   * @param occurredAt instante UTC da comprovação
   * @return conclusão ou estado terminal seguro
   */
  @Transactional
  public ReauthenticationDecisionVO complete(
      Long userId,
      UUID currentSessionReference,
      String challengeReference,
      AuthenticationMethodEnum verifiedMethod,
      String proof,
      Instant occurredAt) {
    Objects.requireNonNull(verifiedMethod, "verifiedMethod must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    VerifiedReauthenticationProofVO verifiedProof = proofService
        .verify(userId, verifiedMethod, proof, occurredAt)
        .orElse(null);
    if (!proofService.supportedMethods().contains(verifiedMethod) || verifiedProof == null
        || verifiedProof.method() != verifiedMethod) {
      return terminal(ReauthenticationStatusEnum.REJECTED);
    }
    return completeVerified(
        userId,
        currentSessionReference,
        challengeReference,
        verifiedMethod,
        verifiedProof.userVerification(),
        occurredAt);
  }

  private ReauthenticationDecisionVO completeVerified(
      Long userId,
      UUID currentSessionReference,
      String challengeReference,
      AuthenticationMethodEnum verifiedMethod,
      Boolean userVerification,
      Instant occurredAt) {
    Long ownerId = flowService.resolveUserId(challengeReference).orElse(null);
    if (ownerId == null || !ownerId.equals(userId)) {
      return terminal(ReauthenticationStatusEnum.REJECTED);
    }
    UserEntity user = lockActiveUser(ownerId);
    if (user == null) {
      return terminal(ReauthenticationStatusEnum.ACCESS_DENIED);
    }
    AuthenticationFlowSnapshotVO snapshot = flowService.snapshot(
        challengeReference, AuthenticationFlowPurposeEnum.REAUTHENTICATION, occurredAt);
    ReauthenticationDecisionVO terminal = terminalFrom(snapshot.status());
    if (terminal != null) {
      return terminal;
    }
    ReauthenticationContextEntity context = contextRepository
        .findByAuthenticationFlowIdForUpdate(snapshot.flowId())
        .orElse(null);
    if (context == null || currentSessionReference == null) {
      return terminal(ReauthenticationStatusEnum.CONFLICT);
    }
    AuthSessionEntity session = sessionRepository
        .findByIdForUpdate(context.getAuthSession().getId())
        .orElse(null);
    if (!belongsTo(session, user, currentSessionReference) || !isUsable(session, occurredAt)) {
      return terminal(ReauthenticationStatusEnum.ACCESS_DENIED);
    }
    ReauthenticationOperationEnum operation = context.getOperation();
    Set<AuthenticationMethodEnum> availableMethods = availabilityService.availableMethods(user.getId());
    if (!operation.allowedMethods().contains(verifiedMethod)
        || !snapshot.permittedMethods().contains(verifiedMethod)
        || !availableMethods.contains(verifiedMethod)) {
      return terminal(ReauthenticationStatusEnum.CONFLICT);
    }
    AuthenticationFlowSnapshotVO verified = flowService.verifyMethod(
        challengeReference,
        AuthenticationFlowPurposeEnum.REAUTHENTICATION,
        verifiedMethod,
        occurredAt,
        userVerification,
        occurredAt);
    AuthenticationAssuranceEnum achieved = assurancePolicy.calculate(verified.verifiedMethods());
    if (!assurancePolicy.satisfies(achieved, operation.requiredAssurance())) {
      return terminal(ReauthenticationStatusEnum.REJECTED);
    }
    AuthenticationFlowInspectionVO consumed = flowService.consume(
        challengeReference, AuthenticationFlowPurposeEnum.REAUTHENTICATION, occurredAt);
    if (consumed.status() != AuthenticationOperationStatusEnum.USED) {
      return terminalFromInspection(consumed.status());
    }
    session.recordStrongAuthentication(achieved, occurredAt);
    appendSessionMethod(session, verifiedMethod, occurredAt, userVerification);
    audit(user, snapshot.correlationId(), IdentityEventTypeEnum.AUTHENTICATION_CHALLENGE_CONSUMED,
        operation, occurredAt);
    return terminal(ReauthenticationStatusEnum.COMPLETED);
  }

  /**
   * Cancela somente um desafio pertencente à identidade e sessão correntes.
   *
   * @return estado terminal seguro; nenhuma operação original é executada
   */
  @Transactional
  public ReauthenticationDecisionVO cancel(
      Long userId,
      UUID currentSessionReference,
      String challengeReference,
      Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    Long ownerId = flowService.resolveUserId(challengeReference).orElse(null);
    if (ownerId == null || !ownerId.equals(userId)) {
      return terminal(ReauthenticationStatusEnum.REJECTED);
    }
    UserEntity user = lockActiveUser(ownerId);
    AuthenticationFlowSnapshotVO snapshot = flowService.snapshot(
        challengeReference, AuthenticationFlowPurposeEnum.REAUTHENTICATION, occurredAt);
    ReauthenticationDecisionVO terminal = terminalFrom(snapshot.status());
    if (terminal != null) {
      return terminal;
    }
    ReauthenticationContextEntity context = contextRepository
        .findByAuthenticationFlowIdForUpdate(snapshot.flowId())
        .orElse(null);
    AuthSessionEntity session = context == null ? null : sessionRepository
        .findByIdForUpdate(context.getAuthSession().getId())
        .orElse(null);
    if (user == null || context == null
        || !belongsTo(session, user, currentSessionReference)) {
      return terminal(ReauthenticationStatusEnum.ACCESS_DENIED);
    }
    AuthenticationFlowInspectionVO cancelled = flowService.cancel(
        challengeReference, AuthenticationFlowPurposeEnum.REAUTHENTICATION, occurredAt);
    return cancelled.status() == AuthenticationOperationStatusEnum.INVALIDATED
        ? terminal(ReauthenticationStatusEnum.REJECTED)
        : terminalFromInspection(cancelled.status());
  }

  private List<VerifiedAuthSessionMethodVO> sessionMethods(AuthSessionEntity session) {
    return sessionMethodRepository.findBySessionIdOrderByFactorOrder(session.getId()).stream()
        .map(method -> new VerifiedAuthSessionMethodVO(
            method.getMethod(), method.getVerifiedAt(), method.getUserVerification()))
        .toList();
  }

  private void appendSessionMethod(
      AuthSessionEntity session,
      AuthenticationMethodEnum method,
      Instant verifiedAt,
      Boolean userVerification) {
    List<AuthSessionMethodEntity> methods =
        sessionMethodRepository.findBySessionIdOrderByFactorOrder(session.getId());
    int factorOrder = methods.stream()
        .mapToInt(AuthSessionMethodEntity::getFactorOrder)
        .max()
        .orElse(0) + 1;
    sessionMethodRepository.saveAndFlush(new AuthSessionMethodEntity(
        session, method, factorOrder, verifiedAt, userVerification));
  }

  private void audit(
      UserEntity user,
      UUID correlationId,
      IdentityEventTypeEnum eventType,
      ReauthenticationOperationEnum operation,
      Instant occurredAt) {
    auditService.record(
        user,
        null,
        correlationId,
        eventType,
        null,
        null,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        operation.name(),
        occurredAt);
  }

  private UserEntity lockActiveUser(Long userId) {
    if (userId == null || userId <= 0) {
      return null;
    }
    return userRepository.findByIdForUpdate(userId)
        .filter(user -> user.getStatus() == UserStatusEnum.ACTIVE)
        .orElse(null);
  }

  private boolean belongsTo(
      AuthSessionEntity session,
      UserEntity user,
      UUID currentSessionReference) {
    return session != null
        && user != null
        && currentSessionReference != null
        && user.getId().equals(session.getUser().getId())
        && java.security.MessageDigest.isEqual(
            session.getPublicReference(), referenceService.encode(currentSessionReference));
  }

  private static boolean isUsable(AuthSessionEntity session, Instant occurredAt) {
    return session != null
        && session.getStatus() == AuthSessionStatusEnum.ACTIVE
        && occurredAt.isBefore(session.getAbsoluteExpiresAt())
        && occurredAt.isBefore(session.getIdleExpiresAt());
  }

  private static ReauthenticationDecisionVO terminalFrom(
      AuthenticationOperationStatusEnum status) {
    if (status == AuthenticationOperationStatusEnum.OPEN) {
      return null;
    }
    return terminalFromInspection(status);
  }

  private static ReauthenticationDecisionVO terminalFromInspection(
      AuthenticationOperationStatusEnum status) {
    return terminal(switch (status) {
      case EXPIRED -> ReauthenticationStatusEnum.EXPIRED;
      case USED, ALREADY_USED, INVALIDATED -> ReauthenticationStatusEnum.CONFLICT;
      case OPEN, REJECTED -> ReauthenticationStatusEnum.REJECTED;
    });
  }

  private static ReauthenticationDecisionVO terminal(ReauthenticationStatusEnum status) {
    return ReauthenticationDecisionVO.terminal(status);
  }
}
