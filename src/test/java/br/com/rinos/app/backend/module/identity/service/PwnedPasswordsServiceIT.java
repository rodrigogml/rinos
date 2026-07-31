package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import br.com.rinos.app.backend.module.identity.enums.PwnedPasswordStatusEnum;
import br.com.rinos.app.config.PwnedPasswordsPropertiesConfig;

@DisplayName("Timeout local da consulta Pwned Passwords")
class PwnedPasswordsServiceIT {

  /**
   * Comprova fail-closed contra um servidor local que excede o timeout de leitura.
   *
   * @throws Exception quando o servidor descartável não pode ser controlado
   */
  @Test
  void check_shouldFailClosed_whenLocalServerExceedsReadTimeout() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/range", exchange -> {
      try {
        Thread.sleep(200);
        exchange.sendResponseHeaders(200, 0);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      } finally {
        exchange.close();
      }
    });
    server.start();
    try {
      URI endpoint = URI.create(
          "http://127.0.0.1:" + server.getAddress().getPort() + "/range/");
      PwnedPasswordsService service = new PwnedPasswordsService(
          new PwnedPasswordsPropertiesConfig(
              endpoint,
              "Rinos-Test/1",
              Duration.ofMillis(100),
              Duration.ofMillis(20)));

      assertThat(service.check("Unique-Password-7!".toCharArray()))
          .isEqualTo(PwnedPasswordStatusEnum.UNAVAILABLE);
    } finally {
      server.stop(0);
    }
  }
}
