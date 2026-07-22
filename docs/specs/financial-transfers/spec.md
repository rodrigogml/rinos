# Feature Specification: Transferências Financeiras

**Feature**: `financial-transfers`
**Created**: 2026-07-22
**Status**: Clarified — pronta para planejamento

## Escopo

Esta feature permite movimentar valor entre duas contas financeiras do mesmo tenant por uma operação única e rastreável. A transferência coordena uma saída na conta de origem e uma entrada na conta de destino, mantendo as duas pontas vinculadas. Operações imediatas confirmam ambas conjuntamente; quando houver intervalo real entre envio e recebimento, a transferência permanece em trânsito depois da saída até que a entrada seja confirmada ou a saída seja compensada.

Transferências entre contas de moedas diferentes preservam os valores efetivamente retirados e recebidos e a memória explícita da conversão. A operação não representa receita, despesa, pagamento a terceiro, recebimento de cliente nem instrução enviada a banco; esses eventos pertencem às respectivas funcionalidades e podem usar transferências somente quando realmente movimentarem recursos entre contas controladas pelo tenant.

> [!IMPORTANT]
> Uma transferência confirmada não pode ser editada ou excluída. Correções preservam a operação original e criam efeitos compensatórios vinculados, respeitando o fechamento financeiro vigente.

> [!NOTE]
> Esta feature registra o fato financeiro interno. Execução bancária, agendamento externo, arquivos de remessa, consentimentos, extratos e conciliação permanecem fora deste escopo.

## Clarifications

### Session 2026-07-22

- Q: As pontas principais de uma transferência devem receber categorias e dimensões? → A: Não. Como o principal transferido não representa receita nem despesa, suas duas pontas dispensam categorias e dimensões. A exceção é estrutural e exclusiva da origem `financial-transfers`; custos adicionais permanecem fatos econômicos separados e integralmente classificados.
- Q: Como representar transferências debitadas e creditadas em datas diferentes? → A: A transferência pode permanecer `EM_TRÂNSITO`. A saída afeta a origem na data do envio e a entrada afeta o destino somente quando o recebimento é confirmado; o valor em trânsito é apresentado separadamente e não pertence ao saldo de uma conta. Transferências imediatas continuam confirmando ambas as pontas de uma vez.
- Q: Como registrar tarifas, impostos e outros custos relacionados à transferência? → A: Como lançamentos econômicos comuns, integralmente classificados e vinculados à transferência. Podem ser confirmados atomicamente com a etapa correspondente ou acrescentados posteriormente como novos fatos imutáveis quando descobertos, sem alterar nem reduzir silenciosamente o principal transferido.

## User Scenarios & Testing

### User Story 1 - Transferir entre contas da mesma moeda (Priority: P1)

Um usuário autorizado escolhe duas contas distintas da mesma moeda, informa data, valor e descrição, revisa os dois efeitos e confirma a transferência como uma única operação.

**Why this priority**: Esta é a menor jornada que movimenta recursos entre contas sem exigir dois lançamentos manuais independentes e sujeitos a inconsistência.

**Independent Test**: Confirmar uma transferência em moeda única e comprovar uma saída e uma entrada de mesmo valor, vinculadas e produzidas uma única vez.

**Acceptance Scenarios**:

1. **Given** duas contas ativas, distintas, compatíveis e do mesmo tenant e moeda, **When** usuário autorizado confirma valor válido, **Then** uma saída e uma entrada equivalentes são produzidas atomicamente.
2. **Given** origem e destino iguais, **When** a confirmação é solicitada, **Then** a operação é rejeitada sem qualquer efeito no saldo.
3. **Given** usuário autorizado somente em uma das contas, **When** tenta confirmar, **Then** a transferência é negada sem revelar saldo ou conteúdo protegido da outra conta.

---

### User Story 2 - Transferir entre moedas diferentes (Priority: P1)

Um usuário autorizado informa o valor retirado na moeda de origem, o valor recebido na moeda de destino e os elementos que explicam a conversão antes de confirmar os dois efeitos.

**Why this priority**: Contas multimoeda são representadas por saldos monetários separados, e a movimentação entre elas precisa explicar a diferença sem somar moedas incompatíveis.

**Independent Test**: Transferir entre contas de moedas diferentes e reproduzir deterministicamente os valores de ambas as pontas, taxa, convenção, fonte, referência e arredondamento.

**Acceptance Scenarios**:

