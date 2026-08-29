# Interface Specification: Provisionamento do Armazenamento de Tenant

**Feature**: `tenant-storage-provisioning`
**Criada**: 2026-08-29
**Status**: pronta para checklist
**Spec**: [spec.md](./spec.md)
**Plano**: [plan.md](./plan.md)
**Superfície**: [Interaction Surface Architecture](../../architecture/interaction-surfaces.md)

## Interface Coverage

| Surface ID | Tipo | Usuários | Cobertura | Incluído | Adiado/excluído |
|---|---|---|---|---|---|
| SURF-WEB-RINOS | WEB responsiva | criador da conta e administradores globais autorizados | PARTIAL | acompanhamento seguro, inventário e detalhe operacional | backup, restauração, migration manual, terminal e apps nativos |

## Evidência do estado atual

| Área | Evidência | Comportamento atual |
|---|---|---|
| Criação de conta | `api.module.account.AccountCreationFacade` e ausência de view em `ui.module.account` | aceita intenção, mas ainda não possui tela ou acompanhamento |
| Administração de storage | ausência de `ui.module.storage` | não há inventário nem detalhe humano |

## Avaliação RFW

A implementação consultará o guia obrigatório do Rinos, README/AGENTS do submódulo e o showroom. A composição usa
`UIFactory`, ações `FILTER`, `FILTER_CLEAR`, `CANCEL` e `CONFIRM`, `RFWAdvancedFilterComponent`, `Grid` Vaadin,
`RFWBannerComponent`, toast e `Dialog`. Estado e domínio permanecem na facade Rinos. Não há lacuna que justifique
alterar a RFW neste ciclo.

## Interaction Inventory

| Interaction ID | Surface ID | Kind | Change Type | Name | Entry Point |
|---|---|---|---|---|
| INT-WEB-TSP-001 | SURF-WEB-RINOS | PANEL | NEW | Acompanhamento da criação | confirmação e Painel do Usuário |
| INT-WEB-TSP-002 | SURF-WEB-RINOS | SCREEN | NEW | Inventário de armazenamento | Administração do Sistema > Operações |
| INT-WEB-TSP-003 | SURF-WEB-RINOS | PANEL | NEW | Detalhe operacional seguro | linha do inventário ou alerta autorizado |

## Interaction Details

### INT-WEB-TSP-001 — Acompanhamento da criação

**Surface**: SURF-WEB-RINOS
**Surface Type**: WEB
**Change Type**: NEW
**Purpose**: acompanhar a preparação da conta sem expor infraestrutura nem anunciar ativação prematura.
**Actors and Permissions**: somente o criador autenticado do protocolo; a fachada deriva sua identidade.
**Entry and Navigation**: confirmação/replay da criação e Painel do Usuário; retorno ao Painel.
**Content and Data**: nome permitido, estado público, atualização e orientação segura.
**Actions and Behavior**: atualizar ou voltar; sem comandos de repetição, correção ou migration.
**Validation and Feedback**: erro seguro preserva resumo marcado como desatualizado.
**Responsive/Adaptive Behavior**: resumo/timeline em coluna única em telefone.
**Accessibility**: heading, foco, região viva e estado textual/visual acessíveis.
**Localization**: chaves `account.storage.*` e tempo localizado.
**Components and Design System**: shell, banner, toast e botões públicos RFW.
**Integration and Contracts**: `AccountCreationFacade.status` e contrato de storage seguro.
**Telemetry**: somente abertura, atualização, estado público e falha segura.
**Wireframe Requirement**: REQUIRED
**Wireframe**: wireframes/creation-status.md

**Propósito**: informar ao criador se a conta está aguardando, sendo preparada, pronta ou com problema, sem anunciar
que ela já pode operar.

**Atores e permissões**: somente o criador autenticado do protocolo. A fachada deriva a identidade; conhecer UUID não
concede consulta.

