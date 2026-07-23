# Feature Specification: Contas a Receber

**Feature**: `accounts-receivable`
**Created**: 2026-07-22
**Status**: Clarified — pronta para planejamento

## Escopo

Esta feature permite ao tenant registrar, organizar, cobrar e liquidar direitos de recebimento contra uma contraparte. Um direito a receber representa um valor esperado e pode possuir uma ou várias parcelas, vencimentos próprios, classificações financeiras, dimensões e origem manual ou modular.

Contas a receber e lançamentos financeiros possuem responsabilidades distintas. O direito e suas parcelas explicam **o que deve ser recebido e quando**. Um crédito ainda não identificado pode produzir efeito na conta como pré-lançamento financeiro, mas somente sua conclusão como recebimento confirmado pode liquidar direitos ou parcelas. Previsões, vencimentos, atrasos e ações de cobrança não alteram saldo por si próprios.

Recebimentos podem ser totais ou parciais e devem preservar principal, descontos concedidos, juros, multas, tarifas, retenções e outros componentes de maneira explicável. O vínculo entre direito, parcela, recebimento, alocação e lançamento financeiro permite consultar a agenda futura e o histórico efetivamente recebido sem duplicar efeitos.

> [!IMPORTANT]
> Estado do direito, estado da parcela, situação de vencimento e situação de cobrança são conceitos distintos. Um direito pode estar parcialmente liquidado, conter parcela vencida em cobrança e outras parcelas ainda a vencer.

> [!NOTE]
> Esta feature controla o direito e as ações internas de cobrança. Ela não emite documento fiscal, não gera ou registra boleto/Pix em instituição externa, não consulta banco, não envia mensagens ao pagador e não substitui conciliação bancária.

## Clarifications

### Session 2026-07-22

- Q: Como registrar crédito real ainda sem pagador ou direitos identificados? → A: O crédito deve ser reconhecido como pré-lançamento de `financial-transactions`. Ele exige somente conta, direção de entrada, data, valor e descrição, já compõe o saldo e permanece pendente. Não identifica receita, não reduz direito e não presume pagador. Depois de completado e alocado, é confirmado preservando identidade e efeito monetário, sem duplicar a movimentação.
- Q: Aprovações e recorrências pertencem a esta primeira versão? → A: Aprovações permanecem adiadas para um MVP futuro. Recorrências não integram esta feature e serão definidas exclusivamente no SDD transversal `financial-recurrences`, sem antecipar aqui suas regras.
- Q: Como tratar valor recebido superior às parcelas inicialmente selecionadas? → A: O sistema não deve presumir a natureza do excedente. O usuário distribui explicitamente todo o recebimento entre parcelas do mesmo devedor, outros direitos compatíveis, componentes categorizados como juros ou multa, crédito escolhido para uso posterior ou outra destinação válida. Um único depósito permanece um único lançamento financeiro; as destinações formam um rateio reconciliado e não movimentam o saldo novamente.

### Session 2026-07-23

- Q: Qual é a abrangência da cobrança nesta primeira versão? → A: A feature deve somente registrar ações manuais, resultados, promessas, observações e próximos acompanhamentos. Avisos e notificações, comunicações externas e emissão de boleto, Pix de cobrança, link de pagamento por cartão ou outro título ou meio de cobrança ficam adiados para um MVP futuro e não terão seus contratos antecipados neste SDD.

## Interface Coverage

| Superfície humana conhecida | Tipo | Atores | Cobertura | Comportamento funcional | Exclusões ou adiamentos |
|-----------------------------|------|--------|-----------|-------------------------|-------------------------|
| Web responsiva | Web | Usuários autorizados do tenant | FULL | Cadastrar, revisar, consultar, registrar acompanhamento manual e liquidar direitos e parcelas, inclusive em lote quando seguro | Avisos, notificações, comunicação externa, emissão de meios de cobrança e documentos de outros módulos ficam adiados para MVP futuro |
| Aplicativo móvel nativo | Mobile | Usuários do tenant | N/A | Não existe superfície móvel nativa definida | Toda capacidade nativa permanece fora do produto atual |
| Aplicação desktop nativa | Desktop | Usuários do tenant | N/A | Não existe superfície desktop nativa definida | Toda capacidade desktop nativa permanece fora do produto atual |
| CLI/TUI | CLI/TUI | Operadores | N/A | Não há jornada humana de contas a receber por terminal | Administração operacional por terminal não integra a feature |

