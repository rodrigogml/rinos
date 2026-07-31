package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.LegalConsentDecisionEnum;
import br.com.rinos.app.backend.module.identity.enums.OriginOperationEnum;
import br.com.rinos.app.backend.module.identity.enums.OriginReservationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationPurposeEnum;
import br.com.rinos.app.backend.module.identity.vo.IssuedVerificationVO;
import br.com.rinos.app.backend.module.identity.vo.OriginAddressVO;
import br.com.rinos.app.backend.module.identity.vo.OriginReservationResultVO;
import br.com.rinos.app.backend.module.identity.vo.RegistrationCreationTransactionVO;
import br.com.rinos.app.backend.module.identity.vo.VerificationEmailDispatchRequestVO;
import br.com.rinos.app.config.RegistrationPropertiesConfig;

/**
 * Persiste todos os efeitos iniciais e agenda a mensagem na mesma fronteira transacional.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class RegistrationCreationService {

  private final IdentityService identityService;
  private final LocalCredentialService credentialService;
  private final LegalConsentService legalConsentService;
  private final VerificationService verificationService;
  private final OriginLimitService originLimitService;
  private final IdentityAuditService auditService;
  private final PublicApplicationUriService uriService;
  private final VerificationEmailDispatchService dispatchService;
  private final RegistrationPropertiesConfig properties;

  /**
   * Reúne os serviços que participam da única transação de criação.
   */
  public RegistrationCreationService(
      IdentityService identityService,
      LocalCredentialService credentialService,
      LegalConsentService legalConsentService,
      VerificationService verificationService,
      OriginLimitService originLimitService,
      IdentityAuditService auditService,
      PublicApplicationUriService uriService,
      VerificationEmailDispatchService dispatchService,
      RegistrationPropertiesConfig properties) {
    this.identityService = identityService;
    this.credentialService = credentialService;
    this.legalConsentService = legalConsentService;
    this.verificationService = verificationService;
    this.originLimitService = originLimitService;
    this.auditService = auditService;
    this.uriService = uriService;
    this.dispatchService = dispatchService;
    this.properties = properties;
  }

  /**
   * Reserva o limite, cria a pendência e registra o despacho para depois do commit.
   *
   * @param email e-mail já validado
   * @param passwordHash hash Argon2id preparado
   * @param legalDecisions aceites das versões publicadas apresentadas no formulário
   * @param origin origem canônica
   * @param locale idioma preferencial
   * @param correlationId correlação técnica
   * @param occurredAt instante comum da operação
   * @return bloqueio sem efeitos ou futuro do despacho da criação confirmada
   */
  @Transactional
  public RegistrationCreationTransactionVO create(
      String email,
      String passwordHash,
      Map<Long, LegalConsentDecisionEnum> legalDecisions,
      OriginAddressVO origin,
      Locale locale,
      UUID correlationId,
      Instant occurredAt) {
    OriginReservationResultVO reservation = originLimitService.reserveNewRegistration(
        origin,
        OriginOperationEnum.USER_REGISTRATION);
    if (reservation.status() == OriginReservationStatusEnum.BLOCKED) {
      TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
      return RegistrationCreationTransactionVO.blocked(reservation.blockedUntil());
    }

    RegistrationEntity registration = identityService.createPendingIdentity(
        email,
        RegistrationMethodEnum.LOCAL,
        occurredAt.plus(properties.pendingRetention()));
    credentialService.replace(registration.getUser(), passwordHash);
    legalConsentService.recordPublishedDecisions(
        registration.getUser(),
        registration,
        legalDecisions,
        occurredAt);
    IssuedVerificationVO verification = verificationService.issue(
        registration,
        VerificationPurposeEnum.REGISTRATION_EMAIL,
        occurredAt);
    auditService.record(
        registration.getUser(),
        registration,
        correlationId,
        IdentityEventTypeEnum.REGISTRATION_STARTED,
        null,
        null,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        "LOCAL",
        occurredAt);

    return RegistrationCreationTransactionVO.scheduled(
        verification.getExpiresAt(),
        dispatchService.scheduleAfterCommit(new VerificationEmailDispatchRequestVO(
            registration.getUser().getEmail(),
            uriService.activationUri(verification.getToken()),
            verification.getToken(),
            verification.getExpiresAt(),
            locale,
            correlationId)));
  }
}
