package br.com.rinos.app.ui.module.access.view;

import static br.eng.rodrigogml.rfw.i18n.vaadin.RFWTr.tr;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.Autocomplete;

import br.eng.rodrigogml.rfw.authentication.dto.RFWReauthenticationBeginRequestDTO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWReauthenticationVerificationRequestDTO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAuthenticationMethodEnum;
import br.eng.rodrigogml.rfw.authentication.enums.RFWReauthenticationStatusEnum;
import br.eng.rodrigogml.rfw.authentication.provider.RFWReauthenticationChallengeProvider;
import br.eng.rodrigogml.rfw.authentication.vo.RFWReauthenticationChallengeVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWReauthenticationOutcomeVO;
import br.eng.rodrigogml.rfw.ui.button.RFWButtonActionEnum;
import br.eng.rodrigogml.rfw.ui.gui.UIFactory;
import br.eng.rodrigogml.rfw.ui.access.passkey.RFWPasskeyComponent;
import br.eng.rodrigogml.rfw.ui.access.passkey.RFWPasskeyOperationEnum;

/** Reutiliza o contrato publico tipado do RFW para confirmar operacoes ACL sensiveis. */
final class AccessReauthenticationFlow {

  private final RFWReauthenticationChallengeProvider provider;

  AccessReauthenticationFlow(RFWReauthenticationChallengeProvider provider) {
    this.provider = provider;
  }

  void execute(UI ui, String operationId, Runnable confirmed, java.util.function.Consumer<String> error) {
    provider.begin(new RFWReauthenticationBeginRequestDTO(operationId)).whenComplete(
        (outcome, failure) -> ui.access(() -> {
          if (failure != null || outcome == null) {
            error.accept("access.reauthentication.unavailable");
          } else if (outcome.status() == RFWReauthenticationStatusEnum.ALREADY_RECENT) {
            confirmed.run();
          } else if (outcome.status() == RFWReauthenticationStatusEnum.CHALLENGE_REQUIRED) {
            open(ui, outcome.challenge(), confirmed, error);
          } else {
            error.accept(errorKey(outcome));
          }
        }));
  }

