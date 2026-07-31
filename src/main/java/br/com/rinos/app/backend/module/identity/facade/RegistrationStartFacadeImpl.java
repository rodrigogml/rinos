package br.com.rinos.app.backend.module.identity.facade;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.dto.RegistrationStartRequestDTO;
import br.com.rinos.app.api.enums.RegistrationStartStatusEnum;
import br.com.rinos.app.api.facade.RegistrationStartFacade;
import br.com.rinos.app.api.vo.RegistrationStartResultVO;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.LegalConsentDecisionEnum;
import br.com.rinos.app.backend.module.identity.enums.PasswordPolicyViolationEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationOperationEnum;
import br.com.rinos.app.backend.module.identity.service.IdentityAuditService;
import br.com.rinos.app.backend.module.identity.service.IdentityService;
import br.com.rinos.app.backend.module.identity.service.LegalConsentService;
import br.com.rinos.app.backend.module.identity.service.OriginAddressService;
import br.com.rinos.app.backend.module.identity.service.PasswordPreparationService;
import br.com.rinos.app.backend.module.identity.service.RegistrationCreationService;
import br.com.rinos.app.backend.module.identity.service.RegistrationObservabilityService;
import br.com.rinos.app.backend.module.identity.vo.PasswordPreparationResultVO;
import br.com.rinos.app.backend.module.identity.vo.RegistrationCreationTransactionVO;

