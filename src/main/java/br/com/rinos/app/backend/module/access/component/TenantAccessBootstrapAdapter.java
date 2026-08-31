package br.com.rinos.app.backend.module.access.component;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.module.access.enums.AccessRuleEffect;
import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.account.enums.AccountBootstrapResultStatus;
import br.com.rinos.app.api.module.account.port.TenantAccessBootstrapPort;
import br.com.rinos.app.api.module.account.vo.AccountBootstrapRequest;
import br.com.rinos.app.api.module.account.vo.AccountBootstrapResult;
import br.com.rinos.app.backend.module.access.entity.AccessGroupEntity;
import br.com.rinos.app.backend.module.access.entity.AccessKeyEntity;
import br.com.rinos.app.backend.module.access.entity.ProtectedGroupBaselineEntity;
import br.com.rinos.app.backend.module.access.enums.AccessAdministrationAction;
import br.com.rinos.app.backend.module.access.enums.AccessRecordStatus;
import br.com.rinos.app.backend.module.access.enums.AccessRuleOriginType;
import br.com.rinos.app.backend.module.access.enums.ProtectedBaselineStatus;
import br.com.rinos.app.backend.module.access.repository.AccessGroupRepository;
import br.com.rinos.app.backend.module.access.repository.AccessKeyRepository;
import br.com.rinos.app.backend.module.access.repository.ProtectedGroupBaselineKeyRepository;
import br.com.rinos.app.backend.module.access.repository.ProtectedGroupBaselineRepository;
import br.com.rinos.app.backend.module.access.service.AccessAdministrationMutationResult;
import br.com.rinos.app.backend.module.access.service.AccessAdministrationMutationService;
import br.com.rinos.app.backend.module.access.service.AccessGroupMutationCommand;
import br.com.rinos.app.backend.module.access.service.AccessGroupSubjectMutationCommand;
import br.com.rinos.app.backend.module.access.service.AccessMutationMetadata;
import br.com.rinos.app.backend.module.access.service.AccessRuleMutationResult;
import br.com.rinos.app.backend.module.access.service.AccessRuleMutationCommand;
import br.com.rinos.app.backend.module.access.service.AccessRuleMutationService;
import br.com.rinos.app.backend.module.access.service.AccountMembershipAccessPort;
import br.com.rinos.app.backend.module.access.service.FoundingMembershipAccessSnapshot;
import br.com.rinos.app.backend.module.account.entity.AccountCreationIntentEntity;
import br.com.rinos.app.backend.module.account.entity.AccountEntity;
import br.com.rinos.app.backend.module.account.entity.TenantEntity;
import br.com.rinos.app.backend.module.account.repository.AccountCreationIntentRepository;
import br.com.rinos.app.backend.module.account.repository.AccountRepository;
import br.com.rinos.app.backend.module.account.repository.TenantRepository;

/**
 * Cria o grupo fundador protegido de um tenant com a baseline administrativa já publicada.
 *
 * <p>O adapter não presume que a conta esteja ativa. Ele somente cria uma associação explícita
 * entre a membership fundadora e o grupo protegido, materializa cada chave mínima da baseline
 * atual e preserva a idempotência pelo tenant, versão de baseline e protocolo de criação.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-31
 */
@Component
@org.springframework.context.annotation.Lazy
public class TenantAccessBootstrapAdapter implements TenantAccessBootstrapPort {

  private static final String SYSTEM_ORIGIN = "account-tenant-access-bootstrap";
  private static final String GROUP_NAME = "Administradores da conta";
  private static final String GROUP_DESCRIPTION = "Baseline protegida do fundador da conta";
  private static final String INVALID_CONTEXT = "ACCOUNT_BOOTSTRAP_CONTEXT_INVALID";

