package br.com.rinos.app.backend.module.membership.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.rinos.app.backend.module.membership.component.UnavailableMembershipPlanCapacityAdapter;
import br.com.rinos.app.backend.module.membership.service.MembershipPlanCapacityPort;

/** Mantém a avaliação de capacidade fail-safe enquanto planos não publicar sua implementação. */
@Configuration(proxyBeanMethods = false)
public class MembershipFallbackConfiguration {

  @Bean
  @ConditionalOnMissingBean(MembershipPlanCapacityPort.class)
  MembershipPlanCapacityPort unavailableMembershipPlanCapacityAdapter() {
    return new UnavailableMembershipPlanCapacityAdapter();
  }
}
