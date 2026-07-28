package br.com.rinos.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da aplicação hospedeira Rinos.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-27
 */
@SpringBootApplication
public class RinosApplication {

  /**
   * Inicializa o contexto Spring Boot e o servidor web da aplicação.
   *
   * @param arguments argumentos recebidos pelo processo; não devem ser usados como fonte de configuração do Rinos
   */
  public static void main(String[] arguments) {
    SpringApplication.run(RinosApplication.class, arguments);
  }
}
