# Feature Specification: Contas Financeiras

**Feature**: `financial-accounts`
**Created**: 2026-07-21
**Status**: Clarified — pronta para planejamento

## Escopo

Esta feature estabelece o cadastro das contas financeiras usadas pelo tenant para representar saldos monetários independentes sob seu controle ou acompanhamento, como caixas físicos, contas de depósito ou pagamento, valores monetários custodiados por plataformas e contas monetárias de liquidação em corretoras. Cada conta possui identidade, natureza canônica, moeda, saldo de abertura datado, organização, estado e classificações complementares compatíveis.

A conta financeira é o local em que lançamentos futuros afetarão saldos. Ela é diferente dos dados de pagamento de uma pessoa, que apenas indicam onde uma contraparte deseja receber valores, e também não representa uma conta do plano contábil.

> [!IMPORTANT]
> O saldo atual não é um campo livre para edição. Ele deve ser explicável pelo saldo de abertura e por todos os eventos financeiros efetivados na conta, preservando precisão, ordem e rastreabilidade.

> [!NOTE]
> Esta primeira versão não cria lançamentos, transferências, cartões, faturas, conciliação, importação de extratos ou integração bancária. Ela define a conta e os contratos que essas funcionalidades deverão respeitar.

## Clarifications

### Session 2026-07-21

- Q: Como representar uma conta ou produto financeiro que mantém saldos em várias moedas? → A: Cada conta financeira que recebe eventos possui exatamente uma moeda. Um produto multimoeda é representado por um agrupador comum sem saldo e por uma conta financeira separada para cada moeda.
- Q: Como corrigir o saldo de abertura depois que a conta já possui eventos financeiros? → A: O saldo de abertura pode ser corrigido diretamente apenas antes do primeiro evento financeiro efetivado. Depois disso ele fica bloqueado, e qualquer correção deve ser registrada como lançamento financeiro comum, usando categoria apropriada, sem tipo privilegiado de ajuste de caixa.
- Q: Um evento financeiro pode deixar a conta com saldo negativo? → A: Sim. O evento deve ser registrado e o saldo negativo sinalizado para investigação, sem bloqueio baseado apenas no saldo ou em limite de crédito informado.
- Q: Como classificar contas sem misturar natureza, instituição, produto, liquidez e comportamento, especialmente para carteiras e investimentos? → A: A conta possui natureza monetária canônica e classificações independentes de custodiante, produto, disponibilidade e capacidades. Carteira somente é conta quando mantém saldo próprio; investimentos são posições separadas, e apenas seu saldo monetário de liquidação pertence a esta feature.
- Q: A titularidade jurídica limita quais contas financeiras podem ser cadastradas? → A: Não. Usuário autorizado possui autonomia para cadastrar e controlar ou acompanhar qualquer conta no tenant; titularidade jurídica e comprovação de propriedade não condicionam o cadastro nem os acessos internos.

## User Scenarios & Testing

### User Story 1 - Cadastrar uma conta financeira (Priority: P1)

Um usuário autorizado cadastra uma conta que o tenant deseja controlar ou acompanhar, escolhe sua natureza e moeda, informa o saldo existente em uma data de referência e, quando aplicável, identifica custodiante e produto.

**Why this priority**: Sem uma conta financeira não existe destino consistente para registrar pagamentos, recebimentos, transferências ou saldos.

**Independent Test**: Cadastrar um caixa com nome, moeda, data e saldo de abertura, consultá-lo no tenant atual e comprovar que não existe qualquer visibilidade cruzada.

**Acceptance Scenarios**:

1. **Given** um usuário autorizado no tenant ativo, **When** cadastra uma conta com os dados mínimos válidos, **Then** o sistema cria uma identidade financeira exclusivamente daquele tenant.
2. **Given** duas contas com nomes semelhantes, **When** o usuário confirma o segundo cadastro, **Then** o sistema permite a distinção por identidade e demais atributos sem fundi-las.
3. **Given** natureza, custodiante, produto ou capacidades incompatíveis entre si, **When** o usuário tenta ativar a conta, **Then** o sistema indica as inconsistências sem criar uma conta aparentemente utilizável.
4. **Given** uma conta que não pertence juridicamente ao tenant ou ao usuário, **When** usuário autorizado decide controlá-la ou acompanhá-la no tenant, **Then** o cadastro não exige comprovação de titularidade e permanece sujeito aos mesmos acessos internos e auditoria.

---

### User Story 2 - Cadastrar uma conta mantida por instituição (Priority: P1)

Um usuário autorizado registra que uma conta financeira corresponde a saldo mantido em banco, instituição de pagamento, corretora ou outro custodiante compatível, usando os identificadores aplicáveis ao país e preservando-os de forma protegida.

**Why this priority**: Contas mantidas por instituições são origens e destinos frequentes de movimentações e precisam estar prontas para futuras conciliações e integrações sem serem confundidas com dados de pagamento de contrapartes, instrumentos ou posições de investimento.

