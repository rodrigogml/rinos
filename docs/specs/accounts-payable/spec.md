# Feature Specification: Contas a Pagar

**Feature**: `accounts-payable`
**Created**: 2026-07-22
**Status**: Clarified — pronta para planejamento

## Escopo

Esta feature permite ao tenant registrar, organizar e liquidar obrigações de pagamento. Uma obrigação representa um compromisso com uma contraparte e pode possuir uma ou várias parcelas, vencimentos próprios, classificações financeiras, dimensões, instruções de pagamento e origem manual ou modular.

Contas a pagar e lançamentos financeiros possuem responsabilidades distintas. A obrigação e suas parcelas explicam **o que deve ser pago e quando**; somente uma liquidação confirmada produz efeito na conta financeira. Previsões, vencimentos e atrasos não alteram saldo por si próprios.

Liquidações podem ser totais ou parciais e devem preservar principal, descontos, juros, multas, tarifas e outros componentes de maneira explicável. O vínculo entre obrigação, parcela, liquidação e lançamento financeiro permite consultar tanto a agenda futura quanto o histórico efetivamente pago sem duplicar efeitos.

> [!IMPORTANT]
> Estado da obrigação, estado da parcela e situação de vencimento são conceitos distintos. Uma obrigação pode estar parcialmente liquidada, enquanto algumas parcelas estão vencidas e outras ainda não venceram.

> [!NOTE]
> Esta feature registra e controla a obrigação interna. Ela não executa pagamentos em bancos, não transmite Pix, boletos ou remessas e não cria documentos fiscais, contratos, pedidos ou folha de pagamento.

## Clarifications

### Session 2026-07-22

- Q: Como pagamentos e parcelas podem ser relacionados? → A: A relação deve ser flexível: uma liquidação pode ser alocada a várias parcelas, inclusive de obrigações distintas compatíveis, e cada parcela pode receber várias liquidações parciais. Todas as alocações devem reconciliar exatamente, e pagamentos a credores diferentes permanecem liquidações distintas, ainda que sejam confirmados em lote.
- Q: Como a aprovação deve funcionar na primeira versão? → A: Fluxos, políticas, estados e alçadas de aprovação ficam adiados para um MVP futuro. Nesta versão, usuários com as chaves necessárias podem confirmar e liquidar obrigações diretamente; autorização e auditoria continuam obrigatórias e não constituem aprovação de negócio.
- Q: Quando recorrências devem gerar novas obrigações? → A: Recorrências de lançamentos, obrigações, direitos e pagamentos serão tratadas em SDD próprio e transversal. A futura feature deverá detalhar frequências semanais, quinzenais, mensais, bimestrais, trimestrais, semestrais, anuais, a cada dois anos e personalizadas, além de exceções, agrupamentos e alterações aplicáveis somente de determinada ocorrência em diante.
- Q: Uma obrigação total ou parcialmente liquidada pode ser alterada? → A: Sim. Estado pago ou liquidado não cria imutabilidade. Enquanto todas as datas financeiras afetadas estiverem abertas, obrigação, parcelas, liquidações, alocações e lançamentos correspondentes podem ser corrigidos ou cancelados de forma coordenada, atômica e versionada. A única fronteira de imutabilidade financeira é a data de fechamento; depois dela, os fatos protegidos exigem compensação em data aberta.
- Q: Toda obrigação confirmada deve possuir contraparte credora cadastrada? → A: Sim. A obrigação confirmada exige uma pessoa credora do mesmo tenant, sem requerer papel de fornecedor nem associação com usuário. Rascunhos podem permanecer sem credor; a jornada pode oferecer cadastro rápido, sempre obedecendo `party-registration` e sem criar duplicidades silenciosas.

## Interface Coverage

