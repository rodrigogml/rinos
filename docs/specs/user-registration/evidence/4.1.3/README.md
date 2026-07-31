# Evidência da tarefa 4.1.3

## Fronteira adotada

As facades backend coordenam os contratos públicos e traduzem resultados, mas não mantêm uma
transação aberta. Cada operação persistente completa possui uma fronteira interna explícita:

| Caso de uso | Fronteira transacional |
|-------------|------------------------|
| Início local | `RegistrationCreationService.create` |
| Reenvio | `RegistrationResendService.resend` |
| Ativação | `RegistrationActivationService.activate` |
| Aceites adicionais na ativação | `RegistrationActivationService.completeConsent` |
| Solicitação de cancelamento | `RegistrationCancellationService.issue` |
| Confirmação de cancelamento | `RegistrationCancellationService.confirm` |

Essa separação permite que a facade trate o resultado depois da decisão transacional. Em
particular, uma colisão de unicidade pode ser classificada fora da transação que falhou e o futuro
do SMTP somente é concluído depois do commit.

> [!IMPORTANT]
> Não se deve mover `@Transactional` para toda a facade. Uma transação externa faria os services
> participarem da mesma transação ainda não concluída, atrasaria o callback pós-commit e poderia
> transformar o tratamento seguro de uma colisão em falha de commit da própria resposta pública.

## Atomicidade e integrações

- criação reúne reserva da origem, usuário, cadastro, credencial, aceites, prova e auditoria;
- reenvio bloqueia a pendência, aplica a janela, substitui a prova e registra auditoria;
- ativação bloqueia e relê a prova, registra aceite quando necessário, consome a prova, altera os
  dois lifecycles, invalida provas concorrentes e audita;
- cancelamento bloqueia a pendência ou a prova e, na confirmação, invalida provas, altera os
  lifecycles, remove a raiz por cascade e grava o tombstone sem PII;
- `VerificationEmailDispatchService.scheduleAfterCommit` exige transação sincronizada, envia
  somente em `afterCommit` e conclui sem transporte quando há rollback;
- as facades backend dependem de services e configurações, sem acesso direto a repositories.

## Teste de regressão arquitetural

`RegistrationTransactionBoundaryTest` protege:

- a presença de `@Transactional` em todos os seis comandos persistentes;
- a ausência de transação de classe ou método nas quatro facades backend;
- a ausência de repository entre as dependências mantidas pelas facades.

O comportamento de commit e rollback permanece coberto por
`VerificationEmailDispatchServiceTest`, enquanto os testes das facades e services validam os
outcomes e efeitos de cada ciclo.

## Validação focada

Comando:

```powershell
mvn -q "-Dtest=RegistrationTransactionBoundaryTest,VerificationEmailDispatchServiceTest,RegistrationStartFacadeImplTest,RegistrationResendFacadeImplTest,RegistrationActivationFacadeImplTest,RegistrationCancellationFacadeImplTest,RegistrationActivationServiceTest,RegistrationResendServiceTest,RegistrationCancellationServiceTest" test
```

Resultado:

```text
Tests run: 44, Failures: 0, Errors: 0, Skipped: 0
```

## Validação completa

Comando:

```powershell
mvn -q verify
```

Resultado consolidado:

```text
Unit tests: 278; failures: 0; errors: 0; skipped: 0
Integration tests: 47; failures: 0; errors: 0; skipped: 37
```

Os 37 skips são os testes que exigem MySQL externo explicitamente habilitado ou Docker
disponível. As fronteiras transacionais, o pós-commit e todas as integrações independentes desses
provedores passaram.