  private final AccountRepository accounts;
  private final TenantRepository tenants;
  private final AccountCreationIntentRepository intents;
  private final AccountMembershipAccessPort memberships;
  private final ProtectedGroupBaselineRepository baselineRepository;
  private final ProtectedGroupBaselineKeyRepository baselineKeyRepository;
  private final AccessKeyRepository keyRepository;
  private final AccessGroupRepository groupRepository;
  private final AccessAdministrationMutationService administration;
  private final AccessRuleMutationService rules;

  /**
   * Cria o adapter que reutiliza as mutações e proteções canônicas do módulo ACL.
   *
   * @param accounts fonte canônica da conta
   * @param tenants fonte canônica do tenant
   * @param intents fonte canônica da intenção aceita
   * @param memberships consulta estrutural da associação fundadora
   * @param baselineRepository baseline publicada por escopo
   * @param baselineKeyRepository chaves explícitas da baseline
   * @param keyRepository catálogo persistido de chaves
   * @param groupRepository consulta idempotente do grupo protegido
   * @param administration mutações auditadas de grupo e sujeito
   * @param rules mutações auditadas de regra e histórico
   */
  public TenantAccessBootstrapAdapter(
      AccountRepository accounts,
      TenantRepository tenants,
      AccountCreationIntentRepository intents,
      AccountMembershipAccessPort memberships,
      ProtectedGroupBaselineRepository baselineRepository,
      ProtectedGroupBaselineKeyRepository baselineKeyRepository,
      AccessKeyRepository keyRepository,
      AccessGroupRepository groupRepository,
      AccessAdministrationMutationService administration,
      AccessRuleMutationService rules) {
    this.accounts = Objects.requireNonNull(accounts, "accounts must not be null");
    this.tenants = Objects.requireNonNull(tenants, "tenants must not be null");
    this.intents = Objects.requireNonNull(intents, "intents must not be null");
    this.memberships = Objects.requireNonNull(memberships, "memberships must not be null");
    this.baselineRepository = Objects.requireNonNull(baselineRepository,
        "baselineRepository must not be null");
    this.baselineKeyRepository = Objects.requireNonNull(baselineKeyRepository,
        "baselineKeyRepository must not be null");
    this.keyRepository = Objects.requireNonNull(keyRepository, "keyRepository must not be null");
    this.groupRepository = Objects.requireNonNull(groupRepository, "groupRepository must not be null");
    this.administration = Objects.requireNonNull(administration, "administration must not be null");
    this.rules = Objects.requireNonNull(rules, "rules must not be null");
  }

  /**
   * Cria ou confirma a baseline ACL do fundador sem conceder acesso implícito por papel.
   *
   * @param request protocolo e identidades públicas emitidos pelo cadastro de conta
   * @return resultado seguro e idempotente da materialização ACL
   */
  @Override
  @Transactional
  public AccountBootstrapResult bootstrapAccess(AccountBootstrapRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    BootstrapContext context = resolveContext(request);
    if (context == null) {
      return rejected();
    }
    try {
      ProtectedGroupBaselineEntity baseline = baselineRepository
          .findFirstByScopeAndStatusOrderByBaselineVersionDesc(
              AccessScope.TENANT, ProtectedBaselineStatus.ACTIVE)
          .orElseThrow(() -> new IllegalStateException("tenant protected baseline is unavailable"));
      var baselineEntries = baselineKeyRepository.findByIdBaselineId(baseline.getId());
      if (baselineEntries.isEmpty()) {
        throw new IllegalStateException("tenant protected baseline is empty");
      }
      var keys = keyRepository.findAllById(baselineEntries.stream()
          .map(entry -> entry.getId().getAccessKeyId()).toList());
      if (keys.size() != baselineEntries.size()
          || keys.stream().anyMatch(key -> key.getScope() != AccessScope.TENANT
              || key.getStatus() != AccessRecordStatus.ACTIVE)) {
        throw new IllegalStateException("tenant protected baseline is inconsistent");
      }
      return materialize(context, baseline, keys, request);
    } catch (IllegalArgumentException exception) {
      return rejected();
    } catch (RuntimeException exception) {
      return AccountBootstrapResult.unavailable();
    }
  }

