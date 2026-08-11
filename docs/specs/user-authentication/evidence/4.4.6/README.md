# Evidência 4.4.6 — Testes de recuperação, notificações e manutenção

## Cobertura

- `PasswordRecoveryServiceTest` confirma neutralidade para identidade inexistente e passwordless, uso único da prova
  e invalidação das sessões ao redefinir a senha.
- `AuthenticationNotificationDispatchServiceTest` confirma renderização/envio pós-commit e contenção de falha de
  template/transporte sem nova tentativa automática.
- `VerificationEmailDispatchServiceTest` e `EmailOtpDispatchServiceTest` cobrem falha SMTP e métricas sem expor
  segredos.
- `IdentityCleanupCatalogSchedulerTest`, `PasswordRecoveryCleanupServiceTest`,
  `AuthenticationSessionLifecycleServiceTest` e os testes de liderança cobrem isolamento de tarefas, retenção e
  execução idempotente.
- `AuthenticationSessionRepositoryIT` executa o contrato de retenção/reconhecimento em MySQL 9.7 descartável.

## Validação

```text
mvn -q verify
```

Resultado atual: aprovado.
