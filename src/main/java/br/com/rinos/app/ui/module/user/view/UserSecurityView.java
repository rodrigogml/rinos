package br.com.rinos.app.ui.module.user.view;

import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import br.eng.rodrigogml.rfw.ui.securitysettings.RFWSecuritySettingsComponentFactory;

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
   */
  public UserSecurityView(RFWSecuritySettingsComponentFactory componentFactory) {
    add(componentFactory.create());
    setSizeFull();
  }
}
