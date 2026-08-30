package br.com.rinos.app.backend.module.storage.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import br.com.rinos.app.backend.module.storage.vo.TenantDatabaseCatalogVO;
import br.eng.rodrigogml.rfw.database.service.DatabaseUpdateScriptDiscoveryService;
import br.eng.rodrigogml.rfw.database.service.DatabaseUpdateScriptNameService;
import br.eng.rodrigogml.rfw.database.service.DatabaseUpdateScriptValidationService;
import br.eng.rodrigogml.rfw.database.service.SqlScriptParserService;
import br.eng.rodrigogml.rfw.exception.RFWDatabaseUpdateException;

/** Testa descoberta, versão e recusa de catálogo de update de tenant. */
@DisplayName("Catálogo estrutural de tenant")
class TenantDatabaseCatalogServiceTest {

  /** Confirma a versão alvo e a evidência SHA-256 do catálogo oficial distribuído. */
  @Test
  void inspect_shouldExposeOrderedTenantCatalog_whenOfficialScriptsArePresent() {
    TenantDatabaseCatalogVO catalog = officialCatalogService().inspect();

    assertThat(catalog.targetVersion().value()).isEqualTo("20260829001");
    assertThat(catalog.scripts()).hasSize(1);
    assertThat(catalog.scripts().getFirst().fileName()).isEqualTo("20260829_001_update.sql");
    assertThat(catalog.scripts().getFirst().contentHash()).hasSize(32);
  }

  /** Confirma que ausência de scripts bloqueia a verificação estrutural. */
  @Test
  void inspect_shouldRejectEmptyCatalog_whenNoSqlResourceIsAvailable() {
    TenantDatabaseCatalogService service = new TenantDatabaseCatalogService(
        discoveryService(), validationService(), List.of("classpath:db/test-storage/absent/"));

    assertThatThrownBy(service::inspect).isInstanceOf(RFWDatabaseUpdateException.class);
  }

  static TenantDatabaseCatalogService officialCatalogService() {
    return new TenantDatabaseCatalogService(discoveryService(), validationService());
  }

  static TenantDatabaseCatalogService controlledCatalogService() {
    return new TenantDatabaseCatalogService(
        discoveryService(), validationService(), List.of("classpath:db/test-storage/update/"));
  }

  private static DatabaseUpdateScriptDiscoveryService discoveryService() {
    return new DatabaseUpdateScriptDiscoveryService(
        new PathMatchingResourcePatternResolver(), new DatabaseUpdateScriptNameService());
  }

  private static DatabaseUpdateScriptValidationService validationService() {
    return new DatabaseUpdateScriptValidationService(new SqlScriptParserService());
  }
}
