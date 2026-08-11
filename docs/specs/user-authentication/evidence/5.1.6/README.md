# Evidência 5.1.6 — testes da interface de login

Camadas cobertas:

- componente: RFWAccessComponentTest e RFWAccessComponentConfigTest;
- integração: RFWPlatformIntegrationTest e RinosAccessComponentFactoryTest;
- E2E opt-in: RegistrationViewE2EIT, com login desktop/telefone, cadastro, ativação, cancelamento e Google;
- acessibilidade/visual: RegistrationAccessibilityE2EIT, matriz WCAG 2.2 AA em viewport desktop/telefone e temas claro/escuro.

Execução E2E requer -Drinos.ui.e2e.enabled=true e os binários locais do Playwright. O build padrão permanece independente de navegador.