/**
 * Implementa o início do cadastro e mantém a escrita atrás de uma única transação interna.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class RegistrationStartFacadeImpl implements RegistrationStartFacade {

  private final IdentityService identityService;
  private final PasswordPreparationService passwordPreparationService;
  private final LegalConsentService legalConsentService;
  private final OriginAddressService originAddressService;
  private final RegistrationCreationService registrationCreationService;
  private final IdentityAuditService auditService;
  private final RegistrationObservabilityService observabilityService;
  private final Clock clock;

  /**
   * Cria a fachada com o relógio UTC da aplicação.
   */
  @Autowired
  public RegistrationStartFacadeImpl(
      IdentityService identityService,
      PasswordPreparationService passwordPreparationService,
      LegalConsentService legalConsentService,
      OriginAddressService originAddressService,
      RegistrationCreationService registrationCreationService,
      IdentityAuditService auditService,
      RegistrationObservabilityService observabilityService) {
    this(
        identityService,
        passwordPreparationService,
        legalConsentService,
        originAddressService,
        registrationCreationService,
        auditService,
        observabilityService,
        Clock.systemUTC());
  }

  RegistrationStartFacadeImpl(
      IdentityService identityService,
      PasswordPreparationService passwordPreparationService,
      LegalConsentService legalConsentService,
      OriginAddressService originAddressService,
      RegistrationCreationService registrationCreationService,
      IdentityAuditService auditService,
      RegistrationObservabilityService observabilityService,
      Clock clock) {
    this.identityService = identityService;
    this.passwordPreparationService = passwordPreparationService;
    this.legalConsentService = legalConsentService;
    this.originAddressService = originAddressService;
    this.registrationCreationService = registrationCreationService;
    this.auditService = auditService;
    this.observabilityService = Objects.requireNonNull(
        observabilityService,
        "observabilityService must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CompletionStage<RegistrationStartResultVO> start(
      RegistrationStartRequestDTO request) {
    Objects.requireNonNull(request, "request must not be null");
    Instant startedAt = clock.instant();
    return observe(
        startInternal(request, startedAt),
        request.getCorrelationId(),
        startedAt);
  }

  private CompletionStage<RegistrationStartResultVO> startInternal(
      RegistrationStartRequestDTO request,
      Instant occurredAt) {
    char[] transferredPassword = request.consumePassword();
    try {
      Optional<UserEntity> existing;
      try {
        existing = identityService.findByEmail(request.getEmail());
      } catch (IllegalArgumentException invalidEmail) {
        RegistrationStartResultVO result = new RegistrationStartResultVO(
            RegistrationStartStatusEnum.VALIDATION_REJECTED,
            Map.of("email", "registration.error.email-invalid"),
            null);
        auditRejected(null, request, result.status(), occurredAt);
        return completed(result);
      }
      if (existing.isPresent()) {
        RegistrationStartResultVO result = classify(existing.get());
        auditRejected(existing.get(), request, result.status(), occurredAt);
        return completed(result);
      }

      PasswordPreparationResultVO password =
          passwordPreparationService.prepare(transferredPassword);
      if (!password.getValidation().accepted()) {
        RegistrationStartResultVO result = passwordRejected(password);
        auditRejected(null, request, result.status(), occurredAt);
        return completed(result);
      }

      Map<Long, LegalConsentDecisionEnum> legalDecisions;
      try {
        List<Long> acceptedDocumentIds =
            parseDocumentIds(request.getAcceptedLegalDocumentIds());
        legalDecisions = legalConsentService.validatePublishedAcceptances(
            acceptedDocumentIds,
            occurredAt);
      } catch (IllegalArgumentException invalidLegalDocuments) {
        RegistrationStartResultVO result = new RegistrationStartResultVO(
            RegistrationStartStatusEnum.VALIDATION_REJECTED,
            Map.of("acceptedLegalDocumentIds", "registration.error.legal-documents"),
            null);
        auditRejected(null, request, result.status(), occurredAt);
        return completed(result);
      }
      RegistrationCreationTransactionVO transaction =
          registrationCreationService.create(
              request.getEmail(),
              password.getEncodedHash(),
              legalDecisions,
              originAddressService.normalize(request.getCanonicalOrigin()),
              request.getLocale(),
              request.getCorrelationId(),
              occurredAt);
      if (transaction.blocked()) {
        Duration retryAfter = Duration.between(occurredAt, transaction.blockedUntil());
        RegistrationStartResultVO result = new RegistrationStartResultVO(
            RegistrationStartStatusEnum.RATE_LIMITED,
            Map.of(),
            retryAfter.isNegative() ? Duration.ZERO : retryAfter);
        auditRejected(null, request, result.status(), occurredAt);
        return completed(result);
      }
      return transaction.dispatch().thenApply(dispatch -> RegistrationStartResultVO.of(
          dispatch.accepted()
              ? RegistrationStartStatusEnum.EMAIL_SENT
              : RegistrationStartStatusEnum.EMAIL_DISPATCH_FAILED));
    } catch (DataIntegrityViolationException collision) {
      Optional<UserEntity> winner = identityService.findByEmail(request.getEmail());
      RegistrationStartResultVO result = winner
          .map(this::classify)
          .orElseGet(() -> RegistrationStartResultVO.of(
              RegistrationStartStatusEnum.UNAVAILABLE));
      auditRejected(winner.orElse(null), request, result.status(), occurredAt);
      return completed(result);
    } catch (RuntimeException unavailable) {
      RegistrationStartResultVO result = RegistrationStartResultVO.of(
          RegistrationStartStatusEnum.UNAVAILABLE);
      auditRejected(null, request, result.status(), occurredAt);
      return completed(result);
    } finally {
      Arrays.fill(transferredPassword, '\0');
    }
  }

  private CompletionStage<RegistrationStartResultVO> observe(
      CompletionStage<RegistrationStartResultVO> result,
      UUID correlationId,
      Instant startedAt) {
    return result.whenComplete((value, failure) -> recordObservation(
        RegistrationOperationEnum.START,
        failure == null && value != null
            ? value.status().name()
            : "UNEXPECTED_FAILURE",
        correlationId,
        startedAt));
  }

  private void recordObservation(
      RegistrationOperationEnum operation,
      String resultCode,
      UUID correlationId,
      Instant startedAt) {
    try {
      observabilityService.recordOperation(
          operation,
          resultCode,
          correlationId,
          startedAt,
          clock.instant());
    } catch (RuntimeException ignoredObservabilityFailure) {
      // A telemetria não participa da decisão funcional nem da resposta pública.
    }
  }

  private RegistrationStartResultVO classify(UserEntity user) {
    return RegistrationStartResultVO.of(
        user.getStatus() == UserStatusEnum.PENDING_VERIFICATION
            ? RegistrationStartStatusEnum.PENDING_ALREADY_EXISTS
            : RegistrationStartStatusEnum.EMAIL_ALREADY_EXISTS);
  }

  private static RegistrationStartResultVO passwordRejected(
      PasswordPreparationResultVO password) {
        PasswordPolicyViolationEnum violation =
        password.getValidation().violations().getFirst();
    return new RegistrationStartResultVO(
        RegistrationStartStatusEnum.VALIDATION_REJECTED,
        Map.of("password", "registration.error.password."
            + violation.name().toLowerCase(Locale.ROOT).replace('_', '-')),
        null);
  }

  private static List<Long> parseDocumentIds(List<String> documentIds) {
    return documentIds.stream()
        .map(Long::valueOf)
        .toList();
  }

  private static CompletionStage<RegistrationStartResultVO> completed(
      RegistrationStartResultVO result) {
    return CompletableFuture.completedFuture(result);
  }

  private void auditRejected(
      UserEntity user,
      RegistrationStartRequestDTO request,
      RegistrationStartStatusEnum status,
      Instant occurredAt) {
    try {
      auditService.record(
          user,
          null,
          request.getCorrelationId(),
          IdentityEventTypeEnum.REGISTRATION_REJECTED,
          null,
          null,
          IdentityTransitionOriginEnum.SELF_SERVICE,
          status.name(),
          occurredAt);
    } catch (RuntimeException ignoredAuditFailure) {
      // A rejeição já não produz identidade; indisponibilidade da auditoria não deve abrir o fluxo.
    }
  }
}
