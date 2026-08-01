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
@DisplayName("Jornadas E2E do cadastro local e da retomada")
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

  private void openRegistration(Page page) {
    page.navigate("http://127.0.0.1:" + port + "/login");
    page.getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Criar conta").setExact(true)).click();
    assertThat(page.locator("[data-rfw-access-step='registration']")).isVisible();
  }

  private void assertNoHorizontalOverflow(Page page) {
    boolean hasOverflow = (boolean) page.evaluate(
        "() => document.documentElement.scrollWidth > document.documentElement.clientWidth");
    org.assertj.core.api.Assertions.assertThat(hasOverflow).isFalse();
  }
}