| Superfície humana conhecida | Tipo | Atores | Cobertura | Comportamento funcional | Exclusões ou adiamentos |
|-----------------------------|------|--------|-----------|-------------------------|-------------------------|
| Web responsiva | Web | Usuários autorizados do tenant | FULL | Cadastrar, revisar, consultar e liquidar obrigações e parcelas, inclusive em lote quando seguro | Aprovações, execução bancária e documentos de módulos externos permanecem fora desta versão |
| Aplicativo móvel nativo | Mobile | Usuários do tenant | N/A | Não existe superfície móvel nativa definida | Toda capacidade nativa permanece fora do produto atual |
| Aplicação desktop nativa | Desktop | Usuários do tenant | N/A | Não existe superfície desktop nativa definida | Toda capacidade desktop nativa permanece fora do produto atual |
| CLI/TUI | CLI/TUI | Operadores | N/A | Não há jornada humana de contas a pagar por terminal | Administração operacional por terminal não integra a feature |

## User Scenarios & Testing

### User Story 1 - Registrar obrigação a pagar (Priority: P1)

Um usuário autorizado registra uma obrigação com contraparte, descrição, moeda, valor, datas e classificação suficientes para representar o compromisso sem afetar o saldo financeiro.

**Why this priority**: A obrigação é a unidade central para organizar compromissos futuros e impedir que previsões sejam confundidas com pagamentos realizados.

**Independent Test**: Cadastrar obrigação simples com parcela única, consultá-la entre os valores em aberto e comprovar que nenhuma conta financeira foi movimentada.

**Acceptance Scenarios**:

1. **Given** tenant e contraparte válidos, **When** usuário confirma obrigação completa, **Then** ela passa a compor a agenda financeira sem alterar saldos.
2. **Given** classificação ou dimensão obrigatória ausente, **When** usuário tenta confirmar a obrigação, **Then** o sistema preserva o rascunho e informa o que deve ser corrigido.
3. **Given** referência de outro tenant, **When** cadastro é solicitado, **Then** a operação é negada sem revelar o dado externo.
4. **Given** rascunho sem credor, **When** usuário tenta confirmá-lo, **Then** o sistema exige selecionar ou cadastrar uma pessoa do tenant antes de concluir.

---

### User Story 2 - Parcelar e programar vencimentos (Priority: P1)

Um usuário autorizado divide a obrigação em parcelas com valores e vencimentos próprios, podendo aceitar uma sugestão ou ajustar cada parcela antes da confirmação.

**Why this priority**: Parcelas determinam a agenda real de compromissos e precisam reconciliar exatamente com o valor da obrigação.

**Independent Test**: Criar obrigação em várias parcelas, ajustar valores e datas e comprovar sequência completa e soma exata.

**Acceptance Scenarios**:

1. **Given** valor e quantidade de parcelas, **When** usuário solicita distribuição, **Then** o sistema sugere valores e vencimentos cuja soma corresponde ao total.
2. **Given** distribuição manual com diferença, **When** confirmação é solicitada, **Then** o sistema rejeita a inconsistência sem ajustar valores silenciosamente.
3. **Given** parcelas confirmadas, **When** a obrigação é consultada, **Then** cada vencimento e o saldo pendente são apresentados individualmente e no total.

---

### User Story 3 - Liquidar total ou parcialmente (Priority: P1)

Um usuário autorizado registra pagamento realizado por uma conta financeira, informa valores efetivos e aloca o resultado às parcelas abrangidas.

**Why this priority**: A liquidação conecta o compromisso planejado ao fato monetário efetivo e precisa explicar diferenças sem alterar saldo diretamente.

**Independent Test**: Liquidar parcialmente uma parcela, verificar lançamento financeiro, saldo residual e posterior quitação sem duplicidade.

**Acceptance Scenarios**:

1. **Given** parcela aberta e apta, **When** pagamento é confirmado, **Then** lançamento de saída e alocações são criados atomicamente e o saldo pendente é recalculado.
2. **Given** pagamento com desconto ou acréscimo, **When** confirmação é solicitada, **Then** cada componente é explicitado e o valor debitado reconcilia com a liquidação.
3. **Given** repetição equivalente da confirmação, **When** a solicitação é processada novamente, **Then** nenhum pagamento ou lançamento adicional é produzido.
4. **Given** várias parcelas compatíveis do mesmo credor, **When** usuário confirma um único pagamento distribuído entre elas, **Then** cada alocação reduz somente seu saldo e a soma reconcilia com a liquidação.

