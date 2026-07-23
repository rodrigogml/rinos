# Feature Specification: Cartões de Crédito

**Feature**: `credit-cards`  
**Created**: 2026-07-22  
**Status**: Clarified — pronta para planejamento

## Escopo

Esta feature permite controlar cartões de crédito como contas financeiras especializadas que representam dívida, e não dinheiro disponível. A conta concentra moeda, instituição, bandeira, limite, regras de fechamento e vencimento, compras, parcelas, pagamentos, créditos e saldo devedor ou credor. Suas faturas são visões calculadas dos lançamentos atribuídos a cada ciclo, sem saldo ou versão próprios.

Uma conta pode possuir cartões físicos, adicionais ou virtuais associados para identificar portadores e origens de compras, mas esse detalhamento é opcional. Todos os cartões associados compartilham a conta, o saldo, o limite e as faturas; nenhum deles cria conta financeira independente.

Compras parceladas originam desde o início um lançamento para cada parcela. Todas preservam a mesma data da compra, enquanto cada parcela possui sua própria data prevista de vencimento, número sequencial, total de parcelas e valor. Isso permite consultar a dívida total assumida, as parcelas futuras e a composição de cada fatura sem depender da configuração atual de fechamento.

> [!IMPORTANT]
> O saldo de uma conta de cartão representa uma posição de crédito ou dívida. Limite, valor disponível, dívida futura, fatura atual e saldo efetivado são conceitos relacionados, mas distintos e não podem compartilhar um único valor mutável.

> [!CAUTION]
> A feature não deve armazenar número completo do cartão, código de segurança, senha, PIN, trilha magnética, criptograma ou credencial capaz de autorizar transação. Cartões opcionais serão reconhecidos por apelido, últimos dígitos e outros dados não secretos.

## Clarifications

### Session 2026-07-22

- Q: Cartão de crédito deve ser instrumento separado ou conta financeira? → A: Deve ser uma conta financeira especializada de crédito/passivo. Compras reduzem seu saldo, créditos e pagamentos o aumentam em direção a zero, e atributos de cartão complementam sua natureza.
- Q: Onde registrar a bandeira? → A: A bandeira pertence à conta de cartão. O catálogo inicial contempla ao menos Mastercard, Visa, American Express, Diners e `OUTRA`, sendo obrigatório descrever a bandeira quando esta última for escolhida.
- Q: É obrigatório cadastrar cada cartão físico, adicional ou virtual? → A: Não. A conta funciona sem esse detalhamento; cartões associados são opcionais e existem somente quando o usuário deseja controlar portadores ou identificar a origem das compras.
- Q: Como representar uma compra parcelada? → A: Todas as parcelas são criadas como lançamentos desde a compra, preservam a mesma data de compra e possuem vencimento, valor, sequência e total próprios. O conjunto permite calcular dívida total e projeções.
- Q: Qual identidade deve agrupar as parcelas? → A: A compra possui identidade própria e todas as parcelas, inclusive a primeira, referenciam essa compra. Nenhuma parcela acumula a função especial de raiz do conjunto.
- Q: Até quando valores e vencimentos de parcelas podem ser alterados pelo usuário? → A: Enquanto a data financeira da parcela não estiver protegida pela data de fechamento financeiro do tenant. O estado da fatura não bloqueia a alteração; toda revisão deve ser auditável e preservar os valores anteriores.
- Q: A fatura precisa manter versões próprias quando seus lançamentos são alterados? → A: Não. A fatura é um saldo calculado a partir dos lançamentos atribuídos ao ciclo e reflete seus valores atuais. A rastreabilidade pertence ao histórico de alterações dos lançamentos, sem duplicar versões do total da fatura.
- Q: O histórico editável antes do fechamento financeiro deve valer somente para parcelas de cartão ou para todo lançamento financeiro? → A: Deve valer para todo lançamento. Lançamentos em datas abertas podem ser revisados ou cancelados com histórico completo e recálculo; lançamentos alcançados pela data de fechamento tornam-se imutáveis e exigem compensação posterior.
- Q: Como o reparcelamento de saldo deve criar a nova dívida? → A: O acordo deve selecionar somente principal ainda pendente, neutralizá-lo explicitamente por novo fato na data aberta do acordo e criar uma nova operação-pai com seu cronograma. Pagamentos e créditos anteriores ficam fora do principal renegociado; o principal substituído não constitui nova despesa, enquanto juros, multas, impostos e tarifas são fatos econômicos separados e categorizados. Compras, parcelas e demais fatos originais permanecem preservados e vinculados ao acordo.

