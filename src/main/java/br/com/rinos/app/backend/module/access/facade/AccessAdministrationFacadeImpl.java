package br.com.rinos.app.backend.module.access.facade;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.rinos.app.api.module.access.dto.AccessGroupSaveRequest;
import br.com.rinos.app.api.module.access.dto.AccessRuleSaveRequest;
import br.com.rinos.app.api.module.access.dto.AccessGroupSubjectChangeRequest;
import br.com.rinos.app.api.module.access.dto.AccessRecordDeactivateRequest;
import br.com.rinos.app.api.module.access.enums.AccessAdministrationOrigin;
import br.com.rinos.app.api.module.access.enums.AccessAdministrationState;
import br.com.rinos.app.api.module.access.enums.AccessKeyStatus;
import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.access.exception.AccessAdministrationConflictException;
import br.com.rinos.app.api.module.access.facade.AccessAdministrationFacade;
import br.com.rinos.app.api.module.access.vo.AccessAdministrationMutationOutcome;
import br.com.rinos.app.api.module.access.vo.AccessAdministrationPreview;
import br.com.rinos.app.api.module.access.vo.AccessAdministrationCapabilities;
import br.com.rinos.app.api.module.access.vo.AccessAdministrationSnapshot;
import br.com.rinos.app.api.module.access.vo.AccessCategoryItem;
import br.com.rinos.app.api.module.access.vo.AccessGroupItem;
import br.com.rinos.app.api.module.access.vo.AccessGroupSubjectItem;
import br.com.rinos.app.api.module.access.vo.AccessKeyItem;
import br.com.rinos.app.api.module.access.vo.AccessRuleItem;
import br.com.rinos.app.api.module.access.vo.AccessSubjectItem;
import br.com.rinos.app.api.module.access.vo.AuthorizationContext;
import br.com.rinos.app.backend.module.access.entity.AccessGroupEntity;
import br.com.rinos.app.backend.module.access.entity.AccessRuleEntity;
import br.com.rinos.app.backend.module.access.entity.AccessGroupSubjectEntity;
import br.com.rinos.app.backend.module.access.enums.AccessAdministrationAction;
import br.com.rinos.app.backend.module.access.enums.AccessGroupSubjectStatus;
import br.com.rinos.app.backend.module.access.enums.AccessRecordStatus;
import br.com.rinos.app.backend.module.access.enums.AccessRuleOriginType;
import br.com.rinos.app.backend.module.access.repository.AccessGroupRepository;
import br.com.rinos.app.backend.module.access.repository.AccessGroupSubjectRepository;
import br.com.rinos.app.backend.module.access.repository.AccessKeyRepository;
import br.com.rinos.app.backend.module.access.repository.AccessRuleRepository;
import br.com.rinos.app.backend.module.access.service.AccessAdministrationMutationResult;
import br.com.rinos.app.backend.module.access.service.AccessAdministrationMutationService;
import br.com.rinos.app.backend.module.access.service.AccessAdministrationPreviewExecutor;
import br.com.rinos.app.backend.module.access.service.AccessAdministrationPreviewRollback;
import br.com.rinos.app.backend.module.access.service.AccessContextRevisionService;
import br.com.rinos.app.backend.module.access.service.AccessGroupMutationCommand;
import br.com.rinos.app.backend.module.access.service.AccessGroupSubjectMutationCommand;
import br.com.rinos.app.backend.module.access.service.AccessKeyRegistryService;
import br.com.rinos.app.backend.module.access.service.AccessMutationMetadata;
import br.com.rinos.app.backend.module.access.service.AccessRuleMutationCommand;
import br.com.rinos.app.backend.module.access.service.AccessRuleDeactivationCommand;
import br.com.rinos.app.backend.module.access.service.AccessRuleMutationResult;
import br.com.rinos.app.backend.module.access.service.AccessRuleMutationService;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.membership.entity.AccountMembershipEntity;
import br.com.rinos.app.backend.module.membership.repository.AccountMembershipRepository;