---

### User Story 4 - Controlar atrasos, prioridades e agenda (Priority: P1)

Um usuário autorizado consulta parcelas vencidas, a vencer e liquidadas, com saldos, contrapartes, categorias e prioridades suficientes para planejar pagamentos.

**Why this priority**: O valor do contas a pagar depende de tornar compromissos acionáveis antes do vencimento e evidenciar atrasos sem processamento manual.

**Independent Test**: Consultar a agenda em diferentes datas e comprovar mudança derivada entre a vencer e vencida sem alterar registros ou criar lançamentos.

**Acceptance Scenarios**:

1. **Given** parcela aberta com vencimento anterior à data de referência, **When** agenda é consultada, **Then** ela aparece como vencida com quantidade de dias e saldo pendente.
2. **Given** pagamento parcial, **When** totais por período são consultados, **Then** somente o saldo residual compõe o valor ainda a pagar.
3. **Given** parcelas em moedas diferentes, **When** visão consolidada é solicitada sem conversão definida, **Then** os totais permanecem separados por moeda.

---

### User Story 5 - Corrigir, cancelar e estornar (Priority: P2)

Um usuário autorizado corrige ou cancela obrigações e liquidações enquanto todas as datas financeiras afetadas estiverem abertas e usa efeitos compensatórios quando alguma delas já estiver protegida pelo fechamento.

**Why this priority**: Erros e cancelamentos são inevitáveis, mas não podem apagar pagamentos nem fatos já protegidos pelo fechamento financeiro.

**Independent Test**: Corrigir obrigação já liquidada enquanto a data estiver aberta e comprovar revisão atômica do pagamento; depois fechar a data e comprovar que a mesma correção exige compensação posterior.

**Acceptance Scenarios**:

1. **Given** obrigação parcial ou totalmente liquidada e todas as datas afetadas abertas, **When** usuário autorizado corrige obrigação, parcelas ou pagamento, **Then** todos os efeitos dependentes são revisados atomicamente e as versões anteriores permanecem auditáveis.
2. **Given** obrigação marcada como liquidada com pagamento em data aberta, **When** usuário autorizado corrige valor, moeda, credor ou alocações, **Then** o estado pago não bloqueia a alteração e nenhuma dependência fica inconsistente.
3. **Given** ao menos um lançamento de pagamento em data bloqueada, **When** correção é solicitada, **Then** o fato protegido permanece imutável e a correção exige compensação em data aberta.

---

### User Story 6 - Receber obrigações de outros módulos (Priority: P2)

Um módulo autorizado cria ou atualiza obrigação originada por processo próprio, preservando identidade, contexto e responsabilidade sobre correções.

**Why this priority**: Compras, fiscal, contratos e folha poderão originar compromissos sem permitir duplicação ou edição financeira que contradiga o processo de origem.

**Independent Test**: Receber duas vezes o mesmo evento modular e comprovar uma única obrigação; tentar conteúdo divergente e obter conflito rastreável.

**Acceptance Scenarios**:

1. **Given** evento modular novo e válido, **When** é aceito, **Then** obrigação e parcelas preservam origem estável e nenhuma liquidação é presumida.
2. **Given** repetição equivalente, **When** origem envia novamente o evento, **Then** o resultado existente é reutilizado sem duplicação.
3. **Given** obrigação controlada por origem, **When** usuário tenta alterar campo protegido diretamente, **Then** a operação é recusada e orientada ao fluxo responsável.

### Edge Cases

- Obrigação sem contraparte apta ou com contraparte desativada.
- Contraparte é alterada ou excluída depois da criação.
- Instrução de pagamento é substituída depois de selecionada.
- Valor total é menor que a quantidade de parcelas na menor unidade monetária.
- Vencimento cai em dia inexistente, fim de semana ou feriado.
- Pagamento ocorre antes do vencimento, depois do vencimento ou parcialmente.
- Desconto supera componentes positivos ou acréscimos tornam o total inesperado.
- Pagamento em moeda diferente da obrigação.
- Pagamento excede o saldo pendente.
- Uma liquidação é repetida, concorrente ou estornada parcialmente.
- Parcela paga participa de conciliação ou de processo externo posterior.
- Obrigação ou parcela é cancelada com dependências ativas.
- Módulo de origem é desativado ou deixa de fornecer contexto apresentável.
- Fechamento financeiro avança entre a preparação e a confirmação do pagamento.

