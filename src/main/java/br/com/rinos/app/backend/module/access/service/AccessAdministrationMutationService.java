package br.com.rinos.app.backend.module.access.service;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.access.entity.AccessAuditEventEntity;
import br.com.rinos.app.backend.module.access.entity.AccessGroupEntity;
import br.com.rinos.app.backend.module.access.entity.AccessGroupSubjectEntity;
import br.com.rinos.app.backend.module.access.enums.AccessAdministrationAction;
import br.com.rinos.app.backend.module.access.enums.AccessGroupSubjectStatus;
import br.com.rinos.app.backend.module.access.enums.AccessRecordStatus;
import br.com.rinos.app.backend.module.access.enums.ProtectedBaselineStatus;
import br.com.rinos.app.backend.module.access.repository.AccessAuditEventRepository;
import br.com.rinos.app.backend.module.access.repository.AccessGroupRepository;
import br.com.rinos.app.backend.module.access.repository.AccessGroupSubjectRepository;
import br.com.rinos.app.backend.module.access.repository.ProtectedGroupBaselineRepository;

/** Mantém grupos e seus sujeitos com auditoria, revisão e invalidação atômicas. */
@Service
@org.springframework.context.annotation.Lazy
public class AccessAdministrationMutationService {

  private final AccessGroupRepository groupRepository;
  private final AccessGroupSubjectRepository subjectRepository;
  private final ProtectedGroupBaselineRepository baselineRepository;
  private final AccessAuditEventRepository auditRepository;
  private final AccessContextRevisionService revisionService;
  private final AccessContextCacheInvalidationService cacheInvalidationService;
  private final AccountMembershipAccessPort membershipAccessPort;
  private final AdministrativeContinuityEvaluator continuity;

  public AccessAdministrationMutationService(
      AccessGroupRepository groupRepository,
      AccessGroupSubjectRepository subjectRepository,
      ProtectedGroupBaselineRepository baselineRepository,
      AccessAuditEventRepository auditRepository,
      AccessContextRevisionService revisionService,
      AccessContextCacheInvalidationService cacheInvalidationService,
      AccountMembershipAccessPort membershipAccessPort,
      AdministrativeContinuityEvaluator continuity) {
    this.groupRepository = groupRepository;
    this.subjectRepository = subjectRepository;
    this.baselineRepository = baselineRepository;
    this.auditRepository = auditRepository;
    this.revisionService = revisionService;
    this.cacheInvalidationService = cacheInvalidationService;
    this.membershipAccessPort = membershipAccessPort;
    this.continuity = continuity;
  }

  @Transactional
  public AccessAdministrationMutationResult mutateGroup(AccessGroupMutationCommand command) {
    validateGroupCommand(command);
    revisionService.lock(command.scope(), command.tenantId());
    return switch (command.action()) {
      case CREATE -> createGroup(command);
      case UPDATE -> updateGroup(command);
      case DEACTIVATE -> deactivateGroup(command);
      default -> throw new IllegalArgumentException("unsupported group action");
    };
  }

  @Transactional
  public AccessAdministrationMutationResult mutateSubject(
      AccessGroupSubjectMutationCommand command) {
    validateSubjectCommand(command);
    AccessMutationContext context = command.action() == AccessAdministrationAction.ASSIGN
        ? groupRepository.findMutationContext(command.groupId()).orElseThrow(
            () -> new IllegalArgumentException("access group is unavailable"))
        : subjectRepository.findMutationContext(command.groupSubjectId()).orElseThrow(
            () -> new IllegalArgumentException("access group subject is unavailable"));
    revisionService.lock(context.scope(), context.tenantId());
    return switch (command.action()) {
      case ASSIGN -> assignSubject(command);
      case END -> endSubject(command);
      default -> throw new IllegalArgumentException("unsupported group subject action");
    };
  }

  private AccessAdministrationMutationResult createGroup(AccessGroupMutationCommand command) {
    String name = normalizeDisplayName(command.name());
    String normalizedName = normalizeName(name);
    ensureNameAvailable(command.scope(), command.tenantId(), normalizedName, null);
    validateBaseline(command.scope(), command.protectedGroup(), command.baselineVersion());
    AccessGroupEntity group = groupRepository.saveAndFlush(new AccessGroupEntity(
        command.scope(), command.tenantId(), name, normalizedName,
        normalizeDescription(command.description()), command.protectedGroup(),
        command.baselineVersion()));
    return changed(command.scope(), command.tenantId(), "ACCESS_GROUP_CREATE", "ACCESS_GROUP",
        group.getId(), null, groupSnapshot(group), command.metadata());
  }

