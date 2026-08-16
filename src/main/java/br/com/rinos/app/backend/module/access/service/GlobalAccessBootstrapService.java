package br.com.rinos.app.backend.module.access.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.module.access.enums.AccessRuleEffect;
import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.access.entity.AccessAuditEventEntity;
import br.com.rinos.app.backend.module.access.enums.AccessAdministrationAction;
import br.com.rinos.app.backend.module.access.enums.AccessBootstrapStatus;
import br.com.rinos.app.backend.module.access.enums.AccessRecordStatus;
import br.com.rinos.app.backend.module.access.enums.AccessRuleOriginType;
import br.com.rinos.app.backend.module.access.enums.GlobalAccessBootstrapStatus;
import br.com.rinos.app.backend.module.access.enums.ProtectedBaselineStatus;
import br.com.rinos.app.backend.module.access.repository.AccessAuditEventRepository;
import br.com.rinos.app.backend.module.access.repository.AccessBootstrapRepository;
import br.com.rinos.app.backend.module.access.repository.AccessGroupRepository;
import br.com.rinos.app.backend.module.access.repository.AccessKeyRepository;
import br.com.rinos.app.backend.module.access.repository.ProtectedGroupBaselineKeyRepository;
import br.com.rinos.app.backend.module.access.repository.ProtectedGroupBaselineRepository;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.service.AuthenticationMethodInventoryService;
import br.com.rinos.app.backend.module.identity.service.EmailNormalizationService;
import br.com.rinos.app.config.AccessBootstrapPropertiesConfig;

/** Conclui no máximo uma vez o bootstrap administrativo global definido por propriedade. */
@Service
@org.springframework.context.annotation.Lazy
public class GlobalAccessBootstrapService {

  public static final String SYSTEM_ORIGIN = "ACCESS_BOOTSTRAP_PROPERTY_FILE";
  private static final long SINGLETON_ID = 1L;

  private final AccessBootstrapPropertiesConfig properties;
  private final EmailNormalizationService emails;
  private final AccessContextRevisionService revisions;
  private final AccessBootstrapRepository bootstraps;
  private final UserRepository users;
  private final AuthenticationMethodInventoryService methods;
  private final ProtectedGroupBaselineRepository baselines;
  private final ProtectedGroupBaselineKeyRepository baselineKeys;
  private final AccessKeyRepository keys;
  private final AccessGroupRepository groups;
  private final AccessAdministrationMutationService administration;
  private final AccessRuleMutationService ruleMutations;
  private final AdministrativeContinuityEvaluator continuity;
  private final AccessAuditEventRepository audits;

