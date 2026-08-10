package br.com.rinos.app.backend.module.identity.facade;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
import br.com.rinos.app.api.dto.AuthenticationOrchestrationAdvanceDTO;
import br.com.rinos.app.api.dto.PasskeyAuthenticationRequestDTO;
import br.com.rinos.app.api.dto.PasskeySecondFactorAuthenticationRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.enums.AuthenticationOrchestrationStatusEnum;
import br.com.rinos.app.api.facade.AuthenticationOrchestrationFacade;
import br.com.rinos.app.api.facade.AuthenticationFlowFacade;
import br.com.rinos.app.api.facade.PasskeyAuthenticationFacade;
import br.com.rinos.app.api.vo.AuthenticationOrchestrationResultVO;
import br.com.rinos.app.api.vo.AuthenticationFlowResultVO;
import br.com.rinos.app.backend.module.identity.entity.PasskeyUserEntity;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.PasskeyUserRepository;
import br.com.rinos.app.backend.module.identity.service.AuthenticationMethodAvailabilityService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationSecondFactorPolicyService;
import br.com.rinos.app.config.AuthenticationMfaPropertiesConfig;

/**
 * Revalida o owner WebAuthn e abre o fluxo transacional comum de autenticação.
 *
 * <p>A assertion e a credential nunca atravessam esta fachada. O handle identifica somente o vínculo
 * validado pelo Spring; o orquestrador volta a conferir usuário e métodos ativos antes de qualquer sessão.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
@Service
@Lazy
public class PasskeyAuthenticationFacadeImpl implements PasskeyAuthenticationFacade {

  private static final Logger LOGGER = LoggerFactory.getLogger(
      PasskeyAuthenticationFacadeImpl.class);

  private final PasskeyUserRepository passkeyUsers;
  private final AuthenticationMethodAvailabilityService methodAvailability;
  private final AuthenticationSecondFactorPolicyService secondFactorPolicy;
  private final AuthenticationFlowFacade flowFacade;
  private final AuthenticationOrchestrationFacade orchestrationFacade;
  private final AuthenticationMfaPropertiesConfig mfaProperties;
  private final Clock clock;

  /** Cria a fachada com relógio UTC. */
  @Autowired
  public PasskeyAuthenticationFacadeImpl(
      PasskeyUserRepository passkeyUsers,
      AuthenticationMethodAvailabilityService methodAvailability,
      AuthenticationSecondFactorPolicyService secondFactorPolicy,
      AuthenticationFlowFacade flowFacade,
      AuthenticationOrchestrationFacade orchestrationFacade,
      AuthenticationMfaPropertiesConfig mfaProperties) {
    this(passkeyUsers, methodAvailability, secondFactorPolicy, flowFacade, orchestrationFacade,
        mfaProperties, Clock.systemUTC());
  }

  /** Cria uma instância com relógio controlável para testes. */
  PasskeyAuthenticationFacadeImpl(
      PasskeyUserRepository passkeyUsers,
      AuthenticationMethodAvailabilityService methodAvailability,
      AuthenticationSecondFactorPolicyService secondFactorPolicy,
      AuthenticationFlowFacade flowFacade,
      AuthenticationOrchestrationFacade orchestrationFacade,
      AuthenticationMfaPropertiesConfig mfaProperties,
      Clock clock) {
    this.passkeyUsers = passkeyUsers;
    this.methodAvailability = methodAvailability;
    this.secondFactorPolicy = secondFactorPolicy;
    this.flowFacade = flowFacade;
    this.orchestrationFacade = orchestrationFacade;
    this.mfaProperties = mfaProperties;
    this.clock = clock;
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public CompletionStage<AuthenticationOrchestrationResultVO> authenticate(
      PasskeyAuthenticationRequestDTO request) {
    if (request == null) {
      throw new NullPointerException("request must not be null");
    }
    Instant now = clock.instant();
    if (request.validatedAt().isAfter(now)
        || request.validatedAt().isBefore(now.minus(mfaProperties.challengeValidity()))) {
      return completed(terminal(AuthenticationOrchestrationStatusEnum.REJECTED));
    }
    try {
      PasskeyUserEntity owner = passkeyUsers.findByUserHandle(request.userHandle()).orElse(null);
      if (owner == null || owner.getUser().getStatus() != UserStatusEnum.ACTIVE) {
        return completed(terminal(AuthenticationOrchestrationStatusEnum.REJECTED));
      }
      long userId = owner.getUser().getId();
      Set<br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum> available =
          methodAvailability.availableMethods(userId);
      if (!available.contains(
          br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.PASSKEY)) {
        return completed(terminal(AuthenticationOrchestrationStatusEnum.REJECTED));
      }
      Set<AuthenticationMethodEnum> permitted = secondFactorPolicy.permittedMethods(
              br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.PASSKEY,
              available).stream()
          .map(method -> AuthenticationMethodEnum.valueOf(method.name()))
          .collect(java.util.stream.Collectors.toUnmodifiableSet());
      AuthenticationAssuranceEnum required = secondFactorPolicy.requiresMultiFactor(available)
          ? AuthenticationAssuranceEnum.MULTI_FACTOR
          : AuthenticationAssuranceEnum.SINGLE_FACTOR;
      return completed(orchestrationFacade.start(new AuthenticationOrchestrationStartDTO(
          userId,
          AuthenticationMethodEnum.PASSKEY,
          required,
          permitted,
          false,
          request.validatedAt(),
          true,
          now,
          now.plus(mfaProperties.challengeValidity()),
          request.correlationId())));
    } catch (RuntimeException unavailable) {
      LOGGER.warn(
          "Autenticação por passkey indisponível: correlationId={}, failureType={}",
          request.correlationId(),
          unavailable.getClass().getSimpleName());
      return completed(terminal(AuthenticationOrchestrationStatusEnum.UNAVAILABLE));
    }
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public CompletionStage<AuthenticationOrchestrationResultVO> authenticateSecondFactor(
      PasskeySecondFactorAuthenticationRequestDTO request) {
    if (request == null) {
      throw new NullPointerException("request must not be null");
    }
    Instant now = clock.instant();
    if (request.validatedAt().isAfter(now)
        || request.validatedAt().isBefore(now.minus(mfaProperties.challengeValidity()))) {
      return completed(terminal(AuthenticationOrchestrationStatusEnum.REJECTED));
    }
    try {
      PasskeyUserEntity owner = passkeyUsers.findByUserHandle(request.userHandle()).orElse(null);
      AuthenticationFlowResultVO flow = flowFacade.inspectFlow(
          request.challengeReference(),
          br.com.rinos.app.api.enums.AuthenticationFlowPurposeEnum.SIGN_IN,
          now);
      if (owner == null || owner.getUser().getStatus() != UserStatusEnum.ACTIVE
          || flow.status() != br.com.rinos.app.api.enums.AuthenticationOperationStatusEnum.OPEN
          || flow.userId() == null
          || !flow.userId().equals(owner.getUser().getId())
          || !flow.permittedMethods().contains(AuthenticationMethodEnum.PASSKEY)) {
        return completed(terminal(AuthenticationOrchestrationStatusEnum.REJECTED));
      }
      return completed(orchestrationFacade.advance(new AuthenticationOrchestrationAdvanceDTO(
          request.challengeReference(),
          AuthenticationMethodEnum.PASSKEY,
          request.validatedAt(),
          true,
          now)));
    } catch (RuntimeException unavailable) {
      LOGGER.warn(
          "Segundo fator por passkey indisponivel: correlationId={}, failureType={}",
          request.correlationId(),
          unavailable.getClass().getSimpleName());
      return completed(terminal(AuthenticationOrchestrationStatusEnum.UNAVAILABLE));
    }
  }

  private static AuthenticationOrchestrationResultVO terminal(
      AuthenticationOrchestrationStatusEnum status) {
    return new AuthenticationOrchestrationResultVO(
        status, null, null, null, Set.of(), List.of(), Set.of(), false, null, null);
  }

  private static CompletionStage<AuthenticationOrchestrationResultVO> completed(
      AuthenticationOrchestrationResultVO result) {
    return CompletableFuture.completedFuture(result);
  }
}