## User Scenarios & Testing

### User Story 1 - Registrar direito a receber (Priority: P1)

Um usuário autorizado registra um direito com contraparte devedora, descrição, moeda, valor, datas e classificação suficientes para representar a expectativa sem afetar o saldo financeiro.

**Why this priority**: O direito é a unidade central para organizar valores futuros e impedir que previsão seja confundida com dinheiro recebido.

**Independent Test**: Cadastrar direito simples com parcela única, consultá-lo entre os valores em aberto e comprovar que nenhuma conta financeira foi movimentada.

**Acceptance Scenarios**:

1. **Given** tenant e contraparte válidos, **When** usuário confirma direito completo, **Then** ele passa a compor a agenda financeira sem alterar saldos.
2. **Given** classificação ou dimensão obrigatória ausente, **When** usuário tenta confirmar o direito, **Then** o sistema preserva o rascunho e informa o que deve ser corrigido.
3. **Given** referência de outro tenant, **When** cadastro é solicitado, **Then** a operação é negada sem revelar o dado externo.
4. **Given** rascunho sem devedor, **When** usuário tenta confirmá-lo, **Then** o sistema exige selecionar ou cadastrar uma pessoa do tenant antes de concluir.

---

### User Story 2 - Parcelar e programar vencimentos (Priority: P1)

Um usuário autorizado divide o direito em parcelas com valores e vencimentos próprios, podendo aceitar uma sugestão ou ajustar cada parcela antes da confirmação.

**Why this priority**: Parcelas determinam a agenda real de recebimentos e precisam reconciliar exatamente com o valor do direito.

**Independent Test**: Criar direito em várias parcelas, ajustar valores e datas e comprovar sequência completa e soma exata.

**Acceptance Scenarios**:

1. **Given** valor e quantidade de parcelas, **When** usuário solicita distribuição, **Then** o sistema sugere valores e vencimentos cuja soma corresponde ao total.
2. **Given** distribuição manual com diferença, **When** confirmação é solicitada, **Then** o sistema rejeita a inconsistência sem ajustar valores silenciosamente.
3. **Given** parcelas confirmadas, **When** o direito é consultado, **Then** cada vencimento e o saldo pendente são apresentados individualmente e no total.

---

### User Story 3 - Registrar recebimento total ou parcial (Priority: P1)

Um usuário autorizado registra valor efetivamente recebido em uma conta financeira, informa seus componentes e o aloca às parcelas abrangidas.

**Why this priority**: O recebimento conecta o direito planejado ao fato monetário efetivo e precisa explicar diferenças sem duplicar ou ocultar saldo.

**Independent Test**: Liquidar parcialmente uma parcela, verificar lançamento financeiro, saldo residual e posterior quitação sem duplicidade.

**Acceptance Scenarios**:

1. **Given** parcela aberta e apta, **When** recebimento é confirmado, **Then** lançamento de entrada e alocações são criados atomicamente e o saldo pendente é recalculado.
2. **Given** recebimento com desconto ou acréscimo, **When** confirmação é solicitada, **Then** cada componente é explicitado e o valor creditado reconcilia com a liquidação.
3. **Given** repetição equivalente da confirmação, **When** solicitação é processada novamente, **Then** nenhum recebimento ou lançamento adicional é produzido.
4. **Given** várias parcelas compatíveis do mesmo devedor, **When** usuário confirma um único recebimento distribuído entre elas, **Then** cada alocação reduz somente seu saldo e a soma reconcilia com o recebimento.
5. **Given** crédito real sem pagador ou direito identificado, **When** usuário o reconhece com os dados mínimos, **Then** ele se torna pré-lançamento, afeta o saldo e não liquida qualquer parcela.
6. **Given** pré-lançamento de entrada posteriormente identificado, **When** usuário completa e confirma o recebimento e suas alocações, **Then** o sistema preserva identidade e efeito monetário sem duplicar saldo.
7. **Given** depósito superior às parcelas inicialmente selecionadas, **When** usuário distribui o restante entre outra parcela, juros categorizados e crédito explícito da contraparte, **Then** todas as destinações reconciliam com o depósito e somente uma movimentação compõe o saldo.

