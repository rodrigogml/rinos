package br.com.rinos.app.backend.module.identity.service;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

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
  private static final String PASSWORD_RESET_STEP = "password-reset";
  private static final String REGISTRATION_CANCELLATION_PATH = "/cancel-registration";
  private static final String MEMBERSHIP_INVITATION_PATH = "/accept-invitation";

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

  /**
   * Cria a URL absoluta que abre diretamente a redefinição de senha no RFW.
   *
   * @param token prova opaca de uso único
   * @return URL absoluta e codificada
   */
  public URI passwordResetUri(String token) {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("token must not be blank");
    }
    return UriComponentsBuilder.fromUri(publicBaseUrl)
        .path(LOGIN_PATH)
        .queryParam("step", PASSWORD_RESET_STEP)
        .queryParam("proof", token)
        .build()
        .encode()
        .toUri();
  }

  /** Cria a URL absoluta de aceite vinculando identificador público e prova opaca. */
  public URI membershipInvitationUri(UUID invitationId, String proof) {
    if (invitationId == null || proof == null || proof.isBlank()) {
      throw new IllegalArgumentException("membership invitation parameters are invalid");
    }
    return UriComponentsBuilder.fromUri(publicBaseUrl)
        .path(MEMBERSHIP_INVITATION_PATH)
        .queryParam("invitation", invitationId)
        .queryParam("proof", proof)
        .build()
        .encode()
        .toUri();
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
