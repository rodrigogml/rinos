package br.com.rinos.app.backend.module.access.service;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.account.entity.AccountEntity;
import br.com.rinos.app.backend.module.account.repository.AccountRepository;
import br.com.rinos.app.backend.module.identity.service.AdministrativeFactorContinuityContext;
import br.com.rinos.app.backend.module.membership.entity.AccountMembershipEntity;
import br.com.rinos.app.backend.module.membership.repository.AccountMembershipRepository;
import br.com.rinos.app.backend.module.membership.service.MembershipContinuityDecision;

class AccessAdministrativeFactorContinuityAdapterTest {

  private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");
  private final AccountMembershipRepository memberships = mock(AccountMembershipRepository.class);
  private final AccountRepository accounts = mock(AccountRepository.class);
  private final AccessContextRevisionService revisions = mock(AccessContextRevisionService.class);
  private final AdministrativeContinuityEvaluator continuity =
      mock(AdministrativeContinuityEvaluator.class);
  private final AccessContextCacheInvalidationService invalidation =
      mock(AccessContextCacheInvalidationService.class);
  private final GlobalAccessBootstrapService bootstrap = mock(GlobalAccessBootstrapService.class);
  private AccessAdministrativeFactorContinuityAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new AccessAdministrativeFactorContinuityAdapter(
        memberships, accounts, revisions, continuity, invalidation, bootstrap);
  }

  @Test
  void shouldLockGlobalThenSortedTenantsAndReviseOnlyAfterAllContextsPermit() {
    AccountMembershipEntity first = mock(AccountMembershipEntity.class);
    AccountMembershipEntity second = mock(AccountMembershipEntity.class);
    when(first.getAccountId()).thenReturn(10L);
    when(second.getAccountId()).thenReturn(20L);
    when(memberships.findByUserIdAndCurrentMarkerOrderByAccountId(7L, 1))
        .thenReturn(List.of(first, second));
    AccountEntity tenant20 = mock(AccountEntity.class);
    AccountEntity tenant10 = mock(AccountEntity.class);
    when(tenant20.getTenantId()).thenReturn(20L);
    when(tenant10.getTenantId()).thenReturn(10L);
    when(accounts.findAllById(List.of(10L, 20L))).thenReturn(List.of(tenant20, tenant10));
    when(continuity.evaluateContext(any(), any(), any()))
        .thenReturn(MembershipContinuityDecision.permit());

    AdministrativeFactorContinuityContext context = adapter.lockContexts(7L);
    adapter.validateAndRevise(context, NOW);

    var order = inOrder(revisions);
    order.verify(revisions).lock(AccessScope.GLOBAL, null);
    order.verify(revisions).lock(AccessScope.TENANT, 10L);
    order.verify(revisions).lock(AccessScope.TENANT, 20L);
    order.verify(revisions).lockAndIncrement(AccessScope.GLOBAL, null);
    order.verify(revisions).lockAndIncrement(AccessScope.TENANT, 10L);
    order.verify(revisions).lockAndIncrement(AccessScope.TENANT, 20L);
  }

  @Test
  void shouldFailBeforeRevisionWhenAnyContextWouldLoseContinuity() {
    when(continuity.evaluateContext(AccessScope.GLOBAL, null, NOW))
        .thenReturn(MembershipContinuityDecision.deny());

    assertThatIllegalArgumentException().isThrownBy(() -> adapter.validateAndRevise(
        new AdministrativeFactorContinuityContext(7L, List.of()), NOW))
        .withMessageContaining("continuity would be lost");

    verify(revisions, never()).lockAndIncrement(any(), any());
    verify(invalidation, never()).afterCommit(any(), any());
  }
}
