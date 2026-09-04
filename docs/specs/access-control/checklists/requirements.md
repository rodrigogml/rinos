# Checklist de Requisitos — Controle de Acesso

**Objetivo**: confirmar cobertura, clareza e rastreabilidade antes de schema ou código.

- [x] `FR-ACL-KEY-*` define catálogo, escopo, categoria, ownership, i18n e invisibilidade de código. {auto}
- [x] `FR-ACL-GRP-*` impede aninhamento e separa grupos global e tenant. {auto}
- [x] `FR-ACL-RULE-*` define origem, efeito, estado, vigência e uma regra corrente. {auto}
- [x] `FR-ACL-AUTHZ-*` formaliza negação padrão e precedência de bloqueio. {auto}
- [x] Operação composta declara todas as chaves e usa semântica `TODAS`. {auto}
- [x] Plano, estado, contexto e garantia de autenticação são motivos separados. {auto}
- [x] Explicação administrativa possui escopo e não revela outro tenant. {auto}
- [x] Suporte deriva somente leitura efetiva após bloqueios. {auto}
- [x] Trabalho assíncrono revalida antes do primeiro efeito. {auto}
- [x] Operação autônoma usa origem sistêmica explícita. {auto}
- [x] Continuidade considera grupos, regras diretas, bloqueios, vigência, estado e fator forte. {auto}
- [x] Grupo protegido usa baseline explícita sem inclusão de chave futura. {auto}
- [x] Bootstrap global não transforma o primeiro cadastrado em administrador. {auto}
- [x] Catálogo inicial cobre plataforma, fundação, pessoas, pagamentos e financeiro. {auto}
- [x] SDDs dependentes recebem referência canônica sem duplicar algoritmo. {auto}
- [x] Contrato público descreve request, decision, explicação e motivos de falha. {auto}
- [x] Login e seleção de tenant não congelam chaves, regras ou decisões na sessão ou no principal. {auto}
- [x] Tenant ativo pertence à UI/área de trabalho exata e duas abas podem usar tenants distintos. {auto}
- [x] Cache é limitado por sujeito e contexto, confirma revisão e respeita fronteiras temporais. {auto}
- [x] Notificação apenas antecipa invalidação e não substitui a revisão persistida. {auto}
- [x] Interface separa contexto global e tenant e pesquisa por nome ou descrição. {auto}
- [x] Wireframes cobrem central, matriz e explicação. {auto}
- [x] Quickstart cobre bloqueio, vigência, operação composta, continuidade e falha segura. {auto}
- [x] Análise cruzada entre constituição, specs, catálogo, plano, modelo, interface, contratos, SDDs e tarefas está registrada em `cross-artifact.md`. {auto}
- [x] Não há decisão pendente que bloqueie o planejamento técnico. {humano}

## Resultado

Checklist aprovado como gate documental inicial.

## Follow-up de implementação — 2026-09-02

O enunciado anterior de que schema e código estavam fora do ciclo descreve o fechamento documental original. A
implementação subsequente está rastreada em `../tasks.md`, e a revisão final em
`../evidence/6.3.3/README.md` confirmou que os requisitos ainda mantêm cobertura canônica. Pendências de consumidores,
benchmark e validação manual continuam abertas no backlog; elas não alteram este checklist de qualidade de requisitos.
