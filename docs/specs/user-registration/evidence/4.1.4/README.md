# Evidência da tarefa 4.1.4

## Contrato implementado

`RFWRegistrationProviderAdapter` implementa as três operações públicas de
`RFWRegistrationProvider`:

- inicia o cadastro local por `RegistrationStartFacade`;
- ativa o cadastro por `RegistrationActivationFacade`;
- solicita nova comprovação por `RegistrationResendFacade`.

O adapter permanece na camada `ui.config` e depende somente de DTOs, VOs e facades da camada
`api`. Nenhuma entity, repository ou service interno é exposto ao RFW.

## Tradução para o RFW

O adapter:

- resolve a origem canônica com `RFWRemoteAddressProvider`;
- cria um identificador de correlação novo para cada operação;
- conserva a senha somente durante a conversão para o comando público;
- traduz sucesso, rejeição, limitação, indisponibilidade e erros por campo para outcomes tipados;
- mantém falha de SMTP como rejeição, sem afirmar que a mensagem foi entregue;
- usa somente referências opacas para ativação;
- encaminha a ativação que exige novos aceites para `ACTIVATION_CONSENT_REQUIRED`;
- não cria autenticação ou sessão como efeito da ativação.

O contexto Spring descobre o adapter como provider efetivo e, por consequência, anuncia a
capability `REGISTRATION` do RFW.

## Validação focal

Comando:

```powershell
mvn "-Dtest=RFWRegistrationProviderAdapterTest,RFWPlatformIntegrationTest,PublicContractSecurityTest" test
```

Resultado:

```text
Tests run: 29, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Os testes cobrem o contrato das três operações, todos os resultados públicos relevantes,
preservação de erros por campo, ausência de requisição HTTP, isolamento da API pública e descoberta
da capability pelo RFW.

## Validação completa do checkpoint

Comando:

```powershell
mvn verify
```

Resultado consolidado:

```text
Unit tests: 281; failures: 0; errors: 0; skipped: 0
Integration tests: 47; failures: 0; errors: 0; skipped: 2
BUILD SUCCESS
```

Os dois testes ignorados são cenários de navegador explicitamente opt-in.