---

### User Story 4 - Controlar vencimentos e cobrança (Priority: P1)

Um usuário autorizado consulta parcelas vencidas, a vencer e liquidadas, prioriza o acompanhamento e registra interações de cobrança sem confundi-las com recebimentos.

**Why this priority**: Contas a receber deve tornar os créditos acionáveis, revelar atraso e preservar o contexto das tratativas realizadas.

**Independent Test**: Consultar agenda em diferentes datas, registrar ação de cobrança e comprovar que nem o estado financeiro nem o saldo foram alterados.

**Acceptance Scenarios**:

1. **Given** parcela aberta com vencimento anterior à data de referência, **When** agenda é consultada, **Then** ela aparece como vencida com dias de atraso e saldo pendente.
2. **Given** recebimento parcial, **When** totais por período são consultados, **Then** somente o saldo residual compõe o valor ainda a receber.
3. **Given** ação de cobrança registrada, **When** histórico é consultado, **Then** usuário visualiza data, ator, tipo, resultado, observações e próximo acompanhamento, sem efeito financeiro.
4. **Given** parcelas em moedas diferentes, **When** visão consolidada é solicitada sem conversão definida, **Then** os totais permanecem separados por moeda.

---

### User Story 5 - Corrigir, cancelar e compensar (Priority: P2)

Um usuário autorizado corrige ou cancela direitos e recebimentos enquanto todas as datas financeiras afetadas estiverem abertas e usa efeitos compensatórios quando algum fato já estiver protegido pelo fechamento.

**Why this priority**: Erros, devoluções e cancelamentos são inevitáveis, mas não podem apagar fatos já protegidos pelo fechamento financeiro.

**Independent Test**: Corrigir direito já liquidado enquanto a data estiver aberta e comprovar revisão coordenada; depois fechar a data e comprovar que a mesma correção exige compensação posterior.

**Acceptance Scenarios**:

1. **Given** direito parcial ou totalmente liquidado e todas as datas afetadas abertas, **When** usuário autorizado corrige direito, parcelas ou recebimento, **Then** todos os efeitos dependentes são revisados atomicamente e as versões anteriores permanecem auditáveis.
2. **Given** direito liquidado com recebimento em data aberta, **When** usuário corrige valor, moeda, devedor ou alocações, **Then** o estado liquidado não bloqueia a alteração e nenhuma dependência fica inconsistente.
3. **Given** ao menos um lançamento de recebimento em data bloqueada, **When** correção é solicitada, **Then** o fato protegido permanece imutável e a correção exige compensação em data aberta.

---

### User Story 6 - Receber direitos originados por outros módulos (Priority: P2)

Um módulo autorizado cria ou atualiza direito originado por processo próprio, preservando identidade, contexto e responsabilidade sobre correções.

**Why this priority**: Vendas, contratos, fiscal e outros módulos poderão originar créditos sem permitir duplicação ou edição financeira que contradiga seu processo.

**Independent Test**: Receber duas vezes o mesmo evento modular e comprovar um único direito; tentar conteúdo divergente e obter conflito rastreável.

**Acceptance Scenarios**:

1. **Given** evento modular novo e válido, **When** ele é aceito, **Then** direito e parcelas preservam origem estável e nenhum recebimento é presumido.
2. **Given** repetição equivalente, **When** origem envia novamente o evento, **Then** resultado existente é reutilizado sem duplicação.
3. **Given** direito controlado por origem, **When** usuário tenta alterar campo protegido diretamente, **Then** operação é recusada e orientada ao fluxo responsável.

### Edge Cases

