# Evidência da tarefa 7.4.3

## Matriz validada

| Comportamento | Métrica | Evidência executável |
|---------------|---------|----------------------|
| Dispatch SMTP aceito | `rinos.registration.verification.smtp.attempts` e `.duration`, resultado `accepted` | `VerificationEmailDispatchServiceTest` |
| Falha de template | mesmas métricas, resultado `template_failure` | `VerificationEmailDispatchServiceTest` |
| Falha de transporte | mesmas métricas, resultado `transport_failure` | `VerificationEmailDispatchServiceTest` |
| Reenvio | `rinos.registration.operations` e `.operation.duration`, operação `resend` | `RegistrationObservabilityServiceTest` e `RegistrationResendFacadeImplTest` |
| Bloqueio | operação com resultado `rate_limited` e lifecycle `blocked` | `RegistrationObservabilityServiceTest` e testes das facades |
| Ativação | operação `activate` e lifecycle `activated` | `RegistrationObservabilityServiceTest` e `RegistrationActivationFacadeImplTest` |
| Cancelamento | operação `cancellation_confirm` e lifecycle `cancelled` | `RegistrationObservabilityServiceTest` e `RegistrationCancellationFacadeImplTest` |
| Limpeza | lifecycle `expired`, incrementado pela quantidade confirmada | `RegistrationObservabilityServiceTest` e `RegistrationExpiryCleanupServiceTest` |

Os testes também comprovam que correlation ID e identificadores persistentes não se tornam tags,
e que e-mail, IP, senha, prova e URL não aparecem nas métricas nem no log de falha SMTP.

## Validação focal

```powershell
mvn "-Dtest=RegistrationObservabilityServiceTest,VerificationEmailDispatchServiceTest,RegistrationResendFacadeImplTest,RegistrationActivationFacadeImplTest,RegistrationCancellationFacadeImplTest,RegistrationExpiryCleanupServiceTest" test
```

Resultado: 29 testes executados, sem falhas, erros ou testes ignorados, e `BUILD SUCCESS`.
