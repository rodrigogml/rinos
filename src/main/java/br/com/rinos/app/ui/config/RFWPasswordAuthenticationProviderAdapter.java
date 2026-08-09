package br.com.rinos.app.ui.config;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import br.com.rinos.app.api.dto.PasswordAuthenticationRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.api.enums.AuthenticationOrchestrationStatusEnum;
import br.com.rinos.app.api.facade.PasswordAuthenticationFacade;
import br.com.rinos.app.api.vo.PasswordAuthenticationResultVO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWPasswordAuthenticationRequestDTO;
import br.eng.rodrigogml.rfw.authentication.provider.RFWPasswordAuthenticationProvider;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAccessErrorVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationOutcomeVO;
import br.eng.rodrigogml.rfw.ui.access.provider.RFWRemoteAddressProvider;

/**
 * Conecta o primeiro fator por senha do RFW ao orquestrador global do Rinos.
 *
 * <p>O componente RFW valida a prova Turnstile no servidor antes de chamar este provider. O
 * adapter resolve novamente a origem pela fronteira confiável, cria uma correlação técnica e
 * publica somente outcomes neutros; senha, identificador, token e origem não entram em logs.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Component
public class RFWPasswordAuthenticationProviderAdapter
    implements RFWPasswordAuthenticationProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(
      RFWPasswordAuthenticationProviderAdapter.class);

  private final PasswordAuthenticationFacade facade;
  private final RFWRemoteAddressProvider remoteAddressProvider;
  private final RFWAuthenticationOutcomeAdapter outcomeAdapter;

  /** Cria o provider sobre contratos públicos e a origem validada compartilhada. */
  public RFWPasswordAuthenticationProviderAdapter(
      @Lazy PasswordAuthenticationFacade facade,
      RFWRemoteAddressProvider remoteAddressProvider,
      RFWAuthenticationOutcomeAdapter outcomeAdapter) {
    this.facade = facade;
    this.remoteAddressProvider = remoteAddressProvider;
    this.outcomeAdapter = outcomeAdapter;
  }

  /** {@inheritDoc} */
  @Override
  public CompletionStage<RFWAuthenticationOutcomeVO> authenticate(
      RFWPasswordAuthenticationRequestDTO request) {
    UUID correlationId = UUID.randomUUID();
    if (request == null || !(RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes)) {
      return completed(unavailable());
    }
    try {
      String origin = remoteAddressProvider.resolve(attributes.getRequest());
      String password = request.password();
      PasswordAuthenticationRequestDTO command = new PasswordAuthenticationRequestDTO(
          request.identifier(),
          password == null ? new char[0] : password.toCharArray(),
          request.rememberMe(),
          request.turnstileToken(),
          origin,
          correlationId);
      return facade.authenticate(command)
          .thenApply(this::map)
          .exceptionally(failure -> unavailable(correlationId, failure));
    } catch (RuntimeException unavailable) {
      return completed(unavailable(correlationId, unavailable));
    }
  }

  RFWAuthenticationOutcomeVO map(PasswordAuthenticationResultVO result) {
    if (result == null) {
      return unavailable();
    }
    if (result.orchestration().status() == AuthenticationOrchestrationStatusEnum.REJECTED
        && !result.retryAfter().isZero()) {
      return RFWAuthenticationOutcomeVO.rateLimited(new RFWAccessErrorVO(
          "authentication.sign-in.rate-limited",
          List.of(),
          Map.of(),
          result.retryAfter()));
    }
    return outcomeAdapter.map(result.orchestration(), AuthenticationFlowPurposeEnum.SIGN_IN);
  }

  private static RFWAuthenticationOutcomeVO unavailable(UUID correlationId, Throwable failure) {
    LOGGER.warn(
        "Autenticação por senha indisponível: correlationId={}, failureType={}",
        correlationId,
        failure.getClass().getSimpleName());
    return unavailable();
  }

  private static RFWAuthenticationOutcomeVO unavailable() {
    return RFWAuthenticationOutcomeVO.rejected(
        RFWAccessErrorVO.of("authentication.temporarily-unavailable"));
  }

  private static CompletionStage<RFWAuthenticationOutcomeVO> completed(
      RFWAuthenticationOutcomeVO outcome) {
    return CompletableFuture.completedFuture(outcome);
  }
}
