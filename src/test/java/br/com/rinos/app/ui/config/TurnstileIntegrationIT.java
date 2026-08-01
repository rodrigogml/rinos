package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig.TurnstileConfig;
import br.eng.rodrigogml.rfw.authentication.enums.RFWHumanVerificationFailureEnum;
import br.eng.rodrigogml.rfw.authentication.enums.RFWHumanVerificationOperationEnum;
import br.eng.rodrigogml.rfw.authentication.turnstile.RFWTurnstileVerificationService;
import br.eng.rodrigogml.rfw.authentication.vo.RFWHumanVerificationRequestVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWHumanVerificationResultVO;

@DisplayName("Integração local com o Siteverify do Turnstile")
class TurnstileIntegrationIT {

  private HttpServer server;
  private final AtomicReference<Response> response = new AtomicReference<>();
  private final AtomicReference<String> requestBody = new AtomicReference<>();

  @BeforeEach
  void beforeEach() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/siteverify", this::handle);
    server.start();
  }

  @AfterEach
  void afterEach() {
    server.stop(0);
  }

  @Test
  void verify_shouldAcceptMatchingContextAndSendAttemptIdempotencyKey() {
    response.set(Response.ok("""
        {"success":true,"hostname":"rinos.test","action":"registration",
         "challenge_ts":"2026-07-29T12:00:00Z"}
        """));
    RFWTurnstileVerificationService service = service(Duration.ofSeconds(1));
    RFWHumanVerificationRequestVO request = request("token-ok", "attempt-1");

    RFWHumanVerificationResultVO result = service.verify(request).toCompletableFuture().join();

    assertThat(result.valid()).isTrue();
    assertThat(requestBody.get())
        .contains("remoteip=203.0.113.10")
        .contains("idempotency_key=attempt-1");
  }

  @Test
  void verify_shouldRejectReplayReportedBySiteverify() {
    AtomicInteger attempts = new AtomicInteger();
    server.removeContext("/siteverify");
    server.createContext("/siteverify", exchange -> {
      requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      String body = attempts.getAndIncrement() == 0
          ? """
              {"success":true,"hostname":"rinos.test","action":"registration",
               "challenge_ts":"2026-07-29T12:00:00Z"}
              """
          : """
              {"success":false,"error-codes":["timeout-or-duplicate"]}
              """;
      write(exchange, body);
    });
    RFWTurnstileVerificationService service = service(Duration.ofSeconds(1));

    RFWHumanVerificationResultVO first = service.verify(request("token-replay", "attempt-1"))
        .toCompletableFuture().join();
    RFWHumanVerificationResultVO replay = service.verify(request("token-replay", "attempt-2"))
        .toCompletableFuture().join();

    assertThat(first.valid()).isTrue();
    assertThat(replay.valid()).isFalse();
    assertThat(replay.failure()).isEqualTo(RFWHumanVerificationFailureEnum.INVALID_PROOF);
  }

  @Test
  void verify_shouldRejectHostnameAndActionMismatchesSeparately() {
    RFWTurnstileVerificationService service = service(Duration.ofSeconds(1));
    response.set(Response.ok("""
        {"success":true,"hostname":"attacker.test","action":"registration",
         "challenge_ts":"2026-07-29T12:00:00Z"}
        """));

    RFWHumanVerificationResultVO hostname = service.verify(request("token-host", "attempt-host"))
        .toCompletableFuture().join();
    response.set(Response.ok("""
        {"success":true,"hostname":"rinos.test","action":"sign-in",
         "challenge_ts":"2026-07-29T12:00:00Z"}
        """));
    RFWHumanVerificationResultVO action = service.verify(request("token-action", "attempt-action"))
        .toCompletableFuture().join();

    assertThat(hostname.failure()).isEqualTo(RFWHumanVerificationFailureEnum.HOSTNAME_MISMATCH);
    assertThat(action.failure()).isEqualTo(RFWHumanVerificationFailureEnum.ACTION_MISMATCH);
  }

  @Test
  void verify_shouldFailClosed_whenSiteverifyTimesOut() {
    response.set(new Response("""
        {"success":true,"hostname":"rinos.test","action":"registration"}
        """, Duration.ofMillis(250)));
    RFWTurnstileVerificationService service = service(Duration.ofMillis(25));

    assertThatThrownBy(() -> service.verify(request("token-timeout", "attempt-timeout"))
        .toCompletableFuture().join())
        .isInstanceOf(CompletionException.class)
        .hasRootCauseInstanceOf(SocketTimeoutException.class);
  }

  private RFWTurnstileVerificationService service(Duration timeout) {
    TurnstileConfig config = new TurnstileConfig(
        true,
        "site-key",
        "secret-key",
        List.of("rinos.test"),
        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/siteverify"),
        timeout);
    return new RFWTurnstileVerificationService(config);
  }

  private RFWHumanVerificationRequestVO request(String token, String idempotencyKey) {
    return new RFWHumanVerificationRequestVO(
        token,
        "203.0.113.10",
        RFWHumanVerificationOperationEnum.REGISTRATION,
        "registration",
        idempotencyKey);
  }

  private void handle(HttpExchange exchange) throws IOException {
    requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    Response current = response.get();
    if (!current.delay().isZero()) {
      try {
        Thread.sleep(current.delay());
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        exchange.close();
        return;
      }
    }
    write(exchange, current.body());
  }

  private static void write(HttpExchange exchange, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  private record Response(String body, Duration delay) {

    private static Response ok(String body) {
      return new Response(body, Duration.ZERO);
    }
  }
}