- Direito sem contraparte apta ou com contraparte posteriormente desativada ou excluída.
- Contraparte paga por meio de terceiro ou com identificação incompleta.
- Valor total é menor que a quantidade de parcelas na menor unidade monetária.
- Vencimento cai em dia inexistente, fim de semana ou feriado.
- Recebimento ocorre antes ou depois do vencimento, parcialmente ou em várias contas.
- Desconto supera o saldo ou acréscimos tornam o total inesperado.
- Recebimento ocorre em moeda diferente da moeda do direito.
- Valor recebido supera os saldos selecionados e o usuário divide o excedente entre destinações de naturezas diferentes.
- Recebimento é repetido, concorrente, devolvido ou compensado parcialmente.
- Parcela liquidada participa de conciliação ou de processo externo posterior.
- Direito ou parcela é cancelado com dependências ativas.
- Ação de cobrança é agendada para data passada ou para direito já liquidado.
- Módulo de origem é desativado ou deixa de fornecer contexto apresentável.
- Fechamento financeiro avança entre a preparação e a confirmação do recebimento.

## Requirements

### Conceitos e Fronteiras

- **FR-AR-BOUND-001**: Todo direito, parcela, recebimento, alocação e registro de cobrança DEVE pertencer exclusivamente a um tenant.
- **FR-AR-BOUND-002**: Direito DEVE representar expectativa de recebimento e NÃO DEVE alterar saldo de conta financeira antes de recebimento confirmado.
- **FR-AR-BOUND-003**: Parcela DEVE representar parte exigível do direito, com valor, vencimento, estado e saldo pendente próprios.
- **FR-AR-BOUND-004**: Recebimento DEVE representar entrada efetivamente reconhecida e produzir seu efeito por `financial-transactions`; pré-lançamento PODERÁ reconhecer o efeito antes da identificação completa, mas somente recebimento confirmado PODERÁ liquidar direito.
- **FR-AR-BOUND-005**: Conta a receber NÃO DEVE ser confundida com transferência entre contas próprias, venda, pedido, contrato, documento fiscal ou lançamento contábil.
- **FR-AR-BOUND-006**: A feature NÃO DEVE afirmar que instituição externa processou pagamento nem usar previsão ou ação de cobrança como comprovação de recebimento.
- **FR-AR-BOUND-007**: Situação vencida, a vencer ou em atraso DEVE ser derivada da data de referência, vencimento, saldo pendente e estado, sem transição periódica obrigatória.

### Direito e Parcelas

- **FR-AR-RCV-001**: Direito DEVE possuir identidade estável, descrição, moeda, data de emissão ou reconhecimento, contraparte devedora, valor total, estado, origem e instante de criação.
- **FR-AR-RCV-002**: Direito PODERÁ preservar referência externa, competência, observações, prioridade, responsável por cobrança e documentos associados quando fornecidos por capacidade própria.
- **FR-AR-RCV-003**: Todo direito confirmado DEVE possuir ao menos uma parcela e a soma das parcelas DEVE corresponder exatamente ao valor total na precisão da moeda.
- **FR-AR-RCV-004**: Cada parcela DEVE possuir sequência, quantidade total, vencimento, valor original, componentes vigentes, saldo pendente e estado.
- **FR-AR-RCV-005**: Sistema DEVE sugerir divisão uniforme e vencimentos periódicos, permitir ajustes e resolver diferenças de arredondamento de forma explícita e determinística.
- **FR-AR-RCV-006**: Categoria, dimensões e contraparte comuns PODERÃO ser herdadas como proposta, mas cada parcela DEVE preservar a classificação efetivamente confirmada para ela.
- **FR-AR-RCV-007**: Classificações DEVEM obedecer `financial-categories` e `financial-dimensions`, inclusive rateios, obrigatoriedades e proibições.
- **FR-AR-RCV-008**: Contraparte DEVE referenciar pessoa do mesmo tenant e preservar apresentação histórica mínima suficiente para que alterações cadastrais não tornem o direito incompreensível.
- **FR-AR-RCV-009**: Valor original, desconto, juros, multa, tarifa, imposto, retenção, honorário e outros componentes DEVEM permanecer distinguíveis e reconciliar com o valor vigente aplicável.
- **FR-AR-RCV-010**: Rascunho PODERÁ ser salvo sem devedor, mas todo direito confirmado DEVE referenciar exatamente uma pessoa devedora apta do mesmo tenant.
- **FR-AR-RCV-011**: Pessoa devedora NÃO DEVE precisar possuir papel de cliente ou outro papel de negócio, e sua associação ao direito NÃO DEVE conceder acesso, participação ou permissão.
- **FR-AR-RCV-012**: Cadastro rápido de devedor DEVE aplicar integralmente `party-registration`, inclusive identidade, isolamento, validação, pesquisa prévia de possíveis duplicidades e autorizações próprias.
- **FR-AR-RCV-013**: Cadastro rápido NÃO DEVE criar automaticamente papel comercial, meio de pagamento nem relacionamento adicional além da pessoa selecionável como devedora.

