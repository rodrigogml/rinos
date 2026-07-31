package br.com.rinos.app.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Define a origem pública canônica usada em referências externas da aplicação.
 *
 * @param publicBaseUrl origem absoluta sem path, query ou fragment
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@ConfigurationProperties("rinos.application")
public record ApplicationPropertiesConfig(
    @DefaultValue("http://localhost:7070") URI publicBaseUrl) {

  /**
   * Rejeita origens ambíguas ou derivadas implicitamente da requisição.
   */
  public ApplicationPropertiesConfig {
    if (publicBaseUrl == null || !publicBaseUrl.isAbsolute()) {
      throw new IllegalArgumentException("publicBaseUrl deve ser uma URI absoluta.");
    }
    String scheme = publicBaseUrl.getScheme();
    if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
      throw new IllegalArgumentException("publicBaseUrl deve usar HTTP ou HTTPS.");
    }
    if (publicBaseUrl.getHost() == null
        || (publicBaseUrl.getPath() != null && !publicBaseUrl.getPath().isEmpty())
        || publicBaseUrl.getQuery() != null
        || publicBaseUrl.getFragment() != null
        || publicBaseUrl.getUserInfo() != null) {
      throw new IllegalArgumentException(
          "publicBaseUrl deve conter somente scheme, host e porta opcional.");
    }
  }
}