**Entrada e navegação**: abre após aceite/replay e pelo Painel do Usuário. Deep link só aceita protocolo do criador;
retorno volta ao Painel, sem tenant ativo implícito.

**Conteúdo e ações**: nome da conta, estado público, última atualização, orientação, atualizar e voltar. Nunca mostra
schema, host, versão, tentativa, fila, etapa interna, script, motivo técnico ou plano. Não há repetir, ignorar,
corrigir ou marcar como pronta.

**Validação e feedback**: indisponibilidade preserva resumo marcado como desatualizado e usa banner seguro;
`ATTENTION` orienta aguardar ou buscar suporte. Mudança relevante usa toast sem trocar o contexto automaticamente.

**Responsividade e acessibilidade**: desktop mostra resumo e timeline pública; tablet/telefone empilham regiões. Foco
entra no título e retorna ao disparador; heading anuncia mudança em região viva; texto, ícone e cor diferenciam
estado. Tab/Enter/Escape, zoom, reflow e teclado virtual preservam todas as ações.

**Localização e componentes**: chaves `account.storage.*`, data/hora no locale e fuso de apresentação. Shell RFW,
banner, toast, botão livre RFW com chave i18n para atualizar e `CANCEL` para voltar; sem CSS estrutural local.

**Integração/telemetria**: `AccountCreationFacade.status` e resumo de prontidão; abertura, atualização, estado
público e falha segura, sem protocolo completo, conta, schema ou detalhe técnico.

**Wireframe**: REQUIRED — [creation-status.md](./wireframes/creation-status.md).

| Estado | Apresentação | Ações | Saída |
|---|---|---|---|
| initial | esqueleto do resumo | voltar | loading |
| loading | região ocupada; último estado preservado | voltar | ready/remote-error |
| empty | N/A — protocolo válido sempre possui estado | voltar | saída segura |
| ready | um dos quatro estados públicos e orientação | atualizar, voltar | loading/saída |
| processing | atualização em curso no botão | voltar | ready/remote-error |
| success | toast após mudança relevante | continuar | ready |
| validation-error | N/A — não há campo editável | N/A | N/A |
| remote-error | banner seguro; conteúdo anterior desatualizado | tentar novamente, voltar | loading |
| offline | N/A — server-side; tratar como remote-error | recarregar | loading |
| access-denied | retorno sem confirmar protocolo alheio | voltar | saída |
| partial-stale | timestamp e aviso de atualização pendente | atualizar | loading |

### INT-WEB-TSP-002 — Inventário de armazenamento

**Surface**: SURF-WEB-RINOS
**Surface Type**: WEB
**Change Type**: NEW
**Purpose**: consultar a frota por estado seguro sem executar operação estrutural.
**Actors and Permissions**: administrador global com chave canônica de consulta.
**Entry and Navigation**: Administração do Sistema > Operações, com retorno ao contexto global.
**Content and Data**: filtros, grid seguro, estado, versão resumida e atualização.
**Actions and Behavior**: filtrar, limpar e abrir detalhe; sem migration, backup ou restauração.
**Validation and Feedback**: filtros tipados, banner seguro e recarga para estado obsoleto.
**Responsive/Adaptive Behavior**: drawer no tablet e lista priorizada no telefone.
**Accessibility**: headings, grid semântico, foco preservado e estados não dependentes de cor.
**Localization**: chaves `storage.operations.*` e duração/tempo localizados.
**Components and Design System**: filtro, `UIFactory`, banner/toast e `Grid` no shell RFW.
**Integration and Contracts**: `TenantStorageStatusFacade.search` com contexto explícito.
**Telemetry**: abertura, filtro estrutural e falha segura sem termos/dados sensíveis.
**Wireframe Requirement**: REQUIRED
**Wireframe**: wireframes/storage-inventory.md

**Propósito**: permitir diagnóstico global autorizado por estado, versão, fila, alerta e falta de progresso sem
comando de migration ou dado funcional.

**Atores e permissões**: contexto global explícito e chaves canônicas de consulta. Papel não basta; ausência de chave
omite entrada e a facade nega chamada direta.

