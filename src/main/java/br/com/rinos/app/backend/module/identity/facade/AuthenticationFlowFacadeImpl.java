package br.com.rinos.app.backend.module.identity.facade;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.dto.AuthenticationFlowIssueRequestDTO;
import br.com.rinos.app.api.dto.AuthenticationProofIssueRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.api.enums.AuthenticationProofTypeEnum;
import br.com.rinos.app.api.facade.AuthenticationFlowFacade;
import br.com.rinos.app.api.vo.AuthenticationFlowResultVO;
import br.com.rinos.app.api.vo.AuthenticationProofResultVO;
import br.com.rinos.app.backend.module.identity.service.AuthenticationFlowService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationProofService;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowInspectionVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationProofInspectionVO;
import br.com.rinos.app.backend.module.identity.vo.IssuedAuthenticationFlowVO;

/**
 * Mapeia o contrato público interno para os serviços persistentes sem publicar entities.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Service
@Lazy
public class AuthenticationFlowFacadeImpl implements AuthenticationFlowFacade {

  private final AuthenticationFlowService flowService;
  private final AuthenticationProofService proofService;

  public AuthenticationFlowFacadeImpl(
      AuthenticationFlowService flowService,
      AuthenticationProofService proofService) {
    this.flowService = flowService;
    this.proofService = proofService;
  }

  @Override
  public AuthenticationFlowResultVO issueFlow(AuthenticationFlowIssueRequestDTO request) {
    Objects.requireNonNull(request, "request must not be null");
    IssuedAuthenticationFlowVO issued = flowService.issue(
        request.userId(),
        backend(request.purpose()),
        backend(request.primaryMethod()),
        backend(request.requiredAssurance()),
        request.permittedMethods().stream()
            .map(AuthenticationFlowFacadeImpl::backend)
            .collect(Collectors.toUnmodifiableSet()),
        request.persistentLoginRequested(),
        request.issuedAt(),
        request.expiresAt(),
        request.correlationId());
    AuthenticationFlowResultVO inspected = inspectFlow(
        issued.reference(),
        request.purpose(),
        request.issuedAt());
    return new AuthenticationFlowResultVO(
        inspected.status(),
        issued.reference(),
        inspected.userId(),
        inspected.purpose(),
        inspected.primaryMethod(),
        inspected.requiredAssurance(),
        inspected.permittedMethods(),
        inspected.persistentLoginRequested(),
        issued.expiresAt(),
        issued.correlationId());
  }

  @Override
  public AuthenticationFlowResultVO inspectFlow(
      String reference,
      AuthenticationFlowPurposeEnum purpose,
      Instant occurredAt) {
    return publicView(flowService.inspect(reference, backend(purpose), occurredAt), null);
  }

  @Override
  public AuthenticationFlowResultVO consumeFlow(
      String reference,
      AuthenticationFlowPurposeEnum purpose,
      Instant occurredAt) {
    return publicView(flowService.consume(reference, backend(purpose), occurredAt), null);
  }

  @Override
  public AuthenticationFlowResultVO cancelFlow(
      String reference,
      AuthenticationFlowPurposeEnum purpose,
      Instant occurredAt) {
    return publicView(flowService.cancel(reference, backend(purpose), occurredAt), null);
  }

  @Override
  public AuthenticationProofResultVO issueProof(AuthenticationProofIssueRequestDTO request) {
    Objects.requireNonNull(request, "request must not be null");
    return publicView(proofService.issue(
        request.getFlowReference(),
        backend(request.getPurpose()),
        backend(request.getType()),
        request.getProofDigest(),
        request.getKeyVersion(),
        request.getIssuedAt(),
        request.getExpiresAt()));
  }

  @Override
  public AuthenticationProofResultVO inspectProof(
      String reference,
      AuthenticationFlowPurposeEnum purpose,
      AuthenticationProofTypeEnum type,
      Instant occurredAt) {
    return publicView(proofService.inspect(
        reference,
        backend(purpose),
        backend(type),
        occurredAt));
  }

  @Override
  public AuthenticationProofResultVO consumeProof(
      String reference,
      AuthenticationFlowPurposeEnum purpose,
      AuthenticationProofTypeEnum type,
      byte[] candidateDigest,
      Instant occurredAt) {
    Objects.requireNonNull(candidateDigest, "candidateDigest must not be null");
    byte[] digest = Arrays.copyOf(candidateDigest, candidateDigest.length);
    try {
      return publicView(proofService.consume(
          reference,
          backend(purpose),
          backend(type),
          digest,
          occurredAt));
    } finally {
      Arrays.fill(digest, (byte) 0);
    }
  }

  @Override
  public AuthenticationProofResultVO cancelProof(
      String reference,
      AuthenticationFlowPurposeEnum purpose,
      AuthenticationProofTypeEnum type,
      Instant occurredAt) {
    return publicView(proofService.cancel(
        reference,
        backend(purpose),
        backend(type),
        occurredAt));
  }

  private static AuthenticationFlowResultVO publicView(
      AuthenticationFlowInspectionVO value,
      String reference) {
    Set<br.com.rinos.app.api.enums.AuthenticationMethodEnum> methods = value.permittedMethods()
        .stream()
        .map(method -> br.com.rinos.app.api.enums.AuthenticationMethodEnum.valueOf(method.name()))
        .collect(Collectors.toUnmodifiableSet());
    return new AuthenticationFlowResultVO(
        br.com.rinos.app.api.enums.AuthenticationOperationStatusEnum.valueOf(value.status().name()),
        reference,
        value.userId(),
        value.purpose() == null ? null : AuthenticationFlowPurposeEnum.valueOf(value.purpose().name()),
        value.primaryMethod() == null ? null
            : br.com.rinos.app.api.enums.AuthenticationMethodEnum.valueOf(
                value.primaryMethod().name()),
        value.requiredAssurance() == null ? null
            : br.com.rinos.app.api.enums.AuthenticationAssuranceEnum.valueOf(
                value.requiredAssurance().name()),
        methods,
        value.persistentLoginRequested(),
        value.expiresAt(),
        value.correlationId());
  }

  private static AuthenticationProofResultVO publicView(AuthenticationProofInspectionVO value) {
    return new AuthenticationProofResultVO(
        br.com.rinos.app.api.enums.AuthenticationOperationStatusEnum.valueOf(value.status().name()),
        value.type() == null ? null : AuthenticationProofTypeEnum.valueOf(value.type().name()),
        value.attemptCount(),
        value.expiresAt());
  }

  private static br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum backend(
      AuthenticationFlowPurposeEnum value) {
    return value == null ? null
        : br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum.valueOf(
            value.name());
  }

  private static br.com.rinos.app.backend.module.identity.enums.AuthenticationProofTypeEnum backend(
      AuthenticationProofTypeEnum value) {
    return value == null ? null
        : br.com.rinos.app.backend.module.identity.enums.AuthenticationProofTypeEnum.valueOf(value.name());
  }

  private static br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum backend(
      br.com.rinos.app.api.enums.AuthenticationMethodEnum value) {
    return value == null ? null
        : br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.valueOf(value.name());
  }

  private static br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum backend(
      br.com.rinos.app.api.enums.AuthenticationAssuranceEnum value) {
    return value == null ? null
        : br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum.valueOf(value.name());
  }
}
