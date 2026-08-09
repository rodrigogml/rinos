package br.com.rinos.app.backend.module.identity.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.enums.AuthenticationOrchestrationStatusEnum;
import br.com.rinos.app.api.vo.AuthenticationOrchestrationResultVO;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.service.AuthenticationOrchestrationService;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowVerifiedMethodVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationOrchestrationDecisionVO;

@DisplayName("Fachada pública do orquestrador")
class AuthenticationOrchestrationFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");

  @Test
  void complete_shouldExposeReadyPrincipalAndKeepOpaqueContinuation() {
    AuthenticationOrchestrationService service = mock(AuthenticationOrchestrationService.class);
    AuthenticationOrchestrationFacadeImpl facade =
        new AuthenticationOrchestrationFacadeImpl(service);
    UUID correlationId = UUID.fromString("76910e57-7d0b-4368-b25c-21ad25cfa822");
    when(service.complete("opaque-reference", NOW)).thenReturn(
        new AuthenticationOrchestrationDecisionVO(
            br.com.rinos.app.backend.module.identity.enums
                .AuthenticationOrchestrationStatusEnum.READY,
            "opaque-reference",
            41L,
            "person@example.test",
            AuthenticationAssuranceEnum.MULTI_FACTOR,
            Set.of(),
            List.of(
                new AuthenticationFlowVerifiedMethodVO(
                    AuthenticationMethodEnum.PASSWORD, NOW, null),
                new AuthenticationFlowVerifiedMethodVO(
                    AuthenticationMethodEnum.TOTP, NOW, null)),
            Set.of(),
            true,
            NOW.plusSeconds(300),
            correlationId));

    AuthenticationOrchestrationResultVO result = facade.complete("opaque-reference", NOW);

    assertThat(result.status()).isEqualTo(AuthenticationOrchestrationStatusEnum.READY);
    assertThat(result.continuationReference()).isEqualTo("opaque-reference");
    assertThat(result.principal().userId()).isEqualTo(41L);
    assertThat(result.principal().email()).isEqualTo("person@example.test");
    assertThat(result.verifiedMethods()).hasSize(2);
    assertThat(result.toString()).contains("continuationReference=REDACTED")
        .doesNotContain("opaque-reference");
  }
}
