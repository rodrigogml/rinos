# Evidência da fase 4.4 — Explicação administrativa

## Contrato e isolamento

- `AccessExplanationRequest` separa o consulente autenticado da operação-alvo e não aceita origem sistêmica como
  consulente administrativo.
- `AuthorizationFacadeImpl` constrói internamente a requisição exata para `global.access.explain` ou
  `tenant.access.explain`; modo `ADMINISTRATIVE` não concede permissão.
- Em tenant, a associação do consulente é revalidada no contexto do alvo antes de qualquer resolução das regras do
  alvo. Falhas usam somente `ACL_EXPLANATION_FORBIDDEN`.
- A decisão explicada preserva resultado por chave, permissões, bloqueios, fontes ignoradas por vigência e gates de
  estrutura, plano e garantia, mantendo esses motivos distintos de bloqueio de chave.
- Negações marcadas como sensíveis geram `SENSITIVE_AUTHORIZATION_DENIED` em transação própria, com dados minimizados:
  contexto, ator/origem, correlação, código da operação, quantidade de chaves e um motivo seguro.

## Cobertura automatizada

- `AuthorizationFacadeImplTest`: ausência da chave de explicação, bloqueio, gate de plano, garantia insuficiente,
  exigência do modo administrativo e interrupção antes da resolução do alvo cross-tenant.
- `AccessExplanationRequestTest`: separação consulente/alvo, associação por escopo e rejeição de origem sistêmica.
- `AuthorizationGateProviderTest` e `AccessRuleResolutionServiceTest`: plano, autenticação forte, vigência e fontes
  diretas/de grupo.
- `AccessPublicContractTest`: DTO público imutável e sem dependência de persistência.
- `AccessDecisionAuditServiceTest`: persistência da negação sensível e ausência de segredo, fonte ou associação nos
  detalhes.

Comando focal:

```text
mvn -q "-Dtest=AccessExplanationRequestTest,AccessPublicContractTest,AuthorizationFacadeImplTest,AuthorizationGateProviderTest,AccessRuleResolutionServiceTest,AccessDecisionAuditServiceTest" test
```
