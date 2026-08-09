package br.com.rinos.app.backend.module.identity.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.dto.AuthenticationConsentRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationOrchestrationStatusEnum;
import br.com.rinos.app.backend.module.identity.service.AuthenticationOrchestrationService;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationOrchestrationDecisionVO;

/**
 * Verifica conversão e rejeição segura na facade de aceite pós-autenticação.
 *
 * @author Rodrigo Leitão
 */
class AuthenticationConsentFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

  @Test
  void complete_shouldConvertDocumentReferencesAndPreserveOpaqueContinuation() {
    AuthenticationOrchestrationService service = mock(AuthenticationOrchestrationService.class);
    AuthenticationOrchestrationDecisionVO decision = new AuthenticationOrchestrationDecisionVO(
        br.com.rinos.app.backend.module.identity.enums.AuthenticationOrchestrationStatusEnum.REJECTED,
        null, null, null, null, Set.of(), List.of(), Set.of(), false, null, null);
    when(service.completeLegalConsent("opaque-reference", Set.of(7L, 8L), NOW))
        .thenReturn(decision);
    AuthenticationConsentFacadeImpl facade = new AuthenticationConsentFacadeImpl(service);

    var result = facade.complete(new AuthenticationConsentRequestDTO(
        "opaque-reference", List.of("7", "8"), NOW));

    assertThat(result.status()).isEqualTo(AuthenticationOrchestrationStatusEnum.REJECTED);
    verify(service).completeLegalConsent("opaque-reference", Set.of(7L, 8L), NOW);
  }

  @Test
  void complete_shouldRejectInvalidDocumentReferenceWithoutCallingDomain() {
    AuthenticationOrchestrationService service = mock(AuthenticationOrchestrationService.class);
    AuthenticationConsentFacadeImpl facade = new AuthenticationConsentFacadeImpl(service);

    var result = facade.complete(new AuthenticationConsentRequestDTO(
        "opaque-reference", List.of("not-an-id"), NOW));

    assertThat(result.status()).isEqualTo(AuthenticationOrchestrationStatusEnum.REJECTED);
    verify(service, never()).completeLegalConsent(
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any());
  }
}
