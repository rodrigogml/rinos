package br.com.rinos.app.backend.module.identity.facade;

import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.dto.SecondFactorVerificationRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationOrchestrationStatusEnum;
import br.com.rinos.app.api.facade.SecondFactorFacade;
import br.com.rinos.app.api.vo.AuthenticationOrchestrationResultVO;
import br.com.rinos.app.backend.module.identity.service.SecondFactorService;

/**
 * Valida o contrato público e delega a composição atômica do segundo fator.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
@Service
@Lazy
public class SecondFactorFacadeImpl implements SecondFactorFacade {

  private final SecondFactorService service;

  /** Cria a fachada sobre a composição transacional. */
  public SecondFactorFacadeImpl(SecondFactorService service) {
    this.service = service;
  }

  /** {@inheritDoc} */
  @Override
  public AuthenticationOrchestrationResultVO verify(SecondFactorVerificationRequestDTO request) {
    if (request == null || request.challengeReference() == null
        || request.challengeReference().isBlank() || request.method() == null
        || request.proof() == null || request.proof().isBlank() || request.occurredAt() == null) {
      return AuthenticationOrchestrationFacadeImpl.rejected();
    }
    try {
      return AuthenticationOrchestrationFacadeImpl.publicView(service.verify(
          request.challengeReference(),
          br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.valueOf(
              request.method().name()),
          request.proof(),
          request.occurredAt()));
    } catch (RuntimeException unavailable) {
      return new AuthenticationOrchestrationResultVO(
          AuthenticationOrchestrationStatusEnum.UNAVAILABLE,
          null, null, null, Set.of(), List.of(), Set.of(), false, null, null);
    }
  }
}
