package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import br.com.rinos.app.api.facade.HumanVerificationPolicyFacade;
import br.com.rinos.app.api.vo.RemoteOriginRequestVO;

@DisplayName("Adapter RFW da origem remota")
class RFWRemoteAddressProviderAdapterTest {

  @Test
  void resolve_shouldForwardCompleteHeaderChainsToPublicFacade() {
    HumanVerificationPolicyFacade facade = mock(HumanVerificationPolicyFacade.class);
    RFWRemoteAddressProviderAdapter adapter = new RFWRemoteAddressProviderAdapter(facade);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("10.0.0.5");
    request.addHeader("Forwarded", "for=203.0.113.10");
    request.addHeader("Forwarded", "for=10.0.0.4");
    request.addHeader("X-Forwarded-For", "203.0.113.10,10.0.0.4");
    RemoteOriginRequestVO expected = new RemoteOriginRequestVO(
        "10.0.0.5",
        "for=203.0.113.10,for=10.0.0.4",
        "203.0.113.10,10.0.0.4");
    when(facade.resolveTrustedOrigin(expected)).thenReturn("203.0.113.10");

    String result = adapter.resolve(request);

    assertThat(result).isEqualTo("203.0.113.10");
    verify(facade).resolveTrustedOrigin(expected);
  }
}
