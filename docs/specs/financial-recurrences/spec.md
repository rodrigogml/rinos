# Feature Specification: Recorrências Financeiras

**Feature**: `financial-recurrences`
**Created**: 2026-07-23
**Status**: Draft — clarificada

## Escopo

Esta feature permite ao tenant definir séries de compromissos financeiros que se repetem ao longo do tempo sem cadastrar antecipadamente todas as contas a pagar, contas a receber ou movimentações futuras. A série recorrente é a fonte de verdade; suas ocorrências futuras são projeções calculadas para a agenda e o fluxo de caixa.

Uma recorrência descreve direção, finalidade, contraparte, calendário, valor esperado, classificação, dimensões e forma de realização. Ela pode representar tanto um compromisso que exige ação do usuário quanto uma movimentação executada externamente, como débito automático em conta.

No modo de ação manual, a ocorrência calculada aparece na agenda como preparação. O usuário decide quando convertê-la em obrigação ou direito real e segue os fluxos de `accounts-payable` ou `accounts-receivable`. No modo automático externo, o Rinos apenas espera a movimentação em uma conta definida e oferece critérios para que uma futura conciliação reconheça o evento; a recorrência não ordena o pagamento e não altera saldo por expectativa.

> [!IMPORTANT]
> Uma ocorrência projetada não é rascunho, pré-lançamento, obrigação, direito, pagamento, recebimento ou lançamento financeiro. Ela não altera saldo, não bloqueia fechamento e não prova que o evento ocorreu.

> [!NOTE]
> Recorrência e parcelamento são conceitos distintos. Parcelas dividem uma única obrigação ou direito já existente; recorrência descreve compromissos independentes que podem continuar indefinidamente e variar ao longo do tempo.

## Clarifications

### Session 2026-07-23

- Q: O sistema deve persistir somente eventos esparsos de ocorrências alteradas, ignoradas, materializadas ou resolvidas, sem gerar registros para todas as ocorrências futuras? -> A: Sim; as ocorrências normais permanecem calculadas, e o cadastro esparso registra apenas desvios ou fatos.
- Q: Quando um débito ou crédito automático for confirmado na conciliação, qual resultado financeiro deve ser produzido? -> A: Cada série define se a confirmação cria obrigação ou direito com sua liquidação, ou se cria somente lançamento financeiro classificado; sugestão e pontuação nunca substituem a decisão final do usuário.

## Interface Coverage

| Superfície humana conhecida | Tipo | Atores | Cobertura | Comportamento funcional | Exclusões ou adiamentos |
|-----------------------------|------|--------|-----------|-------------------------|-------------------------|
| Web responsiva | Web | Usuários autorizados do tenant | FULL | Criar séries, consultar projeções, materializar ocorrências, registrar exceções e acompanhar eventos automáticos esperados | Notificações externas, execução bancária e conciliação de extrato permanecem nas features próprias |
| Aplicativo móvel nativo | Mobile | Usuários do tenant | N/A | Não existe superfície móvel nativa definida | Toda capacidade nativa permanece fora do produto atual |
| Aplicação desktop nativa | Desktop | Usuários do tenant | N/A | Não existe superfície desktop nativa definida | Toda capacidade desktop nativa permanece fora do produto atual |
| CLI/TUI | CLI/TUI | Operadores | N/A | Não há jornada humana de recorrências por terminal | Administração operacional por terminal não integra a feature |

## User Scenarios & Testing

### User Story 1 - Definir uma série recorrente (Priority: P1)

Um usuário autorizado cadastra um compromisso recorrente com direção, contraparte, calendário, valor esperado, classificação e forma de realização.

**Why this priority**: A série é a única fonte capaz de produzir projeções consistentes sem criar documentos futuros em massa.

**Independent Test**: Criar despesa mensal por prazo indeterminado e consultar suas próximas datas e valores sem encontrar obrigações ou lançamentos financeiros correspondentes.

**Acceptance Scenarios**:

1. **Given** tenant e dados mínimos válidos, **When** usuário ativa a série, **Then** o sistema calcula suas ocorrências no intervalo consultado sem criar documentos financeiros futuros.
2. **Given** frequência, data inicial e regra mensal, **When** projeções são consultadas repetidamente, **Then** as mesmas ocorrências e identidades lógicas são reproduzidas.
3. **Given** categoria ou dimensão incompatível, **When** ativação é solicitada, **Then** a série permanece inativa e informa as correções necessárias.
4. **Given** referência de outro tenant, **When** cadastro é solicitado, **Then** a operação é negada sem revelar o dado externo.

---

### User Story 2 - Planejar pagamentos que exigem ação do usuário (Priority: P1)