  private AccountBootstrapResult materialize(
      BootstrapContext context,
      ProtectedGroupBaselineEntity baseline,
      java.util.List<AccessKeyEntity> keys,
      AccountBootstrapRequest request) {
    Instant occurredAt = Instant.now();
    AccessMutationMetadata metadata = new AccessMutationMetadata(
        null, SYSTEM_ORIGIN, "initial tenant protected baseline", request.correlationId(), occurredAt);
    AccessGroupEntity current = groupRepository
        .findByScopeAndTenantIdAndProtectedGroupTrueAndBaselineVersion(
            AccessScope.TENANT, context.tenant().getId(), baseline.getBaselineVersion())
        .orElse(null);
    Long groupId;
    boolean changed = false;
    if (current == null) {
      AccessAdministrationMutationResult mutation = administration.mutateGroup(
          new AccessGroupMutationCommand(AccessAdministrationAction.CREATE, null, AccessScope.TENANT,
              context.tenant().getId(), GROUP_NAME, GROUP_DESCRIPTION, true,
              baseline.getBaselineVersion(), metadata));
      groupId = mutation.targetId();
      changed = mutation.changed();
    } else {
      if (current.getStatus() != AccessRecordStatus.ACTIVE) {
        throw new IllegalStateException("tenant protected group is inactive");
      }
      groupId = current.getId();
    }
    for (AccessKeyEntity key : keys.stream().sorted(Comparator.comparing(AccessKeyEntity::getCode)).toList()) {
      AccessRuleMutationResult mutation = rules.apply(new AccessRuleMutationCommand(
          AccessScope.TENANT, context.tenant().getId(), AccessRuleOriginType.GROUP, null, null,
          groupId, key.getCode(), AccessRuleEffect.PERMITIR, null, null, null, SYSTEM_ORIGIN,
          "tenant protected baseline", request.correlationId(), occurredAt));
      changed = changed || mutation.changed();
    }
    AccessAdministrationMutationResult subjectMutation = administration.mutateSubject(
        new AccessGroupSubjectMutationCommand(AccessAdministrationAction.ASSIGN, null, groupId, null,
            context.membership().membershipId(), null, null, metadata));
    changed = changed || subjectMutation.changed();
    return new AccountBootstrapResult(
        changed ? AccountBootstrapResultStatus.ACCEPTED : AccountBootstrapResultStatus.ALREADY_COMPLETED,
        groupId.toString(), null);
  }

  private BootstrapContext resolveContext(AccountBootstrapRequest request) {
    AccountEntity account = accounts.findByPublicId(request.accountPublicId()).orElse(null);
    if (account == null || !Objects.equals(account.getFounderUserId(), request.founderUserId())) {
      return null;
    }
    TenantEntity tenant = tenants.findById(account.getTenantId())
        .filter(candidate -> request.tenantPublicId().equals(candidate.getPublicId()))
        .orElse(null);
    AccountCreationIntentEntity intent = intents.findByProtocolId(request.protocolId())
        .filter(candidate -> Objects.equals(candidate.getAccountId(), account.getId()))
        .filter(candidate -> Objects.equals(candidate.getCreatorUserId(), account.getFounderUserId()))
        .orElse(null);
    FoundingMembershipAccessSnapshot membership = memberships.inspectFounder(
        account.getId(), account.getFounderUserId());
    if (tenant == null || intent == null || !membership.sourceAvailable() || !membership.exists()
        || !membership.membershipActive() || !Objects.equals(membership.tenantId(), tenant.getId())) {
      return null;
    }
    return new BootstrapContext(account, tenant, membership);
  }

  private static AccountBootstrapResult rejected() {
    return new AccountBootstrapResult(AccountBootstrapResultStatus.REJECTED, null, INVALID_CONTEXT);
  }

  private record BootstrapContext(
      AccountEntity account,
      TenantEntity tenant,
      FoundingMembershipAccessSnapshot membership) {
  }
}
