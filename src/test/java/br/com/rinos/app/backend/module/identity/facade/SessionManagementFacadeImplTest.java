package br.com.rinos.app.backend.module.identity.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.dto.SessionBulkRevocationRequestDTO;
import br.com.rinos.app.api.dto.SessionManagementContextDTO;
import br.com.rinos.app.api.dto.SessionRevocationRequestDTO;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthSessionStatusEnum;
import br.com.rinos.app.backend.module.identity.service.AuthSessionService;
import br.com.rinos.app.backend.module.identity.vo.AuthSessionRevocationVO;
import br.com.rinos.app.backend.module.identity.vo.AuthSessionSummaryVO;

/**
 * Verifica a projeção pública e o contexto obrigatório da gestão de sessões.
 *
 * @author Rodrigo Leitão
 */
class SessionManagementFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
  private static final UUID CURRENT = UUID.fromString("db49bf6d-b307-43d5-a34c-0f0b11424751");
  private static final UUID REMOTE = UUID.fromString("9e9feb95-14dc-420b-8492-6700a28081c1");
  private static final UUID CORRELATION = UUID.fromString("2d907943-5161-43bc-8297-44ef58c3f127");

  @Test
  void list_shouldMarkCurrentWithoutExposingTechnicalLocation() {
    AuthSessionService service = mock(AuthSessionService.class);
    SessionManagementContextDTO context = context();
    when(service.listManaged(42L, CURRENT, NOW)).thenReturn(List.of(
        session(CURRENT, "Current browser"), session(REMOTE, "Remote browser")));

    var result = new SessionManagementFacadeImpl(service).list(context);

    assertThat(result).hasSize(2);
    assertThat(result).filteredOn(session -> session.current())
        .singleElement()
        .satisfies(session -> assertThat(session.reference()).isEqualTo(CURRENT.toString()));
    assertThat(result).allSatisfy(session -> assertThat(session.locationDescription()).isNull());
  }

  @Test
  void revokeOperations_shouldForwardOnlyParsedScopedReferences() {
    AuthSessionService service = mock(AuthSessionService.class);
    SessionManagementFacadeImpl facade = new SessionManagementFacadeImpl(service);
    SessionRevocationRequestDTO one = new SessionRevocationRequestDTO(
        context(), REMOTE.toString(), CORRELATION);
    SessionBulkRevocationRequestDTO others = new SessionBulkRevocationRequestDTO(
        context(), true, CORRELATION);
    when(service.revokeManaged(42L, CURRENT, REMOTE, NOW, CORRELATION))
        .thenReturn(new AuthSessionRevocationVO(1, false));
    when(service.revokeAllManaged(42L, CURRENT, true, NOW, CORRELATION))
        .thenReturn(new AuthSessionRevocationVO(2, false));

    assertThat(facade.revoke(one).revokedCount()).isEqualTo(1);
    assertThat(facade.revokeAll(others).revokedCount()).isEqualTo(2);
    verify(service).revokeManaged(42L, CURRENT, REMOTE, NOW, CORRELATION);
    verify(service).revokeAllManaged(42L, CURRENT, true, NOW, CORRELATION);
  }

  private static SessionManagementContextDTO context() {
    return new SessionManagementContextDTO(42L, CURRENT.toString(), NOW);
  }

  private static AuthSessionSummaryVO session(UUID reference, String device) {
    return new AuthSessionSummaryVO(
        reference,
        false,
        AuthSessionStatusEnum.ACTIVE,
        AuthenticationMethodEnum.PASSWORD,
        AuthenticationAssuranceEnum.SINGLE_FACTOR,
        NOW.minusSeconds(60),
        NOW,
        NOW.plusSeconds(3600),
        device);
  }
}