Um usuário autorizado consulta na agenda compromissos recorrentes futuros que ainda não são contas a pagar e escolhe quando preparar uma ocorrência real.

**Why this priority**: Aluguel, mensalidades e outros compromissos precisam ser lembrados sem inflar o banco com obrigações que talvez mudem ou deixem de existir.

**Independent Test**: Consultar doze meses de uma despesa mensal, comprovar doze projeções calculadas e materializar somente uma delas como conta a pagar.

**Acceptance Scenarios**:

1. **Given** série manual ativa, **When** agenda é consultada, **Then** ocorrências futuras aparecem como projeções distinguíveis de obrigações reais.
2. **Given** ocorrência projetada próxima, **When** usuário solicita preparação, **Then** uma obrigação real é criada uma única vez com proposta baseada na versão aplicável da série.
3. **Given** ocorrência não materializada com data passada, **When** agenda é consultada, **Then** ela aparece como pendência recorrente vencida sem alterar saldo nem bloquear fechamento.
4. **Given** projeção já materializada, **When** usuário repete a ação, **Then** o documento existente é apresentado sem duplicação.

---

### User Story 3 - Acompanhar débito automático esperado (Priority: P1)

Um usuário autorizado configura uma recorrência executada externamente, informa a conta financeira esperada, a data de referência e os sinais que ajudam a reconhecer o débito posteriormente.

**Why this priority**: Contas de água, energia, seguros e assinaturas podem ser debitadas sem ação do usuário e com variações de valor ou data.

**Independent Test**: Cadastrar débito mensal estimado, consultar a expectativa na conta indicada e comprovar que nenhum lançamento é produzido antes de um fato real.

**Acceptance Scenarios**:

1. **Given** modo automático externo, **When** série é ativada, **Then** conta esperada, direção de saída e critérios de identificação tornam-se obrigatórios.
2. **Given** valor estimado e janela de data, **When** agenda é consultada, **Then** ocorrência apresenta expectativa e tolerâncias sem afirmar que houve pagamento.
3. **Given** data esperada ultrapassada além da tolerância sem evento relacionado, **When** acompanhamento é consultado, **Then** ocorrência aparece como não identificada e exige atenção.
4. **Given** mudança cadastral da conta esperada, **When** histórico é consultado, **Then** versões anteriores continuam explicando onde cada ocorrência era esperada.

---

### User Story 4 - Oferecer candidatos à conciliação futura (Priority: P1)

Quando uma linha externa de extrato existir, a futura conciliação pode comparar seus dados com ocorrências recorrentes esperadas e apresentar candidatos explicáveis ao usuário.

**Why this priority**: O maior benefício do débito automático é reduzir digitação sem permitir que semelhanças frágeis produzam classificações incorretas.

**Independent Test**: Comparar linha de saída com conta, data, valor, contraparte e descrição compatíveis e obter uma sugestão vinculada à ocorrência, sem conciliá-la dentro desta feature.

**Acceptance Scenarios**:

1. **Given** linha externa e ocorrência compatíveis, **When** candidatos são solicitados, **Then** a recorrência fornece critérios, tolerâncias e justificativas suficientes para a conciliação.
2. **Given** vários candidatos plausíveis, **When** comparação é realizada, **Then** nenhuma escolha é presumida e a ambiguidade é apresentada.
3. **Given** valor desconhecido, **When** conta, direção, data e identificadores textuais são compatíveis, **Then** a ocorrência pode ser sugerida com confiança reduzida e revisão obrigatória.
4. **Given** evento confirmado pela feature responsável, **When** agenda é consultada novamente, **Then** a ocorrência aparece resolvida sem alterar a série nem as demais projeções.
5. **Given** série configurada para criar obrigação ou direito e liquidá-lo, **When** usuário confirma a correspondência na conciliação, **Then** o módulo responsável produz os documentos e vínculos uma única vez conforme os dados efetivos.
6. **Given** série configurada para classificação direta, **When** usuário confirma a correspondência na conciliação, **Then** é produzido somente o lançamento financeiro classificado, sem criar obrigação ou direito artificial.

---

### User Story 5 - Alterar uma ocorrência ou a série futura (Priority: P1)

Um usuário autorizado corrige somente uma ocorrência, modifica a série a partir dela ou encerra recorrências futuras sem reescrever fatos anteriores.

**Why this priority**: Valores, datas, contas e classificações mudam; o usuário precisa controlar o alcance da alteração com segurança.

**Independent Test**: Alterar o valor de uma ocorrência, depois alterar a categoria daquela data em diante e comprovar projeções passadas, atuais e futuras coerentes.

