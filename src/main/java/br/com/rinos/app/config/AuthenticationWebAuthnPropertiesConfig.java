package br.com.rinos.app.config;

import java.net.URI;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Identidade RP e origins permitidas do WebAuthn.
 *
 * @author Rodrigo Leitão
 */
@ConfigurationProperties("rinos.authentication.webauthn")
public record AuthenticationWebAuthnPropertiesConfig(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("app.rinos.com.br") String rpId,
    @DefaultValue("Rinos") String rpName,
    Set<URI> allowedOrigins,
    @DefaultValue("true") boolean userVerificationRequired) {
  public AuthenticationWebAuthnPropertiesConfig {
    allowedOrigins = allowedOrigins == null ? Set.of() : Set.copyOf(allowedOrigins);
    if (rpId == null || rpId.isBlank() || rpName == null || rpName.isBlank())
      throw new IllegalArgumentException("rpId e rpName são obrigatórios.");
    if (enabled && allowedOrigins.isEmpty()) throw new IllegalArgumentException("WebAuthn habilitado exige allowedOrigins.");
    if (enabled && !userVerificationRequired) throw new IllegalArgumentException("WebAuthn deve exigir user verification.");
    allowedOrigins.forEach(origin -> {
      boolean local = "localhost".equalsIgnoreCase(origin.getHost());
      if ((!"https".equalsIgnoreCase(origin.getScheme()) && !(local && "http".equalsIgnoreCase(origin.getScheme())))
          || origin.getUserInfo() != null || origin.getQuery() != null || origin.getFragment() != null)
        throw new IllegalArgumentException("origin WebAuthn deve ser HTTPS ou localhost HTTP e não conter extras.");
    });
  }
}
