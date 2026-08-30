package br.com.rinos.app.backend.module.storage.component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariDataSource;

import br.com.rinos.app.backend.module.storage.vo.TenantPhysicalIdentifier;

/**
 * Lista somente schemas de tenant reconhecíveis para comparação com o inventário global.
 *
 * <p>A consulta não aceita nome, filtro ou schema externos. Ela examina exclusivamente o padrão físico interno e
 * devolve identificadores validados para uso de serviços de diagnóstico; não cria conexão com cada tenant, não
 * expõe nomes físicos a contratos de usuário e não executa DDL.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
@Component
public class TenantPhysicalSchemaInventoryService {

  private static final String SCHEMA_PREFIX = "rinos_";
  private static final String TENANT_SCHEMA_PATTERN = "^rinos_[0-9a-f]{32}$";

  private final HikariDataSource globalDataSource;

  /**
   * Cria o leitor com a única fonte global configurada pela aplicação.
   *
   * @param globalDataSource datasource global já configurado, sem credencial paralela
   */
  public TenantPhysicalSchemaInventoryService(HikariDataSource globalDataSource) {
    this.globalDataSource = Objects.requireNonNull(globalDataSource, "globalDataSource must not be null");
  }

  /**
   * Descobre schemas físicos cujo nome pertence ao formato reservado de tenant.
   *
   * @return conjunto ordenado de identificadores internos validados
   * @throws IllegalStateException quando o inventário do servidor não puder ser lido com segurança
   */
  public Set<TenantPhysicalIdentifier> findTenantSchemas() {
    Set<TenantPhysicalIdentifier> identifiers = new LinkedHashSet<>();
    try (Connection connection = globalDataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement("""
            SELECT schema_name
            FROM information_schema.schemata
            WHERE schema_name REGEXP ?
            ORDER BY schema_name
            """)) {
      statement.setString(1, TENANT_SCHEMA_PATTERN);
      try (ResultSet result = statement.executeQuery()) {
        while (result.next()) {
          identifiers.add(new TenantPhysicalIdentifier(result.getString(1).substring(SCHEMA_PREFIX.length())));
        }
      }
      return Set.copyOf(identifiers);
    } catch (SQLException exception) {
      throw new IllegalStateException("tenant physical schema inventory is unavailable", exception);
    }
  }
}
