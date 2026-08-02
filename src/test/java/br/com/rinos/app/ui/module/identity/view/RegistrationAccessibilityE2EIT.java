package br.com.rinos.app.ui.module.identity.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.playwright.Reporter;
import com.deque.html.axecore.results.AxeResults;
import com.deque.html.axecore.results.Rule;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.ColorScheme;
import com.microsoft.playwright.options.ReducedMotion;

/**
 * Executa o gate automatizado WCAG 2.2 AA sobre os estados observáveis do cadastro.
 *
 * <p>A suíte usa o renderer e a composição reais do RFW e do Rinos. Somente as fronteiras
 * externas permanecem determinísticas no {@link RegistrationUiTestApplication}, conforme o
 * harness funcional da interface.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-02
 */
@SpringBootTest(
    classes = RegistrationUiTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = "vaadin.launch-browser=false")
@EnabledIfSystemProperty(named = "rinos.ui.e2e.enabled", matches = "true")
@DisplayName("Gate automatizado WCAG 2.2 AA do cadastro")
class RegistrationAccessibilityE2EIT {

  private static final List<String> WCAG_22_AA_TAGS = List.of(
      "wcag2a",
      "wcag2aa",
      "wcag21a",
      "wcag21aa",
      "wcag22a",
      "wcag22aa");
  private static final List<String> BLOCKING_IMPACTS = List.of("critical", "serious");

  private static Playwright playwright;
  private static Browser browser;

  @LocalServerPort
  private int port;

  /**
   * Inicializa o Chromium compartilhado pelas quatro matrizes de apresentação.
   */
  @BeforeAll
  static void startBrowser() {
    playwright = Playwright.create();
    browser = playwright.chromium().launch(
        new BrowserType.LaunchOptions().setHeadless(true));
  }

  /**
   * Libera os processos nativos do navegador ao final do gate.
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
   * Varre todos os estados principais em desktop e telefone, nos temas claro e escuro.
   *
   * @param formFactor identificação estável do viewport para os relatórios
   * @param width largura CSS do viewport
   * @param height altura CSS do viewport
   * @param colorScheme preferência de tema exposta ao renderer
   * @throws IOException quando o relatório bruto não puder ser persistido
   */
  @ParameterizedTest(name = "{0} {3}")
  @MethodSource("presentationMatrix")
  void registrationFlows_shouldHaveNoCriticalOrSeriousWcag22AaViolations(
      String formFactor,
      int width,
      int height,
      ColorScheme colorScheme) throws IOException {
    try (BrowserContext context = browser.newContext(
        new Browser.NewContextOptions()
            .setViewportSize(width, height)
            .setDeviceScaleFactor(1)
            .setColorScheme(colorScheme)
            .setReducedMotion(ReducedMotion.REDUCE))) {
      Page page = context.newPage();
      String presentation = formFactor + "-"
          + colorScheme.name().toLowerCase(Locale.ROOT);

      auditRegistration(page, presentation);
      auditActivation(page, presentation);
      auditExternalRegistration(page, presentation);
      auditCancellationRequest(page, presentation);
      auditCancellationConfirmation(page, presentation);
    }
  }

  private static Stream<Arguments> presentationMatrix() {
    return Stream.of(
        Arguments.of("desktop", 1440, 1000, ColorScheme.LIGHT),
        Arguments.of("desktop", 1440, 1000, ColorScheme.DARK),
        Arguments.of("phone", 390, 844, ColorScheme.LIGHT),
        Arguments.of("phone", 390, 844, ColorScheme.DARK));
  }

  private void auditRegistration(Page page, String presentation) throws IOException {
    page.navigate(applicationUrl("/login"));
    page.getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Criar conta").setExact(true)).click();
    page.locator("[data-rfw-access-step='registration']").waitFor();
    audit(page, "registration-initial", presentation);

    page.getByLabel("E-mail").fill("pessoa@example.com");
    page.getByLabel("Nova senha").fill("Senha!1234");
    page.getByLabel("Confirmar senha").fill("Senha!5678");
    page.getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Criar conta").setExact(true)).click();
    audit(page, "registration-validation", presentation);

    page.getByLabel("Confirmar senha").fill("Senha!1234");
    page.getByLabel("Aceito os Termos de Uso").check();
    page.getByLabel("Li e estou ciente da Política de Privacidade").check();
    audit(page, "registration-ready", presentation);

    page.getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Criar conta").setExact(true)).click();
    page.locator("[data-rfw-access-step='activation']").waitFor();
    audit(page, "registration-submitted", presentation);
  }

