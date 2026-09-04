package br.com.rinos.app.ui.module.user.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.vaadin.flow.dom.Element;

import br.eng.rodrigogml.rfw.ui.securitysettings.RFWSecuritySettingsComponent;
import br.eng.rodrigogml.rfw.ui.securitysettings.RFWSecuritySettingsComponentFactory;
import br.eng.rodrigogml.rfw.ui.securitysettings.config.RFWSecuritySettingsComponentConfig;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAuthenticationMethodEnum;
import br.com.rinos.app.api.facade.FounderTotpEnrollmentFacade;

/**
 * Verifica a rota autenticada que hospeda as configurações RFW.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-11
 */
class UserSecurityViewTest {

  @Test
  void route_shouldUseRfwFactoryComponent() {
    RFWSecuritySettingsComponentFactory factory = mock(RFWSecuritySettingsComponentFactory.class);
    RFWSecuritySettingsComponent component = mock(RFWSecuritySettingsComponent.class);
    when(component.getElement()).thenReturn(new Element("div"));
    when(factory.create(any())).thenReturn(component);

    UserSecurityView view = new UserSecurityView(factory, mock(FounderTotpEnrollmentFacade.class));

    assertThat(UserSecurityView.ROUTE).isEqualTo("user/security");
    ArgumentCaptor<RFWSecuritySettingsComponentConfig> configuration =
        ArgumentCaptor.forClass(RFWSecuritySettingsComponentConfig.class);
    verify(factory).create(configuration.capture());
    assertThat(configuration.getValue().getFactorEnrollmentMethods())
        .isEqualTo(Set.of(RFWAuthenticationMethodEnum.TOTP));
    assertThat(view).isNotNull();
  }
}
