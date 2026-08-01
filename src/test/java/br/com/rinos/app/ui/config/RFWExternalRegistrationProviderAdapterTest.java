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

    var outcome = adapter.completeExternalRegistration(
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

    var outcome = adapter.completeExternalRegistration(
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

    var outcome = adapter.completeExternalRegistration(
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
}
