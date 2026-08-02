# Evidência da tarefa 6.1.6

## Resultado

A capability `PASSWORD_RECOVERY` está ativa pela presença de
`RFWPasswordRecoveryProviderAdapter`. O renderer público do RFW exibe a ação de recuperação no cadastro e abre
`RECOVERY_REQUEST` preservando apenas o e-mail já informado. Senha, confirmação e Turnstile não são transferidos.

## Evidências automatizadas

- `RFWPasswordRecoveryProviderAdapterTest` comprova a entrega do identificador preenchido à fachada e a resposta
  pública neutra.
- `LoginViewTest` comprova a entrada segura no reset por prova opaca e a limpeza da URL.
- Os testes do componente de acesso no submódulo RFW cobrem a transição do erro de cadastro para recuperação com o
  e-mail preenchido.

## Limite

O e-mail permanece somente no estado efêmero do componente; ele não é incluído no link externo nem em auditoria.
