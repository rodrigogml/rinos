package br.com.rinos.app.backend.module.storage.component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariDataSource;

import br.com.rinos.app.backend.module.storage.vo.TenantPhysicalIdentifier;
import br.com.rinos.app.backend.module.storage.vo.TenantSchemaInitializationResultVO;
import br.eng.rodrigogml.rfw.database.config.DatabaseUpdatePropertiesConfig;
import br.eng.rodrigogml.rfw.exception.RFWDatabaseUpdateErrorCategoryEnum;
import br.eng.rodrigogml.rfw.exception.RFWDatabaseUpdateException;

/**
 * Cria e inicializa um schema físico de tenant a partir de uma identidade interna já validada.
 *
 * <p>A criação usa somente o datasource global configurado para o Rinos. Em repetição, um schema já existente não
 * recebe novamente scripts de init: sua estrutura é apenas validada, preservando a investigação de um estado parcial
 * para a etapa coordenadora posterior.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
@Component
@ConditionalOnProperty(prefix = "rfw.database.update", name = "enabled", havingValue = "true")
public class TenantSchemaInitializer {
  private static final String UTF8MB4 = "utf8mb4";
  private static final String UTF8MB4_UNICODE_CI = "utf8mb4_unicode_ci";

  private final DataSource globalDataSource;
  private final TenantDataSourceFactory tenantDataSourceFactory;
  private final TenantSchemaInitScriptComponent initScripts;
  private final TenantDatabaseStructureVerifier structureVerifier;
  private final TenantStorageNamedLockComponent namedLock;
  private final DatabaseUpdatePropertiesConfig databaseUpdateProperties;

  /**
   * Cria o inicializador com as únicas fronteiras permitidas para criação e validação.
   *
   * @param globalDataSource datasource global configurado pela instalação
   * @param tenantDataSourceFactory factory que deriva somente datasources de tenant válidos
   * @param initScripts executor exclusivo do catálogo {@code db/tenant/init}
   * @param structureVerifier validador da versão e baseline após o init ou uma retomada
   * @param namedLock serializador cross-instance do schema físico do tenant
   * @param databaseUpdateProperties propriedade RFW que define o prazo de espera do lock
   */
  public TenantSchemaInitializer(DataSource globalDataSource, TenantDataSourceFactory tenantDataSourceFactory,
      TenantSchemaInitScriptComponent initScripts, TenantDatabaseStructureVerifier structureVerifier,
      TenantStorageNamedLockComponent namedLock, DatabaseUpdatePropertiesConfig databaseUpdateProperties) {
    this.globalDataSource = Objects.requireNonNull(globalDataSource, "globalDataSource must not be null");
    this.tenantDataSourceFactory = Objects.requireNonNull(tenantDataSourceFactory,
        "tenantDataSourceFactory must not be null");
    this.initScripts = Objects.requireNonNull(initScripts, "initScripts must not be null");
    this.structureVerifier = Objects.requireNonNull(structureVerifier, "structureVerifier must not be null");
    this.namedLock = Objects.requireNonNull(namedLock, "namedLock must not be null");
    this.databaseUpdateProperties = Objects.requireNonNull(databaseUpdateProperties,
        "databaseUpdateProperties must not be null");
  }

  /**
   * Cria um schema tenant ausente, executa seu init e valida a versão estrutural esperada.
   *
   * @param physicalIdentifier identificador físico interno do tenant
   * @param expectedVersion versão exata que o schema precisa expor ao término
   * @return resultado que informa somente se a chamada criou o schema agora
   * @throws RFWDatabaseUpdateException quando a criação, o init ou a validação estrutural falhar
   */
  public TenantSchemaInitializationResultVO initialize(TenantPhysicalIdentifier physicalIdentifier,
      String expectedVersion) {
    Objects.requireNonNull(physicalIdentifier, "physicalIdentifier must not be null");
    if (expectedVersion == null || expectedVersion.isBlank()) {
      throw new IllegalArgumentException("expectedVersion must not be blank");
    }
    boolean createdNow = createSchemaIfAbsent(physicalIdentifier);
    try (HikariDataSource tenantDataSource = tenantDataSourceFactory.create(physicalIdentifier)) {
      namedLock.executeExclusive(tenantDataSource, databaseUpdateProperties.getLockTimeout(), () -> {
        if (createdNow) {
          initScripts.execute(tenantDataSource);
        }
        structureVerifier.verify(tenantDataSource, expectedVersion, List.of());
      });
      return new TenantSchemaInitializationResultVO(createdNow);
    }
  }

  private boolean createSchemaIfAbsent(TenantPhysicalIdentifier physicalIdentifier) {
    String schemaName = physicalIdentifier.schemaName();
    try (Connection connection = globalDataSource.getConnection()) {
      if (schemaExists(connection, schemaName)) {
        return false;
      }
      try (Statement statement = connection.createStatement()) {
        statement.execute("CREATE DATABASE `" + schemaName + "` CHARACTER SET " + UTF8MB4
            + " COLLATE " + UTF8MB4_UNICODE_CI);
        return true;
      } catch (SQLException exception) {
        if (schemaExists(connection, schemaName)) {
          return false;
        }
        throw exception;
      }
    } catch (SQLException exception) {
      throw new RFWDatabaseUpdateException(RFWDatabaseUpdateErrorCategoryEnum.EXECUTION,
          "Não foi possível criar ou observar o schema do tenant.", exception);
    }
  }

  private static boolean schemaExists(Connection connection, String schemaName) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
        SELECT 1
        FROM information_schema.schemata
        WHERE schema_name = ?
        """)) {
      statement.setString(1, schemaName);
      return statement.executeQuery().next();
    }
  }
}