1. **Given** contas de moedas diferentes, **When** os dois valores e a memória de conversão são válidos, **Then** cada conta recebe somente o efeito em sua própria moeda.
2. **Given** conversão incompleta ou matematicamente inconsistente, **When** a confirmação é solicitada, **Then** nenhum dos dois efeitos é produzido.
3. **Given** cotação de referência alterada posteriormente, **When** o histórico é consultado, **Then** os valores e a conversão originalmente confirmados permanecem inalterados.

---

### User Story 3 - Acompanhar valor em trânsito (Priority: P1)

Um usuário autorizado registra que o valor saiu da origem e acompanha a transferência até confirmar seu recebimento no destino ou compensar uma falha.

**Why this priority**: Transferências entre instituições podem levar horas ou dias, e fingir simultaneidade torna incorretos os saldos históricos e oculta recursos temporariamente fora das contas.

**Independent Test**: Confirmar somente o envio, visualizar o valor em trânsito e depois confirmar o recebimento, comprovando cada efeito na respectiva data.

**Acceptance Scenarios**:

1. **Given** transferência imediata válida, **When** o usuário confirma envio e recebimento conjuntamente, **Then** as duas pontas são produzidas atomicamente e a operação fica concluída.
2. **Given** recebimento ainda não ocorrido, **When** o usuário confirma o envio, **Then** somente a saída afeta saldo e a operação fica `EM_TRÂNSITO` com valor identificável separadamente.
3. **Given** transferência em trânsito, **When** o recebimento válido é confirmado, **Then** a entrada afeta o destino na data informada e a operação fica concluída.
4. **Given** transferência em trânsito que falhou ou foi devolvida, **When** usuário autorizado registra a compensação, **Then** a saída é neutralizada por novo fato vinculado e nenhuma entrada fictícia é criada.

---

### User Story 4 - Preparar e revisar uma transferência (Priority: P1)

Um usuário autorizado salva uma transferência em rascunho, completa os dados progressivamente e revisa contas, moedas, valores, datas e efeitos antes da confirmação.

**Why this priority**: A revisão conjunta reduz erros de conta, direção e conversão antes que dois saldos sejam afetados.

**Independent Test**: Salvar, alterar e excluir rascunho sem afetar saldos e depois confirmar uma versão completa uma única vez.

**Acceptance Scenarios**:

1. **Given** dados mínimos válidos, **When** o rascunho é salvo, **Then** nenhum saldo é alterado e os dados informados permanecem disponíveis para continuação.
2. **Given** rascunho incompleto ou com dados que perderam vigência, **When** a confirmação é solicitada, **Then** o sistema informa as pendências e não cria efeitos parciais.
3. **Given** rascunho confirmado concorrentemente, **When** solicitações repetidas são processadas, **Then** somente uma transferência e os efeitos esperados para a modalidade imediata ou em trânsito são produzidos.

---

### User Story 5 - Consultar a operação completa (Priority: P1)

Um usuário autorizado encontra a transferência por seus atributos, consulta o efeito em cada conta e navega entre a operação e os lançamentos vinculados conforme seus acessos.

**Why this priority**: Duas linhas isoladas não explicam por que o valor saiu de uma conta e entrou em outra, principalmente em conversões ou correções.

**Independent Test**: Localizar uma transferência por data, contas e valores e comprovar que a consulta explica ambas as pontas sem expor dados sem autorização.

**Acceptance Scenarios**:

1. **Given** acesso às duas contas, **When** a transferência é consultada, **Then** origem, destino, valores, moedas, datas, conversão, estado e vínculos são apresentados em conjunto.
2. **Given** acesso permitido a apenas uma ponta histórica, **When** o lançamento é consultado, **Then** o sistema informa sua origem como transferência sem revelar valores, saldo ou identificação protegida da outra conta.
3. **Given** contas com moedas diferentes, **When** a operação é apresentada, **Then** não há total ou diferença monetária enganosa sem conversão explicitamente declarada.

---

### User Story 6 - Estornar ou corrigir uma transferência (Priority: P1)

Um usuário autorizado corrige uma transferência confirmada por uma operação compensatória completa e, quando necessário, registra uma nova transferência substituta.

**Why this priority**: Corrigir somente uma ponta quebraria a rastreabilidade e tornaria os saldos incompatíveis com a intenção da operação.

**Independent Test**: Estornar uma transferência e criar substituta, comprovando a permanência do original, as duas compensações e os vínculos da cadeia de correção.

**Acceptance Scenarios**:

1. **Given** transferência confirmada e elegível, **When** usuário autorizado informa motivo e confirma estorno, **Then** os dois efeitos são compensados atomicamente sem alterar o original.
2. **Given** transferência já integralmente estornada, **When** novo estorno é solicitado, **Then** nenhum efeito duplicado é produzido.
3. **Given** uma das datas necessárias bloqueada pelo fechamento, **When** a correção é solicitada, **Then** a operação exige datas abertas e preserva as datas dos fatos originais.

---

### User Story 7 - Receber transferência originada por módulo (Priority: P2)

Um módulo autorizado solicita uma transferência como parte de outro processo e preserva sua identidade de origem, sem duplicar os efeitos em repetições técnicas.

**Why this priority**: Módulos futuros podem coordenar investimentos, caixas ou outras movimentações internas sem reconstruir regras de transferência.

**Independent Test**: Repetir o mesmo evento de origem e comprovar uma única transferência, duas pontas e vínculo estável com o processo solicitante.

**Acceptance Scenarios**:

1. **Given** evento válido do mesmo tenant, **When** solicita uma transferência, **Then** a operação e os efeitos correspondentes à modalidade imediata ou em trânsito são confirmados como uma única unidade de negócio por etapa.
2. **Given** mesma identidade de origem e mesmo conteúdo, **When** a solicitação é repetida, **Then** o resultado existente é retornado sem novos efeitos.
3. **Given** mesma identidade com conteúdo divergente, **When** a repetição é processada, **Then** ela é rejeitada e sinalizada para investigação.

---

### User Story 8 - Registrar custos relacionados (Priority: P2)

Um usuário autorizado registra tarifa, imposto ou outro custo econômico relacionado à transferência, preservando conta, data, valor, classificação e vínculo sem misturá-lo ao principal movimentado.

**Why this priority**: Custos bancários e tributários explicam diferenças reais no patrimônio e precisam permanecer pesquisáveis e classificáveis sem transformar a transferência principal em receita ou despesa.

**Independent Test**: Confirmar uma tarifa junto do envio e outra descoberta posteriormente, comprovando lançamentos separados, classificação integral, vínculos e ausência de alteração nas pontas principais.

**Acceptance Scenarios**:

1. **Given** custo conhecido na confirmação de uma etapa, **When** usuário revisa e confirma a operação, **Then** o custo e a etapa são produzidos atomicamente como fatos distintos e vinculados.
2. **Given** custo descoberto depois do envio ou recebimento, **When** usuário autorizado o registra, **Then** um novo lançamento categorizado é vinculado sem alterar a transferência nem seus efeitos anteriores.
3. **Given** custo sem categoria, dimensão obrigatória, conta, data ou autorização válida, **When** sua confirmação é solicitada, **Then** o custo é rejeitado sem modificar a transferência já existente.

### Edge Cases

- Conta de origem igual à conta de destino.
- Conta inativa, encerrada ou sem capacidade de transferência.
- Contas pertencentes a tenants diferentes.
- Usuário pode operar somente uma das contas.
- Valor zero, negativo ou com precisão incompatível com a moeda.
- Transferência que deixa a conta de origem com saldo negativo.
- Confirmação concorrente do mesmo rascunho ou evento de origem.
- Mudança de estado, moeda ou autorização entre a edição e a confirmação.
- Uma ou ambas as datas estão bloqueadas pelo fechamento financeiro.
- Transferência permanece em trânsito por tempo indefinido sem confirmação automática.
- Data de recebimento anterior à data efetiva de envio.
- Valor efetivamente recebido difere da estimativa informada no envio.
- Transferência em trânsito falha, é devolvida ou precisa trocar a conta de destino.
- Tarifa é conhecida somente depois da conclusão da transferência.
- Tarifa é debitada de conta diferente da origem ou em outra moeda.
- Repetição técnica tenta registrar novamente o mesmo custo posterior.
- Conversão cuja taxa exige convenção inversa ou arredondamento.
- Valor debitado e creditado não são matematicamente compatíveis com a memória de conversão.
- Correção solicitada depois de uma das contas ter sido encerrada.
- Dependência futura de conciliação impede estorno imediato.
- Usuário consulta uma ponta, mas não possui acesso ao cadastro ou saldo da outra conta.
- Falha depois da validação e antes da confirmação de ambas as pontas.
- Módulo de origem deixa de estar disponível depois da transferência.

## Requirements

### Conceitos e Fronteiras