## Interface Coverage

| Superfície humana conhecida | Tipo | Atores | Cobertura | Comportamento funcional | Exclusões ou adiamentos |
|-----------------------------|------|--------|-----------|-------------------------|-------------------------|
| Web responsiva | Web | Usuários autorizados do tenant | FULL | Cadastrar contas de cartão, registrar compras e parcelas, consultar dívida e faturas, associar cartões opcionais e registrar pagamentos e ajustes | Integração direta com operadoras e captura automática de compras permanecem adiadas |
| Aplicativo móvel nativo | Mobile | Usuários do tenant | N/A | Não existe superfície móvel nativa definida | Toda capacidade nativa permanece fora do produto atual |
| Aplicação desktop nativa | Desktop | Usuários do tenant | N/A | Não existe superfície desktop nativa definida | Toda capacidade desktop nativa permanece fora do produto atual |
| CLI/TUI | CLI/TUI | Operadores | N/A | Não há jornada humana por terminal | Administração operacional por terminal não integra a feature |

## User Scenarios & Testing

### User Story 1 - Cadastrar uma conta de cartão (Priority: P1)

Um usuário autorizado cadastra uma conta de cartão informando instituição, nome, moeda, bandeira, limite e regras correntes de fechamento e vencimento.

**Why this priority**: A conta é a identidade única que reúne a dívida, as faturas e os cartões opcionais sem duplicar saldos.

**Independent Test**: Cadastrar uma conta sem cartões subordinados e comprovar que ela pode receber compras, consultar dívida e gerar faturas.

**Acceptance Scenarios**:

1. **Given** usuário autorizado e dados válidos, **When** a conta é ativada, **Then** ela possui uma moeda, natureza de crédito, bandeira e saldo inicial explicável.
2. **Given** bandeira não encontrada no catálogo, **When** `OUTRA` é escolhida e descrita, **Then** o cadastro é permitido sem criar automaticamente uma nova bandeira global.
3. **Given** conta sem cartões físicos ou virtuais cadastrados, **When** uma compra é registrada, **Then** a operação é aceita sem exigir instrumento subordinado.

---

### User Story 2 - Registrar compra à vista ou parcelada (Priority: P1)

Um usuário autorizado registra uma compra, informa data, valor, categoria, dimensões aplicáveis e quantidade de parcelas, revisa a distribuição e confirma todos os lançamentos relacionados.

**Why this priority**: Compras e parcelas explicam a dívida atual e futura do cartão.

**Independent Test**: Registrar uma compra em três parcelas e comprovar três lançamentos vinculados, mesma data de compra, vencimentos próprios, sequência 1/3 a 3/3 e soma igual ao total.

**Acceptance Scenarios**:

1. **Given** compra de parcela única válida, **When** confirmada, **Then** um lançamento negativo categorizado é associado à conta.
2. **Given** compra parcelada válida, **When** confirmada, **Then** todas as parcelas são criadas de uma vez e permanecem identificáveis como uma única compra.
3. **Given** divisão com arredondamento, **When** o usuário ajusta os valores antes da confirmação, **Then** a distribuição é aceita somente se a soma for exatamente igual ao total da compra.

---

### User Story 3 - Consultar saldo, dívida e projeção (Priority: P1)

Um usuário autorizado consulta separadamente o saldo efetivado, a dívida total assumida, as parcelas futuras, a próxima fatura e o limite disponível.

**Why this priority**: Um único saldo não explica simultaneamente quanto já foi efetivado, quanto ainda vencerá e quanto crédito permanece utilizável.

**Independent Test**: Registrar compra parcelada, pagamento parcial e crédito e reproduzir cada visão a partir dos fatos e datas correspondentes.

**Acceptance Scenarios**:

