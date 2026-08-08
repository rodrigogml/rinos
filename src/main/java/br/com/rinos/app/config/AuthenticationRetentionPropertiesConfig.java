package br.com.rinos.app.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Retenções operacionais exclusivas dos artefatos de autenticação.
 *
 * @author Rodrigo Leitão
 */
@ConfigurationProperties("rinos.authentication.retention")
public record AuthenticationRetentionPropertiesConfig(
    @DefaultValue("30d") Duration terminalSessions,
    @DefaultValue("30d") Duration temporaryArtifacts,
    @DefaultValue("365d") Duration identityEvents) {
  public AuthenticationRetentionPropertiesConfig {
    if (terminalSessions == null || terminalSessions.isNegative() || terminalSessions.isZero()
        || temporaryArtifacts == null || temporaryArtifacts.isNegative() || temporaryArtifacts.isZero()
        || identityEvents == null || identityEvents.isNegative() || identityEvents.isZero())
      throw new IllegalArgumentException("retenções de autenticação devem ser maiores que zero.");
  }
}
