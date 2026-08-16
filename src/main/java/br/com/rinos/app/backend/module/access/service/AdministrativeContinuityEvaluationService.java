package br.com.rinos.app.backend.module.access.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.module.access.enums.AccessRuleEffect;
import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.membership.enums.MembershipStatus;
import br.com.rinos.app.backend.module.access.entity.AccessGroupEntity;
import br.com.rinos.app.backend.module.access.entity.AccessGroupSubjectEntity;
import br.com.rinos.app.backend.module.access.entity.AccessRuleEntity;
import br.com.rinos.app.backend.module.access.enums.AccessGroupSubjectStatus;
import br.com.rinos.app.backend.module.access.enums.AccessRecordStatus;
import br.com.rinos.app.backend.module.access.enums.AccessRuleOriginType;
import br.com.rinos.app.backend.module.access.enums.ProtectedBaselineStatus;
import br.com.rinos.app.backend.module.access.repository.AccessGroupRepository;
import br.com.rinos.app.backend.module.access.repository.AccessGroupSubjectRepository;
import br.com.rinos.app.backend.module.access.repository.AccessKeyRepository;
import br.com.rinos.app.backend.module.access.repository.AccessRuleRepository;
import br.com.rinos.app.backend.module.access.repository.ProtectedGroupBaselineKeyRepository;
import br.com.rinos.app.backend.module.access.repository.ProtectedGroupBaselineRepository;
import br.com.rinos.app.backend.module.account.repository.AccountRepository;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.service.AuthenticationMethodInventoryService;
import br.com.rinos.app.backend.module.membership.entity.AccountMembershipEntity;
import br.com.rinos.app.backend.module.membership.repository.AccountMembershipRepository;
import br.com.rinos.app.backend.module.membership.service.MembershipAdministrativeContinuityPort;
import br.com.rinos.app.backend.module.membership.service.MembershipContinuityDecision;
import br.com.rinos.app.backend.module.membership.service.MembershipContinuityRequest;