### Estados

- **FR-AR-STATE-001**: Direito DEVE distinguir ao menos rascunho, aberto, parcialmente liquidado, liquidado e cancelado.
- **FR-AR-STATE-002**: Estado agregado do direito DEVE ser derivado de seu fluxo e das parcelas, sem ocultar estados individuais divergentes.
- **FR-AR-STATE-003**: Liquidação somente DEVE ser alocada quando o direito e as parcelas abrangidas estiverem abertos ou parcialmente liquidados e aptos ao recebimento.
- **FR-AR-STATE-004**: Estados parcialmente liquidado ou liquidado DEVEM refletir efeitos vigentes e NÃO DEVEM constituir fronteira de imutabilidade enquanto as datas financeiras afetadas estiverem abertas.
- **FR-AR-STATE-005**: Situação de cobrança NÃO DEVE substituir estado financeiro nem reduzir saldo pendente.

### Recebimento e Alocação

- **FR-AR-PAY-001**: Recebimento DEVE identificar data efetiva, conta recebedora, moeda, valor creditado, componentes, ator ou origem e identidade idempotente.
- **FR-AR-PAY-002**: Confirmação DEVE validar atomicamente tenant, direitos, parcelas, saldos pendentes, conta, moeda, fechamento, classificação, autorização e repetição.
- **FR-AR-PAY-003**: Recebimento e respectivo lançamento financeiro DEVEM ser confirmados ou rejeitados como uma única operação de negócio.
- **FR-AR-PAY-004**: Recebimento parcial DEVE reduzir somente o valor alocado e manter o restante aberto sem reescrever o valor original da parcela.
- **FR-AR-PAY-005**: Um recebimento DEVE poder ser alocado a uma ou várias parcelas compatíveis, e cada parcela DEVE poder receber um ou vários recebimentos até seu saldo pendente ser integralmente quitado.
- **FR-AR-PAY-006**: Principal, desconto, juros, multa, imposto, retenção, tarifa, honorário e diferença cambial DEVEM ser explicitados de modo que o crédito na conta e a redução do direito sejam reconciliáveis.
- **FR-AR-PAY-007**: Componentes econômicos adicionais DEVEM possuir categorias e dimensões compatíveis quando produzirem efeitos financeiros próprios.
- **FR-AR-PAY-008**: Uma alocação DEVE identificar recebimento, parcela, valor aplicado e componentes explicativos, e a soma de todas as destinações DEVE reconciliar exatamente com o valor recebido antes da confirmação.
- **FR-AR-PAY-009**: Um único recebimento somente PODERÁ abranger direitos do mesmo tenant, mesma moeda e mesma contraparte devedora; valores de devedores distintos DEVEM constituir recebimentos separados.
- **FR-AR-PAY-010**: Confirmação em lote PODERÁ agrupar operações para conveniência, mas DEVE preservar recebimentos, validações, efeitos e resultados individualizáveis.
- **FR-AR-PAY-011**: A feature DEVE impedir duplicação por repetição, concorrência ou reprocessamento e rejeitar conflito de conteúdo sob a mesma identidade idempotente.
- **FR-AR-PAY-012**: Crédito sem pagador ou direito identificado DEVE ser registrado como pré-lançamento de entrada conforme `financial-transactions`, exigindo somente conta, data, valor e descrição além da direção predeterminada.
- **FR-AR-PAY-013**: Valor excedente às parcelas inicialmente selecionadas NÃO DEVE receber destinação presumida, ser descartado nem ser reconhecido automaticamente como receita, juros, multa, desconto, crédito ou arredondamento.
- **FR-AR-PAY-014**: Pré-lançamento de entrada DEVE compor saldo, permanecer explicitamente pendente e NÃO DEVE reduzir saldo de direito, criar alocação, presumir devedor nem reconhecer categoria de receita.
- **FR-AR-PAY-015**: Conclusão do pré-lançamento como recebimento confirmado DEVE preservar sua identidade e efeito monetário, validar devedor, componentes, classificações e alocações e NÃO DEVE produzir segunda movimentação.
- **FR-AR-PAY-016**: Pré-lançamento não concluído dentro do limite proposto DEVE bloquear o avanço da data de fechamento conforme `financial-closing-control`.
- **FR-AR-PAY-017**: Usuário autorizado DEVE poder distribuir um único recebimento entre várias parcelas do mesmo devedor, inclusive de direitos distintos, e entre componentes econômicos ou outras destinações válidas.
- **FR-AR-PAY-018**: Juros, multa, tarifa, antecipação, crédito da contraparte, valor a devolver ou outra natureza escolhida DEVEM permanecer explicitamente distinguíveis e possuir classificação compatível quando produzirem efeito econômico próprio.
- **FR-AR-PAY-019**: Um depósito efetivo DEVE permanecer representado por um único lançamento financeiro no valor integral; alocações e componentes explicativos NÃO DEVEM criar novas movimentações na conta.
- **FR-AR-PAY-020**: Confirmação DEVE exigir que cada unidade monetária do recebimento possua destinação explícita e que a soma de parcelas, componentes e créditos corresponda exatamente ao valor do lançamento financeiro.
- **FR-AR-PAY-021**: Valor conscientemente mantido como crédito da contraparte DEVE permanecer disponível e rastreável para alocação ou devolução posterior, sem alterar silenciosamente sua natureza nem apagar a destinação original.