**Entrada e navegação**: Administração do Sistema > Operações. Filtro/deep link por UUID exige consulta autorizada;
voltar preserva filtros apenas no contexto global.

**Conteúdo e ações**: saúde segura, filtros por estado/compatibilidade/alerta, grid de conta permitida, estado, versão
resumida, última transição, idade/progresso e detalhe. Filtrar, limpar e abrir detalhe são permitidos. Não há rodar,
repetir, ignorar migration, backup ou restauração.

**Validação e feedback**: facade valida filtros tipados/paginação; erro mantém critérios e usa banner. Vazio explica
que não há itens correspondentes. Atualização concorrente solicita recarga, sem inferir conclusão.

**Responsividade e acessibilidade**: desktop usa filtros acima do grid; tablet usa drawer; telefone prioriza Conta,
Estado e Atualização e navega para detalhe. Heading precede grid, cabeçalhos são semânticos, foco é preservado e estados
nunca dependem só de cor.

**Localização e componentes**: chaves `storage.operations.*`; durações e tempos localizados. Usa filtro avançado,
`UIFactory`, banner/toast e `Grid` no shell RFW.

**Integração/telemetria**: `TenantStorageStatusFacade.search` e `AuthorizationContext`; loga abertura, filtros
estruturais e falhas seguras, sem termo livre, protocolo, conta, versão ou operação.

**Wireframe**: REQUIRED — [storage-inventory.md](./wireframes/storage-inventory.md).

| Estado | Apresentação | Ações | Saída |
|---|---|---|---|
| initial | shell com filtros fechados | voltar | loading |
| loading | grid ocupado; envio bloqueia somente filtro atual | cancelar navegação | ready/empty/error |
| empty | mensagem contextual sem revelar tenant oculto | limpar filtro | loading |
| ready | grid paginado e resumo seguro | filtrar, abrir detalhe | detalhe/loading |
| processing | filtro em aplicação | cancelar antes do envio | ready/error |
| success | toast apenas para reconhecimento permitido | continuar | ready |
| validation-error | mensagem junto ao filtro | corrigir | ready |
| remote-error | banner sem detalhe técnico | tentar novamente | loading |
| offline | N/A — server-side; usar remote-error | recarregar | loading |
| access-denied | banner e retorno seguro | voltar | saída |
| partial-stale | banner de dados alterados | recarregar | loading |

### INT-WEB-TSP-003 — Detalhe operacional seguro

**Surface**: SURF-WEB-RINOS
**Surface Type**: WEB
**Change Type**: NEW
**Purpose**: diagnosticar uma operação autorizada sem revelar segredos nem controlar migrations.
**Actors and Permissions**: administrador global com chave de detalhe; ações futuras exigem garantia forte.
**Entry and Navigation**: linha autorizada no inventário, retornando ao mesmo filtro e foco.
**Content and Data**: estado, etapa pública, tentativas, tempos, versão segura e orientação.
**Actions and Behavior**: voltar ou abrir futura confirmação de reconciliação/desativação autorizada.
**Validation and Feedback**: estado obsoleto pede recarga; rejeições preservam dados de leitura.
**Responsive/Adaptive Behavior**: timeline vertical em telefone e dialog adaptativo.
**Accessibility**: foco no título, dialog contido e Escape somente cancela.
**Localization**: mensagens i18n e tempos localizados.
**Components and Design System**: `Dialog`, botões, banner, toast e grids RFW/Vaadin.
**Integration and Contracts**: `TenantStorageStatusFacade.details` e resultado seguro.
**Telemetry**: abertura, confirmação, cancelamento, resultado seguro e stale.
**Wireframe Requirement**: OPTIONAL
**Wireframe**: N/A — derivado do inventário e dialog padrão RFW

**Propósito**: permitir diagnóstico de operação, transições, tentativas e orientação externa sem controlar migration
nem revelar segredo.

