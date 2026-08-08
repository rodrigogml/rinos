package br.com.rinos.app.config;

import java.util.Base64;
import java.util.Map;
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
  public AuthenticationKeyringPropertiesConfig {
    keys = keys == null ? Map.of() : Map.copyOf(keys);
    if (enabled) {
      if (activeVersion == null || activeVersion.isBlank() || !keys.containsKey(activeVersion))
        throw new IllegalArgumentException("keyring habilitado exige activeVersion presente em keys.");
      keys.forEach((version, secret) -> {
        if (!version.matches("[A-Za-z0-9_-]{1,32}")) throw new IllegalArgumentException("versão inválida no keyring.");
        try {
          if (Base64.getDecoder().decode(secret).length < 32) throw new IllegalArgumentException("cada chave deve possuir ao menos 256 bits.");
        } catch (IllegalArgumentException invalidBase64) {
          throw new IllegalArgumentException("chaves do keyring devem usar Base64 válido e ao menos 256 bits.", invalidBase64);
        }
      });
    }
  }
}
