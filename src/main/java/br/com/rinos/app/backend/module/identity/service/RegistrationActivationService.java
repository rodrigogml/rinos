package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.enums.RegistrationActivationStatusEnum;
import br.com.rinos.app.api.vo.RegistrationActivationResultVO;
import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.LegalConsentDecisionEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationConsumptionStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationPurposeEnum;
import br.com.rinos.app.backend.module.identity.vo.IdentityTransitionVO;
import br.com.rinos.app.backend.module.identity.vo.LegalRequirementStatusVO;
import br.com.rinos.app.backend.module.identity.vo.VerificationInspectionVO;

/**
 * Conduz a prova local até ativação ou continuação legal na mesma transação.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class RegistrationActivationService {

  private final VerificationService verificationService;
  private final LegalConsentService legalConsentService;
  private final UserLifecycleService userLifecycleService;
  private final RegistrationLifecycleService registrationLifecycleService;
  private final ExternalIdentityService externalIdentityService;
  private final IdentityAuditService auditService;
  private final EmailPrivacyService emailPrivacyService;
  private final RegistrationAuthenticationContinuationService authenticationContinuationService;

  /**
   * Reúne os serviços atômicos participantes da ativação.
   */
  public RegistrationActivationService(
      VerificationService verificationService,
      LegalConsentService legalConsentService,
      UserLifecycleService userLifecycleService,
      RegistrationLifecycleService registrationLifecycleService,
      ExternalIdentityService externalIdentityService,
      IdentityAuditService auditService,
      EmailPrivacyService emailPrivacyService,
      RegistrationAuthenticationContinuationService authenticationContinuationService) {
    this.verificationService = verificationService;
    this.legalConsentService = legalConsentService;
    this.userLifecycleService = userLifecycleService;
    this.registrationLifecycleService = registrationLifecycleService;
    this.externalIdentityService = externalIdentityService;
    this.auditService = auditService;
    this.emailPrivacyService = emailPrivacyService;
    this.authenticationContinuationService = authenticationContinuationService;
  }

  /**
   * Valida a prova sem consumi-la quando ainda faltam aceites atuais.
   *
   * @param proof prova opaca
   * @param correlationId correlação técnica
   * @param occurredAt instante UTC
   * @return ativação, continuação ou rejeição segura
   */
  @Transactional
  public RegistrationActivationResultVO activate(
      String proof,
      UUID correlationId,
      Instant occurredAt) {
    VerificationInspectionVO inspection = verificationService.inspect(
        VerificationPurposeEnum.REGISTRATION_EMAIL,
        proof,
        occurredAt);
    RegistrationActivationResultVO terminal = classifyTerminal(inspection);
    if (terminal != null) {
      return terminal;
    }
    RegistrationEntity registration = inspection.registration();
    if (!isPendingLocal(registration, occurredAt)) {
      return classifyUnavailableRegistration(registration, occurredAt);
    }
    LegalRequirementStatusVO legal = legalConsentService.evaluateRequiredConsents(
        registration.getUser().getId(),
        occurredAt);
    if (legal.requiresConsent()) {
      return consentChallenge(proof, inspection, legal);
    }
    return consumeAndActivate(registration, proof, correlationId, occurredAt);
  }

  /**
   * Registra os documentos atuais e conclui usando a mesma prova ainda aberta.
   *
   * @param proof referência opaca
   * @param acceptedDocumentIds versões vigentes aceitas
   * @param correlationId correlação técnica
   * @param occurredAt instante UTC
   * @return ativação, challenge atualizado ou rejeição
   */
  @Transactional
  public RegistrationActivationResultVO completeConsent(
      String proof,
      List<Long> acceptedDocumentIds,
      UUID correlationId,
      Instant occurredAt) {
    VerificationInspectionVO inspection = verificationService.inspect(
        VerificationPurposeEnum.REGISTRATION_EMAIL,
        proof,
        occurredAt);
    RegistrationActivationResultVO terminal = classifyTerminal(inspection);
    if (terminal != null) {
      return terminal;
    }
    RegistrationEntity registration = inspection.registration();
    if (!isPendingLocal(registration, occurredAt)) {
      return classifyUnavailableRegistration(registration, occurredAt);
    }

    Map<Long, LegalConsentDecisionEnum> decisions =
        legalConsentService.validateCurrentAcceptances(acceptedDocumentIds, occurredAt);
    legalConsentService.recordCurrentDecisions(
        registration.getUser(),
        registration,
        decisions,
        occurredAt);
    LegalRequirementStatusVO legal = legalConsentService.evaluateRequiredConsents(
        registration.getUser().getId(),
        occurredAt);
    if (legal.requiresConsent()) {
      return consentChallenge(proof, inspection, legal);
    }
    return consumeAndActivate(registration, proof, correlationId, occurredAt);
  }

  private RegistrationActivationResultVO classifyTerminal(
      VerificationInspectionVO inspection) {
    if (inspection.status() == VerificationConsumptionStatusEnum.EXPIRED) {
      return RegistrationActivationResultVO.of(
          RegistrationActivationStatusEnum.EXPIRED_PROOF);
    }
    if (inspection.status() == VerificationConsumptionStatusEnum.REJECTED) {
      return RegistrationActivationResultVO.of(
          RegistrationActivationStatusEnum.INVALID_PROOF);
    }
    if (inspection.status() == VerificationConsumptionStatusEnum.ALREADY_USED) {
      RegistrationEntity registration = inspection.registration();
      if (registration != null
          && registration.getStatus() == RegistrationStatusEnum.ACTIVE
          && registration.getUser().getStatus() == UserStatusEnum.ACTIVE) {
        return RegistrationActivationResultVO.of(
            RegistrationActivationStatusEnum.ALREADY_ACTIVE);
      }
      return RegistrationActivationResultVO.of(
          RegistrationActivationStatusEnum.INVALID_PROOF);
    }
    return null;
  }

  private RegistrationActivationResultVO consentChallenge(
      String proof,
      VerificationInspectionVO inspection,
      LegalRequirementStatusVO legal) {
    Set<String> missingIds = legal.missingRequiredVersionIds().stream()
        .map(String::valueOf)
        .collect(Collectors.toUnmodifiableSet());
    return RegistrationActivationResultVO.consentRequired(
        proof,
        emailPrivacyService.maskForPublicDisplay(
            inspection.registration().getUser().getEmail()),
        missingIds,
        inspection.expiresAt());
  }

  /**
   * Distingue encerramento conhecido de uma prova que não pode ser correlacionada com segurança.
   *
   * @param registration cadastro localizado pela prova; pode ser nulo
   * @param occurredAt instante da tentativa
   * @return encerramento explícito ou rejeição genérica
   */
  private static RegistrationActivationResultVO classifyUnavailableRegistration(
      RegistrationEntity registration,
      Instant occurredAt) {
    boolean closed = registration != null
        && (registration.getStatus() == RegistrationStatusEnum.CANCELLED
            || registration.getStatus() == RegistrationStatusEnum.EXPIRED
            || registration.getUser().getStatus() == UserStatusEnum.CANCELLED
            || !occurredAt.isBefore(registration.getExpiresAt()));
    return RegistrationActivationResultVO.of(
        closed
            ? RegistrationActivationStatusEnum.REGISTRATION_CLOSED
            : RegistrationActivationStatusEnum.INVALID_PROOF);
  }

  private RegistrationActivationResultVO consumeAndActivate(
      RegistrationEntity registration,
      String proof,
      UUID correlationId,
      Instant occurredAt) {
    VerificationConsumptionStatusEnum consumption = verificationService.consume(
        registration.getId(),
        VerificationPurposeEnum.REGISTRATION_EMAIL,
        proof,
        occurredAt);
    if (consumption != VerificationConsumptionStatusEnum.VERIFIED) {
      throw new IllegalStateException(
          "verification changed after it was locked for activation");
    }

    UserEntity user = registration.getUser();
    IdentityTransitionVO userTransition = userLifecycleService.transition(
        user,
        UserStatusEnum.ACTIVE,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        "EMAIL_VERIFIED",
        occurredAt,
        correlationId);
    IdentityTransitionVO registrationTransition = registrationLifecycleService.transition(
        registration,
        RegistrationStatusEnum.ACTIVE,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        "EMAIL_VERIFIED",
        occurredAt);
    verificationService.invalidateAllOpen(registration.getId(), occurredAt);
    externalIdentityService.removePendingForLocalActivation(user.getId());

    auditService.record(
        user,
        registration,
        correlationId,
        IdentityEventTypeEnum.VERIFICATION_CONFIRMED,
        null,
        null,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        "REGISTRATION_EMAIL",
        occurredAt);
    auditService.recordTransition(
        user,
        registration,
        correlationId,
        IdentityEventTypeEnum.USER_STATUS_CHANGED,
        userTransition);
    auditService.recordTransition(
        user,
        registration,
        correlationId,
        IdentityEventTypeEnum.REGISTRATION_STATUS_CHANGED,
        registrationTransition);
    auditService.record(
        user,
        registration,
        correlationId,
        IdentityEventTypeEnum.REGISTRATION_ACTIVATED,
        null,
        null,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        "LOCAL",
        occurredAt);
    return RegistrationActivationResultVO.activated(authenticationContinuationService.issue(
        user, registration.getMethod(), correlationId, occurredAt));
  }

  private static boolean isPendingLocal(
      RegistrationEntity registration,
      Instant occurredAt) {
    return registration != null
        && registration.getMethod() == RegistrationMethodEnum.LOCAL
        && registration.getStatus() == RegistrationStatusEnum.PENDING_VERIFICATION
        && registration.getUser().getStatus() == UserStatusEnum.PENDING_VERIFICATION
        && occurredAt.isBefore(registration.getExpiresAt());
  }
}
