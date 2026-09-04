package br.com.rinos.app.backend.module.storage.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import br.eng.rodrigogml.rfw.database.config.DatabaseUpdatePropertiesConfig;
import br.eng.rodrigogml.rfw.database.vo.DatabaseUpdateRequestVO;

/** Testa a fronteira de catálogo explícita entre atualizações globais e de tenant. */
@DisplayName("Requisição de update do tenant")
class TenantDatabaseUpdateRequestFactoryTest {

  /** Comprova que a requisição não reutiliza locations globais configuradas. */
  @Test
  void create_shouldUseOnlyTenantLocation_whenGlobalUpdaterHasDifferentLocations() {
    DatabaseUpdatePropertiesConfig properties = new DatabaseUpdatePropertiesConfig();
    properties.setLocations(List.of("classpath:db/global/update/"));
    properties.setLockTimeout(Duration.ofSeconds(17));
    TenantDatabaseUpdateRequestFactory factory = new TenantDatabaseUpdateRequestFactory(properties);

    DatabaseUpdateRequestVO request = factory.create(new DriverManagerDataSource());

    assertThat(request.locations()).containsExactly(TenantDatabaseUpdateRequestFactory.TENANT_UPDATE_LOCATION);
    assertThat(request.lockTimeout()).isEqualTo(Duration.ofSeconds(17));
  }
}
