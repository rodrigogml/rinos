package br.com.rinos.app.backend.module.membership.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.module.account.enums.AccountStatus;
import br.com.rinos.app.api.module.membership.enums.MembershipMutationOperation;
import br.com.rinos.app.api.module.membership.enums.MembershipMutationResultStatus;
import br.com.rinos.app.api.module.membership.enums.MembershipStatus;
import br.com.rinos.app.api.module.membership.vo.MembershipMutationResult;
import br.com.rinos.app.backend.module.account.enums.TenantStatus;
import br.com.rinos.app.backend.module.account.repository.AccountRepository;
import br.com.rinos.app.backend.module.account.repository.TenantRepository;
import br.com.rinos.app.backend.module.membership.entity.AccountMembershipEntity;
import br.com.rinos.app.backend.module.membership.entity.MembershipEventEntity;
import br.com.rinos.app.backend.module.membership.entity.MembershipOutboxEventEntity;
import br.com.rinos.app.backend.module.membership.repository.AccountMembershipRepository;
import br.com.rinos.app.backend.module.membership.repository.MembershipEventRepository;
import br.com.rinos.app.backend.module.membership.repository.MembershipOutboxEventRepository;
import br.com.rinos.app.api.module.plans.enums.TenantUserCapacityStatus;

/** Núcleo persistente chamado após autorização explícita e derivação confiável da garantia. */
@Service
@org.springframework.context.annotation.Lazy
public class MembershipLifecycleService {

  private final AccountMembershipRepository memberships;
  private final AccountRepository accounts;
  private final TenantRepository tenants;
  private final MembershipEventRepository events;
  private final MembershipOutboxEventRepository outbox;
  private final MembershipAdministrativeContinuityPort continuity;
  private final MembershipContextInvalidationPort invalidation;
  private final MembershipPlanCapacityPort plans;

  public MembershipLifecycleService(
      AccountMembershipRepository memberships,
      AccountRepository accounts,
      TenantRepository tenants,
      MembershipEventRepository events,
      MembershipOutboxEventRepository outbox,
      MembershipAdministrativeContinuityPort continuity,
      MembershipContextInvalidationPort invalidation,
      MembershipPlanCapacityPort plans) {
    this.memberships = memberships;
    this.accounts = accounts;
    this.tenants = tenants;
    this.events = events;
    this.outbox = outbox;
    this.continuity = continuity;
    this.invalidation = invalidation;
    this.plans = plans;
  }

