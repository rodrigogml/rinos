package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.IdentityEventEntity;
import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationPurposeEnum;
import br.com.rinos.app.backend.module.identity.repository.IdentityEventRepository;
import br.com.rinos.app.backend.module.identity.repository.RegistrationRepository;
import br.com.rinos.app.backend.module.identity.vo.IssuedVerificationVO;
import br.com.rinos.app.backend.module.identity.vo.RegistrationResendTransactionVO;
import br.com.rinos.app.backend.module.identity.vo.VerificationEmailDispatchRequestVO;
import br.com.rinos.app.config.RegistrationPropertiesConfig;

/**
 * Serializa, limita e emite uma nova comprovação para uma pendência local.
 *
 * <p>Somente eventos {@code VERIFICATION_REISSUED} consomem a franquia de reenvio. A emissão
 * inicial não é contabilizada e a validade absoluta do cadastro nunca é prorrogada.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class RegistrationResendService {

  private final RegistrationRepository registrationRepository;
  private final IdentityEventRepository eventRepository;
  private final VerificationService verificationService;
  private final IdentityAuditService auditService;
  private final PublicApplicationUriService uriService;
  private final VerificationEmailDispatchService dispatchService;
  private final RegistrationPropertiesConfig properties;

  /**
   * Reúne persistência, comprovação, auditoria e despacho na mesma fronteira transacional.
   *
   * @param registrationRepository persistência bloqueável do cadastro
   * @param eventRepository histórico sanitizado usado pela janela móvel
   * @param verificationService emissor que invalida provas abertas anteriores
   * @param auditService auditoria sanitizada da nova emissão
   * @param uriService origem pública canônica dos links
   * @param dispatchService despacho registrado para depois do commit
   * @param properties limite e janela exclusivos do arquivo de propriedades
   */
  public RegistrationResendService(
      RegistrationRepository registrationRepository,
      IdentityEventRepository eventRepository,
      VerificationService verificationService,
      IdentityAuditService auditService,
      PublicApplicationUriService uriService,
      VerificationEmailDispatchService dispatchService,
      RegistrationPropertiesConfig properties) {
    this.registrationRepository = Objects.requireNonNull(
        registrationRepository,
        "registrationRepository must not be null");
    this.eventRepository = Objects.requireNonNull(
        eventRepository,
        "eventRepository must not be null");
    this.verificationService = Objects.requireNonNull(
        verificationService,
        "verificationService must not be null");
    this.auditService = Objects.requireNonNull(
        auditService,
        "auditService must not be null");
    this.uriService = Objects.requireNonNull(uriService, "uriService must not be null");
    this.dispatchService = Objects.requireNonNull(
        dispatchService,
        "dispatchService must not be null");
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
  }

  /**
   * Emite no máximo três novas provas na janela configurada e agenda a mensagem pós-commit.
   *
   * @param registrationId identificador de uma pendência previamente localizada
   * @param locale idioma preferencial
   * @param correlationId correlação técnica aleatória
   * @param occurredAt instante comum da operação
   * @return resultado neutro, bloqueado ou com despacho agendado
   */
  @Transactional
  public RegistrationResendTransactionVO resend(
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
    if (!isEligible(registration, occurredAt)) {
      return RegistrationResendTransactionVO.notEligible();
    }

    List<IdentityEventEntity> recentReissues = eventRepository
        .findByRegistrationIdAndEventTypeAndOccurredAtAfterOrderByOccurredAtAsc(
            registrationId,
            IdentityEventTypeEnum.VERIFICATION_REISSUED,
            occurredAt.minus(properties.resendWindow()));
    if (recentReissues.size() >= properties.resendLimit()) {
      Instant blockedUntil = recentReissues.getFirst().getOccurredAt()
          .plus(properties.resendWindow());
      return RegistrationResendTransactionVO.blocked(blockedUntil);
    }

    IssuedVerificationVO verification = verificationService.issue(
        registration,
        VerificationPurposeEnum.REGISTRATION_EMAIL,
        occurredAt);
    auditService.record(
        registration.getUser(),
        registration,
        correlationId,
        IdentityEventTypeEnum.VERIFICATION_REISSUED,
        null,
        null,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        "REQUESTED",
        occurredAt);

    return RegistrationResendTransactionVO.scheduled(
        verification.getExpiresAt(),
        dispatchService.scheduleAfterCommit(new VerificationEmailDispatchRequestVO(
            registration.getUser().getEmail(),
            uriService.activationUri(verification.getToken()),
            verification.getToken(),
            verification.getExpiresAt(),
            locale,
            correlationId)));
  }

  private static boolean isEligible(
      RegistrationEntity registration,
      Instant occurredAt) {
    return registration != null
        && registration.getMethod() == RegistrationMethodEnum.LOCAL
        && registration.getStatus() == RegistrationStatusEnum.PENDING_VERIFICATION
        && occurredAt.isBefore(registration.getExpiresAt());
  }
}
