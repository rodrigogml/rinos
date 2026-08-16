# Evidência 1.1 — Contratos públicos de autorização

Data: 2026-08-15

## Entrega

- contratos imutáveis em `api.module.access` para descriptor, ator, contexto, garantia, request, decisão, resultado por
  chave, gates, origens e explicação;
- enums públicos para escopo, estado, efeito, ator, origem e modo de explicação;
- `AuthorizationFacade` com decisão, exigência e explicação pelo mesmo request canônico;
- `AccessKeyContributor` e contributor inicial do próprio módulo `access-control`;
- registry imutável e fail-fast que valida ownership, colisões incompatíveis, categorias, escopo e ciclos antes da
  futura sincronização persistente;
- descriptors tipados das 13 chaves globais e de tenant pertencentes ao núcleo de acesso.

Nenhum contrato contém entity ou repository, e nenhuma chave ou decisão foi adicionada ao principal ou à sessão.

## Validação

```powershell
mvn -q "-Dtest=AccessKeyDescriptorTest,AuthorizationRequestTest,AuthorizationDecisionTest,AccessPublicContractTest,AccessKeyRegistryServiceTest" test
mvn -q test
```

Ambos os comandos concluíram com código zero. A suíte completa também confirmou que o novo registry não interfere nos
fluxos existentes descobertos pelo component scan.

## Limite desta tarefa

A tarefa 1.1 não sincroniza banco. Contributors dos demais módulos, persistência do catálogo e validação de readiness
contra o estado persistido permanecem nas tarefas 1.2 e 2.1.