### Cobrança

- **FR-AR-COL-001**: Sistema DEVE apresentar agenda consultável por vencimento, atraso, devedor, categoria, responsável, moeda, estado e saldo pendente.
- **FR-AR-COL-002**: Usuário autorizado DEVE poder registrar ação de cobrança com data, tipo, ator, resultado, observação e próxima ação opcional.
- **FR-AR-COL-003**: Registro de cobrança NÃO DEVE alterar saldo, liquidar parcela nem provar comunicação externa por si próprio.
- **FR-AR-COL-004**: Nesta versão, cobrança DEVE limitar-se ao registro manual de ações, resultados, promessas, observações e próximos acompanhamentos, sem executar comunicação ou emissão externa.
- **FR-AR-COL-005**: Histórico DEVE permanecer cronológico, auditável e vinculado ao direito, às parcelas abrangidas e à contraparte quando aplicável.
- **FR-AR-COL-006**: Prioridades, próximos acompanhamentos vencidos e dias de atraso DEVEM ser apresentados nas consultas sem reescrever fatos financeiros nem disparar notificação automática.
- **FR-AR-COL-007**: Avisos, notificações, envio de mensagens e emissão ou registro de boleto, Pix de cobrança, link de pagamento, cartão ou outro título ou meio de cobrança NÃO DEVEM integrar esta versão.

### Origem Modular

- **FR-AR-ORIG-001**: Direito originado por outro módulo DEVE preservar tipo, identidade, versão e referência apresentável da origem.
- **FR-AR-ORIG-002**: Repetição equivalente da mesma origem DEVE ser idempotente; conteúdo divergente sob a mesma identidade DEVE produzir conflito rastreável.
- **FR-AR-ORIG-003**: Módulo de origem DEVE declarar quais campos controla e coordenar revisão ou cancelamento desses campos com contas a receber.
- **FR-AR-ORIG-004**: Interface financeira DEVE impedir alteração isolada de campo protegido e orientar o usuário ao fluxo responsável.
- **FR-AR-ORIG-005**: Indisponibilidade do módulo de origem NÃO DEVE apagar o direito nem impedir consulta de seu snapshot financeiro preservado.

### Correção, Cancelamento e Compensação