  private AccessAdministrationMutationResult updateGroup(AccessGroupMutationCommand command) {
    AccessGroupEntity group = requireGroup(command.groupId(), command.scope(), command.tenantId());
    if (group.isProtectedGroup() != command.protectedGroup()
        || !Objects.equals(group.getBaselineVersion(), command.baselineVersion())) {
      throw new IllegalArgumentException("group protection and baseline are immutable");
    }
    String name = normalizeDisplayName(command.name());
    String normalizedName = normalizeName(name);
    String description = normalizeDescription(command.description());
    ensureNameAvailable(group.getScope(), group.getTenantId(), normalizedName, group.getId());
    if (group.getName().equals(name) && group.getNormalizedName().equals(normalizedName)
        && Objects.equals(group.getDescription(), description)) {
      return unchanged(group.getId(), group.getScope(), group.getTenantId());
    }
    String previous = groupSnapshot(group);
    group.update(name, normalizedName, description);
    groupRepository.saveAndFlush(group);
    return changed(group.getScope(), group.getTenantId(), "ACCESS_GROUP_UPDATE", "ACCESS_GROUP",
        group.getId(), previous, groupSnapshot(group), command.metadata());
  }

  private AccessAdministrationMutationResult deactivateGroup(AccessGroupMutationCommand command) {
    AccessGroupEntity group = requireGroup(command.groupId(), command.scope(), command.tenantId());
    if (group.isProtectedGroup()) {
      throw new IllegalArgumentException(
          "protected groups cannot be deactivated");
    }
    if (group.getStatus() == AccessRecordStatus.INACTIVE) {
      return unchanged(group.getId(), group.getScope(), group.getTenantId());
    }
    String previous = groupSnapshot(group);
    group.deactivate();
    groupRepository.saveAndFlush(group);
    validateContinuity(group.getScope(), group.getTenantId(), command.metadata().occurredAt());
    return changed(group.getScope(), group.getTenantId(), "ACCESS_GROUP_DEACTIVATE",
        "ACCESS_GROUP", group.getId(), previous, groupSnapshot(group), command.metadata());
  }

  private AccessAdministrationMutationResult assignSubject(
      AccessGroupSubjectMutationCommand command) {
    AccessGroupEntity group = groupRepository.findById(command.groupId())
        .filter(candidate -> candidate.getStatus() == AccessRecordStatus.ACTIVE)
        .orElseThrow(() -> new IllegalArgumentException("access group is unavailable"));
    validateSubjectForGroup(command, group);
    AccessGroupSubjectEntity subject = group.getScope() == AccessScope.GLOBAL
        ? subjectRepository.findByGroupIdAndUserId(group.getId(), command.userId()).orElse(null)
        : subjectRepository.findByGroupIdAndAccountMembershipId(
            group.getId(), command.accountMembershipId()).orElse(null);
    if (subject != null && subject.getStatus() == AccessGroupSubjectStatus.ACTIVE
        && Objects.equals(subject.getValidFrom(), command.validFrom())
        && Objects.equals(subject.getValidUntil(), command.validUntil())) {
      return unchanged(subject.getId(), group.getScope(), group.getTenantId());
    }
    String previous = subject == null ? null : subjectSnapshot(subject);
    if (subject == null) {
      subject = new AccessGroupSubjectEntity(
          group.getId(), command.userId(), command.accountMembershipId(), command.validFrom(),
          command.validUntil(), command.metadata().actorUserId());
    } else {
      subject.replaceValidity(command.validFrom(), command.validUntil());
    }
    subject = subjectRepository.saveAndFlush(subject);
    return changed(group.getScope(), group.getTenantId(), "ACCESS_GROUP_SUBJECT_ASSIGN",
        "ACCESS_GROUP_SUBJECT", subject.getId(), previous, subjectSnapshot(subject),
        command.metadata());
  }

