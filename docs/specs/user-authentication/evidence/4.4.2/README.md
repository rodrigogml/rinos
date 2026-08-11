# Evidência 4.4.2 — Identidade passwordless na recuperação

## Escopo

O fluxo público de recuperação não pode revelar se o e-mail pertence a uma identidade passwordless nem criar uma
credencial local sem confirmação explícita.

## Evidência automatizada

- `PasswordRecoveryServiceTest.issue_shouldKeepPasswordlessIdentityNeutralWithoutCreatingPasswordOrProof`
  confirma que um usuário ativo sem credencial local recebe `ACCEPTED`, não gera prova, não agenda e-mail e não chama
  qualquer substituição de senha.
- A mensagem `authentication.recovery.request-accepted` é a mesma para qualquer identidade e orienta genericamente
  o retorno ao login para usar um método já configurado, sem nomear ou confirmar Google/passkey.

## Validação

```text
mvn -q -DskipITs -Dtest=PasswordRecoveryServiceTest test
```
