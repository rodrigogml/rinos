package br.com.rinos.app.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Limites locais do cache de fontes ACL; não alteram a autoridade da revisão persistida. */
@ConfigurationProperties("rinos.access.cache")
public record AccessCachePropertiesConfig(
    @DefaultValue("10000") int maxWeight,
    @DefaultValue("30m") Duration idleTimeout) {

  public AccessCachePropertiesConfig {
    if (maxWeight <= 0 || idleTimeout == null || idleTimeout.isZero()
        || idleTimeout.isNegative()) {
      throw new IllegalArgumentException("Os limites do cache ACL devem ser positivos.");
    }
  }
}
