# Evidências da tarefa 6.5.1

Data da validação: 2026-08-02

## Entradas convergentes

A confirmação usa exclusivamente `RFWAccessStepEnum.REGISTRATION_CANCELLATION_CONFIRMATION` por duas entradas:

1. o outcome tipado `REGISTRATION_CANCELLATION_REQUIRED`, produzido depois de uma solicitação sintaticamente válida;
2. a rota anônima `https://app.rinos.com.br/cancel-registration?token=<prova-opaca>`, já emitida pelo template de
   cancelamento.

As duas entradas hospedam o mesmo `RFWAccessComponent`, renderer e provider. A rota não aceita e-mail, `userId`,
`registrationId` ou `verificationId`: ela entrega somente a prova ao estado efêmero, com identificador vazio para
preenchimento explícito.

## Proteção da entrada pública

- somente uma ocorrência de `token` com até 512 caracteres é aceita;
- prova ausente, vazia, repetida ou excessivamente grande é descartada e abre a confirmação manual;
- a presença do parâmetro sempre substitui o histórico visível por `/cancel-registration` depois da entrega;
- parâmetros desconhecidos não são propagados ao componente;
- o gerador da URL continua usando apenas a origem canônica de `rinos.application.public-base-url`.

O outcome neutro da solicitação usa uma challenge aleatória e não representa a prova persistida. Já o link recebido
por e-mail transporta a prova opaca de uso único; nenhum dos dois expõe ID interno.

## Validação automatizada

```powershell
mvn '-Dtest=RegistrationCancellationViewTest,RFWPlatformIntegrationTest,PublicApplicationUriServiceTest' test
```

```text
26 testes; 0 falhas; 0 erros; 0 ignorados
BUILD SUCCESS
```

O gate focal cobre composição por outcome, composição por deep link, entrada manual, token repetido ou grande,
remoção do segredo do histórico, anotação anônima e coerência com a URL gerada pelo backend.

Gate completo:

```powershell
mvn verify
```

```text
Testes unitários: 321; 0 falhas; 0 erros; 0 ignorados
Testes de integração: 56; 0 falhas; 0 erros; 10 E2E opt-in ignorados
BUILD SUCCESS
```