1. **Given** parcelas presentes e futuras, **When** o saldo em uma data é consultado, **Then** somente lançamentos efetivados até a data compõem o saldo daquela data.
2. **Given** a mesma compra, **When** a dívida total é consultada, **Then** parcelas vencidas e futuras ainda não neutralizadas são apresentadas sem dupla contagem.
3. **Given** limite informado, **When** disponível é exibido, **Then** sua memória de cálculo distingue limite, compromissos, pagamentos, créditos e eventuais autorizações futuras.

---

### User Story 4 - Consultar faturas calculadas (Priority: P1)

Um usuário autorizado acompanha a fatura atual, as anteriores e as futuras como agrupamentos calculados dos lançamentos atribuídos a cada ciclo e vencimento.

**Why this priority**: A fatura organiza compras e ajustes por vencimento e precisa ser comparável ao documento da operadora sem duplicar saldos ou históricos já mantidos nos lançamentos.

**Independent Test**: Calcular uma fatura, alterar lançamento ainda permitido e comprovar recálculo imediato do total e preservação da alteração no histórico do lançamento.

**Acceptance Scenarios**:

1. **Given** lançamentos atribuídos a um vencimento, **When** a fatura é consultada, **Then** itens, créditos, pagamentos, atrasos e total são calculados sem saldo armazenado próprio.
2. **Given** alteração nas regras correntes da conta, **When** faturas são consultadas, **Then** vencimentos já gravados nos lançamentos permanecem até que sejam alterados explicitamente onde permitido.
3. **Given** divergência da operadora em data ainda aberta financeiramente, **When** usuário altera uma parcela, **Then** o total calculado da fatura é atualizado e o histórico pertence ao lançamento; se a data estiver bloqueada, a diferença exige novo fato em data aberta.

---

### User Story 5 - Pagar uma fatura (Priority: P1)

Um usuário autorizado registra pagamento total, parcial ou excedente a partir de outra conta financeira e acompanha o saldo remanescente.

**Why this priority**: O pagamento reduz a dívida, mas não representa nova despesa porque as compras já foram categorizadas.

**Independent Test**: Pagar parte de uma fatura por transferência e comprovar saída na conta pagadora, crédito na conta de cartão e ausência de nova despesa.

**Acceptance Scenarios**:

1. **Given** conta pagadora e cartão na mesma moeda, **When** o pagamento é confirmado, **Then** uma transferência reduz a conta pagadora e aumenta o saldo do cartão em direção a zero.
2. **Given** pagamento parcial, **When** confirmado, **Then** a fatura calculada apresenta lançamentos abrangidos, valor pago e saldo pendente.
3. **Given** pagamento maior que a dívida, **When** permitido e confirmado, **Then** o cartão pode apresentar saldo credor sem transformar o excedente em receita.

---

### User Story 6 - Identificar cartões e portadores opcionalmente (Priority: P2)

Um usuário autorizado associa cartões físicos, adicionais ou virtuais à conta para identificar portadores e compras, sem criar novo saldo ou nova fatura.

**Why this priority**: O detalhamento ajuda contas compartilhadas, mas não deve impor burocracia a quem controla somente a fatura consolidada.

**Independent Test**: Operar a conta sem cartões subordinados e depois associar dois cartões, comprovando um único saldo e fatura.

**Acceptance Scenarios**:

1. **Given** conta ativa, **When** cartão opcional é associado, **Then** ele registra somente apresentação segura, tipo, portador e estado.
2. **Given** cartão adicional, **When** compra é registrada com sua referência, **Then** a compra pode ser filtrada pelo portador sem criar conta independente.
3. **Given** cartão cancelado, **When** novo uso é tentado, **Then** a referência é recusada, mas compras históricas permanecem identificáveis.

---

### User Story 7 - Registrar créditos, estornos e ajustes (Priority: P2)

Um usuário autorizado registra estorno de compra, crédito da operadora, juros, multa, tarifa ou ajuste necessário, preservando categoria, data e origem.

**Why this priority**: A fatura real pode divergir da programação inicial e toda diferença precisa ser explicada sem editar saldo diretamente.

**Independent Test**: Registrar crédito parcial e juros e comprovar seus efeitos distintos, categorias e vínculos com compra ou fatura.

**Acceptance Scenarios**:

