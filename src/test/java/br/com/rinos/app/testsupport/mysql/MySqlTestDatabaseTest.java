package br.com.rinos.app.testsupport.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Proteções do banco MySQL descartável")
class MySqlTestDatabaseTest {

  private static final String SCHEMA_NAME =
      "rinos_test_0123456789abcdef0123456789abcdef";

  /**
   * Comprova que parâmetros JDBC do servidor são preservados ao selecionar o schema seguro.
   */
  @Test
  void databaseUrl_shouldPreserveQuery_whenServerUrlHasParameters() {
    String databaseUrl = MySqlTestDatabase.databaseUrl(
        "jdbc:mysql://localhost:3306/?useUnicode=true&characterEncoding=UTF-8",
        SCHEMA_NAME);

    assertThat(databaseUrl).isEqualTo(
        "jdbc:mysql://localhost:3306/" + SCHEMA_NAME
            + "?useUnicode=true&characterEncoding=UTF-8"
            + "&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
  }

  /**
   * Comprova que uma URL administrativa não pode apontar para schema real ou previamente escolhido.
   */
  @Test
  void validateServerUrl_shouldRejectUrl_whenDatabaseIsSelected() {
    assertThatThrownBy(() -> MySqlTestDatabase.validateServerUrl(
        "jdbc:mysql://localhost:3306/rinos_global"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sem selecionar um database");
  }

  /**
   * Comprova que a composição recusa qualquer schema fora do prefixo e formato internos.
   */
  @Test
  void databaseUrl_shouldRejectSchema_whenNameIsNotGeneratedByHarness() {
    assertThatThrownBy(() -> MySqlTestDatabase.databaseUrl(
        "jdbc:mysql://localhost:3306/",
        "rinos_global"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("barreira de segurança");
  }
}