**Acceptance Scenarios**:

1. **Given** ocorrência projetada, **When** usuário altera somente aquela ocorrência, **Then** uma exceção é aplicada sem mudar as demais.
2. **Given** série ativa, **When** usuário altera a partir de determinada ocorrência, **Then** nova versão passa a governar somente aquela ocorrência e as posteriores.
3. **Given** documentos reais já materializados, **When** série é alterada ou encerrada, **Then** esses documentos permanecem sob seus módulos de origem e não são reescritos.
4. **Given** ocorrência não aplicável, **When** usuário a ignora com motivo, **Then** ela deixa de aparecer como pendente sem gerar documento financeiro.
5. **Given** ocorrência futura sem alteração nem fato associado, **When** a agenda é consultada, **Then** ela é calculada sem criar cadastro persistente; somente um desvio ou fato posterior gera registro esparso para sua identidade lógica.

---

### User Story 6 - Planejar receitas recorrentes (Priority: P2)

Um usuário autorizado usa a mesma fundação para projetar direitos recorrentes, distinguindo expectativa de cobrança, recebimento manual e crédito automático externo.

**Why this priority**: Aluguéis recebidos, mensalidades e contratos recorrentes exigem simetria sem duplicar um segundo mecanismo de calendário.

**Independent Test**: Criar receita mensal, materializar uma ocorrência como direito a receber e manter as demais somente projetadas.

**Acceptance Scenarios**:

1. **Given** série de entrada manual, **When** usuário materializa ocorrência, **Then** um direito real é criado por `accounts-receivable` sem alterar saldo.
2. **Given** crédito automático externo esperado, **When** projeção é consultada, **Then** conta recebedora e critérios de identificação são apresentados sem criar recebimento.
3. **Given** ocorrência recebida por valor diferente, **When** feature responsável a resolve, **Then** valor real permanece no recebimento e o valor esperado continua auditável.

---

### User Story 7 - Auditar série, projeção e realização (Priority: P2)

Um usuário autorizado distingue o que foi previsto, alterado, materializado, ignorado e efetivamente realizado ao longo da vida da recorrência.

**Why this priority**: Sem separação entre projeção e fato, relatórios e usuários passam a tratar expectativas como dívidas ou pagamentos reais.

**Independent Test**: Reproduzir uma série com mudança de versão, exceção, ocorrência materializada e débito reconhecido, identificando cada origem e efeito.

**Acceptance Scenarios**:

1. **Given** projeção calculada, **When** usuário abre seus detalhes, **Then** visualiza série, versão, regra, data-base, ajustes e situação atual.
2. **Given** ocorrência materializada, **When** vínculo é consultado, **Then** o documento real e seu módulo responsável são apresentados conforme autorização.
3. **Given** alteração retroativa da série, **When** auditoria é consultada, **Then** conteúdo anterior, novo, alcance, ator, instante e motivo permanecem preservados.

### Edge Cases

- Série mensal ancorada nos dias 29, 30 ou 31 encontra mês sem esse dia.
- Série semanal seleciona vários dias da semana ou cruza mudança de ano.
- Data calculada cai em sábado, domingo ou feriado desconhecido pelo sistema.
- Fuso do tenant muda depois da criação da série.
- Série indefinida produz consulta de intervalo muito amplo.
- Valor é fixo, estimado, desconhecido ou muda de determinada ocorrência em diante.
- Valor real difere da expectativa por juros, multa, consumo, tarifa, imposto ou arredondamento.
- Débito ocorre antes ou depois da data esperada, inclusive fora da tolerância.
- Descrição do extrato muda ou contém identificador parcialmente variável.
- Uma linha externa é compatível com várias séries ou várias ocorrências da mesma série.
- Um débito bancário resume várias ocorrências ou uma ocorrência aparece dividida em várias linhas.
- Ocorrência é materializada concorrentemente por dois usuários.
- Série é pausada, encerrada ou alterada durante uma consulta da agenda.
- Ocorrência é ignorada e depois o débito real aparece.
- Documento materializado é cancelado ou alterado no módulo responsável.
- Data de uma projeção fica anterior ao fechamento financeiro.
- Conta, contraparte, categoria ou dimensão é desativada entre a criação e a ocorrência.
- Alteração “desta em diante” ocorre após exceções futuras já cadastradas.

## Requirements

### Conceitos e Fronteiras

