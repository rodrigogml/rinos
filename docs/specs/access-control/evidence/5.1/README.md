# Evidência da fase 5.1 — Entradas Spring, contexto por UI e jobs

## Integração RFW e Vaadin

- Foram revisados `docs/architecture/rfw-platform-usage.md`, o README/AGENTS do submódulo e o guia público do showroom
  `infrastructure/execution-context.md`.
- A RFW já fornece a fotografia transversal e a propagação delimitada necessárias. Seu contrato proíbe usá-la como
  sessão, cache ou fonte de autorização; portanto, não foi identificada lacuna nem realizada alteração no submódulo.
- `WorkspaceAuthorizationContextAdapter` usa dados do objeto `UI` exato. Ele mantém somente tenant e associação
  pretendidos e não toca `VaadinSession`, `RFWSessionState`, `SecurityContext` ou principal.

## Spring e sessão persistente

- `SpringAuthorizationAdapter` aceita `AuthorizationOperation` tipada e contexto explícito, deriva o principal do
  Spring e nunca consulta authorities como ACL.
- `AuthorizationAuthenticationFacadeImpl` compara a identidade esperada, estado da identidade e sessão, limites
  absoluto/idle e métodos persistidos antes de produzir a garantia corrente.
- Rotas Vaadin e chamadas Spring internas convergem para a mesma `AuthorizationFacade.require(...)`.

## Jobs e operações sistêmicas

- `UserInitiatedAuthorizationJob` não contém decisão nem revisão.
- `UserInitiatedJobAuthorizationService.execute(...)` reautoriza imediatamente antes do callback do primeiro efeito;
  uma negação posterior impede que o callback seja chamado.
- Operações autônomas continuam pelo registry sistêmico exato implementado na fase 4.3 e não passam pelo adapter
  humano.

## Cobertura automatizada

```text
mvn -q "-Dtest=AuthorizationAuthenticationFacadeImplTest,WorkspaceAuthorizationContextAdapterTest,SpringAuthorizationAdapterTest,UserInitiatedJobAuthorizationServiceTest,AccessPublicContractTest,SystemOperationAuthorizerTest" test
```

Os testes cobrem sessão/identidade incompatíveis, chamada interna, rota baseada na UI exata, duas UIs independentes,
revogação entre submissão e início do job e operação sistêmica registrada.
