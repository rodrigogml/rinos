package br.com.rinos.app.backend.module.identity.facade;

import java.time.Instant;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.dto.AuthenticationOrchestrationAdvanceDTO;
import br.com.rinos.app.api.dto.AuthenticationOrchestrationStartDTO;
import br.com.rinos.app.api.facade.AuthenticationOrchestrationFacade;
import br.com.rinos.app.api.vo.AuthenticationMethodEvidenceVO;
import br.com.rinos.app.api.vo.AuthenticationOrchestrationResultVO;
import br.com.rinos.app.api.vo.RinosUserPrincipalVO;
import br.com.rinos.app.backend.module.identity.service.AuthenticationOrchestrationService;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationOrchestrationDecisionVO;

/**
 * Mapeia o contrato público do orquestrador para o núcleo transacional de identidade.
 *
 * <p>A fachada não publica contexto de segurança nem sessão. Um principal somente atravessa esta
 * fronteira nos resultados {@code READY} e {@code COMPLETED}.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Service
@Lazy
public class AuthenticationOrchestrationFacadeImpl implements AuthenticationOrchestrationFacade {

  private final AuthenticationOrchestrationService orchestrationService;

  /**
   * Cria a fachada sobre a única autoridade transacional da orquestração.
   *
   * @param orchestrationService serviço de orquestração persistente
   */
  public AuthenticationOrchestrationFacadeImpl(
      AuthenticationOrchestrationService orchestrationService) {
    this.orchestrationService = orchestrationService;
  }

  @Override
  public AuthenticationOrchestrationResultVO start(AuthenticationOrchestrationStartDTO request) {
    Objects.requireNonNull(request, "request must not be null");
    return publicView(orchestrationService.start(
        request.userId(),
        backend(request.primaryMethod()),
        backend(request.requiredAssurance()),
        request.permittedMethods().stream()
            .map(AuthenticationOrchestrationFacadeImpl::backend)
            .collect(Collectors.toUnmodifiableSet()),
        request.persistentLoginRequested(),
        request.verifiedAt(),
        request.userVerification(),
        request.issuedAt(),
        request.expiresAt(),
        request.correlationId()));
  }

  @Override
  public AuthenticationOrchestrationResultVO advance(
      AuthenticationOrchestrationAdvanceDTO request) {
    Objects.requireNonNull(request, "request must not be null");
    return publicView(orchestrationService.advance(
        request.reference(),
        backend(request.method()),
        request.verifiedAt(),
        request.userVerification(),
        request.occurredAt()));
  }

  @Override
  public AuthenticationOrchestrationResultVO complete(String reference, Instant occurredAt) {
    return publicView(orchestrationService.complete(reference, occurredAt));
  }

  @Override
  public AuthenticationOrchestrationResultVO cancel(String reference, Instant occurredAt) {
    return publicView(orchestrationService.cancel(reference, occurredAt));
  }

  private static AuthenticationOrchestrationResultVO publicView(
      AuthenticationOrchestrationDecisionVO decision) {
    RinosUserPrincipalVO principal = decision.userId() == null || decision.email() == null
        ? null : new RinosUserPrincipalVO(decision.userId(), decision.email());
    return new AuthenticationOrchestrationResultVO(
        br.com.rinos.app.api.enums.AuthenticationOrchestrationStatusEnum.valueOf(
            decision.status().name()),
        decision.continuationReference(),
        principal,
        decision.achievedAssurance() == null ? null
            : br.com.rinos.app.api.enums.AuthenticationAssuranceEnum.valueOf(
                decision.achievedAssurance().name()),
        decision.permittedMethods().stream()
            .map(method -> br.com.rinos.app.api.enums.AuthenticationMethodEnum.valueOf(
                method.name()))
            .collect(Collectors.toUnmodifiableSet()),
        decision.verifiedMethods().stream()
            .map(method -> new AuthenticationMethodEvidenceVO(
                br.com.rinos.app.api.enums.AuthenticationMethodEnum.valueOf(
                    method.method().name()),
                method.verifiedAt(),
                method.userVerification()))
            .toList(),
        decision.missingLegalDocumentIds().stream()
            .map(String::valueOf)
            .collect(Collectors.toUnmodifiableSet()),
        decision.persistentLoginRequested(),
        decision.expiresAt(),
        decision.correlationId());
  }

  private static br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum backend(
      br.com.rinos.app.api.enums.AuthenticationMethodEnum value) {
    return value == null ? null
        : br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.valueOf(
            value.name());
  }

  private static br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum backend(
      br.com.rinos.app.api.enums.AuthenticationAssuranceEnum value) {
    return value == null ? null
        : br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum.valueOf(
            value.name());
  }
}