- **FR-FTF-BOUND-001**: Toda transferência, rascunho, ponta, conversão, correção e origem DEVE pertencer exclusivamente a um tenant.
- **FR-FTF-BOUND-002**: Transferência DEVE representar movimentação entre exatamente duas contas financeiras distintas controladas ou acompanhadas pelo mesmo tenant.
- **FR-FTF-BOUND-003**: Transferência concluída DEVE coordenar exatamente uma saída na origem e uma entrada no destino por lançamentos financeiros vinculados à mesma operação; transferência em trânsito DEVE possuir a saída confirmada e a entrada ainda ausente.
- **FR-FTF-BOUND-004**: A feature NÃO DEVE representar pagamento a terceiro, recebimento de terceiro, compra, venda, aplicação, resgate, documento fiscal, instrução bancária, linha de extrato ou lançamento contábil.
- **FR-FTF-BOUND-005**: Criar transferência NÃO DEVE executar movimentação externa nem presumir que a instituição financeira a realizou.
- **FR-FTF-BOUND-006**: Transferência NÃO DEVE criar conta financeira, contraparte, dado de pagamento, cotação externa ou permissão.
- **FR-FTF-BOUND-007**: Limites de plano pertencem a `plans-entitlements` e NÃO DEVEM remover ou tornar inexplicável uma transferência confirmada.

### Identidade e Estado

- **FR-FTF-CORE-001**: Transferência DEVE possuir identidade técnica estável e imutável, sem exigir código sequencial ou número de negócio exposto ao usuário.
- **FR-FTF-CORE-002**: Transferência DEVE possuir estados `RASCUNHO`, `EM_TRÂNSITO`, `CONCLUÍDA` e `ESTORNADA`, preservando a cadeia de eventual substituição.
- **FR-FTF-CORE-003**: Rascunho DEVE poder ser criado, alterado e excluído sem afetar saldos.
- **FR-FTF-CORE-004**: Transferência confirmada NÃO DEVE ser editada nem excluída por usuário, administrador, módulo ou importação.
- **FR-FTF-CORE-005**: Transferência DEVE preservar descrição, observação opcional, instantes de criação e confirmação, ator ou origem e estado.
- **FR-FTF-CORE-006**: Usuário autorizado DEVE localizar transferências por contas, datas, valores, moedas, descrição, estado, origem e vínculos de correção.
- **FR-FTF-CORE-007**: Uma transferência somente DEVE passar de `RASCUNHO` para `EM_TRÂNSITO` quando a saída for confirmada, de `RASCUNHO` para `CONCLUÍDA` quando ambas as pontas forem confirmadas imediatamente e de `EM_TRÂNSITO` para `CONCLUÍDA` quando o recebimento for confirmado.
- **FR-FTF-CORE-008**: Passagem do tempo NÃO DEVE confirmar recebimento, concluir, falhar, estornar nem alterar automaticamente transferência em trânsito.

### Contas, Valores e Saldos

- **FR-FTF-VALUE-001**: Origem e destino DEVEM ser contas distintas, ativas, aptas a transferir e pertencentes ao mesmo tenant no momento da confirmação.
- **FR-FTF-VALUE-002**: A operação DEVE validar autorização explícita aplicável às duas contas imediatamente antes da confirmação.
- **FR-FTF-VALUE-003**: Cada ponta DEVE possuir valor absoluto maior que zero e respeitar a moeda e precisão da respectiva conta.
- **FR-FTF-VALUE-004**: Em contas de mesma moeda, saída e entrada DEVEM possuir exatamente o mesmo valor.
- **FR-FTF-VALUE-005**: A saída DEVE reduzir somente o saldo da origem e a entrada DEVE aumentar somente o saldo do destino.
- **FR-FTF-VALUE-006**: Saldo insuficiente NÃO DEVE bloquear a transferência por si próprio; eventual saldo negativo DEVE seguir o contrato de `financial-accounts`.
- **FR-FTF-VALUE-007**: Cada transição DEVE produzir seus efeitos uma única vez e atomicamente: confirmação imediata produz as duas pontas; envio produz somente a saída; recebimento posterior produz somente a entrada.
- **FR-FTF-VALUE-008**: Enquanto estiver `EM_TRÂNSITO`, o valor retirado DEVE ser apresentado como recurso em trânsito separado dos saldos das contas, sem criar conta financeira oculta nem entrada antecipada no destino.
- **FR-FTF-VALUE-009**: Recursos em trânsito DEVEM ser derivados das saídas confirmadas ainda sem entrada ou compensação e permanecer discriminados por moeda, sem soma enganosa entre moedas diferentes.

### Conversão entre Moedas

