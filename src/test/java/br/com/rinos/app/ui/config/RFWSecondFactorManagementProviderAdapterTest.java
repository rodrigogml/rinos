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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import br.com.rinos.app.api.dto.TotpEnrollmentCancellationDTO;
import br.com.rinos.app.api.enums.TotpEnrollmentStatusEnum;
import br.com.rinos.app.api.facade.TotpManagementFacade;
import br.com.rinos.app.api.vo.RinosUserPrincipalVO;
import br.com.rinos.app.api.vo.TotpEnrollmentResultVO;
import br.com.rinos.app.api.vo.TotpFactorVO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAuthenticationMethodEnum;
import br.eng.rodrigogml.rfw.authentication.enums.RFWSecurityManagementStatusEnum;

@DisplayName("Provider RFW de gestão TOTP")
class RFWSecondFactorManagementProviderAdapterTest {

  private static final Instant NOW = Instant.parse("2026-08-09T19:00:00Z");
  private TotpManagementFacade facade;
  private RFWSecondFactorManagementProviderAdapter adapter;

  @BeforeEach
  void setUp() {
    facade = mock(TotpManagementFacade.class);
    adapter = new RFWSecondFactorManagementProviderAdapter(
        facade, Clock.fixed(NOW, ZoneOffset.UTC));
    var principal = new RFWAuthenticatedPrincipalAdapter(
        new RinosUserPrincipalVO(41L, "user@example.test"),
        "0ddb5d70-6f51-4f11-8c1b-17bb4ce72156");
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void begin_shouldMapOneTimePresentationFromCurrentPrincipal() throws Exception {
    when(facade.begin(any())).thenReturn(new TotpEnrollmentResultVO(
        TotpEnrollmentStatusEnum.PENDING,
        "6e89f399-c679-495c-8c42-b17f2db18996",
        NOW.plusSeconds(300),
        "otpauth://totp/Rinos:user",
        "SECRET"));

    var enrollment = adapter.beginEnrollmentDetails(RFWAuthenticationMethodEnum.TOTP)
        .toCompletableFuture().get();

    assertThat(enrollment.enrollmentReference())
        .isEqualTo("6e89f399-c679-495c-8c42-b17f2db18996");
    assertThat(enrollment.manualSecret()).isEqualTo("SECRET");
    assertThat(enrollment.provisioningUri()).startsWith("otpauth://");
    verify(facade).begin(any());
  }

  @Test
  void confirm_shouldMapInvalidAndTerminalEnrollmentsWithoutLeakingProof() throws Exception {
    when(facade.confirm(any())).thenReturn(TotpEnrollmentResultVO.terminal(
        TotpEnrollmentStatusEnum.REJECTED));

    var rejected = adapter.confirmEnrollmentOutcome("reference", "000000")
        .toCompletableFuture().get();

    assertThat(rejected.status()).isEqualTo(RFWSecurityManagementStatusEnum.REJECTED);

    when(facade.confirm(any())).thenReturn(TotpEnrollmentResultVO.terminal(
        TotpEnrollmentStatusEnum.ATTEMPTS_EXHAUSTED));
    var exhausted = adapter.confirmEnrollmentOutcome("reference", "000000")
        .toCompletableFuture().get();
    assertThat(exhausted.status()).isEqualTo(RFWSecurityManagementStatusEnum.STALE);
    assertThat(exhausted.refreshRequired()).isTrue();
  }

  @Test
  void listAndCancel_shouldUseOnlyCurrentAuthenticatedUser() throws Exception {
    when(facade.listActive(41L)).thenReturn(List.of(new TotpFactorVO(
        "factor-reference", "Aplicativo autenticador", NOW.minusSeconds(60), NOW)));

    var listed = adapter.listFactorsOutcome().toCompletableFuture().get();
    adapter.cancelEnrollment("pending-reference").toCompletableFuture().get();

    assertThat(listed.value()).singleElement().satisfies(method -> {
      assertThat(method.type()).isEqualTo(RFWAuthenticationMethodEnum.TOTP);
      assertThat(method.enabled()).isTrue();
    });
    verify(facade).cancel(new TotpEnrollmentCancellationDTO(
        41L, "pending-reference", NOW));
  }

  @Test
  void operations_shouldFailClosedWithoutAuthenticatedPrincipal() throws Exception {
    SecurityContextHolder.clearContext();

    var listed = adapter.listFactorsOutcome().toCompletableFuture().get();
    var confirmed = adapter.confirmEnrollmentOutcome("reference", "123456")
        .toCompletableFuture().get();

    assertThat(listed.status()).isEqualTo(RFWSecurityManagementStatusEnum.INSUFFICIENT_ASSURANCE);
    assertThat(confirmed.status()).isEqualTo(RFWSecurityManagementStatusEnum.INSUFFICIENT_ASSURANCE);
    assertThat(adapter.beginEnrollmentDetails(RFWAuthenticationMethodEnum.TOTP).toCompletableFuture())
        .failsWithin(java.time.Duration.ofSeconds(1));
  }
}
