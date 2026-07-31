package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Normalização do endereço de origem")
class OriginAddressServiceTest {

  private final OriginAddressService service = new OriginAddressService();

  @Test
  void normalize_shouldProduceFourBytes_whenLiteralIsIpv4() {
    assertThat(service.normalize("192.0.2.10").getAddress())
        .containsExactly((byte) 192, 0, 2, 10);
  }

  @Test
  void normalize_shouldCanonicalizeEquivalentIpv6Literals() {
    assertThat(service.normalize("2001:db8::1").getAddress())
        .containsExactly(service.normalize("2001:0db8:0:0:0:0:0:1").getAddress());
  }

  @Test
  void toCanonicalLiteral_shouldReturnProtocolSafeLiteral_whenAddressIsValidated() {
    assertThat(service.toCanonicalLiteral(service.normalize("192.0.2.10")))
        .isEqualTo("192.0.2.10");
    assertThat(service.toCanonicalLiteral(service.normalize("2001:0db8:0:0:0:0:0:1")))
        .isEqualTo("2001:db8:0:0:0:0:0:1");
  }

  @Test
  void normalize_shouldRejectHostnamesZonesAndAmbiguousIpv4() {
    assertThatThrownBy(() -> service.normalize("example.com"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.normalize("fe80::1%1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.normalize("192.168.001.1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void toString_shouldNotExposeAddress() {
    assertThat(service.normalize("192.0.2.10").toString())
        .doesNotContain("192.0.2.10");
  }
}