- **FR-FTF-FX-001**: Contas de moedas diferentes DEVEM exigir valor de origem e valor de destino explicitamente informados e revisados.
- **FR-FTF-FX-002**: A transferência DEVE preservar moedas, valores, taxa aplicada, convenção da taxa, fonte, data ou instante de referência e regra de arredondamento.
- **FR-FTF-FX-003**: A memória de conversão DEVE explicar deterministicamente a relação entre os dois valores dentro da tolerância monetária declarada.
- **FR-FTF-FX-004**: A feature NÃO DEVE consultar cotação, escolher taxa, recalcular conversão confirmada nem criar posição cambial automaticamente.
- **FR-FTF-FX-005**: Alteração posterior de cotação NÃO DEVE modificar nenhum dos dois efeitos confirmados.
- **FR-FTF-FX-006**: Diferença entre valores convertidos NÃO DEVE ser tratada como receita ou despesa implícita.
- **FR-FTF-FX-007**: Em transferência multimoeda enviada antes do recebimento, eventual valor de destino informado DEVE ser identificado como estimativa e NÃO DEVE afetar saldo.
- **FR-FTF-FX-008**: A conclusão DEVE registrar o valor efetivamente recebido e consolidar a memória de conversão; divergência em relação à estimativa DEVE ser apresentada para revisão explícita.

### Classificação e Custos

- **FR-FTF-CLASS-001**: As pontas principais originadas pela transferência NÃO DEVEM possuir parcelas de categoria nem distribuições dimensionais, pois representam mudança de custódia e não receita ou despesa.
- **FR-FTF-CLASS-002**: A operação NÃO DEVE transformar o valor principal movimentado em receita ou despesa do tenant.
- **FR-FTF-CLASS-003**: Categoria ou dimensão usada por custo adicional NÃO DEVE alterar a classificação neutra do principal transferido.
- **FR-FTF-CLASS-004**: A dispensa de classificação DEVE ser determinada exclusivamente pelo vínculo estrutural com uma `financial-transfers` válida e NÃO DEVE estar disponível como opção para lançamento manual ou outra origem.
- **FR-FTF-FEE-001**: Transferência DEVE poder possuir zero ou mais tarifas, impostos ou custos econômicos vinculados, cada qual representado por lançamento financeiro comum separado do principal.
- **FR-FTF-FEE-002**: Tarifa, imposto ou diferença econômica NÃO DEVE ser ocultado por redução silenciosa do valor creditado no destino.
- **FR-FTF-FEE-003**: Cada custo DEVE possuir conta, direção, data, valor, moeda, categoria, dimensões aplicáveis, descrição, ator ou origem e auditoria próprios.
- **FR-FTF-FEE-004**: Custo conhecido antes da confirmação de uma etapa PODERÁ ser coordenado atomicamente com ela; falha do custo DEVE rejeitar toda aquela etapa quando o usuário os tiver confirmado conjuntamente.
- **FR-FTF-FEE-005**: Custo descoberto posteriormente DEVE poder ser acrescentado por novo fato imutável vinculado, sem atualizar a transferência, suas pontas, conversão ou custos anteriores.
- **FR-FTF-FEE-006**: Custo posterior DEVE validar conta, moeda, data, fechamento, categoria, dimensões e autorização no momento de sua própria confirmação; falha NÃO DEVE desfazer uma transferência já concluída.
- **FR-FTF-FEE-007**: Vínculo com a transferência NÃO DEVE dispensar qualquer regra de classificação ou autorização exigida de um lançamento econômico comum.
- **FR-FTF-FEE-008**: Custo debitado de conta diferente da origem DEVE exigir acesso explícito à conta escolhida e permanecer no mesmo tenant.
- **FR-FTF-FEE-009**: Custo denominado em moeda diferente da respectiva conta DEVE preservar memória de conversão conforme `financial-transactions`, sem alterar a conversão do principal.
- **FR-FTF-FEE-010**: Cada custo DEVE possuir identidade de origem idempotente no escopo da transferência, impedindo duplicação por repetição técnica.
- **FR-FTF-FEE-011**: Correção ou estorno de custo DEVE ocorrer pelo fluxo da transferência que o originou e criar fatos compensatórios vinculados, sem reescrever o custo confirmado.

### Rascunho e Confirmação

