package br.com.rinos.app.backend.module.identity.service;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import br.com.rinos.app.backend.module.identity.enums.PwnedPasswordStatusEnum;
import br.com.rinos.app.config.PwnedPasswordsPropertiesConfig;

/**
 * Consulta a Pwned Passwords Range API enviando somente cinco caracteres do SHA-1.
 *
 * <p>Senha, hash completo, sufixo e payload recebido permanecem efêmeros e não são registrados.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
public class PwnedPasswordsService {

  private static final int PREFIX_LENGTH = 5;
  private static final int MAXIMUM_RESPONSE_LENGTH = 2_000_000;
  private static final int MAXIMUM_RESPONSE_LINES = 10_000;
  private static final Pattern RESPONSE_LINE =
      Pattern.compile("[0-9A-Fa-f]{35}:[0-9]+");

  private final PwnedPasswordsPropertiesConfig config;
  private final RestClient restClient;

  /**
   * Cria o adapter com timeouts explícitos da instalação.
   *
   * @param config contrato de integração
   */
  @Autowired
  public PwnedPasswordsService(PwnedPasswordsPropertiesConfig config) {
    this(config, createRestClient(config));
  }

  /**
   * Cria o adapter com cliente controlado por teste local.
   *
   * @param config contrato de integração
   * @param restClient cliente HTTP
   */
  PwnedPasswordsService(
      PwnedPasswordsPropertiesConfig config,
      RestClient restClient) {
    this.config = Objects.requireNonNull(config, "config must not be null");
    this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
  }

  /**
   * Verifica a senha de forma fail-closed.
   *
   * @param password senha efêmera, mantida sob responsabilidade do chamador
   * @return estado seguro sem detalhes do hash
   */
  public PwnedPasswordStatusEnum check(char[] password) {
    Objects.requireNonNull(password, "password must not be null");
    HashParts hash = hash(password);
    try {
      String response = restClient.get()
          .uri(buildUri(hash.prefix()))
          .header(HttpHeaders.USER_AGENT, config.userAgent())
          .header("Add-Padding", "true")
          .retrieve()
          .body(String.class);
      return inspectResponse(response, hash.suffix());
    } catch (RestClientException | IllegalArgumentException exception) {
      return PwnedPasswordStatusEnum.UNAVAILABLE;
    }
  }

  private URI buildUri(String prefix) {
    return UriComponentsBuilder.fromUri(config.endpoint())
        .pathSegment(prefix)
        .build()
        .toUri();
  }

  private static PwnedPasswordStatusEnum inspectResponse(
      String response,
      String expectedSuffix) {
    if (response == null || response.length() > MAXIMUM_RESPONSE_LENGTH) {
      return PwnedPasswordStatusEnum.UNAVAILABLE;
    }
    String[] lines = response.split("\\R", -1);
    if (lines.length > MAXIMUM_RESPONSE_LINES) {
      return PwnedPasswordStatusEnum.UNAVAILABLE;
    }
    for (String line : lines) {
      if (line.isBlank()) {
        continue;
      }
      if (!RESPONSE_LINE.matcher(line).matches()) {
        return PwnedPasswordStatusEnum.UNAVAILABLE;
      }
      int separator = line.indexOf(':');
      if (line.regionMatches(true, 0, expectedSuffix, 0, expectedSuffix.length())) {
        try {
          if (Long.parseLong(line.substring(separator + 1)) > 0) {
            return PwnedPasswordStatusEnum.COMPROMISED;
          }
        } catch (NumberFormatException exception) {
          return PwnedPasswordStatusEnum.UNAVAILABLE;
        }
      }
    }
    return PwnedPasswordStatusEnum.SAFE;
  }

  private static HashParts hash(char[] password) {
    ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
    byte[] passwordBytes = new byte[encoded.remaining()];
    encoded.get(passwordBytes);
    try {
      byte[] digest = MessageDigest.getInstance("SHA-1").digest(passwordBytes);
      try {
        String hexadecimal = HexFormat.of().withUpperCase().formatHex(digest);
        return new HashParts(
            hexadecimal.substring(0, PREFIX_LENGTH),
            hexadecimal.substring(PREFIX_LENGTH));
      } finally {
        Arrays.fill(digest, (byte) 0);
      }
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-1 must be available in the Java runtime", exception);
    } finally {
      Arrays.fill(passwordBytes, (byte) 0);
      if (encoded.hasArray()) {
        Arrays.fill(encoded.array(), (byte) 0);
      }
    }
  }

  private static RestClient createRestClient(PwnedPasswordsPropertiesConfig config) {
    Objects.requireNonNull(config, "config must not be null");
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(config.connectTimeout());
    requestFactory.setReadTimeout(config.readTimeout());
    return RestClient.builder().requestFactory(requestFactory).build();
  }

  private record HashParts(String prefix, String suffix) {
  }
}
