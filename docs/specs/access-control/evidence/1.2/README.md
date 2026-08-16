# Evidência 1.2 — Catálogo inicial e readiness

Data: 2026-08-15

## Entrega

- baseline Java com as 90 chaves do catálogo documental v1;
- 24 contributors Spring independentes por módulo proprietário: o núcleo `access-control` e 23 módulos consumidores;
- 12 categorias globais e de tenant com hierarquia sem efeito autorizativo;
- 1.275 vínculos materializados para IDs exatos de requisitos, sem curingas, faixas ou IDs inventados no descriptor;
- registry fail-fast com validação de ownership, escopo, categoria, ciclos, inclusão idempotente e colisão incompatível;
- inativação somente por descriptor explicitamente `INACTIVE`; contributor ativo e inativo para o mesmo código falha a
  inicialização em vez de escolher silenciosamente um deles.

Os contributors dos módulos ainda sem código funcional ficam registrados provisoriamente pela configuração inicial do
módulo `access-control`. Cada bean mantém `moduleCode` próprio e pode ser movido ao módulo proprietário sem alterar
descriptor, registry ou persistência futura.

## Gate de paridade

`AccessKeyCatalogParityTest` lê os SDDs, identifica somente definições formais `FR-*`, expande os seletores do catálogo
Markdown contra esses IDs existentes e compara código, owner, categoria, baseline mínima e requisitos com o catálogo
runtime. O teste exige exatamente 90 chaves e 12 categorias.

Durante a primeira execução o gate encontrou duas divergências no contributor do núcleo: a ampliação de
`FR-ACL-KEY-*` após a revisão documental e a interpretação incorreta da seleção `FR-ACL-ADM-001/006` como faixa. Os
descriptors foram corrigidos antes do encerramento da tarefa.

## Validação

```powershell
mvn -q "-Dtest=br.com.rinos.app.api.module.access.**,br.com.rinos.app.backend.module.access.**" test
mvn -q test
```

Ambos os comandos concluíram com código zero. A sincronização com MySQL continua pertencendo às tarefas 2.1 e 2.2;
nenhuma tabela ou migration foi criada nesta fase.
