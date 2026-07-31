# Evidência da tarefa 4.1.7

## Matriz de paridade

Os testes dos adapters do ciclo local cobrem todos os estados publicados pelos contratos Rinos:

| Operação | Estados cobertos |
|----------|------------------|
| Início | `EMAIL_SENT`, `EMAIL_DISPATCH_FAILED`, `EMAIL_ALREADY_EXISTS`, `PENDING_ALREADY_EXISTS`, `RATE_LIMITED`, `VALIDATION_REJECTED`, `UNAVAILABLE` |
| Reenvio | `REQUEST_ACCEPTED`, `EMAIL_DISPATCH_FAILED`, `RATE_LIMITED`, `VALIDATION_REJECTED`, `UNAVAILABLE` |
| Ativação | `ACTIVATED`, `ALREADY_ACTIVE`, `CONSENT_REQUIRED`, `INVALID_PROOF`, `EXPIRED_PROOF`, `REGISTRATION_CLOSED`, `VALIDATION_REJECTED`, `UNAVAILABLE` |
| Solicitação de cancelamento | `REQUEST_ACCEPTED`, `VALIDATION_REJECTED` |
| Confirmação de cancelamento | `CANCELLED`, `INVALID_PROOF`, `EXPIRED_PROOF`, `VALIDATION_REJECTED`, `UNAVAILABLE` |

`RFWActivationConsentProviderAdapter` reutiliza a mesma tradução exaustiva de ativação e possui
testes próprios para conclusão e renovação da continuação legal.

## Contratos protegidos

A suíte verifica:

- o status RFW correspondente a cada resultado público;
- chaves de mensagem específicas para entrega, prova, expiração e indisponibilidade;
- preservação dos erros por `email`, `identifier`, `password` e `proof`;
- propagação de `retryAfter` nas limitações;
- referências opacas e dados da continuação legal;
- ausência de autenticação em replay de ativação;
- isolamento entre a UI e os tipos internos do backend;
- descoberta das capabilities pelo contexto RFW.

## Validação focal

Comando:

```powershell
mvn "-Dtest=RFWRegistrationProviderAdapterTest,RFWActivationConsentProviderAdapterTest,RFWRegistrationCancellationProviderAdapterTest,RFWPlatformIntegrationTest,PublicContractSecurityTest" test
```

Resultado:

```text
Tests run: 43, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Validação completa

Comando:

```powershell
mvn verify
```

Resultado consolidado:

```text
Unit tests: 291; failures: 0; errors: 0; skipped: 0
Integration tests: 47; failures: 0; errors: 0; skipped: 2
BUILD SUCCESS
```

Os dois testes ignorados são cenários de navegador explicitamente opt-in.