- **FR-AR-REV-001**: Direito, parcelas, recebimentos, alocações e lançamentos correspondentes PODERÃO ser corrigidos ou cancelados quando todas as datas financeiras afetadas estiverem abertas.
- **FR-AR-REV-002**: Correção em data aberta DEVE preservar identidade, versões, autoria e justificativa e recalcular efeitos dependentes de forma atômica.
- **FR-AR-REV-003**: Estado parcial ou integralmente liquidado NÃO DEVE impedir correção autorizada nem constituir imutabilidade adicional.
- **FR-AR-REV-004**: Quando qualquer fato financeiro afetado estiver protegido por `financial-closing-control`, ele DEVE permanecer imutável e a regularização DEVE ocorrer por efeito compensatório em data aberta.
- **FR-AR-REV-005**: Devolução de valor recebido DEVE gerar saída financeira rastreável e ajustar o saldo do direito somente conforme sua relação de compensação, sem apagar o recebimento original.
- **FR-AR-REV-006**: Cancelamento de direito sem recebimento PODERÁ encerrar seu saldo sem lançamento; cancelamento com efeitos financeiros DEVE preservar e tratar todas as dependências.

### Autorização, Auditoria e Proteção

- **FR-AR-SEC-001**: Criação, consulta, confirmação, cobrança, liquidação, correção, cancelamento e compensação DEVEM exigir chaves de acesso próprias e independentes.
- **FR-AR-SEC-002**: Autorização DEVE considerar tenant ativo e escopo do objeto em todas as entradas, consultas e referências.
- **FR-AR-SEC-003**: Logs, erros, métricas e mensagens NÃO DEVEM expor valores, saldos, dados pessoais ou existência de objetos de outro tenant sem finalidade e autorização.
- **FR-AR-AUD-001**: Alterações relevantes DEVEM registrar ator ou origem, instante, tenant, ação, justificativa quando exigida e valores anteriores e posteriores suficientes para reconstrução.
- **FR-AR-AUD-002**: Recebimentos, alocações, correções, cancelamentos, devoluções e ações de cobrança DEVEM possuir trilha auditável sem exclusão destrutiva do histórico.
- **FR-AR-AUD-003**: A feature NÃO DEVE criar fluxo de aprovação implícito; usuários com as chaves necessárias atuam diretamente, sujeitos a autorização e auditoria.

### Experiência e Operação

- **FR-AR-UX-001**: Jornada manual simples DEVE permitir informar primeiro os dados essenciais e revelar parcelamento, componentes, rateios e origem conforme necessários.
- **FR-AR-UX-002**: Listagens DEVEM apresentar saldo pendente, vencimento, atraso, devedor, moeda, estado e situação de cobrança sem depender de código sequencial exposto.
- **FR-AR-UX-003**: Confirmações DEVEM apresentar os efeitos financeiros e alocações antes da conclusão e retornar erros acionáveis sem perder dados válidos.
- **FR-AR-UX-004**: Operações em lote DEVEM permitir revisar itens, impedir mistura incompatível e informar sucesso ou falha individualmente.
- **FR-AR-UX-005**: Jornadas principais DEVEM ser operáveis por teclado e expor nomes, estados, erros e relações compreensíveis a tecnologias assistivas.
- **FR-AR-OPS-001**: Consultas comuns DEVEM suportar paginação e filtros sem carregar todo o conjunto do tenant.
- **FR-AR-OPS-002**: Falha durante operação coordenada NÃO DEVE deixar lançamento financeiro, alocação ou saldo pendente parcialmente atualizado.

## Key Entities

- **Direito a Receber**: expectativa financeira contra uma pessoa devedora, com valor, moeda, origem, classificação e estado agregado.
- **Parcela a Receber**: parte exigível do direito, com sequência, vencimento, componentes, estado e saldo pendente próprios.
- **Recebimento**: reconhecimento de entrada efetiva em conta financeira, com valor, data, moeda, componentes e origem.
- **Alocação de Recebimento**: distribuição explícita do valor recebido entre uma ou várias parcelas compatíveis.
- **Destinação do Recebimento**: parcela do valor recebido atribuída pelo usuário a direito, componente econômico, crédito da contraparte ou outra finalidade válida, sem criar nova movimentação bancária.
- **Ação de Cobrança**: registro não financeiro de contato, tentativa, promessa, observação ou próximo acompanhamento.
- **Origem do Direito**: identidade e contexto do processo manual ou modular que criou e controla o direito.
- **Crédito Pendente de Identificação**: pré-lançamento de entrada que já compõe o saldo, mas ainda não possui informações suficientes para confirmar recebimento ou liquidar direitos.
- **Compensação de Recebimento**: efeito financeiro posterior que corrige ou devolve valor sem apagar fato protegido.

