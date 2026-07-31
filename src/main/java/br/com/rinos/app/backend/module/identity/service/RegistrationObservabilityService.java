package br.com.rinos.app.backend.module.identity.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.enums.RegistrationLifecycleEventEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationOperationEnum;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Centraliza métricas e logs sanitizados do ciclo de cadastro.
 *
 * <p>Operação e evento são enums fechados; resultado aceita somente códigos em caixa alta. O
 * correlation ID aparece apenas no log e nunca é tag de métrica, evitando cardinalidade
 * ilimitada. A API não aceita e-mail, IP, prova, URL ou texto livre.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class RegistrationObservabilityService {

  public static final String OPERATION_COUNTER_NAME = "rinos.registration.operations";
  public static final String OPERATION_DURATION_NAME =
      "rinos.registration.operation.duration";
  public static final String LIFECYCLE_COUNTER_NAME =
      "rinos.registration.lifecycle.events";

  private static final Pattern RESULT_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
  private static final Logger LOGGER =
      LoggerFactory.getLogger(RegistrationObservabilityService.class);

  private final MeterRegistry meterRegistry;

  /**
   * Cria a fronteira sobre o registro de métricas da instalação.
   *
   * @param meterRegistry destino Micrometer, em memória quando não houver exporter
   */
  public RegistrationObservabilityService(MeterRegistry meterRegistry) {
    this.meterRegistry = Objects.requireNonNull(
        meterRegistry,
        "meterRegistry must not be null");
  }

  /**
   * Registra conclusão, resultado e duração de uma operação pública.
   *
   * @param operation operação fechada
   * @param resultCode resultado público fechado
   * @param correlationId correlação técnica opcional, somente para log
   * @param startedAt início UTC
   * @param completedAt conclusão UTC
   */
  public void recordOperation(
      RegistrationOperationEnum operation,
      String resultCode,
      UUID correlationId,
      Instant startedAt,
      Instant completedAt) {
    Objects.requireNonNull(operation, "operation must not be null");
    if (resultCode == null || !RESULT_CODE.matcher(resultCode).matches()) {
      throw new IllegalArgumentException("resultCode must be a safe closed code");
    }
    Objects.requireNonNull(startedAt, "startedAt must not be null");
    Objects.requireNonNull(completedAt, "completedAt must not be null");
    Duration duration = Duration.between(startedAt, completedAt);
    if (duration.isNegative()) {
      duration = Duration.ZERO;
    }
    String operationTag = operation.name().toLowerCase(Locale.ROOT);
    String resultTag = resultCode.toLowerCase(Locale.ROOT);
    Counter.builder(OPERATION_COUNTER_NAME)
        .description("Conclusões das operações públicas do cadastro")
        .tag("operation", operationTag)
        .tag("result", resultTag)
        .register(meterRegistry)
        .increment();
    Timer.builder(OPERATION_DURATION_NAME)
        .description("Duração das operações públicas do cadastro")
        .tag("operation", operationTag)
        .tag("result", resultTag)
        .register(meterRegistry)
        .record(duration);
    LOGGER.info(
        "Operação de cadastro concluída: operation={}, result={}, correlationId={}, "
            + "durationMillis={}",
        operationTag,
        resultTag,
        correlationId,
        duration.toMillis());
    recordDerivedLifecycle(operation, resultCode);
  }

  /**
   * Incrementa efeitos de lifecycle sem usar identidade persistente como dimensão.
   *
   * @param event efeito fechado
   * @param amount quantidade positiva produzida
   */
  public void recordLifecycle(
      RegistrationLifecycleEventEnum event,
      int amount) {
    Objects.requireNonNull(event, "event must not be null");
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be positive");
    }
    Counter.builder(LIFECYCLE_COUNTER_NAME)
        .description("Efeitos terminais ou criação de pendências do cadastro")
        .tag("event", event.name().toLowerCase(Locale.ROOT))
        .register(meterRegistry)
        .increment(amount);
  }

  private void recordDerivedLifecycle(
      RegistrationOperationEnum operation,
      String resultCode) {
    if ("RATE_LIMITED".equals(resultCode)) {
      recordLifecycle(RegistrationLifecycleEventEnum.BLOCKED, 1);
      return;
    }
    if (operation == RegistrationOperationEnum.START
        && ("EMAIL_SENT".equals(resultCode)
            || "EMAIL_DISPATCH_FAILED".equals(resultCode))) {
      recordLifecycle(RegistrationLifecycleEventEnum.PENDING_CREATED, 1);
    } else if ((operation == RegistrationOperationEnum.ACTIVATE
        || operation == RegistrationOperationEnum.ACTIVATION_CONSENT)
        && "ACTIVATED".equals(resultCode)) {
      recordLifecycle(RegistrationLifecycleEventEnum.ACTIVATED, 1);
    } else if (operation == RegistrationOperationEnum.CANCELLATION_CONFIRM
        && "CANCELLED".equals(resultCode)) {
      recordLifecycle(RegistrationLifecycleEventEnum.CANCELLED, 1);
    }
  }
}
