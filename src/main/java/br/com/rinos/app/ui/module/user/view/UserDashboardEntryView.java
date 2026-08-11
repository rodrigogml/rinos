package br.com.rinos.app.ui.module.user.view;

import jakarta.annotation.security.PermitAll;

import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * Reserva a rota global autenticada que será composta pela feature Painel de Usuário.
 *
 * <p>Esta entrada não publica conteúdo, dados nem operações do painel. Sua única responsabilidade
 * é oferecer um destino estável ao término seguro dos fluxos de autenticação.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-01
 */
@Route(UserDashboardEntryView.ROUTE)
@PageTitle("Rinos")
@PermitAll
public class UserDashboardEntryView extends Main {

  /** Rota global reservada para o Painel de Usuário. */
  public static final String ROUTE = "user";

  /**
   * Cria a entrada mínima do painel e seu enlace para segurança.
   */
  public UserDashboardEntryView() {
    add(new Paragraph("Painel de Usuário"),
        new RouterLink("Configurações de segurança", UserSecurityView.class));
  }
}
