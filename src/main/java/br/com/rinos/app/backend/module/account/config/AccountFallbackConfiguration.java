package br.com.rinos.app.backend.module.account.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.rinos.app.backend.module.account.component.UnavailableAccountHumanVerificationAdapter;
import br.com.rinos.app.backend.module.account.service.AccountHumanVerificationPort;

/** Mantém verificação humana fail-safe quando o provider RFW não está configurado. */
@Configuration(proxyBeanMethods = false)
public class AccountFallbackConfiguration {

  @Bean
  @ConditionalOnMissingBean(AccountHumanVerificationPort.class)
  AccountHumanVerificationPort unavailableAccountHumanVerificationAdapter() {
    return new UnavailableAccountHumanVerificationAdapter();
  }
}
