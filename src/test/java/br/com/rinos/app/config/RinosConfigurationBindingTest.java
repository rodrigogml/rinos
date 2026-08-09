package br.com.rinos.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("Binding das propriedades do Rinos")
class RinosConfigurationBindingTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(RinosConfigurationConfig.class);

  private static final String KEY_V1 = Base64.getEncoder().encodeToString(new byte[32]);

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
          RegistrationPropertiesConfig registration =
              context.getBean(RegistrationPropertiesConfig.class);
          assertThat(registration.pendingRetention()).isEqualTo(Duration.ofDays(15));
          assertThat(registration.cancellationRequestLimit()).isEqualTo(3);
          assertThat(registration.cancellationRequestWindow())
              .isEqualTo(Duration.ofMinutes(15));
          assertThat(context.getBean(VerificationPropertiesConfig.class).validity())
              .isEqualTo(Duration.ofHours(24));
          assertThat(context.getBean(OriginPropertiesConfig.class).absoluteLimit()).isEqualTo(20);
          assertThat(context.getBean(AuthenticationSessionPropertiesConfig.class).normalAbsolute())
              .isEqualTo(Duration.ofHours(12));
          AuthenticationMfaPropertiesConfig mfa =
              context.getBean(AuthenticationMfaPropertiesConfig.class);
          assertThat(mfa.maximumAttempts()).isEqualTo(5);
          assertThat(mfa.emailResendCooldown()).isEqualTo(Duration.ofMinutes(1));
          assertThat(mfa.emailEmissionLimit()).isEqualTo(3);
          assertThat(mfa.emailEmissionWindow()).isEqualTo(Duration.ofMinutes(15));
          assertThat(context.getBean(AuthenticationKeyringPropertiesConfig.class).enabled())
              .isFalse();
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

  @Test
  void bind_shouldLoadActiveAndPreviousKeyVersions_whenKeyringIsValid() {
    byte[] secondKey = new byte[32];
    java.util.Arrays.fill(secondKey, (byte) 2);
    contextRunner
        .withPropertyValues(
            "rinos.maintenance.instance-id=test-instance",
            "rinos.authentication.keyring.enabled=true",
            "rinos.authentication.keyring.active-version=v2",
            "rinos.authentication.keyring.keys.v1=" + KEY_V1,
            "rinos.authentication.keyring.keys.v2="
                + Base64.getEncoder().encodeToString(secondKey))
        .run(context -> {
          assertThat(context).hasNotFailed();
          AuthenticationKeyringPropertiesConfig keyring =
              context.getBean(AuthenticationKeyringPropertiesConfig.class);
          assertThat(keyring.activeVersion()).isEqualTo("v2");
          assertThat(keyring.keys()).containsOnlyKeys("v1", "v2");
        });
  }

  @Test
  void bind_shouldFailStartup_whenEnabledKeyringHasInvalidSecret() {
    contextRunner
        .withPropertyValues(
            "rinos.maintenance.instance-id=test-instance",
            "rinos.authentication.keyring.enabled=true",
            "rinos.authentication.keyring.active-version=v1",
            "rinos.authentication.keyring.keys.v1=not-base64")
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasRootCauseMessage(
                  "chaves do keyring devem usar Base64 válido e ao menos 256 bits; "
                      + "cada versão deve ser canônica e distinta.");
        });
  }

  @Test
  void bind_shouldFailStartup_whenActiveKeyVersionIsMissing() {
    contextRunner
        .withPropertyValues(
            "rinos.maintenance.instance-id=test-instance",
            "rinos.authentication.keyring.enabled=true",
            "rinos.authentication.keyring.active-version=v2",
            "rinos.authentication.keyring.keys.v1=" + KEY_V1)
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasRootCauseMessage(
                  "keyring habilitado exige activeVersion presente em keys.");
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

  /** Comprova que o cooldown de reenvio precisa caber na janela de emissões. */
  @Test
  void bind_shouldFail_whenEmailOtpCooldownExceedsEmissionWindow() {
    contextRunner
        .withPropertyValues(
            "rinos.maintenance.instance-id=test-instance",
            "rinos.authentication.mfa.email-resend-cooldown=16m",
            "rinos.authentication.mfa.email-emission-window=15m")
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasRootCauseMessage(
                  "emailResendCooldown não pode exceder emailEmissionWindow.");
        });
  }

  /**
   * Comprova que a janela de cancelamento não aceita duração sem significado operacional.
   */
  @Test
  void bind_shouldFail_whenCancellationRequestWindowIsNotPositive() {
    contextRunner
        .withPropertyValues(
            "rinos.maintenance.instance-id=test-instance",
            "rinos.registration.cancellation-request-window=0s")
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasRootCauseMessage(
                  "cancellationRequestWindow deve ser maior que zero.");
        });
  }

  @Test
  void bind_shouldFail_whenEnabledKeyringHasNoUsableActiveKey() {
    contextRunner
        .withPropertyValues(
            "rinos.maintenance.instance-id=test-instance",
            "rinos.authentication.keyring.enabled=true",
            "rinos.authentication.keyring.active-version=v1",
            "rinos.authentication.keyring.keys.v1=short")
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasStackTraceContaining(
                  "chaves do keyring devem usar Base64 válido e ao menos 256 bits");
        });
  }

  @Test
  void bind_shouldFail_whenEnabledWebAuthnAllowsInsecureRemoteOrigin() {
    contextRunner
        .withPropertyValues(
            "rinos.maintenance.instance-id=test-instance",
            "rinos.authentication.webauthn.enabled=true",
            "rinos.authentication.webauthn.allowed-origins=http://app.rinos.com.br")
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasRootCauseMessage(
                  "origin WebAuthn deve ser HTTPS ou localhost HTTP e não conter extras.");
        });
  }
}
