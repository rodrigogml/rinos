package br.com.rinos.app.backend.module.access.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.rinos.app.backend.module.access.component.UnavailablePlanEntitlementAccessAdapter;
import br.com.rinos.app.backend.module.access.service.PlanEntitlementAccessPort;

/** Registra adapters fail-safe somente enquanto o módulo proprietário ainda não publicou a porta. */
@Configuration(proxyBeanMethods = false)
public class AccessFallbackConfiguration {

  @Bean
  @ConditionalOnMissingBean(PlanEntitlementAccessPort.class)
  PlanEntitlementAccessPort unavailablePlanEntitlementAccessAdapter() {
    return new UnavailablePlanEntitlementAccessAdapter();
  }
}
