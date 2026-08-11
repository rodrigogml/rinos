# Evidência 4.4.3 — Catálogo de notificações de segurança

## Escopo

Os templates de segurança precisam ter nomes estáveis para o catálogo compartilhado do RFW, e os eventos que
solicitam cada notificação precisam ser distinguíveis na auditoria. O envio, o cooldown e os limiares permanecem na
tarefa 4.4.4.

## Entrega

- `AuthenticationNotificationTemplateEnum` declara `authentication-new-session`,
  `authentication-method-changed`, `authentication-recovery-completed` e
  `authentication-repeated-failures`.
- `IdentityEventTypeEnum` declara os quatro eventos correspondentes de solicitação, sem material secreto.
- `AuthenticationNotificationTemplateEnumTest` protege os identificadores de integração com o RFW.

## Validação

```text
mvn -q -DskipITs -Dtest=AuthenticationNotificationTemplateEnumTest test
```
