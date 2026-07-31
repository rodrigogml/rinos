package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.enums.ExternalRegistrationCompletionStatusEnum;
import br.com.rinos.app.api.vo.ExternalRegistrationCompletionResultVO;
import br.com.rinos.app.api.vo.RinosUserPrincipalVO;
import br.com.rinos.app.backend.module.identity.entity.ExternalIdentityEntity;
import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.LegalConsentDecisionEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationConsumptionStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationPurposeEnum;
import br.com.rinos.app.backend.module.identity.vo.IdentityTransitionVO;
import br.com.rinos.app.backend.module.identity.vo.VerificationInspectionVO;

/**
 * Conclui atomicamente uma pendência por identidade externa já validada.
 *
 * <p>A transação bloqueia cadastro, prova e vínculo externo antes de remover qualquer credencial
 * local. O resultado autenticável só retorna ao chamador depois que o interceptor transacional
 * conclui o commit.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class ExternalRegistrationCompletionService {

  private final VerificationService verificationService;
  private final LegalConsentService legalConsentService;
  private final LocalCredentialService credentialService;
  private final ExternalIdentityService externalIdentityService;
  private final UserLifecycleService userLifecycleService;
  private final RegistrationLifecycleService registrationLifecycleService;
  private final IdentityAuditService auditService;

  /**
   * Reúne as fronteiras que participam da ativação externa.
   */
  public ExternalRegistrationCompletionService(
      VerificationService verificationService,
      LegalConsentService legalConsentService,
      LocalCredentialService credentialService,
      ExternalIdentityService externalIdentityService,
      UserLifecycleService userLifecycleService,
      RegistrationLifecycleService registrationLifecycleService,
      IdentityAuditService auditService) {
    this.verificationService = verificationService;
    this.legalConsentService = legalConsentService;
    this.credentialService = credentialService;
    this.externalIdentityService = externalIdentityService;
    this.userLifecycleService = userLifecycleService;
    this.registrationLifecycleService = registrationLifecycleService;
    this.auditService = auditService;
  }

  /**
   * Consome a continuação, registra aceites e ativa exatamente uma identidade.
   *
   * @param reference referência opaca
   * @param acceptedDocumentIds versões legais aceitas
   * @param correlationId correlação técnica
   * @param occurredAt instante UTC comum
   * @return resultado seguro sem prova ou vínculo externo
   */
  @Transactional
  public ExternalRegistrationCompletionResultVO complete(
      String reference,
      List<Long> acceptedDocumentIds,
      UUID correlationId,
      Instant occurredAt) {
    Objects.requireNonNull(acceptedDocumentIds, "acceptedDocumentIds must not be null");
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");

    VerificationInspectionVO inspection = verificationService.inspect(
        VerificationPurposeEnum.EXTERNAL_REGISTRATION,
        reference,
        occurredAt);
    ExternalRegistrationCompletionResultVO terminal = classifyTerminal(inspection);
    if (terminal != null) {
      return terminal;
    }

    RegistrationEntity registration = inspection.registration();
    if (!isPending(registration, occurredAt)) {
      return ExternalRegistrationCompletionResultVO.of(
          ExternalRegistrationCompletionStatusEnum.CONFLICT);
    }
    UserEntity user = registration.getUser();
    Optional<ExternalIdentityEntity> pendingIdentity =
        externalIdentityService.findSinglePendingForUpdate(user.getId());
    if (pendingIdentity.isEmpty()
        || pendingIdentity.get().getStatus() != ExternalIdentityStatusEnum.PENDING) {
      return ExternalRegistrationCompletionResultVO.of(
          ExternalRegistrationCompletionStatusEnum.CONFLICT);
    }

    Map<Long, LegalConsentDecisionEnum> decisions =
        legalConsentService.validateCurrentAcceptances(
            acceptedDocumentIds,
            occurredAt);
    legalConsentService.recordCurrentDecisions(
        user,
        registration,
        decisions,
        occurredAt);

    VerificationConsumptionStatusEnum consumption = verificationService.consume(
        registration.getId(),
        VerificationPurposeEnum.EXTERNAL_REGISTRATION,
        reference,
        occurredAt);
    if (consumption != VerificationConsumptionStatusEnum.VERIFIED) {
      throw new IllegalStateException(
          "external registration proof changed after lock acquisition");
    }

    credentialService.invalidateAndRemoveForGoogle(user.getId(), occurredAt);
    verificationService.invalidateAllOpen(registration.getId(), occurredAt);
    externalIdentityService.activate(pendingIdentity.get(), occurredAt);
    IdentityTransitionVO userTransition = userLifecycleService.transition(
        user,
        UserStatusEnum.ACTIVE,
        IdentityTransitionOriginEnum.EXTERNAL_PROVIDER,
        "GOOGLE_VERIFIED",
        occurredAt);
    IdentityTransitionVO registrationTransition = registrationLifecycleService.transition(
        registration,
        RegistrationStatusEnum.ACTIVE,
        IdentityTransitionOriginEnum.EXTERNAL_PROVIDER,
        "GOOGLE_VERIFIED",
        occurredAt);
    recordAudit(
        user,
        registration,
        correlationId,
        userTransition,
        registrationTransition,
        occurredAt);
    return ExternalRegistrationCompletionResultVO.authenticated(
        new RinosUserPrincipalVO(user.getId(), user.getEmail()));
  }

  private static ExternalRegistrationCompletionResultVO classifyTerminal(
      VerificationInspectionVO inspection) {
    if (inspection.status() == VerificationConsumptionStatusEnum.EXPIRED) {
      return ExternalRegistrationCompletionResultVO.of(
          ExternalRegistrationCompletionStatusEnum.EXPIRED_REFERENCE);
    }
    if (inspection.status() != VerificationConsumptionStatusEnum.VERIFIED) {
      return ExternalRegistrationCompletionResultVO.of(
          ExternalRegistrationCompletionStatusEnum.INVALID_REFERENCE);
    }
    return null;
  }

  private void recordAudit(
      UserEntity user,
      RegistrationEntity registration,
      UUID correlationId,
      IdentityTransitionVO userTransition,
      IdentityTransitionVO registrationTransition,
      Instant occurredAt) {
    auditService.record(
        user,
        registration,
        correlationId,
        IdentityEventTypeEnum.VERIFICATION_CONFIRMED,
        null,
        null,
        IdentityTransitionOriginEnum.EXTERNAL_PROVIDER,
        "EXTERNAL_REGISTRATION",
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
        IdentityEventTypeEnum.EXTERNAL_IDENTITY_RESOLVED,
        null,
        null,
        IdentityTransitionOriginEnum.EXTERNAL_PROVIDER,
        "ACTIVATED",
        occurredAt);
    auditService.record(
        user,
        registration,
        correlationId,
        IdentityEventTypeEnum.REGISTRATION_ACTIVATED,
        null,
        null,
        IdentityTransitionOriginEnum.EXTERNAL_PROVIDER,
        "GOOGLE",
        occurredAt);
  }

  private static boolean isPending(
      RegistrationEntity registration,
      Instant occurredAt) {
    return registration != null
        && registration.getStatus() == RegistrationStatusEnum.PENDING_VERIFICATION
        && registration.getUser().getStatus() == UserStatusEnum.PENDING_VERIFICATION
        && occurredAt.isBefore(registration.getExpiresAt());
  }
}