## Requirements

### Conceitos e Fronteiras

- **FR-AP-BOUND-001**: Toda obrigação, parcela, liquidação e alocação DEVE pertencer exclusivamente a um tenant.
- **FR-AP-BOUND-002**: Obrigação DEVE representar compromisso de pagamento e NÃO DEVE alterar saldo de conta financeira antes de uma liquidação confirmada.
- **FR-AP-BOUND-003**: Parcela DEVE representar parte exigível da obrigação, com valor, vencimento, estado e saldo pendente próprios.
- **FR-AP-BOUND-004**: Liquidação DEVE representar pagamento efetivamente reconhecido e produzir seu efeito por `financial-transactions` ou `financial-transfers`, conforme o caso.
- **FR-AP-BOUND-005**: Conta a pagar NÃO DEVE ser confundida com transferência entre contas próprias, compra de cartão, pedido, contrato, documento fiscal, folha ou lançamento contábil.
- **FR-AP-BOUND-006**: A feature NÃO DEVE executar ordens bancárias nem afirmar que a instituição externa realizou o pagamento.
- **FR-AP-BOUND-007**: Situação vencida, a vencer ou em atraso DEVE ser derivada da data de referência, vencimento, saldo pendente e estado, sem transição periódica obrigatória.

### Obrigação e Parcelas

- **FR-AP-OBL-001**: Obrigação DEVE possuir identidade estável, descrição, moeda, data de emissão ou reconhecimento, contraparte credora, valor total, estado, origem e instante de criação.
- **FR-AP-OBL-002**: Obrigação PODERÁ preservar referência externa, competência, observações, prioridade, instrução de pagamento e documentos associados quando fornecidos por capacidade própria.
- **FR-AP-OBL-003**: Toda obrigação confirmada DEVE possuir ao menos uma parcela e a soma das parcelas DEVE corresponder exatamente ao valor total na precisão da moeda.
- **FR-AP-OBL-004**: Cada parcela DEVE possuir sequência, quantidade total, vencimento, valor original, componentes vigentes, saldo pendente e estado.
- **FR-AP-OBL-005**: Sistema DEVE sugerir divisão uniforme e vencimentos periódicos, permitir ajustes e resolver diferenças de arredondamento de forma explícita e determinística.
- **FR-AP-OBL-006**: Categoria, dimensões e contraparte comuns PODERÃO ser herdadas como proposta, mas cada parcela DEVE preservar a classificação efetivamente confirmada para ela.
- **FR-AP-OBL-007**: Classificações DEVEM obedecer `financial-categories` e `financial-dimensions`, inclusive rateios, obrigatoriedades e proibições.
- **FR-AP-OBL-008**: Contraparte DEVE referenciar pessoa do mesmo tenant e preservar apresentação histórica mínima suficiente para que alterações cadastrais não tornem a obrigação incompreensível.
- **FR-AP-OBL-009**: Instrução selecionada de `party-payment-details` DEVE preservar a versão e o snapshot usados, sem criar autorização ou presumir verificação bancária.
- **FR-AP-OBL-010**: Valor original, desconto, juros, multa, tarifa, imposto, honorário e outros componentes DEVEM permanecer distinguíveis e reconciliar com o valor vigente aplicável.
- **FR-AP-OBL-011**: Rascunho PODERÁ ser salvo sem credor, mas toda obrigação confirmada DEVE referenciar exatamente uma pessoa credora apta do mesmo tenant.
- **FR-AP-OBL-012**: Pessoa credora NÃO DEVE precisar possuir papel de fornecedor, cliente ou outro papel de negócio, e sua associação à obrigação NÃO DEVE conceder acesso, participação ou permissão.
- **FR-AP-OBL-013**: Cadastro rápido de credor DEVE aplicar integralmente `party-registration`, inclusive identidade, isolamento, validação, pesquisa prévia de possíveis duplicidades e autorizações próprias.
- **FR-AP-OBL-014**: Cadastro rápido NÃO DEVE criar automaticamente papel comercial, instrução de pagamento nem relacionamento adicional além da pessoa selecionável como credora.

