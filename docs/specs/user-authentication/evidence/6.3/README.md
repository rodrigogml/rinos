# Evidência 6.3 — jornadas e acessibilidade

A suíte E2E opt-in do Rinos percorre login, recuperação, cadastro, ativação, cancelamento e Google em desktop/telefone; a matriz WCAG 2.2 AA valida os estados públicos em claro/escuro, reduced motion e reflow. Os testes de componente do RFW cobrem foco, teclado, região assertiva, busy, dupla submissão e resultados.

A medição operacional de 20 jornadas por método (6.3.4) exige ambiente candidato com navegador e dados de produção sintéticos; permanece uma atividade de aceite operacional e não é simulada no build unitário.

A tentativa local com `mvn -q -Drinos.ui.e2e.enabled=true -Dit.test=RegistrationViewE2EIT -DskipUnitTests verify` excedeu 120 segundos antes de produzir evidência confiável. Por isso, não se contabiliza a execução como aprovação.
