# Evidência da tarefa 7.2.2

## Provas de uso único

`VerificationServiceTest` comprova que:

- a primeira apresentação válida consome a prova e o replay é rejeitado;
- uma prova emitida para outra finalidade não é aceita nem consumida;
- uma prova pertencente a outro cadastro não é aceita nem consumida.

Os testes verificam os estados públicos e o estado persistente da comprovação, sem expor o token
nos resultados.

## Corridas transacionais

No MySQL 9.7.2, `IdentityRepositoryIT` executa duas disputas:

- duas confirmações do mesmo cancelamento resultam em um único `CANCELLED` e uma rejeição
  `INVALID_PROOF`; usuário, cadastro e provas são removidos uma vez, o tombstone permanece sem PII
  e o e-mail pode ser reutilizado;
- ativação local e conclusão Google concorrentes produzem exatamente um vencedor. Usuário e
  cadastro terminam ativos, com somente a credencial correspondente ao fluxo vencedor.

## Validação focal

Comando:

```powershell
mvn "-Dtest=VerificationServiceTest#consume_shouldVerifyOnceAndRejectReplay_whenTokenIsPresentedTwice+consume_shouldRejectCrossPurposeProof_whenPurposeDoesNotMatch+consume_shouldRejectCrossRegistrationProof_whenRegistrationDoesNotMatch" "-Dit.test=IdentityRepositoryIT#cancelRegistration_shouldChooseOneWinner_andReleaseEmailForNewRegistration+activationRace_shouldCommitExactlyOneLocalOrGoogleWinner" verify
```

Resultado:

```text
Unit tests: 3; failures: 0; errors: 0; skipped: 0
Integration tests: 2; failures: 0; errors: 0; skipped: 0
Database version: 9.7.2
BUILD SUCCESS
```