### Estados

- **FR-AP-STATE-001**: Obrigação DEVE distinguir ao menos rascunho, aberta, parcialmente liquidada, liquidada e cancelada.
- **FR-AP-STATE-002**: Estado agregado da obrigação DEVE ser derivado de seu fluxo e das parcelas, sem ocultar estados individuais divergentes.
- **FR-AP-STATE-003**: Liquidação somente DEVE ser permitida quando a obrigação e as parcelas abrangidas estiverem abertas ou parcialmente liquidadas e aptas ao pagamento.
- **FR-AP-STATE-004**: Esta versão NÃO DEVE criar estado implícito de aprovado nem exigir decisão de aprovação; futuras políticas e alçadas deverão possuir especificação própria.
- **FR-AP-STATE-005**: Estados parcialmente liquidada ou liquidada DEVEM refletir os efeitos vigentes e NÃO DEVEM constituir fronteira de imutabilidade nem impedir correção autorizada enquanto as datas financeiras afetadas estiverem abertas.

### Liquidação e Alocação

- **FR-AP-PAY-001**: Liquidação DEVE identificar data efetiva, conta pagadora, moeda, valor debitado, parcelas abrangidas, componentes, ator ou origem e identidade idempotente.
- **FR-AP-PAY-002**: Confirmação DEVE validar atomicamente tenant, obrigação, parcelas, saldos pendentes, conta, moeda, fechamento, classificação, autorização e repetição.
- **FR-AP-PAY-003**: Liquidação e respectivo lançamento financeiro DEVEM ser confirmados ou rejeitados como uma única operação de negócio.
- **FR-AP-PAY-004**: Pagamento parcial DEVE reduzir somente o valor alocado e manter o restante aberto sem reescrever o valor original da parcela.
- **FR-AP-PAY-005**: Uma liquidação DEVE poder ser alocada a uma ou várias parcelas, e cada parcela DEVE poder receber uma ou várias liquidações até seu saldo pendente ser integralmente quitado.
- **FR-AP-PAY-006**: Principal, desconto, juros, multa, imposto, tarifa, honorário e diferença cambial DEVEM ser explicitados de modo que o débito na conta e a redução da obrigação sejam reconciliáveis.
- **FR-AP-PAY-007**: Componentes econômicos adicionais DEVEM possuir categorias e dimensões próprias quando diferirem da classificação do principal.
- **FR-AP-PAY-008**: Pagamento em moeda diferente DEVE preservar valor liquidado na moeda da obrigação e memória de conversão do valor efetivado na moeda da conta.
- **FR-AP-PAY-009**: Pagamento excedente NÃO DEVE ser absorvido silenciosamente; a confirmação deve impedir excesso ou encaminhar a diferença para capacidade explicitamente definida.
- **FR-AP-PAY-010**: Instrução de pagamento serve como memória e orientação e NÃO DEVE ser tratada como prova de execução externa.
- **FR-AP-PAY-011**: Liquidações futuras ou apenas programadas NÃO DEVEM criar lançamento confirmado nem afetar saldo atual.
- **FR-AP-PAY-012**: Parcelas reunidas na mesma liquidação DEVEM pertencer ao mesmo tenant, credor e moeda da obrigação; obrigações distintas PODERÃO ser combinadas quando satisfizerem essas condições.
- **FR-AP-PAY-013**: A soma das alocações NÃO DEVE exceder os saldos pendentes e DEVE reconciliar exatamente a redução das obrigações com principal, descontos e acréscimos da liquidação.
- **FR-AP-PAY-014**: Pagamentos destinados a credores diferentes DEVEM permanecer liquidações independentes, ainda que uma operação em lote permita prepará-los e confirmá-los conjuntamente.