**Independent Test**: Cadastrar uma conta bancária brasileira, uma conta de pagamento e uma conta monetária de liquidação em corretora, verificando campos condicionais, mascaramento e ausência de vínculo automático com dados de pagamento de pessoas ou posições de investimento.

**Acceptance Scenarios**:

1. **Given** uma conta custodiada por instituição, **When** o usuário informa categoria da instituição, país, produto e identificadores válidos, **Then** o sistema registra apenas os campos aplicáveis ao esquema escolhido.
2. **Given** uma conta internacional sem agência ou dígito brasileiro, **When** é cadastrada com identificadores próprios de seu país, **Then** campos brasileiros inaplicáveis não são exigidos.
3. **Given** usuário sem autorização para identificadores institucionais completos, **When** consulta a conta, **Then** visualiza identificação suficiente para distingui-la sem exposição integral.

---

### User Story 3 - Consultar contas e saldos (Priority: P1)

Um usuário autorizado consulta as contas ativas, seus saldos em uma data e o total compatível, podendo filtrar por natureza, custodiante, produto, capacidade, moeda, grupo e estado.

**Why this priority**: O cadastro somente produz valor operacional quando permite entender onde os recursos estão e quais contas podem receber novos lançamentos.

**Independent Test**: Consultar contas de moedas e naturezas diferentes, conferir os saldos explicáveis e impedir soma enganosa entre moedas distintas.

**Acceptance Scenarios**:

1. **Given** contas ativas com saldos registrados na mesma moeda, **When** o usuário consulta o resumo, **Then** recebe saldos individuais e total compatível para a data solicitada.
2. **Given** contas em moedas diferentes, **When** o resumo é exibido sem taxa de conversão definida, **Then** os valores permanecem separados por moeda e não são somados como equivalentes.
3. **Given** um usuário autorizado a ver contas, mas não saldos, **When** abre a listagem, **Then** a existência das contas pode ser apresentada sem revelar valores financeiros.

---

### User Story 4 - Organizar o diretório financeiro (Priority: P2)

Um usuário autorizado organiza contas em grupos hierárquicos puramente organizacionais, aplica rótulos e ordenação e mantém contas distintas mesmo quando pertencem à mesma instituição.

**Why this priority**: Pessoas e empresas podem controlar muitos caixas, bancos, filiais, carteiras e reservas; uma lista plana se torna difícil de navegar sem justificar contas artificiais.

**Independent Test**: Criar uma hierarquia de grupos, mover contas entre grupos e comprovar que agrupamento não altera saldos, moeda, histórico nem autorização.

**Acceptance Scenarios**:

1. **Given** grupos financeiros existentes, **When** uma conta é movida entre eles, **Then** somente sua organização muda e nenhum saldo é afetado.
2. **Given** um grupo com contas de várias moedas, **When** o usuário consulta seus totais, **Then** os resultados permanecem separados por moeda.
3. **Given** uma tentativa de criar ciclo entre grupos, **When** a alteração é confirmada, **Then** o sistema rejeita a estrutura inválida.

---

### User Story 5 - Desativar e encerrar uma conta (Priority: P2)

Um usuário autorizado retira temporariamente uma conta das novas operações ou registra seu encerramento definitivo, preservando saldos e histórico.

**Why this priority**: Contas deixam de ser usadas ou são encerradas pelas instituições, mas seus lançamentos precisam continuar explicando relatórios passados.

**Independent Test**: Desativar, reativar e encerrar contas, verificando disponibilidade para novas operações, saldo exigido e permanência em consultas históricas.

**Acceptance Scenarios**:

1. **Given** uma conta ativa, **When** é desativada por usuário autorizado, **Then** deixa de aparecer por padrão em novas operações e pode ser reativada sem perder histórico.
2. **Given** uma conta com saldo diferente de zero, **When** o usuário tenta encerrá-la, **Then** o sistema exige regularização explícita antes do encerramento.
3. **Given** uma conta com saldo zero, **When** é encerrada com data e motivo, **Then** permanece consultável historicamente e não aceita novos eventos financeiros comuns.

### Edge Cases

- Conta criada antes do início do controle no sistema.
- Saldo de abertura negativo.
- Conta bancária sem agência ou dígito verificador.
- Conta bancária internacional com IBAN, BIC/SWIFT ou código de roteamento.
- Conta real que mantém recursos em várias moedas.
- Duas contas com o mesmo nome ou os mesmos últimos dígitos.
- Conta conjunta ou legalmente mantida por terceiro, mas controlada pelo tenant.
- Conta de pagamento mantida por instituição que não é banco.
- Produto apresentado como carteira digital que apenas instrumentaliza outra conta e não mantém saldo próprio.
- Conta em corretora que combina saldo monetário de liquidação e posições de investimento na apresentação externa.
- Aplicação com liquidez imediata cujo valor depende de quantidade, cotação ou valorização.
- Caixa físico com saldo negativo por inconsistência operacional.
- Instituição financeira desativada no catálogo global.
- Moeda que altera sua quantidade aceita de casas decimais.
- Conta desativada que ainda recebe evento por integração futura.
- Correção do saldo de abertura depois de existirem lançamentos.
- Encerramento com saldo residual ou eventos futuros pendentes.
- Operações concorrentes alteram estado, grupo ou saldo de abertura.
- Usuário autorizado a consultar cadastro, mas não saldo ou identificadores institucionais.

