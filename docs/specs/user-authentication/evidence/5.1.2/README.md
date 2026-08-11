# Evidência 5.1.2 — Composição do login multimétodo

`RinosAccessComponentFactory` configura a instância pública com `projectKey("rinos")`, `rememberMeEnabled(true)`,
instrução localizada da senha e callback `onAuthenticated` para `UserDashboardEntryView`. O RFW só apresenta
lembrar-me se o provider persistente estiver disponível, evitando checkbox sem efeito.

O renderer RFW permanece responsável pela ordem de login, recuperação, Google, passkey, cadastro e feedbacks neutros;
o Rinos não duplica campos, mensagens ou estados.

## Validação

- `RinosAccessComponentFactoryTest` verifica o projeto, lembrar-me, callback e instrução de senha.
- `RFWPlatformIntegrationTest` verifica callbacks e capabilities no componente real da plataforma.