1. **Given** compra confirmada, **When** estorno total ou parcial é registrado, **Then** novo lançamento positivo é vinculado sem alterar o original.
2. **Given** juros, multa ou tarifa, **When** confirmados, **Then** lançamentos negativos próprios são categorizados e incluídos na fatura aplicável.
3. **Given** simples divergência de saldo, **When** usuário tenta editar o saldo diretamente, **Then** a operação é recusada e exige lançamento explicativo.

---

### User Story 8 - Reparcelar saldo de fatura (Priority: P2)

Um usuário autorizado registra acordo que transforma saldo pendente de uma ou mais faturas em nova programação de parcelas, juros e encargos.

**Why this priority**: Reparcelamento altera vencimentos e custo da dívida, mas não pode apagar a obrigação nem os pagamentos que originaram o acordo.

**Independent Test**: Reparcelar saldo pendente e comprovar preservação da fatura original, neutralização explícita do saldo abrangido e criação da nova programação.

**Acceptance Scenarios**:

1. **Given** principal pendente selecionado, **When** acordo é confirmado, **Then** sua neutralização e a criação do novo cronograma ocorrem atomicamente e sem dupla contagem da dívida.
2. **Given** dívida parcialmente paga ou creditada, **When** o principal elegível é calculado, **Then** pagamentos e créditos anteriores não são novamente financiados.
3. **Given** novo acordo com encargos, **When** seus efeitos são consultados, **Then** o principal substituído não aparece como nova despesa e juros, multas, impostos e tarifas aparecem separadamente classificados.
4. **Given** acordo confirmado, **When** sua origem é consultada, **Then** compras, parcelas, pagamentos e créditos originais permanecem preservados e rastreáveis sem versão própria da fatura.

### Edge Cases

- Conta inicia com dívida anterior ou saldo credor.
- Bandeira ausente do catálogo ou alterada comercialmente no futuro.
- Conta possui cartões de mais de um portador, mas um único limite compartilhado.
- Compra não identifica cartão subordinado.
- Compra parcelada possui valor menor que a quantidade de parcelas na menor unidade monetária.
- Distribuição manual não soma o total da compra.
- Vencimento de parcela cai em data inexistente no mês seguinte.
- Data de fechamento ou vencimento da conta é alterada com lançamentos já atribuídos a ciclos passados ou futuros.
- Compra é registrada depois do fechamento com data anterior a ele.
- Parcela futura alcança período bloqueado pelo fechamento financeiro do tenant.
- Pagamento parcial, atrasado, antecipado ou excedente.
- Pagamento é estornado na conta pagadora.
- Crédito da operadora supera a dívida.
- Estorno da compra ocorre depois de algumas parcelas já pagas.
- Fatura contém juros, multa, IOF, anuidade ou outra tarifa.
- Reparcelamento abrange apenas parte da dívida.
- Conta é encerrada com parcelas futuras ou faturas pendentes.
- Mesmo evento é importado ou confirmado mais de uma vez.
- Alterações concorrentes tentam editar parcelas ou pagamentos da mesma fatura calculada.
- Usuário acessa a conta, mas não possui acesso ao portador ou à categoria relacionada.

## Requirements

### Conta e Fronteiras

- **FR-CC-BOUND-001**: Toda conta de cartão, cartão associado, compra, parcela, fatura, pagamento, ajuste e renegociação DEVE pertencer exclusivamente a um tenant.
- **FR-CC-BOUND-002**: Conta de cartão DEVE ser conta financeira especializada de crédito/passivo e manter exatamente uma moeda.
- **FR-CC-BOUND-003**: Saldo negativo DEVE representar dívida e saldo positivo DEVE poder representar crédito, pagamento excedente ou valor a favor do tenant.
- **FR-CC-BOUND-004**: A conta DEVE concentrar saldo, limite e faturas; cartões físicos, adicionais e virtuais NÃO DEVEM possuir saldo ou fatura independentes.
- **FR-CC-BOUND-005**: Compra, parcela, crédito, juros e tarifa DEVEM produzir lançamentos comuns compatíveis; pagamento de fatura DEVE usar transferência entre contas quando houver conta pagadora controlada pelo tenant.
- **FR-CC-BOUND-006**: A feature NÃO DEVE executar compra, autorizar cartão, comunicar-se com operadora, capturar transação nem presumir que um registro interno altera a instituição externa.
- **FR-CC-BOUND-007**: Cheques, cartões de débito, chaves Pix, aplicativos e canais de conta NÃO DEVEM originar cadastro genérico de instrumentos nesta feature.

