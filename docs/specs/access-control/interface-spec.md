# Interface Specification: Controle de Acesso por Grupos e Chaves

**Feature**: `access-control` | **Date**: 2026-08-15 | **Surface**: `SURF-WEB-RINOS`

## Interface Coverage

| Surface ID | Surface Type | Users | Coverage | Entry points |
|------------|--------------|-------|----------|--------------|
| SURF-WEB-RINOS | WEB | administradores globais e de tenant autorizados | FULL | Administração do sistema ou Configurações da conta |

Não há aplicativo nativo, CLI ou API REST pública nesta feature.

## Design System and RFW Assessment

Antes da implementação, a equipe consulta `docs/architecture/rfw-platform-usage.md`, README/AGENTS do submódulo e o showroom. A composição usa APIs públicas existentes:

- `UIFactory` e `RFWButtonActionEnum` para as ações públicas existentes (`INSERT`, `SAVE`, `EDIT`, `FILTER`,
  `FILTER_CLEAR`, `CANCEL` e `CONFIRM`); abrir e explicar, por serem intenções locais sem enum público, usam o
  overload livre da factory com chave i18n e ícone RFW.
- `RFWFilterSchema` e `RFWAdvancedFilterComponent` para pesquisa por nome e descrição; a facade do Rinos traduz a
  requisição validada para consulta parametrizada e limitada.
- `RFWPicker` para selecionar participantes e chaves quando a escolha exigir busca.
- `UIFactory.createBanner(...)`/`RFWBannerComponent` para impedimento que precisa permanecer consultável e
  `UIFactory.show*Toast(...)`/`RFWToastComponent` para confirmação transitória.
- `Grid` e `TreeGrid` Vaadin no shell RFW para matriz e árvore de categorias.
- `Dialog` Vaadin com botões RFW para confirmação reforçada e prévia de impacto.

**Lacuna avaliada**: foram consultados os guias do showroom `components/buttons.md`, `filtering.md`, `picker.md`,
`banner.md`, `toast.md` e `infrastructure/execution-context.md`, além das APIs públicas correspondentes. O contexto RFW
é fotografia de execução e o estado padrão pertence à `VaadinSession`; por isso o Rinos mantém o tenant na `UI` exata
e apenas o projeta por adapter, sem usar o RFW como sessão ou cache ACL. O RFW atual não contém matriz de autorização,
árvore/matriz integrada de efeitos nem painel de explicação reutilizável. Eles permanecem composições do Rinos
porque dependem do contrato de regras e contexto; não se cria CSS estrutural paralelo. Não há alteração no RFW neste
ciclo. Se a implementação comprovar uma lacuna genérica, o trabalho para e apresenta previamente problema, API
pública proposta, compatibilidade e impacto para hospedeiras, testes e atualização obrigatória do showroom.

## Interaction Inventory

| Interaction ID | Surface ID | Surface Type | Change Type | Actors and Permissions | Summary |
|----------------|------------|--------------|-------------|------------------------|---------|
| INT-WEB-ACL-001 | SURF-WEB-RINOS | WEB | NEW | Administrador do contexto com consulta ACL | Central de acessos |
| INT-WEB-ACL-002 | SURF-WEB-RINOS | WEB | NEW | Administrador com gestão de grupos e regras | Editor de grupo e matriz |
| INT-WEB-ACL-003 | SURF-WEB-RINOS | WEB | NEW | Administrador com gestão de regras | Regra direta do sujeito |
| INT-WEB-ACL-004 | SURF-WEB-RINOS | WEB | NEW | Administrador com explicação ACL | Explicação do acesso efetivo |
| INT-WEB-ACL-005 | SURF-WEB-RINOS | WEB | NEW | Administrador apto a alterar baseline | Prévia de alteração protegida |

## Shared Rules

- A tela sempre mostra o nome do contexto e não infere tenant pela URL ou por último estado local.
- Cada `UI`/área mantém sua própria referência mínima de tenant e associação; a sessão autenticada compartilhada não
  contém chaves, decisão efetiva nem um tenant ativo comum às abas.
- Trocar entre global e tenant exige nova autorização e limpa seleção, filtros, dados e explicações anteriores.
- Capacidades carregadas em lote para montar menus, estados e ações são apenas projeções revogáveis; confirmação e
  chamada direta são sempre reautorizadas no serviço.
- Pesquisa usa nome e descrição localizados; nenhum campo, coluna ou mensagem visível mostra código técnico.
- Estados possíveis de regra: permitido, bloqueado, ausente, expirado, futuro, desativado e indisponível pelo plano.
- “Indisponível pelo plano” não é bloqueio; a explicação deve preservar a diferença.
- Alteração protegida exige autenticação recente e TOTP/passkey quando o contrato da operação indicar garantia forte.