## Requirements

### Conceitos e Fronteiras

- **FR-FAC-BOUND-001**: Toda conta financeira DEVE pertencer exclusivamente a um tenant e somente poderá ser criada, consultada ou alterada em contexto válido desse tenant.
- **FR-FAC-BOUND-002**: Conta financeira DEVE representar um repositório monetário controlado ou acompanhado pelo tenant e apto a receber eventos financeiros.
- **FR-FAC-BOUND-003**: Conta financeira NÃO DEVE ser confundida com dado de pagamento de pessoa, cartão, conta a pagar ou receber, categoria, centro de custo ou conta do plano contábil.
- **FR-FAC-BOUND-004**: Cadastrar conta financeira NÃO DEVE criar lançamentos comuns, integração bancária, autorização externa, participação ou permissão.
- **FR-FAC-BOUND-005**: A identidade da conta DEVE ser imutável e independente de nome, instituição, número bancário, grupo ou estado.
- **FR-FAC-BOUND-006**: A classificação futura do tenant como pessoa física ou jurídica NÃO DEVE ser requisito para cadastrar conta financeira.
- **FR-FAC-BOUND-007**: Titularidade legal, quando informada como dado descritivo, NÃO DEVE presumir associação entre tenant, usuário, participante ou pessoa do diretório nem determinar autorização interna.
- **FR-FAC-BOUND-008**: Instrumento de pagamento, canal de acesso, posição de investimento, linha de extrato externo, conexão bancária, correspondência de conciliação, diário e conta contábil DEVEM possuir identidades e ciclos de vida próprios e NÃO DEVEM ser incorporados à identidade da conta financeira.
- **FR-FAC-BOUND-009**: Um produto somente DEVE ser representado por conta financeira quando mantiver saldo monetário independente, identificável e apto a receber eventos; produto que apenas movimenta ou apresenta saldo mantido em outra conta NÃO DEVE criar uma conta duplicada.
- **FR-FAC-BOUND-010**: Associações futuras da conta com instrumentos, integrações, diários ou contas contábeis DEVEM preservar a identidade da conta e possuir vigência e histórico próprios quando sua alteração puder reinterpretar operações.
- **FR-FAC-BOUND-011**: Usuário autorizado DEVE poder cadastrar qualquer conta monetária que decida controlar ou acompanhar no tenant, independentemente de titularidade jurídica, vínculo com pessoa cadastrada ou comprovação de propriedade.
- **FR-FAC-BOUND-012**: O sistema NÃO DEVE validar propriedade, mandato ou legitimidade jurídica como condição do cadastro; a responsabilidade pela decisão de controlar ou acompanhar a conta pertence ao usuário autorizado e DEVE ser atribuível por auditoria.
- **FR-FAC-BOUND-013**: Autorização interna para consultar ou operar a conta DEVE decorrer exclusivamente dos grupos e chaves do tenant; consentimento, credencial ou autorização exigidos por custodiante externo pertencem à futura feature de conectividade e NÃO DEVEM ser presumidos pelo cadastro.

### Natureza e Classificação da Conta

- **FR-FAC-TYPE-001**: Toda conta DEVE possuir uma natureza monetária canônica fornecida pelo sistema ou por módulo, com semântica, dados mínimos e comportamentos compatíveis.
- **FR-FAC-TYPE-002**: A primeira versão DEVE suportar, no mínimo, as naturezas caixa físico, conta de depósito, conta de pagamento ou valor monetário armazenado, conta monetária de liquidação ou custódia e outra conta monetária controlada.
- **FR-FAC-TYPE-003**: Natureza, categoria do custodiante, produto ou subtipo, disponibilidade e capacidades DEVEM ser classificações independentes; o sistema NÃO DEVE codificar suas combinações em um único tipo funcional.
- **FR-FAC-TYPE-004**: Tenant NÃO DEVE criar naturezas ou capacidades funcionais arbitrárias; nomes, rótulos, produtos informados e grupos personalizados NÃO alteram a semântica canônica.
- **FR-FAC-TYPE-005**: Alterar a natureza depois da ativação DEVE ser impedido quando puder reinterpretar dados ou histórico; o usuário deverá encerrar a conta e criar outra quando a natureza tiver mudado.
- **FR-FAC-TYPE-006**: Cartão de crédito, cartão de débito, cheque, chave de endereçamento, token e demais instrumentos ou canais de pagamento NÃO DEVEM ser tratados como simples natureza de conta nesta feature.
- **FR-FAC-TYPE-007**: Custodiante DEVE poder ser classificado, quando aplicável, como banco, instituição de pagamento, corretora, custodiante especializado ou outra categoria canônica, sem presumir que toda conta institucional seja bancária.
- **FR-FAC-TYPE-008**: Produto ou subtipo DEVE poder distinguir corrente, poupança, pagamento, salário, liquidação e outros produtos canônicos ou declarativos compatíveis, sem definir sozinho o comportamento da conta.
- **FR-FAC-TYPE-009**: Carteira digital, aplicativo ou plataforma somente DEVE originar conta financeira quando mantiver saldo monetário próprio; quando apenas acessar, apresentar ou movimentar outra conta, DEVE ser modelado futuramente como instrumento, canal ou integração.
- **FR-FAC-TYPE-010**: Conta mantida em corretora ou plataforma de investimentos DEVE representar nesta feature somente o saldo monetário de liquidação ou custódia, usado para depósitos, aplicações, resgates e transferências.
- **FR-FAC-TYPE-011**: Ativo ou posição de investimento cujo valor dependa de quantidade, cotação, índice, valorização, vencimento ou evento patrimonial NÃO DEVE ser tratado como saldo de conta financeira, ainda que possua liquidez imediata.
- **FR-FAC-TYPE-012**: Capacidade de receber, pagar, transferir, contar caixa, importar extrato, conciliar ou conectar-se externamente DEVE possuir semântica canônica fornecida pelo sistema ou módulo e ser habilitada somente quando compatível com natureza, produto e custodiante.
- **FR-FAC-TYPE-013**: Alteração de custodiante, produto ou capacidade DEVE preservar histórico e vigência quando afetar a interpretação ou a disponibilidade da conta para módulos consumidores.

