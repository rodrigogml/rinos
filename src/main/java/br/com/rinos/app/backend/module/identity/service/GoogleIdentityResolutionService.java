package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.ExternalIdentityEntity;
import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityProviderEnum;
import br.com.rinos.app.backend.module.identity.enums.GoogleIdentityDomainStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationPurposeEnum;
import br.com.rinos.app.backend.module.identity.repository.RegistrationRepository;
import br.com.rinos.app.backend.module.identity.vo.GoogleIdentityDomainResultVO;
import br.com.rinos.app.backend.module.identity.vo.IssuedVerificationVO;
import br.com.rinos.app.config.RegistrationPropertiesConfig;

/**
 * Resolve uma identidade Google validada e emite sua continuação opaca numa única transação.
 *
 * <p>O serviço nunca recebe token Google, nonce ou claims completos. A senha e as provas de uma
 * pendência local permanecem intactas até a futura conclusão transacional dos aceites.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class GoogleIdentityResolutionService {

  private final IdentityService identityService;
  private final EmailNormalizationService emailNormalizationService;
  private final ExternalIdentityService externalIdentityService;
  private final RegistrationRepository registrationRepository;
  private final VerificationService verificationService;
  private final IdentityAuditService auditService;
  private final RegistrationPropertiesConfig registrationProperties;

  /**
   * Reúne os serviços persistentes usados pela decisão de domínio.
   */
  public GoogleIdentityResolutionService(
      IdentityService identityService,
      EmailNormalizationService emailNormalizationService,
      ExternalIdentityService externalIdentityService,
      RegistrationRepository registrationRepository,
      VerificationService verificationService,
      IdentityAuditService auditService,
      RegistrationPropertiesConfig registrationProperties) {
    this.identityService = identityService;
    this.emailNormalizationService = emailNormalizationService;
    this.externalIdentityService = externalIdentityService;
    this.registrationRepository = registrationRepository;
    this.verificationService = verificationService;
    this.auditService = auditService;
    this.registrationProperties = registrationProperties;
  }

  /**
   * Resolve o vínculo por chave externa e, somente depois, considera o e-mail verificado.
   *
   * @param issuer emissor validado
   * @param subject identificador estável validado
   * @param verifiedEmail e-mail verificado e normalizável
   * @param correlationId correlação sanitizada
   * @param occurredAt instante UTC comum da tentativa
   * @return continuação ou decisão segura sem identificadores
   */
  @Transactional
  public GoogleIdentityDomainResultVO resolve(
      String issuer,
      String subject,
      String verifiedEmail,
      UUID correlationId,
      Instant occurredAt) {
    Optional<ExternalIdentityEntity> linked =
        externalIdentityService.findForUpdate(issuer, subject);
    if (linked.isPresent()) {
      return resolveLinked(linked.get(), verifiedEmail, correlationId, occurredAt);
    }

    Optional<UserEntity> existingUser = identityService.findByEmailForUpdate(verifiedEmail);
    if (existingUser.isPresent()) {
      return resolveExistingUser(
          existingUser.get(),
          issuer,
          subject,
          correlationId,
          occurredAt);
    }

    RegistrationEntity registration = identityService.createPendingIdentity(
        verifiedEmail,
        RegistrationMethodEnum.GOOGLE,
        occurredAt.plus(registrationProperties.pendingRetention()));
    externalIdentityService.createPending(
        registration.getUser(),
        ExternalIdentityProviderEnum.GOOGLE,
        issuer,
        subject,
        occurredAt);
    return issueContinuation(registration, correlationId, occurredAt);
  }

  private GoogleIdentityDomainResultVO resolveLinked(
      ExternalIdentityEntity identity,
      String verifiedEmail,
      UUID correlationId,
      Instant occurredAt) {
    UserEntity user = identity.getUser();
    if (!user.getNormalizedEmail().equals(normalizedEmail(verifiedEmail))) {
      return recordDecision(
          user,
          null,
          correlationId,
          GoogleIdentityDomainStatusEnum.EXTERNAL_IDENTITY_CONFLICT,
          occurredAt);
    }
    if (user.getStatus() != UserStatusEnum.PENDING_VERIFICATION) {
      return recordDecision(
          user,
          null,
          correlationId,
          GoogleIdentityDomainStatusEnum.EXISTING_USER_REAUTHENTICATION_REQUIRED,
          occurredAt);
    }
    RegistrationEntity registration = registrationRepository
        .findByUserIdAndStatus(user.getId(), RegistrationStatusEnum.PENDING_VERIFICATION)
        .flatMap(candidate -> registrationRepository.findByIdForUpdate(candidate.getId()))
        .filter(candidate -> occurredAt.isBefore(candidate.getExpiresAt()))
        .orElse(null);
    if (registration == null) {
      return recordDecision(
          user,
          null,
          correlationId,
          GoogleIdentityDomainStatusEnum.EXTERNAL_IDENTITY_CONFLICT,
          occurredAt);
    }
    return issueContinuation(registration, correlationId, occurredAt);
  }

  private GoogleIdentityDomainResultVO resolveExistingUser(
      UserEntity user,
      String issuer,
      String subject,
      UUID correlationId,
      Instant occurredAt) {
    if (user.getStatus() != UserStatusEnum.PENDING_VERIFICATION) {
      return recordDecision(
          user,
          null,
          correlationId,
          GoogleIdentityDomainStatusEnum.EXISTING_USER_REAUTHENTICATION_REQUIRED,
          occurredAt);
    }
    RegistrationEntity registration = registrationRepository
        .findByUserIdAndStatus(user.getId(), RegistrationStatusEnum.PENDING_VERIFICATION)
        .flatMap(candidate -> registrationRepository.findByIdForUpdate(candidate.getId()))
        .filter(candidate -> occurredAt.isBefore(candidate.getExpiresAt()))
        .orElse(null);
    if (registration == null) {
      return recordDecision(
          user,
          null,
          correlationId,
          GoogleIdentityDomainStatusEnum.EXTERNAL_IDENTITY_CONFLICT,
          occurredAt);
    }
    externalIdentityService.replacePending(
        user,
        ExternalIdentityProviderEnum.GOOGLE,
        issuer,
        subject,
        occurredAt);
    return issueContinuation(registration, correlationId, occurredAt);
  }

  private GoogleIdentityDomainResultVO issueContinuation(
      RegistrationEntity registration,
      UUID correlationId,
      Instant occurredAt) {
    IssuedVerificationVO verification = verificationService.issue(
        registration,
        VerificationPurposeEnum.EXTERNAL_REGISTRATION,
        occurredAt);
    auditService.record(
        registration.getUser(),
        registration,
        correlationId,
        IdentityEventTypeEnum.EXTERNAL_IDENTITY_RESOLVED,
        null,
        null,
        IdentityTransitionOriginEnum.EXTERNAL_PROVIDER,
        GoogleIdentityDomainStatusEnum.CONTINUATION_REQUIRED.name(),
        occurredAt);
    return GoogleIdentityDomainResultVO.continuation(
        verification.getToken(),
        registration.getUser().getEmail(),
        verification.getExpiresAt());
  }

  private GoogleIdentityDomainResultVO recordDecision(
      UserEntity user,
      RegistrationEntity registration,
      UUID correlationId,
      GoogleIdentityDomainStatusEnum status,
      Instant occurredAt) {
    auditService.record(
        user,
        registration,
        correlationId,
        IdentityEventTypeEnum.EXTERNAL_IDENTITY_RESOLVED,
        null,
        null,
        IdentityTransitionOriginEnum.EXTERNAL_PROVIDER,
        status.name(),
        occurredAt);
    return GoogleIdentityDomainResultVO.of(status);
  }

  private String normalizedEmail(String email) {
    return emailNormalizationService.normalize(email).normalizedEmail();
  }
}