### Agenda, Consulta e Operações em Lote

- **FR-AP-AGENDA-001**: Usuário autorizado DEVE poder pesquisar por contraparte, período de emissão, competência, vencimento, pagamento, estado, atraso, moeda, faixa de valor, categoria, dimensão, prioridade e origem.
- **FR-AP-AGENDA-002**: Agenda DEVE separar vencido, vencendo, futuro, parcialmente liquidado, liquidado e cancelado.
- **FR-AP-AGENDA-003**: Totais DEVEM usar saldos pendentes e permanecer separados por moeda sem conversão explícita.
- **FR-AP-AGENDA-004**: Usuário DEVE poder navegar entre obrigação, parcelas, liquidações, lançamentos e origem quando autorizado em cada contexto.
- **FR-AP-AGENDA-005**: Ações em lote DEVEM validar cada item, apresentar efeitos antes da confirmação e informar sucessos e falhas individualmente sem estado parcial oculto.
- **FR-AP-AGENDA-006**: Operação em lote NÃO DEVE misturar tenants nem contornar fechamento, autorização, classificação ou idempotência.

### Origem Modular

- **FR-AP-SOURCE-001**: Origem DEVE distinguir cadastro manual de obrigação coordenada por outro módulo.
- **FR-AP-SOURCE-002**: Origem modular DEVE preservar módulo, tipo, identidade estável do evento e versão quando aplicável.
- **FR-AP-SOURCE-003**: Repetição equivalente DEVE reutilizar o resultado existente; repetição divergente DEVE ser rejeitada e sinalizada.
- **FR-AP-SOURCE-004**: Campos controlados pelo módulo de origem somente DEVEM ser corrigidos por fluxo coordenado por ele; dados financeiros complementares DEVEM declarar sua responsabilidade.
- **FR-AP-SOURCE-005**: Falha no contas a pagar DEVE impedir que a origem considere o compromisso criado ou alterado quando fizerem parte da mesma operação de negócio.
- **FR-AP-SOURCE-006**: Ausência posterior do módulo de origem NÃO DEVE apagar obrigações nem impedir a apresentação histórica mínima preservada.

### Revisão, Cancelamento e Estorno

- **FR-AP-REV-001**: Revisão DEVE preservar identidade, versões completas, motivo, ator ou origem, instante e efeitos anteriores e novos na obrigação, parcelas, liquidações, alocações e lançamentos coordenados.
- **FR-AP-REV-002**: Obrigação, parcela ou liquidação DEVE poder ser revisada ou cancelada por usuário autorizado quando todas as datas financeiras afetadas estiverem abertas e as dependências forem tratadas na mesma operação de negócio.
- **FR-AP-REV-003**: Cancelamento lógico NÃO DEVE apagar obrigação, parcela, liquidação, alocação, lançamento ou histórico; seus efeitos vigentes DEVEM ser retirados ou revisados de forma coordenada quando permitido.
- **FR-AP-REV-004**: Correção que alcance liquidação ou lançamento em data aberta DEVE revisar ou cancelar seus efeitos pelo fluxo de `accounts-payable`; se alguma data afetada estiver bloqueada, o fato protegido DEVE permanecer imutável e a correção DEVE usar compensação em data aberta.
- **FR-AP-REV-005**: Estorno financeiro DEVE reabrir exatamente os saldos alocados às parcelas, sem exceder valores anteriormente liquidados.
- **FR-AP-REV-006**: Dependência de conciliação ou outro módulo DEVE ser coordenada antes ou durante a correção e PODE impedir a operação enquanto permanecer incompatível, mas NÃO DEVE criar imutabilidade financeira distinta do fechamento.
- **FR-AP-REV-007**: Alterar vencimento, contraparte, moeda, valor, classificação ou instrução DEVE reavaliar estados, saldos e dependências afetadas.
- **FR-AP-REV-008**: Marcação de pago, liquidado, conciliado ou outro estado operacional NÃO DEVE, isoladamente, impedir revisão permitida pela data de fechamento e pelos contratos das origens envolvidas.
- **FR-AP-REV-009**: Mudança de credor, moeda, conta pagadora, valores ou alocações após liquidação somente DEVE ser confirmada se todos os efeitos financeiros e snapshots dependentes forem revisados atomicamente e continuarem consistentes.

