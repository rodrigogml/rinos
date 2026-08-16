package br.com.rinos.app.backend.module.access.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.enums.AccessKeyStatus;
import br.com.rinos.app.api.module.access.enums.AccessRuleEffect;
import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.access.enums.AuthorizationActorType;
import br.com.rinos.app.api.module.access.enums.AuthorizationSourceStatus;
import br.com.rinos.app.api.module.access.enums.AuthorizationSourceType;
import br.com.rinos.app.api.module.access.vo.AccessKeyDescriptor;
import br.com.rinos.app.api.module.access.vo.AuthorizationKeyResult;
import br.com.rinos.app.api.module.access.vo.AuthorizationRuleSource;
import br.com.rinos.app.backend.module.access.entity.AccessGroupEntity;
import br.com.rinos.app.backend.module.access.entity.AccessGroupSubjectEntity;
import br.com.rinos.app.backend.module.access.entity.AccessRuleEntity;
import br.com.rinos.app.backend.module.access.enums.AccessGroupSubjectStatus;
import br.com.rinos.app.backend.module.access.enums.AccessRecordStatus;
import br.com.rinos.app.backend.module.access.repository.AccessGroupRepository;
import br.com.rinos.app.backend.module.access.repository.AccessGroupSubjectRepository;
import br.com.rinos.app.backend.module.access.repository.AccessKeyRepository;
import br.com.rinos.app.backend.module.access.repository.AccessRuleRepository;

/** Resolve fontes diretas e de grupo com snapshot local revisionado por sujeito e contexto. */
@Service
@org.springframework.context.annotation.Lazy
public class AccessRuleResolutionService {

  private final AccessKeyRegistryService registry;
  private final AccessContextRevisionService revisionService;
  private final AccessRuleRepository ruleRepository;
  private final AccessGroupRepository groupRepository;
  private final AccessGroupSubjectRepository subjectRepository;
  private final AccessKeyRepository keyRepository;
  private final AuthorizationSnapshotCache snapshotCache;

  public AccessRuleResolutionService(
      AccessKeyRegistryService registry,
      AccessContextRevisionService revisionService,
      AccessRuleRepository ruleRepository,
      AccessGroupRepository groupRepository,
      AccessGroupSubjectRepository subjectRepository,
      AccessKeyRepository keyRepository,
      AuthorizationSnapshotCache snapshotCache) {
    this.registry = registry;
    this.revisionService = revisionService;
    this.ruleRepository = ruleRepository;
    this.groupRepository = groupRepository;
    this.subjectRepository = subjectRepository;
    this.keyRepository = keyRepository;
    this.snapshotCache = snapshotCache;
  }

  /** Resolve um ator humano sem misturar regras de outro sujeito ou contexto. */
  @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
  public ResolvedAccessSnapshot resolveHuman(AuthorizationRequest request, Instant decidedAt) {
    validateRequest(request, decidedAt);
    validateDescriptors(request);
    AccessScope scope = request.context().scope();
    Long tenantId = request.context().tenantId();
    long subjectId = scope == AccessScope.GLOBAL
        ? request.actor().identityId() : request.membershipId();
    AccessSubjectContextKey cacheKey = new AccessSubjectContextKey(scope, tenantId, subjectId);
    long revisionBefore = revisionService.current(scope, tenantId);
    SubjectAccessSnapshot snapshot = snapshotCache.get(cacheKey, revisionBefore, decidedAt)
        .orElseGet(() -> loadSnapshot(
            request, cacheKey, revisionBefore, decidedAt));

    Map<String, Long> keyIds = resolveKeyIds(request);
    List<AuthorizationKeyResult> results = request.requiredKeys().stream()
        .sorted(Comparator.comparing(AccessKeyDescriptor::code))
        .map(key -> resolveKey(
            key, keyIds.get(key.code()), snapshot.sources(), decidedAt))
        .toList();
    return new ResolvedAccessSnapshot(
        snapshot.contextRevision(), results, snapshot.nextTemporalBoundary());
  }

