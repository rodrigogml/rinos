package br.com.rinos.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("Binding das propriedades do Rinos")
class RinosConfigurationBindingTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(RinosConfigurationConfig.class);

  /**
   * Comprova os valores padrão funcionais e o binding de lista explícita.
   */
  @Test
  void bind_shouldApplyDefaultsAndExplicitList_whenOnlyRequiredValueIsProvided() {
    contextRunner
        .withPropertyValues(
            "rinos.maintenance.instance-id=test-instance",
            "rinos.proxy.trusted-proxies=10.0.0.1,10.0.0.0/24")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getBean(RegistrationPropertiesConfig.class).pendingRetention())
              .isEqualTo(Duration.ofDays(15));
          assertThat(context.getBean(VerificationPropertiesConfig.class).validity())
              .isEqualTo(Duration.ofHours(24));
          assertThat(context.getBean(OriginPropertiesConfig.class).absoluteLimit()).isEqualTo(20);
          assertThat(context.getBean(ProxyPropertiesConfig.class).trustedProxies())
              .isEqualTo(List.of("10.0.0.1", "10.0.0.0/24"));
        });
  }

  /**
   * Comprova que o timeout transacional não pode alcançar a estabilização.
   */
  @Test
  void bind_shouldFail_whenBatchTimeoutIsNotShorterThanStabilization() {
    contextRunner
        .withPropertyValues(
            "rinos.maintenance.instance-id=test-instance",
            "rinos.maintenance.stabilization-period=5m",
            "rinos.maintenance.batch-transaction-timeout=5m")
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasRootCauseMessage(
                  "batchTransactionTimeout deve ser menor que stabilizationPeriod.");
        });
  }

  /**
   * Comprova que propriedades externas não reduzem o piso Argon2id.
   */
  @Test
  void bind_shouldFail_whenPasswordMemoryIsBelowSecurityFloor() {
    contextRunner
        .withPropertyValues(
            "rinos.maintenance.instance-id=test-instance",
            "rinos.password-hash.memory-kib=1024")
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasRootCauseMessage(
                  "Os parâmetros Argon2id não podem ficar abaixo do piso de segurança.");
        });
  }

  /**
   * Comprova que o limiar do Turnstile não pode ultrapassar o bloqueio absoluto.
   */
  @Test
  void bind_shouldFail_whenTurnstileThresholdExceedsAbsoluteLimit() {
    contextRunner
        .withPropertyValues(
            "rinos.maintenance.instance-id=test-instance",
            "rinos.origin.turnstile-threshold=21")
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasRootCauseMessage(
                  "absoluteLimit deve ser positivo e não pode ser menor que turnstileThreshold.");
        });
  }
}
