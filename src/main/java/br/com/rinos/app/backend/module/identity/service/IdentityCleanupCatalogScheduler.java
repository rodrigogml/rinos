package br.com.rinos.app.backend.module.identity.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara o catálogo diário de limpezas em tarefas e transações independentes.
 *
 * <p>Cada tarefa comprova a liderança por conta própria e abre lotes limitados separados. Uma
 * falha parcial não impede as tarefas seguintes nem registra PII, tokens ou conteúdo removido.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Component
@Lazy(false)
public class IdentityCleanupCatalogScheduler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(IdentityCleanupCatalogScheduler.class);

  private final Supplier<RegistrationExpiryCleanupService> registrationCleanupSupplier;
  private final Supplier<OriginWindowCleanupService> originCleanupSupplier;
  private final Supplier<IdentityTombstoneCleanupService> tombstoneCleanupSupplier;
  private final Clock clock;

  /**
   * Cria o catálogo com o relógio UTC da aplicação.
   *
   * @param registrationCleanupProvider resolução tardia da expiração das pendências
   * @param originCleanupProvider resolução tardia da retenção dos endereços de origem
   * @param tombstoneCleanupProvider resolução tardia da retenção do tombstone
   */
  @Autowired
  public IdentityCleanupCatalogScheduler(
      ObjectProvider<RegistrationExpiryCleanupService> registrationCleanupProvider,
      ObjectProvider<OriginWindowCleanupService> originCleanupProvider,
      ObjectProvider<IdentityTombstoneCleanupService> tombstoneCleanupProvider) {
    registrationCleanupSupplier = registrationCleanupProvider::getIfAvailable;
    originCleanupSupplier = originCleanupProvider::getIfAvailable;
    tombstoneCleanupSupplier = tombstoneCleanupProvider::getIfAvailable;
    clock = Clock.systemUTC();
  }

  /**
   * Cria o catálogo com relógio controlável.
   *
   * @param registrationExpiryCleanupService limpeza das pendências
   * @param originWindowCleanupService limpeza das origens
   * @param tombstoneCleanupService limpeza dos tombstones
   * @param clock relógio do corte comum
   */
  IdentityCleanupCatalogScheduler(
      RegistrationExpiryCleanupService registrationExpiryCleanupService,
      OriginWindowCleanupService originWindowCleanupService,
      IdentityTombstoneCleanupService tombstoneCleanupService,
      Clock clock) {
    RegistrationExpiryCleanupService registrationCleanup = Objects.requireNonNull(
        registrationExpiryCleanupService,
        "registrationExpiryCleanupService must not be null");
    OriginWindowCleanupService originCleanup = Objects.requireNonNull(
        originWindowCleanupService,
        "originWindowCleanupService must not be null");
    IdentityTombstoneCleanupService tombstoneCleanup = Objects.requireNonNull(
        tombstoneCleanupService,
        "tombstoneCleanupService must not be null");
    registrationCleanupSupplier = () -> registrationCleanup;
    originCleanupSupplier = () -> originCleanup;
    tombstoneCleanupSupplier = () -> tombstoneCleanup;
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * Executa o catálogo com atraso inicial suficiente para a primeira estabilização do lease.
   */
  @Scheduled(
      fixedDelayString = "${rinos.cleanup.interval:24h}",
      initialDelayString = "${rinos.maintenance.heartbeat-interval:30m}")
  public void cleanup() {
    Instant executionTime = clock.instant();
    RegistrationExpiryCleanupService registrationCleanup =
        registrationCleanupSupplier.get();
    if (registrationCleanup != null) {
      execute(
          "registration-expiry",
          instant -> registrationCleanup.cleanup(instant),
          executionTime);
    }
    OriginWindowCleanupService originCleanup = originCleanupSupplier.get();
    if (originCleanup != null) {
      execute(
          "origin-window-retention",
          instant -> originCleanup.cleanup(instant),
          executionTime);
    }
    IdentityTombstoneCleanupService tombstoneCleanup = tombstoneCleanupSupplier.get();
    if (tombstoneCleanup != null) {
      execute(
          "cancellation-tombstone-retention",
          instant -> tombstoneCleanup.cleanup(instant),
          executionTime);
    }
  }

  private static void execute(
      String taskName,
      Consumer<Instant> task,
      Instant executionTime) {
    try {
      task.accept(executionTime);
    } catch (RuntimeException failure) {
      LOGGER.warn(
          "Tarefa do catálogo de limpeza falhou: task={}, failureType={}",
          taskName,
          failure.getClass().getSimpleName());
    }
  }
}
