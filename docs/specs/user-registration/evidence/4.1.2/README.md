# Evidência da tarefa 4.1.2

## Contratos publicados

O ciclo local expõe quatro facades públicas:

| Facade | Casos de uso | Resultado seguro |
|--------|--------------|------------------|
| `RegistrationStartFacade` | iniciar cadastro | `RegistrationStartResultVO` |
| `RegistrationResendFacade` | reenviar comprovação | `RegistrationResendResultVO` |
| `RegistrationActivationFacade` | ativar e concluir novos aceites | `RegistrationActivationResultVO` |
| `RegistrationCancellationFacade` | solicitar e confirmar cancelamento | `RegistrationCancellationRequestResultVO` e `RegistrationCancellationConfirmationResultVO` |

Todos os métodos recebem DTOs do package público, retornam `CompletionStage` de VOs públicos e
não referenciam UI, backend, entities ou repositories.

## Consulta segura de estado

A consulta segura não é uma operação genérica de busca. O estado é o resultado fechado do próprio
caso de uso executado:

- o início distingue envio, pendência existente, e-mail existente, limite e indisponibilidade;
- o reenvio informa aceite, falha de despacho, limite e indisponibilidade;
- a ativação informa conclusão, repetição, necessidade de aceite, prova inválida ou expirada e
  processo encerrado;
- a solicitação de cancelamento mantém a mesma forma pública neutra quando o cadastro não pode ser
  confirmado;
- somente continuações aplicáveis transportam referência opaca e expiração.

> [!IMPORTANT]
> Não existe endpoint ou facade para pesquisar livremente estado por e-mail, prova ou ID
> persistente. Isso reduz enumeração de cadastros e mantém a interface dependente apenas de
> outcomes públicos. O formato interno da prova pode evoluir sem alterar essa decisão.

## Validação focada

Comando:

```powershell
mvn -q "-Dtest=PublicFacadeContractTest,PublicContractSecurityTest,RegistrationStartFacadeImplTest,RegistrationResendFacadeImplTest,RegistrationActivationFacadeImplTest,RegistrationCancellationFacadeImplTest" test
```

Resultado:

```text
Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
```

O conjunto cobre a fronteira estrutural das facades, a segurança dos DTOs/VOs e os estados
produzidos pelas quatro implementações locais.

## Validação completa

Comando:

```powershell
mvn -q verify
```

Resultado consolidado:

```text
Unit tests: 275; failures: 0; errors: 0; skipped: 0
Integration tests: 47; failures: 0; errors: 0; skipped: 37
```

Os 37 skips correspondem a cenários que exigem MySQL externo explicitamente habilitado ou Docker
disponível. As validações de contrato e as integrações independentes desses provedores passaram.