## Success Criteria

- **SC-AR-001**: Em 100% dos testes, criar ou alterar direito sem recebimento confirmado não modifica saldo de conta financeira.
- **SC-AR-002**: Em 100% dos direitos confirmados, parcelas reconciliam exatamente com o total na precisão da moeda.
- **SC-AR-003**: Em 100% dos recebimentos confirmados, lançamento financeiro, componentes e alocações reconciliam ou toda a operação é rejeitada sem efeito parcial.
- **SC-AR-004**: Em 100% dos recebimentos parciais, somente o valor alocado reduz o saldo das parcelas abrangidas.
- **SC-AR-005**: Em 100% dos testes de isolamento, usuário ou referência de outro tenant não lê, altera nem infere dados protegidos.
- **SC-AR-006**: Em 100% das repetições equivalentes, no máximo um recebimento é produzido; conteúdo divergente sob a mesma identidade é rejeitado.
- **SC-AR-007**: Em 100% das consultas, atraso e saldo pendente refletem vencimentos e efeitos vigentes sem depender de transição diária persistida.
- **SC-AR-008**: Em 100% das ações de cobrança, nenhum saldo ou estado de liquidação é alterado sem recebimento confirmado.
- **SC-AR-009**: Em 100% das correções em datas abertas, efeitos dependentes são atualizados atomicamente com histórico preservado.
- **SC-AR-010**: Em 100% dos testes com data fechada, o fato protegido permanece imutável e somente compensação rastreável em data aberta é aceita.
- **SC-AR-011**: Em 100% dos eventos modulares repetidos, não há direito duplicado e divergências permanecem rastreáveis.
- **SC-AR-012**: Usuários autorizados concluem um direito manual simples, com parcela única, em até 90 segundos nos testes de usabilidade.
- **SC-AR-013**: Pelo menos 95% das consultas comuns de agenda e recebimentos retornam resultado utilizável em até 2 segundos nas condições operacionais da primeira versão.
- **SC-AR-014**: Todas as jornadas principais podem ser concluídas somente por teclado e sem bloqueios críticos para tecnologias assistivas.
- **SC-AR-015**: Em 100% dos créditos sem identificação, o pré-lançamento altera o saldo uma única vez, permanece pendente e não reduz qualquer direito.
- **SC-AR-016**: Em 100% das conclusões de crédito pendente, a confirmação preserva identidade e efeito monetário e cria somente as alocações validadas, sem duplicar saldo.
- **SC-AR-017**: Em 100% dos recebimentos superiores aos saldos inicialmente selecionados, a confirmação exige rateio integral escolhido pelo usuário, reconcilia todas as destinações e mantém uma única movimentação financeira.
- **SC-AR-018**: Em 100% das ações de cobrança desta versão, somente o histórico interno é atualizado, sem enviar notificação ou mensagem nem gerar título ou meio externo de cobrança.

## Fora do Escopo

- Emissão, registro, baixa ou cancelamento de boleto, Pix de cobrança, link de pagamento por cartão ou outro título ou meio em instituição externa; essas capacidades ficam adiadas para MVP futuro.
- Avisos e notificações automáticos e envio de e-mail, mensagem ou carta de cobrança; essas capacidades ficam adiadas para MVP futuro.
- Importação de extrato, identificação automática de crédito e conciliação bancária.
- Vendas, pedidos, contratos, documentos fiscais e escrituração contábil.
- Recorrências; séries futuras pertencem a `financial-recurrences`.
- Fluxos, alçadas e estados de aprovação.
- Negociação formal de dívida, acordos, protesto, negativação, cobrança judicial e cessão de recebíveis.
- Anexos, comprovantes e gestão documental.
- Conversão consolidada de relatórios entre moedas.
