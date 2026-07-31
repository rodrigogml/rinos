package br.com.rinos.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
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
          MaintenancePropertiesConfig maintenance =
              context.getBean(MaintenancePropertiesConfig.class);
          assertThat(maintenance.instanceId()).isEqualTo("test-instance");
          assertThat(maintenance.heartbeatInterval()).isEqualTo(Duration.ofMinutes(30));
          assertThat(maintenance.leaseTimeout()).isEqualTo(Duration.ofHours(4));
          assertThat(maintenance.stabilizationPeriod()).isEqualTo(Duration.ofMinutes(10));
          assertThat(maintenance.batchTransactionTimeout()).isEqualTo(Duration.ofMinutes(5));
          assertThat(context.getBean(RegistrationPropertiesConfig.class).pendingRetention())
              .isEqualTo(Duration.ofDays(15));
          assertThat(context.getBean(VerificationPropertiesConfig.class).validity())
              .isEqualTo(Duration.ofHours(24));
          assertThat(context.getBean(OriginPropertiesConfig.class).absoluteLimit()).isEqualTo(20);
          assertThat(context.getBean(ApplicationPropertiesConfig.class).publicBaseUrl())
              .isEqualTo(URI.create("http://localhost:7070"));
          assertThat(context.getBean(ProxyPropertiesConfig.class).trustedProxies())
              .isEqualTo(List.of("10.0.0.1", "10.0.0.0/24"));
        });
  }

  /**
   * Comprova que a origem pública de produção é lida sem depender da porta interna.
   */
  @Test
  void bind_shouldApplyCanonicalProductionOrigin_whenExplicitlyConfigured() {
    contextRunner
        .withPropertyValues(
            "rinos.maintenance.instance-id=test-instance",
            "rinos.application.public-base-url=https://app.rinos.com.br")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getBean(ApplicationPropertiesConfig.class).publicBaseUrl())
              .isEqualTo(URI.create("https://app.rinos.com.br"));
        });
  }

  /**
   * Comprova que o timeout transacional não pode alcançar a estabilização.
   */
  @Test
  void bind_shouldFail_whenBatchTimeoutEqualsStabilization() {
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
   * Comprova o binding tipado de todos os tempos da coordenação de manutenção.
   */
  @Test
  void bind_shouldApplyExplicitMaintenanceValues_whenAllValuesAreProvided() {
    contextRunner
        .withPropertyValues(
            "rinos.maintenance.instance-id=explicit-instance",
            "rinos.maintenance.heartbeat-interval=15m",
            "rinos.maintenance.lease-timeout=2h",
            "rinos.maintenance.stabilization-period=8m",
            "rinos.maintenance.batch-transaction-timeout=3m")
        .run(context -> {
          assertThat(context).hasNotFailed();
          MaintenancePropertiesConfig maintenance =
              context.getBean(MaintenancePropertiesConfig.class);
          assertThat(maintenance.instanceId()).isEqualTo("explicit-instance");
          assertThat(maintenance.heartbeatInterval()).isEqualTo(Duration.ofMinutes(15));
          assertThat(maintenance.leaseTimeout()).isEqualTo(Duration.ofHours(2));
          assertThat(maintenance.stabilizationPeriod()).isEqualTo(Duration.ofMinutes(8));
          assertThat(maintenance.batchTransactionTimeout()).isEqualTo(Duration.ofMinutes(3));
        });
  }

  /**
   * Comprova que a identidade da instância deve ser declarada explicitamente.
   */
  @Test
  void bind_shouldFail_whenInstanceIdIsBlank() {
    contextRunner
        .withPropertyValues("rinos.maintenance.instance-id= ")
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasRootCauseMessage("instanceId é obrigatório.");
        });
  }

  /**
   * Comprova que o timeout transacional não pode ultrapassar a estabilização.
   */
  @Test
  void bind_shouldFail_whenBatchTimeoutExceedsStabilization() {
    contextRunner
        .withPropertyValues(
            "rinos.maintenance.instance-id=test-instance",
            "rinos.maintenance.stabilization-period=5m",
            "rinos.maintenance.batch-transaction-timeout=6m")
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasRootCauseMessage(
                  "batchTransactionTimeout deve ser menor que stabilizationPeriod.");
        });
  }

  /**
   * Comprova que tempos nulos ou negativos não produzem uma coordenação inválida.
   */
  @Test
  void bind_shouldFail_whenMaintenanceDurationIsNotPositive() {
    contextRunner
        .withPropertyValues(
            "rinos.maintenance.instance-id=test-instance",
            "rinos.maintenance.heartbeat-interval=0s")
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasRootCauseMessage("Os tempos de manutenção devem ser maiores que zero.");
        });
  }

  /**
   * Comprova que o heartbeat deve ocorrer antes de o lease expirar.
   */
  @Test
  void bind_shouldFail_whenHeartbeatIsNotShorterThanLease() {
    contextRunner
        .withPropertyValues(
            "rinos.maintenance.instance-id=test-instance",
            "rinos.maintenance.heartbeat-interval=4h",
            "rinos.maintenance.lease-timeout=4h")
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasRootCauseMessage("heartbeatInterval deve ser menor que leaseTimeout.");
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