- Conteúdo omitido por falta de acesso não é substituído por detalhes que revelem outro tenant.

## Interaction Details

### INT-WEB-ACL-001 — Central de acessos

**Surface**: SURF-WEB-RINOS
**Surface Type**: WEB
**Change Type**: NEW
**Actors and Permissions**: administrador do contexto com chave de consulta de catálogo, grupos ou regras.

**Purpose**: listar grupos, participantes e chaves disponíveis no contexto selecionado.

**Entry and Navigation**: rota global sob Administração do Sistema e rota tenant sob Configurações da Conta; deep link exige contexto válido e retorna à central após edição.

**Content and Data**: cabeçalho de contexto; campo de pesquisa; árvore de categorias; abas Grupos, Participantes e Catálogo; grids exibem nome, descrição, estado e resumo de efeito, nunca código.

**Actions and Behavior**: filtrar, expandir árvore, abrir grupo, abrir sujeito, criar grupo e solicitar explicação. Ações ausentes são escondidas e chamadas diretas continuam protegidas pela facade.

**Validation and Feedback**: filtro vazio mostra catálogo navegável; erro de autorização usa banner seguro; sucesso transitório usa toast; erros de versão concorrente solicitam recarga sem descartar texto ainda não enviado.

**Responsive/Adaptive Behavior**: desktop usa árvore e grid lado a lado; tablet alterna painéis; telefone mostra uma aba/painel por vez com botão de retorno e ações no rodapé.

**Accessibility**: árvore e grids operam por teclado; foco entra no título após troca de contexto; expansão anuncia estado; ícones possuem rótulo; contraste e reflow seguem tokens RFW.

**Localization**: todos os textos são i18n; nome e descrição são localizados; datas de vigência usam locale; o código interno não tem tradução nem exposição.

**Components and Design System**: shell RFW, filtering, `TreeGrid`, `Grid`, botão `INSERT` para novo grupo,
`FILTER`/`FILTER_CLEAR` para pesquisa, ações locais de abrir/explicar criadas pela `UIFactory`, banner e toast.

**Integration and Contracts**: consulta lista de catálogo/grupos da facade e usa `AuthorizationDecision` para visibilidade; nenhuma entity chega à UI.

**Telemetry**: registra abertura, filtro e erro de carregamento sem termo pesquisado sensível ou conteúdo de explicação.

**Wireframe Requirement**: REQUIRED
**Wireframe**: wireframes/access-center.md

**States**:

| State | Expected Presentation | Available Actions | Transition/Exit |
|-------|-----------------------|-------------------|-----------------|
| initial | skeleton contextual | voltar | carregamento |
| loading | grid e árvore ocupadas | cancelar navegação | ready, empty ou remote-error |
| empty | mensagem sem resultados | limpar filtro, criar se permitido | loading |
| ready | árvore e listas completas | filtrar, abrir, criar | editor ou explicação |
| processing | ação local indisponível | cancelar quando seguro | success ou validation-error |
| success | toast breve | continuar | ready |
| validation-error | mensagem no controle | corrigir | processing |
| remote-error | banner persistente | tentar novamente | loading |
| offline | N/A — aplicação server-side sem modo offline | recarregar | loading |
| access-denied | banner seguro e retorno | voltar | saída |
| partial-stale | banner de revisão | recarregar dados | loading |

### INT-WEB-ACL-002 — Editor de grupo e matriz

**Surface**: SURF-WEB-RINOS
**Surface Type**: WEB
**Change Type**: NEW
**Actors and Permissions**: administrador do contexto com gestão de grupos e regras.

**Purpose**: criar ou manter grupo, participantes e regras de grupo no contexto explícito.

**Entry and Navigation**: aberto a partir da central; retorno preserva filtro e contexto, nunca dados de outro tenant.

**Content and Data**: formulário de nome/descrição/estado; participantes do grupo; matriz por categoria e chave; cada linha mostra permitido, bloqueado, ausente, futuro, expirado, desativado ou indisponível pelo plano.

**Actions and Behavior**: adicionar/remover participante; trocar efeito de regra; definir vigência; desativar grupo; abrir prévia de impacto. Grupo protegido mostra baseline e não permite bloquear/remover chave mínima.

**Validation and Feedback**: nome único, intervalo válido, escopo e plano são validados no servidor. Salvar falha inteiro em conflito/continuidade; preview precede alteração sensível.

**Responsive/Adaptive Behavior**: matriz vira lista de categorias expansíveis no telefone; ações de salvar/cancelar permanecem acessíveis no rodapé.

**Accessibility**: o efeito é selecionado por controle textual, nunca somente cor; foco vai ao primeiro erro; tabela oferece cabeçalhos e rótulos de célula.

