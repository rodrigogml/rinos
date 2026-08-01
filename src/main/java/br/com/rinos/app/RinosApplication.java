package br.com.rinos.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;

/**
 * Ponto de entrada da aplicação hospedeira Rinos.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-27
 */
@SpringBootApplication
@StyleSheet("context://rfw/styles.css")
public class RinosApplication implements AppShellConfigurator {

  /**
   * Inicializa o contexto Spring Boot e o servidor web da aplicação.
   *
   * @param arguments argumentos recebidos pelo processo; não devem ser usados como fonte de configuração do Rinos
   */
  public static void main(String[] arguments) {
    SpringApplication.run(RinosApplication.class, arguments);
  }
}
