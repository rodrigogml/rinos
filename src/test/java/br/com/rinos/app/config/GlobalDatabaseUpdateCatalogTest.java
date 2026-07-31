package br.com.rinos.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import br.eng.rodrigogml.rfw.platform.database.config.DatabaseUpdatePropertiesConfig;
import br.eng.rodrigogml.rfw.platform.database.service.DatabaseUpdateScriptDiscoveryService;
import br.eng.rodrigogml.rfw.platform.database.service.DatabaseUpdateScriptNameService;
import br.eng.rodrigogml.rfw.platform.database.vo.DatabaseUpdateScriptVO;

@DisplayName("Catálogo de updates do banco global")
class GlobalDatabaseUpdateCatalogTest {

  /**
   * Comprova que o catálogo de teste legado e os updates atuais são descobertos em ordem.
   */
  @Test
  void discover_shouldReturnOrderedGlobalUpdates_whenTestAndMainResourcesAreCombined() {
    DatabaseUpdatePropertiesConfig properties = new DatabaseUpdatePropertiesConfig();
    properties.setLocations(List.of(
        "classpath:db/global/update/20260728_001_update.sql",
        "classpath:db/global/update/20260728_002_update.sql",
        "classpath:db/global/update/20260729_001_update.sql",
        "classpath:db/global/update/20260729_002_update.sql",
        "classpath:db/global/update/20260729_003_update.sql",
        "classpath:db/global/update/20260729_004_update.sql",
        "classpath:db/global/update/20260729_005_update.sql"));
    DatabaseUpdateScriptDiscoveryService discovery = new DatabaseUpdateScriptDiscoveryService(
        new PathMatchingResourcePatternResolver(),
        new DatabaseUpdateScriptNameService());

    List<DatabaseUpdateScriptVO> scripts = discovery.discover(properties);

    assertThat(scripts).extracting(DatabaseUpdateScriptVO::fileName).containsExactly(
        "20260728_001_update.sql",
        "20260728_002_update.sql",
        "20260729_001_update.sql",
        "20260729_002_update.sql",
        "20260729_003_update.sql",
        "20260729_004_update.sql",
        "20260729_005_update.sql");
    assertThat(scripts).extracting(script -> script.version().value()).containsExactly(
        "20260728001",
        "20260728002",
        "20260729001",
        "20260729002",
        "20260729003",
        "20260729004",
        "20260729005");
  }
}
