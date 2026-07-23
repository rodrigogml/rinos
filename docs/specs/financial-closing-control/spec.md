# Feature Specification: Controle de Fechamento Financeiro

**Feature**: `financial-closing-control`
**Created**: 2026-07-22
**Status**: Clarified — pronta para planejamento

## Escopo

Esta feature estabelece uma única data de fechamento financeiro opcional por tenant. Quando informada, a data forma um limite temporal contínuo: nenhum fato monetário com data de efetivação igual ou anterior a ela pode ser criado, revisado ou cancelado. Quando ausente, não há bloqueio temporal próprio do financeiro.

O tenant pode avançar a data para proteger todo o histórico até um novo limite ou, mediante autorização reforçada, retrocedê-la ou removê-la para reabrir datas anteriores. Toda mudança é motivada e auditada. O fechamento não altera lançamentos existentes, não cria períodos e não materializa saldos oficiais.

> [!IMPORTANT]
> A data de fechamento é a fronteira de imutabilidade. Lançamentos posteriores a ela podem ser revisados ou cancelados com histórico completo; lançamentos alcançados pelo limite ficam imutáveis e somente podem ser corrigidos por novos fatos compensatórios em data aberta.

> [!NOTE]
> Saldos financeiros permanecem derivados do saldo de abertura e dos lançamentos confirmados. Eventuais projeções ou snapshots de desempenho poderão ser introduzidos futuramente como dados reconstruíveis, nunca como fonte de verdade desta feature.

## Clarifications

### Session 2026-07-22

- Q: O controle financeiro precisa cadastrar e fechar períodos? → A: Não. Cada tenant possui somente uma data de fechamento opcional. Avançá-la fecha continuamente todas as datas até o novo limite; retrocedê-la ou removê-la reabre o intervalo correspondente.
- Q: O fechamento precisa registrar snapshots de saldo para auditoria, extratos ou SPED? → A: Não. O histórico imutável das mudanças do fechamento, combinado com datas, versões, confirmações, revisões, cancelamentos, atores e origens dos lançamentos, deve identificar tudo que mudou durante reaberturas. Evidências periódicas contábeis, fiscais e bancárias pertencem às respectivas features futuras.
- Q: O que a data de fechamento torna imutável? → A: Todo lançamento cuja data de efetivação seja igual ou anterior ao limite. Datas posteriores permanecem editáveis mediante autorização e histórico; retroceder ou remover o limite reabre também a possibilidade de revisar e cancelar lançamentos no intervalo correspondente.

## User Scenarios & Testing

### User Story 1 - Consultar o limite temporal vigente (Priority: P1)

Um usuário autorizado consulta se o tenant possui data de fechamento e compreende quais datas estão bloqueadas ou abertas antes de registrar fatos financeiros.

**Why this priority**: O limite precisa ser inequívoco e previsível para todos os módulos que produzem efeitos monetários.

**Independent Test**: Consultar um tenant sem fechamento e outro com uma data definida, comprovando a apresentação correta dos intervalos aberto e bloqueado.

**Acceptance Scenarios**:

1. **Given** tenant sem data de fechamento, **When** o estado é consultado, **Then** o sistema informa que nenhuma data está bloqueada por esta regra.
2. **Given** data de fechamento definida, **When** o estado é consultado, **Then** o sistema informa que datas iguais ou anteriores estão bloqueadas e datas posteriores permanecem abertas.
3. **Given** usuário sem acesso aos dados financeiros protegidos, **When** consulta o estado temporal necessário a uma operação permitida, **Then** recebe somente a decisão necessária sem inferir valores ou lançamentos.

---

### User Story 2 - Avançar a data de fechamento (Priority: P1)

Um usuário autorizado revisa o intervalo que será protegido e avança a data de fechamento para impedir novos fatos até o limite escolhido.

**Why this priority**: Avançar o limite é a operação ordinária que protege conferências financeiras concluídas.

**Independent Test**: Avançar o fechamento e comprovar que toda data até o novo limite fica bloqueada sem alterar fatos existentes.

**Acceptance Scenarios**:

