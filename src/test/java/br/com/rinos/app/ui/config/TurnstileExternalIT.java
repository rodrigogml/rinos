package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig.TurnstileConfig;
import br.eng.rodrigogml.rfw.authentication.turnstile.RFWTurnstileVerificationService;
import br.eng.rodrigogml.rfw.authentication.vo.RFWHumanVerificationResultVO;

/**
 * Smoke test opt-in do adapter RFW contra o Siteverify externo da Cloudflare.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-02
 */
@DisplayName("Integração externa com o Siteverify do Turnstile")
class TurnstileExternalIT {

  private static final URI SITEVERIFY_ENDPOINT =
      URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify");
  private static final String ALWAYS_PASS_TEST_SECRET =
      "1x0000000000000000000000000000000AA";
  private static final String DUMMY_TOKEN = "XXXX.DUMMY.TOKEN.XXXX";

  /**
   * Comprova o transporte HTTPS e a desserialização do adapter com as credenciais públicas
   * que a Cloudflare destina a testes automatizados.
   */
  @Test
  @EnabledIfSystemProperty(named = "rinos.turnstile.gate.enabled", matches = "true")
  void verify_shouldAcceptOfficialDummyToken_onRealSiteverifyEndpoint() {
    TurnstileConfig config = new TurnstileConfig(
        true,
        "1x00000000000000000000AA",
        ALWAYS_PASS_TEST_SECRET,
        List.of(),
        SITEVERIFY_ENDPOINT,
        Duration.ofSeconds(10));
    RFWTurnstileVerificationService service = new RFWTurnstileVerificationService(config);

    RFWHumanVerificationResultVO result = service.verify(DUMMY_TOKEN, "192.0.2.10")
        .toCompletableFuture()
        .join();

    assertThat(result.valid()).isTrue();
    assertThat(result.failure()).isNull();
    assertThat(result.errorCodes()).isEmpty();
  }
}
