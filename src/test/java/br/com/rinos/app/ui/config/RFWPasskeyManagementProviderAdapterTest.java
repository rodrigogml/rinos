package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import br.com.rinos.app.api.dto.PasskeyRenameRequestDTO;
import br.com.rinos.app.api.enums.PasskeyManagementStatusEnum;
import br.com.rinos.app.api.enums.PasskeyStateEnum;
import br.com.rinos.app.api.facade.PasskeyManagementFacade;
import br.com.rinos.app.api.vo.PasskeyManagementResultVO;
import br.com.rinos.app.api.vo.PasskeyVO;
import br.com.rinos.app.api.vo.RinosUserPrincipalVO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAuthenticationMethodEnum;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAuthenticationMethodStateEnum;
import br.eng.rodrigogml.rfw.authentication.enums.RFWSecurityManagementStatusEnum;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationMethodVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWSecurityManagementOutcomeVO;

@DisplayName("Provider RFW de gestao de passkeys")
class RFWPasskeyManagementProviderAdapterTest {

  private static final Instant NOW = Instant.parse("2026-08-10T20:30:00Z");
  private static final String SESSION = "58a06f7d-c288-45fb-ab2f-7773a4abac14";
  private PasskeyManagementFacade facade;
  private RFWPasskeyManagementProviderAdapter adapter;

  @BeforeEach
  void setUp() {
    facade = mock(PasskeyManagementFacade.class);
    adapter = new RFWPasskeyManagementProviderAdapter(
        facade, Clock.fixed(NOW, ZoneOffset.UTC));
    RFWAuthenticatedPrincipalAdapter principal = new RFWAuthenticatedPrincipalAdapter(
        new RinosUserPrincipalVO(41L, "user@example.test"), SESSION);
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void list_shouldMapSafeStateAndTimestamps_fromCurrentAuthenticatedUser() throws Exception {
    when(facade.list(any())).thenReturn(List.of(
        new PasskeyVO("active-reference", "Notebook", NOW.minusSeconds(600), NOW,
            PasskeyStateEnum.ACTIVE),
        new PasskeyVO("revoked-reference", "Chave antiga", NOW.minusSeconds(1200), null,
            PasskeyStateEnum.REVOKED)));

    List<RFWAuthenticationMethodVO> values =
        adapter.listPasskeysOutcome().toCompletableFuture().get().value();

    assertThat(values).hasSize(2);
    assertThat(values.getFirst().type()).isEqualTo(RFWAuthenticationMethodEnum.PASSKEY);
    assertThat(values.getFirst().state()).isEqualTo(RFWAuthenticationMethodStateEnum.ACTIVE);
    assertThat(values.getFirst().lastUsedAt()).isEqualTo(NOW);
    assertThat(values.getLast().state()).isEqualTo(RFWAuthenticationMethodStateEnum.REVOKED);
    assertThat(values.getLast().enabled()).isFalse();
  }

  @Test
  void rename_shouldDeriveIdentitySessionAndTime_withoutAcceptingThemFromComponent() throws Exception {
    when(facade.rename(any())).thenReturn(new PasskeyManagementResultVO(
        PasskeyManagementStatusEnum.COMPLETED));

    RFWSecurityManagementStatusEnum status = adapter
        .renamePasskeyOutcome("passkey-reference", "Chave principal")
        .toCompletableFuture().get().status();

    assertThat(status).isEqualTo(RFWSecurityManagementStatusEnum.COMPLETED);
    ArgumentCaptor<PasskeyRenameRequestDTO> request =
        ArgumentCaptor.forClass(PasskeyRenameRequestDTO.class);
    verify(facade).rename(request.capture());
    assertThat(request.getValue().context().userId()).isEqualTo(41L);
    assertThat(request.getValue().context().currentSessionReference()).isEqualTo(SESSION);
    assertThat(request.getValue().context().occurredAt()).isEqualTo(NOW);
    assertThat(request.getValue().passkeyReference()).isEqualTo("passkey-reference");
  }

  @Test
  void revoke_shouldMapLastMethodAndStaleResults_withoutDeletingOtherCredentials()
      throws Exception {
    when(facade.revoke(any())).thenReturn(new PasskeyManagementResultVO(
        PasskeyManagementStatusEnum.LAST_METHOD));

    RFWSecurityManagementOutcomeVO<Void> lastMethod =
        adapter.revokePasskeyOutcome("passkey-reference").toCompletableFuture().get();
    assertThat(lastMethod.status()).isEqualTo(RFWSecurityManagementStatusEnum.LAST_METHOD);

    when(facade.revoke(any())).thenReturn(new PasskeyManagementResultVO(
        PasskeyManagementStatusEnum.STALE));
    RFWSecurityManagementOutcomeVO<Void> stale =
        adapter.revokePasskeyOutcome("passkey-reference").toCompletableFuture().get();
    assertThat(stale.status()).isEqualTo(RFWSecurityManagementStatusEnum.STALE);
    assertThat(stale.refreshRequired()).isTrue();
  }

  @Test
  void operations_shouldFailClosed_withoutAuthenticatedPrincipal() throws Exception {
    SecurityContextHolder.clearContext();

    assertThat(adapter.listPasskeysOutcome().toCompletableFuture().get().status())
        .isEqualTo(RFWSecurityManagementStatusEnum.INSUFFICIENT_ASSURANCE);
    assertThat(adapter.renamePasskeyOutcome("reference", "label").toCompletableFuture().get().status())
        .isEqualTo(RFWSecurityManagementStatusEnum.INSUFFICIENT_ASSURANCE);
    assertThat(adapter.revokePasskeyOutcome("reference").toCompletableFuture().get().status())
        .isEqualTo(RFWSecurityManagementStatusEnum.INSUFFICIENT_ASSURANCE);
  }
}
