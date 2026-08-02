# Evidência da tarefa 7.5.1

## Resultado

O Rinos fornece a recuperação mínima de senha local exigida para liberar o cadastro e registra um
`RFWPasswordRecoveryProvider` real. O recorte técnico está documentado em
[password-recovery-release-slice.md](../../../user-authentication/password-recovery-release-slice.md).

## Contratos comprovados

- solicitação neutra para identidade inexistente, inativa ou sem senha local;
- Turnstile obrigatório na solicitação pelo contrato de verificação humana do RFW;
- prova opaca de uma hora, vinculada ao usuário, persistida somente como hash e consumida uma vez;
- nova emissão invalida a anterior;
- limites configuráveis por origem e usuário;
- mesma política de senha, HIBP e Argon2id do cadastro;
- troca transacional, auditoria sanitizada e retenção coordenada;
- schema global `identity_passwordRecovery` presente tanto no init quanto no update `20260802_001`.

## Validações

```text
mvn -q test
mvn -q '-Dit.test=GlobalDatabaseMigrationIT,IdentityRepositoryIT' failsafe:integration-test failsafe:verify
```

As duas validações concluíram com código zero usando MySQL 9.7.2 para os testes de persistência e migration.
