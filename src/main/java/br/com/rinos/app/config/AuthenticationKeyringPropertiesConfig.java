package br.com.rinos.app.config;

import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Keyring local versionado, ativado somente quando segredos explícitos estiverem configurados.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@ConfigurationProperties("rinos.authentication.keyring")
public record AuthenticationKeyringPropertiesConfig(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("") String activeVersion,
    Map<String, String> keys) {

  private static final String INVALID_KEY_MESSAGE =
      "chaves do keyring devem usar Base64 válido e ao menos 256 bits; "
          + "cada versão deve ser canônica e distinta.";

  public AuthenticationKeyringPropertiesConfig {
    keys = keys == null ? Map.of() : Map.copyOf(keys);
    if (!enabled) {
      if (activeVersion != null && !activeVersion.isBlank()) {
        throw new IllegalArgumentException(
            "keyring desabilitado não pode declarar activeVersion.");
      }
      if (keys.values().stream().anyMatch(secret -> secret != null && !secret.isBlank())) {
        throw new IllegalArgumentException(
            "keyring desabilitado não pode declarar chaves utilizáveis.");
      }
    } else {
      if (activeVersion == null || activeVersion.isBlank() || !keys.containsKey(activeVersion)) {
        throw new IllegalArgumentException(
            "keyring habilitado exige activeVersion presente em keys.");
      }
      Set<String> decodedKeys = new HashSet<>();
      keys.forEach((version, secret) -> validateKey(version, secret, decodedKeys));
    }
  }

  private static void validateKey(String version, String secret, Set<String> decodedKeys) {
    if (version == null || !version.matches("[A-Za-z0-9_-]{1,32}")) {
      throw new IllegalArgumentException("versão inválida no keyring.");
    }
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(secret);
    } catch (NullPointerException | IllegalArgumentException invalidKey) {
      throw new IllegalArgumentException(INVALID_KEY_MESSAGE);
    }
    if (decoded.length < 32) {
      throw new IllegalArgumentException(INVALID_KEY_MESSAGE);
    }
    String canonical = Base64.getEncoder().encodeToString(decoded);
    if (!canonical.equals(secret) || !decodedKeys.add(canonical)) {
      throw new IllegalArgumentException(INVALID_KEY_MESSAGE);
    }
  }

  /** Oculta as chaves e expõe somente estado e quantidade de versões em diagnósticos. */
  @Override
  public String toString() {
    return "AuthenticationKeyringPropertiesConfig[enabled=" + enabled
        + ", activeVersion=" + activeVersion + ", keys=REDACTED(" + keys.size() + ")]";
  }
}