1. **Given** fechamento ausente ou anterior, **When** usuário autorizado confirma uma data válida, **Then** todas as datas até o novo limite ficam bloqueadas atomicamente.
2. **Given** alteração ainda não confirmada, **When** o usuário solicita a prévia, **Then** visualiza o limite atual, o proposto e o intervalo que passará a ser bloqueado.
3. **Given** data proposta futura no calendário local do tenant, **When** a confirmação é solicitada, **Then** o sistema rejeita a alteração sem modificar o fechamento vigente.

---

### User Story 3 - Bloquear efeitos retroativos em todos os módulos (Priority: P1)

Um usuário ou módulo tenta confirmar, revisar, cancelar, estornar ou corrigir um lançamento e recebe uma decisão temporal consistente imediatamente antes da alteração.

**Why this priority**: Uma regra aplicada somente pela interface financeira poderia ser contornada por integrações ou módulos internos.

**Independent Test**: Tentar criar, revisar e cancelar fatos na data fechada por origens diferentes e comprovar que todos são rejeitados sem efeitos parciais.

**Acceptance Scenarios**:

1. **Given** fechamento em determinada data, **When** qualquer módulo tenta confirmar, revisar ou cancelar fato nessa data ou antes, **Then** a operação é bloqueada sem efeito monetário parcial.
2. **Given** fato com data posterior ao fechamento, **When** as demais regras são satisfeitas, **Then** esta feature não impede sua confirmação, revisão ou cancelamento.
3. **Given** fechamento alterado concorrentemente durante uma confirmação, **When** o efeito seria produzido, **Then** a decisão usa o estado vigente de forma atômica e não permite contorno por condição de corrida.

---

### User Story 4 - Reabrir datas anteriores (Priority: P1)

Um usuário com autorização específica informa motivo e retrocede ou remove a data de fechamento para permitir novos fatos, revisões e cancelamentos no intervalo reaberto.

**Why this priority**: Erros e fatos tardios exigem reabertura controlada, sem apagar a proteção e a responsabilidade históricas.

**Independent Test**: Retroceder e depois remover o fechamento, comprovando os intervalos reabertos, a preservação dos fatos existentes e a auditoria das mudanças.

**Acceptance Scenarios**:

1. **Given** fechamento vigente, **When** usuário autorizado retrocede a data com motivo, **Then** somente o intervalo posterior ao novo limite e até o limite anterior é reaberto.
2. **Given** fechamento vigente, **When** usuário autorizado remove a data com motivo, **Then** todo o histórico deixa de ser bloqueado por esta feature.
3. **Given** usuário sem autorização específica ou motivo inválido, **When** tenta retroceder ou remover o fechamento, **Then** a operação é rejeitada e o estado vigente permanece inalterado.

---

### User Story 5 - Auditar reaberturas e novos fatos históricos (Priority: P1)

Um auditor autorizado consulta as mudanças da data de fechamento e identifica os fatos confirmados em intervalos históricos enquanto estavam reabertos.

**Why this priority**: A simplicidade de uma única data só é segura quando retrocessos e seus efeitos permanecem explicáveis.

**Independent Test**: Reabrir um intervalo, confirmar fatos retroativos, fechar novamente e reproduzir quem reabriu, por quê, por quanto tempo e quais fatos foram introduzidos.

**Acceptance Scenarios**:

1. **Given** qualquer mudança de fechamento, **When** a auditoria é consultada, **Then** apresenta tenant, valores anterior e novo, ator, instante, origem e motivo.
2. **Given** intervalo reaberto com fatos incluídos, revisados ou cancelados, **When** a análise é solicitada, **Then** o sistema identifica todas as alterações pela combinação do histórico de fechamento e do histórico dos lançamentos.
3. **Given** novo avanço após reabertura, **When** a auditoria é consultada, **Then** todos os eventos permanecem preservados sem consolidação destrutiva ou snapshot obrigatório.

### Edge Cases

- Tenant recém-criado sem data de fechamento.
- Data proposta igual à data vigente.
- Data proposta futura em relação ao calendário local do tenant.
- Dois usuários tentam alterar o fechamento simultaneamente.
- Confirmação de fato concorre com avanço do fechamento.
- Retrocesso seguido imediatamente por novo avanço.
- Remoção do fechamento seguida de fatos com datas muito antigas.
- Rascunho existente é alcançado por avanço do fechamento.
- Lançamento revisado concorre com avanço do fechamento sobre sua data.
- Estorno de fato antigo precisa ocorrer depois da data fechada.
- Mudança de fuso do tenant altera a interpretação da data corrente.
- Módulo de origem tenta reutilizar decisão temporal obtida antes de uma mudança.
- Usuário pode alterar o fechamento, mas não consultar valores financeiros.
- Histórico possui múltiplas reaberturas parcialmente sobrepostas ao mesmo intervalo.

