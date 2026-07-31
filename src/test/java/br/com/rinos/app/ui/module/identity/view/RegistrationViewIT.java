package br.com.rinos.app.ui.module.identity.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Valida a inicialização HTTP real da rota Vaadin sobre o harness isolado da superfície.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@SpringBootTest(
    classes = RegistrationUiTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = "vaadin.launch-browser=false")
@DisplayName("Integração Vaadin da rota de cadastro")
class RegistrationViewIT {

  @LocalServerPort
  private int port;

  /**
   * Confirma que o bootstrap Vaadin da rota canônica responde sem banco ou formulário paralelo.
   *
   * @throws Exception quando a requisição local não puder ser concluída
   */
  @Test
  void loginRoute_shouldReturnVaadinBootstrap_whenUiHarnessStarts() throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("http://127.0.0.1:" + port + "/login"))
        .GET()
        .build();

    HttpClient client = HttpClient.newHttpClient();
    long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
    HttpResponse<String> response;
    do {
      response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (!response.body().contains("Building front-end development bundle")) {
        break;
      }
      Thread.sleep(250);
    } while (System.nanoTime() < deadline);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .doesNotContain("Building front-end development bundle")
        .contains("<body")
        .containsAnyOf("VAADIN", "Vaadin");
  }
}
