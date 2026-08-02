# Evidência 6.5.4 — resultados adversos da confirmação

## Resultado

A confirmação de cancelamento cobre os quatro estados adversos sem repetir efeitos destrutivos:

- prova desconhecida ou inválida retorna `INVALID_PROOF`;
- prova expirada retorna `EXPIRED_PROOF`;
- prova já utilizada retorna o mesmo resultado público `INVALID_PROOF`, sem revelar que a prova existiu;
- cadastro já encerrado retorna o mesmo resultado público `INVALID_PROOF`, sem revelar o estado interno do processo.

O adapter real converte prova inválida e expirada em erros do campo `proof`. Em todos os casos adversos,
o serviço não consome a prova, não remove o usuário e não grava um novo tombstone de cancelamento.

## Validação executada

```text
mvn "-Dtest=RegistrationCancellationServiceTest,RFWRegistrationCancellationProviderAdapterTest,RFWPlatformIntegrationTest" test
Tests run: 35, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Os testes de serviço verificam separadamente prova inválida, expirada, usada e processo encerrado. Os testes
do adapter comprovam a tradução dos únicos resultados públicos apresentados pela interface, preservando a
neutralidade entre prova usada, processo encerrado e qualquer outra prova inválida.