### Moeda e Precisão

- **FR-FAC-CUR-001**: Toda conta DEVE declarar moeda e aplicar a precisão monetária definida para ela em saldos e futuros eventos.
- **FR-FAC-CUR-002**: Cada conta financeira que recebe eventos DEVE possuir exatamente uma moeda e um único saldo nessa moeda.
- **FR-FAC-CUR-003**: Valores de moedas diferentes NÃO DEVEM ser somados ou comparados como equivalentes sem taxa, data, fonte e política de conversão explicitamente definidas por funcionalidade própria.
- **FR-FAC-CUR-004**: A moeda da conta NÃO DEVE ser alterada depois da ativação; correção exige conta substituta ou procedimento excepcional auditado que não reinterprete eventos existentes.
- **FR-FAC-CUR-005**: Cálculos DEVEM preservar precisão determinística e NÃO DEVEM introduzir arredondamentos silenciosos incompatíveis com a moeda.
- **FR-FAC-CUR-006**: Produto financeiro multimoeda DEVE ser representado por um agrupador comum sem saldo e por uma conta financeira separada para cada moeda controlada.
- **FR-FAC-CUR-007**: Um agrupador multimoeda DEVE possuir no máximo uma conta ativa para cada moeda dentro do mesmo produto, preservando contas anteriores encerradas no histórico.
- **FR-FAC-CUR-008**: Agrupar contas de moedas diferentes NÃO DEVE criar conversão, transferência, saldo consolidado nem equivalência automática entre seus valores.

### Saldo de Abertura e Saldo Atual

- **FR-FAC-OPEN-001**: Toda conta DEVE possuir um saldo de abertura, inclusive zero, e uma data de referência que delimite o início do controle no sistema.
- **FR-FAC-OPEN-002**: O saldo de abertura DEVE aceitar valor positivo, zero ou negativo e registrar origem, responsável e instante da declaração.
- **FR-FAC-OPEN-003**: A data de abertura no sistema PODERÁ ser diferente da data real de abertura na instituição e DEVE representar claramente o início do período controlado.
- **FR-FAC-OPEN-004**: O saldo de abertura PODERÁ ser corrigido diretamente somente enquanto a conta não possuir outro evento financeiro efetivado; a correção DEVE exigir autorização, motivo e auditoria.
- **FR-FAC-OPEN-005**: Depois do primeiro evento financeiro efetivado, o saldo de abertura DEVE ficar bloqueado e NÃO DEVE ser reescrito, nem mesmo por rotinas administrativas ou de conciliação.
- **FR-FAC-OPEN-006**: Qualquer correção posterior ao bloqueio DEVE ser registrada como lançamento financeiro comum, com data, valor, categoria, ator e motivo, conforme os requisitos da futura feature de lançamentos.
- **FR-FAC-BAL-001**: O saldo atual DEVE ser explicável pelo saldo de abertura e por todos os eventos financeiros efetivados até a data consultada.
- **FR-FAC-BAL-002**: Usuário NÃO DEVE editar diretamente o saldo atual.
- **FR-FAC-BAL-003**: Eventos previstos, pendentes ou não efetivados NÃO DEVEM alterar o saldo atual e deverão compor projeções separadas em feature própria.
- **FR-FAC-BAL-004**: Totais DEVEM ser apresentados separadamente por moeda enquanto não houver conversão explicitamente solicitada e definida.
- **FR-FAC-BAL-005**: Limite de crédito, cheque especial ou valor disponível informado pela instituição NÃO DEVE ser incorporado ao saldo próprio da conta.
- **FR-FAC-BAL-006**: Evento financeiro autorizado DEVE poder ser efetivado mesmo quando resultar em saldo negativo; o sistema NÃO DEVE rejeitá-lo com base apenas no saldo atual ou em limite de crédito informado.
- **FR-FAC-BAL-007**: Consulta de saldo em data passada DEVE utilizar somente abertura e eventos efetivados até aquela data, sem reescrever o resultado por alterações cadastrais posteriores.
- **FR-FAC-BAL-008**: Qualquer valor disponível que inclua limite de crédito DEVE ser apresentado separadamente do saldo próprio e identificado como informativo.
- **FR-FAC-BAL-009**: O sistema NÃO DEVE possuir tipo privilegiado de lançamento de correção de caixa nem permitir mutação direta do saldo atual por fora do fluxo normal de lançamentos.
- **FR-FAC-BAL-010**: Uma futura funcionalidade de contagem ou conciliação PODERÁ calcular e propor a diferença, mas sua confirmação DEVE gerar lançamento financeiro comum e respeitar categoria, autorização e auditoria.
- **FR-FAC-BAL-011**: Saldo negativo DEVE ser apresentado de forma inequívoca e sinalizado para investigação, sem alterar automaticamente lançamentos, abertura, limite ou estado da conta.
- **FR-FAC-BAL-012**: Saldo interno efetivado, saldo conciliado, saldo do extrato externo, saldo disponível informado pelo custodiante, valor bloqueado, valor pendente, saldo projetado, limite e saldo contábil DEVEM ser conceitos distintos e NÃO DEVEM compartilhar um único campo mutável.
- **FR-FAC-BAL-013**: Esta feature DEVE manter somente abertura e saldo interno derivado; as demais visões de saldo DEVEM declarar fonte, instante de referência e regra de cálculo nas respectivas features.
- **FR-FAC-BAL-014**: Valor de mercado, custo de aquisição e rendimento de posições de investimento NÃO DEVEM compor o saldo da conta monetária de liquidação antes de evento monetário efetivado, como crédito de rendimento, venda ou resgate liquidado.

