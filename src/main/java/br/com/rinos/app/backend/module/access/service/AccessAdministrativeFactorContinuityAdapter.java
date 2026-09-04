package br.com.rinos.app.backend.module.access.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.account.repository.AccountRepository;
import br.com.rinos.app.backend.module.identity.service.AdministrativeFactorContinuityContext;
import br.com.rinos.app.backend.module.identity.service.AdministrativeFactorContinuityPort;
import br.com.rinos.app.backend.module.identity.service.AdministrativeIdentityContinuityContext;
import br.com.rinos.app.backend.module.identity.service.AdministrativeIdentityContinuityPort;
import br.com.rinos.app.backend.module.membership.repository.AccountMembershipRepository;

/** Serializa alterações de fator forte e de estado da identidade com mutações ACL dos contextos afetados. */
@Service
@org.springframework.context.annotation.Lazy
@Primary
@ConditionalOnBean(DataSource.class)
public class AccessAdministrativeFactorContinuityAdapter
    implements AdministrativeFactorContinuityPort, AdministrativeIdentityContinuityPort {

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
    List<Long> tenantIds = lockAffectedContexts(userId);
    return new AdministrativeFactorContinuityContext(userId, tenantIds);
  }

  @Override
  public AdministrativeIdentityContinuityContext lockIdentityContexts(long userId) {
    List<Long> tenantIds = lockAffectedContexts(userId);
    return new AdministrativeIdentityContinuityContext(userId, tenantIds);
  }

  private List<Long> lockAffectedContexts(long userId) {
    if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
    // Esta precisa ser a primeira leitura da transação: no MySQL, uma consulta anterior pode
    // fixar o snapshot REPEATABLE READ antes de esperar uma mutação ACL concorrente.
    revisions.lock(AccessScope.GLOBAL, null);
    List<Long> accountIds = memberships.findByUserIdAndCurrentMarkerOrderByAccountId(userId, 1)
        .stream().map(value -> value.getAccountId()).distinct().toList();
    var affectedAccounts = accounts.findAllById(accountIds);
    if (affectedAccounts.size() != accountIds.size()) {
      throw new IllegalStateException("administrative continuity context is unavailable");
    }
    List<Long> tenantIds = affectedAccounts.stream()
        .map(value -> value.getTenantId()).distinct().sorted().toList();
    tenantIds.forEach(tenantId -> revisions.lock(AccessScope.TENANT, tenantId));
    return tenantIds;
  }

  @Override
  public void validateAndRevise(
      AdministrativeFactorContinuityContext context, Instant effectiveAt) {
    if (context == null) throw new IllegalArgumentException("administrative factor continuity is incomplete");
    validateAndRevise(context.tenantIds(), effectiveAt);
  }

  @Override
  public void validateAndRevise(
      AdministrativeIdentityContinuityContext context, Instant effectiveAt) {
    if (context == null) throw new IllegalArgumentException("administrative identity continuity is incomplete");
    validateAndRevise(context.tenantIds(), effectiveAt);
  }

  private void validateAndRevise(List<Long> tenantIds, Instant effectiveAt) {
    if (effectiveAt == null) {
      throw new IllegalArgumentException("administrative continuity is incomplete");
    }
    validate(AccessScope.GLOBAL, null, effectiveAt);
    tenantIds.forEach(tenantId ->
        validate(AccessScope.TENANT, tenantId, effectiveAt));
    revisions.lockAndIncrement(AccessScope.GLOBAL, null);
    invalidation.afterCommit(AccessScope.GLOBAL, null);
    tenantIds.forEach(tenantId -> {
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
