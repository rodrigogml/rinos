package br.com.rinos.app.backend.module.access.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.access.entity.AccessAuditEventEntity;
import br.com.rinos.app.backend.module.access.entity.AccessGroupEntity;
import br.com.rinos.app.backend.module.access.entity.AccessKeyEntity;
import br.com.rinos.app.backend.module.access.entity.AccessRuleEntity;
import br.com.rinos.app.backend.module.access.entity.AccessRuleHistoryEntity;
import br.com.rinos.app.backend.module.access.entity.ProtectedGroupBaselineKeyId;
import br.com.rinos.app.backend.module.access.enums.AccessRecordStatus;
import br.com.rinos.app.backend.module.access.enums.AccessRuleChangeType;
import br.com.rinos.app.backend.module.access.enums.AccessRuleOriginType;
import br.com.rinos.app.backend.module.access.repository.AccessAuditEventRepository;
import br.com.rinos.app.backend.module.access.repository.AccessGroupRepository;
import br.com.rinos.app.backend.module.access.repository.AccessKeyRepository;
import br.com.rinos.app.backend.module.access.repository.AccessRuleHistoryRepository;
import br.com.rinos.app.backend.module.access.repository.AccessRuleRepository;
import br.com.rinos.app.backend.module.access.repository.ProtectedGroupBaselineKeyRepository;
import br.com.rinos.app.backend.module.access.repository.ProtectedGroupBaselineRepository;

/**
 * Substitui uma regra corrente e grava histórico, auditoria e revisão na mesma transação.
 */
@Service
@org.springframework.context.annotation.Lazy
public class AccessRuleMutationService {

  private final AccessKeyRepository keyRepository;
  private final AccessGroupRepository groupRepository;
  private final AccessRuleRepository ruleRepository;
  private final AccessRuleHistoryRepository historyRepository;
  private final AccessAuditEventRepository auditRepository;
  private final AccessContextRevisionService revisionService;
  private final ProtectedGroupBaselineRepository baselineRepository;
  private final ProtectedGroupBaselineKeyRepository baselineKeyRepository;
  private final AccessContextCacheInvalidationService cacheInvalidationService;
  private final AdministrativeContinuityEvaluator continuity;

  public AccessRuleMutationService(
      AccessKeyRepository keyRepository,
      AccessGroupRepository groupRepository,
      AccessRuleRepository ruleRepository,
      AccessRuleHistoryRepository historyRepository,
      AccessAuditEventRepository auditRepository,
      AccessContextRevisionService revisionService,
      ProtectedGroupBaselineRepository baselineRepository,
      ProtectedGroupBaselineKeyRepository baselineKeyRepository,
      AccessContextCacheInvalidationService cacheInvalidationService,
      AdministrativeContinuityEvaluator continuity) {
    this.keyRepository = keyRepository;
    this.groupRepository = groupRepository;
    this.ruleRepository = ruleRepository;
    this.historyRepository = historyRepository;
    this.auditRepository = auditRepository;
    this.revisionService = revisionService;
    this.baselineRepository = baselineRepository;
    this.baselineKeyRepository = baselineKeyRepository;
    this.cacheInvalidationService = cacheInvalidationService;
    this.continuity = continuity;
  }

  /** Cria a regra ou substitui efeito e vigência preservando sua identidade e seu histórico. */
  @Transactional
  public AccessRuleMutationResult apply(AccessRuleMutationCommand command) {
    validate(command);
    revisionService.lock(command.scope(), command.tenantId());
    AccessKeyEntity key = keyRepository.findByCode(command.accessKeyCode())
        .orElseThrow(() -> new IllegalArgumentException("unknown access key"));
    if (key.getScope() != command.scope() || key.getStatus() != AccessRecordStatus.ACTIVE) {
      throw new IllegalArgumentException("access key is unavailable in the requested context");
    }
    validateGroup(command, key.getId());

    AccessRuleEntity rule = findCurrent(command, key.getId()).orElse(null);
    String previous = rule == null ? null : snapshot(rule);
    if (rule != null && isSameActiveRule(rule, command)) {
      return new AccessRuleMutationResult(rule.getId(),
          revisionService.current(command.scope(), command.tenantId()), false);
    }
    boolean continuityRequired =
        command.effect() == br.com.rinos.app.api.module.access.enums.AccessRuleEffect.BLOQUEAR
            || rule != null && rule.getStatus() == AccessRecordStatus.ACTIVE
                && rule.getEffect()
                    == br.com.rinos.app.api.module.access.enums.AccessRuleEffect.PERMITIR;

    AccessRuleChangeType changeType;
    if (rule == null) {
      rule = new AccessRuleEntity(
          command.scope(), command.tenantId(), command.originType(), command.userId(),
          command.accountMembershipId(), command.accessGroupId(), key.getId(), command.effect(),
          command.validFrom(), command.validUntil(), command.actorUserId());
      changeType = AccessRuleChangeType.CREATE;
    } else {
      changeType = rule.getEffect() == command.effect()
          ? AccessRuleChangeType.VALIDITY_CHANGE
          : AccessRuleChangeType.EFFECT_CHANGE;
      rule.replace(command.effect(), command.validFrom(), command.validUntil(),
          command.actorUserId());
    }
    rule = ruleRepository.saveAndFlush(rule);
    if (continuityRequired) {
      validateContinuity(command.scope(), command.tenantId(), command.occurredAt());
    }
    Instant occurredAt = command.occurredAt();
    historyRepository.save(new AccessRuleHistoryEntity(
        rule.getId(), changeType, previous, snapshot(rule), command.actorUserId(),
        normalize(command.systemOrigin()), normalize(command.reason()), command.correlationId(),
        occurredAt));
    auditRepository.save(new AccessAuditEventEntity(
        "ACCESS_RULE_" + changeType.name(), command.scope(), command.tenantId(),
        command.actorUserId(), normalize(command.systemOrigin()), "ACCESS_RULE", rule.getId(),
        command.correlationId(), null, null, occurredAt));
    long revision = revisionService.lockAndIncrement(command.scope(), command.tenantId());
    cacheInvalidationService.afterCommit(command.scope(), command.tenantId());
    return new AccessRuleMutationResult(rule.getId(), revision, true);
  }

