# Interface Specification — Planos e contratos

## Princípios

As jornadas são separadas por titular. “Meu plano” mostra somente o contrato `PERSONAL`; “Plano da conta” exige tenant
ativo e mostra somente aquele contrato. Administração global possui área própria e não assume o plano pessoal do ator.
Códigos técnicos nunca aparecem ao usuário comum.

## Superfícies

### Contrato pessoal

Resumo do `Free`, estado, versão apresentada em linguagem humana e capacidades pessoais disponíveis. No MVP a
composição vazia é apresentada sem sugerir serviços futuros.

### Contrato tenant

Resumo do `Free`, franquia “Usuários associados: usados/reservados de 10”, explicação de que remover ou suspender não
libera vaga e relação segura das ocupações/reservas que o consulente está autorizado a conhecer.

### Administração global

Catálogos separados por `Pessoal` e `Contas`, versões, composição, contratos e diagnóstico. Alterações futuras exibem
prévia de impacto, autenticação recente e confirmação explícita.

## Estados

`Disponível`, `Indisponível`, `Limite atingido`, `Reservado`, `Em fallback`, `Inconsistente`, `Rascunho`, `Publicado`,
`Retirado` e `Fonte indisponível`, sempre com texto/ícone além de cor.

## RFW Platform

A pesquisa realizada na baseline fixada identificou APIs públicas para filtros tipados, grid renderers, seletor de
colunas, banners, botões, grupos de controles, i18n, tokens e layout responsivo. A implementação deverá consumir:

- `RFWFilterSchema` e componentes públicos de filtro;
- `RFWGridRenderers` e `RFWGridColumnSelectorComponent`;
- componentes e variantes públicas de banner/botão;
- tokens, stylesheet agregado e padrões responsivos documentados;
- facades/VOs do Rinos por adapters, sem repositories na view.

## Lacunas prévias à implementação

Não foi localizado contrato público revisado para árvore de categorias, matriz editável de composição nem protocolo
genérico de prévia de impacto. A [análise de lacunas RFW](rfw-gap-analysis.md) registra evidências, APIs candidatas,
compatibilidade e impacto no showroom. Antes das telas administrativas, deve-se decidir separadamente:

1. problema genérico e exemplos de outras hospedeiras;
2. API pública candidata para navegação hierárquica/matriz/prévia;
3. compatibilidade, acessibilidade e impacto no showroom;
4. decisão entre evolução do RFW e composição local estritamente de domínio.

Nenhuma alteração no submódulo está autorizada por esta especificação. As telas também aguardam facades públicas de
consulta/administração do Rinos; bootstrap, avaliação e capacidade não constituem API de apresentação.

## Segurança e acessibilidade

- ações ocultas continuam verificadas na facade;
- plano pessoal nunca aparece em tenant nem vice-versa;
- mensagens de limite não revelam usuário, convite ou contrato sem autorização;
- navegação por teclado, foco previsível, anúncios de estado e contraste seguem RFW;
- prévia não reserva nem altera capacidade; confirmação revalida tudo no backend.
