# Evidência da tarefa 4.1.6

## Contrato implementado

`RFWRegistrationCancellationProviderAdapter` implementa as duas operações de
`RFWRegistrationCancellationProvider`:

1. solicita a comprovação de cancelamento por `RegistrationCancellationFacade`;
2. confirma o cancelamento usando a referência e a prova recebidas do RFW.

O adapter pertence à camada `ui.config` e depende exclusivamente dos contratos públicos em
`api`. A interface não acessa entities, repositories ou services internos.

## Comportamento público

A solicitação aceita sempre abre a etapa de confirmação com uma referência opaca, método
`EMAIL_CODE` e expiração explícita. A neutralidade sobre a existência do cadastro é preservada pela
facade; o adapter não realiza consultas adicionais nem deriva respostas pelo identificador.

Na confirmação, o provider traduz:

- cancelamento efetivado para conclusão pública;
- prova inválida ou expirada para erro associado ao campo `proof`;
- validação estrutural para os erros por campo da facade;
- indisponibilidade para uma rejeição pública sem detalhes internos.

O contexto Spring descobre o adapter como provider efetivo e anuncia a capability
`REGISTRATION_CANCELLATION` do RFW.

## Validação focal

Comando:

```powershell
mvn "-Dtest=RFWRegistrationCancellationProviderAdapterTest,RFWPlatformIntegrationTest,PublicContractSecurityTest" test
```

Resultado:

```text
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Os testes confirmam solicitação, confirmação, isolamento da API pública e descoberta da
capability. A cobertura de paridade de todos os outcomes e erros por campo pertence à tarefa
4.1.7.
