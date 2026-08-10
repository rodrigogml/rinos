package br.com.rinos.app.ui.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import br.com.rinos.app.api.dto.EmailOtpEmissionRequestDTO;
import br.com.rinos.app.api.dto.SecondFactorVerificationRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.enums.EmailOtpEmissionStatusEnum;
import br.com.rinos.app.api.facade.EmailOtpFacade;
import br.com.rinos.app.api.facade.SecondFactorFacade;
import br.com.rinos.app.api.vo.AuthenticationOrchestrationResultVO;
import br.com.rinos.app.api.vo.EmailOtpEmissionResultVO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWSecondFactorEmissionRequestDTO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWSecondFactorRequestDTO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAuthenticationMethodEnum;
import br.eng.rodrigogml.rfw.authentication.provider.RFWSecondFactorProvider;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAccessErrorVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationOutcomeVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWSecondFactorEmissionOutcomeVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWSecondFactorEmissionVO;

/**
 * Conecta o desafio contextual do RFW aos fatores adicionais globais do Rinos.
 *
 * <p>Somente e-mail requer emissão e ela ocorre após escolha explícita no componente RFW. TOTP e
 * recovery code são verificados diretamente, sem qualquer efeito de envio.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
@Component
public class RFWSecondFactorProviderAdapter implements RFWSecondFactorProvider {

  private static final Set<RFWAuthenticationMethodEnum> EMISSION_METHODS =
      Set.of(RFWAuthenticationMethodEnum.EMAIL_CODE);

  private final EmailOtpFacade emailOtpFacade;
  private final SecondFactorFacade secondFactorFacade;
  private final RFWAuthenticationOutcomeAdapter outcomeAdapter;
  private final Clock clock;

  /** Cria o provider com relógio UTC. */
  @Autowired
  public RFWSecondFactorProviderAdapter(
      @Lazy EmailOtpFacade emailOtpFacade,
      @Lazy SecondFactorFacade secondFactorFacade,
      RFWAuthenticationOutcomeAdapter outcomeAdapter) {
    this(emailOtpFacade, secondFactorFacade, outcomeAdapter, Clock.systemUTC());
  }

  /** Cria uma instância com relógio controlável para testes. */
  RFWSecondFactorProviderAdapter(
      EmailOtpFacade emailOtpFacade,
      SecondFactorFacade secondFactorFacade,
      RFWAuthenticationOutcomeAdapter outcomeAdapter,
      Clock clock) {
    this.emailOtpFacade = emailOtpFacade;
    this.secondFactorFacade = secondFactorFacade;
    this.outcomeAdapter = outcomeAdapter;
    this.clock = clock;
  }

  /** {@inheritDoc} */
  @Override
  public Set<RFWAuthenticationMethodEnum> getEmissionMethods() {
    return EMISSION_METHODS;
  }

  /** {@inheritDoc} */
  @Override
  public CompletionStage<RFWSecondFactorEmissionOutcomeVO> begin(
      RFWSecondFactorEmissionRequestDTO request) {
    return emit(request, false);
  }

  /** {@inheritDoc} */
  @Override
  public CompletionStage<RFWSecondFactorEmissionOutcomeVO> resend(
      RFWSecondFactorEmissionRequestDTO request) {
    return emit(request, true);
  }

  /** {@inheritDoc} */
  @Override
  public CompletionStage<RFWAuthenticationOutcomeVO> verify(RFWSecondFactorRequestDTO request) {
    if (request == null || request.challengeId() == null || request.challengeId().isBlank()
        || request.method() == null || request.proof() == null || request.proof().isBlank()) {
      return completed(unavailable());
    }
    try {
      AuthenticationOrchestrationResultVO result = secondFactorFacade.verify(
          new SecondFactorVerificationRequestDTO(
              request.challengeId(), AuthenticationMethodEnum.valueOf(request.method().name()),
              request.proof(), clock.instant()));
      return completed(outcomeAdapter.map(result, AuthenticationFlowPurposeEnum.SIGN_IN));
    } catch (RuntimeException unavailable) {
      return completed(unavailable());
    }
  }

  private CompletionStage<RFWSecondFactorEmissionOutcomeVO> emit(
      RFWSecondFactorEmissionRequestDTO request,
      boolean resend) {
    if (request == null || request.challengeReference() == null
        || request.challengeReference().isBlank()
        || request.method() != RFWAuthenticationMethodEnum.EMAIL_CODE
        || request.origin() == null || request.origin().isBlank()) {
      return completedEmission(emissionUnavailable());
    }
    try {
      Instant now = clock.instant();
      Locale locale = LocaleContextHolder.getLocale();
      CompletionStage<EmailOtpEmissionResultVO> operation = resend
          ? emailOtpFacade.resend(new EmailOtpEmissionRequestDTO(
              request.challengeReference(), locale, now))
          : emailOtpFacade.begin(new EmailOtpEmissionRequestDTO(
              request.challengeReference(), locale, now));
      return operation.thenApply(result -> mapEmission(result, now))
          .exceptionally(ignored -> emissionUnavailable());
    } catch (RuntimeException unavailable) {
      return completedEmission(emissionUnavailable());
    }
  }

  private static RFWSecondFactorEmissionOutcomeVO mapEmission(
      EmailOtpEmissionResultVO result,
      Instant now) {
    if (result == null) {
      return emissionUnavailable();
    }
    if (result.status() == EmailOtpEmissionStatusEnum.EMITTED) {
      return RFWSecondFactorEmissionOutcomeVO.emitted(new RFWSecondFactorEmissionVO(
          result.challengeReference(), RFWAuthenticationMethodEnum.EMAIL_CODE,
          result.maskedDestination(), result.expiresAt(), result.resendAvailableAt()));
    }
    if (result.status() == EmailOtpEmissionStatusEnum.RATE_LIMITED) {
      Duration retryAfter = Duration.between(now, result.retryAfter());
      if (retryAfter.isNegative()) {
        retryAfter = Duration.ZERO;
      }
      return RFWSecondFactorEmissionOutcomeVO.rejected(new RFWAccessErrorVO(
          "authentication.sign-in.rate-limited", List.of(), Map.of(), retryAfter));
    }
    return emissionUnavailable();
  }

  private static RFWSecondFactorEmissionOutcomeVO emissionUnavailable() {
    return RFWSecondFactorEmissionOutcomeVO.rejected(
        RFWAccessErrorVO.of("authentication.temporarily-unavailable"));
  }

  private static RFWAuthenticationOutcomeVO unavailable() {
    return RFWAuthenticationOutcomeVO.rejected(
        RFWAccessErrorVO.of("authentication.temporarily-unavailable"));
  }

  private static <T> CompletionStage<T> completed(T value) {
    return CompletableFuture.completedFuture(value);
  }

  private static CompletionStage<RFWSecondFactorEmissionOutcomeVO> completedEmission(
      RFWSecondFactorEmissionOutcomeVO value) {
    return CompletableFuture.completedFuture(value);
  }
}
