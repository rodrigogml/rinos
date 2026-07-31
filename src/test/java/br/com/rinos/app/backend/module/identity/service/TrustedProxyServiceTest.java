package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.config.ProxyPropertiesConfig;

@DisplayName("Resolução da origem por proxy confiável")
class TrustedProxyServiceTest {

  @Test
  void resolve_shouldUseImmediatePeer_whenNoProxyIsTrusted() {
    TrustedProxyService service = service(List.of());

    assertThat(service.resolve("198.51.100.4", null, null).getAddress())
        .containsExactly((byte) 198, 51, 100, 4);
  }

  @Test
  void resolve_shouldRejectForwardedHeader_fromUntrustedPeer() {
    TrustedProxyService service = service(List.of("10.0.0.0/8"));

    assertThatThrownBy(() -> service.resolve(
        "198.51.100.4",
        null,
        "203.0.113.9"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resolve_shouldChooseRightmostUntrustedAddress_andIgnoreSpoofedPrefix() {
    TrustedProxyService service = service(List.of("10.0.0.0/8"));

    assertThat(service.resolve(
        "10.0.0.1",
        null,
        "203.0.113.9, 198.51.100.4, 10.0.0.2").getAddress())
        .containsExactly((byte) 198, 51, 100, 4);
  }

  @Test
  void resolve_shouldAcceptMatchingStandardAndLegacyHeaders() {
    TrustedProxyService service = service(List.of("2001:db8:1::/48"));

    assertThat(service.resolve(
        "2001:db8:1::1",
        "for=\"[2001:db8:2::7]:443\";proto=https",
        "2001:db8:2::7").getAddress())
        .hasSize(16);
  }

  @Test
  void resolve_shouldRejectContradictoryHeadersOrMissingChain() {
    TrustedProxyService service = service(List.of("10.0.0.0/8"));

    assertThatThrownBy(() -> service.resolve(
        "10.0.0.1",
        "for=198.51.100.4",
        "203.0.113.9"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.resolve("10.0.0.1", null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructor_shouldRejectInvalidCidr_withoutResolvingHostname() {
    assertThatThrownBy(() -> service(List.of("proxy.example.com")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service(List.of("10.0.0.0/33")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static TrustedProxyService service(List<String> trustedProxies) {
    return new TrustedProxyService(
        new ProxyPropertiesConfig(trustedProxies),
        new OriginAddressService());
  }
}