  /** Desativa logicamente uma regra, preservando a linha corrente e o histórico append-only. */
  @Transactional
  public AccessRuleMutationResult deactivate(AccessRuleDeactivationCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    Objects.requireNonNull(command.scope(), "scope must not be null");
    Objects.requireNonNull(command.metadata(), "metadata must not be null");
    if (command.ruleId() <= 0
        || command.scope() == AccessScope.GLOBAL && command.tenantId() != null
        || command.scope() == AccessScope.TENANT
            && (command.tenantId() == null || command.tenantId() <= 0)) {
      throw new IllegalArgumentException("access rule deactivation is inconsistent");
    }
    revisionService.lock(command.scope(), command.tenantId());
    AccessRuleEntity rule = ruleRepository.findById(command.ruleId())
        .filter(candidate -> candidate.getScope() == command.scope()
            && Objects.equals(candidate.getTenantId(), command.tenantId()))
        .orElseThrow(() -> new IllegalArgumentException(
            "access rule is unavailable in the requested context"));
    validateProtectedRuleDeactivation(rule);
    if (rule.getStatus() == AccessRecordStatus.INACTIVE) {
      return new AccessRuleMutationResult(rule.getId(),
          revisionService.current(rule.getScope(), rule.getTenantId()), false);
    }
    String previous = snapshot(rule);
    boolean continuityRequired = rule.getEffect()
            == br.com.rinos.app.api.module.access.enums.AccessRuleEffect.PERMITIR;
    rule.deactivate(command.metadata().actorUserId());
    ruleRepository.saveAndFlush(rule);
    if (continuityRequired) {
      validateContinuity(rule.getScope(), rule.getTenantId(), command.metadata().occurredAt());
    }
    historyRepository.save(new AccessRuleHistoryEntity(
        rule.getId(), AccessRuleChangeType.DEACTIVATE, previous, snapshot(rule),
        command.metadata().actorUserId(), command.metadata().systemOrigin(),
        command.metadata().reason(), command.metadata().correlationId(),
        command.metadata().occurredAt()));
    auditRepository.save(new AccessAuditEventEntity(
        "ACCESS_RULE_DEACTIVATE", rule.getScope(), rule.getTenantId(),
        command.metadata().actorUserId(), command.metadata().systemOrigin(), "ACCESS_RULE",
        rule.getId(), command.metadata().correlationId(), null, null,
        command.metadata().occurredAt()));
    long revision = revisionService.lockAndIncrement(rule.getScope(), rule.getTenantId());
    cacheInvalidationService.afterCommit(rule.getScope(), rule.getTenantId());
    return new AccessRuleMutationResult(rule.getId(), revision, true);
  }

  private java.util.Optional<AccessRuleEntity> findCurrent(
      AccessRuleMutationCommand command, Long keyId) {
    return switch (command.originType()) {
      case DIRECT_USER -> ruleRepository.findByScopeAndTenantIdAndUserIdAndAccessKeyId(
          command.scope(), null, command.userId(), keyId);
      case DIRECT_MEMBERSHIP ->
          ruleRepository.findByScopeAndTenantIdAndAccountMembershipIdAndAccessKeyId(
              command.scope(), command.tenantId(), command.accountMembershipId(), keyId);
      case GROUP -> ruleRepository.findByScopeAndTenantIdAndAccessGroupIdAndAccessKeyId(
          command.scope(), command.tenantId(), command.accessGroupId(), keyId);
    };
  }

