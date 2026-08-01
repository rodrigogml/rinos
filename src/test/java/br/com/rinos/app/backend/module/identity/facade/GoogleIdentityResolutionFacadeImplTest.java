package br.com.rinos.app.backend.module.identity.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import br.com.rinos.app.api.enums.GoogleIdentityResolutionStatusEnum;
import br.com.rinos.app.api.vo.GoogleIdentityResolutionRequestVO;
import br.com.rinos.app.api.vo.GoogleIdentityResolutionResultVO;
import br.com.rinos.app.backend.module.identity.enums.GoogleIdentityDomainStatusEnum;
import br.com.rinos.app.backend.module.identity.service.GoogleIdentityResolutionService;
import br.com.rinos.app.backend.module.identity.vo.GoogleIdentityDomainResultVO;
import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig;
import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig.GoogleConfig;

@DisplayName("Fachada de resolução da identidade Google")
class GoogleIdentityResolutionFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
  private static final String ISSUER = "https://accounts.google.com";

  @Test
  void resolve_shouldReturnContinuation_whenDomainAcceptsVerifiedIdentity() {
    GoogleIdentityResolutionService service = mock(GoogleIdentityResolutionService.class);
    GoogleIdentityResolutionRequestVO request = validRequest();
    when(service.resolve(
        request.issuer(),
        request.subject(),
        request.email(),
        request.correlationId(),
        NOW))
        .thenReturn(GoogleIdentityDomainResultVO.continuation(
            "opaque-reference",
            "person@example.com",
            NOW.plusSeconds(600)));

    GoogleIdentityResolutionResultVO result =
        facade(service).resolve(request).toCompletableFuture().join();

    assertThat(result.status())
        .isEqualTo(GoogleIdentityResolutionStatusEnum.CONTINUATION_REQUIRED);
    assertThat(result.registrationReference()).isEqualTo("opaque-reference");
    assertThat(result.providerId()).isEqualTo("google");
    assertThat(result.verifiedEmail()).isEqualTo("person@example.com");
  }

  @Test
  void resolve_shouldRejectWithoutCallingDomain_whenEmailIsNotVerified() {
    GoogleIdentityResolutionService service = mock(GoogleIdentityResolutionService.class);
    GoogleIdentityResolutionRequestVO request = new GoogleIdentityResolutionRequestVO(
        "google",
        ISSUER,
        "subject-1",
        "person@example.com",
        false,
        UUID.randomUUID());

    GoogleIdentityResolutionResultVO result =
        facade(service).resolve(request).toCompletableFuture().join();

    assertThat(result.status())
        .isEqualTo(GoogleIdentityResolutionStatusEnum.EXTERNAL_EMAIL_NOT_VERIFIED);
    verifyNoInteractions(service);
  }

  @Test
  void resolve_shouldRejectWithoutCallingDomain_whenIssuerDiffersFromConfiguration() {
    GoogleIdentityResolutionService service = mock(GoogleIdentityResolutionService.class);
    GoogleIdentityResolutionRequestVO request = new GoogleIdentityResolutionRequestVO(
        "google",
        "https://untrusted.example",
        "subject-1",
        "person@example.com",
        true,
        UUID.randomUUID());

    GoogleIdentityResolutionResultVO result =
        facade(service).resolve(request).toCompletableFuture().join();

    assertThat(result.status())
        .isEqualTo(GoogleIdentityResolutionStatusEnum.EXTERNAL_IDENTITY_REJECTED);
    verifyNoInteractions(service);
  }

  @Test
  void resolve_shouldRetryOnce_whenConcurrentConstraintSelectsAnotherWinner() {
    GoogleIdentityResolutionService service = mock(GoogleIdentityResolutionService.class);
    GoogleIdentityResolutionRequestVO request = validRequest();
    when(service.resolve(
        request.issuer(),
        request.subject(),
        request.email(),
        request.correlationId(),
        NOW))
        .thenThrow(new DataIntegrityViolationException("concurrent winner"))
        .thenReturn(GoogleIdentityDomainResultVO.of(
            GoogleIdentityDomainStatusEnum.EXTERNAL_IDENTITY_CONFLICT));

    GoogleIdentityResolutionResultVO result =
        facade(service).resolve(request).toCompletableFuture().join();

    assertThat(result.status())
        .isEqualTo(GoogleIdentityResolutionStatusEnum.EXTERNAL_IDENTITY_CONFLICT);
  }

  @Test
  void resolve_shouldReturnUnavailable_whenDomainFails() {
    GoogleIdentityResolutionService service = mock(GoogleIdentityResolutionService.class);
    GoogleIdentityResolutionRequestVO request = validRequest();
    when(service.resolve(
        request.issuer(),
        request.subject(),
        request.email(),
        request.correlationId(),
        NOW))
        .thenThrow(new IllegalStateException("database unavailable"));

    GoogleIdentityResolutionResultVO result =
        facade(service).resolve(request).toCompletableFuture().join();

    assertThat(result.status()).isEqualTo(GoogleIdentityResolutionStatusEnum.UNAVAILABLE);
  }

  private static GoogleIdentityResolutionFacadeImpl facade(
      GoogleIdentityResolutionService service) {
    GoogleConfig google = new GoogleConfig(
        true,
        "client-id",
        ISSUER,
        Duration.ofSeconds(10),
        Duration.ofSeconds(60));
    RFWAuthenticationPropertiesConfig properties =
        new RFWAuthenticationPropertiesConfig(google, null, null, null);
    return new GoogleIdentityResolutionFacadeImpl(
        service,
        properties,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static GoogleIdentityResolutionRequestVO validRequest() {
    return new GoogleIdentityResolutionRequestVO(
        "google",
        ISSUER,
        "subject-1",
        "person@example.com",
        true,
        UUID.randomUUID());
  }
}