### Custodiantes, Instituições e Identificadores

- **FR-FAC-BANK-001**: Conta mantida por custodiante DEVE identificar país, categoria do custodiante, instituição, produto ou subtipo e esquema de identificação aplicável.
- **FR-FAC-BANK-002**: Bancos, instituições de pagamento, corretoras e demais custodiantes conhecidos DEVEM poder ser selecionados por código e nome; instituição ausente do catálogo DEVE poder ser informada de forma controlada com nome, categoria e país.
- **FR-FAC-BANK-003**: Agência, dígito de agência, número da conta e dígito da conta DEVEM ser exigidos somente quando aplicáveis ao esquema escolhido.
- **FR-FAC-BANK-004**: Contas internacionais DEVEM admitir identificadores como IBAN, BIC/SWIFT e códigos de roteamento sem exigir estrutura brasileira.
- **FR-FAC-BANK-005**: Identificadores DEVEM ser normalizados e validados segundo o esquema, preservando apresentação apropriada.
- **FR-FAC-BANK-006**: Conta semelhante já existente no tenant DEVE gerar alerta antes da confirmação e NÃO DEVE provocar fusão automática.
- **FR-FAC-BANK-007**: Desativação da instituição no catálogo NÃO DEVE apagar nem invalidar o histórico da conta, mas poderá impedir novos cadastros com referência obsoleta.
- **FR-FAC-BANK-008**: Identificadores institucionais próprios da conta financeira NÃO DEVEM criar nem atualizar automaticamente dados de pagamento de qualquer pessoa.
- **FR-FAC-BANK-009**: Integração, consentimento, credencial, token, extrato e estado de conexão com custodiante NÃO fazem parte deste cadastro-base.
- **FR-FAC-BANK-010**: Identificador externo da conta DEVE possuir esquema, valor protegido, país, custodiante e vigência próprios, sem ser utilizado como identidade interna da conta.
- **FR-FAC-BANK-011**: Conexões futuras DEVEM poder associar uma autorização externa a uma ou várias contas sem incorporar credenciais, consentimento ou estado de sincronização ao cadastro essencial da conta.

### Identificação e Organização

- **FR-FAC-ORG-001**: Toda conta DEVE possuir nome apresentável e PODERÁ possuir código interno, descrição, rótulos e ordem de exibição.
- **FR-FAC-ORG-002**: Nomes iguais ou semelhantes DEVEM gerar alerta e permanecer permitidos quando outros dados distinguirem as contas.
- **FR-FAC-ORG-003**: Usuário autorizado DEVE poder criar grupos financeiros hierárquicos exclusivamente organizacionais e associar cada conta a no máximo um grupo direto.
- **FR-FAC-ORG-004**: Grupo DEVE poder conter contas e outros grupos, mas NÃO DEVE receber lançamentos, possuir moeda própria nem manter saldo independente.
- **FR-FAC-ORG-005**: Hierarquia NÃO DEVE admitir ciclos e DEVE preservar identidade e histórico quando conta ou grupo for movido.
- **FR-FAC-ORG-006**: Totais de grupo DEVEM ser derivados das contas elegíveis e permanecer separados por moeda.
- **FR-FAC-ORG-007**: Excluir grupo DEVE exigir que seus filhos sejam realocados ou que o usuário confirme uma realocação válida, sem excluir contas.
- **FR-FAC-ORG-008**: Preferências de conta para pagamentos, recebimentos ou módulos específicos pertencem às funcionalidades consumidoras e NÃO DEVEM ser presumidas pelo agrupamento.
- **FR-FAC-ORG-009**: Agrupador multimoeda DEVE poder identificar a instituição e a referência comum do produto, enquanto identificadores de roteamento e saldo específicos permanecem nas contas de cada moeda.