  private void open(
      UI ui, RFWReauthenticationChallengeVO challenge, Runnable confirmed,
      java.util.function.Consumer<String> error) {
    List<RFWAuthenticationMethodEnum> methods = challenge.allowedMethods().stream()
        .filter(method -> method == RFWAuthenticationMethodEnum.PASSWORD
            || method == RFWAuthenticationMethodEnum.TOTP
            || method == RFWAuthenticationMethodEnum.PASSKEY)
        .sorted(Comparator.comparingInt(AccessReauthenticationFlow::priority)).toList();
    if (methods.isEmpty()) {
      provider.cancel(challenge.challengeReference());
      error.accept("access.reauthentication.unavailable");
      return;
    }
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(tr("access.reauthentication.title"));
    dialog.setCloseOnOutsideClick(false);
    Paragraph operation = new Paragraph(tr(challenge.operationLabelKey()));
    RadioButtonGroup<RFWAuthenticationMethodEnum> selector = new RadioButtonGroup<>();
    selector.setLabel(tr("access.reauthentication.method"));
    selector.setItems(methods);
    selector.setItemLabelGenerator(AccessReauthenticationFlow::methodLabel);
    Div proof = new Div();
    Div feedback = new Div();
    feedback.getElement().setAttribute("aria-live", "polite");
    var confirm = UIFactory.createButton(RFWButtonActionEnum.CONFIRM);
    confirm.setVisible(false);
    var cancel = UIFactory.createButton(RFWButtonActionEnum.CANCEL);
    AtomicBoolean completed = new AtomicBoolean();
    final Component[] field = {null};
    selector.addValueChangeListener(event -> {
      proof.removeAll();
      feedback.removeAll();
      confirm.setVisible(false);
      if (event.getValue() == RFWAuthenticationMethodEnum.PASSWORD) {
        PasswordField password = new PasswordField(tr("access.reauthentication.password"));
        password.setAutocomplete(Autocomplete.CURRENT_PASSWORD);
        field[0] = password;
        proof.add(password);
        confirm.setVisible(true);
        password.focus();
      } else if (event.getValue() == RFWAuthenticationMethodEnum.TOTP) {
        TextField totp = new TextField(tr("access.reauthentication.totp"));
        totp.getElement().setAttribute("autocomplete", "one-time-code");
        field[0] = totp;
        proof.add(totp);
        confirm.setVisible(true);
        totp.focus();
      } else if (event.getValue() == RFWAuthenticationMethodEnum.PASSKEY) {
        RFWPasskeyComponent passkey = new RFWPasskeyComponent(
            RFWPasskeyOperationEnum.AUTHENTICATE, null,
            tr("access.reauthentication.passkey"), true);
        passkey.addCompletedListener(passkeyEvent -> verify(ui, dialog, feedback, challenge,
            RFWAuthenticationMethodEnum.PASSKEY, passkeyEvent.getCompletionReference(),
            completed, confirmed, error));
        field[0] = passkey;
        proof.add(passkey);
      }
    });
    confirm.addClickListener(event -> {
      String value = field[0] instanceof PasswordField password ? password.getValue()
          : field[0] instanceof TextField text ? text.getValue() : null;
      if (field[0] instanceof PasswordField password) password.clear();
      if (field[0] instanceof TextField text) text.clear();
      verify(ui, dialog, feedback, challenge, selector.getValue(), value,
          completed, confirmed, error);
    });
    cancel.addClickListener(event -> dialog.close());
    dialog.addOpenedChangeListener(event -> {
      if (!event.isOpened() && !completed.get()) provider.cancel(challenge.challengeReference());
    });
    VerticalLayout body = new VerticalLayout(operation, selector, proof, feedback);
    body.setPadding(false);
    dialog.add(body);
    dialog.getFooter().add(cancel, confirm);
    dialog.setWidth("min(36rem, 95vw)");
    dialog.open();
    selector.getElement().callJsFunction("focus");
  }

  private void verify(
      UI ui, Dialog dialog, Div feedback, RFWReauthenticationChallengeVO challenge,
      RFWAuthenticationMethodEnum method, String proof, AtomicBoolean completed,
      Runnable confirmed, java.util.function.Consumer<String> error) {
    if (method == null || proof == null || proof.isBlank()) {
      feedback.removeAll();
      feedback.add(UIFactory.createBanner(tr("access.reauthentication.required")));
      return;
    }
    provider.verify(new RFWReauthenticationVerificationRequestDTO(
        challenge.challengeReference(), method, proof)).whenComplete((outcome, failure) ->
            ui.access(() -> {
              if (failure == null && outcome != null
                  && outcome.status() == RFWReauthenticationStatusEnum.COMPLETED
                  && completed.compareAndSet(false, true)) {
                dialog.close();
                confirmed.run();
              } else {
                feedback.removeAll();
                feedback.add(UIFactory.createBanner(tr(failure == null && outcome != null
                    ? errorKey(outcome) : "access.reauthentication.unavailable")));
              }
            }));
  }

  private static int priority(RFWAuthenticationMethodEnum method) {
    return method == RFWAuthenticationMethodEnum.PASSKEY ? 0
        : method == RFWAuthenticationMethodEnum.TOTP ? 1 : 2;
  }

  private static String methodLabel(RFWAuthenticationMethodEnum method) {
    return tr("access.reauthentication.method." + method.name().toLowerCase(java.util.Locale.ROOT));
  }

  private static String errorKey(RFWReauthenticationOutcomeVO outcome) {
    if (outcome.errorKey() != null && !outcome.errorKey().isBlank()) return outcome.errorKey();
    return switch (outcome.status()) {
      case EXPIRED -> "access.reauthentication.expired";
      case CONFLICT -> "access.reauthentication.conflict";
      case ACCESS_DENIED -> "access.reauthentication.denied";
      case REJECTED, RATE_LIMITED -> "access.reauthentication.rejected";
      default -> "access.reauthentication.unavailable";
    };
  }
}
