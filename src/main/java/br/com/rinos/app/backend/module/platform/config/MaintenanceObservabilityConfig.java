package br.com.rinos.app.backend.module.platform.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Garante um registro mínimo de métricas sem exigir endpoint administrativo ou exporter.
 *
 * <p>Uma instalação que fornecer outro {@link MeterRegistry} substitui automaticamente o registro
 * em memória, preservando a instrumentação do módulo.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Configuration(proxyBeanMethods = false)
public class MaintenanceObservabilityConfig {

  /**
   * Cria o registro local usado quando nenhuma integração de métricas foi instalada.
   *
   * @return registro de métricas em memória
   */
  @Bean
  @ConditionalOnMissingBean(MeterRegistry.class)
  MeterRegistry maintenanceMeterRegistry() {
    return new SimpleMeterRegistry();
  }
}
