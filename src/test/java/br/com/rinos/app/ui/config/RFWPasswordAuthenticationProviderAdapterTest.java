package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import br.com.rinos.app.api.dto.PasswordAuthenticationRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationOrchestrationStatusEnum;
import br.com.rinos.app.api.facade.PasswordAuthenticationFacade;
import br.com.rinos.app.api.vo.AuthenticationOrchestrationResultVO;
import br.com.rinos.app.api.vo.PasswordAuthenticationResultVO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWPasswordAuthenticationRequestDTO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAccessStatusEnum;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationOutcomeVO;
import br.eng.rodrigogml.rfw.ui.access.provider.RFWRemoteAddressProvider;

@DisplayName("Provider RFW do primeiro fator por senha")
class RFWPasswordAuthenticationProviderAdapterTest {

  @AfterEach
  void clearRequest() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void authenticate_shouldResolveTrustedOriginAndCreateCorrelatedCommand() {
    PasswordAuthenticationFacade facade = mock(PasswordAuthenticationFacade.class);
    when(facade.authenticate(any())).thenReturn(CompletableFuture.completedFuture(
        result(AuthenticationOrchestrationStatusEnum.REJECTED, Duration.ZERO)));
    RFWRemoteAddressProvider originProvider = request -> "198.51.100.12";
    RFWPasswordAuthenticationProviderAdapter adapter = new RFWPasswordAuthenticationProviderAdapter(
        facade, originProvider, new RFWAuthenticationOutcomeAdapter());
    bindRequest();

    RFWAuthenticationOutcomeVO outcome = adapter.authenticate(
        new RFWPasswordAuthenticationRequestDTO(
            "person@example.test", "Password1!", true, "validated-token"))
        .toCompletableFuture()
        .join();

    ArgumentCaptor<PasswordAuthenticationRequestDTO> captor =
        ArgumentCaptor.forClass(PasswordAuthenticationRequestDTO.class);
    verify(facade).authenticate(captor.capture());
    assertThat(captor.getValue().identifier()).isEqualTo("person@example.test");
    assertThat(captor.getValue().consumePassword()).containsExactly("Password1!".toCharArray());
    assertThat(captor.getValue().persistentLoginRequested()).isTrue();
    assertThat(captor.getValue().turnstileToken()).isEqualTo("validated-token");
    assertThat(captor.getValue().canonicalOrigin()).isEqualTo("198.51.100.12");
    assertThat(captor.getValue().correlationId()).isNotNull();
    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().messageKey()).isEqualTo("authentication.credentials.invalid");
  }

  @Test
  void map_shouldExposeRetryWithoutRevealingTriggeringDimension() {
    RFWPasswordAuthenticationProviderAdapter adapter = new RFWPasswordAuthenticationProviderAdapter(
        mock(PasswordAuthenticationFacade.class),
        request -> "198.51.100.12",
        new RFWAuthenticationOutcomeAdapter());

    RFWAuthenticationOutcomeVO outcome = adapter.map(
        result(AuthenticationOrchestrationStatusEnum.REJECTED, Duration.ofSeconds(4)));

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.RATE_LIMITED);
    assertThat(outcome.error().messageKey()).isEqualTo("authentication.sign-in.rate-limited");
    assertThat(outcome.error().retryAfter()).isEqualTo(Duration.ofSeconds(4));
    assertThat(outcome.error().arguments()).isEmpty();
    assertThat(outcome.error().fieldErrors()).isEmpty();
  }

  @Test
  void authenticate_shouldFailClosedWithoutCallingFacadeWhenOriginIsUnavailable() {
    PasswordAuthenticationFacade facade = mock(PasswordAuthenticationFacade.class);
    RFWPasswordAuthenticationProviderAdapter adapter = new RFWPasswordAuthenticationProviderAdapter(
        facade,
        request -> {
          throw new IllegalArgumentException("untrusted proxy chain");
        },
        new RFWAuthenticationOutcomeAdapter());
    bindRequest();

    RFWAuthenticationOutcomeVO outcome = adapter.authenticate(
        new RFWPasswordAuthenticationRequestDTO(
            "person@example.test", "Password1!", false, null))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().messageKey())
        .isEqualTo("authentication.temporarily-unavailable");
    verifyNoInteractions(facade);
  }

  private static PasswordAuthenticationResultVO result(
      AuthenticationOrchestrationStatusEnum status,
      Duration retryAfter) {
    return new PasswordAuthenticationResultVO(
        new AuthenticationOrchestrationResultVO(
            status,
            null,
            null,
            null,
            Set.of(),
            List.of(),
            Set.of(),
            false,
            null,
            null),
        false,
        retryAfter);
  }

  private static void bindRequest() {
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
        new MockHttpServletRequest(), new MockHttpServletResponse()));
  }
}