  @Transactional
  public MembershipMutationResult mutate(MembershipMutationCommand command) {
    Long targetId = memberships.findIdByPublicId(command.targetMembershipPublicId()).orElse(null);
    if (targetId == null) return rejected(null, null, "MEMBERSHIP_CONTEXT_INVALID");
    Long accountId = memberships.findAccountIdById(targetId).orElse(null);
    var contextAccount = accountId == null ? null : accounts.findById(accountId).orElse(null);
    if (contextAccount == null) return rejected(null, null, "MEMBERSHIP_CONTEXT_INVALID");
    invalidation.lock(contextAccount.getTenantId());

    long firstId = Math.min(command.actorMembershipId(), targetId);
    long secondId = Math.max(command.actorMembershipId(), targetId);
    AccountMembershipEntity first = memberships.findByIdForUpdate(firstId).orElse(null);
    AccountMembershipEntity second = secondId == firstId ? first
        : memberships.findByIdForUpdate(secondId).orElse(null);
    AccountMembershipEntity actor = entity(command.actorMembershipId(), first, second);
    AccountMembershipEntity target = entity(targetId, first, second);
    if (!validActorAndTarget(actor, target, command.operation())) {
      return rejected(target, actor, "MEMBERSHIP_CONTEXT_INVALID");
    }
    if (!command.recentStrongAuthentication()) {
      return rejected(target, actor, "MEMBERSHIP_STRONG_AUTHENTICATION_REQUIRED", command);
    }

    var account = accounts.findById(target.getAccountId())
        .filter(candidate -> candidate.getStatus() == AccountStatus.ACTIVE).orElse(null);
    var tenant = account == null ? null : tenants.findById(account.getTenantId())
        .filter(candidate -> candidate.getStatus() == TenantStatus.OPERATIONAL).orElse(null);
    if (account == null || tenant == null) {
      return rejected(target, actor, "MEMBERSHIP_CONTEXT_INVALID");
    }
    if (target.getVersion() != command.expectedVersion()) {
      audit(target, actor, command, "MEMBERSHIP_MUTATION_CONFLICT", "CONFLICT");
      return new MembershipMutationResult(MembershipMutationResultStatus.CONFLICT,
          target.getPublicId(), target.getVersion(), null, "MEMBERSHIP_VERSION_CONFLICT");
    }
    if (!validTransition(target, actor, command)) {
      return rejected(target, actor, "MEMBERSHIP_TRANSITION_INVALID", command);
    }
    if (noChange(target, command)) {
      audit(target, actor, command, "MEMBERSHIP_MUTATION_NO_CHANGE", "NO_CHANGE");
      return new MembershipMutationResult(MembershipMutationResultStatus.NO_CHANGE,
          target.getPublicId(), target.getVersion(), null, null);
    }

    if (reducesAdministrativeCapacity(command.operation())) {
      MembershipContinuityDecision decision = continuity.evaluate(new MembershipContinuityRequest(
          target.getAccountId(), tenant.getId(), target.getId(), command.operation(),
          resultingStatus(target, command.operation()),
          command.proposedRole() == null ? target.getRoleType() : command.proposedRole(),
          command.occurredAt()));
      if (!decision.sourceAvailable()) {
        audit(target, actor, command, "MEMBERSHIP_CONTINUITY_UNAVAILABLE", "UNAVAILABLE");
        return new MembershipMutationResult(MembershipMutationResultStatus.UNAVAILABLE,
            target.getPublicId(), target.getVersion(), null, "MEMBERSHIP_CONTINUITY_UNAVAILABLE");
      }
      if (!decision.allowed()) {
        return rejected(target, actor, "MEMBERSHIP_ADMINISTRATIVE_CONTINUITY_REQUIRED", command);
      }
    }

    if (command.operation() == MembershipMutationOperation.REACTIVATE) {
      var capacity = plans.occupy(target.getAccountId(), target.getUserId(),
          target.getPublicId(), command.correlationId());
      if (capacity.status() == TenantUserCapacityStatus.SOURCE_UNAVAILABLE) {
        return new MembershipMutationResult(MembershipMutationResultStatus.UNAVAILABLE,
            target.getPublicId(), target.getVersion(), null, "MEMBERSHIP_PLAN_UNAVAILABLE");
      }
      if (capacity.status() != TenantUserCapacityStatus.OCCUPIED
          && capacity.status() != TenantUserCapacityStatus.ALREADY_OCCUPIED) {
        return rejected(target, actor, "MEMBERSHIP_PLAN_LIMIT_REACHED", command);
      }
    }
    apply(target, command);
    memberships.saveAndFlush(target);
    long revision = invalidation.revise(tenant.getId());
    String eventType = eventType(command.operation());
    audit(target, actor, command, eventType, "COMPLETED");
    outbox.saveAndFlush(new MembershipOutboxEventEntity(
        UUID.randomUUID(), target.getId(), eventType,
        "{\"membershipPublicId\":\"" + target.getPublicId() + "\",\"contextRevision\":" + revision + "}"));
    return new MembershipMutationResult(MembershipMutationResultStatus.CHANGED,
        target.getPublicId(), target.getVersion(), revision, null);
  }

