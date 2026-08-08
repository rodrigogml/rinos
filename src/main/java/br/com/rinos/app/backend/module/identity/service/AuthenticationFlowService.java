package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
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
    Objects.requireNonNull(purpose, "purpose must not be null");
    Objects.requireNonNull(requiredAssurance, "requiredAssurance must not be null");
    Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    Set<AuthenticationMethodEnum> methods = copyMethods(permittedMethods);
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
    methodRepository.saveAllAndFlush(methods.stream()
        .map(method -> new AuthenticationFlowMethodEntity(flow, method))
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
    auditService.record(
        flow.getUser(),
        null,
        flow.getCorrelationId(),
        IdentityEventTypeEnum.AUTHENTICATION_SUCCEEDED,
        null,
        null,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        flow.getPurpose().name(),
        occurredAt);
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

  private AuthenticationFlowEntity findLocked(String reference) {
    if (reference == null || reference.isBlank()) {
      return null;
    }
    return flowRepository.findByReferenceHashForUpdate(opaqueTokenService.hash(reference))
        .orElse(null);
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
      Set<AuthenticationMethodEnum> permittedMethods) {
    Objects.requireNonNull(permittedMethods, "permittedMethods must not be null");
    if (permittedMethods.isEmpty()
        || permittedMethods.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("permittedMethods must not be empty or contain null");
    }
    return EnumSet.copyOf(permittedMethods);
  }
}