### Autorização, Auditoria e Privacidade

- **FR-AP-SEC-001**: Acesso DEVE ser negado por padrão e liberado por chaves específicas do tenant.
- **FR-AP-SEC-002**: Consultar, criar, alterar rascunho, confirmar obrigação, revisar obrigação ou liquidação em data aberta, liquidar, pagar retroativamente, cancelar, compensar fato bloqueado, consultar valores, revelar instrução, operar em lote e exportar DEVEM admitir autorizações independentes conforme o risco.
- **FR-AP-SEC-003**: Papel do ator NÃO DEVE conceder permissão por si próprio nem substituir acesso à contraparte, conta, categoria, dimensão ou instrução relacionada.
- **FR-AP-SEC-004**: Criações, revisões, confirmações, liquidações, cancelamentos, estornos, repetições divergentes, operações em lote e negações sensíveis DEVEM ser auditadas.
- **FR-AP-SEC-005**: Usuário sem acesso a valor, contraparte, conta ou instrução NÃO DEVE inferi-los por filtros, totais, contagens, erros, histórico ou vínculos.
- **FR-AP-SEC-006**: Logs, erros e métricas NÃO DEVEM expor valores, saldos, documentos pessoais nem instruções financeiras sem finalidade e proteção aprovadas.

### Experiência e Acessibilidade

- **FR-AP-UX-001**: Interface DEVE distinguir claramente obrigação, parcela, vencimento, pagamento programado e liquidação efetiva.
- **FR-AP-UX-002**: Antes de confirmar obrigação ou liquidação, usuário DEVE visualizar valores, componentes, datas, classificações, conta, contraparte, instrução e efeitos resultantes aplicáveis.
- **FR-AP-UX-003**: Erros de soma, classificação, fechamento, autorização, moeda, excesso ou repetição DEVEM indicar como regularizar sem revelar dados protegidos.
- **FR-AP-UX-004**: Estados, atraso, prioridade e risco NÃO DEVEM depender exclusivamente de cor.
- **FR-AP-UX-005**: Jornadas principais e ações em lote DEVEM ser operáveis por teclado e tecnologias assistivas.
- **FR-AP-UX-006**: Antes de revisar obrigação já liquidada, a interface DEVE apresentar versões vigentes, efeitos financeiros que serão alterados, datas afetadas, dependências e eventual impedimento por fechamento.

### Decisões de Infraestrutura Auditáveis

> Esta feature não agenda nem gera recorrências, não executa pagamentos externos, não mantém tokens externos e não realiza rotação criptográfica própria. Essas capacidades dependem de especificações próprias.

- **FR-AP-INFRA-IDEMP**: Confirmação de obrigação e liquidação DEVE possuir identidade idempotente estável no tenant e na origem.
- **FR-AP-INFRA-LOCK**: Operações concorrentes DEVEM preservar saldos pendentes, estados, alocações, versões e unicidade em todas as instâncias da aplicação.
- **FR-AP-INFRA-ATOMIC**: Cada transição DEVE confirmar ou rejeitar conjuntamente estado, parcelas, componentes, alocações, lançamentos aplicáveis e auditoria de sucesso.
- **FR-AP-INFRA-BACKUP**: Obrigações, parcelas, liquidações, alocações, snapshots, origens e auditorias DEVEM participar dos backups e restaurações do tenant segundo `tenant-data-governance`.

### Key Entities

