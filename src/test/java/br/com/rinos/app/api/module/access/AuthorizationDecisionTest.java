package br.com.rinos.app.api.module.access;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.module.access.keys.AccessControlAccessKeys;
import br.com.rinos.app.api.module.access.vo.AuthorizationContext;
import br.com.rinos.app.api.module.access.vo.AuthorizationDecision;
import br.com.rinos.app.api.module.access.vo.AuthorizationKeyResult;

class AuthorizationDecisionTest {

  @Test
  void decision_shouldRejectResultOrRevisionInconsistency() {
    AuthorizationKeyResult missing = new AuthorizationKeyResult(
        AccessControlAccessKeys.GLOBAL_CATALOG_VIEW,
        false,
        List.of(),
        List.of(),
        true,
        List.of());

    assertThatThrownBy(() -> new AuthorizationDecision(
        true,
        AuthorizationContext.global().withRevision(2),
        List.of(missing),
        List.of(),
        List.of(),
        List.of(),
        Set.of(),
        2,
        Instant.parse("2026-08-15T12:00:00Z"),
        UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new AuthorizationDecision(
        false,
        AuthorizationContext.global().withRevision(1),
        List.of(missing),
        List.of(),
        List.of(),
        List.of(),
        Set.of("ACL_MISSING_PERMIT"),
        2,
        Instant.parse("2026-08-15T12:00:00Z"),
        UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
  }
}
