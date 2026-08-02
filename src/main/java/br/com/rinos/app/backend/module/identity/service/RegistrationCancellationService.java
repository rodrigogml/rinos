package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.enums.RegistrationCancellationConfirmationStatusEnum;
import br.com.rinos.app.backend.module.identity.entity.IdentityEventEntity;
import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationConsumptionStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationEmailTemplateEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationPurposeEnum;
import br.com.rinos.app.backend.module.identity.repository.IdentityEventRepository;
import br.com.rinos.app.backend.module.identity.repository.RegistrationRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.IssuedVerificationVO;
import br.com.rinos.app.backend.module.identity.vo.RegistrationCancellationIssueVO;
import br.com.rinos.app.backend.module.identity.vo.VerificationEmailDispatchRequestVO;
import br.com.rinos.app.backend.module.identity.vo.VerificationInspectionVO;
import br.com.rinos.app.config.RegistrationPropertiesConfig;

/**
 * Emite e confirma a prova que autoriza remover integralmente um cadastro pendente.
 *
 * <p>A solicitação não revela elegibilidade. A confirmação bloqueia primeiro o cadastro por meio
 * da prova, revalida o identificador e remove usuário e dependências na mesma transação que cria
 * o tombstone sem PII.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class RegistrationCancellationService {

  private final RegistrationRepository registrationRepository;
  private final IdentityEventRepository eventRepository;
  private final UserRepository userRepository;
  private final VerificationService verificationService;
  private final EmailNormalizationService emailNormalizationService;
  private final RegistrationLifecycleService registrationLifecycleService;
  private final UserLifecycleService userLifecycleService;
  private final IdentityAuditService auditService;
  private final PublicApplicationUriService uriService;
  private final VerificationEmailDispatchService dispatchService;
  private final RegistrationPropertiesConfig registrationProperties;

  /**
   * Reúne as fronteiras persistentes, de prova, lifecycle, auditoria e e-mail.
   *
   * @param registrationRepository persistência bloqueável do cadastro
   * @param eventRepository eventos que formam a janela móvel de emissão
   * @param userRepository persistência que remove a raiz e suas dependências
   * @param verificationService emissão, inspeção, consumo e invalidação
   * @param emailNormalizationService comparação canônica do identificador comprovado
   * @param registrationLifecycleService regras de transição do cadastro
   * @param userLifecycleService regras de transição da identidade
   * @param auditService persistência do tombstone sanitizado
   * @param uriService origem pública canônica do link
   * @param dispatchService despacho SMTP pós-commit
   * @param registrationProperties limites fixos do ciclo de cadastro
   */
  public RegistrationCancellationService(
      RegistrationRepository registrationRepository,
      IdentityEventRepository eventRepository,
      UserRepository userRepository,
      VerificationService verificationService,
      EmailNormalizationService emailNormalizationService,
      RegistrationLifecycleService registrationLifecycleService,
      UserLifecycleService userLifecycleService,
      IdentityAuditService auditService,
      PublicApplicationUriService uriService,
      VerificationEmailDispatchService dispatchService,
      RegistrationPropertiesConfig registrationProperties) {
    this.registrationRepository = Objects.requireNonNull(
        registrationRepository,
        "registrationRepository must not be null");
    this.eventRepository = Objects.requireNonNull(
        eventRepository,
        "eventRepository must not be null");
    this.userRepository = Objects.requireNonNull(
        userRepository,
        "userRepository must not be null");
    this.verificationService = Objects.requireNonNull(
        verificationService,
        "verificationService must not be null");
    this.emailNormalizationService = Objects.requireNonNull(
        emailNormalizationService,
        "emailNormalizationService must not be null");
    this.registrationLifecycleService = Objects.requireNonNull(
        registrationLifecycleService,
        "registrationLifecycleService must not be null");
    this.userLifecycleService = Objects.requireNonNull(
        userLifecycleService,
        "userLifecycleService must not be null");
    this.auditService = Objects.requireNonNull(
        auditService,
        "auditService must not be null");
    this.uriService = Objects.requireNonNull(uriService, "uriService must not be null");
    this.dispatchService = Objects.requireNonNull(
        dispatchService,
        "dispatchService must not be null");
    this.registrationProperties = Objects.requireNonNull(
        registrationProperties,
        "registrationProperties must not be null");
  }

  /**
   * Emite uma prova somente para pendência ainda válida e agenda seu envio após o commit.
   *
   * @param registrationId cadastro previamente localizado sem expor o resultado
   * @param locale idioma preferencial
   * @param correlationId correlação técnica
   * @param occurredAt instante UTC comum
   * @return emissão real ou resultado inelegível consumido apenas pela fachada neutra
   */
  @Transactional
  public RegistrationCancellationIssueVO issue(
      Long registrationId,
      Locale locale,
      UUID correlationId,
      Instant occurredAt) {
    Objects.requireNonNull(registrationId, "registrationId must not be null");
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    RegistrationEntity registration = registrationRepository
        .findByIdForUpdate(registrationId)
        .orElse(null);
    if (!isPending(registration, occurredAt)) {
      return RegistrationCancellationIssueVO.notIssued();
    }

    List<IdentityEventEntity> recentIssues = eventRepository
        .findByRegistrationIdAndEventTypeAndOccurredAtAfterOrderByOccurredAtAsc(
            registrationId,
            IdentityEventTypeEnum.REGISTRATION_CANCELLATION_REQUESTED,
            occurredAt.minus(registrationProperties.cancellationRequestWindow()));
    if (recentIssues.size() >= registrationProperties.cancellationRequestLimit()) {
      Instant blockedUntil = recentIssues.getFirst().getOccurredAt()
          .plus(registrationProperties.cancellationRequestWindow());
      return RegistrationCancellationIssueVO.rateLimited(blockedUntil);
    }

    IssuedVerificationVO verification = verificationService.issue(
        registration,
        VerificationPurposeEnum.REGISTRATION_CANCEL,
        occurredAt);
    auditService.record(
        registration.getUser(),
        registration,
        correlationId,
        IdentityEventTypeEnum.REGISTRATION_CANCELLATION_REQUESTED,
        null,
        null,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        "REQUESTED",
        occurredAt);
    return RegistrationCancellationIssueVO.issued(
        verification.getExpiresAt(),
        dispatchService.scheduleAfterCommit(new VerificationEmailDispatchRequestVO(
            registration.getUser().getEmail(),
            uriService.registrationCancellationUri(verification.getToken()),
            null,
            verification.getExpiresAt(),
            locale,
            correlationId,
            VerificationEmailTemplateEnum.REGISTRATION_CANCELLATION)));
  }

  /**
   * Consome a prova e elimina atomicamente a raiz pendente e suas dependências.
   *
   * @param identifier identificador que deve corresponder à prova
   * @param proof prova opaca recebida
   * @param correlationId correlação técnica
   * @param occurredAt instante UTC comum
   * @return resultado fechado sem identificadores persistentes
   */
  @Transactional
  public RegistrationCancellationConfirmationStatusEnum confirm(
      String identifier,
      String proof,
      UUID correlationId,
      Instant occurredAt) {
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    String normalizedIdentifier = emailNormalizationService.normalize(identifier).normalizedEmail();
    VerificationInspectionVO inspection = verificationService.inspect(
        VerificationPurposeEnum.REGISTRATION_CANCEL,
        proof,
        occurredAt);
    if (inspection.status() == VerificationConsumptionStatusEnum.EXPIRED) {
      return RegistrationCancellationConfirmationStatusEnum.EXPIRED_PROOF;
    }
    if (inspection.status() != VerificationConsumptionStatusEnum.VERIFIED) {
      return RegistrationCancellationConfirmationStatusEnum.INVALID_PROOF;
    }

    RegistrationEntity registration = inspection.registration();
    UserEntity user = registration.getUser();
    if (!isPending(registration, occurredAt)
        || user.getStatus() != UserStatusEnum.PENDING_VERIFICATION
        || !normalizedIdentifier.equals(user.getNormalizedEmail())) {
      return RegistrationCancellationConfirmationStatusEnum.INVALID_PROOF;
    }

    VerificationConsumptionStatusEnum consumption = verificationService.consume(
        registration.getId(),
        VerificationPurposeEnum.REGISTRATION_CANCEL,
        proof,
        occurredAt);
    if (consumption != VerificationConsumptionStatusEnum.VERIFIED) {
      throw new IllegalStateException("Locked cancellation proof changed unexpectedly");
    }
    verificationService.invalidateAllOpen(registration.getId(), occurredAt);
    registrationLifecycleService.transition(
        registration,
        RegistrationStatusEnum.CANCELLED,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        "EMAIL_CONTROL_CONFIRMED",
        occurredAt);
    userLifecycleService.transition(
        user,
        UserStatusEnum.CANCELLED,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        "REGISTRATION_CANCELLED",
        occurredAt);

    auditService.minimizeForTerminalRemoval(user, registration);
    userRepository.delete(user);
    userRepository.flush();
    auditService.recordCancellationTombstone(
        correlationId,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        "EMAIL_CONTROL_CONFIRMED",
        occurredAt);
    return RegistrationCancellationConfirmationStatusEnum.CANCELLED;
  }

  private static boolean isPending(
      RegistrationEntity registration,
      Instant occurredAt) {
    return registration != null
        && registration.getStatus() == RegistrationStatusEnum.PENDING_VERIFICATION
        && occurredAt.isBefore(registration.getExpiresAt());
  }
}