  private void apply(AccountMembershipEntity target, MembershipMutationCommand command) {
    switch (command.operation()) {
      case CHANGE_ROLE -> target.changeRole(command.proposedRole());
      case SUSPEND -> target.suspend();
      case REACTIVATE -> target.reactivate();
      case REMOVE -> target.remove(command.occurredAt());
      case LEAVE -> target.leave(command.occurredAt());
    }
  }

  private void audit(AccountMembershipEntity target, AccountMembershipEntity actor,
      MembershipMutationCommand command, String eventType, String result) {
    events.save(new MembershipEventEntity(eventType, target.getAccountId(), target.getId(), null,
        actor == null ? null : actor.getUserId(), command.correlationId(), result, command.occurredAt()));
  }

  private MembershipMutationResult rejected(AccountMembershipEntity target, AccountMembershipEntity actor,
      String reason, MembershipMutationCommand command) {
    audit(target, actor, command, "MEMBERSHIP_MUTATION_REJECTED", reason);
    return rejected(target, reason);
  }

  private static MembershipMutationResult rejected(
      AccountMembershipEntity target, AccountMembershipEntity ignored, String reason) {
    return rejected(target, reason);
  }

  private static MembershipMutationResult rejected(AccountMembershipEntity target, String reason) {
    return new MembershipMutationResult(MembershipMutationResultStatus.REJECTED,
        target == null ? null : target.getPublicId(), target == null ? null : target.getVersion(), null, reason);
  }

  private static AccountMembershipEntity entity(
      long id, AccountMembershipEntity first, AccountMembershipEntity second) {
    if (first != null && first.getId() == id) return first;
    return second != null && second.getId() == id ? second : null;
  }

  private static boolean validActorAndTarget(AccountMembershipEntity actor, AccountMembershipEntity target,
      MembershipMutationOperation operation) {
    if (actor == null || target == null || actor.getStatus() != MembershipStatus.ACTIVE
        || !actor.getAccountId().equals(target.getAccountId())) return false;
    return operation == MembershipMutationOperation.LEAVE
        ? actor.getId().equals(target.getId()) : true;
  }

  private static boolean validTransition(
      AccountMembershipEntity target, AccountMembershipEntity actor, MembershipMutationCommand command) {
    return switch (command.operation()) {
      case CHANGE_ROLE, SUSPEND -> target.getStatus() == MembershipStatus.ACTIVE;
      case REACTIVATE -> target.getStatus() == MembershipStatus.SUSPENDED;
      case REMOVE -> target.getStatus() == MembershipStatus.ACTIVE
          || target.getStatus() == MembershipStatus.SUSPENDED;
      case LEAVE -> target.getStatus() == MembershipStatus.ACTIVE
          && actor.getId().equals(target.getId());
    };
  }

  private static boolean noChange(AccountMembershipEntity target, MembershipMutationCommand command) {
    return command.operation() == MembershipMutationOperation.CHANGE_ROLE
        && target.getRoleType() == command.proposedRole();
  }

  private static boolean reducesAdministrativeCapacity(MembershipMutationOperation operation) {
    return operation != MembershipMutationOperation.REACTIVATE;
  }

  private static MembershipStatus resultingStatus(
      AccountMembershipEntity target, MembershipMutationOperation operation) {
    return switch (operation) {
      case CHANGE_ROLE -> target.getStatus();
      case SUSPEND -> MembershipStatus.SUSPENDED;
      case REACTIVATE -> MembershipStatus.ACTIVE;
      case REMOVE -> MembershipStatus.REMOVED;
      case LEAVE -> MembershipStatus.LEFT;
    };
  }

  private static String eventType(MembershipMutationOperation operation) {
    return switch (operation) {
      case CHANGE_ROLE -> "MEMBERSHIP_ROLE_CHANGED";
      case SUSPEND -> "MEMBERSHIP_SUSPENDED";
      case REACTIVATE -> "MEMBERSHIP_REACTIVATED";
      case REMOVE -> "MEMBERSHIP_REMOVED";
      case LEAVE -> "MEMBERSHIP_LEFT";
    };
  }
}