  private void validateGroup(AccessRuleMutationCommand command, Long accessKeyId) {
    if (command.originType() != AccessRuleOriginType.GROUP) {
      return;
    }
    AccessGroupEntity group = groupRepository.findById(command.accessGroupId())
        .orElseThrow(() -> new IllegalArgumentException("unknown access group"));
    if (group.getScope() != command.scope()
        || !Objects.equals(group.getTenantId(), command.tenantId())
        || group.getStatus() != AccessRecordStatus.ACTIVE) {
      throw new IllegalArgumentException("access group is unavailable in the requested context");
    }
    boolean ownBaselineKey = group.isProtectedGroup()
        && baselineRepository.findByScopeAndBaselineVersion(
            group.getScope(), group.getBaselineVersion())
            .map(baseline -> baselineKeyRepository.existsById(
                new ProtectedGroupBaselineKeyId(baseline.getId(), accessKeyId)))
            .orElseThrow(() -> new IllegalStateException("protected baseline is unavailable"));
    if (ownBaselineKey
        && (command.effect()
                == br.com.rinos.app.api.module.access.enums.AccessRuleEffect.BLOQUEAR
            || command.validFrom() != null || command.validUntil() != null)) {
      throw new IllegalArgumentException("protected groups cannot weaken their baseline keys");
    }
  }

  private void validateProtectedRuleDeactivation(AccessRuleEntity rule) {
    if (rule.getOriginType() != AccessRuleOriginType.GROUP) {
      return;
    }
    AccessGroupEntity group = groupRepository.findById(rule.getAccessGroupId())
        .orElseThrow(() -> new IllegalStateException("access group is unavailable"));
    if (!group.isProtectedGroup()) {
      return;
    }
    boolean baselineKey = baselineRepository.findByScopeAndBaselineVersion(
        group.getScope(), group.getBaselineVersion())
        .map(baseline -> baselineKeyRepository.existsById(
            new ProtectedGroupBaselineKeyId(baseline.getId(), rule.getAccessKeyId())))
        .orElseThrow(() -> new IllegalStateException("protected baseline is unavailable"));
    if (baselineKey) {
      throw new IllegalArgumentException(
          "protected groups cannot lose their baseline rules");
    }
  }

  private String snapshot(AccessRuleEntity rule) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("scope", rule.getScope().name());
    values.put("tenantId", rule.getTenantId());
    values.put("originType", rule.getOriginType().name());
    values.put("userId", rule.getUserId());
    values.put("accountMembershipId", rule.getAccountMembershipId());
    values.put("accessGroupId", rule.getAccessGroupId());
    values.put("accessKeyId", rule.getAccessKeyId());
    values.put("effect", rule.getEffect().name());
    values.put("status", rule.getStatus().name());
    values.put("validFrom", rule.getValidFrom());
    values.put("validUntil", rule.getValidUntil());
    StringBuilder json = new StringBuilder("{");
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      if (json.length() > 1) {
        json.append(',');
      }
      json.append('"').append(entry.getKey()).append("\":");
      Object value = entry.getValue();
      if (value == null || value instanceof Number) {
        json.append(value);
      } else {
        json.append('"').append(value).append('"');
      }
    }
    return json.append('}').toString();
  }

  private static boolean isSameActiveRule(
      AccessRuleEntity rule, AccessRuleMutationCommand command) {
    return rule.getStatus() == AccessRecordStatus.ACTIVE
        && rule.getEffect() == command.effect()
        && Objects.equals(rule.getValidFrom(), command.validFrom())
        && Objects.equals(rule.getValidUntil(), command.validUntil());
  }

  private static void validate(AccessRuleMutationCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    if (command.scope() == null || command.originType() == null || command.effect() == null
        || command.occurredAt() == null || isBlank(command.accessKeyCode())
        || isBlank(command.correlationId())) {
      throw new IllegalArgumentException("access rule mutation is incomplete");
    }
    boolean global = command.scope() == AccessScope.GLOBAL;
    if (global != (command.tenantId() == null)
        || !global && command.tenantId() <= 0
        || command.validFrom() != null && command.validUntil() != null
            && !command.validUntil().isAfter(command.validFrom())
        || (command.actorUserId() == null) == isBlank(command.systemOrigin())) {
      throw new IllegalArgumentException("access rule mutation is inconsistent");
    }
    boolean validOrigin = switch (command.originType()) {
      case DIRECT_USER -> global && command.userId() != null
          && command.accountMembershipId() == null && command.accessGroupId() == null;
      case DIRECT_MEMBERSHIP -> !global && command.userId() == null
          && command.accountMembershipId() != null && command.accountMembershipId() > 0
          && command.accessGroupId() == null;
      case GROUP -> command.userId() == null && command.accountMembershipId() == null
          && command.accessGroupId() != null;
    };
    if (!validOrigin) {
      throw new IllegalArgumentException("access rule origin is inconsistent");
    }
  }

  private static String normalize(String value) {
    return isBlank(value) ? null : value.strip();
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private void validateContinuity(AccessScope scope, Long tenantId, Instant at) {
    var decision = continuity.evaluateContext(scope, tenantId, at);
    if (!decision.sourceAvailable()) throw new IllegalStateException("administrative continuity is unavailable");
    if (!decision.allowed()) throw new IllegalArgumentException("administrative continuity would be lost");
  }

}
