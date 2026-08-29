package br.com.rinos.app.backend.module.storage.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import br.com.rinos.app.backend.module.storage.vo.TenantPhysicalIdentifier;

@DisplayName("Factory de datasource de tenant")
class TenantDataSourceFactoryTest {

  private HikariDataSource globalDataSource;

  @AfterEach
  void closeDataSource() {
    if (globalDataSource != null) {
      globalDataSource.close();
    }
  }

  @Test
  void configurationFor_shouldReplaceOnlyGlobalCatalogAndPreserveConnectionSettings() {
    globalDataSource = globalDataSource(
        "jdbc:mysql://mysql.example.test:3307/rinos_global?useUnicode=true&characterEncoding=UTF-8");
    TenantDataSourceFactory factory = new TenantDataSourceFactory(globalDataSource);

    HikariConfig configuration = factory.configurationFor(identifier());

    assertThat(configuration.getJdbcUrl()).isEqualTo(
        "jdbc:mysql://mysql.example.test:3307/rinos_0123456789abcdef0123456789abcdef"
            + "?useUnicode=true&characterEncoding=UTF-8");
    assertThat(configuration.getUsername()).isEqualTo("rinos");
    assertThat(configuration.getPassword()).isEqualTo("local-only-test-password");
    assertThat(configuration.getConnectionInitSql()).isEqualTo("SET time_zone = '+00:00'");
  }

  @Test
  void jdbcUrlFor_shouldRejectGlobalUrlOutsideExpectedCatalogBeforeConnectionCreation() {
    globalDataSource = globalDataSource("jdbc:mysql://mysql.example.test:3307/other_database");
    TenantDataSourceFactory factory = new TenantDataSourceFactory(globalDataSource);

    assertThatIllegalStateException()
        .isThrownBy(() -> factory.jdbcUrlFor(identifier()))
        .withMessage("global datasource URL must target rinos_global");
  }

  @Test
  void jdbcUrlFor_shouldRejectNonMysqlGlobalUrlBeforeConnectionCreation() {
    HikariDataSource dataSource = mock(HikariDataSource.class);
    when(dataSource.getJdbcUrl()).thenReturn("jdbc:postgresql://postgres.example.test:5432/rinos_global");
    TenantDataSourceFactory factory = new TenantDataSourceFactory(dataSource);

    assertThatIllegalStateException()
        .isThrownBy(() -> factory.jdbcUrlFor(identifier()))
        .withMessage("global datasource URL must target rinos_global");
  }

  private static TenantPhysicalIdentifier identifier() {
    return new TenantPhysicalIdentifier("0123456789abcdef0123456789abcdef");
  }

  private static HikariDataSource globalDataSource(String jdbcUrl) {
    HikariConfig configuration = new HikariConfig();
    configuration.setJdbcUrl(jdbcUrl);
    configuration.setUsername("rinos");
    configuration.setPassword("local-only-test-password");
    configuration.setConnectionInitSql("SET time_zone = '+00:00'");
    configuration.setInitializationFailTimeout(-1);
    return new HikariDataSource(configuration);
  }
}