**Atores e permissões**: administrador global com chave de detalhe. Reconciliação/desativação futura exige chaves
distintas, garantia recente, 2FA e confirmação reforçada.

**Entrada e navegação**: abre a partir de uma linha autorizada; fechar retorna ao mesmo filtro e foco.

**Conteúdo e ações**: estado, protocolo formatado, etapa pública, tentativas, tempos, versão segura, transições,
auditoria visível e orientação. Só permite voltar ou abrir confirmação para futura reconciliação/desativação
autorizada. Nunca executa migration ou recuperação.

**Validação/feedback**: estado obsoleto fecha confirmação e exige recarga; rejeição preserva leitura e usa banner;
conclusão usa toast e recarrega.

**Responsividade/acessibilidade/localização**: timeline vertical no telefone; foco entra no título e fica contido no
dialog; Escape cancela; texto, ícone e cor distinguem risco; textos são i18n e tempos usam locale.

**Componentes/integração**: `Dialog`, botões RFW `CANCEL`/`CONFIRM`, banner/toast e grid/lista. Comando futuro usa
`TenantStorageStatusFacade.details`, contexto explícito e resultado seguro; não leva schema ao cliente.

**Telemetria**: abertura, confirmação, cancelamento, resultado seguro e stale, sem stack trace, schema ou SQL.

**Wireframe**: OPTIONAL — derivado de [storage-inventory.md](./wireframes/storage-inventory.md) e dialog RFW.

| Estado | Apresentação | Ações | Saída |
|---|---|---|---|
| initial | título sem dados | voltar | loading |
| loading | timeline ocupada | voltar | ready/error |
| empty | N/A — detalhe válido tem operação ou motivo seguro | voltar | saída |
| ready | resumo e histórico seguro | voltar, ação autorizada | dialog/saída |
| processing | dialog bloqueia duplicidade | cancelar quando seguro | success/error |
| success | toast e recarga | voltar | ready |
| validation-error | banner/dialog com motivo seguro | corrigir/cancelar | ready |
| remote-error | banner seguro | tentar novamente | loading |
| offline | N/A — server-side; usar remote-error | recarregar | loading |
| access-denied | fechar sem revelar detalhe | voltar | inventário |
| partial-stale | aviso de estado alterado | recarregar | loading |

## Regras compartilhadas

- Nenhuma tela seleciona ou guarda tenant ativo implicitamente.
- Prontidão física não anuncia conta ativa; a saga ainda requer membership, ACL e plano.
- Não existe modo offline local, retry técnico, comando de migration, backup ou restauração.
- Ações ocultas por permissão continuam verificadas na facade.

## Traceability

| Interaction ID | User Stories | Functional Requirements | Success Criteria | Contracts |
|---|---|---|---|---|
| INT-WEB-TSP-001 | US1, US2 | PROV-010..014, STATE-003 | SC-TSP-002, 004, 016 | tenant-storage-provisioning.md |
| INT-WEB-TSP-002 | US3, US5 | OPS-001..008, SEC-001..008 | SC-TSP-009, 010, 012 | tenant-storage-provisioning.md |
| INT-WEB-TSP-003 | US2, US4, US5 | REC-008..013, LIFE-002..006 | SC-TSP-009..013 | tenant-storage-provisioning.md |

## Wireframes

| Interação | Exigência | Artefato |
|---|---|---|
| `INT-WEB-TSP-001` | REQUIRED | [creation-status.md](./wireframes/creation-status.md) |
| `INT-WEB-TSP-002` | REQUIRED | [storage-inventory.md](./wireframes/storage-inventory.md) |
| `INT-WEB-TSP-003` | OPTIONAL | dialog derivado |

## Validation Summary

- Cobertura revisada: sim.
- Todas as interações detalhadas: sim.
- Estados canônicos resolvidos: sim.
- Wireframes obrigatórios presentes: sim.
- Acessibilidade resolvida: sim.
- Contratos mapeados: sim.
- Decisões abertas: 0.