- **FR-FTF-POST-001**: Rascunho DEVE exigir, no mínimo, origem, destino, data prevista ou efetiva de envio, valor de origem e descrição; valor de destino será adicionalmente exigido para conclusão entre moedas diferentes.
- **FR-FTF-POST-002**: Rascunho DEVE preservar progressivamente observação, conversão, origem modular e demais dados admitidos na confirmação.
- **FR-FTF-POST-003**: Confirmação DEVE revalidar atomicamente tenant, contas, capacidades, moedas, precisão, valores, datas, fechamento, autorização, origem e repetição.
- **FR-FTF-POST-004**: Antes de confirmar cada etapa, o usuário DEVE revisar claramente os efeitos que ocorrerão naquele momento, os efeitos ainda pendentes, moedas, saldos resultantes permitidos e conversão aplicável.
- **FR-FTF-POST-005**: Falha em qualquer validação ou efeito DEVE preservar os saldos anteriores e não deixar operação, ponta, conversão, vínculo ou auditoria de sucesso parcial.
- **FR-FTF-POST-006**: Rascunho com data futura NÃO DEVE ser confirmado automaticamente, agendar transferência nem afetar saldo.
- **FR-FTF-POST-007**: Confirmar envio sem recebimento DEVE exigir indicação explícita de que a operação permanecerá em trânsito e NÃO DEVE criar a entrada antecipadamente.
- **FR-FTF-POST-008**: Confirmar recebimento DEVE revalidar destino, valor recebido, moeda, data, fechamento, autorização e estado em trânsito imediatamente antes de criar a entrada.

### Fechamento e Datas

- **FR-FTF-DATE-001**: Toda ponta DEVE possuir data de efetivação que determine quando seu lançamento afeta o saldo da conta correspondente.
- **FR-FTF-DATE-002**: Datas e instantes DEVEM possuir interpretação inequívoca no fuso aplicável ao tenant.
- **FR-FTF-DATE-003**: Cada efeito DEVE respeitar `financial-closing-control` imediatamente antes da confirmação.
- **FR-FTF-DATE-004**: Data bloqueada NÃO DEVE aceitar nova ponta, estorno ou correção, ainda que a outra data esteja aberta.
- **FR-FTF-DATE-005**: Retroceder ou remover o fechamento NÃO DEVE alterar transferências existentes nem dispensar as demais validações.
- **FR-FTF-DATE-006**: Data efetiva de recebimento NÃO DEVE ser anterior à data efetiva de envio.
- **FR-FTF-DATE-007**: Envio e recebimento DEVEM registrar seus próprios instantes de confirmação e atores ou origens, ainda que possuam a mesma data de efetivação.

### Origem e Idempotência

- **FR-FTF-SOURCE-001**: Origem DEVE distinguir transferência manual de transferência coordenada por outro módulo.
- **FR-FTF-SOURCE-002**: Transferência de módulo DEVE preservar módulo, tipo e identidade estável do evento de origem e, quando aplicável, sua versão.
- **FR-FTF-SOURCE-003**: Mesma identidade de origem somente PODERÁ produzir uma transferência por efeito esperado no tenant.
- **FR-FTF-SOURCE-004**: Repetição equivalente DEVE retornar o resultado existente; repetição divergente DEVE ser rejeitada e sinalizada.
- **FR-FTF-SOURCE-005**: Transferência originada por módulo somente DEVE ser corrigida por fluxo coordenado pela origem, sem atalho financeiro comum.
- **FR-FTF-SOURCE-006**: Falha da transferência DEVE impedir que o módulo de origem considere concluído o efeito quando fizerem parte da mesma confirmação de negócio.

### Estorno e Correção

- **FR-FTF-REV-001**: Transferência confirmada somente DEVE ser corrigida por novos fatos vinculados, nunca por atualização ou exclusão.
- **FR-FTF-REV-002**: Estorno integral DEVE criar operação inversa que compense atomicamente as duas pontas e preserve valores, moedas, conversão, motivo e vínculos.
- **FR-FTF-REV-003**: Estorno DEVE exigir autorização aplicável às duas contas, motivo não vazio e prévia dos efeitos.
- **FR-FTF-REV-004**: Uma transferência NÃO DEVE possuir mais de um estorno integral efetivado nem permanecer parcialmente estornada.
- **FR-FTF-REV-005**: Correção de conta, valor, data ou conversão DEVE vincular original, estorno e eventual substituta em uma cadeia explicável.
- **FR-FTF-REV-006**: Dependência de conciliação, caixa, investimento ou outro módulo PODERÁ impedir o estorno até que o vínculo seja legitimamente desfeito no contexto responsável.
- **FR-FTF-REV-007**: Encerramento posterior de conta NÃO DEVE autorizar reescrita; correção excepcional deverá usar contas e datas permitidas sem ocultar o original.
- **FR-FTF-REV-008**: Falha, devolução ou cancelamento depois do envio e antes do recebimento DEVE compensar somente a saída por novo lançamento vinculado e conduzir a operação a `ESTORNADA`, sem criar entrada no destino.
- **FR-FTF-REV-009**: Depois do recebimento, estorno DEVE compensar as duas pontas; não será permitido tratar transferência concluída como simples cancelamento de trânsito.

