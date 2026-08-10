package br.com.rinos.app.backend.module.identity.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.dto.PasskeyManagementContextDTO;
import br.com.rinos.app.api.dto.PasskeyRenameRequestDTO;
import br.com.rinos.app.api.dto.PasskeyRevocationRequestDTO;
import br.com.rinos.app.api.enums.PasskeyManagementStatusEnum;
import br.com.rinos.app.api.vo.PasskeyVO;
import br.com.rinos.app.backend.module.identity.enums.FactorOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.PasskeyCredentialStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.ReauthenticationOperationEnum;
import br.com.rinos.app.backend.module.identity.service.AuthSessionService;
import br.com.rinos.app.backend.module.identity.service.PasskeyCredentialService;
import br.com.rinos.app.backend.module.identity.service.ReauthenticationService;
import br.com.rinos.app.backend.module.identity.vo.PasskeyCredentialSummaryVO;

@DisplayName("Fachada de gestao de passkeys")
class PasskeyManagementFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-08-10T20:00:00Z");
  private static final UUID SESSION = UUID.fromString("58a06f7d-c288-45fb-ab2f-7773a4abac14");
  private static final UUID PASSKEY = UUID.fromString("cc3d2029-2b13-41e5-ae98-6d91723eb339");
  private AuthSessionService sessionService;
  private ReauthenticationService reauthenticationService;
  private PasskeyCredentialService passkeyService;
  private PasskeyManagementFacadeImpl facade;

  @BeforeEach
  void setUp() {
    sessionService = mock(AuthSessionService.class);
    reauthenticationService = mock(ReauthenticationService.class);
    passkeyService = mock(PasskeyCredentialService.class);
    facade = new PasskeyManagementFacadeImpl(
        sessionService, reauthenticationService, passkeyService);
  }

  @Test
  void list_shouldExposeOnlySafeFields_afterValidatingCurrentSession() {
    when(passkeyService.list(41L)).thenReturn(List.of(new PasskeyCredentialSummaryVO(
        PASSKEY,
        "Notebook",
        PasskeyCredentialStatusEnum.ACTIVE,
        NOW.minusSeconds(600),
        NOW.minusSeconds(60))));

    List<PasskeyVO> result = facade.list(context());

    assertThat(result).singleElement().satisfies(passkey -> {
      assertThat(passkey.reference()).isEqualTo(PASSKEY.toString());
      assertThat(passkey.label()).isEqualTo("Notebook");
      assertThat(passkey.state().name()).isEqualTo("ACTIVE");
    });
    verify(sessionService).listManaged(41L, SESSION, NOW);
  }

  @Test
  void rename_shouldRejectOperation_whenRecentAuthenticationIsNoLongerValid() {
    when(reauthenticationService.isRecentlyAuthorized(
        41L, SESSION, ReauthenticationOperationEnum.RENAME_PASSKEY, NOW))
        .thenReturn(false);

    PasskeyManagementStatusEnum status = facade.rename(new PasskeyRenameRequestDTO(
        context(), PASSKEY.toString(), "Chave principal", UUID.randomUUID())).status();

    assertThat(status).isEqualTo(PasskeyManagementStatusEnum.ACCESS_DENIED);
    verify(passkeyService, never()).rename(any(), any(), any(), any(), any());
  }

  @Test
  void rename_shouldChangeOnlyOwnedActivePasskey_whenAssuranceRemainsRecent() {
    when(reauthenticationService.isRecentlyAuthorized(
        41L, SESSION, ReauthenticationOperationEnum.RENAME_PASSKEY, NOW))
        .thenReturn(true);
    UUID correlationId = UUID.randomUUID();

    PasskeyManagementStatusEnum status = facade.rename(new PasskeyRenameRequestDTO(
        context(), PASSKEY.toString(), "Chave principal", correlationId)).status();

    assertThat(status).isEqualTo(PasskeyManagementStatusEnum.COMPLETED);
    verify(passkeyService).rename(
        41L, PASSKEY, "Chave principal", correlationId, NOW);
  }

  @Test
  void revoke_shouldPreserveLastUsableMethod_afterConcurrentRevalidation() {
    when(reauthenticationService.isRecentlyAuthorized(
        41L, SESSION, ReauthenticationOperationEnum.REVOKE_PASSKEY, NOW))
        .thenReturn(true);
    when(passkeyService.revoke(eq(41L), eq(PASSKEY), eq(false), any(), eq(NOW)))
        .thenReturn(FactorOperationStatusEnum.LAST_METHOD);

    PasskeyManagementStatusEnum status = facade.revoke(new PasskeyRevocationRequestDTO(
        context(), PASSKEY.toString(), UUID.randomUUID())).status();

    assertThat(status).isEqualTo(PasskeyManagementStatusEnum.LAST_METHOD);
  }

  private static PasskeyManagementContextDTO context() {
    return new PasskeyManagementContextDTO(41L, SESSION.toString(), NOW);
  }
}