## Requirements

### Conceitos e Fronteiras

- **FR-FCC-BOUND-001**: O controle de fechamento DEVE pertencer exclusivamente a um tenant e jamais afetar ou revelar o estado de outro tenant.
- **FR-FCC-BOUND-002**: A feature DEVE controlar somente uma data de fechamento contínua e NÃO DEVE criar calendários, exercícios ou períodos financeiros.
- **FR-FCC-BOUND-003**: Alterar o fechamento NÃO DEVE criar, editar, excluir, estornar, corrigir ou consolidar lançamentos financeiros.
- **FR-FCC-BOUND-004**: A feature NÃO DEVE produzir snapshots autoritativos de saldo, lançamentos consolidados nem saldos de encerramento.
- **FR-FCC-BOUND-005**: Fechamentos contábeis, fiscais, bancários, de caixa ou de outros módulos pertencem aos respectivos contextos e PODERÃO acrescentar restrições próprias.
- **FR-FCC-BOUND-006**: A inexistência ou flexibilização do fechamento financeiro NÃO DEVE relaxar bloqueios adicionais impostos legitimamente por outros módulos.
- **FR-FCC-BOUND-007**: O tenant NÃO DEVE possuir data inicial obrigatória para uso do módulo financeiro; a admissibilidade de fatos antigos depende do fechamento vigente e das demais regras de negócio.

### Estado do Fechamento

- **FR-FCC-STATE-001**: Cada tenant DEVE possuir no máximo uma data de fechamento financeiro vigente, que PODERÁ ser nula.
- **FR-FCC-STATE-002**: Data nula DEVE significar que nenhuma data está bloqueada por esta feature.
- **FR-FCC-STATE-003**: Quando preenchida, a data de fechamento DEVE bloquear continuamente todas as datas de efetivação iguais ou anteriores e deixar posteriores abertas quanto a esta regra.
- **FR-FCC-STATE-004**: A data DEVE ser interpretada no calendário e fuso aplicáveis ao tenant, sem depender do fuso da estação do usuário.
- **FR-FCC-STATE-005**: A data de fechamento NÃO DEVE ser posterior à data corrente do tenant no momento da alteração.
- **FR-FCC-STATE-006**: Repetir exatamente o estado vigente DEVE resultar em operação idempotente e não criar mudança de auditoria enganosa.

### Alteração e Reabertura

- **FR-FCC-CHANGE-001**: Definir data posterior à vigente, ou definir a primeira data, DEVE bloquear atomicamente todo o intervalo adicional até o novo limite.
- **FR-FCC-CHANGE-002**: Retroceder a data DEVE reabrir somente o intervalo maior que a nova data e menor ou igual à data anterior.
- **FR-FCC-CHANGE-003**: Remover a data DEVE reabrir todas as datas quanto a esta feature.
- **FR-FCC-CHANGE-004**: Retrocesso e remoção DEVEM exigir autorização específica e motivo compreensível antes da confirmação.
- **FR-FCC-CHANGE-005**: O sistema DEVE apresentar antes da confirmação os valores atual e proposto e o intervalo que será bloqueado ou reaberto.
- **FR-FCC-CHANGE-006**: Mudança confirmada DEVE ser atômica e rejeitar concorrência baseada em estado vigente diferente daquele revisado pelo usuário.
- **FR-FCC-CHANGE-007**: Reabrir datas NÃO DEVE dispensar validações de conta, autorização, classificação, origem ou outros contratos aplicáveis aos novos fatos.
- **FR-FCC-CHANGE-008**: A feature NÃO DEVE avançar, retroceder ou remover automaticamente a data por agenda, passagem do tempo ou atividade financeira.

### Decisão Temporal

