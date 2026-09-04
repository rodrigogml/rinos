package br.com.rinos.app.ui.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vaadin.flow.router.BeforeEnterEvent;

import br.com.rinos.app.api.facade.FounderTotpEnrollmentFacade;
import br.com.rinos.app.api.vo.RinosUserPrincipalVO;
import br.com.rinos.app.ui.module.user.view.UserDashboardEntryView;
import br.com.rinos.app.ui.module.user.view.UserSecurityView;

@DisplayName("Guard de navegação do enrollment TOTP fundador")
class FounderTotpEnrollmentNavigationGuardTest {

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void guard_shouldRerouteFounderFromAnyAuthenticatedJourneyUntilTotpIsConfirmed() {
    FounderTotpEnrollmentFacade enrollment = mock(FounderTotpEnrollmentFacade.class);
    when(enrollment.requiresEnrollment(41L)).thenReturn(true);
    authenticate(41L);
    BeforeEnterEvent event = mock(BeforeEnterEvent.class);
    doReturn(UserDashboardEntryView.class).when(event).getNavigationTarget();

    new FounderTotpEnrollmentNavigationGuard(enrollment).guard(event);

    verify(event).rerouteTo(UserSecurityView.class);
  }

  @Test
  void guard_shouldKeepSecurityJourneyAvailableForFounderEnrollment() {
    FounderTotpEnrollmentFacade enrollment = mock(FounderTotpEnrollmentFacade.class);
    authenticate(41L);
    BeforeEnterEvent event = mock(BeforeEnterEvent.class);
    doReturn(UserSecurityView.class).when(event).getNavigationTarget();

    new FounderTotpEnrollmentNavigationGuard(enrollment).guard(event);

    verify(event, never()).rerouteTo(UserSecurityView.class);
    verify(enrollment, never()).requiresEnrollment(41L);
  }

  private static void authenticate(long userId) {
    RFWAuthenticatedPrincipalAdapter principal = new RFWAuthenticatedPrincipalAdapter(
        new RinosUserPrincipalVO(userId, "admin@rinos.com.br"), "opaque-session-reference");
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }
}
