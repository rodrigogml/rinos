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
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import br.com.rinos.app.api.dto.ExternalIdentityLinkRequestDTO;
import br.com.rinos.app.api.enums.ExternalIdentityManagementStatusEnum;
import br.com.rinos.app.api.facade.ExternalIdentityManagementFacade;
import br.com.rinos.app.api.vo.ExternalIdentityManagementResultVO;
import br.com.rinos.app.api.vo.ExternalIdentityVO;
import br.com.rinos.app.api.vo.RinosUserPrincipalVO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAuthenticationMethodEnum;
import br.eng.rodrigogml.rfw.authentication.enums.RFWSecurityManagementStatusEnum;
import br.eng.rodrigogml.rfw.authentication.vo.RFWVerifiedExternalIdentityVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWSecurityManagementOutcomeVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationMethodVO;

@DisplayName("Provider RFW de gestão de identidades externas")
class RFWExternalIdentityManagementProviderAdapterTest {

  private static final Instant NOW = Instant.parse("2026-08-10T21:30:00Z");
  private static final String SESSION = "01d89fe9-875e-4dca-8984-8d61d22dab90";
  private ExternalIdentityManagementFacade facade;
  private RFWExternalIdentityManagementProviderAdapter adapter;

  @BeforeEach
  void setUp() {
    facade = mock(ExternalIdentityManagementFacade.class);
    adapter = new RFWExternalIdentityManagementProviderAdapter(
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
  void list_shouldExposeOpaqueReferenceAndSafeGoogleLabel() throws Exception {
    when(facade.list(any())).thenReturn(List.of(new ExternalIdentityVO(
        "6a230ff3-4a9c-4d0c-85d8-64328e561593", "google", NOW.minusSeconds(60), NOW)));

    RFWSecurityManagementOutcomeVO<List<RFWAuthenticationMethodVO>> outcome =
        adapter.listExternalIdentitiesOutcome().toCompletableFuture().get();

    assertThat(outcome.value()).hasSize(1);
    assertThat(outcome.value().getFirst().id())
        .isEqualTo("6a230ff3-4a9c-4d0c-85d8-64328e561593");
    assertThat(outcome.value().getFirst().type()).isEqualTo(RFWAuthenticationMethodEnum.GOOGLE);
    assertThat(outcome.value().getFirst().label()).isEqualTo("Google");
  }

  @Test
  void link_shouldDiscardEmailAndClaimsAndDeclareRfwConfirmation() throws Exception {
    when(facade.link(any())).thenReturn(new ExternalIdentityManagementResultVO(
        ExternalIdentityManagementStatusEnum.COMPLETED));
    RFWVerifiedExternalIdentityVO identity = new RFWVerifiedExternalIdentityVO(
        "google",
        "stable-subject",
        "must-not-cross@example.test",
        true,
        Map.of("iss", "https://accounts.google.com", "name", "Must not cross"));

    assertThat(adapter.linkOutcome(identity).toCompletableFuture().get().status())
        .isEqualTo(RFWSecurityManagementStatusEnum.COMPLETED);

    ArgumentCaptor<ExternalIdentityLinkRequestDTO> request =
        ArgumentCaptor.forClass(ExternalIdentityLinkRequestDTO.class);
    verify(facade).link(request.capture());
    assertThat(request.getValue().context().userId()).isEqualTo(41L);
    assertThat(request.getValue().context().currentSessionReference()).isEqualTo(SESSION);
    assertThat(request.getValue().context().occurredAt()).isEqualTo(NOW);
    assertThat(request.getValue().issuer()).isEqualTo("https://accounts.google.com");
    assertThat(request.getValue().subject()).isEqualTo("stable-subject");
    assertThat(request.getValue().explicitlyConfirmed()).isTrue();
  }

  @Test
  void unlink_shouldMapLastMethodAndConflictOutcomes() throws Exception {
    when(facade.unlink(any())).thenReturn(new ExternalIdentityManagementResultVO(
        ExternalIdentityManagementStatusEnum.LAST_METHOD));
    assertThat(adapter.unlinkOutcome("reference").toCompletableFuture().get().status())
        .isEqualTo(RFWSecurityManagementStatusEnum.LAST_METHOD);

    when(facade.link(any())).thenReturn(new ExternalIdentityManagementResultVO(
        ExternalIdentityManagementStatusEnum.CONFLICT));
    RFWVerifiedExternalIdentityVO identity = new RFWVerifiedExternalIdentityVO(
        "google", "subject", null, false, Map.of("iss", "https://accounts.google.com"));
    assertThat(adapter.linkOutcome(identity).toCompletableFuture().get().status())
        .isEqualTo(RFWSecurityManagementStatusEnum.CONFLICT);
  }

  @Test
  void operations_shouldFailClosedWithoutAuthenticatedPrincipal() throws Exception {
    SecurityContextHolder.clearContext();

    assertThat(adapter.listExternalIdentitiesOutcome().toCompletableFuture().get().status())
        .isEqualTo(RFWSecurityManagementStatusEnum.INSUFFICIENT_ASSURANCE);
    assertThat(adapter.unlinkOutcome("reference").toCompletableFuture().get().status())
        .isEqualTo(RFWSecurityManagementStatusEnum.INSUFFICIENT_ASSURANCE);
  }
}
