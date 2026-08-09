package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.identity.vo.ProtectedAuthenticationKeyVO;
import br.com.rinos.app.config.AuthenticationKeyringPropertiesConfig;

@DisplayName("MAC versionado do keyring de autenticação")
class AuthenticationKeyringMacServiceTest {

  private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

  @Test
  void protect_shouldBeDeterministicAndSeparateSemanticDomains() {
    AuthenticationKeyringMacService service = new AuthenticationKeyringMacService(
        new AuthenticationKeyringService(
            new AuthenticationKeyringPropertiesConfig(true, "v1", Map.of("v1", KEY))));
    byte[] value = "same-value".getBytes(StandardCharsets.UTF_8);

    ProtectedAuthenticationKeyVO first = service.protect("sign-in/identifier", value);
    ProtectedAuthenticationKeyVO repeated = service.protect("sign-in/identifier", value);
    ProtectedAuthenticationKeyVO otherDomain = service.protect("sign-in/origin", value);

    assertThat(first.digest()).containsExactly(repeated.digest());
    assertThat(first.digest()).isNotEqualTo(otherDomain.digest());
    assertThat(first.keyVersion()).isEqualTo("v1");
    assertThat(first.toString()).doesNotContain(Base64.getEncoder().encodeToString(first.digest()));
  }

  @Test
  void protect_shouldFailClosedWhenKeyringIsDisabled() {
    AuthenticationKeyringMacService service = new AuthenticationKeyringMacService(
        new AuthenticationKeyringService(
            new AuthenticationKeyringPropertiesConfig(false, "", Map.of())));

    assertThatThrownBy(() -> service.protect("sign-in/origin", new byte[] {1, 2, 3, 4}))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not enabled");
  }
}