### Ciclo de Vida

- **FR-FAC-LIFE-001**: Conta DEVE possuir estados ativa, inativa e encerrada.
- **FR-FAC-LIFE-002**: Conta ativa DEVE estar disponível para novas operações compatíveis; conta inativa NÃO DEVE aparecer por padrão, mas PODERÁ ser reativada.
- **FR-FAC-LIFE-003**: Conta encerrada NÃO DEVE aceitar novos eventos financeiros comuns e DEVE permanecer em consultas históricas.
- **FR-FAC-LIFE-004**: Encerramento DEVE exigir saldo próprio zero, ausência de eventos futuros incompatíveis, data, motivo, autorização e auditoria.
- **FR-FAC-LIFE-005**: Reabertura de conta encerrada somente PODERÁ ocorrer como correção excepcional, com autorização específica, motivo e auditoria, sem remover o evento de encerramento.
- **FR-FAC-LIFE-006**: Conta nunca utilizada, sem saldo diferente de zero e sem referência PODERÁ ser excluída definitivamente por usuário autorizado; caso contrário, somente poderá ser inativada ou encerrada.
- **FR-FAC-LIFE-007**: Alterações em nome, descrição, rótulos, grupo e ordem NÃO DEVEM reescrever saldos ou referências históricas.
- **FR-FAC-LIFE-008**: Mudança de identificador externo depois de existirem eventos DEVE preservar o valor anterior e sua vigência; quando representar outra conta real, DEVE exigir encerramento e novo cadastro.

### Pesquisa e Uso por Outros Módulos

- **FR-FAC-USE-001**: Usuário autorizado DEVE poder pesquisar e filtrar contas por nome, código, natureza, custodiante, produto, capacidade, moeda, grupo e estado.
- **FR-FAC-USE-002**: Seleções comuns DEVEM apresentar somente contas ativas e compatíveis com moeda, natureza, capacidades e operação exigidas pelo módulo consumidor.
- **FR-FAC-USE-003**: Conta inativa ou encerrada DEVE permanecer identificável em operações e relatórios históricos autorizados.
- **FR-FAC-USE-004**: Todo módulo consumidor DEVE referenciar a identidade imutável da conta e validar o tenant antes de criar ou consultar eventos.
- **FR-FAC-USE-005**: Módulos consumidores DEVEM preservar moeda e precisão da conta e rejeitar evento monetário incompatível.
- **FR-FAC-USE-006**: Importação ou integração futura NÃO DEVE alterar natureza, custodiante, produto, moeda, abertura ou identificadores críticos silenciosamente; divergências DEVEM ser reconciliadas explicitamente.
- **FR-FAC-USE-007**: Consultas de saldo DEVEM declarar a data de referência ou usar um instante atual claramente identificado.
- **FR-FAC-USE-008**: Lançamentos financeiros e suas categorias DEVERÃO preservar classificação suficiente para futura integração contábil; regras de partidas dobradas, contrapartidas, débito, crédito e rateio pertencem à especificação contábil e NÃO DEVEM ser presumidas por esta feature.
- **FR-FAC-USE-009**: Linha de extrato externo e lançamento financeiro interno DEVEM permanecer registros distintos; importação NÃO DEVE transformar uma linha externa em fato interno definitivo sem regra explícita da feature consumidora.
- **FR-FAC-USE-010**: Conciliação futura DEVE admitir correspondências auditáveis de um para um, um para muitos e muitos para um entre linhas externas e lançamentos internos, sem modificar suas identidades originais.
- **FR-FAC-USE-011**: Associação futura com diário ou conta contábil DEVE ser explícita e versionada; a conta financeira NÃO DEVE herdar identidade nem comportamento contábil apenas por possuir categoria ou custodiante.
- **FR-FAC-USE-012**: Lançamento futuro em moeda diferente da moeda funcional do tenant DEVERÁ preservar valor e moeda originais, valor convertido, taxa, fonte, data e arredondamento aplicáveis, sem alterar a regra de uma moeda por conta.

### Autorização, Proteção e Auditoria

