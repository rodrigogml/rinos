package br.com.rinos.app.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig;

/**
 * Reforça invariantes fixas do Rinos sobre protocolos configuráveis fornecidos pela RFW.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
@Configuration(proxyBeanMethods = false)
public class AuthenticationProtocolPropertiesValidatorConfig {

  /**
   * Falha a inicialização quando a quantidade configurada diverge do contrato do Rinos.
   *
   * @param authentication propriedades de autenticação fornecidas pela RFW
   * @return validação executada depois da criação dos singletons
   */
  @Bean
  SmartInitializingSingleton authenticationProtocolPropertiesValidator(
      RFWAuthenticationPropertiesConfig authentication) {
    return () -> {
      if (authentication.secondFactor().recoveryCodeCount() != 10) {
        throw new IllegalStateException(
            "O Rinos exige exatamente 10 códigos de recuperação.");
      }
    };
  }
}