### Cadastro da Conta

- **FR-CC-ACCOUNT-001**: Conta DEVE possuir identidade estável, nome, moeda, instituição, bandeira, estado e data inicial de controle.
- **FR-CC-ACCOUNT-002**: Bandeira DEVE ser selecionada em catálogo global que inclua ao menos `MASTERCARD`, `VISA`, `AMERICAN_EXPRESS`, `DINERS` e `OUTRA`.
- **FR-CC-ACCOUNT-003**: Escolha de `OUTRA` DEVE exigir descrição apresentável e NÃO DEVE criar ou alterar automaticamente item global.
- **FR-CC-ACCOUNT-004**: Conta DEVE permitir limite informado separadamente do saldo e preservar histórico de alterações quando afetar consultas passadas.
- **FR-CC-ACCOUNT-005**: Conta DEVE manter regras vigentes de fechamento e vencimento para sugerir novos ciclos sem reescrever automaticamente datas já gravadas nos lançamentos.
- **FR-CC-ACCOUNT-006**: Encerramento DEVE exigir ausência de dívida, crédito, parcelas futuras e faturas pendentes, salvo correção excepcional explicitamente auditada.

### Cartões Opcionais e Portadores

- **FR-CC-CARD-001**: Cadastro de cartão físico, adicional ou virtual DEVE ser opcional e a conta DEVE funcionar integralmente sem ele.
- **FR-CC-CARD-002**: Cartão associado DEVE possuir identidade própria, apelido, tipo e estado e PODE possuir últimos dígitos, validade e portador.
- **FR-CC-CARD-003**: Portador, quando informado, DEVE referenciar pessoa do mesmo tenant; associação com usuário NÃO DEVE conceder acesso.
- **FR-CC-CARD-004**: Suspensão, expiração ou cancelamento DEVE impedir nova referência e preservar compras anteriores.
- **FR-CC-CARD-005**: Substituição DEVE criar nova identidade opcionalmente vinculada à anterior, sem reescrever histórico.

### Compras e Parcelas

- **FR-CC-PURCH-001**: Compra DEVE preservar conta, data da compra, descrição, valor total, moeda, categoria, dimensões aplicáveis, contraparte opcional e cartão opcional.
- **FR-CC-PURCH-002**: Compra em parcela única DEVE produzir um lançamento; compra parcelada DEVE produzir um lançamento para cada parcela desde sua confirmação.
- **FR-CC-PURCH-003**: Todas as parcelas DEVEM preservar a mesma data da compra e possuir sequência, quantidade total, valor e data de vencimento próprios.
- **FR-CC-PURCH-004**: Soma dos valores das parcelas DEVE ser exatamente igual ao total da compra na precisão da moeda.
- **FR-CC-PURCH-005**: Sistema DEVE sugerir distribuição e vencimentos, mas permitir revisão dos valores e datas dentro das regras ainda abertas.
- **FR-CC-PURCH-006**: Parcelas DEVEM permanecer consultáveis como conjunto e individualmente, sem depender de descrição textual ou código digitado pelo usuário.
- **FR-CC-PURCH-007**: Cada parcela DEVE manter classificação suficiente para relatórios; rateio comum da compra PODE ser replicado inicialmente e ajustado enquanto sua data estiver aberta.
- **FR-CC-PURCH-008**: Parcela futura NÃO DEVE ser apresentada como vencida nem compor saldo efetivado anterior à sua data, mas DEVE compor dívida futura e projeções.
- **FR-CC-PURCH-009**: Confirmação da compra e criação de todas as parcelas DEVEM ocorrer como uma única operação de negócio, sem conjunto parcial.
- **FR-CC-PURCH-010**: Compra DEVE possuir identidade própria e todas as parcelas, inclusive a primeira, DEVEM referenciá-la diretamente, sem usar uma parcela como raiz estrutural do conjunto.
- **FR-CC-PURCH-011**: Categoria, dimensões, contraparte e demais classificações comuns definidas na compra DEVEM ser replicadas para cada parcela como campos do lançamento financeiro padrão, sem depender da consulta ao pai para interpretar o lançamento.
- **FR-CC-PURCH-012**: Parcela DEVE reutilizar os contratos comuns de lançamento financeiro para conta, moeda, valor, descrição, categoria, dimensões, contraparte, datas, estado, auditoria e correção; somente a compra, o vínculo parcelar e a fatura DEVEM acrescentar contexto próprio do cartão.
- **FR-CC-PURCH-013**: Usuário autorizado DEVE poder alterar valor, vencimento e classificações de parcela cuja data financeira não esteja protegida pelo fechamento financeiro do tenant, independentemente de a fatura estar aberta ou fechada.
- **FR-CC-PURCH-014**: Alteração de parcela DEVE preservar versão anterior, valores modificados, motivo, ator e instante; a operação NÃO DEVE apagar silenciosamente o lançamento anteriormente conhecido.
- **FR-CC-PURCH-015**: Parcela com data protegida pelo fechamento financeiro NÃO DEVE aceitar alteração direta; correção posterior DEVE ocorrer por novos fatos vinculados em data aberta.