- **FR-FCC-BLOCK-001**: Todo módulo que crie, revise, cancele, estorne ou corrija fato monetário DEVE obter e aplicar a decisão temporal imediatamente antes da alteração.
- **FR-FCC-BLOCK-002**: Criação, revisão ou cancelamento que afete data de efetivação igual ou anterior ao fechamento vigente DEVE ser rejeitado sem qualquer efeito parcial.
- **FR-FCC-BLOCK-003**: Não DEVE existir contorno operacional comum, administrativo ou de integração capaz de ignorar o fechamento vigente.
- **FR-FCC-BLOCK-004**: Estorno ou correção de fato em data bloqueada DEVE produzir eventual compensação somente em data posterior ao fechamento, preservando a data do original.
- **FR-FCC-BLOCK-005**: Novo rascunho NÃO DEVE aceitar data bloqueada. Rascunho preexistente alcançado por avanço do fechamento DEVE permanecer identificável, mas somente poderá ser excluído ou movido para data aberta enquanto o bloqueio persistir.
- **FR-FCC-BLOCK-006**: Decisão temporal DEVE considerar tenant e data de efetivação explicitamente e NÃO DEVE aceitar contexto implícito de outro tenant.
- **FR-FCC-BLOCK-007**: A aplicação do bloqueio e a produção do fato monetário DEVEM resistir a alterações concorrentes do fechamento sem permitir condição de corrida.
- **FR-FCC-BLOCK-008**: Falha ao avaliar o fechamento DEVE impedir a produção do fato, sem assumir permissividade por indisponibilidade ou erro.
- **FR-FCC-BLOCK-009**: Revisão que altere data de efetivação DEVE ser permitida somente quando as datas anterior e nova estiverem posteriores ao fechamento vigente.

### Auditoria e Rastreabilidade

- **FR-FCC-AUDIT-001**: Cada mudança efetiva DEVE gerar evento imutável contendo tenant, valor anterior, valor novo, ator, instante, origem e motivo quando exigido.
- **FR-FCC-AUDIT-002**: O histórico NÃO DEVE permitir edição, exclusão ou substituição destrutiva de eventos.
- **FR-FCC-AUDIT-003**: Usuário autorizado DEVE poder consultar e filtrar mudanças por intervalo, ator, origem e direção da alteração.
- **FR-FCC-AUDIT-004**: O sistema DEVE permitir correlacionar cada intervalo reaberto aos fatos confirmados, revisados ou cancelados durante sua vigência por datas, instantes, versões, atores, origens e vínculos.
- **FR-FCC-AUDIT-005**: A correlação DEVE distinguir fato introduzido durante a reabertura de fato preexistente revisado, cancelado ou apenas mantido no intervalo.
- **FR-FCC-AUDIT-006**: Novo avanço do fechamento NÃO DEVE apagar nem consolidar destrutivamente mudanças anteriores ou fatos correlacionados.
- **FR-FCC-AUDIT-007**: Consultas de auditoria DEVEM respeitar autorização parcial e não revelar valores, descrições, contrapartes ou origens além do permitido.

### Autorização e Segurança

- **FR-FCC-SEC-001**: Chaves distintas DEVEM controlar visualização do estado, alteração ordinária, retrocesso ou remoção e consulta da auditoria.
- **FR-FCC-SEC-002**: Papéis de ator NÃO DEVEM conceder essas capacidades por si próprios; o acesso deriva de grupos e chaves no tenant.
- **FR-FCC-SEC-003**: Autorização DEVE ser revalidada na confirmação da mudança e na leitura de dados protegidos.
- **FR-FCC-SEC-004**: Mensagens de rejeição, logs e métricas NÃO DEVEM expor valores financeiros, dados pessoais ou existência de recursos inacessíveis.
- **FR-FCC-SEC-005**: Acesso temporário de suporte DEVE permanecer somente leitura e não poderá alterar, retroceder ou remover o fechamento.

### Experiência e Acessibilidade

- **FR-FCC-UX-001**: A interface DEVE explicar em linguagem direta que o limite inclui a própria data de fechamento.
- **FR-FCC-UX-002**: A prévia DEVE diferenciar avanço, retrocesso e remoção e informar claramente as datas afetadas.
- **FR-FCC-UX-003**: Retrocesso ou remoção DEVE apresentar alerta reforçado sobre a possibilidade de novos fatos retroativos antes da confirmação.
- **FR-FCC-UX-004**: Bloqueio de operação DEVE informar a data efetiva solicitada, o limite aplicável e a ação possível, sem revelar dados protegidos.
- **FR-FCC-UX-005**: Estado, alertas e direção da mudança NÃO DEVEM depender somente de cor e DEVEM ser operáveis por teclado e tecnologias assistivas.