  public GlobalAccessBootstrapService(
      AccessBootstrapPropertiesConfig properties,
      EmailNormalizationService emails,
      AccessContextRevisionService revisions,
      AccessBootstrapRepository bootstraps,
      UserRepository users,
      AuthenticationMethodInventoryService methods,
      ProtectedGroupBaselineRepository baselines,
      ProtectedGroupBaselineKeyRepository baselineKeys,
      AccessKeyRepository keys,
      AccessGroupRepository groups,
      AccessAdministrationMutationService administration,
      AccessRuleMutationService ruleMutations,
      AdministrativeContinuityEvaluator continuity,
      AccessAuditEventRepository audits) {
    this.properties = properties;
    this.emails = emails;
    this.revisions = revisions;
    this.bootstraps = bootstraps;
    this.users = users;
    this.methods = methods;
    this.baselines = baselines;
    this.baselineKeys = baselineKeys;
    this.keys = keys;
    this.groups = groups;
    this.administration = administration;
    this.ruleMutations = ruleMutations;
    this.continuity = continuity;
    this.audits = audits;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public GlobalAccessBootstrapResult attempt(UUID correlationId, Instant occurredAt) {
    if (correlationId == null || occurredAt == null) {
      throw new IllegalArgumentException("bootstrap metadata is incomplete");
    }
    String correlation = correlationId.toString();
    revisions.lock(AccessScope.GLOBAL, null);
    var marker = bootstraps.findSingletonForUpdate()
        .orElseThrow(() -> new IllegalStateException("access bootstrap marker is unavailable"));
    if (marker.getStatus() == AccessBootstrapStatus.COMPLETED) {
      return new GlobalAccessBootstrapResult(
          GlobalAccessBootstrapStatus.ALREADY_COMPLETED,
          marker.getCompletedByUserId(), null);
    }

    String normalizedEmail = emails.normalize(properties.administratorEmail()).normalizedEmail();
    var candidate = users.findByNormalizedEmailForUpdate(normalizedEmail).orElse(null);
    if (candidate == null || candidate.getStatus() != UserStatusEnum.ACTIVE
        || candidate.getActivatedAt() == null
        || methods.inspect(candidate.getId()).administrativeFactorCount() <= 0) {
      return new GlobalAccessBootstrapResult(
          GlobalAccessBootstrapStatus.CANDIDATE_NOT_READY, null, null);
    }

    var baseline = baselines.findFirstByScopeAndStatusOrderByBaselineVersionDesc(
        AccessScope.GLOBAL, ProtectedBaselineStatus.ACTIVE)
        .orElseThrow(() -> new IllegalStateException("global protected baseline is unavailable"));
    var baselineEntries = baselineKeys.findByIdBaselineId(baseline.getId());
    if (baselineEntries.isEmpty()) {
      throw new IllegalStateException("global protected baseline is empty");
    }
    var keyEntities = keys.findAllById(baselineEntries.stream()
        .map(entry -> entry.getId().getAccessKeyId()).toList());
    if (keyEntities.size() != baselineEntries.size()
        || keyEntities.stream().anyMatch(key -> key.getScope() != AccessScope.GLOBAL
            || key.getStatus() != AccessRecordStatus.ACTIVE)) {
      throw new IllegalStateException("global protected baseline is inconsistent");
    }

    AccessMutationMetadata metadata = new AccessMutationMetadata(
        null, SYSTEM_ORIGIN, "initial global administrative bootstrap", correlation, occurredAt);
    Long groupId = groups
        .findByScopeAndTenantIdAndProtectedGroupTrueAndBaselineVersion(
            AccessScope.GLOBAL, null, baseline.getBaselineVersion())
        .map(group -> {
          if (group.getStatus() != AccessRecordStatus.ACTIVE) {
            throw new IllegalStateException("global protected group is inactive");
          }
          return group.getId();
        })
        .orElseGet(() -> administration.mutateGroup(new AccessGroupMutationCommand(
            AccessAdministrationAction.CREATE, null, AccessScope.GLOBAL, null,
            "Administradores do sistema", "Bootstrap administrativo global protegido",
            true, baseline.getBaselineVersion(), metadata)).targetId());

    keyEntities.stream().sorted(Comparator.comparing(value -> value.getCode())).forEach(key ->
        ruleMutations.apply(new AccessRuleMutationCommand(
            AccessScope.GLOBAL, null, AccessRuleOriginType.GROUP, null, null, groupId,
            key.getCode(), AccessRuleEffect.PERMITIR, null, null, null, SYSTEM_ORIGIN,
            "global protected baseline", correlation, occurredAt)));
    administration.mutateSubject(new AccessGroupSubjectMutationCommand(
        AccessAdministrationAction.ASSIGN, null, groupId, candidate.getId(), null,
        null, null, metadata));

    candidate.identifyAsSystemAdministrator();
    users.saveAndFlush(candidate);
    var decision = continuity.evaluateContext(AccessScope.GLOBAL, null, occurredAt);
    if (!decision.sourceAvailable() || !decision.allowed()) {
      throw new IllegalStateException("global bootstrap did not establish continuity");
    }
    marker.complete(candidate.getId(), occurredAt, correlation);
    bootstraps.saveAndFlush(marker);
    audits.save(new AccessAuditEventEntity(
        "ACCESS_BOOTSTRAP_COMPLETED", AccessScope.GLOBAL, null, null, SYSTEM_ORIGIN,
        "ACCESS_BOOTSTRAP", SINGLETON_ID, correlation, null,
        "{\"administratorUserId\":" + candidate.getId()
            + ",\"protectedGroupId\":" + groupId + "}", occurredAt));
    return new GlobalAccessBootstrapResult(
        GlobalAccessBootstrapStatus.COMPLETED, candidate.getId(), groupId);
  }
}
