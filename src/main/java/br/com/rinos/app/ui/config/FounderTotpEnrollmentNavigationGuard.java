package br.com.rinos.app.ui.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;

import br.com.rinos.app.api.facade.FounderTotpEnrollmentFacade;
import br.com.rinos.app.ui.module.user.view.UserSecurityView;

/**
 * Limita globalmente a navegação do fundador até a confirmação do primeiro fator TOTP.
 *
 * <p>O guard é aplicado a cada nova UI e não depende de esconder links. A única rota permitida
 * durante a restrição é a tela RFW de segurança, configurada para expor apenas segundo fator.
 *
 * @author Rodrigo Leitão
 * @since 2026-09-01
 */
@Component
public class FounderTotpEnrollmentNavigationGuard implements VaadinServiceInitListener {

  private final FounderTotpEnrollmentFacade enrollment;

  /** Cria o guard sobre a decisão pública e minimizada da identidade atual. */
  public FounderTotpEnrollmentNavigationGuard(FounderTotpEnrollmentFacade enrollment) {
    this.enrollment = enrollment;
  }

  /** {@inheritDoc} */
  @Override
  public void serviceInit(ServiceInitEvent event) {
    event.getSource().addUIInitListener(initialization ->
        initialization.getUI().addBeforeEnterListener(this::guard));
  }

  /**
   * Redireciona somente o fundador ainda pendente para a rota que permite concluir o TOTP.
   *
   * @param event navegação que será avaliada antes de renderizar a rota solicitada
   */
  void guard(BeforeEnterEvent event) {
    if (event.getNavigationTarget() == UserSecurityView.class) {
      return;
    }
    RFWAuthenticatedPrincipalAdapter principal = principal();
    if (principal != null && enrollment.requiresEnrollment(principal.user().userId())) {
      event.rerouteTo(UserSecurityView.class);
    }
  }

  private static RFWAuthenticatedPrincipalAdapter principal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof RFWAuthenticatedPrincipalAdapter principal
            ? principal : null;
  }
}