### Saldos, Dívida e Limite

- **FR-CC-BAL-001**: Saldo efetivado em uma data DEVE resultar do saldo de abertura e dos lançamentos aplicáveis efetivados até aquela data.
- **FR-CC-BAL-002**: Dívida total DEVE incluir saldo devedor efetivado e parcelas futuras ainda não neutralizadas, sem dupla contagem.
- **FR-CC-BAL-003**: Sistema DEVE apresentar separadamente saldo efetivado, dívida vencida, fatura atual, dívida futura, crédito, limite total e limite disponível.
- **FR-CC-BAL-004**: Limite disponível DEVE possuir memória de cálculo e NÃO DEVE ser inferido apenas pela soma do limite com o saldo efetivado.
- **FR-CC-BAL-005**: Usuário NÃO DEVE editar diretamente saldo, dívida total, valor da fatura nem limite disponível calculado.
- **FR-CC-BAL-006**: Totais de contas de moedas diferentes DEVEM permanecer separados enquanto não houver conversão explícita.

### Faturas

- **FR-CC-BILL-001**: Fatura DEVE ser uma visão calculada dos lançamentos atribuídos a uma conta, ciclo e vencimento, sem manter saldo, total ou versão próprios.
- **FR-CC-BILL-002**: Fatura DEVE apresentar período, referência de fechamento, vencimento, moeda, itens, créditos, pagamentos, atrasos e total derivados.
- **FR-CC-BILL-003**: Alteração permitida em lançamento DEVE atualizar imediatamente a fatura calculada correspondente; seu histórico DEVE permanecer no lançamento, sem criar versão da fatura.
- **FR-CC-BILL-004**: Mudança das regras correntes de fechamento ou vencimento DEVE orientar novas compras e parcelas, mas NÃO DEVE alterar automaticamente datas já gravadas nos lançamentos existentes.
- **FR-CC-BILL-005**: Fatura DEVE distinguir cobranças, pagamentos, créditos, saldo pendente, atraso e eventual saldo renegociado sem duplicar seus fatos de origem.
- **FR-CC-BILL-006**: Referência de fechamento da fatura do cartão NÃO DEVE alterar nem substituir a data de fechamento financeiro do tenant.
- **FR-CC-BILL-007**: Faturas passadas e futuras DEVEM ser reproduzíveis a partir dos lançamentos e de suas datas atuais, enquanto alterações anteriores permanecem explicáveis pelo histórico de cada lançamento.

### Pagamentos, Créditos e Renegociação

