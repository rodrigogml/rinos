package br.com.rinos.app.ui.module.identity.view;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;

/**
 * Percorre a interface real do cadastro em navegador Chromium e registra evidências visuais.
 *
 * <p>O teste é opt-in porque requer os binários locais do Playwright. Execute com
 * {@code -Drinos.ui.e2e.enabled=true}; o build padrão continua independente de navegador.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@SpringBootTest(
    classes = RegistrationUiTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = "vaadin.launch-browser=false")
@EnabledIfSystemProperty(named = "rinos.ui.e2e.enabled", matches = "true")
@DisplayName("Jornadas E2E do cadastro local, retomada e Google")
class RegistrationViewE2EIT {

  private static Playwright playwright;
  private static Browser browser;

  @LocalServerPort
  private int port;

  /**
   * Inicializa um único navegador headless para as jornadas da classe.
   */
  @BeforeAll
  static void startBrowser() {
    playwright = Playwright.create();
    browser = playwright.chromium().launch(
        new BrowserType.LaunchOptions().setHeadless(true));
  }

  /**
   * Libera os processos nativos do Playwright ao final da suíte.
   */
  @AfterAll
  static void stopBrowser() {
    if (browser != null) {
      browser.close();
    }
    if (playwright != null) {
      playwright.close();
    }
  }

  /**
   * Valida conteúdo, erro local, foco, submissão e transição nominal em viewport desktop.
   *
   * @throws Exception quando a pasta de evidências temporárias não puder ser criada
   */
  @Test
  void registration_shouldValidateAndReachActivation_onDesktop() throws Exception {
    Path evidenceDirectory = Files.createDirectories(Path.of("target", "ui-evidence"));
    try (BrowserContext context = browser.newContext(
        new Browser.NewContextOptions().setViewportSize(1440, 1000))) {
      Page page = context.newPage();
      openRegistration(page);

      assertThat(page.getByRole(AriaRole.HEADING,
          new Page.GetByRoleOptions().setName("Crie sua conta"))).isVisible();
      assertThat(page.getByText(
          "Use de 10 a 128 caracteres",
          new Page.GetByTextOptions().setExact(false))).isVisible();
      assertThat(page.locator("[data-rfw-legal-document-id='terms-v1']")).isVisible();
      assertThat(page.locator("[data-rfw-legal-document-id='privacy-v1']")).isVisible();
      assertNoHorizontalOverflow(page);

      page.getByLabel("E-mail").fill("pessoa@example.com");
      page.getByLabel("Nova senha").fill("Senha!1234");
      page.getByLabel("Confirmar senha").fill("Senha!5678");
      page.getByRole(AriaRole.BUTTON,
          new Page.GetByRoleOptions().setName("Criar conta").setExact(true)).click();

      Locator confirmation = page.getByLabel("Confirmar senha");
      assertThat(confirmation).hasAttribute("invalid", "");
      assertThat(confirmation).isFocused();
      page.screenshot(new Page.ScreenshotOptions()
          .setPath(evidenceDirectory.resolve("registration-validation-desktop.png"))
          .setFullPage(true));

      confirmation.fill("Senha!1234");
      Locator termsAcceptance = page.locator(
          "[data-rfw-legal-document-id='terms-v1']").locator("input[type='checkbox']");
      Locator privacyAcceptance = page.locator(
          "[data-rfw-legal-document-id='privacy-v1']").locator("input[type='checkbox']");
      termsAcceptance.check();
      privacyAcceptance.check();
      assertThat(termsAcceptance).isChecked();
      assertThat(privacyAcceptance).isChecked();
      page.screenshot(new Page.ScreenshotOptions()
          .setPath(evidenceDirectory.resolve("registration-ready-desktop.png"))
          .setFullPage(true));
      page.getByRole(AriaRole.BUTTON,
          new Page.GetByRoleOptions().setName("Criar conta").setExact(true)).click();

      assertThat(page.locator("[data-rfw-access-step='activation']")).isVisible();
      assertThat(page.getByText("Enviamos as instruções de confirmação",
          new Page.GetByTextOptions().setExact(false))).isVisible();
      page.screenshot(new Page.ScreenshotOptions()
          .setPath(evidenceDirectory.resolve("registration-activation-desktop.png"))
          .setFullPage(true));
    }
  }

  /**
   * Confirma o reflow da mesma composição e registra a evidência em viewport de telefone.
   *
   * @throws Exception quando a pasta de evidências temporárias não puder ser criada
   */
  @Test
  void registration_shouldReflowWithoutHorizontalOverflow_onPhone() throws Exception {
    Path evidenceDirectory = Files.createDirectories(Path.of("target", "ui-evidence"));
    try (BrowserContext context = browser.newContext(
        new Browser.NewContextOptions()
            .setViewportSize(390, 844)
            .setDeviceScaleFactor(1))) {
      Page page = context.newPage();
      openRegistration(page);

      assertNoHorizontalOverflow(page);
      assertThat(page.getByLabel("E-mail")).isVisible();
      assertThat(page.getByRole(AriaRole.BUTTON,
          new Page.GetByRoleOptions().setName("Criar conta").setExact(true))).isVisible();
      page.screenshot(new Page.ScreenshotOptions()
          .setPath(evidenceDirectory.resolve("registration-ready-phone.png"))
          .setFullPage(true));
    }
  }

  /**
   * Retoma pelo deep link, remove a prova da URL e conclui a ativação em desktop.
   *
   * @throws Exception quando a pasta de evidências temporárias não puder ser criada
   */
  @Test
  void activation_shouldResumeFromDeepLinkAndComplete_onDesktop() throws Exception {
    Path evidenceDirectory = Files.createDirectories(Path.of("target", "ui-evidence"));
    try (BrowserContext context = browser.newContext(
        new Browser.NewContextOptions().setViewportSize(1440, 1000))) {
      Page page = context.newPage();
      page.navigate("http://127.0.0.1:" + port
          + "/login?step=activation&proof=opaque-resume-proof");

      assertThat(page.locator("[data-rfw-access-step='activation']")).isVisible();
      Locator proof = page.getByRole(AriaRole.TEXTBOX,
          new Page.GetByRoleOptions()
              .setName("Código de ativação")
              .setExact(true));
      assertThat(proof).hasValue("opaque-resume-proof");
      assertThat(proof).hasAttribute("autocomplete", "one-time-code");
      Locator activate = page.getByRole(AriaRole.BUTTON,
          new Page.GetByRoleOptions().setName("Ativar conta").setExact(true));
      assertThat(activate).isFocused();
      org.assertj.core.api.Assertions.assertThat(page.url())
          .isEqualTo("http://127.0.0.1:" + port + "/login");
      assertNoHorizontalOverflow(page);
      page.screenshot(new Page.ScreenshotOptions()
          .setPath(evidenceDirectory.resolve("activation-deep-link-desktop.png"))
          .setFullPage(true));

      activate.click();

      assertThat(page.getByText(
          "Seu cadastro foi ativado com sucesso.",
          new Page.GetByTextOptions().setExact(true))).isVisible();
      org.assertj.core.api.Assertions.assertThat(page.content())
          .doesNotContain("opaque-resume-proof");
      org.assertj.core.api.Assertions.assertThat(page.url())
          .doesNotContain("proof=");
    }
  }

  /**
   * Retoma manualmente, confirma o foco e solicita reenvio sem sair da ativação em telefone.
   *
   * @throws Exception quando a pasta de evidências temporárias não puder ser criada
   */
  @Test
  void activation_shouldResumeManuallyAndResend_onPhone() throws Exception {
    Path evidenceDirectory = Files.createDirectories(Path.of("target", "ui-evidence"));
    try (BrowserContext context = browser.newContext(
        new Browser.NewContextOptions()
            .setViewportSize(390, 844)
            .setDeviceScaleFactor(1))) {
      Page page = context.newPage();
      page.navigate("http://127.0.0.1:" + port + "/login?step=activation");

      assertThat(page.locator("[data-rfw-access-step='activation']")).isVisible();
      Locator proof = page.getByRole(AriaRole.TEXTBOX,
          new Page.GetByRoleOptions()
              .setName("Código de ativação")
              .setExact(true));
      assertThat(proof).isFocused();
      assertThat(proof).hasAttribute("autocomplete", "one-time-code");
      page.getByLabel("E-mail ou usuário").fill("pessoa@example.com");
      proof.fill("manual-resume-proof");
      assertNoHorizontalOverflow(page);
      page.screenshot(new Page.ScreenshotOptions()
          .setPath(evidenceDirectory.resolve("activation-manual-phone.png"))
          .setFullPage(true));

      page.getByRole(AriaRole.BUTTON,
          new Page.GetByRoleOptions()
              .setName("Reenviar código de ativação")
              .setExact(true))
          .click();

      assertThat(page.locator("[data-rfw-access-step='activation']")).isVisible();
      assertThat(page.getByText(
          "Se houver um cadastro pendente",
          new Page.GetByTextOptions().setExact(false))).isVisible();
      assertThat(page.getByText(
          "p***@example.com",
          new Page.GetByTextOptions().setExact(false))).isVisible();
      assertThat(page.locator("[data-rfw-activation-expiration]"))
          .containsText("01/08/2026");
      assertNoHorizontalOverflow(page);
      page.screenshot(new Page.ScreenshotOptions()
          .setPath(evidenceDirectory.resolve("activation-resend-phone.png"))
          .setFullPage(true));
    }
  }

  /**
   * Percorre por teclado a solicitação de cancelamento e comprova foco e feedback anunciável.
   *
   * @throws Exception quando a pasta de evidências temporárias não puder ser criada
   */
  @Test
  void cancellationRequest_shouldRemainKeyboardAccessibleAndAnnounced_onDesktop()
      throws Exception {
    Path evidenceDirectory = Files.createDirectories(Path.of("target", "ui-evidence"));
    try (BrowserContext context = browser.newContext(
        new Browser.NewContextOptions().setViewportSize(1440, 1000))) {
      Page page = context.newPage();
      openCancellationRequest(page, "pessoa@example.com");

      Locator identifier = page.getByLabel("E-mail ou usuário");
      Locator submit = page.getByRole(AriaRole.BUTTON,
          new Page.GetByRoleOptions().setName("Solicitar cancelamento").setExact(true));
      assertThat(page.getByRole(AriaRole.HEADING,
          new Page.GetByRoleOptions().setName("Cancelar cadastro pendente").setExact(true)))
          .isVisible();
      assertThat(page.getByText(
          "Solicitar as instruções não cancela o cadastro",
          new Page.GetByTextOptions().setExact(false))).isVisible();
      assertThat(identifier).hasAttribute("required", "");
      assertThat(identifier).isFocused();

      identifier.fill("");
      identifier.press("Tab");
      assertThat(submit).isFocused();
      submit.press("Enter");

      Locator feedback = page.getByRole(AriaRole.ALERT);
      assertThat(feedback).containsText("Revise os campos indicados.");
      assertThat(identifier).hasAttribute("invalid", "");
      assertThat(identifier).isFocused();
      assertNoHorizontalOverflow(page);
      page.screenshot(new Page.ScreenshotOptions()
          .setPath(evidenceDirectory.resolve("cancellation-request-feedback-desktop.png"))
          .setFullPage(true));

      identifier.fill("pessoa@example.com");
      identifier.press("Tab");
      submit.press("Enter");
      assertThat(page.locator(
          "[data-rfw-access-step='registration_cancellation_confirmation']"))
          .isVisible();
    }
  }

  /**
   * Confirma reflow, alvo de toque e conteúdo localizado da solicitação em telefone.
   *
   * @throws Exception quando a pasta de evidências temporárias não puder ser criada
   */
  @Test
  void cancellationRequest_shouldSupportTouchAndLocalizedReflow_onPhone()
      throws Exception {
    Path evidenceDirectory = Files.createDirectories(Path.of("target", "ui-evidence"));
    try (BrowserContext context = browser.newContext(
        new Browser.NewContextOptions()
            .setViewportSize(390, 844)
            .setDeviceScaleFactor(1)
            .setHasTouch(true))) {
      Page page = context.newPage();
      openCancellationRequest(page, "pessoa@example.com");

      Locator submit = page.getByRole(AriaRole.BUTTON,
          new Page.GetByRoleOptions().setName("Solicitar cancelamento").setExact(true));
      assertThat(page.getByRole(AriaRole.HEADING,
          new Page.GetByRoleOptions().setName("Cancelar cadastro pendente").setExact(true)))
          .isVisible();
      assertThat(page.getByText(
          "O cancelamento confirmado não pode ser desfeito.",
          new Page.GetByTextOptions().setExact(false))).isVisible();
      assertNoHorizontalOverflow(page);
      BoundingBox target = submit.boundingBox();
      org.assertj.core.api.Assertions.assertThat(target).isNotNull();
      org.assertj.core.api.Assertions.assertThat(target.width).isGreaterThanOrEqualTo(24);
      org.assertj.core.api.Assertions.assertThat(target.height).isGreaterThanOrEqualTo(24);
      page.screenshot(new Page.ScreenshotOptions()
          .setPath(evidenceDirectory.resolve("cancellation-request-ready-phone.png"))
          .setFullPage(true));

      page.touchscreen().tap(target.x + target.width / 2, target.y + target.height / 2);
      assertThat(page.locator(
          "[data-rfw-access-step='registration_cancellation_confirmation']"))
          .isVisible();
    }
  }

  /**
   * Confirma pelo deep link a operação destrutiva simulada e registra entrada e resultado em desktop.
   *
   * @throws Exception quando a pasta de evidências temporárias não puder ser criada
   */
  @Test
  void cancellationConfirmation_shouldConsumeDeepLinkAndReachTerminalResult_onDesktop()
      throws Exception {
    Path evidenceDirectory = Files.createDirectories(Path.of("target", "ui-evidence"));
    try (BrowserContext context = browser.newContext(
        new Browser.NewContextOptions().setViewportSize(1440, 1000))) {
      Page page = context.newPage();
      page.navigate("http://127.0.0.1:" + port
          + "/cancel-registration?token=opaque-cancel-proof");
      assertRfwStylesLoaded(page);

      Locator proof = page.getByRole(AriaRole.TEXTBOX,
          new Page.GetByRoleOptions().setName("Código de cancelamento").setExact(true));
      Locator submit = page.getByRole(AriaRole.BUTTON,
          new Page.GetByRoleOptions().setName("Confirmar cancelamento").setExact(true));
      assertThat(page.locator(
          "[data-rfw-access-step='registration_cancellation_confirmation']"))
          .isVisible();
      assertThat(page.getByText(
          "Um código válido confirma a exclusão definitiva",
          new Page.GetByTextOptions().setExact(false))).isVisible();
      assertThat(proof).hasValue("opaque-cancel-proof");
      assertThat(proof).hasAttribute("autocomplete", "one-time-code");
      assertThat(proof).isFocused();
      org.assertj.core.api.Assertions.assertThat(page.url())
          .isEqualTo("http://127.0.0.1:" + port + "/cancel-registration");
      page.getByLabel("E-mail ou usuário").fill("pessoa@example.com");
      assertNoHorizontalOverflow(page);
      page.screenshot(new Page.ScreenshotOptions()
          .setPath(evidenceDirectory.resolve("cancellation-confirmation-ready-desktop.png"))
          .setFullPage(true));

      submit.click();

      assertThat(page.locator("[data-rfw-access-step='result']")).isVisible();
      assertThat(page.getByText(
          "O cadastro pendente foi cancelado e o e-mail está liberado para um novo cadastro.",
          new Page.GetByTextOptions().setExact(true))).isVisible();
      assertThat(page.getByRole(AriaRole.BUTTON,
          new Page.GetByRoleOptions().setName("Criar conta").setExact(true))).isVisible();
      assertThat(page.getByRole(AriaRole.BUTTON,
          new Page.GetByRoleOptions().setName("Voltar para entrar").setExact(true))).isVisible();
      org.assertj.core.api.Assertions.assertThat(page.content())
          .doesNotContain("opaque-cancel-proof");
      page.screenshot(new Page.ScreenshotOptions()
          .setPath(evidenceDirectory.resolve("cancellation-confirmation-result-desktop.png"))
          .setFullPage(true));
    }
  }

  /**
   * Confirma reflow e acionamento touch da confirmação destrutiva em telefone.
   *
   * @throws Exception quando a pasta de evidências temporárias não puder ser criada
   */
  @Test
  void cancellationConfirmation_shouldSupportTouchAndTerminalActions_onPhone()
      throws Exception {
    Path evidenceDirectory = Files.createDirectories(Path.of("target", "ui-evidence"));
    try (BrowserContext context = browser.newContext(
        new Browser.NewContextOptions()
            .setViewportSize(390, 844)
            .setDeviceScaleFactor(1)
            .setHasTouch(true))) {
      Page page = context.newPage();
      page.navigate("http://127.0.0.1:" + port + "/cancel-registration");
      assertRfwStylesLoaded(page);

      page.getByLabel("E-mail ou usuário").fill("pessoa@example.com");
      page.getByLabel("Código de cancelamento").fill("manual-cancel-proof");
      Locator submit = page.getByRole(AriaRole.BUTTON,
          new Page.GetByRoleOptions().setName("Confirmar cancelamento").setExact(true));
      assertNoHorizontalOverflow(page);
      BoundingBox target = submit.boundingBox();
      org.assertj.core.api.Assertions.assertThat(target).isNotNull();
      org.assertj.core.api.Assertions.assertThat(target.width).isGreaterThanOrEqualTo(24);
      org.assertj.core.api.Assertions.assertThat(target.height).isGreaterThanOrEqualTo(24);
      page.screenshot(new Page.ScreenshotOptions()
          .setPath(evidenceDirectory.resolve("cancellation-confirmation-ready-phone.png"))
          .setFullPage(true));

      page.touchscreen().tap(target.x + target.width / 2, target.y + target.height / 2);

      assertThat(page.locator("[data-rfw-access-step='result']")).isVisible();
      assertThat(page.getByRole(AriaRole.BUTTON,
          new Page.GetByRoleOptions().setName("Criar conta").setExact(true))).isVisible();
      assertThat(page.getByRole(AriaRole.BUTTON,
          new Page.GetByRoleOptions().setName("Voltar para entrar").setExact(true))).isVisible();
      assertNoHorizontalOverflow(page);
      page.screenshot(new Page.ScreenshotOptions()
          .setPath(evidenceDirectory.resolve("cancellation-confirmation-result-phone.png"))
          .setFullPage(true));
    }
  }

  /**
   * Exercita por teclado o foco inicial e a rejeição anunciada da continuação Google.
   *
   * @throws Exception quando a pasta de evidências temporárias não puder ser criada
   */
  @Test
  void externalRegistration_shouldExposeKeyboardFocusAndAnnouncedFeedback_onDesktop()
      throws Exception {
    Path evidenceDirectory = Files.createDirectories(Path.of("target", "ui-evidence"));
    try (BrowserContext context = browser.newContext(
        new Browser.NewContextOptions().setViewportSize(1440, 1000))) {
      Page page = context.newPage();
      openExternalRegistration(page);

      Locator email = page.getByLabel("E-mail");
      Locator terms = page.getByLabel("Aceito os Termos de Uso");
      Locator privacy = page.getByLabel("Li e estou ciente da Política de Privacidade");
      Locator submit = page.getByRole(AriaRole.BUTTON,
          new Page.GetByRoleOptions().setName("Concluir cadastro").setExact(true));

      assertThat(email).hasValue("verified@example.com");
      assertThat(email).hasAttribute("readonly", "");
      assertThat(terms).isFocused();
      terms.press("Space");
      privacy.press("Space");
      assertThat(terms).isChecked();
      assertThat(privacy).isChecked();
      submit.press("Enter");

      Locator feedback = page.getByRole(AriaRole.ALERT);
      assertThat(feedback).containsText("Revise os campos indicados.");
      assertThat(terms).hasAttribute("invalid", "");
      assertThat(privacy).hasAttribute("invalid", "");
      assertThat(terms).isFocused();
      assertNoHorizontalOverflow(page);
      page.screenshot(new Page.ScreenshotOptions()
          .setPath(evidenceDirectory.resolve("external-registration-feedback-desktop.png"))
          .setFullPage(true));
    }
  }

  /**
   * Confirma reflow e textos localizados da continuação Google no viewport de telefone.
   *
   * @throws Exception quando a pasta de evidências temporárias não puder ser criada
   */
  @Test
  void externalRegistration_shouldReflowLocalizedContent_onPhone() throws Exception {
    Path evidenceDirectory = Files.createDirectories(Path.of("target", "ui-evidence"));
    try (BrowserContext context = browser.newContext(
        new Browser.NewContextOptions()
            .setViewportSize(390, 844)
            .setDeviceScaleFactor(1))) {
      Page page = context.newPage();
      openExternalRegistration(page);

      assertThat(page.getByRole(AriaRole.HEADING,
          new Page.GetByRoleOptions().setName("Conclua sua conta").setExact(true)))
          .isVisible();
      assertThat(page.getByText(
          "Seu e-mail foi verificado pelo provedor externo",
          new Page.GetByTextOptions().setExact(false))).isVisible();
      assertThat(page.getByLabel("E-mail")).isVisible();
      assertThat(page.getByRole(AriaRole.LINK,
          new Page.GetByRoleOptions().setName("Aceito os Termos de Uso").setExact(true)))
          .isVisible();
      assertThat(page.getByRole(AriaRole.BUTTON,
          new Page.GetByRoleOptions().setName("Concluir cadastro").setExact(true)))
          .isVisible();
      assertNoHorizontalOverflow(page);
      page.screenshot(new Page.ScreenshotOptions()
          .setPath(evidenceDirectory.resolve("external-registration-ready-phone.png"))
          .setFullPage(true));
    }
  }

  /**
   * Percorre a integração Google simulada desde o login até o Painel de Usuário.
   *
   * @throws Exception quando a pasta de evidências temporárias não puder ser criada
   */
  @Test
  void googleRegistration_shouldAuthenticateAndReachUserDashboard_onDesktop()
      throws Exception {
    Path evidenceDirectory = Files.createDirectories(Path.of("target", "ui-evidence"));
    try (BrowserContext context = googleBrowserContext(1440, 1000)) {
      Page page = context.newPage();
      page.navigate("http://127.0.0.1:" + port + "/login");
      assertRfwStylesLoaded(page);

      page.getByRole(AriaRole.BUTTON,
          new Page.GetByRoleOptions().setName("Entrar com Google").setExact(true))
          .click();

      assertExternalRegistrationReady(page);
      assertNoHorizontalOverflow(page);
      page.screenshot(new Page.ScreenshotOptions()
          .setPath(evidenceDirectory.resolve("google-continuation-desktop.png"))
          .setFullPage(true));

      acceptRequiredDocuments(page);
      page.getByRole(AriaRole.BUTTON,
          new Page.GetByRoleOptions().setName("Concluir cadastro").setExact(true))
          .click();

      page.waitForURL("**/user");
      org.assertj.core.api.Assertions.assertThat(page.url())
          .isEqualTo("http://127.0.0.1:" + port + "/user");
      page.screenshot(new Page.ScreenshotOptions()
          .setPath(evidenceDirectory.resolve("google-user-dashboard-desktop.png"))
          .setFullPage(true));
    }
  }

  /**
   * Confirma que a jornada Google nominal permanece operável e sem overflow em telefone.
   *
   * @throws Exception quando a pasta de evidências temporárias não puder ser criada
   */
  @Test
  void googleRegistration_shouldReflowAndComplete_onPhone() throws Exception {
    Path evidenceDirectory = Files.createDirectories(Path.of("target", "ui-evidence"));
    try (BrowserContext context = googleBrowserContext(390, 844)) {
      Page page = context.newPage();
      page.navigate("http://127.0.0.1:" + port + "/login");
      assertRfwStylesLoaded(page);

      page.getByRole(AriaRole.BUTTON,
          new Page.GetByRoleOptions().setName("Entrar com Google").setExact(true))
          .click();

      assertExternalRegistrationReady(page);
      assertNoHorizontalOverflow(page);
      page.screenshot(new Page.ScreenshotOptions()
          .setPath(evidenceDirectory.resolve("google-continuation-phone.png"))
          .setFullPage(true));

      acceptRequiredDocuments(page);
      page.getByRole(AriaRole.BUTTON,
          new Page.GetByRoleOptions().setName("Concluir cadastro").setExact(true))
          .click();
      page.waitForURL("**/user");
    }
  }

  private void openRegistration(Page page) {
    page.navigate("http://127.0.0.1:" + port + "/login");
    assertRfwStylesLoaded(page);
    page.getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Criar conta").setExact(true)).click();
    assertThat(page.locator("[data-rfw-access-step='registration']")).isVisible();
  }

  private void openExternalRegistration(Page page) {
    page.navigate("http://127.0.0.1:" + port + "/test/external-registration");
    assertRfwStylesLoaded(page);
    assertThat(page.locator("[data-rfw-access-step='external_registration']")).isVisible();
  }

  private void openCancellationRequest(Page page, String identifier) {
    page.navigate("http://127.0.0.1:" + port + "/login?step=activation");
    assertRfwStylesLoaded(page);
    page.getByLabel("E-mail ou usuário").fill(identifier);
    page.getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Cancelar cadastro pendente").setExact(true))
        .click();
    assertThat(page.locator("[data-rfw-access-step='registration_cancellation_request']"))
        .isVisible();
  }

  private BrowserContext googleBrowserContext(int width, int height) {
    BrowserContext context = browser.newContext(
        new Browser.NewContextOptions()
            .setViewportSize(width, height)
            .setDeviceScaleFactor(1));
    context.addInitScript("""
        window.google = {
          accounts: {
            id: {
              initialize: configuration => {
                window.__rinosGoogleCallback = configuration.callback;
              },
              renderButton: element => {
                const button = document.createElement('button');
                button.type = 'button';
                button.textContent = 'Entrar com Google';
                button.addEventListener('click', () => {
                  window.__rinosGoogleCallback({ credential: 'test-google-credential' });
                });
                element.replaceChildren(button);
              }
            }
          }
        };
        """);
    return context;
  }

  private void assertExternalRegistrationReady(Page page) {
    assertThat(page.locator("[data-rfw-access-step='external_registration']")).isVisible();
    Locator email = page.getByLabel("E-mail");
    assertThat(email).hasValue("verified@example.com");
    assertThat(email).hasAttribute("readonly", "");
    assertThat(page.locator("input[type='password']")).hasCount(0);
    org.assertj.core.api.Assertions.assertThat(page.content())
        .doesNotContain("test-google-credential", "test-google-subject");
  }

  private void acceptRequiredDocuments(Page page) {
    page.getByLabel("Aceito os Termos de Uso").check();
    page.getByLabel("Li e estou ciente da Política de Privacidade").check();
  }

  private void assertRfwStylesLoaded(Page page) {
    String textColor = (String) page.evaluate(
        "() => getComputedStyle(document.documentElement)"
            + ".getPropertyValue('--rfw-theme-text-color').trim()");
    org.assertj.core.api.Assertions.assertThat(textColor).isNotBlank();
  }

  private void assertNoHorizontalOverflow(Page page) {
    boolean hasOverflow = (boolean) page.evaluate(
        "() => document.documentElement.scrollWidth > document.documentElement.clientWidth");
    org.assertj.core.api.Assertions.assertThat(hasOverflow).isFalse();
  }
}