  private AccessAdministrationMutationResult endSubject(
      AccessGroupSubjectMutationCommand command) {
    AccessGroupSubjectEntity subject = subjectRepository.findById(command.groupSubjectId())
        .orElseThrow(() -> new IllegalArgumentException("unknown access group subject"));
    AccessGroupEntity group = groupRepository.findById(subject.getGroupId())
        .orElseThrow(() -> new IllegalStateException("access group is unavailable"));
    if (subject.getStatus() == AccessGroupSubjectStatus.ENDED) {
      return unchanged(subject.getId(), group.getScope(), group.getTenantId());
    }
    String previous = subjectSnapshot(subject);
    subject.end();
    subjectRepository.saveAndFlush(subject);
    validateContinuity(group.getScope(), group.getTenantId(), command.metadata().occurredAt());
    return changed(group.getScope(), group.getTenantId(), "ACCESS_GROUP_SUBJECT_END",
        "ACCESS_GROUP_SUBJECT", subject.getId(), previous, subjectSnapshot(subject),
        command.metadata());
  }

  private void validateSubjectForGroup(
      AccessGroupSubjectMutationCommand command, AccessGroupEntity group) {
    if (group.getScope() == AccessScope.GLOBAL) {
      if (command.userId() == null || command.userId() <= 0
          || command.accountMembershipId() != null) {
        throw new IllegalArgumentException("global group requires a global identity");
      }
      return;
    }
    if (command.userId() != null || command.accountMembershipId() == null
        || command.accountMembershipId() <= 0) {
      throw new IllegalArgumentException("tenant group requires an account membership");
    }
    AccountMembershipAccessSnapshot membership =
        membershipAccessPort.inspect(command.accountMembershipId());
    if (!membership.sourceAvailable()) {
      throw new IllegalStateException("account membership source is unavailable");
    }
    if (!membership.exists() || !group.getTenantId().equals(membership.tenantId())) {
      throw new IllegalArgumentException("account membership belongs to another context");
    }
  }

  private AccessAdministrationMutationResult changed(
      AccessScope scope, Long tenantId, String eventType, String targetType, Long targetId,
      String previous, String current, AccessMutationMetadata metadata) {
    auditRepository.save(new AccessAuditEventEntity(
        eventType, scope, tenantId, metadata.actorUserId(), metadata.systemOrigin(), targetType,
        targetId, metadata.correlationId(), null,
        "{\"previous\":" + nullableJson(previous) + ",\"current\":" + current + "}",
        metadata.occurredAt()));
    long revision = revisionService.lockAndIncrement(scope, tenantId);
    cacheInvalidationService.afterCommit(scope, tenantId);
    return new AccessAdministrationMutationResult(targetId, revision, true);
  }

  private void validateContinuity(AccessScope scope, Long tenantId, Instant at) {
    var decision = continuity.evaluateContext(scope, tenantId, at);
    if (!decision.sourceAvailable()) throw new IllegalStateException("administrative continuity is unavailable");
    if (!decision.allowed()) throw new IllegalArgumentException("administrative continuity would be lost");
  }

  private AccessAdministrationMutationResult unchanged(
      long targetId, AccessScope scope, Long tenantId) {
    return new AccessAdministrationMutationResult(
        targetId, revisionService.current(scope, tenantId), false);
  }

  private AccessGroupEntity requireGroup(long groupId, AccessScope scope, Long tenantId) {
    return groupRepository.findById(groupId)
        .filter(group -> group.getScope() == scope && Objects.equals(group.getTenantId(), tenantId))
        .orElseThrow(() -> new IllegalArgumentException(
            "access group is unavailable in the requested context"));
  }

  private void ensureNameAvailable(
      AccessScope scope, Long tenantId, String normalizedName, Long currentId) {
    groupRepository.findByScopeAndTenantIdAndNormalizedName(scope, tenantId, normalizedName)
        .filter(group -> !group.getId().equals(currentId))
        .ifPresent(group -> {
          throw new IllegalArgumentException("access group name already exists in context");
        });
  }

  private void validateBaseline(
      AccessScope scope, boolean protectedGroup, Integer baselineVersion) {
    if (protectedGroup != (baselineVersion != null) || baselineVersion != null && baselineVersion <= 0) {
      throw new IllegalArgumentException("protected group baseline is inconsistent");
    }
    if (protectedGroup) {
      baselineRepository.findByScopeAndBaselineVersion(scope, baselineVersion)
          .filter(baseline -> baseline.getStatus() == ProtectedBaselineStatus.ACTIVE)
          .orElseThrow(() -> new IllegalArgumentException("protected baseline is unavailable"));
    }
  }

