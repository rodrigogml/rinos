# Evidência 4.4.4 — Notificações pós-commit

## Entrega

- `IdentityAuditService` registra o evento original e, quando aplicável, um evento de solicitação de notificação na
  mesma transação.
- `AuthenticationNotificationDispatchService` usa `@TransactionalEventListener(AFTER_COMMIT)` e o
  `EmailDispatchService` do RFW. Falha de template ou SMTP é observada sem reverter a operação confirmada.
- Mudança de método e recuperação concluída geram uma notificação por evento confirmado.
- Sessão nova só gera `authentication-new-session` quando o digest do navegador não aparece em sessão retida nos
  últimos 30 dias.
- Falhas repetidas são registradas apenas para identidade ativa existente quando a janela do identificador alcança o
  Turnstile; o evento de solicitação é limitado pelo cooldown de 24 horas.
- Os quatro templates são estáticos e não recebem segredo, IP, digest ou cookie.

## Validação

```text
mvn -q -DskipITs -Dtest=AuthenticationNotificationDispatchServiceTest,PasswordAuthenticationFacadeImplTest test
mvn -q -DskipUnitTests -Dit.test=AuthenticationSessionRepositoryIT verify
```
