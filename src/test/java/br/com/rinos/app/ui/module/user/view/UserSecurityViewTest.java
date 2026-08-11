package br.com.rinos.app.ui.module.user.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.dom.Element;

import br.eng.rodrigogml.rfw.ui.securitysettings.RFWSecuritySettingsComponent;
import br.eng.rodrigogml.rfw.ui.securitysettings.RFWSecuritySettingsComponentFactory;

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
    when(factory.create()).thenReturn(component);

    UserSecurityView view = new UserSecurityView(factory);

    assertThat(UserSecurityView.ROUTE).isEqualTo("user/security");
    verify(factory).create();
    assertThat(view).isNotNull();
  }
}