- **FR-CC-PAY-001**: Pagamento a partir de conta controlada DEVE vincular transferência que reduza a conta pagadora e aumente o saldo do cartão, sem categoria de despesa no principal.
- **FR-CC-PAY-002**: Pagamento DEVE poder ser total, parcial, antecipado ou excedente e preservar sua alocação às faturas afetadas.
- **FR-CC-PAY-003**: Estorno de pagamento DEVE compensar seus efeitos sem apagar a transferência original.
- **FR-CC-PAY-004**: Estorno, reembolso ou crédito da operadora DEVE ser lançamento positivo vinculado à origem quando conhecida.
- **FR-CC-PAY-005**: Juros, multas, tarifas, impostos e anuidades DEVEM ser lançamentos negativos próprios, categorizados e separados do principal.
- **FR-CC-PAY-006**: Reparcelamento DEVE preservar os fatos e as faturas calculadas de origem, identificar principal abrangido, juros e encargos e criar nova programação rastreável.
- **FR-CC-PAY-007**: Reparcelamento NÃO DEVE editar compras, parcelas, pagamentos, créditos ou outros fatos originais para simular o novo acordo.
- **FR-CC-PAY-008**: Principal elegível DEVE resultar apenas dos débitos expressamente selecionados que ainda estejam pendentes, descontando pagamentos, estornos, reembolsos, créditos e neutralizações já aplicados.
- **FR-CC-PAY-009**: Confirmação do acordo DEVE criar atomicamente um lançamento positivo que neutralize o principal selecionado e uma nova operação-pai com todas as parcelas do cronograma, sem estado parcial nem dupla contagem.
- **FR-CC-PAY-010**: Componentes que reapresentem o principal renegociado DEVEM ser estruturais e não econômicos, sem produzir nova receita ou despesa; juros, multas, impostos, tarifas e demais encargos DEVEM ser lançamentos econômicos negativos próprios e categorizados.
- **FR-CC-PAY-011**: Acordo e seus efeitos DEVEM manter vínculos rastreáveis com os débitos selecionados e suas compras, parcelas, pagamentos e créditos originais, sem apagá-los ou reescrevê-los.
- **FR-CC-PAY-012**: A data do acordo e dos novos fatos que neutralizam ou reapresentam principal DEVE estar aberta no controle financeiro; o reparcelamento NÃO DEVE exigir edição retroativa das datas originais já protegidas.
- **FR-CC-PAY-013**: Soma do principal reapresentado e dos encargos explícitos DEVE corresponder exatamente ao total do novo cronograma na precisão da moeda.

### Segurança, Autorização e Confiabilidade

- **FR-CC-SEC-001**: Consultar conta, saldo, limite, compra, fatura, cartão, portador e pagamento; criar; alterar; confirmar; fechar; pagar; ajustar; renegociar e exportar DEVEM admitir autorizações independentes conforme o risco.
- **FR-CC-SEC-002**: Sistema NÃO DEVE receber nem armazenar número completo do cartão, CVV/CVC/CID, PIN, senha, trilha, criptograma ou credencial de autorização.
- **FR-CC-SEC-003**: Últimos dígitos e demais dados permitidos DEVEM ser mascarados por padrão e ausentes de logs, erros e métricas sem finalidade legítima.
- **FR-CC-SEC-004**: Toda confirmação, fechamento, pagamento, ajuste, alteração de limite, mudança de regra e renegociação DEVE registrar ator ou origem, instante, tenant, motivo e efeitos.
- **FR-CC-SEC-005**: Repetição equivalente da mesma solicitação DEVE produzir no máximo uma conta, compra, conjunto de parcelas, fatura, pagamento ou ajuste; repetição divergente DEVE ser rejeitada.
- **FR-CC-SEC-006**: Operações concorrentes DEVEM impedir parcela duplicada, fechamento incompatível, pagamento duplicado e perda de ajuste.
- **FR-CC-SEC-007**: Acesso a conta ou fatura NÃO DEVE revelar pessoa, categoria, dimensão ou conta pagadora protegida por autorização independente.

### Decisões de Infraestrutura

- **FR-CC-INFRA-001**: Consultas por data DEVEM determinar efetivação e projeção sem depender de processamento periódico obrigatório; passagem do tempo não pode duplicar lançamentos.
- **FR-CC-INFRA-002**: A feature NÃO utiliza tokens externos, credenciais persistentes nem criptografia de números completos de cartão; eventual integração futura exige especificação e proteção próprias.
- **FR-CC-INFRA-003**: Dados e auditorias DEVEM participar da governança, backup e restauração do tenant sem reabrir fatura nem reativar cartão cancelado.
- **FR-CC-INFRA-004**: Idempotência DEVE ser estável no tenant e na operação de negócio; prazo e armazenamento da chave serão definidos no planejamento sem comprometer a detecção de repetição.

### Key Entities

