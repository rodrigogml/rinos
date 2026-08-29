package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import br.com.rinos.app.api.vo.RinosUserPrincipalVO;
import br.com.rinos.app.api.module.account.vo.AccountCreationContext;

@DisplayName("Contexto Spring da criação de conta")
class SpringAccountCreationContextFacadeImplTest {

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void current_shouldDeriveIdentitySessionAndTrustedOrigin() {
    RFWRemoteAddressProviderAdapter remoteAddress = mock(RFWRemoteAddressProviderAdapter.class);
    MockHttpServletRequest request = new MockHttpServletRequest();
    RFWAuthenticatedPrincipalAdapter principal = new RFWAuthenticatedPrincipalAdapter(
        new RinosUserPrincipalVO(41L, "person@example.test"),
        "c3ef7a78-0bb6-4577-8d1d-6f6161047d11");
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    when(remoteAddress.resolve(request)).thenReturn("203.0.113.10");
    SpringAccountCreationContextFacadeImpl facade =
        new SpringAccountCreationContextFacadeImpl(remoteAddress);

    Optional<AccountCreationContext> context = facade.current();

    assertThat(context).hasValueSatisfying(value -> {
      assertThat(value.userId()).isEqualTo(41L);
      assertThat(value.sessionReference()).isEqualTo(principal.sessionReference());
      assertThat(value.canonicalOrigin()).isEqualTo("203.0.113.10");
    });
  }

  @Test
  void current_shouldReturnEmpty_whenSpringSessionIsAbsent() {
    SpringAccountCreationContextFacadeImpl facade =
        new SpringAccountCreationContextFacadeImpl(mock(RFWRemoteAddressProviderAdapter.class));

    assertThat(facade.current()).isEmpty();
  }
}
