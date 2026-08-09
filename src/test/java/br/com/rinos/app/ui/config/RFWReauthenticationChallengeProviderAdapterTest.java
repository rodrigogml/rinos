package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import br.com.rinos.app.api.dto.ReauthenticationBeginRequestDTO;
import br.com.rinos.app.api.dto.ReauthenticationVerificationRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.enums.ReauthenticationStatusEnum;
import br.com.rinos.app.api.facade.ReauthenticationFacade;
import br.com.rinos.app.api.vo.ReauthenticationResultVO;
import br.com.rinos.app.api.vo.RinosUserPrincipalVO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWReauthenticationBeginRequestDTO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWReauthenticationVerificationRequestDTO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAuthenticationMethodEnum;
import br.eng.rodrigogml.rfw.authentication.enums.RFWReauthenticationStatusEnum;

@DisplayName("Provider RFW da reautenticação vinculada à sessão")
class RFWReauthenticationChallengeProviderAdapterTest {

  private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
  private static final String SESSION = "286ba2c3-baea-46d4-942f-e94684cd25ea";
  private ReauthenticationFacade facade;
  private RFWReauthenticationChallengeProviderAdapter adapter;

  @BeforeEach
  void setUp() {
    facade = mock(ReauthenticationFacade.class);
    adapter = new RFWReauthenticationChallengeProviderAdapter(
        facade, Clock.fixed(NOW, ZoneOffset.UTC));
    var principal = new RFWAuthenticatedPrincipalAdapter(
        new RinosUserPrincipalVO(41L, "person@example.test"), SESSION);
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void begin_shouldBindChallengeToCurrentPrincipalAndSession() {
    when(facade.begin(any())).thenReturn(new ReauthenticationResultVO(
        ReauthenticationStatusEnum.CHALLENGE_REQUIRED,
        "challenge-reference",
        "identity.reauthentication.operation.change-password",
        NOW.plusSeconds(300),
        Set.of(AuthenticationMethodEnum.PASSWORD)));

    var outcome = adapter.begin(new RFWReauthenticationBeginRequestDTO("change-password"))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status()).isEqualTo(
        RFWReauthenticationStatusEnum.CHALLENGE_REQUIRED);
    assertThat(outcome.challenge().allowedMethods())
        .containsExactly(RFWAuthenticationMethodEnum.PASSWORD);
    ArgumentCaptor<ReauthenticationBeginRequestDTO> request =
        ArgumentCaptor.forClass(ReauthenticationBeginRequestDTO.class);
    verify(facade).begin(request.capture());
    assertThat(request.getValue().userId()).isEqualTo(41L);
    assertThat(request.getValue().sessionReference()).isEqualTo(SESSION);
    assertThat(request.getValue().operationId()).isEqualTo("change-password");
    assertThat(request.getValue().occurredAt()).isEqualTo(NOW);
  }

  @Test
  void verify_shouldForwardEphemeralProofWithoutCreatingAuthentication() {
    when(facade.verify(any())).thenReturn(new ReauthenticationResultVO(
        ReauthenticationStatusEnum.COMPLETED, null, null, null, Set.of()));

    var outcome = adapter.verify(new RFWReauthenticationVerificationRequestDTO(
        "challenge-reference",
        RFWAuthenticationMethodEnum.PASSWORD,
        "CorrectPassword1!"))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status()).isEqualTo(RFWReauthenticationStatusEnum.COMPLETED);
    assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities()).isEmpty();
    ArgumentCaptor<ReauthenticationVerificationRequestDTO> request =
        ArgumentCaptor.forClass(ReauthenticationVerificationRequestDTO.class);
    verify(facade).verify(request.capture());
    assertThat(request.getValue().userId()).isEqualTo(41L);
    assertThat(request.getValue().sessionReference()).isEqualTo(SESSION);
    assertThat(request.getValue().proof()).isEqualTo("CorrectPassword1!");
    assertThat(request.getValue().occurredAt()).isEqualTo(NOW);
  }

  @Test
  void begin_shouldDenyRequestWithoutAuthenticatedRinosPrincipal() {
    SecurityContextHolder.clearContext();

    var outcome = adapter.begin(new RFWReauthenticationBeginRequestDTO("change-password"))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status()).isEqualTo(RFWReauthenticationStatusEnum.ACCESS_DENIED);
    verifyNoInteractions(facade);
  }

  @Test
  void verify_shouldMapExpiredChallengeWithoutChangingAuthenticatedContext() {
    when(facade.verify(any())).thenReturn(new ReauthenticationResultVO(
        ReauthenticationStatusEnum.EXPIRED, null, null, null, Set.of()));

    var outcome = adapter.verify(new RFWReauthenticationVerificationRequestDTO(
        "challenge-reference",
        RFWAuthenticationMethodEnum.PASSWORD,
        "CorrectPassword1!"))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status()).isEqualTo(RFWReauthenticationStatusEnum.EXPIRED);
    assertThat(outcome.errorKey())
        .isEqualTo("ui.securitySettings.error.reauthenticationExpired");
    assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
  }

  @Test
  void verify_shouldMapStaleChallengeAsConflictWithoutChangingAuthenticatedContext() {
    when(facade.verify(any())).thenReturn(new ReauthenticationResultVO(
        ReauthenticationStatusEnum.CONFLICT, null, null, null, Set.of()));

    var outcome = adapter.verify(new RFWReauthenticationVerificationRequestDTO(
        "challenge-reference",
        RFWAuthenticationMethodEnum.PASSWORD,
        "CorrectPassword1!"))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status()).isEqualTo(RFWReauthenticationStatusEnum.CONFLICT);
    assertThat(outcome.errorKey())
        .isEqualTo("ui.securitySettings.error.reauthenticationConflict");
    assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
  }
}