- **FR-FAC-SEC-001**: Acesso a contas financeiras DEVE ser negado por padrão e liberado por chaves específicas do tenant.
- **FR-FAC-SEC-002**: Consultar cadastro, consultar saldo, consultar identificadores externos completos, criar, alterar, corrigir abertura, organizar, inativar, encerrar, reabrir e excluir DEVEM poder ser autorizados separadamente.
- **FR-FAC-SEC-003**: Interfaces e operações internas DEVEM aplicar o mesmo contrato de autorização e isolamento.
- **FR-FAC-SEC-004**: Identificadores externos DEVEM ser mascarados por padrão em telas, pesquisas, mensagens e exportações.
- **FR-FAC-SEC-005**: Revelação ou exportação de identificadores completos DEVE exigir autorização específica e auditoria.
- **FR-FAC-SEC-006**: Saldo, limite, identificadores externos e demais informações financeiras NÃO DEVEM aparecer em logs, erros ou métricas sem necessidade legítima e proteção adequada.
- **FR-FAC-SEC-007**: Criação, alteração, correção de abertura, revelação, mudança de estado, reabertura, exclusão e tentativa sensível negada DEVEM registrar ator, tenant, instante, origem, motivo, resultado e efeito.
- **FR-FAC-SEC-008**: Usuário sem autorização de saldo ou identificadores externos NÃO DEVE inferir seus valores por totais, filtros, mensagens de erro ou auditoria.

### Experiência e Acessibilidade

- **FR-FAC-UX-001**: A interface DEVE explicar a diferença entre conta financeira do tenant e dado de pagamento de contraparte.
- **FR-FAC-UX-002**: O formulário DEVE adaptar campos à natureza, custodiante, produto, país e esquema sem exigir informações institucionais para caixas próprios.
- **FR-FAC-UX-003**: Antes da ativação, o usuário DEVE revisar natureza, custodiante e produto aplicáveis, moeda, data, saldo de abertura e possíveis duplicidades.
- **FR-FAC-UX-004**: Listagens DEVEM distinguir natureza, custodiante ou produto relevante, moeda, grupo e estado e DEVEM mascarar identificadores institucionais por padrão.
- **FR-FAC-UX-008**: Ao cadastrar carteira, plataforma ou produto de investimento, a interface DEVE explicar se o usuário está registrando saldo monetário próprio, instrumento de acesso ou posição de ativo e encaminhar somente o primeiro para esta feature.
- **FR-FAC-UX-005**: Saldos negativos, conta inativa e conta encerrada DEVEM ser perceptíveis sem depender exclusivamente de cor.
- **FR-FAC-UX-006**: Jornadas principais DEVEM ser realizáveis por teclado e tecnologias assistivas, com erros associados aos campos correspondentes.
- **FR-FAC-UX-007**: Operações de correção, inativação, encerramento e reabertura DEVEM apresentar seus efeitos antes da confirmação.

### Decisões de Infraestrutura Auditáveis

> Decisões de infraestrutura: não há agendamento nem refresh de token externo próprios desta feature. Saldos mudam somente por operações transacionais explícitas; integrações futuras deverão definir suas próprias políticas.

- **FR-FAC-INFRA-KEY**: A proteção criptográfica de identificadores externos persistidos DEVE admitir identificação da versão da chave, rotação controlada e leitura segura durante a transição.
- **FR-FAC-INFRA-IDEMP**: Repetição técnica da mesma solicitação confirmada DEVE produzir no máximo uma conta, alteração de abertura ou mudança de estado.
- **FR-FAC-INFRA-LOCK**: Operações concorrentes DEVEM preservar abertura, saldo, moeda, estado, hierarquia e histórico determinísticos em todas as instâncias da aplicação.
- **FR-FAC-INFRA-BACKUP**: Contas, aberturas, estados, identificadores protegidos e auditorias DEVEM participar dos backups e restaurações do tenant segundo `tenant-data-governance`.

### Key Entities

- **Conta Financeira**: identidade estável de um saldo monetário independente do tenant, com natureza, moeda, estado, organização e contratos de saldo.
- **Natureza da Conta**: semântica monetária canônica que define o que a conta representa, seus dados mínimos e compatibilidades, sem incorporar custodiante, produto ou canal.
- **Custodiante da Conta**: banco, instituição de pagamento, corretora, custodiante especializado ou outra instituição que mantém o saldo, quando aplicável.
- **Produto da Conta**: classificação complementar, como corrente, poupança, pagamento, salário ou liquidação, sem produzir saldo independente da conta financeira.
- **Capacidade da Conta**: comportamento canônico disponibilizado por módulo e compatível com a natureza e o produto, como receber, pagar, transferir, contar caixa, importar ou conciliar.
- **Saldo de Abertura**: valor e data que iniciam o período controlado, com origem e autoria.
- **Identificador Externo da Conta**: identificação nacional ou internacional da conta junto ao custodiante, protegida, versionada e condicionada ao esquema aplicável.
- **Grupo Financeiro**: nó hierárquico organizacional sem lançamentos ou saldo próprio, que também pode representar o vínculo comum de um produto multimoeda.
- **Estado da Conta**: situação ativa, inativa ou encerrada, com período e eventos auditáveis de transição.
- **Evento Financeiro**: alteração monetária futura que afeta uma conta de forma rastreável; seu cadastro e regras pertencem a especificação posterior.
- **Linha de Extrato Externo**: observação importada ou sincronizada de fonte externa, distinta do evento financeiro interno e pertencente à futura feature de extratos e conciliação.
- **Posição de Investimento**: quantidade ou direito sobre ativo cujo valor depende de cotação, índice, valorização ou evento patrimonial; não integra esta feature.
- **Evento de Auditoria da Conta**: evidência de alteração cadastral, acesso sensível ou mudança de estado.

