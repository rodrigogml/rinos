package br.com.rinos.app.backend.module.access.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.module.access.enums.AccessKeyStatus;
import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.access.vo.AccessCategoryDescriptor;
import br.com.rinos.app.api.module.access.vo.AccessKeyDescriptor;
import br.com.rinos.app.backend.module.access.entity.AccessKeyCategoryEntity;
import br.com.rinos.app.backend.module.access.entity.AccessKeyEntity;
import br.com.rinos.app.backend.module.access.entity.AccessKeyRequirementEntity;
import br.com.rinos.app.backend.module.access.entity.ProtectedGroupBaselineEntity;
import br.com.rinos.app.backend.module.access.entity.ProtectedGroupBaselineKeyEntity;
import br.com.rinos.app.backend.module.access.enums.AccessRecordStatus;
import br.com.rinos.app.backend.module.access.repository.AccessKeyCategoryRepository;
import br.com.rinos.app.backend.module.access.repository.AccessKeyRepository;
import br.com.rinos.app.backend.module.access.repository.AccessKeyRequirementRepository;
import br.com.rinos.app.backend.module.access.repository.ProtectedGroupBaselineKeyRepository;
import br.com.rinos.app.backend.module.access.repository.ProtectedGroupBaselineRepository;

/** Sincroniza inclusões compatíveis do registry sem inventar ou remover chaves persistidas. */
@Service
@org.springframework.context.annotation.Lazy
public class AccessCatalogSynchronizationService {

  private static final int DESCRIPTOR_VERSION = 1;
  private static final int INITIAL_BASELINE_VERSION = 1;

  private final AccessKeyRegistryService registry;
  private final AccessKeyCategoryRepository categoryRepository;
  private final AccessKeyRepository keyRepository;
  private final AccessKeyRequirementRepository requirementRepository;
  private final ProtectedGroupBaselineRepository baselineRepository;
  private final ProtectedGroupBaselineKeyRepository baselineKeyRepository;

  public AccessCatalogSynchronizationService(
      AccessKeyRegistryService registry,
      AccessKeyCategoryRepository categoryRepository,
      AccessKeyRepository keyRepository,
      AccessKeyRequirementRepository requirementRepository,
      ProtectedGroupBaselineRepository baselineRepository,
      ProtectedGroupBaselineKeyRepository baselineKeyRepository) {
    this.registry = registry;
    this.categoryRepository = categoryRepository;
    this.keyRepository = keyRepository;
    this.requirementRepository = requirementRepository;
    this.baselineRepository = baselineRepository;
    this.baselineKeyRepository = baselineKeyRepository;
  }

  /** Publica catálogo e baselines iniciais em uma única transação de readiness. */
  @Transactional
  public void synchronize() {
    Map<String, AccessKeyCategoryEntity> categories = synchronizeCategories(registry.categories());
    Map<String, AccessKeyEntity> keys = synchronizeKeys(registry.accessKeys(), categories);
    synchronizeBaseline(AccessScope.GLOBAL, registry.accessKeys(), keys);
    synchronizeBaseline(AccessScope.TENANT, registry.accessKeys(), keys);
  }

  private Map<String, AccessKeyCategoryEntity> synchronizeCategories(
      Collection<AccessCategoryDescriptor> descriptors) {
    Map<String, AccessKeyCategoryEntity> persisted = new HashMap<>();
    categoryRepository.findAll().forEach(category -> persisted.put(category.getCode(), category));
    List<AccessCategoryDescriptor> pending = new ArrayList<>(descriptors);
    int displayOrder = 0;
    while (!pending.isEmpty()) {
      int previousSize = pending.size();
      for (var iterator = pending.iterator(); iterator.hasNext();) {
        AccessCategoryDescriptor descriptor = iterator.next();
        AccessKeyCategoryEntity parent = descriptor.parentCode() == null
            ? null : persisted.get(descriptor.parentCode());
        if (descriptor.parentCode() != null && parent == null) {
          continue;
        }
        AccessKeyCategoryEntity entity = persisted.get(descriptor.code());
        if (entity != null && entity.getScope() != descriptor.scope()) {
          throw new IllegalStateException("persisted access category has incompatible scope");
        }
        if (entity == null) {
          entity = new AccessKeyCategoryEntity(
              descriptor.code(), parent == null ? null : parent.getId(), descriptor.scope(),
              descriptor.nameI18nKey(), descriptionKey(descriptor.code()), displayOrder,
              AccessRecordStatus.ACTIVE);
        } else {
          entity.synchronize(parent == null ? null : parent.getId(), descriptor.nameI18nKey(),
              descriptionKey(descriptor.code()), displayOrder, AccessRecordStatus.ACTIVE);
        }
        entity = categoryRepository.saveAndFlush(entity);
        persisted.put(descriptor.code(), entity);
        iterator.remove();
        displayOrder++;
      }
      if (pending.size() == previousSize) {
        throw new IllegalStateException("access category hierarchy could not be persisted");
      }
    }
    return persisted;
  }