/** Resolve continuidade pelo resultado efetivo das chaves mínimas e por fator forte persistente. */
@Service
@org.springframework.context.annotation.Lazy
@Primary
public class AdministrativeContinuityEvaluationService
    implements MembershipAdministrativeContinuityPort, AdministrativeContinuityEvaluator {

  private final AccountRepository accounts;
  private final AccountMembershipRepository memberships;
  private final UserRepository users;
  private final AuthenticationMethodInventoryService methods;
  private final ProtectedGroupBaselineRepository baselines;
  private final ProtectedGroupBaselineKeyRepository baselineKeys;
  private final AccessKeyRepository keys;
  private final AccessRuleRepository rules;
  private final AccessGroupSubjectRepository subjects;
  private final AccessGroupRepository groups;

  public AdministrativeContinuityEvaluationService(
      AccountRepository accounts,
      AccountMembershipRepository memberships,
      UserRepository users,
      AuthenticationMethodInventoryService methods,
      ProtectedGroupBaselineRepository baselines,
      ProtectedGroupBaselineKeyRepository baselineKeys,
      AccessKeyRepository keys,
      AccessRuleRepository rules,
      AccessGroupSubjectRepository subjects,
      AccessGroupRepository groups) {
    this.accounts = accounts;
    this.memberships = memberships;
    this.users = users;
    this.methods = methods;
    this.baselines = baselines;
    this.baselineKeys = baselineKeys;
    this.keys = keys;
    this.rules = rules;
    this.subjects = subjects;
    this.groups = groups;
  }

  @Override
  @Transactional(readOnly = true)
  public MembershipContinuityDecision evaluate(MembershipContinuityRequest request) {
    if (request == null || request.accountId() <= 0 || request.tenantId() <= 0
        || request.affectedMembershipId() <= 0 || request.resultingStatus() == null
        || request.effectiveAt() == null) return MembershipContinuityDecision.unavailable();
    try {
      var account = accounts.findById(request.accountId())
          .filter(value -> value.getTenantId() == request.tenantId()).orElse(null);
      if (account == null) return MembershipContinuityDecision.unavailable();
      return decideAcrossKnownBoundaries(request.accountId(), request.tenantId(), request.effectiveAt(),
          request.affectedMembershipId(), request.resultingStatus());
    } catch (RuntimeException unavailable) {
      return MembershipContinuityDecision.unavailable();
    }
  }

  /** Avalia o estado corrente depois de uma mutação ACL ainda não confirmada. */
  @Transactional(readOnly = true)
  public MembershipContinuityDecision evaluateTenant(long tenantId, Instant effectiveAt) {
    if (tenantId <= 0 || effectiveAt == null) return MembershipContinuityDecision.unavailable();
    try {
      var account = accounts.findByTenantId(tenantId).orElse(null);
      return account == null ? MembershipContinuityDecision.unavailable()
          : decideAcrossKnownBoundaries(account.getId(), tenantId, effectiveAt, null, null);
    } catch (RuntimeException unavailable) {
      return MembershipContinuityDecision.unavailable();
    }
  }

  @Override
  public AdministrativeContinuitySnapshot inspectContext(
      AccessScope scope, Long tenantId, Instant effectiveAt) {
    if (scope == AccessScope.TENANT && tenantId != null) {
      return inspectTenant(tenantId, effectiveAt);
    }
    if (scope != AccessScope.GLOBAL || tenantId != null || effectiveAt == null) {
      return AdministrativeContinuitySnapshot.unavailable();
    }
    try {
      return inspectGlobalAcrossKnownBoundaries(effectiveAt);
    } catch (RuntimeException unavailable) {
      return AdministrativeContinuitySnapshot.unavailable();
    }
  }

  private AdministrativeContinuitySnapshot inspectTenant(long tenantId, Instant effectiveAt) {
    if (tenantId <= 0 || effectiveAt == null) return AdministrativeContinuitySnapshot.unavailable();
    try {
      var account = accounts.findByTenantId(tenantId).orElse(null);
      return account == null ? AdministrativeContinuitySnapshot.unavailable()
          : inspectAcrossKnownBoundaries(account.getId(), tenantId, effectiveAt, null, null);
    } catch (RuntimeException unavailable) {
      return AdministrativeContinuitySnapshot.unavailable();
    }
  }

  private AdministrativeContinuitySnapshot inspectGlobalAcrossKnownBoundaries(Instant effectiveAt) {
    List<AccessRuleEntity> globalRules = rules.findByScopeAndTenantId(AccessScope.GLOBAL, null);
    Map<Long, AccessGroupEntity> globalGroups = new HashMap<>();
    groups.findByScopeAndTenantIdOrderByNormalizedName(AccessScope.GLOBAL, null)
        .forEach(group -> globalGroups.put(group.getId(), group));
    List<AccessGroupSubjectEntity> globalSubjects = subjects.findAll().stream()
        .filter(subject -> globalGroups.containsKey(subject.getGroupId())).toList();
    TreeSet<Instant> boundaries = new TreeSet<>();
    boundaries.add(effectiveAt);
    globalRules.forEach(rule -> {
      addFuture(boundaries, rule.getValidFrom(), effectiveAt);
      addFuture(boundaries, rule.getValidUntil(), effectiveAt);
    });
    globalSubjects.forEach(subject -> {
      addFuture(boundaries, subject.getValidFrom(), effectiveAt);
      addFuture(boundaries, subject.getValidUntil(), effectiveAt);
    });
    int minimum = Integer.MAX_VALUE;
    for (Instant boundary : boundaries) {
      int count = countGlobal(globalRules, globalGroups, globalSubjects, boundary);
      if (count < 0) return AdministrativeContinuitySnapshot.unavailable();
      minimum = Math.min(minimum, count);
    }
    return AdministrativeContinuitySnapshot.available(minimum == Integer.MAX_VALUE ? 0 : minimum);
  }

  private int countGlobal(
      List<AccessRuleEntity> globalRules,
      Map<Long, AccessGroupEntity> globalGroups,
      List<AccessGroupSubjectEntity> globalSubjects,
      Instant at) {
    var baseline = baselines.findFirstByScopeAndStatusOrderByBaselineVersionDesc(
        AccessScope.GLOBAL, ProtectedBaselineStatus.ACTIVE).orElse(null);
    if (baseline == null) return -1;
    Set<Long> requiredKeyIds = baselineKeys.findByIdBaselineId(baseline.getId()).stream()
        .map(value -> value.getId().getAccessKeyId()).collect(java.util.stream.Collectors.toSet());
    if (requiredKeyIds.isEmpty() || keys.findAllById(requiredKeyIds).size() != requiredKeyIds.size()) {
      return -1;
    }
    int eligible = 0;
    for (var user : users.findAll()) {
      if (user.getStatus() != UserStatusEnum.ACTIVE
          || methods.inspect(user.getId()).administrativeFactorCount() <= 0) continue;
      Set<Long> groupIds = globalSubjects.stream()
          .filter(subject -> user.getId().equals(subject.getUserId()) && current(subject, at))
          .map(AccessGroupSubjectEntity::getGroupId)
          .filter(groupId -> activeGlobalGroup(globalGroups.get(groupId)))
          .collect(java.util.stream.Collectors.toSet());
      Set<Long> permitted = new HashSet<>();
      Set<Long> blocked = new HashSet<>();
      globalRules.stream().filter(rule -> current(rule, at))
          .filter(rule -> rule.getOriginType() == AccessRuleOriginType.DIRECT_USER
              && user.getId().equals(rule.getUserId())
              || rule.getOriginType() == AccessRuleOriginType.GROUP
                  && groupIds.contains(rule.getAccessGroupId()))
          .filter(rule -> requiredKeyIds.contains(rule.getAccessKeyId()))
          .forEach(rule -> (rule.getEffect() == AccessRuleEffect.BLOQUEAR ? blocked : permitted)
              .add(rule.getAccessKeyId()));
      if (blocked.isEmpty() && permitted.containsAll(requiredKeyIds)) eligible++;
    }
    return eligible;
  }

  private AdministrativeContinuitySnapshot inspectAcrossKnownBoundaries(
      long accountId, long tenantId, Instant effectiveAt, Long affectedMembershipId,
      MembershipStatus resultingStatus) {
    TreeSet<Instant> boundaries = new TreeSet<>();
    boundaries.add(effectiveAt);
    rules.findByScopeAndTenantId(AccessScope.TENANT, tenantId).forEach(rule -> {
      addFuture(boundaries, rule.getValidFrom(), effectiveAt);
      addFuture(boundaries, rule.getValidUntil(), effectiveAt);
    });
    memberships.findByAccountIdAndCurrentMarkerOrderById(accountId, 1).forEach(membership ->
        subjects.findByAccountMembershipId(membership.getId()).forEach(subject -> {
          addFuture(boundaries, subject.getValidFrom(), effectiveAt);
          addFuture(boundaries, subject.getValidUntil(), effectiveAt);
        }));
    int minimum = Integer.MAX_VALUE;
    for (Instant boundary : boundaries) {
      int count = countTenant(
          accountId, tenantId, boundary, affectedMembershipId, resultingStatus);
      if (count < 0) return AdministrativeContinuitySnapshot.unavailable();
      minimum = Math.min(minimum, count);
    }
    return AdministrativeContinuitySnapshot.available(minimum == Integer.MAX_VALUE ? 0 : minimum);
  }

  private int countTenant(
      long accountId, long tenantId, Instant at, Long affectedMembershipId,
      MembershipStatus resultingStatus) {
    var baseline = baselines.findFirstByScopeAndStatusOrderByBaselineVersionDesc(
        AccessScope.TENANT, ProtectedBaselineStatus.ACTIVE).orElse(null);
    if (baseline == null) return -1;
    Set<Long> requiredKeyIds = baselineKeys.findByIdBaselineId(baseline.getId()).stream()
        .map(value -> value.getId().getAccessKeyId()).collect(java.util.stream.Collectors.toSet());
    if (requiredKeyIds.isEmpty() || keys.findAllById(requiredKeyIds).size() != requiredKeyIds.size()) {
      return -1;
    }
    List<AccessRuleEntity> tenantRules = rules.findByScopeAndTenantId(AccessScope.TENANT, tenantId);
    Map<Long, AccessGroupEntity> tenantGroups = new HashMap<>();
    groups.findByScopeAndTenantIdOrderByNormalizedName(AccessScope.TENANT, tenantId)
        .forEach(group -> tenantGroups.put(group.getId(), group));
    int eligible = 0;
    for (AccountMembershipEntity membership
        : memberships.findByAccountIdAndCurrentMarkerOrderById(accountId, 1)) {
      MembershipStatus status = membership.getId().equals(affectedMembershipId)
          ? resultingStatus : membership.getStatus();
      if (status == MembershipStatus.ACTIVE && strongActiveIdentity(membership.getUserId())
          && allowsEveryRequiredKey(
              membership.getId(), requiredKeyIds, tenantRules, tenantGroups, at)) {
        eligible++;
      }
    }
    return eligible;
  }

  private MembershipContinuityDecision decideGlobalAcrossKnownBoundaries(Instant effectiveAt) {
    List<AccessRuleEntity> globalRules = rules.findByScopeAndTenantId(AccessScope.GLOBAL, null);
    Map<Long, AccessGroupEntity> globalGroups = new HashMap<>();
    groups.findByScopeAndTenantIdOrderByNormalizedName(AccessScope.GLOBAL, null)
        .forEach(group -> globalGroups.put(group.getId(), group));
    List<AccessGroupSubjectEntity> globalSubjects = subjects.findAll().stream()
        .filter(subject -> globalGroups.containsKey(subject.getGroupId())).toList();
    TreeSet<Instant> boundaries = new TreeSet<>();
    boundaries.add(effectiveAt);
    globalRules.forEach(rule -> {
      addFuture(boundaries, rule.getValidFrom(), effectiveAt);
      addFuture(boundaries, rule.getValidUntil(), effectiveAt);
    });
    globalSubjects.forEach(subject -> {
      addFuture(boundaries, subject.getValidFrom(), effectiveAt);
      addFuture(boundaries, subject.getValidUntil(), effectiveAt);
    });
    for (Instant boundary : boundaries) {
      MembershipContinuityDecision decision =
          decideGlobal(globalRules, globalGroups, globalSubjects, boundary);
      if (!decision.sourceAvailable() || !decision.allowed()) return decision;
    }
    return MembershipContinuityDecision.permit();
  }

  private MembershipContinuityDecision decideGlobal(
      List<AccessRuleEntity> globalRules,
      Map<Long, AccessGroupEntity> globalGroups,
      List<AccessGroupSubjectEntity> globalSubjects,
      Instant at) {
    var baseline = baselines.findFirstByScopeAndStatusOrderByBaselineVersionDesc(
        AccessScope.GLOBAL, ProtectedBaselineStatus.ACTIVE).orElse(null);
    if (baseline == null) return MembershipContinuityDecision.unavailable();
    Set<Long> requiredKeyIds = baselineKeys.findByIdBaselineId(baseline.getId()).stream()
        .map(value -> value.getId().getAccessKeyId()).collect(java.util.stream.Collectors.toSet());
    if (requiredKeyIds.isEmpty() || keys.findAllById(requiredKeyIds).size() != requiredKeyIds.size()) {
      return MembershipContinuityDecision.unavailable();
    }
    for (var user : users.findAll()) {
      if (user.getStatus() != UserStatusEnum.ACTIVE
          || methods.inspect(user.getId()).administrativeFactorCount() <= 0) continue;
      Set<Long> groupIds = globalSubjects.stream()
          .filter(subject -> user.getId().equals(subject.getUserId()) && current(subject, at))
          .map(AccessGroupSubjectEntity::getGroupId)
          .filter(groupId -> activeGlobalGroup(globalGroups.get(groupId)))
          .collect(java.util.stream.Collectors.toSet());
      Set<Long> permitted = new HashSet<>();
      Set<Long> blocked = new HashSet<>();
      globalRules.stream().filter(rule -> current(rule, at))
          .filter(rule -> rule.getOriginType() == AccessRuleOriginType.DIRECT_USER
              && user.getId().equals(rule.getUserId())
              || rule.getOriginType() == AccessRuleOriginType.GROUP
                  && groupIds.contains(rule.getAccessGroupId()))
          .filter(rule -> requiredKeyIds.contains(rule.getAccessKeyId()))
          .forEach(rule -> (rule.getEffect() == AccessRuleEffect.BLOQUEAR ? blocked : permitted)
              .add(rule.getAccessKeyId()));
      if (blocked.isEmpty() && permitted.containsAll(requiredKeyIds)) {
        return MembershipContinuityDecision.permit();
      }
    }
    return MembershipContinuityDecision.deny();
  }

  private MembershipContinuityDecision decideAcrossKnownBoundaries(
      long accountId, long tenantId, Instant effectiveAt, Long affectedMembershipId,
      MembershipStatus resultingStatus) {
    TreeSet<Instant> boundaries = new TreeSet<>();
    boundaries.add(effectiveAt);
    rules.findByScopeAndTenantId(AccessScope.TENANT, tenantId).forEach(rule -> {
      addFuture(boundaries, rule.getValidFrom(), effectiveAt);
      addFuture(boundaries, rule.getValidUntil(), effectiveAt);
    });
    memberships.findByAccountIdAndCurrentMarkerOrderById(accountId, 1).forEach(membership ->
        subjects.findByAccountMembershipId(membership.getId()).forEach(subject -> {
          addFuture(boundaries, subject.getValidFrom(), effectiveAt);
          addFuture(boundaries, subject.getValidUntil(), effectiveAt);
        }));
    for (Instant boundary : boundaries) {
      MembershipContinuityDecision decision = decide(
          accountId, tenantId, boundary, affectedMembershipId, resultingStatus);
      if (!decision.sourceAvailable() || !decision.allowed()) return decision;
    }
    return MembershipContinuityDecision.permit();
  }

  private static void addFuture(Set<Instant> boundaries, Instant value, Instant effectiveAt) {
    if (value != null && value.isAfter(effectiveAt)) boundaries.add(value);
  }

  private MembershipContinuityDecision decide(
      long accountId, long tenantId, Instant at, Long affectedMembershipId,
      MembershipStatus resultingStatus) {
    var baseline = baselines.findFirstByScopeAndStatusOrderByBaselineVersionDesc(
        AccessScope.TENANT, ProtectedBaselineStatus.ACTIVE).orElse(null);
    if (baseline == null) return MembershipContinuityDecision.unavailable();
    Set<Long> requiredKeyIds = baselineKeys.findByIdBaselineId(baseline.getId()).stream()
        .map(value -> value.getId().getAccessKeyId()).collect(java.util.stream.Collectors.toSet());
    if (requiredKeyIds.isEmpty() || keys.findAllById(requiredKeyIds).size() != requiredKeyIds.size()) {
      return MembershipContinuityDecision.unavailable();
    }

    List<AccessRuleEntity> tenantRules = rules.findByScopeAndTenantId(AccessScope.TENANT, tenantId);
    Map<Long, AccessGroupEntity> tenantGroups = new HashMap<>();
    groups.findByScopeAndTenantIdOrderByNormalizedName(AccessScope.TENANT, tenantId)
        .forEach(group -> tenantGroups.put(group.getId(), group));
    for (AccountMembershipEntity membership
        : memberships.findByAccountIdAndCurrentMarkerOrderById(accountId, 1)) {
      MembershipStatus status = membership.getId().equals(affectedMembershipId)
          ? resultingStatus : membership.getStatus();
      if (status != MembershipStatus.ACTIVE || !strongActiveIdentity(membership.getUserId())) continue;
      if (allowsEveryRequiredKey(
          membership.getId(), requiredKeyIds, tenantRules, tenantGroups, at)) {
        return MembershipContinuityDecision.permit();
      }
    }
    return MembershipContinuityDecision.deny();
  }

  private boolean strongActiveIdentity(long userId) {
    return users.findById(userId).filter(user -> user.getStatus() == UserStatusEnum.ACTIVE).isPresent()
        && methods.inspect(userId).administrativeFactorCount() > 0;
  }

  private boolean allowsEveryRequiredKey(
      long membershipId,
      Set<Long> requiredKeyIds,
      List<AccessRuleEntity> tenantRules,
      Map<Long, AccessGroupEntity> tenantGroups,
      Instant at) {
    List<AccessGroupSubjectEntity> currentSubjects = subjects.findByAccountMembershipId(membershipId)
        .stream().filter(subject -> current(subject, at)).toList();
    Set<Long> groupIds = currentSubjects.stream().map(AccessGroupSubjectEntity::getGroupId)
        .filter(groupId -> activeTenantGroup(tenantGroups.get(groupId))).collect(java.util.stream.Collectors.toSet());
    Set<Long> permitted = new HashSet<>();
    Set<Long> blocked = new HashSet<>();
    tenantRules.stream().filter(rule -> current(rule, at))
        .filter(rule -> applies(rule, membershipId, groupIds))
        .filter(rule -> requiredKeyIds.contains(rule.getAccessKeyId()))
        .forEach(rule -> (rule.getEffect() == AccessRuleEffect.BLOQUEAR ? blocked : permitted)
            .add(rule.getAccessKeyId()));
    return blocked.isEmpty() && permitted.containsAll(requiredKeyIds);
  }

  private static boolean applies(AccessRuleEntity rule, long membershipId, Set<Long> groupIds) {
    return rule.getOriginType() == AccessRuleOriginType.DIRECT_MEMBERSHIP
        && rule.getAccountMembershipId() == membershipId
        || rule.getOriginType() == AccessRuleOriginType.GROUP
        && groupIds.contains(rule.getAccessGroupId());
  }

  private static boolean activeTenantGroup(AccessGroupEntity group) {
    return group != null && group.getScope() == AccessScope.TENANT
        && group.getStatus() == AccessRecordStatus.ACTIVE;
  }

  private static boolean activeGlobalGroup(AccessGroupEntity group) {
    return group != null && group.getScope() == AccessScope.GLOBAL
        && group.getStatus() == AccessRecordStatus.ACTIVE;
  }

  private static boolean current(AccessRuleEntity rule, Instant at) {
    return rule.getStatus() == AccessRecordStatus.ACTIVE
        && (rule.getValidFrom() == null || !at.isBefore(rule.getValidFrom()))
        && (rule.getValidUntil() == null || at.isBefore(rule.getValidUntil()));
  }

  private static boolean current(AccessGroupSubjectEntity subject, Instant at) {
    return subject.getStatus() == AccessGroupSubjectStatus.ACTIVE
        && (subject.getValidFrom() == null || !at.isBefore(subject.getValidFrom()))
        && (subject.getValidUntil() == null || at.isBefore(subject.getValidUntil()));
  }
}
