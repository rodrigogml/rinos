# Evidência 6.3 — jornadas e acessibilidade

A suíte E2E opt-in do Rinos percorre login, recuperação, cadastro, ativação, cancelamento e Google em desktop/telefone; a matriz WCAG 2.2 AA valida os estados públicos em claro/escuro, reduced motion e reflow. Os testes de componente do RFW cobrem foco, teclado, região assertiva, busy, dupla submissão e resultados.

A medição operacional de 20 jornadas por método (6.3.4) exige ambiente candidato com navegador e dados de produção sintéticos; permanece uma atividade de aceite operacional e não é simulada no build unitário.

O preenchimento deve usar [`journey-measurement-template.csv`](./journey-measurement-template.csv). Registre somente identificadores técnicos mínimos, instantes UTC, duração, resultado e causa observável; não inclua senha, OTP, passkey, e-mail completo, token ou conteúdo de resposta. Para login por senha, exclua apenas o tempo de entrega externa do segundo fator, conforme o critério SC-AUTH-001; para passkey e enrollment TOTP, mantenha toda a interação da aplicação na duração.

Uma nova tentativa local delimitada do harness E2E (`RegistrationViewE2EIT`, `RANDOM_PORT`, `failsafe:integration-test`, 180 segundos) foi encerrada sem resultado após a inicialização do contexto Spring. Não houve falha funcional observável nem aprovação de jornada; esse resultado reforça que a contagem 6.3.4 deve ocorrer no ambiente candidato preparado, com navegador, banco e integrações sintéticas disponíveis, e não deve ser inferida de uma execução interrompida.

A tentativa local inicial excedeu 120 segundos por deixar uma JVM do harness presa na porta 7070. O harness foi ajustado para `RANDOM_PORT` e a repetição isolada passou a iniciar em porta efêmera, mas ainda excedeu a janela durante a inicialização do contexto/MySQL. Por isso, não se contabiliza a execução como aprovação operacional.
