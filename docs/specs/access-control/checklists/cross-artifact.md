# Análise Cruzada — Controle de Acesso

**Data**: 2026-08-15
**Escopo**: constituição, especificação, catálogo, pesquisa, plano, modelo, interface, contratos, quickstart,
checklists, SDDs consumidores e backlog.
**Resultado histórico**: coerente para encerrar o ciclo documental; àquela data, schema e código continuavam não
iniciados.

## Invariantes e fontes

| Invariante | Fonte normativa | Propagação verificada |
|------------|-----------------|-------------------------|
| negação por padrão | constituição II; FR-ACL-AUTHZ-001 | contrato, algoritmo, quickstart, checklist e tarefas 3.1/6.3 |
| permissão existente e nenhum bloqueio por chave | constituição II; FR-ACL-AUTHZ-002/003 | `authorization.md`, research D5, plan, cenários 2–4 e testes 3.1 |
| contexto global e tenant incompatíveis | constituição I/II; FR-ACL-KEY-005 e AUTHZ-006 | modelo, cache por sujeito/contexto, interface separada e quickstart 5 |
| papéis sem efeito autorizativo | constituição II; escopo e cenário US1.6 | fundação, membership, diretório e catálogo |
| plano independente da ACL | FR-ACL-AUTHZ-007; `plans-entitlements` | descriptor, decision gates, explicação, UI e checklist de segurança |
| operação composta usa todas as chaves | FR-ACL-AUTHZ-005 | catálogo transversal, request, plan, quickstart 6 e tarefa 3.2 |
| continuidade pelo resultado efetivo | FR-ACL-CONT-* | research D7, modelo, transações, UI de prévia e tarefa 4.2 |
| falha interna nunca amplia acesso | FR-ACL-AUTHZ-012 | contrato, cache revisionado, quickstart 9 e tarefa 3.3 |
| sessão autentica, mas não congela ACL ou tenant | FR-ACL-AUTHZ-013/014 | auth plan, tenant-context, contrato, interface e tarefa 5.1 |
| cache limitado por sujeito e contexto | FR-ACL-AUTHZ-015/016 | research D8, modelo, plano e tarefa 3.3 |
| vigência independe de mutação | FR-ACL-AUTHZ-017 | research D6/D8, modelo, quickstart 11 e tarefa 3.3 |
| notificação não substitui revisão | FR-ACL-AUTHZ-018 | research D8, plano, modelo e quickstart 10 |
| código técnico invisível | FR-ACL-KEY-004 | catálogo, contrato, todas as interações e tarefa 5.2.3 |

## Cenários obrigatórios

| Cenário | Cobertura documental |
|---------|-----------------------|
| ausência de permissão nega | US1.2; SC-ACL-002; tarefa 3.1 |
| permissão direta autoriza | FR-ACL-RULE-002; SC-ACL-003; tarefa 3.1 |
| permissão de grupo autoriza | quickstart 2; SC-ACL-003; tarefa 3.1 |
| bloqueio direto vence grupo | US1.3; quickstart 3; SC-ACL-004 |
| bloqueio de grupo vence direta | quickstart 4; SC-ACL-004 |
| um grupo bloqueador vence vários permissivos | US4.2; SC-ACL-005 |
| bloqueio de outro tenant não interfere | US1.4; quickstart 5; SC-ACL-006 |
| bloqueio global não interfere no tenant | US3.3; FR-ACL-AUTHZ-006 |
| regra futura ou expirada não produz efeito | FR-ACL-RULE-006..008; quickstart 5 |
| troca de efeito preserva histórico e uma regra corrente | FR-ACL-RULE-004/010; SC-ACL-007; tarefa 4.1.2 |
| operação composta falha por chave ausente ou bloqueada | US1.5; SC-ACL-008; quickstart 6 |
| direito ausente nega sem virar bloqueio | US4.3; FR-ACL-AUTHZ-007; SC-ACL-009 |
| identidade bloqueada invalida todos os contextos | FR-ACL-AUTHZ-004; checklist de segurança |
| último administrador apto é preservado atomicamente | US5; FR-ACL-CONT-*; quickstart 7 |
| revogação afeta novas operações e jobs pendentes | FR-ACL-AUTHZ-009/010; SC-ACL-011; quickstart 8 |
| operação iniciada pode terminar sem autorizar repetição | FR-ACL-AUTHZ-010; `tenant-context-isolation` |
| suporte não supera alcance efetivo | FR-ACL-EXP-006; SC-ACL-012; `tenant-support-access` |
| explicação não revela outro tenant | US4.4; FR-ACL-EXP-004; SC-ACL-013 |
| ocultar ação não substitui serviço | FR-ACL-AUTHZ-008; SC-ACL-015; interface Shared Rules |
| cache ou indisponibilidade falha fechado | FR-ACL-AUTHZ-012; SC-ACL-016; quickstart 9 |
| duas abas mantêm tenants independentes | FR-ACL-AUTHZ-014; SC-ACL-019; quickstart 10; `tenant-context-isolation` |
| fronteira temporal muda decisão sem mutação | FR-ACL-AUTHZ-017; SC-ACL-020; quickstart 11 |
| notificação perdida não mantém acesso | FR-ACL-AUTHZ-018; SC-ACL-021; quickstart 10 |