**Localization**: nomes, descrições e estados vêm de i18n; termo técnico da chave não aparece.

**Components and Design System**: `FormLayout`, `Grid`, árvore Vaadin, filtering RFW, picker, botões RFW, banner, toast e dialog.

**Integration and Contracts**: usa comandos de administração e `AccessExplanation` resumido para a prévia.

**Telemetry**: registra tentativa, sucesso, rejeição de continuidade e conflito, sem listar regras no evento.

**Wireframe Requirement**: REQUIRED
**Wireframe**: wireframes/group-editor.md

**States**:

| State | Expected Presentation | Available Actions | Transition/Exit |
|-------|-----------------------|-------------------|-----------------|
| initial | formulário sem edição | cancelar | loading |
| loading | campos e matriz ocupados | voltar | ready ou remote-error |
| empty | grupo novo sem regras | adicionar regra/participante | ready |
| ready | formulário editável | salvar, prévia, cancelar | processing |
| processing | salvar bloqueado | cancelar somente antes do envio | success ou validation-error |
| success | toast e retorno/lista | continuar | ready ou central |
| validation-error | erro junto ao campo/regra | corrigir | ready |
| remote-error | banner com retry | tentar novamente | loading |
| offline | N/A — server-side | recarregar | loading |
| access-denied | retorno seguro | voltar | central |
| partial-stale | conflito de versão | recarregar, comparar dados locais | loading |

### INT-WEB-ACL-003 — Regra direta do sujeito

**Surface**: SURF-WEB-RINOS
**Surface Type**: WEB
**Change Type**: NEW
**Actors and Permissions**: administrador do contexto com gestão de regras diretas.

**Purpose**: administrar regra direta de identidade global ou associação de tenant.

**Entry and Navigation**: aberta pelo participante da central; sujeito e contexto são somente leitura.

**Content and Data**: seleção de chave por nome/descrição, efeito, vigência, estado e justificativa quando aplicável.

**Actions and Behavior**: criar, trocar efeito, ajustar vigência ou desativar; informar que bloqueio vence regras de grupo e abrir prévia se a alteração for sensível.

**Validation and Feedback**: rejeita chave de outro escopo, duplicidade corrente e intervalo inválido; segue `INT-WEB-ACL-002`.

**Responsive/Adaptive Behavior**: formulário de coluna única em telefone.

**Accessibility**: picker e controles de data operam por teclado e possuem rótulos.

**Localization**: usa textos localizados e não revela códigos.

**Components and Design System**: RFWPicker, FormLayout, botões RFW, banner/toast.

**Integration and Contracts**: comandos de regra direta e `AuthorizationDecision` para prévia.

**Telemetry**: tentativa e resultado sem expor alvo a quem não pode consultá-lo.

**Wireframe Requirement**: N/A
**Wireframe**: N/A

**States**:

| State | Expected Presentation | Available Actions | Transition/Exit |
|-------|-----------------------|-------------------|-----------------|
| initial | formulário vazio | voltar | loading |
| loading | dados em carregamento | voltar | ready ou remote-error |
| empty | sem regra corrente | criar regra | ready |
| ready | formulário editável | salvar ou cancelar | processing |
| processing | gravação em curso | aguardar | success ou validation-error |
| success | toast | continuar | ready |
| validation-error | erro de campo | corrigir | ready |
| remote-error | banner | tentar novamente | loading |
| offline | N/A — server-side | recarregar | loading |
| access-denied | retorno seguro | voltar | central |
| partial-stale | revisão mudou | recarregar | loading |

### INT-WEB-ACL-004 — Explicação do acesso efetivo

**Surface**: SURF-WEB-RINOS
**Surface Type**: WEB
**Change Type**: NEW
**Actors and Permissions**: administrador com chave de explicação no mesmo contexto do alvo.

**Purpose**: demonstrar decisão por chave sem confundir ausência, bloqueio, plano ou garantia de autenticação.

**Entry and Navigation**: aberta a partir de grupo, sujeito ou operação; só aceita alvo no contexto que o administrador pode conhecer.

**Content and Data**: resumo permitido/negado, gates estruturais, plano, autenticação, resultado por chave, permissões, bloqueios, vigências e condição decisiva.

**Actions and Behavior**: filtrar por chave/nome, expandir origem autorizada, copiar somente mensagem segura e voltar ao ponto de origem.

**Validation and Feedback**: explicação indisponível ou negada não revela se grupo, usuário ou tenant existe.

**Responsive/Adaptive Behavior**: detalhes expansíveis verticalmente no telefone.

**Accessibility**: resumo é anunciado antes da tabela; expansão mantém foco; estado nunca depende só de cor.

**Localization**: nomes e causas seguras localizados; códigos técnicos ocultos.