### Confiabilidade e Operação

- **FR-FCC-OPS-001**: Mudanças e decisões temporais DEVEM ser determinísticas, idempotentes quando repetidas e observáveis sem registrar conteúdo financeiro sensível.
- **FR-FCC-OPS-002**: A feature NÃO DEVE depender de agendador, token temporário, chave externa ou integração de terceiros para manter o fechamento vigente.
- **FR-FCC-OPS-003**: Backup, restauração, migração e indisponibilidade do tenant DEVEM seguir `tenant-data-governance` e `tenant-storage-provisioning`, preservando estado e histórico juntos.
- **FR-FCC-OPS-004**: Estado atual e histórico DEVEM permanecer consistentes; falha na persistência de qualquer parte DEVE reverter integralmente a mudança.

## Key Entities

- **Estado Atual do Fechamento Financeiro**: limite opcional vigente de um tenant e referência usada pelas decisões temporais.
- **Mudança da Data de Fechamento**: evento imutável com transição anterior e nova, autoria, instante, origem e motivação.
- **Intervalo Histórico Reaberto**: projeção lógica derivada de um retrocesso ou remoção para identificar quais datas voltaram a aceitar fatos.
- **Decisão Temporal Financeira**: resultado permitido ou bloqueado para uma data de efetivação em determinado tenant e estado vigente.

## Success Criteria

### Measurable Outcomes

- **SC-FCC-001**: Em 100% dos testes cruzados, o fechamento de um tenant não afeta nem revela o estado de outro.
- **SC-FCC-002**: Em 100% dos testes, data igual ou anterior ao fechamento rejeita criação, revisão e cancelamento sem efeito parcial, enquanto data posterior não é bloqueada por esta feature.
- **SC-FCC-003**: Em 100% das mudanças válidas, estado atual e evento de auditoria são persistidos atomicamente.
- **SC-FCC-004**: Em 100% dos testes concorrentes, avanço do fechamento e confirmação de fato não permitem que um efeito em data bloqueada seja produzido.
- **SC-FCC-005**: Em 100% dos retrocessos, somente o intervalo maior que a nova data e menor ou igual à anterior é reaberto.
- **SC-FCC-006**: Em 100% das remoções, nenhuma data permanece bloqueada por esta feature e bloqueios de outros módulos continuam respeitados.
- **SC-FCC-007**: Em 100% dos testes de auditoria, mudanças exibem valores anterior e novo, ator, instante, origem e motivo exigido sem permitir alteração histórica.
- **SC-FCC-008**: Em 100% dos cenários de reabertura testados, fatos incluídos, revisados e cancelados podem ser distinguidos dos fatos apenas preexistentes sem snapshot de saldo.
- **SC-FCC-009**: Em 100% dos testes de autorização, somente chaves adequadas permitem visualizar, avançar, retroceder, remover ou auditar o fechamento.
- **SC-FCC-010**: Usuários autorizados compreendem na prévia quais datas serão bloqueadas ou reabertas e concluem a alteração em até 60 segundos nos testes de usabilidade.
- **SC-FCC-011**: Todas as jornadas principais podem ser concluídas somente por teclado e sem bloqueios críticos para tecnologias assistivas.
- **SC-FCC-012**: Logs, erros e métricas examinados não contêm valores financeiros nem dados pessoais sem finalidade e proteção aprovadas.

## Fora do Escopo

- Cadastro de exercícios, calendários e períodos financeiros.
- Fechamento contábil ou fiscal, apuração, SPED e obrigações acessórias.
- Tarefas, dependências e checklists formais de encerramento periódico.
- Snapshots autoritativos, saldos consolidados ou tabelas obrigatórias de fechamento.
- Saldos inicial e final de linhas de extrato, importação bancária e conciliação.
- Fechamento operacional, contagem ou diferença de caixa físico.
- Regras funcionais de edição, cancelamento, estorno e correção dos lançamentos, que pertencem a `financial-transactions`; esta feature somente decide quais datas estão abertas.
- Avanço automático do fechamento por agenda ou passagem do tempo.
