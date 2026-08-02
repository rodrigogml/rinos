# Evidências da tarefa 6.4.5

Data da validação: 2026-08-02

## Matriz de estados

| Estado da interface | Evidência automatizada | Resultado comprovado |
|---------------------|------------------------|----------------------|
| `initial` e `ready` | `activation_shouldOpenCancellationRequestWithCurrentIdentifier_whenCapabilityIsAvailable` | A etapa abre com consequência, identificador atual e ações corretas. |
| `processing` | `cancellationRequest_shouldRemainProcessingUntilProviderResponds` | `aria-busy` permanece ativo e nenhuma confirmação é presumida antes da resposta. |
| `validation-error` | `cancellationRequest_shouldPreserveIdentifierAndNeverReuseConsumedTurnstileToken` | O identificador pode ser corrigido, mas a prova humana precisa ser renovada. |
| `success` | `cancellationRequest_shouldExplainConsequencesAndKeepConfirmationResponseNeutral` | Uma resposta aceita abre a confirmação condicional sem revelar existência ou destino. |
| `remote-error` | `cancellationRequest_shouldRemainRecoverableWhenProviderIsUnavailable` | A etapa permanece editável, exibe indisponibilidade pública e não afirma envio. |
| `offline` | `cancellationRequest_shouldRemainProcessingUntilProviderResponds` | Enquanto não há resposta, o cliente não recebe transição de sucesso nem challenge. |
| limitação neutra | `request_shouldKeepPublicResponseNeutralAndRecordLimitInternally_whenIssueIsBlocked` | A limitação só aparece na observabilidade interna; o contrato público continua `REQUEST_ACCEPTED`. |

`access-denied` e `partial-stale` são não aplicáveis nesta solicitação pública sem conteúdo versionado, conforme a
Interface Design. A perda real de conectividade no navegador e a reconexão integram o gate E2E da tarefa 6.4.7; esta
tarefa comprova a invariável servidor-side de não presumir efeito sem resposta.

## Segurança da limitação

O resultado limitado não contém `retryAfter`, instante de liberação, destino, identificador ou indicação de cadastro
existente. A fachada registra `RATE_LIMITED` somente na observabilidade sanitizada e o adapter recebe a mesma forma
pública de uma solicitação aceita ou ausente.

> [!IMPORTANT]
> Não houve alteração na RFW Platform. Os estados e o feedback público foram validados pela API da revisão
> `7d47fe735d181acde035c6aa22c8e1dd6c0c7c17`.

## Validação automatizada focada

```powershell
mvn '-Dtest=RFWPlatformIntegrationTest,RegistrationCancellationFacadeImplTest' test
```

```text
Testes unitários: 22; 0 falhas; 0 erros; 0 ignorados
BUILD SUCCESS
```

## Gate completo do Rinos

```powershell
mvn verify
```

```text
Testes unitários: 316; 0 falhas; 0 erros; 0 ignorados
Testes de integração: 54; 0 falhas; 0 erros; 8 ignorados por serem E2E opt-in
BUILD SUCCESS
```
