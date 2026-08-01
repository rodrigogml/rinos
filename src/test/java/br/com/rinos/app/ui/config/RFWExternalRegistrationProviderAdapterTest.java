package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;

import br.com.rinos.app.api.dto.ExternalRegistrationCompletionRequestDTO;
import br.com.rinos.app.api.enums.ExternalRegistrationCompletionStatusEnum;
import br.com.rinos.app.api.facade.ExternalRegistrationFacade;
import br.com.rinos.app.api.vo.ExternalRegistrationCompletionResultVO;
import br.com.rinos.app.api.vo.RinosUserPrincipalVO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWExternalRegistrationRequestDTO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAccessStatusEnum;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationOutcomeVO;

@DisplayName("Adapter RFW da conclusão externa")
class RFWExternalRegistrationProviderAdapterTest {

  @Test
  void complete_shouldAuthenticateOnlyFromCommittedFacadePrincipal() {
    ExternalRegistrationFacade facade = mock(ExternalRegistrationFacade.class);
    when(facade.complete(any())).thenReturn(CompletableFuture.completedFuture(
        ExternalRegistrationCompletionResultVO.authenticated(
            new RinosUserPrincipalVO(10L, "person@example.com"))));
    RFWExternalRegistrationProviderAdapter adapter =
        new RFWExternalRegistrationProviderAdapter(facade);

    RFWAuthenticationOutcomeVO outcome = adapter.completeExternalRegistration(
        new RFWExternalRegistrationRequestDTO(
            "opaque-reference",
            List.of("101", "102")))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.AUTHENTICATED);
    Authentication authentication = outcome.authentication();
    assertThat(authentication.isAuthenticated()).isTrue();
    assertThat(authentication.getPrincipal())
        .isEqualTo(new RinosUserPrincipalVO(10L, "person@example.com"));
    assertThat(authentication.getAuthorities()).isEmpty();
    ArgumentCaptor<ExternalRegistrationCompletionRequestDTO> request =
        ArgumentCaptor.forClass(ExternalRegistrationCompletionRequestDTO.class);
    org.mockito.Mockito.verify(facade).complete(request.capture());
    assertThat(request.getValue().registrationReference())
        .isEqualTo("opaque-reference");
    assertThat(request.getValue().acceptedLegalDocumentIds())
        .containsExactly("101", "102");
    assertThat(request.getValue().correlationId()).isNotNull();
  }

  @Test
  void complete_shouldMapLegalRejectionWithoutPublishingAuthentication() {
    ExternalRegistrationFacade facade = mock(ExternalRegistrationFacade.class);
    when(facade.complete(any())).thenReturn(CompletableFuture.completedFuture(
        ExternalRegistrationCompletionResultVO.validationRejected(
            Map.of(
                "acceptedLegalDocumentIds",
                "registration.error.legal-documents"))));
    RFWExternalRegistrationProviderAdapter adapter =
        new RFWExternalRegistrationProviderAdapter(facade);

    RFWAuthenticationOutcomeVO outcome = adapter.completeExternalRegistration(
        new RFWExternalRegistrationRequestDTO(
            "opaque-reference",
            List.of()))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.authentication()).isNull();
    assertThat(outcome.error().fieldErrors())
        .containsEntry(
            "acceptedLegalDocumentIds",
            "registration.error.legal-documents");
  }

  @Test
  void complete_shouldRejectReplayWithoutPublishingAuthentication() {
    ExternalRegistrationFacade facade = mock(ExternalRegistrationFacade.class);
    when(facade.complete(any())).thenReturn(CompletableFuture.completedFuture(
        ExternalRegistrationCompletionResultVO.of(
            ExternalRegistrationCompletionStatusEnum.INVALID_REFERENCE)));
    RFWExternalRegistrationProviderAdapter adapter =
        new RFWExternalRegistrationProviderAdapter(facade);

    RFWAuthenticationOutcomeVO outcome = adapter.completeExternalRegistration(
        new RFWExternalRegistrationRequestDTO(
            "used-reference",
            List.of()))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.authentication()).isNull();
    assertThat(outcome.error().messageKey())
        .isEqualTo("registration.google.completion.invalid-reference");
  }

  /**
   * Comprova que uma continuação expirada não pode produzir autenticação.
   */
  @Test
  void complete_shouldRejectWithoutAuthentication_whenReferenceExpired() {
    RFWAuthenticationOutcomeVO outcome = completeWithStatus(
        ExternalRegistrationCompletionStatusEnum.EXPIRED_REFERENCE);

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.authentication()).isNull();
    assertThat(outcome.error().messageKey())
        .isEqualTo("registration.google.completion.expired-reference");
  }

  /**
   * Comprova que uma corrida ou mudança concorrente encerra a continuação sem sessão.
   */
  @Test
  void complete_shouldRejectWithoutAuthentication_whenRegistrationConflicts() {
    RFWAuthenticationOutcomeVO outcome = completeWithStatus(
        ExternalRegistrationCompletionStatusEnum.CONFLICT);

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.authentication()).isNull();
    assertThat(outcome.error().messageKey())
        .isEqualTo("registration.google.completion.conflict");
  }

  /**
   * Comprova que indisponibilidade da conclusão não deixa autenticação parcial.
   */
  @Test
  void complete_shouldRejectWithoutAuthentication_whenCompletionIsUnavailable() {
    RFWAuthenticationOutcomeVO outcome = completeWithStatus(
        ExternalRegistrationCompletionStatusEnum.UNAVAILABLE);

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.authentication()).isNull();
    assertThat(outcome.error().messageKey())
        .isEqualTo("registration.google.unavailable");
  }

  /**
   * Executa o adapter com um resultado terminal seguro devolvido pela facade.
   *
   * @param status resultado público a traduzir
   * @return resultado equivalente do contrato RFW
   */
  private static RFWAuthenticationOutcomeVO completeWithStatus(
      ExternalRegistrationCompletionStatusEnum status) {
    ExternalRegistrationFacade facade = mock(ExternalRegistrationFacade.class);
    when(facade.complete(any())).thenReturn(CompletableFuture.completedFuture(
        ExternalRegistrationCompletionResultVO.of(status)));
    RFWExternalRegistrationProviderAdapter adapter =
        new RFWExternalRegistrationProviderAdapter(facade);

    return adapter.completeExternalRegistration(
        new RFWExternalRegistrationRequestDTO(
            "opaque-reference",
            List.of("101")))
        .toCompletableFuture()
        .join();
  }
}