## Success Criteria

### Measurable Outcomes

- **SC-FAC-001**: Em 100% dos testes cruzados, contas, grupos, saldos e identificadores de um tenant não são consultados, alterados nem confirmados em outro tenant.
- **SC-FAC-002**: Em 100% dos testes, cadastrar conta não cria lançamento comum, cartão, dado de pagamento, integração ou permissão.
- **SC-FAC-003**: Usuários autorizados cadastram uma conta simples com abertura em até 2 minutos durante testes de usabilidade.
- **SC-FAC-004**: Em 100% das consultas testadas, o saldo apresentado é reconciliável com abertura e eventos efetivados até a data solicitada.
- **SC-FAC-005**: Em 100% dos resumos sem conversão definida, valores de moedas diferentes permanecem separados.
- **SC-FAC-006**: Em 100% das listagens comuns, identificadores externos permanecem mascarados e saldos respeitam autorização específica.
- **SC-FAC-007**: Em 100% dos testes, grupo hierárquico não recebe evento nem mantém saldo próprio, e ciclos são rejeitados.
- **SC-FAC-008**: Em 100% dos testes, conta inativa ou encerrada deixa de aparecer em novas operações comuns e permanece identificável historicamente.
- **SC-FAC-009**: Em 100% dos encerramentos testados, a conta possui saldo zero, data, motivo e auditoria.
- **SC-FAC-010**: Pelo menos 95% das consultas comuns de contas e saldos retornam resultado utilizável em até 2 segundos nas condições operacionais definidas para a primeira versão.
- **SC-FAC-011**: Em 100% dos testes concorrentes e de repetição técnica, cada operação confirmada produz no máximo um efeito e preserva saldo, estado e hierarquia determinísticos.
- **SC-FAC-012**: Logs, mensagens de erro e métricas examinados nos testes não contêm saldos nem identificadores externos completos sem finalidade e proteção aprovadas.
- **SC-FAC-013**: Todas as jornadas principais podem ser concluídas apenas por teclado e sem bloqueios críticos para tecnologias assistivas.
- **SC-FAC-014**: Em 100% dos testes multimoeda, cada conta mantém uma única moeda, o agrupador não possui saldo e não ocorre soma ou conversão automática entre as contas.
- **SC-FAC-015**: Em 100% dos testes, depois do primeiro evento financeiro efetivado, a edição direta do saldo de abertura é rejeitada e qualquer correção afeta o saldo somente por lançamento financeiro comum categorizado.
- **SC-FAC-016**: Em 100% dos testes, evento financeiro autorizado que produza saldo negativo pode ser registrado, e o resultado é sinalizado sem incorporar limite de crédito ao saldo próprio.
- **SC-FAC-017**: Em 100% dos testes de classificação, natureza, custodiante, produto e capacidades permanecem dimensões independentes e combinações incompatíveis são rejeitadas.
- **SC-FAC-018**: Em 100% dos testes, carteira ou plataforma sem saldo monetário próprio não cria conta financeira duplicada.
- **SC-FAC-019**: Em 100% dos testes, valorização ou cotação de posição de investimento não altera o saldo da conta monetária de liquidação sem evento monetário efetivado.
- **SC-FAC-020**: Em 100% dos testes de fronteira, instrumentos, conexões, linhas de extrato, conciliações e mapeamentos contábeis mantêm identidade independente da conta financeira.
- **SC-FAC-021**: Em 100% dos testes de cadastro, ausência de titularidade jurídica, vínculo com pessoa ou comprovação de propriedade não impede usuário autorizado de criar a conta, e nenhum usuário sem acesso do tenant passa a consultá-la ou operá-la.

## Fora do Escopo

- Lançamentos de crédito e débito, transferências e ajustes financeiros comuns.
- Contas a pagar, contas a receber, parcelas e recorrências.
- Cartões, compras, limites, faturas e pagamentos de fatura.
- Categorias, centros de custo e rateios.
- Extratos, conciliação, OFX, Open Finance e integrações bancárias.
- Cotações, conversões cambiais e consolidação entre moedas.
- Ativos, posições, custódia patrimonial, compra, venda, rendimento, valorização, amortização, vencimento e tributação de investimentos.
- Instrumentos e canais de pagamento, como cartões, cheques, tokens, chaves de endereçamento e carteiras que não mantenham saldo próprio.
- Sessões de caixa, abertura operacional, troca de operador, contagem, sangria, suprimento e fechamento de caixa.
- Fluxo de caixa, projeções e relatórios financeiros avançados.
- Dados de pagamento de pessoas e execução de pagamentos.
- Plano de contas contábil, diários, mapeamentos contábeis, escrituração, fiscal e patrimônio não financeiro.
