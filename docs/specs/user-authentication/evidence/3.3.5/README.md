# Evidência 3.3.5 — Invalidação por estado e senha

## Escopo validado

- o catálogo aceita cancelamento terminal de identidade ativa, bloqueada ou desativada;
- qualquer transição operacional para estado diferente de `ACTIVE` revoga todas as sessões;
- estado e sessões mudam na mesma transação e usam a correlação original;
- criação inicial de senha não fabrica invalidação;
- substituição de senha existente exige usuário ativo e autoridade de sessão disponível;
- novo hash e revogação total pertencem à mesma transação;
- outra instância rejeita imediatamente cookies emitidos antes do bloqueio ou da troca de senha.

> [!NOTE]
> A tarefa 4.4.1 continua responsável por mover a recuperação de senha existente para a nova operação e agregar
> a invalidação das provas e dos fatores aplicáveis. Esta etapa entrega a invariant de sessões sem antecipar a
> política completa de recuperação.

## Execução reproduzível

```powershell
mvn -q "-Dtest=UserLifecycleServiceTest,LocalCredentialServiceTest,RegistrationActivationServiceTest,ExternalRegistrationCompletionServiceTest,RegistrationCancellationServiceTest" "-Dit.test=AuthenticationSessionRepositoryIT" verify
```

Ambiente: Java 25, MySQL 9.7.2 e schema temporário exclusivo removido pelo harness.

## Rastreabilidade

| Tema | Prova principal |
|------|-----------------|
| Catálogo e revogação ao sair de `ACTIVE` | `UserLifecycleServiceTest` |
| Troca de hash e revogação total | `LocalCredentialServiceTest` |
| Efeito observado entre instâncias | `AuthenticationSessionRepositoryIT` |
| Compatibilidade com ativação/cancelamento do cadastro | testes dos serviços de cadastro |