  private SubjectAccessSnapshot loadSnapshot(
      AuthorizationRequest request,
      AccessSubjectContextKey cacheKey,
      long revisionBefore,
      Instant decidedAt) {
    AccessScope scope = request.context().scope();
    Long tenantId = request.context().tenantId();
    List<AccessRuleEntity> direct = scope == AccessScope.GLOBAL
        ? ruleRepository.findByScopeAndTenantIdAndUserId(
            scope, null, request.actor().identityId())
        : ruleRepository.findByScopeAndTenantIdAndAccountMembershipId(
            scope, tenantId, request.membershipId());
    List<AccessGroupSubjectEntity> subjects = scope == AccessScope.GLOBAL
        ? subjectRepository.findByUserId(request.actor().identityId())
        : subjectRepository.findByAccountMembershipId(request.membershipId());
    List<Long> groupIds = subjects.stream().map(AccessGroupSubjectEntity::getGroupId).distinct()
        .toList();
    Map<Long, AccessGroupEntity> groups = new HashMap<>();
    groupRepository.findAllById(groupIds).forEach(group -> groups.put(group.getId(), group));
    List<AccessRuleEntity> groupRules = groupIds.isEmpty() ? List.of()
        : ruleRepository.findByScopeAndTenantIdAndAccessGroupIdIn(scope, tenantId, groupIds);
    Map<Long, AccessGroupSubjectEntity> subjectByGroup = new HashMap<>();
    subjects.forEach(subject -> subjectByGroup.put(subject.getGroupId(), subject));

    List<AccessSourceSnapshot> sources = new ArrayList<>();
    direct.stream().map(rule -> snapshotSource(
        rule, AuthorizationSourceType.DIRECT, null, null)).forEach(sources::add);
    groupRules.stream()
        .filter(rule -> groupMatches(groups.get(rule.getAccessGroupId()), scope, tenantId))
        .map(rule -> snapshotSource(
            rule, AuthorizationSourceType.GROUP,
            subjectByGroup.get(rule.getAccessGroupId()), groups.get(rule.getAccessGroupId())))
        .forEach(sources::add);
    long revisionAfter = revisionService.current(scope, tenantId);
    if (revisionBefore != revisionAfter) {
      throw new IllegalStateException("access context changed during rule resolution");
    }
    Instant nextBoundary = sources.stream().flatMap(AccessSourceSnapshot::temporalBoundaries)
        .filter(boundary -> boundary.isAfter(decidedAt))
        .min(Instant::compareTo)
        .orElse(null);
    SubjectAccessSnapshot snapshot = new SubjectAccessSnapshot(
        cacheKey, revisionAfter, sources, decidedAt, nextBoundary);
    snapshotCache.put(snapshot, decidedAt);
    return snapshot;
  }

  private Map<String, Long> resolveKeyIds(AuthorizationRequest request) {
    Map<String, Long> keyIds = new HashMap<>();
    request.requiredKeys().forEach(key -> keyIds.put(key.code(), keyRepository.findByCode(key.code())
        .orElseThrow(() -> new IllegalStateException("registered access key is not synchronized"))
        .getId()));
    return keyIds;
  }

  private static AuthorizationKeyResult resolveKey(
      AccessKeyDescriptor key,
      Long keyId,
      List<AccessSourceSnapshot> snapshots,
      Instant at) {
    List<AuthorizationRuleSource> permits = new ArrayList<>();
    List<AuthorizationRuleSource> blocks = new ArrayList<>();
    List<AuthorizationRuleSource> ignored = new ArrayList<>();
    snapshots.stream().filter(source -> source.accessKeyId() == keyId)
        .map(source -> source.evaluate(at))
        .forEach(source -> classify(source, permits, blocks, ignored));
    boolean allowed = !permits.isEmpty() && blocks.isEmpty();
    return new AuthorizationKeyResult(key, allowed, permits, blocks, permits.isEmpty(), ignored);
  }

  private static AccessSourceSnapshot snapshotSource(
      AccessRuleEntity rule,
      AuthorizationSourceType type,
      AccessGroupSubjectEntity subject,
      AccessGroupEntity group) {
    boolean active = rule.getStatus() == AccessRecordStatus.ACTIVE
        && (subject == null || subject.getStatus() == AccessGroupSubjectStatus.ACTIVE)
        && (group == null || group.getStatus() == AccessRecordStatus.ACTIVE);
    return new AccessSourceSnapshot(
        rule.getAccessKeyId(), type, "rule:" + rule.getId(), rule.getEffect(), active,
        rule.getValidFrom(), rule.getValidUntil(),
        subject == null ? null : subject.getValidFrom(),
        subject == null ? null : subject.getValidUntil());
  }

  private void validateDescriptors(AuthorizationRequest request) {
    for (AccessKeyDescriptor descriptor : request.requiredKeys()) {
      if (!registry.find(descriptor.code()).filter(descriptor::equals).isPresent()
          || descriptor.status() != AccessKeyStatus.ACTIVE) {
        throw new IllegalArgumentException("unknown or inactive access key descriptor");
      }
    }
  }

  private static void validateRequest(AuthorizationRequest request, Instant decidedAt) {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(decidedAt, "decidedAt must not be null");
    if (request.actor().type() != AuthorizationActorType.HUMAN) {
      throw new IllegalArgumentException("human rule resolution requires a human actor");
    }
  }

  private static boolean groupMatches(
      AccessGroupEntity group, AccessScope scope, Long tenantId) {
    return group != null && group.getScope() == scope && Objects.equals(group.getTenantId(), tenantId);
  }

  private static void classify(
      AuthorizationRuleSource source,
      List<AuthorizationRuleSource> permits,
      List<AuthorizationRuleSource> blocks,
      List<AuthorizationRuleSource> ignored) {
    if (source.status() != AuthorizationSourceStatus.CURRENT) {
      ignored.add(source);
    } else if (source.effect() == AccessRuleEffect.BLOQUEAR) {
      blocks.add(source);
    } else {
      permits.add(source);
    }
  }
}
