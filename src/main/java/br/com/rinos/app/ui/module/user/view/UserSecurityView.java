package br.com.rinos.app.ui.module.user.view;

import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import br.com.rinos.app.api.facade.FounderTotpEnrollmentFacade;
import br.com.rinos.app.ui.config.RFWAuthenticatedPrincipalAdapter;
import br.eng.rodrigogml.rfw.ui.securitysettings.RFWSecuritySettingsSectionEnum;
import br.eng.rodrigogml.rfw.ui.securitysettings.RFWSecuritySettingsComponentFactory;
import br.eng.rodrigogml.rfw.ui.securitysettings.config.RFWSecuritySettingsComponentConfig;

/**
 * Apresenta as configurações de segurança do usuário usando exclusivamente o componente RFW.
 * A autorização efetiva permanece no contexto autenticado do Spring Security.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-11
 */
@Route(UserSecurityView.ROUTE)
@PageTitle("Segurança do usuário")
@PermitAll
public class UserSecurityView extends Main {

  /** Rota estável das configurações de segurança. */
  public static final String ROUTE = "user/security";

  /**
   * Cria a tela com a factory compartilhada do RFW.
   *
   * @param componentFactory factory de configurações RFW
   * @param founderEnrollment consulta que restringe o fundador ao cadastro obrigatório do TOTP
   */
  public UserSecurityView(
      RFWSecuritySettingsComponentFactory componentFactory,
      FounderTotpEnrollmentFacade founderEnrollment) {
    add(componentFactory.create(restrictedConfiguration(founderEnrollment)));
    setSizeFull();
  }

  private static RFWSecuritySettingsComponentConfig restrictedConfiguration(
      FounderTotpEnrollmentFacade founderEnrollment) {
    RFWAuthenticatedPrincipalAdapter principal = currentPrincipal();
    if (principal == null || !founderEnrollment.requiresEnrollment(principal.user().userId())) {
      return RFWSecuritySettingsComponentConfig.builder().build();
    }
    return RFWSecuritySettingsComponentConfig.builder()
        .disableSection(RFWSecuritySettingsSectionEnum.PASSWORD)
        .disableSection(RFWSecuritySettingsSectionEnum.PASSKEYS)
        .disableSection(RFWSecuritySettingsSectionEnum.EXTERNAL_IDENTITIES)
        .disableSection(RFWSecuritySettingsSectionEnum.SESSIONS)
        .disableSection(RFWSecuritySettingsSectionEnum.RECOVERY_CODES)
        .build();
  }

  private static RFWAuthenticatedPrincipalAdapter currentPrincipal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof RFWAuthenticatedPrincipalAdapter principal
            ? principal : null;
  }
}
