# Análise de Lacunas RFW — Planos e contratos

**Data**: 2026-08-16
**Estado**: aguardando decisão e autorização separada

## Baseline auditada

O ponteiro versionado do Rinos referencia a revisão RFW `7559314380021be60f6289c9488933bf74adbdcb`. A cópia local do
submódulo estava previamente em `ba1bfda04335749e405606c77b04f4753e9c9f2a`; a diferença entre as duas revisões é
restrita ao protocolo Turnstile de criação de conta e não altera as capacidades de interface avaliadas aqui. Nenhuma
alteração ou atualização de ponteiro foi realizada nesta análise.

Foram consultados o guia de uso da RFW no Rinos, as instruções do submódulo, o showroom e as APIs públicas de filtros,
picker, grid, banners, botões, grupos de controles, topbar, i18n, tokens e responsividade.

## Capacidades já suficientes

As superfícies de consulta pessoal e tenant podem usar as APIs atuais quando o Rinos publicar facades de leitura:

- `RFWFilterSchema` e `RFWAdvancedFilterComponent` para pesquisa validada por nome, estado e escopo;
- `RFWGridRenderers` e `RFWGridColumnSelectorComponent` para listagens tabulares e seleção de colunas;
- `RFWPicker` para selecionar contrato, versão ou titular em uma etapa de associação, sem usá-lo como exportação;
- `UIFactory`, `RFWButtonActionEnum`, `RFWBannerComponent` e `RFWControlGroup` para ações e estados;
- `RFWTopbarComponent`, `RFWTr`, stylesheet agregado, tokens e padrões responsivos para shell e apresentação.

Essas APIs não justificam alteração no RFW. A view deve depender apenas de facades e VOs públicos do Rinos, aplicar
autorização no backend e tratar indisponibilidade sem reutilizar a última resposta como autoridade.

## Lacuna 1 — Navegação hierárquica

### Problema

Catálogos de direitos e chaves precisam navegar categorias aninhadas, preservar expansão, pesquisar descendentes e
anunciar a seleção por teclado. A baseline não publica componente, factory ou contrato de dados hierárquicos. Montar
um `TreeGrid` diferente em cada hospedeira duplicaria carregamento, estados vazio/erro, acessibilidade e seleção.

### API pública candidata

Propor no RFW um `RFWHierarchyExplorer<T>` configurável por descritores estáveis, provider assíncrono de raízes/filhos,
extratores de rótulo e descrição, modos de seleção e evento explícito de ativação. A API deve expor partes públicas,
estado observável (`INITIAL`, `LOADING`, `RESULTS`, `EMPTY`, `ERROR`), foco inicial e integração opcional com
`RFWFilterSchema`, sem conhecer categorias de acesso ou planos.

### Compatibilidade e showroom

Mudança aditiva. Deve incluir navegação por teclado, RTL, lazy loading, reflow, falha assíncrona, classes/tokens
públicos, testes e laboratório nos seis idiomas do showroom.

## Lacuna 2 — Matriz editável

### Problema

Composição de plano e regras de acesso compartilham uma matriz de linhas e colunas com valor atual, valor proposto,
estado indisponível, conflito e edição por teclado. `Grid`, renderers e seletor de colunas cobrem leitura, mas a RFW não
publica o protocolo de edição, dirty state, erro por célula ou aplicação em lote. Uma implementação local seria
reutilizável por várias hospedeiras e features.

### API pública candidata

Propor `RFWEditableMatrix<R, C, V>` com definições imutáveis de linha/coluna/célula, renderer e editor tipados,
validação por célula e por matriz, estados `READ_ONLY`, `EDITING`, `DIRTY`, `VALIDATING`, `CONFLICT` e `ERROR`, além de
eventos de alteração e solicitação de aplicação. Persistência, autorização, optimistic lock e significado do valor
continuam pertencendo à hospedeira.

### Compatibilidade e showroom

Mudança aditiva. O laboratório deve cobrir teclado, leitor de tela, células indisponíveis, validação, conflito,
densidade, telas estreitas e conjuntos grandes. A API não deve expor entities nem executar mutações de negócio.

## Lacuna 3 — Prévia confirmável de impacto

### Problema

Mudanças sensíveis precisam calcular impacto sem alterar estado, apresentar avisos e confirmar usando a mesma intenção,
com revalidação obrigatória antes do commit. Dialog, banner e botões existem, mas não há máquina de estados genérica
para impedir confirmação duplicada, descartar resposta assíncrona antiga ou tratar prévia obsoleta.

### API pública candidata

Propor `RFWImpactPreviewFlow<I, P, R>` e um provider assíncrono com `preview(I)` e `confirm(previewReference)`. A
referência deve ser opaca; outcomes mínimos: `READY`, `COMPLETED`, `STALE`, `REJECTED` e `UNAVAILABLE`. O componente
cuida de busy state, foco, anúncio acessível, cancelamento e repetição; a hospedeira calcula impacto, reautoriza,
revalida garantia forte e executa a transação.

### Compatibilidade e showroom

Mudança aditiva. Deve haver testes de concorrência de respostas, confirmação única, expiração, cancelamento e falha,
com laboratório e traduções completas. O protocolo não pode transformar a prévia em reserva ou autorização.

## Pré-condições no Rinos

Mesmo com a evolução da RFW, a Fase 7.3 ainda não está liberada pelo backend atual:

1. o slice aprovado declara catálogo administrável por UI fora de escopo;
2. não existem facades públicas para consultar resumo de contrato pessoal/tenant, ocupações autorizadas, catálogo,
   versões ou composição;
3. não existem contratos públicos de prévia/mutação administrativa, optimistic version, paginação ou busca;
4. os contratos atuais são operacionais — bootstrap, avaliação e capacidade — e não devem ser usados como API de
   apresentação.

Antes das telas, é necessário aprovar se o próximo slice entregará apenas consultas pessoal/tenant ou também a
administração global completa. Depois devem ser especificados e implementados os VOs/facades correspondentes, com
chaves de acesso, explicação segura, paginação, auditoria e testes de isolamento.

## Recomendação

Evoluir as três capacidades genéricas no RFW em ciclo separado antes das superfícies administrativas. As telas
somente de resumo podem ser construídas com a baseline atual, mas devem aguardar as facades de leitura do Rinos. Não
implementar matriz, árvore ou máquina de prévia localmente e não alterar o submódulo sem autorização explícita.
