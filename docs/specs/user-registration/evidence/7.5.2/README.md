# Evidência da tarefa 7.5.2

## Resultado

Quando o cadastro rejeita um e-mail já ativo, a ação de recuperação do renderer RFW abre a etapa
`RECOVERY_REQUEST` com esse mesmo e-mail preenchido. O provider real está ativo e converte a solicitação para
`PasswordRecoveryRequestDTO` sem expor o identificador na URL.

O link recebido por e-mail abre `PASSWORD_RESET` diretamente por prova opaca. A rota aceita apenas o passo canônico,
limita a prova e remove o query string do histórico visível depois de entregá-la ao componente.

## Evidências automatizadas

- `RFWPasswordRecoveryProviderAdapterTest.requestRecovery_shouldReturnNeutralCompletion_andPrefillCommandIdentifier`;
- `LoginViewTest.resolveEntry_shouldOpenPasswordResetWithOpaqueProof`;
- `LoginViewTest.beforeEnter_shouldConsumeProofAndReplaceSensitiveBrowserLocation`;
- testes públicos do `RFWDefaultAccessStepRenderer` que transferem o e-mail do cadastro à recuperação somente quando
  a capability existe.
