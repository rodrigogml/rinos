package br.com.rinos.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;

import br.com.rinos.app.ui.module.identity.view.LoginView;
import br.eng.rodrigogml.rfw.platform.authentication.config.RFWAccessSecurityConfigurer;

/**
 * Integra a autorização de rotas Vaadin com os protocolos de acesso compartilhados do RFW.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Configuration
@EnableWebSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

  /**
   * Mantém as rotas anônimas declaradas pelas views e protege qualquer rota futura por padrão.
   *
   * @param http cadeia Spring Security
   * @param rfwAccess protocolos opcionais do RFW
   * @return cadeia construída
   * @throws Exception quando um configurer rejeitar a composição
   */
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      RFWAccessSecurityConfigurer rfwAccess) throws Exception {
    rfwAccess.configure(http);
    http.authorizeHttpRequests(authorize -> authorize
        .requestMatchers("/login", "/legal-document/**").permitAll());
    http.with(VaadinSecurityConfigurer.vaadin(), vaadin ->
        vaadin.loginView(LoginView.class));
    return http.build();
  }
}
