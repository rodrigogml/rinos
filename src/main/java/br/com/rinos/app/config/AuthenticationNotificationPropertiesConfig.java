package br.com.rinos.app.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cooldowns fixos das notificações de segurança.
 *
 * @author Rodrigo Leitão
 */
@ConfigurationProperties("rinos.authentication.notification")
public record AuthenticationNotificationPropertiesConfig(
    @DefaultValue("24h") Duration failedLoginCooldown,
    @DefaultValue("30d") Duration deviceRecognition) {
  public AuthenticationNotificationPropertiesConfig {
    if (failedLoginCooldown == null || failedLoginCooldown.isNegative() || failedLoginCooldown.isZero()
        || deviceRecognition == null || deviceRecognition.isNegative() || deviceRecognition.isZero())
      throw new IllegalArgumentException("durações de notificação devem ser maiores que zero.");
  }
}
