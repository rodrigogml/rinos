package br.com.rinos.app.ui.config;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import br.com.rinos.app.api.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.api.vo.AuthenticationOrchestrationResultVO;
import br.com.rinos.app.api.vo.RinosAuthenticationCompletionVO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAuthenticationMethodEnum;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAccessChallengeVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAccessErrorVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationConsentChallengeVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationOutcomeVO;

/**
 * Traduz decisões do orquestrador para a máquina de estados pública do RFW.
 *
 * <p>Somente {@code READY} materializa uma autenticação, ainda com a continuação efêmera
 * necessária ao lifecycle. Nenhuma decisão cria authorities de tenant.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Component
public class RFWAuthenticationOutcomeAdapter {

  /** Converte o resultado sem expor causas internas ou referências em mensagens. */
  public RFWAuthenticationOutcomeVO map(
      AuthenticationOrchestrationResultVO result,
      AuthenticationFlowPurposeEnum purpose) {
    Objects.requireNonNull(result, "result must not be null");
    Objects.requireNonNull(purpose, "purpose must not be null");
    return switch (result.status()) {
      case READY -> authenticated(result, purpose);
      case CHALLENGE_REQUIRED -> challenge(result);
      case LEGAL_CONSENT_REQUIRED -> legalConsent(result);
      case CANCELLED -> RFWAuthenticationOutcomeVO.completed(
          "authentication.flow.cancelled");
      case REJECTED -> rejected("authentication.credentials.invalid");
      case EXPIRED -> rejected("authentication.flow.expired");
      case CONFLICT -> rejected("authentication.flow.conflict");
      case UNAVAILABLE -> rejected("authentication.temporarily-unavailable");
    };
  }

  private static RFWAuthenticationOutcomeVO authenticated(
      AuthenticationOrchestrationResultVO result,
      AuthenticationFlowPurposeEnum purpose) {
    UsernamePasswordAuthenticationToken authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            result.principal(), null, List.of());
    authentication.setDetails(new RinosAuthenticationCompletionVO(
        result.continuationReference(), purpose));
    return RFWAuthenticationOutcomeVO.authenticated(authentication);
  }

  private static RFWAuthenticationOutcomeVO challenge(
      AuthenticationOrchestrationResultVO result) {
    Set<RFWAuthenticationMethodEnum> allowed = result.permittedMethods().stream()
        .map(method -> RFWAuthenticationMethodEnum.valueOf(method.name()))
        .collect(Collectors.toUnmodifiableSet());
    RFWAuthenticationMethodEnum selected = allowed.stream()
        .min(Comparator.comparingInt(Enum::ordinal))
        .orElseThrow(() -> new IllegalStateException("Authentication challenge has no method"));
    return RFWAuthenticationOutcomeVO.challengeRequired(new RFWAccessChallengeVO(
        result.continuationReference(),
        selected,
        null,
        result.expiresAt(),
        allowed));
  }

  private static RFWAuthenticationOutcomeVO legalConsent(
      AuthenticationOrchestrationResultVO result) {
    return RFWAuthenticationOutcomeVO.authenticationConsentRequired(
        new RFWAuthenticationConsentChallengeVO(
            result.continuationReference(),
            result.missingLegalDocumentIds(),
            result.expiresAt(),
            result.persistentLoginRequested()));
  }

  private static RFWAuthenticationOutcomeVO rejected(String messageKey) {
    return RFWAuthenticationOutcomeVO.rejected(RFWAccessErrorVO.of(messageKey));
  }
}
