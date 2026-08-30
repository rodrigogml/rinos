package br.com.rinos.app.backend.module.storage.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.storage.component.TenantPhysicalSchemaInventoryService;
import br.com.rinos.app.backend.module.storage.entity.StorageOperationEntity;
import br.com.rinos.app.backend.module.storage.entity.TenantStorageRegistryEntity;
import br.com.rinos.app.backend.module.storage.enums.TenantStorageDivergenceType;
import br.com.rinos.app.backend.module.storage.repository.StorageOperationRepository;
import br.com.rinos.app.backend.module.storage.repository.TenantStorageRegistryRepository;
import br.com.rinos.app.backend.module.storage.vo.TenantPhysicalIdentifier;
import br.com.rinos.app.backend.module.storage.vo.TenantStorageDivergenceVO;
import br.com.rinos.app.backend.module.storage.vo.TenantStorageReconciliationSnapshotVO;

/**
 * Detecta divergências físicas sem alterar registry, operações, schemas ou estados de tenant.
 *
 * <p>O serviço é uma fronteira de leitura para a futura reconciliação administrativa. A ausência de uma localização
 * ou o vencimento de um lease só é relatada: nenhuma localização é adotada, removida, atribuída ou considerada
 * pronta, e nenhuma operação recebe retry por esta inspeção.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
@Service
public class TenantStorageReconciliationInspectionService {

  private final TenantStorageRegistryRepository registryRepository;
  private final StorageOperationRepository operationRepository;
  private final TenantPhysicalSchemaInventoryService schemaInventoryService;
  private final Clock clock;

  /**
   * Cria o detector read-only com as fontes global e física necessárias à comparação.
   *
   * @param registryRepository inventário global de tenants conhecidos
   * @param operationRepository fila global, consultada somente para leases vencidos
   * @param schemaInventoryService leitor fechado de schemas físicos internos
   */
  public TenantStorageReconciliationInspectionService(TenantStorageRegistryRepository registryRepository,
      StorageOperationRepository operationRepository, TenantPhysicalSchemaInventoryService schemaInventoryService) {
    this(registryRepository, operationRepository, schemaInventoryService, Clock.systemUTC());
  }

  TenantStorageReconciliationInspectionService(TenantStorageRegistryRepository registryRepository,
      StorageOperationRepository operationRepository, TenantPhysicalSchemaInventoryService schemaInventoryService,
      Clock clock) {
    this.registryRepository = Objects.requireNonNull(registryRepository, "registryRepository must not be null");
    this.operationRepository = Objects.requireNonNull(operationRepository, "operationRepository must not be null");
    this.schemaInventoryService = Objects.requireNonNull(schemaInventoryService,
        "schemaInventoryService must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * Compara o registro global e o inventário físico, preservando um resultado sanitizado e exclusivamente diagnóstico.
   *
   * @return divergências de registros conhecidos e quantidade de schemas órfãos reconhecíveis
   */
  @Transactional(readOnly = true)
  public TenantStorageReconciliationSnapshotVO inspect() {
    Instant now = clock.instant();
    Set<TenantPhysicalIdentifier> physicalIdentifiers = schemaInventoryService.findTenantSchemas();
    List<TenantStorageRegistryEntity> registries = registryRepository.findAll();
    Set<TenantPhysicalIdentifier> registeredIdentifiers = new HashSet<>();
    List<TenantStorageDivergenceVO> divergences = new ArrayList<>();
    for (TenantStorageRegistryEntity registry : registries) {
      registeredIdentifiers.add(registry.getPhysicalIdentifier());
      if (!physicalIdentifiers.contains(registry.getPhysicalIdentifier())) {
        divergences.add(new TenantStorageDivergenceVO(registry.getId(),
            TenantStorageDivergenceType.REGISTRY_SCHEMA_MISSING));
      }
    }
    for (StorageOperationEntity operation : operationRepository.findAllStalledForReconciliation(now)) {
      divergences.add(new TenantStorageDivergenceVO(operation.getTenantStorageRegistryId(),
          TenantStorageDivergenceType.OPERATION_WITHOUT_PROGRESS));
    }
    int unregisteredSchemaCount = (int) physicalIdentifiers.stream()
        .filter(identifier -> !registeredIdentifiers.contains(identifier))
        .count();
    return new TenantStorageReconciliationSnapshotVO(divergences, unregisteredSchemaCount, now);
  }
}