- **FR-FREC-BOUND-001**: Toda série, versão, projeção, exceção e realização DEVE pertencer exclusivamente a um tenant.
- **FR-FREC-BOUND-002**: Série recorrente DEVE ser a fonte das regras futuras e NÃO DEVE ser uma obrigação, direito, lançamento, pagamento, recebimento ou linha de extrato.
- **FR-FREC-BOUND-003**: Ocorrência futura DEVE ser calculada para o intervalo consultado e NÃO DEVE gerar antecipadamente documentos reais em `accounts-payable`, `accounts-receivable` ou `financial-transactions`.
- **FR-FREC-BOUND-004**: Projeção recorrente NÃO DEVE alterar saldo, liquidar documento, consumir limite, bloquear fechamento nem comprovar execução externa.
- **FR-FREC-BOUND-005**: Materialização DEVE criar no máximo um documento real por ocorrência e transferir sua responsabilidade funcional ao módulo de destino.
- **FR-FREC-BOUND-006**: Alterar, pausar ou encerrar série NÃO DEVE alterar ou excluir documentos já materializados nem fatos financeiros relacionados.
- **FR-FREC-BOUND-007**: Esta feature NÃO DEVE executar pagamento, débito, cobrança, transferência, geração de boleto ou comunicação externa.
- **FR-FREC-BOUND-008**: Parcelamento de uma obrigação ou direito existente DEVE permanecer sob responsabilidade de contas a pagar ou receber e NÃO DEVE ser representado como recorrência.

### Série e Modelo Financeiro

- **FR-FREC-MASTER-001**: Série DEVE possuir identidade estável, nome, direção de entrada ou saída, forma de realização, moeda, data inicial, regra de repetição, estado e origem.
- **FR-FREC-MASTER-002**: Série PODERÁ definir contraparte, descrição proposta, referência, categoria, dimensões, conta financeira preferencial, observações e prioridade.
- **FR-FREC-MASTER-003**: Forma de realização DEVE distinguir ao menos `AÇÃO_DO_USUÁRIO` e `AUTOMÁTICA_EXTERNA` sem presumir que “automática” significa execução pelo Rinos.
- **FR-FREC-MASTER-004**: Série de ação do usuário PODERÁ sugerir conta financeira, mas a conta efetiva será definida ou validada no fluxo de pagamento ou recebimento.
- **FR-FREC-MASTER-005**: Série automática externa DEVE definir exatamente uma conta financeira esperada e direção compatível.
- **FR-FREC-MASTER-006**: Contraparte, categoria e dimensões da série DEVEM funcionar como propostas versionadas; documentos materializados preservam os valores efetivamente confirmados.
- **FR-FREC-MASTER-007**: Série de saída que materialize obrigação DEVE obedecer aos requisitos de credor de `accounts-payable`; série de entrada que materialize direito DEVE obedecer aos requisitos de devedor de `accounts-receivable`.
- **FR-FREC-MASTER-008**: Série PODERÁ permanecer em rascunho sem todos os dados finais, mas somente série ativa DEVE aparecer nas projeções operacionais.

### Calendário e Frequência

- **FR-FREC-SCHED-001**: Série DEVE definir data inicial e PODERÁ terminar por data, quantidade de ocorrências ou permanecer sem término predeterminado.
- **FR-FREC-SCHED-002**: Regra DEVE suportar diariamente, semanalmente, quinzenalmente, mensalmente, bimestralmente, trimestralmente, semestralmente, anualmente, a cada dois anos e a cada intervalo personalizado de dias, semanas, meses ou anos.
- **FR-FREC-SCHED-003**: Regra semanal PODERÁ selecionar um ou vários dias da semana; regra mensal ou anual DEVE preservar dia ou posição de calendário escolhida.
- **FR-FREC-SCHED-004**: Para dia inexistente no período, usuário DEVE escolher entre último dia válido, próxima data válida ou omissão da ocorrência.
- **FR-FREC-SCHED-005**: Série PODERÁ manter a data calculada, antecipá-la ou postergá-la quando cair em dia não útil reconhecido; a data-base e a data ajustada DEVEM permanecer distinguíveis.
- **FR-FREC-SCHED-006**: Ausência de calendário de feriados completo NÃO DEVE inventar ajuste; usuário poderá corrigir a ocorrência ou usar tolerância de identificação.
- **FR-FREC-SCHED-007**: Cálculo DEVE ser determinístico no calendário e fuso do tenant e reproduzir a mesma sequência sob a mesma versão.
- **FR-FREC-SCHED-008**: Consulta DEVE aceitar intervalo inicial e final finitos e impedir expansão ilimitada de série sem término.

### Valor Esperado

