# Evidência da tarefa 7.2.1

## Autoridades de unicidade

O schema global reforça as duas invariantes com constraints do MySQL:

- `uk_identity_user_normalized_email` em `identity_user.normalizedEmail`;
- `uk_identity_external_identity_issuer_subject` em
  `identity_externalIdentity(issuer, subject)`.

Os testes usam duas transações e duas threads liberadas pelo mesmo latch. Cada disputa comprova que
exatamente uma escrita conclui, a outra é rejeitada por integridade e somente uma linha permanece.

## Cenários concorrentes

`IdentityRepositoryIT` executa:

- `save_shouldChooseOneUser_whenNormalizedEmailIsWrittenConcurrently`;
- `save_shouldChooseOneExternalIdentity_whenProviderKeyIsWrittenConcurrently`.

No primeiro cenário, representações distintas convergem para o mesmo e-mail normalizado. No
segundo, usuários diferentes disputam a mesma chave imutável do provedor.

## Validação no MySQL

Comando:

```powershell
mvn "-Dit.test=IdentityRepositoryIT#save_shouldChooseOneUser_whenNormalizedEmailIsWrittenConcurrently+save_shouldChooseOneExternalIdentity_whenProviderKeyIsWrittenConcurrently" verify
```

Ambiente observado:

```text
Database version: 9.7.2
Isolation level: REPEATABLE_READ
```

Resultado:

```text
Integration tests: 2; failures: 0; errors: 0; skipped: 0
BUILD SUCCESS
```

As duas disputas produziram a rejeição `1062 / SQLState 23000` esperada para a escrita perdedora,
uma pela constraint de e-mail normalizado e outra pela constraint de `issuer + subject`.