/** Implementa a fronteira pública com fotografia contextual e mutações concorrentes seguras. */
@Service
@Lazy
public class AccessAdministrationFacadeImpl implements AccessAdministrationFacade {

  private final AccessKeyRegistryService registry;
  private final AccessContextRevisionService revisions;
  private final AccessGroupRepository groups;
  private final AccessGroupSubjectRepository groupSubjects;
  private final AccessKeyRepository keys;
  private final AccessRuleRepository rules;
  private final AccountMembershipRepository memberships;
  private final UserRepository users;
  private final AccessAdministrationMutationService groupMutations;
  private final AccessRuleMutationService ruleMutations;
  private final AccessAdministrationPreviewExecutor previews;
  private final Clock clock;

  @Autowired
  public AccessAdministrationFacadeImpl(
      AccessKeyRegistryService registry,
      AccessContextRevisionService revisions,
      AccessGroupRepository groups,
      AccessGroupSubjectRepository groupSubjects,
      AccessKeyRepository keys,
      AccessRuleRepository rules,
      AccountMembershipRepository memberships,
      UserRepository users,
      AccessAdministrationMutationService groupMutations,
      AccessRuleMutationService ruleMutations,
      AccessAdministrationPreviewExecutor previews) {
    this(registry, revisions, groups, groupSubjects, keys, rules, memberships, users,
        groupMutations, ruleMutations, previews, Clock.systemUTC());
  }