- **FR-FREC-AMT-001**: Série DEVE declarar moeda e estratégia de valor `EXATO`, `ESTIMADO` ou `DESCONHECIDO`.
- **FR-FREC-AMT-002**: Valor exato DEVE ser proposto integralmente na materialização, sem impedir correção autorizada do documento real.
- **FR-FREC-AMT-003**: Valor estimado DEVE alimentar projeções e correspondência, mas NÃO DEVE ser apresentado como dívida ou movimentação confirmada.
- **FR-FREC-AMT-004**: Valor desconhecido DEVE permitir projeção sem total monetário e exigir outros critérios para identificação.
- **FR-FREC-AMT-005**: Alteração de valor a partir de determinada ocorrência DEVE criar nova versão sem reescrever valores esperados por versões anteriores.
- **FR-FREC-AMT-006**: Fórmulas de consumo, índices externos, reajustes automáticos e conversão futura de moedas NÃO DEVEM ser presumidos; seus resultados somente entram na série por regra expressamente suportada ou alteração autorizada.

### Projeções e Agenda

- **FR-FREC-PROJ-001**: Projeção DEVE identificar logicamente série, versão, posição na sequência, data-base, data ajustada, valor esperado e situação derivada.
- **FR-FREC-PROJ-002**: Sistema DEVE calcular somente ocorrências necessárias ao intervalo consultado, aplicando versões e exceções vigentes.
- **FR-FREC-PROJ-003**: Agenda DEVE distinguir projeção futura, vencida não tratada, materializada, ignorada, resolvida e não identificada após tolerância.
- **FR-FREC-PROJ-004**: Totais projetados DEVEM permanecer separados por moeda e por grau de certeza de valor.
- **FR-FREC-PROJ-005**: Projeção com valor desconhecido NÃO DEVE ser somada como zero nem incluída silenciosamente em total monetário.
- **FR-FREC-PROJ-006**: Ocorrência em data fechada DEVE permanecer consultável e poderá originar documento não monetário conforme o módulo responsável, mas NÃO DEVE criar efeito financeiro retroativo proibido.
- **FR-FREC-PROJ-007**: Fechamento financeiro NÃO DEVE ser impedido por projeção recorrente, inclusive vencida, porque ela não constitui rascunho nem pré-lançamento.

### Materialização Manual

- **FR-FREC-MAT-001**: Ocorrência de ação do usuário somente DEVE ser materializada por ação explícita de usuário ou módulo autorizado, sem geração periódica automática.
- **FR-FREC-MAT-002**: Materialização de saída DEVE criar obrigação ou rascunho compatível em `accounts-payable`; materialização de entrada DEVE criar direito ou rascunho compatível em `accounts-receivable`.
- **FR-FREC-MAT-003**: Usuário DEVE revisar os dados propostos e completar informações exigidas pelo módulo de destino antes da confirmação do documento real.
- **FR-FREC-MAT-004**: Identidade lógica da ocorrência DEVE tornar a materialização idempotente e impedir documentos duplicados por repetição ou concorrência.
- **FR-FREC-MAT-005**: Documento materializado DEVE preservar vínculo com série, versão e ocorrência de origem sem permanecer subordinado às mudanças futuras da série.
- **FR-FREC-MAT-006**: Materializar uma ocorrência NÃO DEVE materializar ocorrências anteriores ou posteriores.
- **FR-FREC-MAT-007**: Cancelamento do documento real DEVE ser tratado pelo módulo de destino e refletido na agenda sem recriar automaticamente a ocorrência.

### Execução Automática Externa

- **FR-FREC-AUTO-001**: Série automática externa DEVE representar expectativa de fato executado fora do Rinos e NÃO DEVE agendar comando bancário.
- **FR-FREC-AUTO-002**: Ocorrência automática DEVE definir conta, direção, data esperada, tolerância anterior e posterior e critérios mínimos de identificação.
- **FR-FREC-AUTO-003**: Antes de existir fato externo reconhecido, ocorrência NÃO DEVE produzir lançamento, obrigação liquidada, direito liquidado ou mudança de saldo.
- **FR-FREC-AUTO-004**: Ultrapassada a janela posterior sem realização vinculada, ocorrência DEVE ser apresentada como não identificada, sem presumir falha do banco ou inadimplência.
- **FR-FREC-AUTO-005**: Reconhecimento de fato real DEVE preservar separadamente data e valor esperados e data e valor efetivos.
- **FR-FREC-AUTO-006**: Estorno, devolução ou reversão do fato real DEVE ser tratado pelas features financeiras responsáveis e atualizar somente a situação derivada da ocorrência.
- **FR-FREC-AUTO-007**: Cada série automática DEVE definir se uma confirmação futura produzirá obrigação ou direito com sua liquidação, ou somente lançamento financeiro classificado.
- **FR-FREC-AUTO-008**: Sugestão, candidato ou pontuação de correspondência NÃO DEVE produzir documento, vínculo, classificação, liquidação ou alteração de saldo antes da confirmação explícita do usuário na feature de conciliação.
- **FR-FREC-AUTO-009**: Na estratégia de obrigação ou direito, a confirmação DEVE acionar os contratos de `accounts-payable` ou `accounts-receivable`, criar os documentos e alocações aplicáveis e reconhecer exatamente uma movimentação financeira efetiva.
- **FR-FREC-AUTO-010**: Na estratégia de classificação direta, a confirmação DEVE produzir somente o lançamento financeiro categorizado compatível com a direção e os dados efetivos, sem criar obrigação, direito, liquidação ou recebimento artificial.
- **FR-FREC-AUTO-011**: Repetição da confirmação ou reprocessamento da mesma linha externa NÃO DEVE duplicar documento, liquidação, lançamento financeiro nem registro esparso da ocorrência.

