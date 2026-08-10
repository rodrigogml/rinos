package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationOrchestrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.EmailOtpVerificationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.FactorOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowSnapshotVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationOrchestrationDecisionVO;
import br.com.rinos.app.config.AuthenticationMfaPropertiesConfig;

/**
 * Compõe os verificadores de segundo fator com a continuação de autenticação.
 *
 * <p>A operação bloqueia usuário → fluxo → fator, recalcula o catálogo vigente e mantém consumo da
 * prova e avanço do fluxo na mesma transação. Se o avanço não puder ser concluído, o consumo é
 * revertido para não queimar TOTP, OTP ou recovery code sem progresso correspondente.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
@Service
@Lazy
public class SecondFactorService {

  private final AuthenticationFlowService flowService;
  private final AuthenticationMethodAvailabilityService methodAvailability;
  private final AuthenticationSecondFactorPolicyService factorPolicy;
  private final TotpFactorService totpFactorService;
  private final EmailOtpService emailOtpService;
  private final RecoveryCodeService recoveryCodeService;
  private final AuthenticationOrchestrationService orchestrationService;
  private final UserRepository userRepository;
  private final AuthenticationMfaPropertiesConfig properties;

  /** Cria a composição sobre as autoridades transacionais de cada fator. */
  public SecondFactorService(
      AuthenticationFlowService flowService,
      AuthenticationMethodAvailabilityService methodAvailability,
      AuthenticationSecondFactorPolicyService factorPolicy,
      TotpFactorService totpFactorService,
      EmailOtpService emailOtpService,
      RecoveryCodeService recoveryCodeService,
      AuthenticationOrchestrationService orchestrationService,
      UserRepository userRepository,
      AuthenticationMfaPropertiesConfig properties) {
    this.flowService = flowService;
    this.methodAvailability = methodAvailability;
    this.factorPolicy = factorPolicy;
    this.totpFactorService = totpFactorService;
    this.emailOtpService = emailOtpService;
    this.recoveryCodeService = recoveryCodeService;
    this.orchestrationService = orchestrationService;
    this.userRepository = userRepository;
    this.properties = properties;
  }

  /**
   * Valida uma prova contra o estado atual e avança a mesma continuação atomicamente.
   *
   * @param reference referência opaca corrente
   * @param method fator escolhido
   * @param proof prova transitória
   * @param occurredAt instante UTC da tentativa
   * @return decisão interna pronta para a fachada pública
   */
  @Transactional
  public AuthenticationOrchestrationDecisionVO verify(
      String reference,
      AuthenticationMethodEnum method,
      String proof,
      Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    Long userId = flowService.resolveUserId(reference).orElse(null);
    UserEntity user = userId == null ? null : userRepository.findByIdForUpdate(userId).orElse(null);
    if (user == null || user.getStatus() != UserStatusEnum.ACTIVE || method == null
        || proof == null || proof.isBlank()) {
      return terminal(AuthenticationOrchestrationStatusEnum.REJECTED);
    }
    AuthenticationFlowSnapshotVO flow = flowService.snapshot(
        reference, AuthenticationFlowPurposeEnum.SIGN_IN, occurredAt);
    if (flow.status() != AuthenticationOperationStatusEnum.OPEN
        || !Objects.equals(flow.userId(), user.getId())) {
      return terminalStatus(flow.status());
    }
    Set<AuthenticationMethodEnum> current = factorPolicy.permittedMethods(
        flow.primaryMethod(), methodAvailability.availableMethods(user.getId()));
    boolean alreadyVerified = flow.verifiedMethods().stream()
        .anyMatch(evidence -> evidence.method() == method);
    if (!flow.permittedMethods().contains(method) || !current.contains(method) || alreadyVerified) {
      return terminal(AuthenticationOrchestrationStatusEnum.REJECTED);
    }

    VerificationOutcome verification = verifyProof(user.getId(), reference, method, proof, occurredAt);
    if (!verification.accepted()) {
      if (verification.countFailure()) {
        if (verification.failureAlreadyRegistered()) {
          flowService.enforceFailureLimit(
              reference, AuthenticationFlowPurposeEnum.SIGN_IN,
              properties.maximumAttempts(), occurredAt);
        } else {
          flowService.registerFailure(
              reference, AuthenticationFlowPurposeEnum.SIGN_IN,
              properties.maximumAttempts(), occurredAt);
        }
      }
      return terminal(AuthenticationOrchestrationStatusEnum.REJECTED);
    }

    AuthenticationOrchestrationDecisionVO decision = orchestrationService.advance(
        reference, method, occurredAt, null, occurredAt);
    if (!isProgress(decision.status())) {
      markRollbackOnly();
    }
    return decision;
  }

  private VerificationOutcome verifyProof(
      Long userId,
      String reference,
      AuthenticationMethodEnum method,
      String proof,
      Instant occurredAt) {
    return switch (method) {
      case TOTP -> factor(totpFactorService.verifyActive(userId, proof, occurredAt));
      case EMAIL_CODE -> email(emailOtpService.verify(reference, proof, occurredAt));
      case RECOVERY_CODE -> factor(recoveryCodeService.consume(userId, proof, occurredAt));
      default -> VerificationOutcome.rejected(false, false);
    };
  }

  private static VerificationOutcome factor(FactorOperationStatusEnum status) {
    return status == FactorOperationStatusEnum.USED || status == FactorOperationStatusEnum.EXHAUSTED
        ? VerificationOutcome.acceptedOutcome()
        : VerificationOutcome.rejected(true, false);
  }

  private static VerificationOutcome email(EmailOtpVerificationStatusEnum status) {
    return switch (status) {
      case USED -> VerificationOutcome.acceptedOutcome();
      case REJECTED, ATTEMPTS_EXHAUSTED -> VerificationOutcome.rejected(true, true);
      case EXPIRED, STALE -> VerificationOutcome.rejected(false, false);
    };
  }

  private static boolean isProgress(AuthenticationOrchestrationStatusEnum status) {
    return status == AuthenticationOrchestrationStatusEnum.CHALLENGE_REQUIRED
        || status == AuthenticationOrchestrationStatusEnum.READY
        || status == AuthenticationOrchestrationStatusEnum.LEGAL_CONSENT_REQUIRED;
  }

  private static void markRollbackOnly() {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    }
  }

  private static AuthenticationOrchestrationDecisionVO terminalStatus(
      AuthenticationOperationStatusEnum status) {
    return terminal(switch (status) {
      case EXPIRED -> AuthenticationOrchestrationStatusEnum.EXPIRED;
      case ALREADY_USED, INVALIDATED -> AuthenticationOrchestrationStatusEnum.CONFLICT;
      default -> AuthenticationOrchestrationStatusEnum.REJECTED;
    });
  }

  private static AuthenticationOrchestrationDecisionVO terminal(
      AuthenticationOrchestrationStatusEnum status) {
    return new AuthenticationOrchestrationDecisionVO(
        status, null, null, null, null, Set.of(), List.of(), Set.of(), false, null, null);
  }

  private record VerificationOutcome(
      boolean accepted,
      boolean countFailure,
      boolean failureAlreadyRegistered) {

    private static VerificationOutcome acceptedOutcome() {
      return new VerificationOutcome(true, false, false);
    }

    private static VerificationOutcome rejected(boolean expired, boolean alreadyRegistered) {
      return new VerificationOutcome(false, expired, alreadyRegistered);
    }
  }
}