  private void auditActivation(Page page, String presentation) throws IOException {
    page.navigate(applicationUrl("/login?step=activation"));
    page.locator("[data-rfw-access-step='activation']").waitFor();
    audit(page, "activation-initial", presentation);

    page.getByLabel("E-mail ou usuário").fill("pessoa@example.com");
    page.getByRole(AriaRole.TEXTBOX,
        new Page.GetByRoleOptions().setName("Código de ativação").setExact(true))
        .fill("manual-resume-proof");
    page.getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions()
            .setName("Reenviar código de ativação")
            .setExact(true))
        .click();
    page.getByText(
        "Se houver um cadastro pendente",
        new Page.GetByTextOptions().setExact(false))
        .waitFor();
    audit(page, "activation-resent", presentation);
  }

  private void auditExternalRegistration(Page page, String presentation) throws IOException {
    page.navigate(applicationUrl("/test/external-registration"));
    page.locator("[data-rfw-access-step='external_registration']").waitFor();
    audit(page, "external-registration-initial", presentation);

    page.getByLabel("Aceito os Termos de Uso").check();
    page.getByLabel("Li e estou ciente da Política de Privacidade").check();
    page.getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Concluir cadastro").setExact(true)).click();
    page.getByRole(AriaRole.ALERT).waitFor();
    audit(page, "external-registration-validation", presentation);
  }

  private void auditCancellationRequest(Page page, String presentation) throws IOException {
    page.navigate(applicationUrl("/login?step=activation"));
    page.getByLabel("E-mail ou usuário").fill("pessoa@example.com");
    page.getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions()
            .setName("Cancelar cadastro pendente")
            .setExact(true))
        .click();
    page.locator("[data-rfw-access-step='registration_cancellation_request']").waitFor();
    audit(page, "cancellation-request-initial", presentation);

    page.getByLabel("E-mail ou usuário").fill("");
    page.getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Solicitar cancelamento").setExact(true))
        .click();
    page.getByRole(AriaRole.ALERT).waitFor();
    audit(page, "cancellation-request-validation", presentation);

    page.getByLabel("E-mail ou usuário").fill("pessoa@example.com");
    page.getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Solicitar cancelamento").setExact(true))
        .click();
    page.locator("[data-rfw-access-step='registration_cancellation_confirmation']").waitFor();
    audit(page, "cancellation-request-submitted", presentation);
  }

  private void auditCancellationConfirmation(Page page, String presentation) throws IOException {
    page.navigate(applicationUrl("/cancel-registration"));
    page.locator("[data-rfw-access-step='registration_cancellation_confirmation']").waitFor();
    audit(page, "cancellation-confirmation-initial", presentation);

    page.getByLabel("E-mail ou usuário").fill("pessoa@example.com");
    page.getByLabel("Código de cancelamento").fill("manual-cancel-proof");
    page.getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Confirmar cancelamento").setExact(true))
        .click();
    page.locator("[data-rfw-access-step='result']").waitFor();
    audit(page, "cancellation-confirmation-result", presentation);
  }

  private void audit(Page page, String state, String presentation) throws IOException {
    if (presentation.endsWith("-dark")) {
      page.evaluate("document.documentElement.setAttribute('theme', 'dark')");
      page.waitForFunction(
          "() => getComputedStyle(document.querySelector('.rfw-access-card'))"
              + ".backgroundColor === 'rgb(23, 23, 23)'");
    }
    AxeResults results = new AxeBuilder(page)
        .withTags(WCAG_22_AA_TAGS)
        .analyze();
    Path evidenceDirectory = Files.createDirectories(
        Path.of("target", "accessibility-evidence"));
    Path report = evidenceDirectory.resolve(state + "-" + presentation + ".json");
    new Reporter().JSONStringify(results, report.toString());

    assertThat(results.isErrored())
        .as("axe-core executou sem erro em %s (%s): %s",
            state, presentation, results.getErrorMessage())
        .isFalse();
    List<Rule> blockingViolations = results.getViolations().stream()
        .filter(rule -> BLOCKING_IMPACTS.contains(rule.getImpact()))
        .toList();
    assertThat(blockingViolations)
        .as("violações WCAG 2.2 AA críticas/sérias em %s (%s); relatório: %s",
            state, presentation, report)
        .isEmpty();
  }

  private String applicationUrl(String path) {
    return "http://127.0.0.1:" + port + path;
  }
}