## Consistência estrutural

- [x] `Access Rule` substitui o antigo grant exclusivamente positivo em spec, modelo, contrato, algoritmo e tarefas.
- [x] `AccessRuleEffect` usa os mesmos valores `PERMITIR` e `BLOQUEAR` em todos os artefatos.
- [x] Regra direta global usa identidade; regra direta tenant usa associação; grupos seguem a mesma fronteira.
- [x] Categoria é hierárquica apenas para navegação e nunca participa da decisão.
- [x] Baseline protegida é uma relação versionada; nova chave não entra automaticamente.
- [x] Vigência usa início inclusivo, término exclusivo e UTC em spec, research, modelo e contrato.
- [x] Snapshot é não persistido, limitado por sujeito/contexto, revisionado e inelegível na próxima fronteira temporal.
- [x] Decisão final, plano, garantia, estado e relógio não são congelados no cache ACL.
- [x] Continuidade verifica o instante atual e fronteiras futuras conhecidas de vigência.
- [x] O singleton de bootstrap não pode possuir duas linhas de estados diferentes.
- [x] Catálogo materializa código, i18n, escopo, categoria, owner, estado e requisitos de origem de cada descriptor.
- [x] Cada chave possui exatamente um módulo proprietário; consumidores adicionais ficam na rastreabilidade.

## Interfaces e RFW

- [x] Foram consultados os guias públicos do showroom para botões, filtering, picker, banner e toast.
- [x] O guia de contexto de execução foi revisado; tenant por UI fica no Rinos e a fotografia RFW não vira sessão ou cache.
- [x] A interface usa somente ações existentes de `RFWButtonActionEnum`; intenções locais usam overload i18n da
  `UIFactory`.
- [x] Matriz e explicação são composições de domínio do Rinos, sem cópia de componente ou CSS estrutural paralelo.
- [x] A lacuna e o protocolo de eventual evolução separada da RFW estão registrados em `interface-spec.md`.
- [x] Estados, responsividade, teclado, foco, i18n e não dependência exclusiva de cor estão especificados.

## SDDs consumidores

- [x] `account-registration` referencia grupo fundador e baseline explícitos.
- [x] `account-membership` calcula último administrador pelo acesso efetivo.
- [x] `tenant-context-isolation` reavalia permissões e bloqueios para novas operações.
- [x] `tenant-context-isolation` delimita tenant por UI/área e proíbe ACL ou tenant comum na sessão compartilhada.
- [x] `tenant-support-access` deriva leitura somente depois de todos os bloqueios e gates.
- [x] `system-directory-administration` preserva a excepcionalidade da recuperação.
- [x] `plans-entitlements` distingue plano, ausência de permissão e bloqueio.
- [x] Pessoas, relacionamentos, dados de pagamento e módulos financeiros apontam para o catálogo canônico sem
  duplicar o algoritmo de precedência.

## Gate de saída documental

- [x] Requisitos gerais aprovados em `requirements.md`.
- [x] Segurança e isolamento aprovados em `security.md`.
- [x] Backlog executável criado em `tasks.md` com dependências e quality gates.
- [x] Nenhum schema, entity, migration ou código de aplicação foi criado neste ciclo.
- [x] `docs/briefing/humaine-outline.md` permaneceu intocado.

## Follow-up de implementação — 2026-09-02

O gate acima é o registro imutável do encerramento documental de 2026-08-15. Ele não descreve o estado atual do
repositório: schema global, contratos, núcleo de decisão, administração, adapters e interface foram implementados em
fases posteriores. A revisão final de implementação está em `../evidence/6.3.3/README.md`; `tasks.md` é a fonte de
verdade para as integrações consumidoras, benchmark e validações manuais que ainda faltam.