### Critérios para Correspondência

- **FR-FREC-MATCH-001**: Série automática DEVE fornecer à futura conciliação conta, direção, janela de datas e moeda como critérios obrigatórios.
- **FR-FREC-MATCH-002**: Série PODERÁ fornecer contraparte, descrição esperada, palavras, padrões textuais, referência, faixa de valor e outros sinais não sensíveis permitidos.
- **FR-FREC-MATCH-003**: Tolerância de data DEVE permitir quantidades diferentes de dias antes e depois da data esperada.
- **FR-FREC-MATCH-004**: Para valor estimado, usuário PODERÁ definir tolerância absoluta, percentual ou ambas; quando ambas existirem, a regra aplicável DEVE ser explícita.
- **FR-FREC-MATCH-005**: Valor desconhecido NÃO DEVE aceitar correspondência automática baseada apenas em proximidade de data e conta.
- **FR-FREC-MATCH-006**: Critérios DEVEM produzir candidatos e justificativas, mas esta feature NÃO DEVE importar, conciliar nem confirmar linhas de extrato.
- **FR-FREC-MATCH-007**: Havendo múltiplos candidatos plausíveis ou sinais contraditórios, resultado DEVE permanecer ambíguo e exigir decisão na feature de conciliação.
- **FR-FREC-MATCH-008**: Correspondência futura DEVE admitir uma ocorrência relacionada a várias linhas e uma linha relacionada a várias ocorrências quando `bank-statements-reconciliation` permitir, preservando rateios e explicação.
- **FR-FREC-MATCH-009**: Alterar critérios da série NÃO DEVE reclassificar automaticamente fatos já resolvidos.
- **FR-FREC-MATCH-010**: A futura conciliação DEVE ser a única responsável por pontuar candidatos, apresentar sua explicação e obter a decisão final do usuário; esta feature fornece somente a expectativa recorrente e seus sinais.

### Exceções, Versões e Ciclo de Vida

- **FR-FREC-CHANGE-001**: Alteração DEVE permitir alcance `SOMENTE_ESTA_OCORRÊNCIA` ou `DESTA_OCORRÊNCIA_EM_DIANTE`.
- **FR-FREC-CHANGE-002**: Alteração isolada DEVE preservar exceção de data, valor, conta, contraparte, classificação, descrição ou situação sem alterar a regra principal.
- **FR-FREC-CHANGE-003**: Alteração futura DEVE criar versão efetiva a partir da ocorrência escolhida e manter versões anteriores auditáveis.
- **FR-FREC-CHANGE-004**: Exceções futuras existentes DEVEM ser reapresentadas ao usuário quando nova versão puder contradizê-las, sem descarte silencioso.
- **FR-FREC-CHANGE-005**: Usuário DEVE poder adicionar ocorrência extraordinária, ignorar ocorrência prevista ou restaurar ocorrência ignorada enquanto não houver fato incompatível.
- **FR-FREC-CHANGE-006**: Série DEVE distinguir rascunho, ativa, pausada, encerrada e cancelada.
- **FR-FREC-CHANGE-007**: Pausa DEVE suprimir projeções durante intervalo definido sem apagar a série; retomada DEVE preservar a sequência segundo política apresentada ao usuário.
- **FR-FREC-CHANGE-008**: Encerramento DEVE impedir novas ocorrências após o limite; cancelamento DEVE interromper a série sem apagar histórico, exceções ou realizações.
- **FR-FREC-CHANGE-009**: Reativação ou mudança retroativa NÃO DEVE criar documentos reais automaticamente.
- **FR-FREC-CHANGE-010**: Sistema DEVE persistir cadastro esparso de ocorrência somente quando sua identidade lógica possuir exceção, inclusão extraordinária, omissão ou restauração deliberada, materialização, resolução ou vínculo com fato efetivo.
- **FR-FREC-CHANGE-011**: Ocorrência projetada sem desvio nem fato associado DEVE permanecer calculada a partir da série e NÃO DEVE gerar registro persistente apenas por ter sido exibida, vencido ou integrada a uma consulta.
- **FR-FREC-CHANGE-012**: Registro esparso DEVE conservar a identidade lógica da ocorrência, o tipo do evento, seu estado vigente e os dados necessários para reproduzir a diferença em relação à série, sem duplicar os dados invariáveis da série.