- **Obrigação a Pagar**: compromisso de identidade estável que reúne contraparte, moeda, valor, datas, origem, estado e parcelas.
- **Pessoa Credora**: contraparte obrigatória da obrigação confirmada, cadastrada no tenant sem necessidade de papel comercial e preservada por referência e apresentação histórica mínima.
- **Parcela a Pagar**: parte exigível da obrigação, com sequência, vencimento, componentes, classificação, estado e saldo pendente.
- **Componente Monetário**: parcela explicativa de principal, desconto, juros, multa, imposto, tarifa, honorário ou outro ajuste.
- **Liquidação**: reconhecimento de pagamento efetivo, com conta, data, moeda, valor debitado, componentes e lançamento financeiro correspondente.
- **Alocação de Liquidação**: parte da liquidação atribuída ao saldo de uma parcela específica.
- **Instrução Preservada de Pagamento**: snapshot da versão do dado indicado para executar o pagamento fora do sistema.
- **Origem da Obrigação**: usuário ou evento modular responsável pela criação e pelo contrato de correção.

## Success Criteria

### Measurable Outcomes

- **SC-AP-001**: Em 100% dos testes cruzados, obrigações, parcelas, valores e liquidações de um tenant não são consultados nem afetados por outro.
- **SC-AP-002**: Em 100% das obrigações válidas, a soma das parcelas corresponde exatamente ao total na precisão da moeda.
- **SC-AP-003**: Em 100% dos testes, obrigações, vencimentos e programações sem liquidação não alteram saldo de conta financeira.
- **SC-AP-004**: Em 100% das liquidações, valor debitado, componentes, alocações e redução dos saldos pendentes são reconciliáveis sem dupla contagem.
- **SC-AP-005**: Em 100% das revisões de obrigações parcial ou totalmente liquidadas com datas abertas, obrigação, parcelas, liquidações, alocações, lançamentos e saldos permanecem reconciliados e versionados; com alguma data bloqueada, nenhum fato protegido é reescrito.
- **SC-AP-006**: Em 100% das repetições equivalentes e disputas concorrentes testadas, nenhuma obrigação, liquidação ou alocação é duplicada ou perdida.
- **SC-AP-007**: Em 100% das consultas por data, a situação vencida ou a vencer é reproduzida sem depender de atualização periódica do registro.
- **SC-AP-008**: Usuários autorizados registram obrigação simples em até 90 segundos e liquidam parcela simples em até 60 segundos nos testes de usabilidade.
- **SC-AP-009**: Pelo menos 95% das consultas comuns de agenda e obrigação retornam resultado utilizável em até 2 segundos nas condições operacionais da primeira versão.
- **SC-AP-010**: Todas as jornadas principais podem ser concluídas somente por teclado e sem bloqueios críticos para tecnologias assistivas.
- **SC-AP-011**: Em 100% das origens modulares testadas, repetição equivalente reutiliza a obrigação e correção protegida não pode ser feita por atalho financeiro.
- **SC-AP-012**: Logs, erros e métricas examinados não contêm valores, saldos, documentos pessoais ou instruções financeiras sem finalidade e proteção aprovadas.
- **SC-AP-013**: Em 100% das obrigações confirmadas testadas, existe exatamente uma pessoa credora do mesmo tenant; nenhum cadastro rápido cria papel, acesso ou duplicidade silenciosa.

## Fora do Escopo

- Execução bancária, iniciação de pagamento, Pix, TED, boletos, remessa, retorno e confirmação fornecida por instituição financeira.
- Cadastro-base de pessoas e de suas instruções de pagamento.
- Criação e gestão de pedidos de compra, contratos, documentos fiscais, tributos ou folha de pagamento.
- Escrituração contábil, retenções fiscais e declarações legais.
- Conciliação bancária e importação de extratos.
- Gestão documental completa; anexos e comprovantes dependerão de capacidade própria.
- Políticas, estados, fluxos e alçadas de aprovação de obrigações ou pagamentos, adiados para um MVP futuro.
- Recorrências de obrigações, direitos, pagamentos ou lançamentos; frequências, séries, exceções e alterações futuras pertencem a `financial-recurrences`.
- Orçamento e fluxo de caixa avançado além das consultas de compromissos desta feature.
- Contas a receber, adiantamentos concedidos, reembolsos a recuperar e créditos contra fornecedores.
- Aplicativos móveis ou desktop nativos.
