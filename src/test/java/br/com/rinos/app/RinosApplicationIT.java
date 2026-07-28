package br.com.rinos.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
    "rfw.platform.database.update.enabled=false"
})
class RinosApplicationIT {

  /**
   * Comprova que a aplicação hospedeira e as auto-configurações disponíveis iniciam sem infraestrutura externa.
   */
  @Test
  void contextLoads_shouldStartApplication_whenExternalInfrastructureIsDisabled() {
  }
}
