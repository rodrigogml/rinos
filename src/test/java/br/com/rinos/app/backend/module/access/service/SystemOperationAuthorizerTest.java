package br.com.rinos.app.backend.module.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.access.enums.AuthorizationExplanationMode;
import br.com.rinos.app.api.module.access.enums.AuthorizationSourceType;
import br.com.rinos.app.api.module.access.keys.AccessControlAccessKeys;
import br.com.rinos.app.api.module.access.spi.SystemOperationContributor;
import br.com.rinos.app.api.module.access.vo.AuthorizationActor;
import br.com.rinos.app.api.module.access.vo.AuthorizationContext;
import br.com.rinos.app.api.module.access.vo.SystemOperationDescriptor;
import br.com.rinos.app.backend.module.access.repository.AccessAuditEventRepository;

class SystemOperationAuthorizerTest {

  @Test
  void shouldPermitOnlyExactRegisteredOperationScopeAndKeys() {
    var key = AccessControlAccessKeys.GLOBAL_CATALOG_VIEW;
    SystemOperationContributor contributor = () -> List.of(new SystemOperationDescriptor(
        "ACCESS_CATALOG_READINESS", "access.catalog.synchronize", AccessScope.GLOBAL,
        Set.of(key.code()), true));
    var registry = new SystemOperationRegistryService(List.of(contributor));
    AccessKeyRegistryService keys = mock(AccessKeyRegistryService.class);
    when(keys.find(key.code())).thenReturn(Optional.of(key));
    AccessContextRevisionService revisions = mock(AccessContextRevisionService.class);
    when(revisions.current(AccessScope.GLOBAL, null)).thenReturn(7L);
    AccessAuditEventRepository audits = mock(AccessAuditEventRepository.class);
    var authorizer = new SystemOperationAuthorizer(registry, keys, revisions, audits);
    AuthorizationRequest request = new AuthorizationRequest(
        AuthorizationActor.system("ACCESS_CATALOG_READINESS"), null,
        AuthorizationContext.global(), "access.catalog.synchronize", Set.of(key), null,
        true, AuthorizationExplanationMode.NONE);

    assertThat(authorizer.matches(request)).isTrue();
    var snapshot = authorizer.resolve(request, Instant.parse("2026-08-16T12:00:00Z"));
    assertThat(snapshot.contextRevision()).isEqualTo(7L);
    org.mockito.Mockito.verify(audits).save(org.mockito.ArgumentMatchers.any());
    assertThat(snapshot.keyResults()).allSatisfy(result -> {
      assertThat(result.allowed()).isTrue();
      assertThat(result.permitSources()).singleElement().satisfies(source ->
          assertThat(source.type()).isEqualTo(AuthorizationSourceType.SYSTEM_SOURCE));
    });

    AuthorizationRequest mismatch = new AuthorizationRequest(
        AuthorizationActor.system("UNKNOWN"), null, AuthorizationContext.global(),
        "access.catalog.synchronize", Set.of(key), null, true, AuthorizationExplanationMode.NONE);
    assertThat(authorizer.matches(mismatch)).isFalse();
    assertThatThrownBy(() -> authorizer.resolve(mismatch, Instant.now()))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("ACL_SYSTEM_SOURCE_MISMATCH");
  }

  @Test
  void registryShouldRejectDuplicateSemanticIdentity() {
    var descriptor = new SystemOperationDescriptor(
        "SOURCE", "operation", AccessScope.TENANT, Set.of("tenant.access.catalog.view"), true);
    SystemOperationContributor first = () -> List.of(descriptor);
    SystemOperationContributor second = () -> List.of(descriptor);
    assertThatThrownBy(() -> new SystemOperationRegistryService(List.of(first, second)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("duplicate");
  }
}
