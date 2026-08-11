package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig.GoogleConfig;
import br.eng.rodrigogml.rfw.authentication.dto.RFWExternalIdentityRequestDTO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAccessStatusEnum;
import br.eng.rodrigogml.rfw.authentication.google.RFWGoogleIdentityProvider;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationOutcomeVO;

/**
 * Valida as fronteiras do provider Google da RFW exigidas pela aplicação hospedeira.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
@DisplayName("Fronteira RFW do provider Google")
class RFWGoogleIdentityProviderBoundaryIT {

  /**
   * Comprova que uma falha do JWKS é indisponibilidade recuperável e não publica identidade.
   *
   * @throws Exception quando a infraestrutura criptográfica local não pode ser preparada
   */
  @Test
  void authenticate_shouldRemainUnavailable_whenDiscoveryWorksButJwksFails()
      throws Exception {
    RSAKey signingKey = new RSAKeyGenerator(2048).keyID("unavailable-key").generate();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    String issuer = "http://127.0.0.1:" + server.getAddress().getPort();
    server.createContext("/.well-known/openid-configuration", exchange -> respond(
        exchange,
        200,
        """
            {"issuer":"%s","jwks_uri":"%s/jwks",
             "id_token_signing_alg_values_supported":["RS256"]}
            """.formatted(issuer, issuer)));
    server.createContext("/jwks", exchange -> respond(exchange, 503, "{}"));
    server.start();
    try {
      RFWGoogleIdentityProvider provider = new RFWGoogleIdentityProvider(
          new GoogleConfig(
              true,
              "client",
              issuer,
              Duration.ofSeconds(2),
              Duration.ofSeconds(60)),
          identity -> CompletableFuture.completedFuture(
              RFWAuthenticationOutcomeVO.completed("must-not-resolve")));

      RFWAuthenticationOutcomeVO outcome = provider.authenticate(
          new RFWExternalIdentityRequestDTO(
              "google",
              signedToken(signingKey, issuer),
              "expected-nonce"))
          .toCompletableFuture()
          .join();

      assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
      assertThat(outcome.error().messageKey())
          .isEqualTo("ui.access.error.externalIdentityUnavailable");
      assertThat(outcome.authentication()).isNull();
    } finally {
      server.stop(0);
    }
  }

  private static String signedToken(RSAKey signingKey, String issuer)
      throws JOSEException {
    Instant now = Instant.now();
    SignedJWT jwt = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.RS256)
            .type(JOSEObjectType.JWT)
            .keyID(signingKey.getKeyID())
            .build(),
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience("client")
            .subject("subject")
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(Duration.ofMinutes(5))))
            .claim("nonce", "expected-nonce")
            .build());
    jwt.sign(new RSASSASigner(signingKey));
    return jwt.serialize();
  }

  private static void respond(HttpExchange exchange, int status, String body)
      throws IOException {
    byte[] payload = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, payload.length);
    exchange.getResponseBody().write(payload);
    exchange.close();
  }
}
