package br.com.rinos.app.backend.module.identity.facade;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.dto.AuthenticationOrchestrationStartDTO;
import br.com.rinos.app.api.dto.PasswordAuthenticationRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.enums.AuthenticationOrchestrationStatusEnum;
import br.com.rinos.app.api.facade.AuthenticationOrchestrationFacade;
import br.com.rinos.app.api.facade.PasswordAuthenticationFacade;
import br.com.rinos.app.api.vo.AuthenticationOrchestrationResultVO;
import br.com.rinos.app.api.vo.PasswordAuthenticationResultVO;
import br.com.rinos.app.backend.module.identity.service.AuthenticationAbuseProtectionService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationMethodAvailabilityService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationSecondFactorPolicyService;
import br.com.rinos.app.backend.module.identity.service.EmailNormalizationService;
import br.com.rinos.app.backend.module.identity.service.IdentityAuditService;
import br.com.rinos.app.backend.module.identity.service.PasswordCredentialAuthenticationService;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationAbuseDecisionVO;
import br.com.rinos.app.config.AuthenticationMfaPropertiesConfig;

/**
 * Mantém a prova de senha e a abertura do fluxo na mesma transação bloqueada.
 *
 * <p>Falhas são contabilizadas nas dimensões independentes de identificador e origem. A validação
 * server-side do token Turnstile ocorre no RFW antes da chamada desta fachada.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Service
@Lazy
public class PasswordAuthenticationFacadeImpl implements PasswordAuthenticationFacade {

  private static final Logger LOGGER = LoggerFactory.getLogger(
      PasswordAuthenticationFacadeImpl.class);

  private final PasswordCredentialAuthenticationService credentialAuthenticationService;
  private final AuthenticationAbuseProtectionService abuseProtectionService;
  private final AuthenticationMethodAvailabilityService methodAvailabilityService;
  private final AuthenticationSecondFactorPolicyService secondFactorPolicy;
  private final AuthenticationOrchestrationFacade orchestrationFacade;
  private final AuthenticationMfaPropertiesConfig mfaProperties;
  private final UserRepository userRepository;
  private final EmailNormalizationService emailNormalizationService;
  private final IdentityAuditService identityAuditService;
  private final Clock clock;

  /** Cria a fachada com relógio UTC. */
  public PasswordAuthenticationFacadeImpl(
      PasswordCredentialAuthenticationService credentialAuthenticationService,
      AuthenticationAbuseProtectionService abuseProtectionService,
      AuthenticationMethodAvailabilityService methodAvailabilityService,
      AuthenticationSecondFactorPolicyService secondFactorPolicy,
      AuthenticationOrchestrationFacade orchestrationFacade,
      AuthenticationMfaPropertiesConfig mfaProperties) {
    this(credentialAuthenticationService, abuseProtectionService, methodAvailabilityService,
        secondFactorPolicy,
        orchestrationFacade,
        mfaProperties, null, null, null, Clock.systemUTC());
  }

  /** Cria a fachada com o registro interno de falhas repetidas habilitado. */
  @Autowired
  public PasswordAuthenticationFacadeImpl(
      PasswordCredentialAuthenticationService credentialAuthenticationService,
      AuthenticationAbuseProtectionService abuseProtectionService,
      AuthenticationMethodAvailabilityService methodAvailabilityService,
      AuthenticationSecondFactorPolicyService secondFactorPolicy,
      AuthenticationOrchestrationFacade orchestrationFacade,
      AuthenticationMfaPropertiesConfig mfaProperties,
      UserRepository userRepository,
      EmailNormalizationService emailNormalizationService,
      IdentityAuditService identityAuditService) {
    this(credentialAuthenticationService, abuseProtectionService, methodAvailabilityService,
        secondFactorPolicy, orchestrationFacade, mfaProperties, userRepository,
        emailNormalizationService, identityAuditService, Clock.systemUTC());
  }

  /** Cria uma instância com relógio controlável para testes. */
  PasswordAuthenticationFacadeImpl(
      PasswordCredentialAuthenticationService credentialAuthenticationService,
      AuthenticationAbuseProtectionService abuseProtectionService,
      AuthenticationMethodAvailabilityService methodAvailabilityService,
      AuthenticationSecondFactorPolicyService secondFactorPolicy,
      AuthenticationOrchestrationFacade orchestrationFacade,
      AuthenticationMfaPropertiesConfig mfaProperties,
      Clock clock) {
    this(credentialAuthenticationService, abuseProtectionService, methodAvailabilityService,
        secondFactorPolicy, orchestrationFacade, mfaProperties, null, null, null, clock);
  }

  /** Cria uma instância com relógio controlável e observabilidade completa. */
  PasswordAuthenticationFacadeImpl(
      PasswordCredentialAuthenticationService credentialAuthenticationService,
      AuthenticationAbuseProtectionService abuseProtectionService,
      AuthenticationMethodAvailabilityService methodAvailabilityService,
      AuthenticationSecondFactorPolicyService secondFactorPolicy,
      AuthenticationOrchestrationFacade orchestrationFacade,
      AuthenticationMfaPropertiesConfig mfaProperties,
      UserRepository userRepository,
      EmailNormalizationService emailNormalizationService,
      IdentityAuditService identityAuditService,
      Clock clock) {
    this.credentialAuthenticationService = credentialAuthenticationService;
    this.abuseProtectionService = abuseProtectionService;
    this.methodAvailabilityService = methodAvailabilityService;
    this.secondFactorPolicy = secondFactorPolicy;
    this.orchestrationFacade = orchestrationFacade;
    this.mfaProperties = mfaProperties;
    this.userRepository = userRepository;
    this.emailNormalizationService = emailNormalizationService;
    this.identityAuditService = identityAuditService;
    this.clock = clock;
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public CompletionStage<PasswordAuthenticationResultVO> authenticate(
      PasswordAuthenticationRequestDTO request) {
    if (request == null) {
      throw new NullPointerException("request must not be null");
    }
    Instant now = clock.instant();
    try {
      OptionalLong verifiedUser = credentialAuthenticationService.verify(
          request.identifier(), request.consumePassword(), now);
      if (verifiedUser.isEmpty()) {
        AuthenticationAbuseDecisionVO abuse = abuseProtectionService.registerFailure(
            request.identifier(), request.canonicalOrigin(), now);
        if (abuse.turnstileRequired()
            && abuseProtectionService.isIdentifierTurnstileRequired(request.identifier(), now)) {
          recordRepeatedFailureIfKnown(request.identifier(), request.correlationId(), now);
        }
        return completed(new PasswordAuthenticationResultVO(
            terminal(AuthenticationOrchestrationStatusEnum.REJECTED),
            abuse.turnstileRequired(),
            abuse.retryAfter()));
      }
      long userId = verifiedUser.getAsLong();
      Set<br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum> available =
          methodAvailabilityService.availableMethods(userId);
      Set<br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum> backendPermitted =
          secondFactorPolicy.permittedMethods(
              br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.PASSWORD,
              available);
      Set<AuthenticationMethodEnum> permitted = backendPermitted.stream()
          .map(method -> AuthenticationMethodEnum.valueOf(method.name()))
          .collect(java.util.stream.Collectors.toUnmodifiableSet());
      AuthenticationAssuranceEnum requiredAssurance = secondFactorPolicy.requiresMultiFactor(available)
              ? AuthenticationAssuranceEnum.MULTI_FACTOR
              : AuthenticationAssuranceEnum.SINGLE_FACTOR;
      return completed(new PasswordAuthenticationResultVO(
          orchestrationFacade.start(new AuthenticationOrchestrationStartDTO(
          userId,
          AuthenticationMethodEnum.PASSWORD,
          requiredAssurance,
          permitted,
          request.persistentLoginRequested(),
          now,
          null,
          now,
          now.plus(mfaProperties.challengeValidity()),
          request.correlationId())),
          false,
          java.time.Duration.ZERO));
    } catch (RuntimeException unavailable) {
      LOGGER.warn(
          "Primeiro fator por senha indisponível: correlationId={}, failureType={}",
          request.correlationId(),
          unavailable.getClass().getSimpleName());
      return completed(new PasswordAuthenticationResultVO(
          terminal(AuthenticationOrchestrationStatusEnum.UNAVAILABLE),
          false,
          java.time.Duration.ZERO));
    }
  }

  private void recordRepeatedFailureIfKnown(
      String identifier,
      java.util.UUID correlationId,
      Instant occurredAt) {
    if (userRepository == null || emailNormalizationService == null || identityAuditService == null) {
      return;
    }
    try {
      String normalized = emailNormalizationService.normalize(identifier).normalizedEmail();
      UserEntity user = userRepository.findByNormalizedEmailForUpdate(normalized).orElse(null);
      if (user != null && user.getStatus() == UserStatusEnum.ACTIVE) {
        identityAuditService.record(
            user,
            null,
            correlationId,
            IdentityEventTypeEnum.AUTHENTICATION_REPEATED_FAILURES,
            null,
            null,
            IdentityTransitionOriginEnum.SELF_SERVICE,
            "TURNSTILE_THRESHOLD",
            occurredAt);
      }
    } catch (RuntimeException ignored) {
      // Neutralidade pública prevalece quando a consulta de observabilidade falha.
    }
  }

  private static AuthenticationOrchestrationResultVO terminal(
      AuthenticationOrchestrationStatusEnum status) {
    return new AuthenticationOrchestrationResultVO(
        status, null, null, null, Set.of(), List.of(), Set.of(), false, null, null);
  }

  private static CompletionStage<PasswordAuthenticationResultVO> completed(
      PasswordAuthenticationResultVO result) {
    return CompletableFuture.completedFuture(result);
  }
}