  private Map<String, AccessKeyEntity> synchronizeKeys(
      Collection<AccessKeyDescriptor> descriptors,
      Map<String, AccessKeyCategoryEntity> categories) {
    Map<String, AccessKeyEntity> persisted = new HashMap<>();
    keyRepository.findAll().forEach(key -> persisted.put(key.getCode(), key));
    for (AccessKeyDescriptor descriptor : descriptors) {
      AccessKeyCategoryEntity category = categories.get(descriptor.categoryCode());
      AccessKeyEntity entity = persisted.get(descriptor.code());
      if (entity != null && (entity.getScope() != descriptor.scope()
          || !entity.getOwnerModule().equals(descriptor.ownerModule()))) {
        throw new IllegalStateException("persisted access key has incompatible identity");
      }
      AccessRecordStatus status = descriptor.status() == AccessKeyStatus.ACTIVE
          ? AccessRecordStatus.ACTIVE : AccessRecordStatus.INACTIVE;
      if (entity == null) {
        entity = new AccessKeyEntity(
            descriptor.code(), descriptor.scope(), category.getId(), descriptor.ownerModule(),
            descriptor.nameI18nKey(), descriptor.descriptionI18nKey(),
            entitlementScope(descriptor), entitlementCode(descriptor), status, DESCRIPTOR_VERSION);
      } else {
        entity.synchronize(
            category.getId(), descriptor.nameI18nKey(), descriptor.descriptionI18nKey(),
            entitlementScope(descriptor), entitlementCode(descriptor), status, DESCRIPTOR_VERSION);
      }
      entity = keyRepository.saveAndFlush(entity);
      synchronizeRequirements(entity, descriptor);
      persisted.put(descriptor.code(), entity);
    }
    return persisted;
  }

  private void synchronizeRequirements(AccessKeyEntity key, AccessKeyDescriptor descriptor) {
    requirementRepository.deleteByAccessKeyId(key.getId());
    List<AccessKeyRequirementEntity> requirements = descriptor.sourceRequirements().stream()
        .map(requirement -> new AccessKeyRequirementEntity(
            key.getId(), requirement.featureCode(), requirement.requirementCode()))
        .toList();
    requirementRepository.saveAll(requirements);
  }

  private void synchronizeBaseline(
      AccessScope scope,
      Collection<AccessKeyDescriptor> descriptors,
      Map<String, AccessKeyEntity> keys) {
    Set<Long> expected = new HashSet<>();
    descriptors.stream()
        .filter(descriptor -> descriptor.scope() == scope && descriptor.minimumAdministrative())
        .map(descriptor -> keys.get(descriptor.code()).getId())
        .forEach(expected::add);
    ProtectedGroupBaselineEntity baseline = baselineRepository
        .findByScopeAndBaselineVersion(scope, INITIAL_BASELINE_VERSION)
        .orElseGet(() -> baselineRepository.saveAndFlush(
            new ProtectedGroupBaselineEntity(scope, INITIAL_BASELINE_VERSION)));
    Set<Long> actual = baselineKeyRepository.findByIdBaselineId(baseline.getId()).stream()
        .map(entity -> entity.getId().getAccessKeyId())
        .collect(java.util.stream.Collectors.toSet());
    if (actual.isEmpty()) {
      baselineKeyRepository.saveAll(expected.stream()
          .map(keyId -> new ProtectedGroupBaselineKeyEntity(baseline.getId(), keyId))
          .toList());
    } else if (!actual.equals(expected)) {
      throw new IllegalStateException(
          "protected baseline differs from its explicit published version");
    }
  }

  private static String descriptionKey(String code) {
    return "access.category." + code + ".description";
  }

  private static String entitlementCode(AccessKeyDescriptor descriptor) {
    return descriptor.entitlementRequirement() == null
        ? null : descriptor.entitlementRequirement().code();
  }

  private static br.com.rinos.app.api.module.plans.enums.ContractScope entitlementScope(
      AccessKeyDescriptor descriptor) {
    return descriptor.entitlementRequirement() == null
        ? null : descriptor.entitlementRequirement().subjectScope();
  }
}
