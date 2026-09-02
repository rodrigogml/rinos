package br.com.rinos.app.backend.module.identity.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import br.com.rinos.app.api.dto.ExternalRegistrationCompletionRequestDTO;
import br.com.rinos.app.api.enums.ExternalRegistrationCompletionStatusEnum;
import br.com.rinos.app.api.vo.ExternalRegistrationCompletionResultVO;
import br.com.rinos.app.api.vo.RinosUserPrincipalVO;
import br.com.rinos.app.backend.module.identity.service.ExternalRegistrationCompletionService;

@DisplayName("Fachada da conclusão externa")
class ExternalRegistrationFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
  private static final UUID CORRELATION_ID =
      UUID.fromString("95f6724a-67bf-49fe-90f4-873c96b446ab");

  @Test
  void context_shouldCreateFacade_whenTestConstructorAlsoExists() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.registerBean(
          ExternalRegistrationCompletionService.class,
          () -> mock(ExternalRegistrationCompletionService.class));
      context.register(ExternalRegistrationFacadeImpl.class);

      context.refresh();

      assertThat(context.getBean(ExternalRegistrationFacadeImpl.class)).isNotNull();
    }
  }

  @Test
  void complete_shouldPublishPrincipalReturnedAfterDomainCompletion() {
    ExternalRegistrationCompletionService service =
        mock(ExternalRegistrationCompletionService.class);
    when(service.complete(
        "opaque-reference",
        List.of(101L, 102L),
        CORRELATION_ID,
        NOW)).thenReturn(ExternalRegistrationCompletionResultVO.authenticated(
            new br.com.rinos.app.api.vo.RegistrationAuthenticationContinuationVO(
                new RinosUserPrincipalVO(10L, "person@example.com"),
                new br.com.rinos.app.api.vo.RinosAuthenticationCompletionVO(
                    "registration-facade-continuation",
                    br.com.rinos.app.api.enums.AuthenticationFlowPurposeEnum
                        .REGISTRATION_ACTIVATION))));
    ExternalRegistrationFacadeImpl facade = facade(service);

    ExternalRegistrationCompletionResultVO result = facade.complete(
        new ExternalRegistrationCompletionRequestDTO(
            "opaque-reference",
            List.of("101", "102"),
            CORRELATION_ID))
        .toCompletableFuture()
        .join();

    assertThat(result.status())
        .isEqualTo(ExternalRegistrationCompletionStatusEnum.AUTHENTICATED);
    assertThat(result.principal().userId()).isEqualTo(10L);
    verify(service).complete(
        "opaque-reference",
        List.of(101L, 102L),
        CORRELATION_ID,
        NOW);
  }

  @Test
  void complete_shouldRejectInvalidDocumentBeforeDomainCall() {
    ExternalRegistrationCompletionService service =
        mock(ExternalRegistrationCompletionService.class);
    ExternalRegistrationFacadeImpl facade = facade(service);

    ExternalRegistrationCompletionResultVO result = facade.complete(
        new ExternalRegistrationCompletionRequestDTO(
            "opaque-reference",
            List.of("not-a-number"),
            CORRELATION_ID))
        .toCompletableFuture()
        .join();

    assertThat(result.status())
        .isEqualTo(ExternalRegistrationCompletionStatusEnum.VALIDATION_REJECTED);
    verifyNoInteractions(service);
  }

  /**
   * Comprova que a troca de versão jurídica exige nova decisão sem expor a falha interna.
   */
  @Test
  void complete_shouldReturnRecoverableFieldError_whenLegalDocumentChanged() {
    ExternalRegistrationCompletionService service =
        mock(ExternalRegistrationCompletionService.class);
    when(service.complete(
        "opaque-reference",
        List.of(101L),
        CORRELATION_ID,
        NOW)).thenThrow(new IllegalArgumentException("document is no longer current"));
    ExternalRegistrationFacadeImpl facade = facade(service);

    ExternalRegistrationCompletionResultVO result = facade.complete(
        new ExternalRegistrationCompletionRequestDTO(
            "opaque-reference",
            List.of("101"),
            CORRELATION_ID))
        .toCompletableFuture()
        .join();

    assertThat(result.status())
        .isEqualTo(ExternalRegistrationCompletionStatusEnum.VALIDATION_REJECTED);
    assertThat(result.principal()).isNull();
    assertThat(result.fieldErrors()).containsEntry(
        "acceptedLegalDocumentIds",
        "registration.error.legal-documents");
  }

  @Test
  void complete_shouldReturnUnavailableWithoutPrincipal_whenCommitFails() {
    ExternalRegistrationCompletionService service =
        mock(ExternalRegistrationCompletionService.class);
    when(service.complete(
        "opaque-reference",
        List.of(101L),
        CORRELATION_ID,
        NOW)).thenThrow(new IllegalStateException("commit unavailable"));
    ExternalRegistrationFacadeImpl facade = facade(service);

    ExternalRegistrationCompletionResultVO result = facade.complete(
        new ExternalRegistrationCompletionRequestDTO(
            "opaque-reference",
            List.of("101"),
            CORRELATION_ID))
        .toCompletableFuture()
        .join();

    assertThat(result.status())
        .isEqualTo(ExternalRegistrationCompletionStatusEnum.UNAVAILABLE);
    assertThat(result.principal()).isNull();
  }

  private static ExternalRegistrationFacadeImpl facade(
      ExternalRegistrationCompletionService service) {
    return new ExternalRegistrationFacadeImpl(
        service,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }
}
