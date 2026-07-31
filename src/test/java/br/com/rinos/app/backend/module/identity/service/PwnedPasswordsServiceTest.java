package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import br.com.rinos.app.backend.module.identity.enums.PwnedPasswordStatusEnum;
import br.com.rinos.app.config.PwnedPasswordsPropertiesConfig;

@DisplayName("Consulta k-anônima de senhas comprometidas")
class PwnedPasswordsServiceTest {

  private static final String PASSWORD = "Unique-Password-7!";

  @Test
  void check_shouldSendOnlyPrefixWithPadding_andDetectCompromisedSuffix() throws Exception {
    String hash = sha1(PASSWORD);
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo("https://hibp.example.test/range/" + hash.substring(0, 5)))
        .andExpect(header("Add-Padding", "true"))
        .andExpect(header("User-Agent", "Rinos-Test/1"))
        .andRespond(withSuccess(
            hash.substring(5) + ":42\r\n" + "0".repeat(35) + ":0\r\n",
            MediaType.TEXT_PLAIN));
    PwnedPasswordsService service = new PwnedPasswordsService(config(), builder.build());

    assertThat(service.check(PASSWORD.toCharArray()))
        .isEqualTo(PwnedPasswordStatusEnum.COMPROMISED);
    server.verify();
  }

  @Test
  void check_shouldAcceptPassword_whenSuffixIsAbsent() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo(org.hamcrest.Matchers.containsString("/range/")))
        .andRespond(withSuccess("0".repeat(35) + ":0\r\n", MediaType.TEXT_PLAIN));
    PwnedPasswordsService service = new PwnedPasswordsService(config(), builder.build());

    assertThat(service.check(PASSWORD.toCharArray()))
        .isEqualTo(PwnedPasswordStatusEnum.SAFE);
    server.verify();
  }

  @Test
  void check_shouldFailClosed_whenPayloadIsMalformedOrServerFails() {
    RestClient.Builder malformedBuilder = RestClient.builder();
    MockRestServiceServer malformedServer =
        MockRestServiceServer.bindTo(malformedBuilder).build();
    malformedServer.expect(requestTo(org.hamcrest.Matchers.containsString("/range/")))
        .andRespond(withSuccess("not-a-range-response", MediaType.TEXT_PLAIN));
    PwnedPasswordsService malformedService =
        new PwnedPasswordsService(config(), malformedBuilder.build());
    assertThat(malformedService.check(PASSWORD.toCharArray()))
        .isEqualTo(PwnedPasswordStatusEnum.UNAVAILABLE);

    RestClient.Builder failedBuilder = RestClient.builder();
    MockRestServiceServer failedServer = MockRestServiceServer.bindTo(failedBuilder).build();
    failedServer.expect(requestTo(org.hamcrest.Matchers.containsString("/range/")))
        .andRespond(withServerError());
    PwnedPasswordsService failedService =
        new PwnedPasswordsService(config(), failedBuilder.build());
    assertThat(failedService.check(PASSWORD.toCharArray()))
        .isEqualTo(PwnedPasswordStatusEnum.UNAVAILABLE);
  }

  private static PwnedPasswordsPropertiesConfig config() {
    return new PwnedPasswordsPropertiesConfig(
        URI.create("https://hibp.example.test/range/"),
        "Rinos-Test/1",
        Duration.ofSeconds(1),
        Duration.ofSeconds(1));
  }

  private static String sha1(String value) throws Exception {
    return HexFormat.of().withUpperCase().formatHex(
        MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8)));
  }
}