**Components and Design System**: banner, TreeGrid/Grid, controles expansíveis Vaadin e botões RFW.

**Integration and Contracts**: `AccessExplanation` e `AuthorizationDecision` em modo administrativo.

**Telemetry**: registra consulta e falha técnica sem o conteúdo da explicação.

**Wireframe Requirement**: REQUIRED
**Wireframe**: wireframes/access-explanation.md

**States**:

| State | Expected Presentation | Available Actions | Transition/Exit |
|-------|-----------------------|-------------------|-----------------|
| initial | resumo vazio | voltar | loading |
| loading | explicação carregando | voltar | ready ou remote-error |
| empty | N/A — decisão sempre possui resultado | voltar | saída |
| ready | decisão detalhada | expandir ou voltar | saída |
| processing | N/A — somente leitura | voltar | saída |
| success | N/A — consulta não altera dados | voltar | saída |
| validation-error | N/A — sem edição | voltar | saída |
| remote-error | banner seguro | tentar novamente | loading |
| offline | N/A — server-side | recarregar | loading |
| access-denied | retorno seguro | voltar | central |
| partial-stale | versão divergiu | consultar novamente | loading |

### INT-WEB-ACL-005 — Prévia de alteração protegida

**Surface**: SURF-WEB-RINOS
**Surface Type**: WEB
**Change Type**: NEW
**Actors and Permissions**: administrador autorizado a alterar grupo, regra, associação ou estado protegido.

**Purpose**: mostrar impacto antes de mudança que possa reduzir administração mínima.

**Entry and Navigation**: dialog aberto por editor de grupo, regra, associação, fator forte ou estado.

**Content and Data**: mudança proposta, administradores aptos antes/depois, baseline protegida afetada e motivo de possível rejeição.

**Actions and Behavior**: cancelar ou confirmar após reautenticação exigida. Confirmação revalida tudo no servidor; prévia não reserva resultado.

**Validation and Feedback**: se último administrador seria perdido, confirmação fica indisponível com explicação segura. Mudança concorrente fecha a prévia e requer recarga.

**Responsive/Adaptive Behavior**: dialog ocupa viewport móvel com ações fixas.

**Accessibility**: foco entra no título, fica contido no dialog e retorna ao disparador; tecla Escape apenas cancela.

**Localization**: textos de risco claros e localizados.

**Components and Design System**: `Dialog` Vaadin, `RFWBannerComponent` e botões `CANCEL`/`CONFIRM`.

**Integration and Contracts**: comando de simulação e comando final; ambos usam `AuthorizationDecision` e continuidade.

**Telemetry**: abertura, cancelamento, confirmação e rejeição sem listar chaves em log público.

**Wireframe Requirement**: N/A
**Wireframe**: N/A — modal derivado do editor de grupo.

**States**:

| State | Expected Presentation | Available Actions | Transition/Exit |
|-------|-----------------------|-------------------|-----------------|
| initial | dialog sem cálculo | cancelar | loading |
| loading | impacto calculando | cancelar | ready ou remote-error |
| empty | N/A — não abre sem mudança | cancelar | saída |
| ready | impacto e decisão | confirmar ou cancelar | processing |
| processing | confirmação em curso | aguardar | success ou validation-error |
| success | dialog fecha e toast aparece | continuar | editor |
| validation-error | impedimento explicado | corrigir ou cancelar | editor |
| remote-error | banner no dialog | tentar novamente | loading |
| offline | N/A — server-side | recarregar | loading |
| access-denied | dialog fecha seguro | voltar | editor |
| partial-stale | mudança concorrente | recarregar editor | loading |

## Traceability

| Interaction ID | User Stories | Functional Requirements | Success Criteria | Contracts |
|----------------|--------------|-------------------------|------------------|-----------|
| INT-WEB-ACL-001 | US2, US3 | FR-ACL-KEY-*, GRP-* | SC-ACL-001, 018 | authorization.md |
| INT-WEB-ACL-002 | US2, US5 | FR-ACL-GRP-*, RULE-*, CONT-* | SC-ACL-004..011 | authorization.md |
| INT-WEB-ACL-003 | US2 | FR-ACL-RULE-* | SC-ACL-004..007 | authorization.md |
| INT-WEB-ACL-004 | US1, US4 | FR-ACL-EXP-*, AUTHZ-* | SC-ACL-009, 012, 013 | authorization.md |
| INT-WEB-ACL-005 | US5 | FR-ACL-CONT-* | SC-ACL-010 | authorization.md |

## Validation Summary

- Coverage matrix reviewed: yes.
- All inventory items detailed: yes.
- Canonical states resolved: yes.
- Required wireframes present: yes.
- Accessibility requirements resolved: yes.
- Contract mappings verified: yes.
- Placeholders or open decisions remaining: 0.
