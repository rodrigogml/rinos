# Evidências da tarefa 4.4.1

## Contrato efetivado

`PasswordRecoveryService.reset(...)` substitui a credencial por meio de
`LocalCredentialService.replaceAndInvalidateSessions(...)`, passando o mesmo usuário, instante UTC e `correlationId`
da operação. A chamada ocorre dentro da transação de recuperação.

Depois do consumo da prova, a mesma transação marca a prova usada e invalida todas as outras provas de recuperação
abertas do usuário. TOTP, passkey e identidade Google não são fatores perdidos pela troca de senha e permanecem
disponíveis; a recuperação de 2FA continua sendo uma tarefa posterior específica.

## Testes e validações

```text
mvn -q -DskipITs '-Dtest=PasswordRecoveryServiceTest,LocalCredentialServiceTest' test
Exit code: 0

mvn -q -DskipUnitTests '-Dit.test=AuthenticationSessionRepositoryIT' verify
Exit code: 0
```

O primeiro teste verifica que o reset chama a operação transacional com a correlação e o instante corretos e que
uma repetição da prova não altera novamente a credencial. O segundo valida contra MySQL 9.7.2 a revogação persistente
de todas as sessões, incluindo o comportamento entre transações/instâncias.
