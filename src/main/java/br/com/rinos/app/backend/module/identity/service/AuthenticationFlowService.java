package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.AuthenticationFlowEntity;
import br.com.rinos.app.backend.module.identity.entity.AuthenticationFlowMethodEntity;
import br.com.rinos.app.backend.module.identity.entity.AuthenticationProofEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowMethodStateEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationProofStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.repository.AuthenticationFlowMethodRepository;
import br.com.rinos.app.backend.module.identity.repository.AuthenticationFlowRepository;
import br.com.rinos.app.backend.module.identity.repository.AuthenticationProofRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationCleanupResultVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowInspectionVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowSnapshotVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowVerifiedMethodVO;
import br.com.rinos.app.backend.module.identity.vo.IssuedAuthenticationFlowVO;
import br.eng.rodrigogml.rfw.authentication.service.RFWOpaqueTokenService;
import jakarta.persistence.EntityNotFoundException;

/**
 * Emite, inspeciona, consome, cancela e remove continuações opacas de autenticação.
 *
 * <p>Todo consumo bloqueia primeiro o fluxo. Provas filhas são encerradas sob o mesmo lock e
 * nenhuma referência bruta, digest ou entidade atravessa o resultado do serviço.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Service
@Lazy
public class AuthenticationFlowService {

  private final AuthenticationFlowRepository flowRepository;
  private final AuthenticationFlowMethodRepository methodRepository;
  private final AuthenticationProofRepository proofRepository;
  private final UserRepository userRepository;
  private final RFWOpaqueTokenService opaqueTokenService;
  private final IdentityAuditService auditService;

  public AuthenticationFlowService(
      AuthenticationFlowRepository flowRepository,
      AuthenticationFlowMethodRepository methodRepository,
      AuthenticationProofRepository proofRepository,
      UserRepository userRepository,
      RFWOpaqueTokenService opaqueTokenService,
      IdentityAuditService auditService) {
    this.flowRepository = flowRepository;
    this.methodRepository = methodRepository;
    this.proofRepository = proofRepository;
    this.userRepository = userRepository;
    this.opaqueTokenService = opaqueTokenService;
    this.auditService = auditService;
  }

  /**
   * Persiste um fluxo e seus métodos permitidos, devolvendo a referência bruta uma única vez.
   */
  @Transactional
  public IssuedAuthenticationFlowVO issue(
      Long userId,
      AuthenticationFlowPurposeEnum purpose,
      AuthenticationMethodEnum primaryMethod,
      AuthenticationAssuranceEnum requiredAssurance,
      Set<AuthenticationMethodEnum> permittedMethods,
      boolean persistentLoginRequested,
      Instant issuedAt,
      Instant expiresAt,
      UUID correlationId) {
    List<AuthenticationFlowVerifiedMethodVO> verifiedMethods = primaryMethod == null
        ? List.of()
        : List.of(new AuthenticationFlowVerifiedMethodVO(primaryMethod, issuedAt, null));
    return issue(
        userId,
        purpose,
        primaryMethod,
        requiredAssurance,
        permittedMethods,
        verifiedMethods,
        persistentLoginRequested,
        issuedAt,
        expiresAt,
        correlationId);
  }

  /**
   * Persiste um fluxo com a fotografia explícita dos métodos já comprovados.
   */
  @Transactional
  public IssuedAuthenticationFlowVO issue(
      Long userId,
      AuthenticationFlowPurposeEnum purpose,
      AuthenticationMethodEnum primaryMethod,
      AuthenticationAssuranceEnum requiredAssurance,
      Set<AuthenticationMethodEnum> permittedMethods,
      List<AuthenticationFlowVerifiedMethodVO> verifiedMethods,
      boolean persistentLoginRequested,
      Instant issuedAt,
      Instant expiresAt,
      UUID correlationId) {
    Objects.requireNonNull(purpose, "purpose must not be null");
    Objects.requireNonNull(requiredAssurance, "requiredAssurance must not be null");
    Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    Set<AuthenticationMethodEnum> methods = copyMethods(permittedMethods, verifiedMethods);
    UserEntity user = resolveUser(userId);
    String reference = opaqueTokenService.generate();
    AuthenticationFlowEntity flow = flowRepository.saveAndFlush(new AuthenticationFlowEntity(
        user,
        opaqueTokenService.hash(reference),
        purpose,
        primaryMethod,
        requiredAssurance,
        persistentLoginRequested,
        issuedAt,
        expiresAt,
        correlationId));
    java.util.Map<AuthenticationMethodEnum, AuthenticationFlowVerifiedMethodVO> verifiedByMethod =
        verifiedMethods.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
            AuthenticationFlowVerifiedMethodVO::method,
            method -> method));
    methodRepository.saveAllAndFlush(methods.stream()
        .map(method -> {
          AuthenticationFlowMethodEntity entity = new AuthenticationFlowMethodEntity(flow, method);
          AuthenticationFlowVerifiedMethodVO verified = verifiedByMethod.get(method);
          if (verified != null) {
            entity.markVerified(verified.verifiedAt(), verified.userVerification());
          }
          return entity;
        })
        .toList());
    auditService.record(
        user,
        null,
        correlationId,
        IdentityEventTypeEnum.AUTHENTICATION_ATTEMPTED,
        null,
        null,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        purpose.name(),
        issuedAt);
    return new IssuedAuthenticationFlowVO(reference, expiresAt, correlationId);
  }

  /**
   * Registra um método adicional previamente permitido e devolve a fotografia sob o mesmo lock.
   */
  @Transactional
  public AuthenticationFlowSnapshotVO verifyMethod(
      String reference,
      AuthenticationFlowPurposeEnum expectedPurpose,
      AuthenticationMethodEnum method,
      Instant verifiedAt,
      Boolean userVerification,
      Instant occurredAt) {
    Objects.requireNonNull(method, "method must not be null");
    Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    AuthenticationFlowEntity flow = findLocked(reference);
    if (flow == null || expectedPurpose == null || flow.getPurpose() != expectedPurpose) {
      return rejectedSnapshot();
    }
    AuthenticationFlowInspectionVO terminal = terminalOrExpire(flow, occurredAt);
    if (terminal != null) {
      return snapshot(flow, terminal.status());
    }
    AuthenticationFlowMethodEntity flowMethod = methodRepository
        .findByFlowIdAndMethodForUpdate(flow.getId(), method)
        .orElse(null);
    if (flowMethod == null) {
      flow.registerFailure();
      return snapshot(flow, AuthenticationOperationStatusEnum.REJECTED);
    }
    flowMethod.markVerified(verifiedAt, userVerification);
    return snapshot(flow, AuthenticationOperationStatusEnum.OPEN);
  }

  /** Inspeciona estados permitido/comprovado sob lock para uma decisão atômica superior. */
  @Transactional
  public AuthenticationFlowSnapshotVO snapshot(
      String reference,
      AuthenticationFlowPurposeEnum expectedPurpose,
      Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    AuthenticationFlowEntity flow = findLocked(reference);
    if (flow == null || expectedPurpose == null || flow.getPurpose() != expectedPurpose) {
      return rejectedSnapshot();
    }
    AuthenticationFlowInspectionVO terminal = terminalOrExpire(flow, occurredAt);
    return terminal == null
        ? snapshot(flow, AuthenticationOperationStatusEnum.OPEN)
        : snapshot(flow, terminal.status());
  }

  /** Inspeciona um fluxo já resolvido sem reintroduzir sua referência bruta. */
  @Transactional
  public AuthenticationFlowSnapshotVO snapshotById(
      Long flowId,
      AuthenticationFlowPurposeEnum expectedPurpose,
      Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    AuthenticationFlowEntity flow = findLocked(flowId);
    if (flow == null || expectedPurpose == null || flow.getPurpose() != expectedPurpose) {
      return rejectedSnapshot();
    }
    AuthenticationFlowInspectionVO terminal = terminalOrExpire(flow, occurredAt);
    return terminal == null
        ? snapshot(flow, AuthenticationOperationStatusEnum.OPEN)
        : snapshot(flow, terminal.status());
  }

  /**
   * Resolve somente o usuário proprietário para que a camada superior respeite a ordem de locks.
   */
  @Transactional(readOnly = true)
  public Optional<Long> resolveUserId(String reference) {
    if (reference == null || reference.isBlank()) {
      return Optional.empty();
    }
    return flowRepository.findByReferenceHash(opaqueTokenService.hash(reference))
        .map(AuthenticationFlowEntity::getUser)
        .map(UserEntity::getId);
  }

  /** Inspeciona o estado corrente sem consumir o fluxo. */
  @Transactional
  public AuthenticationFlowInspectionVO inspect(
      String reference,
      AuthenticationFlowPurposeEnum expectedPurpose,
      Instant occurredAt) {
    return access(reference, expectedPurpose, occurredAt, false);
  }

  /** Consome o fluxo uma única vez e encerra provas alternativas ainda abertas. */
  @Transactional
  public AuthenticationFlowInspectionVO consume(
      String reference,
      AuthenticationFlowPurposeEnum expectedPurpose,
      Instant occurredAt) {
    return access(reference, expectedPurpose, occurredAt, true);
  }

  /** Consome por ID um fluxo previamente resolvido pelo lifecycle da sessão. */
  @Transactional
  public AuthenticationFlowInspectionVO consumeById(
      Long flowId,
      AuthenticationFlowPurposeEnum expectedPurpose,
      Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    AuthenticationFlowEntity flow = findLocked(flowId);
    if (flow == null || expectedPurpose == null || flow.getPurpose() != expectedPurpose) {
      return AuthenticationFlowInspectionVO.rejected();
    }
    return access(flow, expectedPurpose, occurredAt, true);
  }

  /** Cancela uma continuação aberta e todas as suas provas abertas. */
  @Transactional
  public AuthenticationFlowInspectionVO cancel(
      String reference,
      AuthenticationFlowPurposeEnum expectedPurpose,
      Instant occurredAt) {
    AuthenticationFlowEntity flow = findLocked(reference);
    if (flow == null || expectedPurpose == null || flow.getPurpose() != expectedPurpose) {
      return AuthenticationFlowInspectionVO.rejected();
    }
    AuthenticationFlowInspectionVO terminal = terminalOrExpire(flow, occurredAt);
    if (terminal != null) {
      return terminal;
    }
    invalidateOpenProofs(flow, occurredAt);
    flow.invalidate(occurredAt);
    return view(flow, AuthenticationOperationStatusEnum.INVALIDATED);
  }

  /** Expira registros vencidos e remove somente estados terminais fora da retenção. */
  @Transactional
  public AuthenticationCleanupResultVO cleanup(Instant occurredAt, Instant retentionCutoff) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    Objects.requireNonNull(retentionCutoff, "retentionCutoff must not be null");
    if (retentionCutoff.isAfter(occurredAt)) {
      throw new IllegalArgumentException("retentionCutoff must not be after occurredAt");
    }
    List<AuthenticationFlowEntity> expired = flowRepository.findExpiredByStatusForUpdate(
        AuthenticationFlowStatusEnum.OPEN,
        occurredAt);
    expired.forEach(flow -> {
      invalidateOpenProofs(flow, occurredAt);
      flow.expire(occurredAt);
    });
    flowRepository.flush();
    int deleted = flowRepository.deleteTerminalBefore(
        AuthenticationFlowStatusEnum.OPEN,
        retentionCutoff);
    return new AuthenticationCleanupResultVO(expired.size(), deleted);
  }

  private AuthenticationFlowInspectionVO access(
      String reference,
      AuthenticationFlowPurposeEnum expectedPurpose,
      Instant occurredAt,
      boolean consume) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    AuthenticationFlowEntity flow = findLocked(reference);
    return access(flow, expectedPurpose, occurredAt, consume);
  }

  private AuthenticationFlowInspectionVO access(
      AuthenticationFlowEntity flow,
      AuthenticationFlowPurposeEnum expectedPurpose,
      Instant occurredAt,
      boolean consume) {
    if (flow == null || expectedPurpose == null) {
      return AuthenticationFlowInspectionVO.rejected();
    }
    if (flow.getPurpose() != expectedPurpose) {
      if (flow.getStatus() == AuthenticationFlowStatusEnum.OPEN) {
        flow.registerFailure();
      }
      return AuthenticationFlowInspectionVO.rejected();
    }
    AuthenticationFlowInspectionVO terminal = terminalOrExpire(flow, occurredAt);
    if (terminal != null) {
      return terminal;
    }
    if (consume && flow.getUser() == null) {
      flow.registerFailure();
      return AuthenticationFlowInspectionVO.rejected();
    }
    if (!consume) {
      return view(flow, AuthenticationOperationStatusEnum.OPEN);
    }
    invalidateOpenProofs(flow, occurredAt);
    flow.markUsed(occurredAt);
    return view(flow, AuthenticationOperationStatusEnum.USED);
  }

  private AuthenticationFlowInspectionVO terminalOrExpire(
      AuthenticationFlowEntity flow,
      Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    if (flow.getStatus() == AuthenticationFlowStatusEnum.OPEN
        && !occurredAt.isBefore(flow.getExpiresAt())) {
      invalidateOpenProofs(flow, occurredAt);
      flow.expire(occurredAt);
      return view(flow, AuthenticationOperationStatusEnum.EXPIRED);
    }
    return switch (flow.getStatus()) {
      case OPEN -> null;
      case USED -> view(flow, AuthenticationOperationStatusEnum.ALREADY_USED);
      case INVALIDATED -> view(flow, AuthenticationOperationStatusEnum.INVALIDATED);
      case EXPIRED -> view(flow, AuthenticationOperationStatusEnum.EXPIRED);
    };
  }

  private void invalidateOpenProofs(AuthenticationFlowEntity flow, Instant occurredAt) {
    List<AuthenticationProofEntity> proofs = proofRepository.findByFlowIdAndStatusForUpdate(
        flow.getId(),
        AuthenticationProofStatusEnum.OPEN);
    proofs.forEach(proof -> proof.invalidate(occurredAt));
    proofRepository.saveAll(proofs);
  }

  private AuthenticationFlowInspectionVO view(
      AuthenticationFlowEntity flow,
      AuthenticationOperationStatusEnum status) {
    Set<AuthenticationMethodEnum> methods = methodRepository
        .findByFlowIdOrderByMethod(flow.getId())
        .stream()
        .filter(method -> method.getState() == AuthenticationFlowMethodStateEnum.PERMITTED)
        .map(AuthenticationFlowMethodEntity::getMethod)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
    return new AuthenticationFlowInspectionVO(
        status,
        flow.getUser() == null ? null : flow.getUser().getId(),
        flow.getPurpose(),
        flow.getPrimaryMethod(),
        flow.getRequiredAssurance(),
        methods,
        flow.isPersistentLoginRequested(),
        flow.getExpiresAt(),
        flow.getCorrelationId());
  }

  private AuthenticationFlowSnapshotVO snapshot(
      AuthenticationFlowEntity flow,
      AuthenticationOperationStatusEnum status) {
    List<AuthenticationFlowMethodEntity> methods = methodRepository
        .findByFlowIdOrderByMethod(flow.getId());
    Set<AuthenticationMethodEnum> permitted = methods.stream()
        .filter(method -> method.getState() == AuthenticationFlowMethodStateEnum.PERMITTED)
        .map(AuthenticationFlowMethodEntity::getMethod)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
    List<AuthenticationFlowVerifiedMethodVO> verified = methods.stream()
        .filter(method -> method.getState() == AuthenticationFlowMethodStateEnum.VERIFIED)
        .map(method -> new AuthenticationFlowVerifiedMethodVO(
            method.getMethod(), method.getVerifiedAt(), method.getUserVerification()))
        .toList();
    return new AuthenticationFlowSnapshotVO(
        status,
        flow.getId(),
        flow.getUser() == null ? null : flow.getUser().getId(),
        flow.getPurpose(),
        flow.getPrimaryMethod(),
        flow.getRequiredAssurance(),
        permitted,
        verified,
        flow.isPersistentLoginRequested(),
        flow.getExpiresAt(),
        flow.getCorrelationId());
  }

  private static AuthenticationFlowSnapshotVO rejectedSnapshot() {
    return new AuthenticationFlowSnapshotVO(
        AuthenticationOperationStatusEnum.REJECTED,
        null,
        null,
        null,
        null,
        null,
        Set.of(),
        List.of(),
        false,
        null,
        null);
  }

  private AuthenticationFlowEntity findLocked(String reference) {
    if (reference == null || reference.isBlank()) {
      return null;
    }
    return flowRepository.findByReferenceHashForUpdate(opaqueTokenService.hash(reference))
        .orElse(null);
  }

  private AuthenticationFlowEntity findLocked(Long flowId) {
    return flowId == null || flowId <= 0
        ? null : flowRepository.findByIdForUpdate(flowId).orElse(null);
  }

  private UserEntity resolveUser(Long userId) {
    if (userId == null) {
      return null;
    }
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    return userRepository.findById(userId)
        .orElseThrow(() -> new EntityNotFoundException("User not found for authentication flow"));
  }

  private static Set<AuthenticationMethodEnum> copyMethods(
      Set<AuthenticationMethodEnum> permittedMethods,
      List<AuthenticationFlowVerifiedMethodVO> verifiedMethods) {
    Objects.requireNonNull(permittedMethods, "permittedMethods must not be null");
    Objects.requireNonNull(verifiedMethods, "verifiedMethods must not be null");
    if (permittedMethods.stream().anyMatch(Objects::isNull)
        || verifiedMethods.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("authentication methods must not contain null");
    }
    EnumSet<AuthenticationMethodEnum> methods = permittedMethods.isEmpty()
        ? EnumSet.noneOf(AuthenticationMethodEnum.class)
        : EnumSet.copyOf(permittedMethods);
    Set<AuthenticationMethodEnum> verified = verifiedMethods.stream()
        .map(AuthenticationFlowVerifiedMethodVO::method)
        .collect(java.util.stream.Collectors.toSet());
    if (verified.size() != verifiedMethods.size()) {
      throw new IllegalArgumentException("verifiedMethods must not contain duplicates");
    }
    methods.addAll(verified);
    if (methods.isEmpty()) {
      throw new IllegalArgumentException("authentication methods must not be empty");
    }
    return methods;
  }
}