### Autorização, Auditoria e Isolamento

- **FR-FTF-SEC-001**: Acesso DEVE ser negado por padrão e liberado por chaves específicas do tenant.
- **FR-FTF-SEC-002**: Consultar, criar rascunho, alterar próprio rascunho, alterar qualquer rascunho, confirmar, transferir retroativamente, converter moedas, estornar, corrigir, consultar origem e exportar DEVEM poder ser autorizados separadamente quando o risco diferir.
- **FR-FTF-SEC-003**: Autorização geral para transferir NÃO DEVE substituir autorização necessária sobre cada conta nem conceder acesso a seus saldos.
- **FR-FTF-SEC-004**: Interfaces, módulos, importações e operações em lote DEVEM aplicar o mesmo isolamento e contrato de autorização.
- **FR-FTF-SEC-005**: Criação, alteração e exclusão de rascunho; confirmação, repetição divergente, estorno, correção, retroatividade e negação sensível DEVEM registrar ator, tenant, instante, origem, motivo, resultado e efeitos.
- **FR-FTF-SEC-006**: Usuário sem acesso à outra ponta NÃO DEVE inferir conta, valor, saldo, conversão ou existência por filtros, mensagens, totais, auditoria ou vínculos.
- **FR-FTF-SEC-007**: Logs, erros e métricas NÃO DEVEM expor valores, saldos, identificadores de contas ou dados pessoais sem finalidade e proteção aprovadas.

### Consulta e Experiência

- **FR-FTF-UX-001**: A interface DEVE apresentar origem e saída, destino e entrada como partes visualmente vinculadas da mesma operação.
- **FR-FTF-UX-002**: Usuário DEVE poder inverter origem e destino durante o rascunho sem conservar valores ou conversão incompatíveis silenciosamente.
- **FR-FTF-UX-003**: Contas selecionáveis DEVEM ser filtradas por tenant, estado e capacidade, preservando explicação acessível quando uma conta conhecida não estiver disponível.
- **FR-FTF-UX-004**: Em moedas diferentes, valores, moedas, taxa, convenção e arredondamento DEVEM ser apresentados sem sugerir igualdade nominal.
- **FR-FTF-UX-005**: Saldo negativo resultante, fechamento, ausência de autorização, conversão inválida e repetição divergente DEVEM possuir mensagens acionáveis sem revelar dados protegidos.
- **FR-FTF-UX-006**: Estado, direção, conta e moeda NÃO DEVEM depender somente de cor; jornadas principais DEVEM ser operáveis por teclado e tecnologias assistivas.
- **FR-FTF-UX-007**: Estorno e correção DEVEM apresentar original, compensação, substituta eventual e resultado líquido antes da confirmação.
- **FR-FTF-UX-008**: A interface DEVE distinguir claramente o principal neutro, os recursos em trânsito e cada custo econômico, sem apresentar o valor líquido como se fosse uma única ponta.

### Decisões de Infraestrutura Auditáveis

> Decisões de infraestrutura: esta feature não executa agendamentos, não mantém tokens externos e não realiza rotação criptográfica própria. Transferências futuras ou instruções bancárias pertencem a funcionalidades específicas.

- **FR-FTF-INFRA-IDEMP**: Cada confirmação de envio, recebimento imediato, recebimento posterior ou compensação DEVE possuir identidade idempotente no tenant e origem, produzindo no máximo o efeito esperado para aquela etapa.
- **FR-FTF-INFRA-LOCK**: Confirmação, estorno e correção concorrentes DEVEM preservar unicidade, vínculos, fechamento e saldos determinísticos em todas as instâncias da aplicação.
- **FR-FTF-INFRA-ATOMIC**: Em cada transição, estado, ponta aplicável, conversão disponível, vínculos e auditoria de sucesso DEVEM ser confirmados ou rejeitados como uma única operação de negócio.
- **FR-FTF-INFRA-BACKUP**: Rascunhos, transferências, pontas, conversões, vínculos, origens e auditorias DEVEM participar dos backups e restaurações do tenant segundo `tenant-data-governance`.

### Key Entities

