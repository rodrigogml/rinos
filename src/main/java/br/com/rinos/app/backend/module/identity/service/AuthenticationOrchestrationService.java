package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationOrchestrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowSnapshotVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowVerifiedMethodVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationOrchestrationDecisionVO;
import br.com.rinos.app.backend.module.identity.vo.IssuedAuthenticationFlowVO;
import br.com.rinos.app.backend.module.identity.vo.LegalRequirementStatusVO;

/**
 * Orquestra fatores, garantia e gate legal sem publicar {@code SecurityContext}.
 *
 * <p>A ordem de lock é sempre usuário → fluxo → método/prova. Apenas {@link #complete}
 * consome o fluxo como autenticação bem-sucedida; resultados READY ainda não autenticam.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Service
@Lazy
public class AuthenticationOrchestrationService {

  private final AuthenticationFlowService flowService;
  private final AuthenticationAssurancePolicyService assurancePolicy;
  private final LegalConsentService legalConsentService;
  private final UserRepository userRepository;

  /** Cria o orquestrador sobre serviços persistentes e políticas puras. */
  public AuthenticationOrchestrationService(
      AuthenticationFlowService flowService,
      AuthenticationAssurancePolicyService assurancePolicy,
      LegalConsentService legalConsentService,
      UserRepository userRepository) {
    this.flowService = flowService;
    this.assurancePolicy = assurancePolicy;
    this.legalConsentService = legalConsentService;
    this.userRepository = userRepository;
  }

  /** Inicia um fluxo somente depois que o primeiro fator foi validado. */
  @Transactional
  public AuthenticationOrchestrationDecisionVO start(
      long userId,
      AuthenticationMethodEnum primaryMethod,
      AuthenticationAssuranceEnum requiredAssurance,
      Set<AuthenticationMethodEnum> permittedMethods,
      boolean persistentLoginRequested,
      Instant verifiedAt,
      Boolean userVerification,
      Instant issuedAt,
      Instant expiresAt,
      UUID correlationId) {
    UserEntity user = lockActiveUser(userId);
    if (user == null) {
      return rejected();
    }
    AuthenticationFlowVerifiedMethodVO primary = new AuthenticationFlowVerifiedMethodVO(
        primaryMethod, verifiedAt, userVerification);
    IssuedAuthenticationFlowVO issued = flowService.issue(
        user.getId(),
        AuthenticationFlowPurposeEnum.SIGN_IN,
        primaryMethod,
        requiredAssurance,
        permittedMethods,
        List.of(primary),
        persistentLoginRequested,
        issuedAt,
        expiresAt,
        correlationId);
    AuthenticationFlowSnapshotVO snapshot = flowService.snapshot(
        issued.reference(), AuthenticationFlowPurposeEnum.SIGN_IN, issuedAt);
    return decide(user, issued.reference(), snapshot, issuedAt);
  }

  /** Acrescenta ao fluxo uma prova já validada pelo serviço especializado. */
  @Transactional
  public AuthenticationOrchestrationDecisionVO advance(
      String reference,
      AuthenticationMethodEnum method,
      Instant verifiedAt,
      Boolean userVerification,
      Instant occurredAt) {
    UserEntity user = lockActiveOwner(reference);
    if (user == null) {
      return rejected();
    }
    AuthenticationFlowSnapshotVO snapshot = flowService.verifyMethod(
        reference,
        AuthenticationFlowPurposeEnum.SIGN_IN,
        method,
        verifiedAt,
        userVerification,
        occurredAt);
    return decide(user, reference, snapshot, occurredAt);
  }

  /**
   * Consome uma conclusão pronta uma única vez, imediatamente antes do lifecycle RFW.
   */
  @Transactional
  public AuthenticationOrchestrationDecisionVO complete(
      String reference,
      Instant occurredAt) {
    UserEntity user = lockActiveOwner(reference);
    if (user == null) {
      return rejected();
    }
    AuthenticationFlowSnapshotVO snapshot = flowService.snapshot(
        reference, AuthenticationFlowPurposeEnum.SIGN_IN, occurredAt);
    AuthenticationOrchestrationDecisionVO decision = decide(
        user, reference, snapshot, occurredAt);
    if (decision.status() != AuthenticationOrchestrationStatusEnum.READY) {
      return decision;
    }
    AuthenticationOperationStatusEnum consumed = flowService.consume(
        reference, AuthenticationFlowPurposeEnum.SIGN_IN, occurredAt).status();
    if (consumed != AuthenticationOperationStatusEnum.USED) {
      return mapTerminal(consumed);
    }
    return result(
        AuthenticationOrchestrationStatusEnum.COMPLETED,
        null,
        user,
        decision.achievedAssurance(),
        Set.of(),
        decision.verifiedMethods(),
        Set.of(),
        decision.persistentLoginRequested(),
        decision.expiresAt(),
        decision.correlationId());
  }

  /** Cancela idempotentemente um fluxo de login sem produzir evento de sucesso. */
  @Transactional
  public AuthenticationOrchestrationDecisionVO cancel(String reference, Instant occurredAt) {
    UserEntity user = lockOwner(reference);
    if (user == null) {
      return rejected();
    }
    AuthenticationOperationStatusEnum status = flowService.cancel(
        reference, AuthenticationFlowPurposeEnum.SIGN_IN, occurredAt).status();
    if (status == AuthenticationOperationStatusEnum.REJECTED) {
      status = flowService.cancel(
          reference, AuthenticationFlowPurposeEnum.LEGAL_CONSENT, occurredAt).status();
    }
    return switch (status) {
      case INVALIDATED, ALREADY_USED -> result(
          AuthenticationOrchestrationStatusEnum.CANCELLED,
          null, null, null, Set.of(), List.of(), Set.of(), false, null, null);
      case EXPIRED -> terminal(AuthenticationOrchestrationStatusEnum.EXPIRED);
      default -> rejected();
    };
  }

  private AuthenticationOrchestrationDecisionVO decide(
      UserEntity user,
      String reference,
      AuthenticationFlowSnapshotVO snapshot,
      Instant occurredAt) {
    if (snapshot.status() != AuthenticationOperationStatusEnum.OPEN) {
      return mapTerminal(snapshot.status());
    }
    if (!Objects.equals(snapshot.userId(), user.getId())
        || user.getStatus() != UserStatusEnum.ACTIVE
        || snapshot.verifiedMethods().isEmpty()) {
      return rejected();
    }
    AuthenticationAssuranceEnum achieved = assurancePolicy.calculate(snapshot.verifiedMethods());
    if (!assurancePolicy.satisfies(achieved, snapshot.requiredAssurance())) {
      if (snapshot.permittedMethods().isEmpty()) {
        return result(
            AuthenticationOrchestrationStatusEnum.CONFLICT,
            null, null, achieved, Set.of(), snapshot.verifiedMethods(), Set.of(),
            snapshot.persistentLoginRequested(), snapshot.expiresAt(), snapshot.correlationId());
      }
      return result(
          AuthenticationOrchestrationStatusEnum.CHALLENGE_REQUIRED,
          reference, null, achieved, snapshot.permittedMethods(), snapshot.verifiedMethods(),
          Set.of(), snapshot.persistentLoginRequested(), snapshot.expiresAt(),
          snapshot.correlationId());
    }
    LegalRequirementStatusVO legalStatus;
    try {
      legalStatus = legalConsentService.evaluateRequiredConsents(user.getId(), occurredAt);
    } catch (RuntimeException unavailableCatalog) {
      return result(
          AuthenticationOrchestrationStatusEnum.UNAVAILABLE,
          reference, null, achieved, Set.of(), snapshot.verifiedMethods(), Set.of(),
          snapshot.persistentLoginRequested(), snapshot.expiresAt(), snapshot.correlationId());
    }
    if (legalStatus.requiresConsent()) {
      return moveToLegalGate(user, reference, snapshot, achieved, legalStatus, occurredAt);
    }
    return result(
        AuthenticationOrchestrationStatusEnum.READY,
        reference,
        user,
        achieved,
        Set.of(),
        snapshot.verifiedMethods(),
        Set.of(),
        snapshot.persistentLoginRequested(),
        snapshot.expiresAt(),
        snapshot.correlationId());
  }

  private AuthenticationOrchestrationDecisionVO moveToLegalGate(
      UserEntity user,
      String sourceReference,
      AuthenticationFlowSnapshotVO source,
      AuthenticationAssuranceEnum achieved,
      LegalRequirementStatusVO legalStatus,
      Instant occurredAt) {
    if (source.purpose() == AuthenticationFlowPurposeEnum.LEGAL_CONSENT) {
      return result(
          AuthenticationOrchestrationStatusEnum.LEGAL_CONSENT_REQUIRED,
          sourceReference,
          null,
          achieved,
          Set.of(),
          source.verifiedMethods(),
          Set.copyOf(legalStatus.missingRequiredVersionIds()),
          source.persistentLoginRequested(),
          source.expiresAt(),
          source.correlationId());
    }
    flowService.cancel(sourceReference, AuthenticationFlowPurposeEnum.SIGN_IN, occurredAt);
    IssuedAuthenticationFlowVO legalFlow = flowService.issue(
        user.getId(),
        AuthenticationFlowPurposeEnum.LEGAL_CONSENT,
        source.primaryMethod(),
        source.requiredAssurance(),
        Set.of(),
        source.verifiedMethods(),
        source.persistentLoginRequested(),
        occurredAt,
        source.expiresAt(),
        source.correlationId());
    return result(
        AuthenticationOrchestrationStatusEnum.LEGAL_CONSENT_REQUIRED,
        legalFlow.reference(),
        null,
        achieved,
        Set.of(),
        source.verifiedMethods(),
        Set.copyOf(legalStatus.missingRequiredVersionIds()),
        source.persistentLoginRequested(),
        legalFlow.expiresAt(),
        source.correlationId());
  }

  private UserEntity lockActiveOwner(String reference) {
    UserEntity user = lockOwner(reference);
    return user != null && user.getStatus() == UserStatusEnum.ACTIVE ? user : null;
  }

  private UserEntity lockOwner(String reference) {
    Long userId = flowService.resolveUserId(reference).orElse(null);
    return userId == null ? null : userRepository.findByIdForUpdate(userId).orElse(null);
  }

  private UserEntity lockActiveUser(long userId) {
    if (userId <= 0) {
      return null;
    }
    return userRepository.findByIdForUpdate(userId)
        .filter(candidate -> candidate.getStatus() == UserStatusEnum.ACTIVE)
        .orElse(null);
  }

  private static AuthenticationOrchestrationDecisionVO mapTerminal(
      AuthenticationOperationStatusEnum status) {
    return switch (status) {
      case EXPIRED -> terminal(AuthenticationOrchestrationStatusEnum.EXPIRED);
      case ALREADY_USED -> terminal(AuthenticationOrchestrationStatusEnum.CONFLICT);
      default -> rejected();
    };
  }

  private static AuthenticationOrchestrationDecisionVO rejected() {
    return terminal(AuthenticationOrchestrationStatusEnum.REJECTED);
  }

  private static AuthenticationOrchestrationDecisionVO terminal(
      AuthenticationOrchestrationStatusEnum status) {
    return result(status, null, null, null, Set.of(), List.of(), Set.of(), false, null, null);
  }

  private static AuthenticationOrchestrationDecisionVO result(
      AuthenticationOrchestrationStatusEnum status,
      String reference,
      UserEntity user,
      AuthenticationAssuranceEnum achieved,
      Set<AuthenticationMethodEnum> permittedMethods,
      List<AuthenticationFlowVerifiedMethodVO> verifiedMethods,
      Set<Long> missingLegalDocumentIds,
      boolean persistentLoginRequested,
      Instant expiresAt,
      UUID correlationId) {
    return new AuthenticationOrchestrationDecisionVO(
        status,
        reference,
        user == null ? null : user.getId(),
        user == null ? null : user.getEmail(),
        achieved,
        permittedMethods,
        verifiedMethods,
        missingLegalDocumentIds,
        persistentLoginRequested,
        expiresAt,
        correlationId);
  }
}
