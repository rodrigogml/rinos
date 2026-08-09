package br.com.rinos.app.backend.module.identity.facade;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.dto.AuthenticationSessionPreparationRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationSessionLifecycleStatusEnum;
import br.com.rinos.app.api.facade.AuthenticationSessionLifecycleFacade;
import br.com.rinos.app.api.vo.AuthenticationSessionLifecycleResultVO;
import br.com.rinos.app.api.vo.RinosUserPrincipalVO;
import br.com.rinos.app.backend.module.identity.service.AuthenticationSessionLifecycleService;
import br.com.rinos.app.backend.module.identity.service.OriginAddressService;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationSessionLifecycleVO;

/**
 * Adapta o contrato público ao lifecycle transacional persistente.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Service
@Lazy
public class AuthenticationSessionLifecycleFacadeImpl
    implements AuthenticationSessionLifecycleFacade {

  private final AuthenticationSessionLifecycleService lifecycleService;
  private final OriginAddressService originAddressService;

  /** Cria a fachada com normalização explícita da origem confiável. */
  public AuthenticationSessionLifecycleFacadeImpl(
      AuthenticationSessionLifecycleService lifecycleService,
      OriginAddressService originAddressService) {
    this.lifecycleService = lifecycleService;
    this.originAddressService = originAddressService;
  }

  @Override
  public AuthenticationSessionLifecycleResultVO prepare(
      AuthenticationSessionPreparationRequestDTO request) {
    Objects.requireNonNull(request, "request must not be null");
    return publicView(lifecycleService.prepare(
        request.flowReference(),
        br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum.valueOf(
            request.purpose().name()),
        request.expectedUserId(),
        request.persistent(),
        originAddressService.normalize(request.canonicalOrigin()).getAddress(),
        request.userAgent(),
        request.occurredAt()));
  }

  @Override
  public AuthenticationSessionLifecycleResultVO publish(
      String sessionReference,
      Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    UUID reference = parse(sessionReference);
    return reference == null ? invalid() : publicView(lifecycleService.publish(reference, occurredAt));
  }

  @Override
  public AuthenticationSessionLifecycleResultVO validate(
      String sessionReference,
      Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    UUID reference = parse(sessionReference);
    return reference == null ? invalid() : publicView(lifecycleService.validate(reference, occurredAt));
  }

  @Override
  public void abort(String sessionReference, Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    UUID reference = parse(sessionReference);
    if (reference != null) {
      lifecycleService.abort(reference, occurredAt);
    }
  }

  @Override
  public void close(String sessionReference, Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    UUID reference = parse(sessionReference);
    if (reference != null) {
      lifecycleService.close(reference, occurredAt);
    }
  }

  private static AuthenticationSessionLifecycleResultVO publicView(
      AuthenticationSessionLifecycleVO result) {
    RinosUserPrincipalVO principal = result.userId() == null || result.email() == null
        ? null : new RinosUserPrincipalVO(result.userId(), result.email());
    return new AuthenticationSessionLifecycleResultVO(
        AuthenticationSessionLifecycleStatusEnum.valueOf(result.status().name()),
        result.sessionReference() == null ? null : result.sessionReference().toString(),
        principal,
        result.persistent(),
        result.absoluteExpiresAt());
  }

  private static AuthenticationSessionLifecycleResultVO invalid() {
    return new AuthenticationSessionLifecycleResultVO(
        AuthenticationSessionLifecycleStatusEnum.INVALID, null, null, false, null);
  }

  private static UUID parse(String reference) {
    if (reference == null || reference.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(reference);
    } catch (IllegalArgumentException invalidReference) {
      return null;
    }
  }
}