### Autorização, Auditoria e Isolamento

- **FR-FREC-SEC-001**: Criar, consultar, alterar, ativar, pausar, encerrar, cancelar, materializar, ignorar, restaurar e auditar DEVEM admitir chaves de acesso distintas quando o risco diferir.
- **FR-FREC-SEC-002**: Autorização DEVE ser revalidada no tenant ativo e também no módulo de destino durante materialização ou consulta de documento real.
- **FR-FREC-SEC-003**: Acesso à série NÃO DEVE conceder automaticamente acesso a valores, contas, contrapartes, categorias, dimensões, extratos ou documentos vinculados.
- **FR-FREC-SEC-004**: Logs, erros, métricas e mensagens NÃO DEVEM expor valores, saldos, dados pessoais ou padrões financeiros além da finalidade autorizada.
- **FR-FREC-AUD-001**: Mudança de série, versão, exceção, estado, materialização e resolução DEVE registrar tenant, ator ou origem, instante, conteúdo anterior e novo, alcance e motivo quando exigido.
- **FR-FREC-AUD-002**: Histórico DEVE permitir reproduzir qual versão e exceção produziram qualquer projeção materializada ou resolvida.
- **FR-FREC-AUD-003**: Auditoria NÃO DEVE transformar projeções nunca utilizadas em fatos persistidos nem afirmar que expectativa calculada existia como documento real.

### Experiência e Acessibilidade

- **FR-FREC-UX-001**: Interface DEVE distinguir visual e textualmente série, projeção, documento materializado e fato financeiro realizado.
- **FR-FREC-UX-002**: Cadastro DEVE apresentar prévia finita das próximas ocorrências antes da ativação ou alteração da regra.
- **FR-FREC-UX-003**: Prévia DEVE evidenciar datas inexistentes, ajustes de dia útil, tolerâncias, valor desconhecido e término da série.
- **FR-FREC-UX-004**: Alteração DEVE perguntar seu alcance e apresentar impacto nas próximas ocorrências e exceções antes da confirmação.
- **FR-FREC-UX-005**: Agenda DEVE filtrar por direção, forma de realização, conta esperada, contraparte, período, situação, categoria e grau de certeza.
- **FR-FREC-UX-006**: Ações em lote DEVEM preservar decisão individual, impedir combinações incompatíveis e apresentar falhas sem perder resultados válidos.
- **FR-FREC-UX-007**: Estados, atrasos, incertezas e correspondências NÃO DEVEM depender somente de cor e as jornadas principais DEVEM ser operáveis por teclado e tecnologias assistivas.

### Confiabilidade e Operação

- **FR-FREC-INFRA-SCHED**: Projeções DEVEM ser calculadas sob demanda e ocorrências manuais NÃO DEVEM ser materializadas por agendador periódico nesta versão.
- **FR-FREC-INFRA-IDEMP**: Materialização e registro de realização DEVEM usar identidade estável no escopo do tenant, série e ocorrência, de modo que repetição produza no máximo um vínculo vigente.
- **FR-FREC-INFRA-LOCK**: Alteração de série, materialização, exceção e resolução concorrentes DEVEM preservar uma sequência determinística e impedir duplicidade em todas as instâncias da aplicação.
- **FR-FREC-INFRA-ATOMIC**: Materialização e vínculo com documento real DEVEM concluir integralmente ou não produzir documento parcial nem marcar ocorrência como realizada.
- **FR-FREC-INFRA-BACKUP**: Séries, versões, exceções, estados, realizações e auditorias DEVEM participar dos backups e restaurações do tenant conforme `tenant-data-governance`.
- **FR-FREC-INFRA-NOKEY**: Esta feature NÃO DEVE manter token externo, segredo próprio ou política de rotação criptográfica.
- **FR-FREC-OPS-001**: Consulta de agenda DEVE ser limitada por intervalo e paginação lógica, sem expandir séries indefinidas integralmente.
- **FR-FREC-OPS-002**: Falha ao calcular uma série NÃO DEVE impedir a apresentação das demais e DEVE identificar a recorrência inconsistente sem expor conteúdo protegido.

