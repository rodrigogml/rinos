package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.config.ApplicationPropertiesConfig;

@DisplayName("Referências da origem pública canônica")
class PublicApplicationUriServiceTest {

  @Test
  void activationUri_shouldUseConfiguredOrigin_withoutRequestHeaders() {
    PublicApplicationUriService service = new PublicApplicationUriService(
        new ApplicationPropertiesConfig(URI.create("https://app.rinos.com.br")));

    URI result = service.activationUri("token with spaces");

    assertThat(result)
        .isEqualTo(URI.create(
            "https://app.rinos.com.br/login?step=activation&proof=token%20with%20spaces"));
  }

  @Test
  void activationUri_shouldKeepInternalDevelopmentPort_whenConfigured() {
    PublicApplicationUriService service = new PublicApplicationUriService(
        new ApplicationPropertiesConfig(URI.create("http://localhost:7070")));

    URI result = service.activationUri("opaque-token");

    assertThat(result)
        .isEqualTo(URI.create(
            "http://localhost:7070/login?step=activation&proof=opaque-token"));
  }

  @Test
  void registrationCancellationUri_shouldUseSameConfiguredOrigin() {
    PublicApplicationUriService service = new PublicApplicationUriService(
        new ApplicationPropertiesConfig(URI.create("https://app.rinos.com.br")));

    URI result = service.registrationCancellationUri("opaque-token");

    assertThat(result).isEqualTo(URI.create(
        "https://app.rinos.com.br/cancel-registration?token=opaque-token"));
  }
}
