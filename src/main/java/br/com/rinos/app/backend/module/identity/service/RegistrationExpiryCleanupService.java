package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationLifecycleEventEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.RegistrationRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.platform.service.MaintenanceCoordinatorService;
import br.com.rinos.app.config.CleanupPropertiesConfig;

/**
 * Remove identidades que permaneceram pendentes além do prazo absoluto do cadastro.
 *
 * <p>Cada lote é relido com lock dentro da transação coordenada. Uma identidade que tenha sido
 * ativada antes da aquisição do lock é ignorada, mesmo diante de cadastro inconsistente.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class RegistrationExpiryCleanupService {

  private final RegistrationRepository registrationRepository;
  private final UserRepository userRepository;
  private final RegistrationLifecycleService lifecycleService;
  private final IdentityAuditService auditService;
  private final MaintenanceCoordinatorService coordinator;
  private final CleanupPropertiesConfig cleanupProperties;
  private final RegistrationObservabilityService observabilityService;

  /**
   * Cria a tarefa de expiração submetida ao lease global.
   *
   * @param registrationRepository seleção bloqueável das pendências
   * @param userRepository deleção da raiz e dependências por cascade
   * @param lifecycleService validação da transição terminal
   * @param auditService evento sanitizado de expiração
   * @param coordinator prova de liderança e fronteira transacional
   * @param cleanupProperties tamanho máximo de cada lote
   * @param observabilityService métricas sanitizadas de expiração
   */
  public RegistrationExpiryCleanupService(
      RegistrationRepository registrationRepository,
      UserRepository userRepository,
      RegistrationLifecycleService lifecycleService,
      IdentityAuditService auditService,
      MaintenanceCoordinatorService coordinator,
      CleanupPropertiesConfig cleanupProperties,
      RegistrationObservabilityService observabilityService) {
    this.registrationRepository = Objects.requireNonNull(
        registrationRepository,
        "registrationRepository must not be null");
    this.userRepository = Objects.requireNonNull(
        userRepository,
        "userRepository must not be null");
    this.lifecycleService = Objects.requireNonNull(
        lifecycleService,
        "lifecycleService must not be null");
    this.auditService = Objects.requireNonNull(
        auditService,
        "auditService must not be null");
    this.coordinator = Objects.requireNonNull(
        coordinator,
        "coordinator must not be null");
    this.cleanupProperties = Objects.requireNonNull(
        cleanupProperties,
        "cleanupProperties must not be null");
    this.observabilityService = Objects.requireNonNull(
        observabilityService,
        "observabilityService must not be null");
  }

  /**
   * Executa lotes até esgotar as pendências vencidas ou perder a liderança.
   *
   * @param executionTime instante UTC que fixa o corte do catálogo
   * @return total de raízes removidas
   */
  public int cleanup(Instant executionTime) {
    Objects.requireNonNull(executionTime, "executionTime must not be null");
    if (!coordinator.canStartJob()) {
      return 0;
    }
    int total = 0;
    while (true) {
      AtomicInteger deleted = new AtomicInteger();
      boolean executed = coordinator.executeBatch(
          () -> deleted.set(deleteBatch(executionTime)));
      if (!executed) {
        return total;
      }
      recordExpiration(deleted.get());
      total = Math.addExact(total, deleted.get());
      if (deleted.get() < cleanupProperties.batchSize()) {
        return total;
      }
    }
  }

  private int deleteBatch(Instant executionTime) {
    List<RegistrationEntity> registrations =
        registrationRepository.findExpiredPendingBatchForUpdate(
            RegistrationStatusEnum.PENDING_VERIFICATION,
            UserStatusEnum.PENDING_VERIFICATION,
            executionTime,
            PageRequest.of(0, cleanupProperties.batchSize()));
    int deleted = 0;
    List<UUID> tombstones = new ArrayList<>();
    List<RegistrationEntity> expiredRegistrations = new ArrayList<>();
    for (RegistrationEntity registration : registrations) {
      UserEntity user = registration.getUser();
      if (user.getStatus() != UserStatusEnum.PENDING_VERIFICATION
          || registration.getStatus() != RegistrationStatusEnum.PENDING_VERIFICATION
          || registration.getExpiresAt().isAfter(executionTime)) {
        continue;
      }
      lifecycleService.transition(
          registration,
          RegistrationStatusEnum.EXPIRED,
          IdentityTransitionOriginEnum.SCHEDULED_JOB,
          "RETENTION_ELAPSED",
          executionTime);
      expiredRegistrations.add(registration);
    }
    if (!expiredRegistrations.isEmpty()) {
      registrationRepository.flush();
    }
    for (RegistrationEntity registration : expiredRegistrations) {
      UserEntity user = registration.getUser();
      UUID correlationId = UUID.randomUUID();
      auditService.minimizeForTerminalRemoval(user, registration);
      registrationRepository.delete(registration);
      userRepository.delete(user);
      tombstones.add(correlationId);
      deleted++;
    }
    if (deleted > 0) {
      userRepository.flush();
      tombstones.forEach(correlationId ->
          auditService.recordExpiryTombstone(correlationId, executionTime));
    }
    return deleted;
  }

  private void recordExpiration(int amount) {
    if (amount <= 0) {
      return;
    }
    try {
      observabilityService.recordLifecycle(
          RegistrationLifecycleEventEnum.EXPIRED,
          amount);
    } catch (RuntimeException ignoredObservabilityFailure) {
      // A métrica não participa do commit do lote nem deve interromper a retenção.
    }
  }
}
