package br.com.rinos.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Única propriedade capaz de indicar a identidade candidata ao primeiro bootstrap global. */
@ConfigurationProperties("rinos.access.bootstrap")
public record AccessBootstrapPropertiesConfig(
    @DefaultValue("admin@rinos.com.br") String administratorEmail) {

  public AccessBootstrapPropertiesConfig {
    if (administratorEmail == null || administratorEmail.isBlank()
        || administratorEmail.length() > 320) {
      throw new IllegalArgumentException("O e-mail de bootstrap deve ser informado.");
    }
    administratorEmail = administratorEmail.strip();
  }
}
