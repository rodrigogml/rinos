package br.com.rinos.app.backend.module.access.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.module.access.dto.AccessGroupSaveRequest;
import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.access.exception.AccessAdministrationConflictException;
import br.com.rinos.app.api.module.access.keys.AccessControlAccessKeys;
import br.com.rinos.app.api.module.access.vo.AccessCategoryDescriptor;
import br.com.rinos.app.api.module.access.vo.AccessAdministrationCapabilities;
import br.com.rinos.app.api.module.access.vo.AuthorizationContext;
import br.com.rinos.app.backend.module.access.entity.AccessContextRevisionEntity;
import br.com.rinos.app.backend.module.access.repository.AccessGroupRepository;
import br.com.rinos.app.backend.module.access.repository.AccessGroupSubjectRepository;
import br.com.rinos.app.backend.module.access.repository.AccessKeyRepository;
import br.com.rinos.app.backend.module.access.repository.AccessRuleRepository;
import br.com.rinos.app.backend.module.access.service.AccessAdministrationMutationService;
import br.com.rinos.app.backend.module.access.service.AccessAdministrationPreviewExecutor;
import br.com.rinos.app.backend.module.access.service.AccessContextRevisionService;
import br.com.rinos.app.backend.module.access.service.AccessKeyRegistryService;
import br.com.rinos.app.backend.module.access.service.AccessRuleMutationService;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.membership.repository.AccountMembershipRepository;

class AccessAdministrationFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");

  @Test
  void inspect_shouldReturnOnlyDescriptorsAndRecordsFromRequestedContext() {
    Fixture fixture = new Fixture();
    AuthorizationContext context = AuthorizationContext.tenant(44L);
    when(fixture.revisions.current(AccessScope.TENANT, 44L)).thenReturn(7L);
    when(fixture.registry.categories()).thenReturn(List.of(
        new AccessCategoryDescriptor("tenant.foundation", null, AccessScope.TENANT,
            "access.category.tenant.foundation.name"),
        new AccessCategoryDescriptor("global.platform", null, AccessScope.GLOBAL,
            "access.category.global.platform.name")));
    when(fixture.registry.accessKeys()).thenReturn(List.of(
        AccessControlAccessKeys.TENANT_GROUP_VIEW,
        AccessControlAccessKeys.GLOBAL_GROUP_VIEW));
    when(fixture.groups.findByScopeAndTenantIdOrderByNormalizedName(AccessScope.TENANT, 44L))
        .thenReturn(List.of());
    when(fixture.keys.findAllByOrderByCodeAsc()).thenReturn(List.of());
    when(fixture.rules.findByScopeAndTenantId(AccessScope.TENANT, 44L)).thenReturn(List.of());
    when(fixture.memberships.findByAccountIdAndCurrentMarkerOrderById(44L, 1))
        .thenReturn(List.of());

    var snapshot = fixture.facade.inspect(context,
        new AccessAdministrationCapabilities(true, true, true, true, true, true));

    assertThat(snapshot.revision()).isEqualTo(7L);
    assertThat(snapshot.context().tenantId()).isEqualTo(44L);
    assertThat(snapshot.categories()).extracting("internalReference")
        .containsExactly("tenant.foundation");
    assertThat(snapshot.keys()).extracting("internalReference")
        .containsExactly(AccessControlAccessKeys.TENANT_GROUP_VIEW.code());
    assertThat(snapshot.groups()).isEmpty();
    assertThat(snapshot.rules()).isEmpty();
  }

  @Test
  void saveGroup_shouldRejectStaleRevisionBeforeAnyMutation() {
    Fixture fixture = new Fixture();
    AccessContextRevisionEntity locked = mock(AccessContextRevisionEntity.class);
    when(locked.getRevision()).thenReturn(9L);
    when(fixture.revisions.lock(AccessScope.GLOBAL, null)).thenReturn(locked);
    var request = new AccessGroupSaveRequest(
        AuthorizationContext.global(), 8L, null, "Operadores", null, 11L,
        "teste", "correlation-1");

    assertThatThrownBy(() -> fixture.facade.saveGroup(request))
        .isInstanceOf(AccessAdministrationConflictException.class)
        .hasMessage("ACL_CONTEXT_REVISION_CONFLICT");
    verifyNoInteractions(fixture.groupMutations);
  }

  @Test
  void inspect_shouldNotReadSectionsWithoutResolvedVisibility() {
    Fixture fixture = new Fixture();
    when(fixture.revisions.current(AccessScope.GLOBAL, null)).thenReturn(2L);
    when(fixture.registry.categories()).thenReturn(List.of());
    when(fixture.registry.accessKeys()).thenReturn(List.of());

    var snapshot = fixture.facade.inspect(AuthorizationContext.global(),
        new AccessAdministrationCapabilities(true, false, false, false, false, false));

    assertThat(snapshot.groups()).isEmpty();
    assertThat(snapshot.subjects()).isEmpty();
    assertThat(snapshot.rules()).isEmpty();
    verifyNoInteractions(fixture.groups, fixture.groupSubjects, fixture.keys, fixture.rules,
        fixture.memberships, fixture.users);
  }

  private static final class Fixture {
    private final AccessKeyRegistryService registry = mock(AccessKeyRegistryService.class);
    private final AccessContextRevisionService revisions = mock(AccessContextRevisionService.class);
    private final AccessGroupRepository groups = mock(AccessGroupRepository.class);
    private final AccessGroupSubjectRepository groupSubjects = mock(AccessGroupSubjectRepository.class);
    private final AccessKeyRepository keys = mock(AccessKeyRepository.class);
    private final AccessRuleRepository rules = mock(AccessRuleRepository.class);
    private final AccountMembershipRepository memberships = mock(AccountMembershipRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AccessAdministrationMutationService groupMutations =
        mock(AccessAdministrationMutationService.class);
    private final AccessRuleMutationService ruleMutations = mock(AccessRuleMutationService.class);
    private final AccessAdministrationPreviewExecutor previews =
        mock(AccessAdministrationPreviewExecutor.class);
    private final AccessAdministrationFacadeImpl facade = new AccessAdministrationFacadeImpl(
        registry, revisions, groups, groupSubjects, keys, rules, memberships, users,
        groupMutations, ruleMutations, previews, Clock.fixed(NOW, ZoneOffset.UTC));
  }
}
