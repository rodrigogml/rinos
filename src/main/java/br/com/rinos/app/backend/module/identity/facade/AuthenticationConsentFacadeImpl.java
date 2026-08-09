package br.com.rinos.app.backend.module.identity.facade;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.dto.AuthenticationConsentRequestDTO;
import br.com.rinos.app.api.facade.AuthenticationConsentFacade;
import br.com.rinos.app.api.vo.AuthenticationOrchestrationResultVO;
import br.com.rinos.app.backend.module.identity.service.AuthenticationOrchestrationService;

/**
 * Converte referências públicas do gate legal para a fronteira transacional da identidade.
 *
 * @author Rodrigo Leitão
 */
@Service
@Lazy
public class AuthenticationConsentFacadeImpl implements AuthenticationConsentFacade {

  private final AuthenticationOrchestrationService orchestrationService;

  /** Cria a facade sobre a autoridade única de orquestração. */
  public AuthenticationConsentFacadeImpl(
      AuthenticationOrchestrationService orchestrationService) {
    this.orchestrationService = Objects.requireNonNull(
        orchestrationService, "orchestrationService must not be null");
  }

  @Override
  public AuthenticationOrchestrationResultVO complete(AuthenticationConsentRequestDTO request) {
    Objects.requireNonNull(request, "request must not be null");
    Set<Long> acceptedIds;
    try {
      acceptedIds = request.acceptedLegalDocumentIds().stream()
          .map(AuthenticationConsentFacadeImpl::parsePositiveId)
          .collect(Collectors.toUnmodifiableSet());
    } catch (IllegalArgumentException invalidReference) {
      return AuthenticationOrchestrationFacadeImpl.rejected();
    }
    return AuthenticationOrchestrationFacadeImpl.publicView(
        orchestrationService.completeLegalConsent(
            request.continuationReference(), acceptedIds, request.occurredAt()));
  }

  @Override
  public AuthenticationOrchestrationResultVO cancel(
      String continuationReference,
      Instant occurredAt) {
    return AuthenticationOrchestrationFacadeImpl.publicView(
        orchestrationService.cancel(continuationReference, occurredAt));
  }

  private static Long parsePositiveId(String reference) {
    try {
      long id = Long.parseLong(reference);
      if (id <= 0) {
        throw new IllegalArgumentException("legalDocumentId must be positive");
      }
      return id;
    } catch (NumberFormatException invalidReference) {
      throw new IllegalArgumentException("legalDocumentId is invalid", invalidReference);
    }
  }
}