  AccessAdministrationFacadeImpl(
      AccessKeyRegistryService registry,
      AccessContextRevisionService revisions,
      AccessGroupRepository groups,
      AccessGroupSubjectRepository groupSubjects,
      AccessKeyRepository keys,
      AccessRuleRepository rules,
      AccountMembershipRepository memberships,
      UserRepository users,
      AccessAdministrationMutationService groupMutations,
      AccessRuleMutationService ruleMutations,
      AccessAdministrationPreviewExecutor previews,
      Clock clock) {
    this.registry = registry;
    this.revisions = revisions;
    this.groups = groups;
    this.groupSubjects = groupSubjects;
    this.keys = keys;
    this.rules = rules;
    this.memberships = memberships;
    this.users = users;
    this.groupMutations = groupMutations;
    this.ruleMutations = ruleMutations;
    this.previews = previews;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public AccessAdministrationSnapshot inspect(
      AuthorizationContext context, AccessAdministrationCapabilities capabilities) {
    validateContext(context);
    Objects.requireNonNull(capabilities, "capabilities must not be null");
    if (!capabilities.anyView()) throw new IllegalArgumentException("no visible access section");
    long revision = revisions.current(context.scope(), context.tenantId());
    List<AccessGroupEntity> contextGroups = capabilities.groupView() || capabilities.ruleView()
        ? groups.findByScopeAndTenantIdOrderByNormalizedName(context.scope(), context.tenantId())
        : List.of();
    List<Long> groupIds = contextGroups.stream().map(AccessGroupEntity::getId).toList();
    Map<Long, Long> subjectCounts = new HashMap<>();
    List<AccessGroupSubjectEntity> contextGroupSubjects = groupIds.isEmpty()
        ? List.of() : groupSubjects.findByGroupIdIn(groupIds);
    if (!contextGroupSubjects.isEmpty()) {
      contextGroupSubjects.stream()
          .filter(subject -> subject.getStatus() == AccessGroupSubjectStatus.ACTIVE)
          .forEach(subject -> subjectCounts.merge(subject.getGroupId(), 1L, Long::sum));
    }
    Map<Long, String> keyCodes = new HashMap<>();
    if (capabilities.ruleView()) {
      keys.findAllByOrderByCodeAsc().forEach(key -> keyCodes.put(key.getId(), key.getCode()));
    }
    Instant now = clock.instant();
    List<AccessRuleItem> ruleItems = (capabilities.ruleView()
        ? rules.findByScopeAndTenantId(context.scope(), context.tenantId())
        : List.<AccessRuleEntity>of()).stream()
        .filter(rule -> keyCodes.containsKey(rule.getAccessKeyId()))
        .map(rule -> ruleItem(rule, keyCodes.get(rule.getAccessKeyId()), now))
        .toList();
    return new AccessAdministrationSnapshot(
        context.withRevision(revision), revision, capabilities,
        registry.categories().stream()
            .filter(category -> (capabilities.catalogView() || capabilities.ruleView())
                && category.scope() == context.scope())
            .map(category -> new AccessCategoryItem(
                category.code(), category.parentCode(), category.nameI18nKey()))
            .toList(),
        registry.accessKeys().stream()
            .filter(key -> (capabilities.catalogView() || capabilities.ruleView())
                && key.scope() == context.scope())
            .map(key -> new AccessKeyItem(
                key.code(), key.categoryCode(), key.nameI18nKey(), key.descriptionI18nKey(),
                key.status() == AccessKeyStatus.ACTIVE
                    ? AccessAdministrationState.ACTIVE : AccessAdministrationState.INACTIVE,
                key.minimumAdministrative()))
            .toList(),
        contextGroups.stream().map(group -> new AccessGroupItem(
            group.getId(), group.getName(), group.getDescription(),
            group.getStatus() == AccessRecordStatus.ACTIVE
                ? AccessAdministrationState.ACTIVE : AccessAdministrationState.INACTIVE,
            group.isProtectedGroup(), group.getBaselineVersion(), group.getVersion(),
            subjectCounts.getOrDefault(group.getId(), 0L))).toList(),
        capabilities.groupView() || capabilities.ruleView()
            ? searchSubjects(context, "", 200) : List.of(),
        contextGroupSubjects.stream().map(subject -> new AccessGroupSubjectItem(
            subject.getId(), subject.getGroupId(),
            context.scope() == AccessScope.GLOBAL ? subject.getUserId()
                : subject.getAccountMembershipId(),
            temporalState(subject, now), subject.getValidFrom(), subject.getValidUntil(),
            subject.getVersion())).toList(),
        ruleItems);
  }

  @Override
  @Transactional(readOnly = true)
  public List<AccessSubjectItem> searchSubjects(
      AuthorizationContext context, String query, int limit) {
    validateContext(context);
    if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit is out of range");
    String term = query == null ? "" : query.strip().toLowerCase(java.util.Locale.ROOT);
    if (context.scope() == AccessScope.GLOBAL) {
      return users.findForAccessAdministration(term, PageRequest.of(0, limit)).stream()
          .map(user -> new AccessSubjectItem(
              user.getId(), user.getId(), user.getEmail(), userState(user.getStatus())))
          .toList();
    }
    List<AccountMembershipEntity> candidates =
        memberships.findByAccountIdAndCurrentMarkerOrderById(context.tenantId(), 1);
    Map<Long, UserEntity> byId = new HashMap<>();
    users.findAllById(candidates.stream().map(AccountMembershipEntity::getUserId).toList())
        .forEach(user -> byId.put(user.getId(), user));
    return candidates.stream()
        .filter(membership -> byId.containsKey(membership.getUserId()))
        .filter(membership -> matches(byId.get(membership.getUserId()).getEmail(), term))
        .limit(limit)
        .map(membership -> new AccessSubjectItem(
            membership.getId(), membership.getUserId(), byId.get(membership.getUserId()).getEmail(),
            membership.getStatus() == br.com.rinos.app.api.module.membership.enums.MembershipStatus.ACTIVE
                ? AccessAdministrationState.ACTIVE : AccessAdministrationState.INACTIVE))
        .toList();
  }

  @Override
  @Transactional
  public AccessAdministrationMutationOutcome saveGroup(AccessGroupSaveRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    validateMutation(request.context(), request.expectedRevision(), request.actorUserId(),
        request.correlationId());
    AccessGroupEntity existing = request.groupId() == null ? null
        : groups.findById(request.groupId())
            .filter(group -> group.getScope() == request.context().scope()
                && Objects.equals(group.getTenantId(), request.context().tenantId()))
            .orElseThrow(() -> new IllegalArgumentException("access group is unavailable"));
    AccessAdministrationMutationResult result = groupMutations.mutateGroup(
        new AccessGroupMutationCommand(
            existing == null ? AccessAdministrationAction.CREATE : AccessAdministrationAction.UPDATE,
            request.groupId(), request.context().scope(), request.context().tenantId(),
            request.name(), request.description(), existing != null && existing.isProtectedGroup(),
            existing == null ? null : existing.getBaselineVersion(), metadata(request)));
    return new AccessAdministrationMutationOutcome(
        result.targetId(), result.contextRevision(), result.changed());
  }

  @Override
  @Transactional
  public AccessAdministrationMutationOutcome saveRule(AccessRuleSaveRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    validateMutation(request.context(), request.expectedRevision(), request.actorUserId(),
        request.correlationId());
    AccessRuleOriginType origin = switch (request.origin()) {
      case DIRECT_IDENTITY -> AccessRuleOriginType.DIRECT_USER;
      case DIRECT_MEMBERSHIP -> AccessRuleOriginType.DIRECT_MEMBERSHIP;
      case GROUP -> AccessRuleOriginType.GROUP;
    };
    AccessRuleMutationResult result = ruleMutations.apply(new AccessRuleMutationCommand(
        request.context().scope(), request.context().tenantId(), origin,
        origin == AccessRuleOriginType.DIRECT_USER ? request.originId() : null,
        origin == AccessRuleOriginType.DIRECT_MEMBERSHIP ? request.originId() : null,
        origin == AccessRuleOriginType.GROUP ? request.originId() : null,
        request.accessKeyInternalReference(), request.effect(), request.validFrom(),
        request.validUntil(), request.actorUserId(), null, request.reason(),
        request.correlationId(), clock.instant()));
    return new AccessAdministrationMutationOutcome(
        result.ruleId(), result.contextRevision(), result.changed());
  }

  @Override
  @Transactional
  public AccessAdministrationMutationOutcome changeGroupSubject(
      AccessGroupSubjectChangeRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    validateMutation(request.context(), request.expectedRevision(), request.actorUserId(),
        request.correlationId());
    boolean global = request.context().scope() == AccessScope.GLOBAL;
    AccessAdministrationMutationResult result = groupMutations.mutateSubject(
        new AccessGroupSubjectMutationCommand(
            request.assign() ? AccessAdministrationAction.ASSIGN : AccessAdministrationAction.END,
            request.groupSubjectId(), request.groupId(),
            request.assign() && global ? request.subjectId() : null,
            request.assign() && !global ? request.subjectId() : null,
            request.validFrom(), request.validUntil(),
            new AccessMutationMetadata(request.actorUserId(), null, request.reason(),
                request.correlationId(), clock.instant())));
    return new AccessAdministrationMutationOutcome(
        result.targetId(), result.contextRevision(), result.changed());
  }

  @Override
  @Transactional
  public AccessAdministrationMutationOutcome deactivateGroup(AccessRecordDeactivateRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    validateMutation(request.context(), request.expectedRevision(), request.actorUserId(),
        request.correlationId());
    AccessGroupEntity group = groups.findById(request.targetId())
        .filter(candidate -> candidate.getScope() == request.context().scope()
            && Objects.equals(candidate.getTenantId(), request.context().tenantId()))
        .orElseThrow(() -> new IllegalArgumentException("access group is unavailable"));
    AccessAdministrationMutationResult result = groupMutations.mutateGroup(
        new AccessGroupMutationCommand(AccessAdministrationAction.DEACTIVATE, group.getId(),
            group.getScope(), group.getTenantId(), null, null, group.isProtectedGroup(),
            group.getBaselineVersion(), metadata(request)));
    return new AccessAdministrationMutationOutcome(
        result.targetId(), result.contextRevision(), result.changed());
  }

  @Override
  @Transactional
  public AccessAdministrationMutationOutcome deactivateRule(AccessRecordDeactivateRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    validateMutation(request.context(), request.expectedRevision(), request.actorUserId(),
        request.correlationId());
    AccessRuleMutationResult result = ruleMutations.deactivate(new AccessRuleDeactivationCommand(
        request.targetId(), request.context().scope(), request.context().tenantId(),
        metadata(request)));
    return new AccessAdministrationMutationOutcome(
        result.ruleId(), result.contextRevision(), result.changed());
  }

  @Override
  public AccessAdministrationPreview previewGroup(AccessGroupSaveRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    return preview(request.context(), request.expectedRevision(),
        request.groupId() == null ? "ACCESS_GROUP_CREATE" : "ACCESS_GROUP_UPDATE", false,
        () -> saveGroup(request));
  }

  @Override
  public AccessAdministrationPreview previewRule(AccessRuleSaveRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    boolean baseline = registry.accessKeys().stream()
        .anyMatch(key -> key.code().equals(request.accessKeyInternalReference())
            && key.minimumAdministrative());
    return preview(request.context(), request.expectedRevision(), "ACCESS_RULE_SAVE", baseline,
        () -> saveRule(request));
  }

  @Override
  public AccessAdministrationPreview previewGroupSubject(AccessGroupSubjectChangeRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    Long groupId = request.assign() ? request.groupId()
        : groupSubjects.findById(request.groupSubjectId()).map(AccessGroupSubjectEntity::getGroupId)
            .orElse(null);
    return preview(request.context(), request.expectedRevision(),
        request.assign() ? "ACCESS_GROUP_SUBJECT_ASSIGN" : "ACCESS_GROUP_SUBJECT_END",
        groupUsesAdministrativeBaseline(request.context(), groupId),
        () -> changeGroupSubject(request));
  }

  @Override
  public AccessAdministrationPreview previewGroupDeactivation(
      AccessRecordDeactivateRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    return preview(request.context(), request.expectedRevision(), "ACCESS_GROUP_DEACTIVATE",
        groupUsesAdministrativeBaseline(request.context(), request.targetId()),
        () -> deactivateGroup(request));
  }

  @Override
  public AccessAdministrationPreview previewRuleDeactivation(
      AccessRecordDeactivateRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    boolean baseline = rules.findById(request.targetId()).flatMap(rule ->
        keys.findById(rule.getAccessKeyId())).map(key -> registry.accessKeys().stream()
            .anyMatch(descriptor -> descriptor.code().equals(key.getCode())
                && descriptor.minimumAdministrative())).orElse(false);
    return preview(request.context(), request.expectedRevision(), "ACCESS_RULE_DEACTIVATE",
        baseline, () -> deactivateRule(request));
  }

  private AccessAdministrationPreview preview(
      AuthorizationContext context, long revision, String code, boolean baselineAffected,
      Runnable mutation) {
    try {
      previews.execute(context, revision, code, baselineAffected, mutation);
      throw new IllegalStateException("preview transaction did not roll back");
    } catch (AccessAdministrationPreviewRollback rollback) {
      return rollback.preview();
    }
  }

  private boolean groupUsesAdministrativeBaseline(AuthorizationContext context, Long groupId) {
    if (groupId == null) return false;
    Map<Long, String> codes = new HashMap<>();
    keys.findAllById(rules.findByScopeAndTenantIdAndAccessGroupIdIn(
        context.scope(), context.tenantId(), List.of(groupId)).stream()
        .map(AccessRuleEntity::getAccessKeyId).toList()).forEach(key ->
            codes.put(key.getId(), key.getCode()));
    return registry.accessKeys().stream().filter(key -> key.minimumAdministrative())
        .anyMatch(key -> codes.containsValue(key.code()));
  }

  private void validateMutation(
      AuthorizationContext context, long expectedRevision, long actorUserId,
      String correlationId) {
    validateContext(context);
    if (actorUserId <= 0 || expectedRevision < 0
        || correlationId == null || correlationId.isBlank()) {
      throw new IllegalArgumentException("access mutation request is incomplete");
    }
    long current = revisions.lock(context.scope(), context.tenantId()).getRevision();
    if (current != expectedRevision) throw new AccessAdministrationConflictException();
  }

  private AccessMutationMetadata metadata(AccessGroupSaveRequest request) {
    return new AccessMutationMetadata(request.actorUserId(), null, request.reason(),
        request.correlationId(), clock.instant());
  }

  private AccessMutationMetadata metadata(AccessRecordDeactivateRequest request) {
    return new AccessMutationMetadata(request.actorUserId(), null, request.reason(),
        request.correlationId(), clock.instant());
  }

  private static AccessRuleItem ruleItem(AccessRuleEntity rule, String code, Instant now) {
    AccessAdministrationState state = rule.getStatus() != AccessRecordStatus.ACTIVE
        ? AccessAdministrationState.INACTIVE
        : rule.getValidFrom() != null && now.isBefore(rule.getValidFrom())
            ? AccessAdministrationState.FUTURE
            : rule.getValidUntil() != null && !now.isBefore(rule.getValidUntil())
                ? AccessAdministrationState.EXPIRED
                : rule.getEffect() == br.com.rinos.app.api.module.access.enums.AccessRuleEffect.BLOQUEAR
                    ? AccessAdministrationState.BLOCKED : AccessAdministrationState.ACTIVE;
    return new AccessRuleItem(
        rule.getId(), switch (rule.getOriginType()) {
          case DIRECT_USER -> AccessAdministrationOrigin.DIRECT_IDENTITY;
          case DIRECT_MEMBERSHIP -> AccessAdministrationOrigin.DIRECT_MEMBERSHIP;
          case GROUP -> AccessAdministrationOrigin.GROUP;
        },
        switch (rule.getOriginType()) {
          case DIRECT_USER -> rule.getUserId();
          case DIRECT_MEMBERSHIP -> rule.getAccountMembershipId();
          case GROUP -> rule.getAccessGroupId();
        },
        code, rule.getEffect(), state, rule.getValidFrom(), rule.getValidUntil(), rule.getVersion());
  }

  private static AccessAdministrationState userState(UserStatusEnum status) {
    return switch (status) {
      case ACTIVE -> AccessAdministrationState.ACTIVE;
      case BLOCKED -> AccessAdministrationState.BLOCKED;
      default -> AccessAdministrationState.INACTIVE;
    };
  }

  private static AccessAdministrationState temporalState(
      AccessGroupSubjectEntity subject, Instant now) {
    if (subject.getStatus() != AccessGroupSubjectStatus.ACTIVE) {
      return AccessAdministrationState.INACTIVE;
    }
    if (subject.getValidFrom() != null && now.isBefore(subject.getValidFrom())) {
      return AccessAdministrationState.FUTURE;
    }
    if (subject.getValidUntil() != null && !now.isBefore(subject.getValidUntil())) {
      return AccessAdministrationState.EXPIRED;
    }
    return AccessAdministrationState.ACTIVE;
  }

  private static boolean matches(String value, String term) {
    return term.isEmpty() || value.toLowerCase(java.util.Locale.ROOT).contains(term);
  }

  private static void validateContext(AuthorizationContext context) {
    Objects.requireNonNull(context, "context must not be null");
  }
}