- **Conta de Cartão de Crédito**: conta financeira de crédito/passivo que reúne moeda, instituição, bandeira, limite, saldo, regras e faturas.
- **Bandeira de Cartão**: classificação global controlada, com alternativa `OUTRA` descrita na própria conta.
- **Cartão Associado**: identificação opcional de cartão físico, adicional ou virtual, sem saldo próprio.
- **Compra de Cartão**: identidade própria que coordena os dados comuns do fato e todas as suas parcelas, sem possuir saldo separado nem substituir os lançamentos.
- **Parcela de Compra**: lançamento financeiro padrão vinculado diretamente à compra, com data da compra comum, vencimento, valor e posição na sequência.
- **Fatura**: visão calculada que agrupa parcelas, créditos, encargos e pagamentos por conta, ciclo e vencimento, sem saldo armazenado nem versionamento próprio.
- **Pagamento de Fatura**: vínculo entre a fatura e uma transferência ou outro crédito que reduz sua dívida.
- **Ajuste de Cartão**: crédito ou débito explicativo posterior, como estorno, juros, tarifa ou divergência.
- **Acordo de Reparcelamento**: operação-pai que seleciona principal ainda pendente, coordena sua neutralização e sua reapresentação em novo cronograma e mantém encargos separados e vínculos com todos os fatos originais.

## Success Criteria

### Measurable Outcomes

- **SC-CC-001**: Em 100% dos testes cruzados, nenhuma conta, compra, parcela, fatura, pagamento ou cartão de um tenant é consultado ou alterado por outro.
- **SC-CC-002**: Em 100% das compras parceladas válidas, todas as parcelas são produzidas uma única vez, possuem sequência completa e somam exatamente o total.
- **SC-CC-003**: Em 100% das consultas testadas, saldo efetivado, dívida total, fatura atual, parcelas futuras e limite disponível podem ser reproduzidos sem dupla contagem.
- **SC-CC-004**: Em 100% das faturas testadas, o total corresponde aos lançamentos atualmente atribuídos ao ciclo, e toda diferença anterior pode ser explicada pelo histórico dos lançamentos sem versão própria da fatura.
- **SC-CC-005**: Em 100% dos pagamentos por conta controlada, o principal é transferência vinculada e não cria nova despesa categorizada.
- **SC-CC-006**: Em 100% dos ajustes e renegociações, fatos originais permanecem imutáveis e a cadeia explica o saldo resultante.
- **SC-CC-007**: Em 100% das contas sem cartões associados, todas as jornadas essenciais continuam disponíveis.
- **SC-CC-008**: Em 100% das superfícies examinadas, nenhum número completo, código de segurança, senha, PIN, trilha ou criptograma está presente.
- **SC-CC-009**: Usuários autorizados registram e revisam uma compra simples em até 60 segundos e uma compra parcelada comum em até 120 segundos nos testes de usabilidade.
- **SC-CC-010**: Pelo menos 95% das consultas comuns de conta, dívida e fatura retornam resultado utilizável em até 2 segundos nas condições operacionais da primeira versão.
- **SC-CC-011**: Todas as jornadas principais podem ser concluídas por teclado e sem bloqueios críticos para tecnologias assistivas.
- **SC-CC-012**: Em 100% das repetições e disputas concorrentes testadas, nenhuma compra, parcela, fatura, pagamento ou ajuste é duplicado ou perdido.
- **SC-CC-013**: Em 100% dos reparcelamentos testados, o principal selecionado é neutralizado uma única vez, pagamentos e créditos anteriores são excluídos, o novo cronograma reconcilia exatamente e apenas os encargos produzem nova despesa.

## Fora do Escopo

- Cartões de débito, chaves Pix, aplicativos e cadastro genérico de instrumentos de pagamento.
- Cheques e seu ciclo operacional.
- Autorização, captura ou execução de compra junto à operadora.
- Número completo, código de segurança, senha, PIN, trilha, criptograma ou cofre de cartões.
- Importação de extrato, sincronização automática e conciliação com operadora.
- Contestação, chargeback e disputa externa com estabelecimento ou emissor.
- Programas de pontos, milhas, cashback promocional e benefícios da bandeira.
- Conversão automática de compras internacionais e cálculo tributário cambial.
- Regras contábeis de partidas dobradas e demonstrações formais.
