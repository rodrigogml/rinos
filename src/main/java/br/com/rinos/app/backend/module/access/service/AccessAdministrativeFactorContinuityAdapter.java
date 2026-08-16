package br.com.rinos.app.backend.module.access.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.account.repository.AccountRepository;
import br.com.rinos.app.backend.module.identity.service.AdministrativeFactorContinuityContext;
import br.com.rinos.app.backend.module.identity.service.AdministrativeFactorContinuityPort;
import br.com.rinos.app.backend.module.membership.repository.AccountMembershipRepository;

/** Serializa remoções de fator com as mutações ACL dos contextos afetados. */
@Service
@org.springframework.context.annotation.Lazy
@Primary
public class AccessAdministrativeFactorContinuityAdapter
    implements AdministrativeFactorContinuityPort {

  private final AccountMembershipRepository memberships;
  private final AccountRepository accounts;
  private final AccessContextRevisionService revisions;
  private final AdministrativeContinuityEvaluator continuity;
  private final AccessContextCacheInvalidationService invalidation;
  private final GlobalAccessBootstrapService bootstrap;

  public AccessAdministrativeFactorContinuityAdapter(
      AccountMembershipRepository memberships,
      AccountRepository accounts,
      AccessContextRevisionService revisions,
      AdministrativeContinuityEvaluator continuity,
      AccessContextCacheInvalidationService invalidation,
      GlobalAccessBootstrapService bootstrap) {
    this.memberships = memberships;
    this.accounts = accounts;
    this.revisions = revisions;
    this.continuity = continuity;
    this.invalidation = invalidation;
    this.bootstrap = bootstrap;
  }

  @Override
  public void afterStrongFactorEstablished(UUID correlationId, Instant occurredAt) {
    if (correlationId == null || occurredAt == null) return;
    Runnable attempt = () -> bootstrap.attempt(correlationId, occurredAt);
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      attempt.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        attempt.run();
      }
    });
  }

  @Override
  public AdministrativeFactorContinuityContext lockContexts(long userId) {
    if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
    List<Long> accountIds = memberships.findByUserIdAndCurrentMarkerOrderByAccountId(userId, 1)
        .stream().map(value -> value.getAccountId()).distinct().toList();
    var affectedAccounts = accounts.findAllById(accountIds);
    if (affectedAccounts.size() != accountIds.size()) {
      throw new IllegalStateException("administrative continuity context is unavailable");
    }
    List<Long> tenantIds = affectedAccounts.stream()
        .map(value -> value.getTenantId()).distinct().sorted().toList();
    revisions.lock(AccessScope.GLOBAL, null);
    tenantIds.forEach(tenantId -> revisions.lock(AccessScope.TENANT, tenantId));
    return new AdministrativeFactorContinuityContext(userId, tenantIds);
  }

  @Override
  public void validateAndRevise(
      AdministrativeFactorContinuityContext context, Instant effectiveAt) {
    if (context == null || effectiveAt == null) {
      throw new IllegalArgumentException("administrative factor continuity is incomplete");
    }
    validate(AccessScope.GLOBAL, null, effectiveAt);
    context.tenantIds().forEach(tenantId ->
        validate(AccessScope.TENANT, tenantId, effectiveAt));
    revisions.lockAndIncrement(AccessScope.GLOBAL, null);
    invalidation.afterCommit(AccessScope.GLOBAL, null);
    context.tenantIds().forEach(tenantId -> {
      revisions.lockAndIncrement(AccessScope.TENANT, tenantId);
      invalidation.afterCommit(AccessScope.TENANT, tenantId);
    });
  }

  private void validate(AccessScope scope, Long tenantId, Instant effectiveAt) {
    var decision = continuity.evaluateContext(scope, tenantId, effectiveAt);
    if (!decision.sourceAvailable()) {
      throw new IllegalStateException("administrative continuity is unavailable");
    }
    if (!decision.allowed()) {
      throw new IllegalArgumentException("administrative continuity would be lost");
    }
  }
}