- **Rascunho de Transferência**: preparação mutável e sem efeito em saldo, contendo as contas, data, descrição e valores progressivamente informados.
- **Transferência Financeira**: operação imutável que coordena duas pontas monetárias entre contas distintas do mesmo tenant.
- **Ponta da Transferência**: papel de origem ou destino associado ao respectivo lançamento financeiro, conta, moeda, valor e data.
- **Recurso em Trânsito**: posição derivada da saída confirmada ainda sem entrada ou compensação, discriminada por moeda e sem constituir conta financeira.
- **Memória de Conversão da Transferência**: evidência que relaciona valores de moedas diferentes pela taxa, convenção, fonte, referência e arredondamento confirmados.
- **Custo Vinculado à Transferência**: lançamento econômico comum, integralmente classificado e associado sem integrar nem alterar o principal movimentado.
- **Origem da Transferência**: usuário ou evento modular que solicitou a operação e fornece sua identidade idempotente.
- **Estorno da Transferência**: operação inversa integralmente vinculada que neutraliza as duas pontas sem apagar o fato original.
- **Cadeia de Correção**: relação entre transferência original, estorno e eventual substituta.

## Success Criteria

### Measurable Outcomes

- **SC-FTF-001**: Em 100% dos testes cruzados, transferências, rascunhos, contas, pontas e valores de um tenant não são consultados nem afetados por outro.
- **SC-FTF-002**: Em 100% das transferências imediatas válidas, exatamente uma saída e uma entrada vinculadas são produzidas; em cada etapa de trânsito, somente o efeito correspondente é produzido e nenhuma falha deixa transição parcial.
- **SC-FTF-003**: Em 100% das repetições equivalentes, no máximo uma transferência é produzida; conteúdo divergente com a mesma origem é rejeitado.
- **SC-FTF-004**: Em 100% dos testes de mesma moeda, os valores das pontas são idênticos e o efeito líquido conjunto nessa moeda é zero.
- **SC-FTF-005**: Em 100% dos testes multimoeda, cada conta recebe apenas sua moeda e a memória de conversão reproduz os dois valores confirmados.
- **SC-FTF-006**: Em 100% dos estornos, original permanece imutável e ambas as pontas são compensadas uma única vez.
- **SC-FTF-007**: Em 100% dos testes de fechamento, nenhuma ponta é produzida em data bloqueada e correções preservam as datas originais.
- **SC-FTF-008**: Em 100% dos testes de autorização parcial, nenhuma ponta é confirmada sem acesso necessário às duas contas e dados protegidos não são inferidos.
- **SC-FTF-009**: Usuários autorizados concluem uma transferência simples entre contas da mesma moeda em até 60 segundos nos testes de usabilidade.
- **SC-FTF-010**: Pelo menos 95% das consultas comuns retornam resultado utilizável em até 2 segundos nas condições operacionais da primeira versão.
- **SC-FTF-011**: Todas as jornadas principais podem ser concluídas somente por teclado e sem bloqueios críticos para tecnologias assistivas.
- **SC-FTF-012**: Logs, erros e métricas examinados não contêm valores, saldos, identificadores de conta ou dados pessoais sem finalidade e proteção aprovadas.
- **SC-FTF-013**: Em 100% dos testes de trânsito, a saída afeta a origem na data de envio, a entrada afeta o destino somente no recebimento e o intervalo permanece identificável separadamente por moeda.
- **SC-FTF-014**: Em 100% das falhas ou devoluções em trânsito, a saída é compensada por novo fato vinculado e nenhuma entrada fictícia é criada.
- **SC-FTF-015**: Em 100% dos custos confirmados, principal e custo permanecem fatos distintos, o custo está integralmente classificado e nenhuma diferença é ocultada nas pontas.
- **SC-FTF-016**: Em 100% dos custos acrescentados posteriormente, a transferência e seus efeitos anteriores permanecem imutáveis e repetição técnica produz no máximo um novo lançamento.

## Fora do Escopo

- Transferência para conta de terceiro não cadastrada como conta financeira do tenant.
- Pagamentos a fornecedores, colaboradores, governos ou outras contrapartes.
- Recebimentos de clientes ou outras contrapartes.
- Execução bancária, Pix, TED, remessa, retorno ou iniciação de pagamento.
- Agendamento e recorrência de transferências futuras.
- Linhas de extrato, importação, sincronização e conciliação bancária.
- Contas a pagar, contas a receber e liquidações de títulos.
- Compra, venda, aplicação, resgate ou transferência de custódia de ativos.
- Escrituração contábil, tributação cambial e documentos fiscais.
- Edição ou exclusão de transferências confirmadas.
