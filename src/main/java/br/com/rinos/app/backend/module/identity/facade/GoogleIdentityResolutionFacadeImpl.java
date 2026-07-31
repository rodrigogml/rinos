package br.com.rinos.app.backend.module.identity.facade;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.enums.GoogleIdentityResolutionStatusEnum;
import br.com.rinos.app.api.facade.GoogleIdentityResolutionFacade;
import br.com.rinos.app.api.vo.GoogleIdentityResolutionRequestVO;
import br.com.rinos.app.api.vo.GoogleIdentityResolutionResultVO;
import br.com.rinos.app.backend.module.identity.enums.GoogleIdentityDomainStatusEnum;
import br.com.rinos.app.backend.module.identity.service.GoogleIdentityResolutionService;
import br.com.rinos.app.backend.module.identity.vo.GoogleIdentityDomainResultVO;
import br.eng.rodrigogml.rfw.platform.authentication.config.RFWAuthenticationPropertiesConfig;

/**
 * Valida a borda mínima Google e delega a decisão persistente ao serviço transacional.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class GoogleIdentityResolutionFacadeImpl implements GoogleIdentityResolutionFacade {

  private static final String GOOGLE_PROVIDER_ID = "google";

  private final GoogleIdentityResolutionService resolutionService;
  private final RFWAuthenticationPropertiesConfig authenticationProperties;
  private final Clock clock;

  /**
   * Cria a fachada com relógio UTC da aplicação.
   *
   * @param resolutionService resolução transacional
   * @param authenticationProperties emissor configurado e validado
   */
  public GoogleIdentityResolutionFacadeImpl(
      GoogleIdentityResolutionService resolutionService,
      RFWAuthenticationPropertiesConfig authenticationProperties) {
    this(resolutionService, authenticationProperties, Clock.systemUTC());
  }

  GoogleIdentityResolutionFacadeImpl(
      GoogleIdentityResolutionService resolutionService,
      RFWAuthenticationPropertiesConfig authenticationProperties,
      Clock clock) {
    this.resolutionService = resolutionService;
    this.authenticationProperties = authenticationProperties;
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CompletionStage<GoogleIdentityResolutionResultVO> resolve(
      GoogleIdentityResolutionRequestVO request) {
    if (request == null || request.correlationId() == null
        || isBlank(request.providerId()) || isBlank(request.issuer())
        || isBlank(request.subject()) || isBlank(request.email())) {
      return completed(GoogleIdentityResolutionResultVO.of(
          GoogleIdentityResolutionStatusEnum.EXTERNAL_IDENTITY_REJECTED));
    }
    if (!request.emailVerified()) {
      return completed(GoogleIdentityResolutionResultVO.of(
          GoogleIdentityResolutionStatusEnum.EXTERNAL_EMAIL_NOT_VERIFIED));
    }
    if (!authenticationProperties.google().enabled()
        || !GOOGLE_PROVIDER_ID.equals(request.providerId())
        || !authenticationProperties.google().issuer().equals(request.issuer())) {
      return completed(GoogleIdentityResolutionResultVO.of(
          GoogleIdentityResolutionStatusEnum.EXTERNAL_IDENTITY_REJECTED));
    }

    Instant occurredAt = clock.instant();
    try {
      return completed(map(resolveWithSingleCollisionRetry(request, occurredAt)));
    } catch (IllegalArgumentException rejected) {
      return completed(GoogleIdentityResolutionResultVO.of(
          GoogleIdentityResolutionStatusEnum.EXTERNAL_IDENTITY_REJECTED));
    } catch (RuntimeException unavailable) {
      return completed(GoogleIdentityResolutionResultVO.of(
          GoogleIdentityResolutionStatusEnum.UNAVAILABLE));
    }
  }

  private GoogleIdentityDomainResultVO resolveWithSingleCollisionRetry(
      GoogleIdentityResolutionRequestVO request,
      Instant occurredAt) {
    try {
      return resolveDomain(request, occurredAt);
    } catch (DataIntegrityViolationException collision) {
      return resolveDomain(request, occurredAt);
    }
  }

  private GoogleIdentityDomainResultVO resolveDomain(
      GoogleIdentityResolutionRequestVO request,
      Instant occurredAt) {
    return resolutionService.resolve(
        request.issuer(),
        request.subject(),
        request.email(),
        request.correlationId(),
        occurredAt);
  }

  private static GoogleIdentityResolutionResultVO map(
      GoogleIdentityDomainResultVO result) {
    if (result.status() == GoogleIdentityDomainStatusEnum.CONTINUATION_REQUIRED) {
      return GoogleIdentityResolutionResultVO.continuation(
          result.continuationToken(),
          GOOGLE_PROVIDER_ID,
          result.verifiedEmail(),
          result.expiresAt());
    }
    GoogleIdentityResolutionStatusEnum publicStatus = switch (result.status()) {
      case EXISTING_USER_REAUTHENTICATION_REQUIRED ->
          GoogleIdentityResolutionStatusEnum.EXISTING_USER_REAUTHENTICATION_REQUIRED;
      case EXTERNAL_IDENTITY_CONFLICT ->
          GoogleIdentityResolutionStatusEnum.EXTERNAL_IDENTITY_CONFLICT;
      case CONTINUATION_REQUIRED -> throw new IllegalStateException(
          "continuation must be mapped with its required data");
    };
    return GoogleIdentityResolutionResultVO.of(publicStatus);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static CompletionStage<GoogleIdentityResolutionResultVO> completed(
      GoogleIdentityResolutionResultVO result) {
    return CompletableFuture.completedFuture(result);
  }
}
