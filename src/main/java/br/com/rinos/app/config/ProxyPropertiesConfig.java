package br.com.rinos.app.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mantém a lista explícita de proxies cujos cabeçalhos de encaminhamento podem ser considerados.
 *
 * @param trustedProxies endereços ou redes autorizados pela instalação
 * @author Rodrigo Leitão
 * @since 2026-07-27
 */
@ConfigurationProperties("rinos.proxy")
public record ProxyPropertiesConfig(List<String> trustedProxies) {

  /**
   * Protege a lista contra nulidade e alteração externa.
   */
  public ProxyPropertiesConfig {
    trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
  }
}
