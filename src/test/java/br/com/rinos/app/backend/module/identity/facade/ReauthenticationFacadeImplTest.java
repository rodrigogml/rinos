package br.com.rinos.app.backend.module.identity.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.dto.ReauthenticationBeginRequestDTO;
import br.com.rinos.app.api.dto.ReauthenticationVerificationRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.enums.ReauthenticationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.ReauthenticationOperationEnum;
import br.com.rinos.app.backend.module.identity.service.ReauthenticationService;
import br.com.rinos.app.backend.module.identity.vo.ReauthenticationDecisionVO;

@DisplayName("Facade pública da reautenticação")
class ReauthenticationFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
  private static final UUID SESSION = UUID.fromString("286ba2c3-baea-46d4-942f-e94684cd25ea");
  private ReauthenticationService service;
  private ReauthenticationFacadeImpl facade;

  @BeforeEach
  void setUp() {
    service = mock(ReauthenticationService.class);
    facade = new ReauthenticationFacadeImpl(service);
  }

  @Test
  void begin_shouldMapCatalogOperationAndChallenge() {
    Instant expiresAt = NOW.plusSeconds(300);
    when(service.begin(
        41L, SESSION, ReauthenticationOperationEnum.CHANGE_PASSWORD, NOW))
        .thenReturn(new ReauthenticationDecisionVO(
            br.com.rinos.app.backend.module.identity.enums.ReauthenticationStatusEnum
                .CHALLENGE_REQUIRED,
            "challenge-reference",
            "identity.reauthentication.operation.change-password",
            expiresAt,
            Set.of(br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.PASSWORD)));

    var result = facade.begin(new ReauthenticationBeginRequestDTO(
        41L, SESSION.toString(), "change-password", NOW));

    assertThat(result.status()).isEqualTo(ReauthenticationStatusEnum.CHALLENGE_REQUIRED);
    assertThat(result.challengeReference()).isEqualTo("challenge-reference");
    assertThat(result.allowedMethods()).containsExactly(AuthenticationMethodEnum.PASSWORD);
  }

  @Test
  void verify_shouldMapProofWithoutExposingBackendTypes() {
    when(service.complete(
        41L,
        SESSION,
        "challenge-reference",
        br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.PASSWORD,
        "CorrectPassword1!",
        NOW))
        .thenReturn(ReauthenticationDecisionVO.terminal(
            br.com.rinos.app.backend.module.identity.enums.ReauthenticationStatusEnum.COMPLETED));

    var result = facade.verify(new ReauthenticationVerificationRequestDTO(
        41L,
        SESSION.toString(),
        "challenge-reference",
        AuthenticationMethodEnum.PASSWORD,
        "CorrectPassword1!",
        NOW));

    assertThat(result.status()).isEqualTo(ReauthenticationStatusEnum.COMPLETED);
    verify(service).complete(
        41L,
        SESSION,
        "challenge-reference",
        br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.PASSWORD,
        "CorrectPassword1!",
        NOW);
  }

  @Test
  void begin_shouldDenyMalformedSessionWithoutCallingService() {
    var result = facade.begin(new ReauthenticationBeginRequestDTO(
        41L, "not-a-uuid", "change-password", NOW));

    assertThat(result.status()).isEqualTo(ReauthenticationStatusEnum.ACCESS_DENIED);
    verifyNoInteractions(service);
  }
}