## Key Entities

- **Série Recorrente**: fonte de verdade que define calendário, direção, valor esperado, classificação, contraparte e forma de realização.
- **Versão da Série**: conjunto de regras aplicável a partir de determinada ocorrência sem reescrever versões anteriores.
- **Ocorrência Projetada**: representação calculada e não financeira de um compromisso em uma data, derivada da série, versão e exceções.
- **Exceção de Ocorrência**: alteração esparsa aplicável somente a uma ocorrência, como mudança, omissão ou inclusão extraordinária.
- **Registro Esparso da Ocorrência**: cadastro criado somente para uma ocorrência com desvio ou fato, preservando exceção, inclusão extraordinária, omissão, restauração, materialização, resolução ou vínculo com evento efetivo sem criar as ocorrências futuras normais.
- **Critérios de Correspondência**: conta, direção, data, valor, contraparte, referências e padrões usados para sugerir relação com evento externo.
- **Documento Materializado**: obrigação ou direito real criado explicitamente a partir de uma ocorrência e controlado por seu módulo financeiro.

## Success Criteria

- **SC-FREC-001**: Em 100% das consultas, ocorrências futuras não materializadas são reproduzidas a partir da série sem criar obrigações, direitos ou lançamentos financeiros.
- **SC-FREC-002**: Em 100% dos testes, projeção recorrente não altera saldo nem bloqueia fechamento financeiro.
- **SC-FREC-003**: Em 100% das materializações repetidas ou concorrentes, no máximo um documento real é vinculado à ocorrência.
- **SC-FREC-004**: Em 100% das alterações isoladas, somente a ocorrência escolhida muda; em alterações futuras, somente ela e as posteriores usam a nova versão.
- **SC-FREC-005**: Em 100% das séries alteradas ou encerradas, documentos materializados e fatos realizados permanecem inalterados e rastreáveis.
- **SC-FREC-006**: Em 100% das projeções, valor exato, estimado e desconhecido permanecem distinguíveis e totais não tratam desconhecido como zero.
- **SC-FREC-007**: Em 100% dos débitos automáticos esperados, nenhum saldo ou pagamento é criado antes de fato real reconhecido por feature responsável.
- **SC-FREC-008**: Em 100% das comparações ambíguas, nenhuma ocorrência ou linha externa é conciliada por esta feature e todos os candidatos permanecem explicáveis.
- **SC-FREC-009**: Em 100% dos testes de variação, datas e valores esperados e efetivos permanecem separadamente consultáveis.
- **SC-FREC-010**: Em 100% dos testes cruzados, dados de um tenant não são consultados, calculados, materializados nem inferidos por outro.
- **SC-FREC-011**: Usuários autorizados criam uma série mensal simples e validam sua prévia em até 90 segundos nos testes de usabilidade.
- **SC-FREC-012**: Pelo menos 95% das consultas comuns de agenda anual retornam projeções utilizáveis em até 2 segundos nas condições operacionais da primeira versão.
- **SC-FREC-013**: Todas as jornadas principais podem ser concluídas somente por teclado e sem bloqueios críticos para tecnologias assistivas.
- **SC-FREC-014**: Em 100% das falhas de materialização, nenhum documento parcial nem realização enganosa permanece.
- **SC-FREC-015**: Em 100% das auditorias testadas, série, versão, exceção e realização explicam o resultado sem transformar projeções não utilizadas em fatos históricos.
- **SC-FREC-016**: Em 100% das sugestões ainda não confirmadas pelo usuário, nenhum documento, vínculo, classificação, liquidação, lançamento ou alteração de saldo é produzido.
- **SC-FREC-017**: Em 100% dos reprocessamentos de uma confirmação, a linha externa e a ocorrência mantêm no máximo um conjunto vigente de efeitos financeiros.

## Fora do Escopo

- Execução de pagamentos, débitos, transferências ou cobranças em instituições externas.
- Importação de extratos e decisão final de conciliação, pertencentes a `bank-statements-reconciliation`.
- Conexão e sincronização bancária, pertencentes a `financial-connectivity`.
- Avisos, notificações automáticas e envio de mensagens ao usuário ou à contraparte.
- Emissão de boletos, Pix de cobrança, links de pagamento ou cartões.
- Aprovações, alçadas e segregação de funções de pagamentos.
- Cálculo de consumo de água, energia ou telefonia e consulta a faturas de fornecedores.
- Índices externos e reajustes automáticos dependentes de serviços de terceiros.
- Geração antecipada em massa de obrigações, direitos, pagamentos ou recebimentos futuros.
- Calendário fiscal, contábil ou trabalhista e manutenção completa de feriados.
