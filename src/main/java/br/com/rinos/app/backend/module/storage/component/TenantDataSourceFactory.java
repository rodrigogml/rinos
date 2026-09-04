package br.com.rinos.app.backend.module.storage.component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import br.com.rinos.app.backend.module.storage.vo.TenantPhysicalIdentifier;

/**
 * Deriva um datasource físico de tenant a partir do datasource global já configurado pela aplicação.
 *
 * <p>A factory preserva host, porta, parâmetros e credenciais da única fonte {@code spring.datasource.*}; altera
 * exclusivamente o catálogo para o schema derivado de {@link TenantPhysicalIdentifier}. A URL global precisa apontar
 * para {@code rinos_global}, de modo que catálogo arbitrário, outra origem ou conexão ainda não validada sejam
 * recusados antes da abertura de qualquer conexão tenant.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-29
 */
@Component
public class TenantDataSourceFactory {

  private static final String JDBC_PREFIX = "jdbc:";
  private static final String MYSQL_SCHEME = "mysql";
  private static final String GLOBAL_CATALOG_PATH = "/rinos_global";

  private final HikariDataSource globalDataSource;

  /**
   * Usa o pool global materializado pelo Spring Boot como fonte única de configuração.
   *
   * @param globalDataSource datasource configurado por {@code spring.datasource.*}
   */
  public TenantDataSourceFactory(HikariDataSource globalDataSource) {
    this.globalDataSource = Objects.requireNonNull(globalDataSource, "globalDataSource must not be null");
  }

  /**
   * Cria um pool independente para o schema derivado, preservando a configuração global já validada.
   *
   * @param physicalIdentifier identificador físico interno do tenant
   * @return datasource configurado para o único schema correspondente ao identificador
   * @throws IllegalStateException quando a URL global não atende o catálogo e o dialeto esperados
   */
  public HikariDataSource create(TenantPhysicalIdentifier physicalIdentifier) {
    return new HikariDataSource(configurationFor(physicalIdentifier));
  }

  /**
   * Produz a configuração do pool sem abrir conexão, permitindo validar a fronteira antes do uso estrutural.
   *
   * @param physicalIdentifier identificador físico interno do tenant
   * @return configuração derivada para o schema do tenant
   * @throws IllegalStateException quando a URL global não atende o catálogo e o dialeto esperados
   */
  public HikariConfig configurationFor(TenantPhysicalIdentifier physicalIdentifier) {
    Objects.requireNonNull(physicalIdentifier, "physicalIdentifier must not be null");
    HikariConfig configuration = new HikariConfig();
    globalDataSource.copyStateTo(configuration);
    configuration.setJdbcUrl(jdbcUrlFor(physicalIdentifier));
    return configuration;
  }

  /**
   * Deriva a URL JDBC tenant preservando a autoridade e os parâmetros definidos na URL global.
   *
   * @param physicalIdentifier identificador físico interno do tenant
   * @return URL JDBC segura para o schema derivado
   * @throws IllegalStateException quando a URL global não aponta exclusivamente para {@code rinos_global}
   */
  public String jdbcUrlFor(TenantPhysicalIdentifier physicalIdentifier) {
    Objects.requireNonNull(physicalIdentifier, "physicalIdentifier must not be null");
    URI globalUri = parseGlobalJdbcUrl();
    try {
      URI tenantUri = new URI(
          MYSQL_SCHEME,
          null,
          globalUri.getHost(),
          globalUri.getPort(),
          "/" + physicalIdentifier.schemaName(),
          globalUri.getRawQuery(),
          null);
      return JDBC_PREFIX + tenantUri;
    } catch (URISyntaxException exception) {
      throw new IllegalStateException("global datasource URL is invalid", exception);
    }
  }

  private URI parseGlobalJdbcUrl() {
    String jdbcUrl = globalDataSource.getJdbcUrl();
    if (jdbcUrl == null || !jdbcUrl.startsWith(JDBC_PREFIX)) {
      throw new IllegalStateException("global datasource URL must use JDBC MySQL");
    }
    try {
      URI uri = new URI(jdbcUrl.substring(JDBC_PREFIX.length()));
      if (!MYSQL_SCHEME.equalsIgnoreCase(uri.getScheme())
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || uri.getFragment() != null
          || !GLOBAL_CATALOG_PATH.equals(uri.getRawPath())) {
        throw new IllegalStateException("global datasource URL must target rinos_global");
      }
      return uri;
    } catch (URISyntaxException exception) {
      throw new IllegalStateException("global datasource URL is invalid", exception);
    }
  }
}
