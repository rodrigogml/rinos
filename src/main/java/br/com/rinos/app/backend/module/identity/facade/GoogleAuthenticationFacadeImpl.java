package br.com.rinos.app.backend.module.identity.facade;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.dto.AuthenticationOrchestrationStartDTO;
import br.com.rinos.app.api.dto.GoogleAuthenticationRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.enums.AuthenticationOrchestrationStatusEnum;
import br.com.rinos.app.api.facade.AuthenticationOrchestrationFacade;
import br.com.rinos.app.api.facade.GoogleAuthenticationFacade;
import br.com.rinos.app.api.vo.AuthenticationOrchestrationResultVO;
import br.com.rinos.app.api.vo.GoogleAuthenticationResultVO;
import br.com.rinos.app.backend.module.identity.enums.GoogleAuthenticationIdentityStatusEnum;
import br.com.rinos.app.backend.module.identity.service.AuthenticationMethodAvailabilityService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationSecondFactorPolicyService;
import br.com.rinos.app.backend.module.identity.service.GoogleAuthenticationIdentityService;
import br.com.rinos.app.backend.module.identity.vo.GoogleAuthenticationIdentityVO;
import br.com.rinos.app.config.AuthenticationMfaPropertiesConfig;

/**
 * Abre o fluxo comum somente para um vínculo Google ativo localizado pela chave estável.
 *
 * <p>A fachada não recebe e-mail ou claims. O orquestrador continua sendo a autoridade sobre MFA,
 * aceite legal e criação posterior da sessão.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
@Service
@Lazy
public class GoogleAuthenticationFacadeImpl implements GoogleAuthenticationFacade {

  private static final Logger LOGGER = LoggerFactory.getLogger(
      GoogleAuthenticationFacadeImpl.class);

  private final GoogleAuthenticationIdentityService identities;
  private final AuthenticationMethodAvailabilityService methodAvailability;
  private final AuthenticationSecondFactorPolicyService secondFactorPolicy;
  private final AuthenticationOrchestrationFacade orchestrationFacade;
  private final AuthenticationMfaPropertiesConfig mfaProperties;
  private final Clock clock;

  /** Cria a fachada com relógio UTC. */
  @Autowired
  public GoogleAuthenticationFacadeImpl(
      GoogleAuthenticationIdentityService identities,
      AuthenticationMethodAvailabilityService methodAvailability,
      AuthenticationSecondFactorPolicyService secondFactorPolicy,
      AuthenticationOrchestrationFacade orchestrationFacade,
      AuthenticationMfaPropertiesConfig mfaProperties) {
    this(identities, methodAvailability, secondFactorPolicy, orchestrationFacade,
        mfaProperties, Clock.systemUTC());
  }

  /** Cria uma instância com relógio controlável para testes. */
  GoogleAuthenticationFacadeImpl(
      GoogleAuthenticationIdentityService identities,
      AuthenticationMethodAvailabilityService methodAvailability,
      AuthenticationSecondFactorPolicyService secondFactorPolicy,
      AuthenticationOrchestrationFacade orchestrationFacade,
      AuthenticationMfaPropertiesConfig mfaProperties,
      Clock clock) {
    this.identities = identities;
    this.methodAvailability = methodAvailability;
    this.secondFactorPolicy = secondFactorPolicy;
    this.orchestrationFacade = orchestrationFacade;
    this.mfaProperties = mfaProperties;
    this.clock = clock;
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public CompletionStage<GoogleAuthenticationResultVO> authenticate(
      GoogleAuthenticationRequestDTO request) {
    if (request == null) {
      throw new NullPointerException("request must not be null");
    }
    Instant now = clock.instant();
    if (request.validatedAt().isAfter(now)
        || request.validatedAt().isBefore(now.minus(mfaProperties.challengeValidity()))) {
      return completed(orchestrated(AuthenticationOrchestrationStatusEnum.REJECTED));
    }
    try {
      GoogleAuthenticationIdentityVO identity = identities.resolve(
          request.issuer(), request.subject());
      if (identity.status() == GoogleAuthenticationIdentityStatusEnum.NOT_FOUND) {
        return completed(GoogleAuthenticationResultVO.identityNotFound());
      }
      if (identity.status() != GoogleAuthenticationIdentityStatusEnum.MATCHED) {
        return completed(orchestrated(AuthenticationOrchestrationStatusEnum.REJECTED));
      }
      long userId = identity.userId();
      Set<br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum> available =
          methodAvailability.availableMethods(userId);
      if (!available.contains(
          br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.GOOGLE)) {
        return completed(orchestrated(AuthenticationOrchestrationStatusEnum.REJECTED));
      }
      Set<AuthenticationMethodEnum> permitted = secondFactorPolicy.permittedMethods(
              br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.GOOGLE,
              available).stream()
          .map(method -> AuthenticationMethodEnum.valueOf(method.name()))
          .collect(Collectors.toUnmodifiableSet());
      AuthenticationAssuranceEnum required = secondFactorPolicy.requiresMultiFactor(available)
          ? AuthenticationAssuranceEnum.MULTI_FACTOR
          : AuthenticationAssuranceEnum.SINGLE_FACTOR;
      AuthenticationOrchestrationResultVO result = orchestrationFacade.start(
          new AuthenticationOrchestrationStartDTO(
              userId,
              AuthenticationMethodEnum.GOOGLE,
              required,
              permitted,
              false,
              request.validatedAt(),
              null,
              now,
              now.plus(mfaProperties.challengeValidity()),
              request.correlationId()));
      return completed(GoogleAuthenticationResultVO.orchestrated(result));
    } catch (RuntimeException unavailable) {
      LOGGER.warn(
          "Autenticacao Google indisponivel: correlationId={}, failureType={}",
          request.correlationId(),
          unavailable.getClass().getSimpleName());
      return completed(orchestrated(AuthenticationOrchestrationStatusEnum.UNAVAILABLE));
    }
  }

  private static GoogleAuthenticationResultVO orchestrated(
      AuthenticationOrchestrationStatusEnum status) {
    return GoogleAuthenticationResultVO.orchestrated(new AuthenticationOrchestrationResultVO(
        status, null, null, null, Set.of(), List.of(), Set.of(), false, null, null));
  }

  private static CompletionStage<GoogleAuthenticationResultVO> completed(
      GoogleAuthenticationResultVO result) {
    return CompletableFuture.completedFuture(result);
  }
}
