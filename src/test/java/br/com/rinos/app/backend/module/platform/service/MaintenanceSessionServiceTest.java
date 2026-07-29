package br.com.rinos.app.backend.module.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import br.com.rinos.app.backend.module.platform.vo.MaintenanceSessionVO;
import br.com.rinos.app.config.MaintenancePropertiesConfig;

@DisplayName("Sessão da coordenação de manutenção")
class MaintenanceSessionServiceTest {

  private static final String INSTANCE_ID = "maintenance-instance";

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withBean(MaintenancePropertiesConfig.class, MaintenanceSessionServiceTest::properties)
      .withUserConfiguration(MaintenanceSessionService.class);

  /**
   * Comprova que todas as leituras do mesmo contexto compartilham a sessão inicial.
   */
  @Test
  void getCurrentSession_shouldReturnSameSession_whenContextRemainsActive() {
    contextRunner.run(context -> {
      MaintenanceSessionService service = context.getBean(MaintenanceSessionService.class);

      MaintenanceSessionVO first = service.getCurrentSession();
      MaintenanceSessionVO second = service.getCurrentSession();

      assertThat(first).isSameAs(second);
      assertThat(first.instanceId()).isEqualTo(INSTANCE_ID);
      assertThat(first.sessionId().version()).isEqualTo(4);
    });
  }

  /**
   * Comprova que uma nova inicialização não reutiliza a sessão do contexto anterior.
   */
  @Test
  void getCurrentSession_shouldGenerateNewSession_whenContextRestarts() {
    AtomicReference<MaintenanceSessionVO> previous = new AtomicReference<>();
    contextRunner.run(context -> previous.set(
        context.getBean(MaintenanceSessionService.class).getCurrentSession()));

    contextRunner.run(context -> {
      MaintenanceSessionVO current =
          context.getBean(MaintenanceSessionService.class).getCurrentSession();

      assertThat(current.instanceId()).isEqualTo(previous.get().instanceId());
      assertThat(current.sessionId()).isNotEqualTo(previous.get().sessionId());
    });
  }

  private static MaintenancePropertiesConfig properties() {
    return new MaintenancePropertiesConfig(
        INSTANCE_ID,
        Duration.ofMinutes(30),
        Duration.ofHours(4),
        Duration.ofMinutes(10),
        Duration.ofMinutes(5));
  }
}