  private static void validateGroupCommand(AccessGroupMutationCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    Objects.requireNonNull(command.action(), "action must not be null");
    Objects.requireNonNull(command.scope(), "scope must not be null");
    Objects.requireNonNull(command.metadata(), "metadata must not be null");
    boolean global = command.scope() == AccessScope.GLOBAL;
    if (global != (command.tenantId() == null)
        || !global && command.tenantId() <= 0
        || command.action() == AccessAdministrationAction.CREATE && command.groupId() != null
        || command.action() != AccessAdministrationAction.CREATE
            && (command.groupId() == null || command.groupId() <= 0)
        || command.action() != AccessAdministrationAction.DEACTIVATE
            && (command.name() == null || command.name().isBlank())) {
      throw new IllegalArgumentException("access group mutation is inconsistent");
    }
  }

  private static void validateSubjectCommand(AccessGroupSubjectMutationCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    Objects.requireNonNull(command.action(), "action must not be null");
    Objects.requireNonNull(command.metadata(), "metadata must not be null");
    if (command.validFrom() != null && command.validUntil() != null
        && !command.validUntil().isAfter(command.validFrom())
        || command.action() == AccessAdministrationAction.ASSIGN
            && (command.groupSubjectId() != null || command.groupId() == null
                || command.groupId() <= 0)
        || command.action() == AccessAdministrationAction.END
            && (command.groupSubjectId() == null || command.groupSubjectId() <= 0)
        || command.action() != AccessAdministrationAction.ASSIGN
            && command.action() != AccessAdministrationAction.END) {
      throw new IllegalArgumentException("access group subject mutation is inconsistent");
    }
  }

  private static String normalizeDisplayName(String value) {
    String normalized = value.strip().replaceAll("\\s+", " ");
    if (normalized.isEmpty() || normalized.length() > 160) {
      throw new IllegalArgumentException("access group name is invalid");
    }
    return normalized;
  }

  private static String normalizeName(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
  }

  private static String normalizeDescription(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.strip();
    if (normalized.length() > 500) {
      throw new IllegalArgumentException("access group description is invalid");
    }
    return normalized;
  }

  private static String groupSnapshot(AccessGroupEntity group) {
    return "{\"name\":" + quote(group.getName())
        + ",\"description\":" + nullableQuote(group.getDescription())
        + ",\"status\":" + quote(group.getStatus().name())
        + ",\"protected\":" + group.isProtectedGroup()
        + ",\"baselineVersion\":" + group.getBaselineVersion() + "}";
  }

  private static String subjectSnapshot(AccessGroupSubjectEntity subject) {
    return "{\"groupId\":" + subject.getGroupId()
        + ",\"userId\":" + subject.getUserId()
        + ",\"membershipId\":" + subject.getAccountMembershipId()
        + ",\"status\":" + quote(subject.getStatus().name())
        + ",\"validFrom\":" + nullableInstant(subject.getValidFrom())
        + ",\"validUntil\":" + nullableInstant(subject.getValidUntil()) + "}";
  }

  private static String nullableJson(String json) {
    return json == null ? "null" : json;
  }

  private static String nullableQuote(String value) {
    return value == null ? "null" : quote(value);
  }

  private static String nullableInstant(Instant value) {
    return value == null ? "null" : quote(value.toString());
  }

  private static String quote(String value) {
    StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
    value.codePoints().forEach(codePoint -> {
      switch (codePoint) {
        case '"' -> escaped.append("\\\"");
        case '\\' -> escaped.append("\\\\");
        case '\b' -> escaped.append("\\b");
        case '\f' -> escaped.append("\\f");
        case '\n' -> escaped.append("\\n");
        case '\r' -> escaped.append("\\r");
        case '\t' -> escaped.append("\\t");
        default -> {
          if (codePoint < 0x20) {
            escaped.append(String.format("\\u%04x", codePoint));
          } else {
            escaped.appendCodePoint(codePoint);
          }
        }
      }
    });
    return escaped.append('"').toString();
  }
}
