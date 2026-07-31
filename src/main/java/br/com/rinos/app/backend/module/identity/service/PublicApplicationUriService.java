package br.com.rinos.app.backend.module.identity.service;

import java.net.URI;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import br.com.rinos.app.config.ApplicationPropertiesConfig;

/**
 * Monta referências externas exclusivamente sobre a origem pública canônica configurada.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
public class PublicApplicationUriService {

  private static final String LOGIN_PATH = "/login";
  private static final String ACTIVATION_STEP = "activation";
  private static final String REGISTRATION_CANCELLATION_PATH = "/cancel-registration";

  private final URI publicBaseUrl;

  /**
   * Captura a origem imutável validada durante a inicialização.
   *
   * @param properties configuração exclusiva da aplicação
   */
  public PublicApplicationUriService(ApplicationPropertiesConfig properties) {
    publicBaseUrl = Objects.requireNonNull(
        properties,
        "properties must not be null").publicBaseUrl();
  }

  /**
   * Cria a URL absoluta da comprovação sem consultar cabeçalhos da requisição.
   *
   * @param token prova opaca de uso único
   * @return URL absoluta e codificada
   */
  public URI activationUri(String token) {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("token must not be blank");
    }
    return UriComponentsBuilder.fromUri(publicBaseUrl)
        .path(LOGIN_PATH)
        .queryParam("step", ACTIVATION_STEP)
        .queryParam("proof", token)
        .build()
        .encode()
        .toUri();
  }

  /**
   * Cria a URL absoluta de confirmação do cancelamento sobre a mesma origem pública.
   *
   * @param token prova opaca de uso único
   * @return URL absoluta e codificada
   */
  public URI registrationCancellationUri(String token) {
    return verificationUri(REGISTRATION_CANCELLATION_PATH, token);
  }

  private URI verificationUri(String path, String token) {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("token must not be blank");
    }
    return UriComponentsBuilder.fromUri(publicBaseUrl)
        .path(path)
        .queryParam("token", token)
        .build()
        .encode()
        .toUri();
  }
}
