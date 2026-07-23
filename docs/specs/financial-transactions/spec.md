# Feature Specification: Lançamentos Financeiros

**Feature**: `financial-transactions`
**Created**: 2026-07-21
**Status**: Clarified — pronta para planejamento

## Escopo

Esta feature estabelece o fato monetário interno que explica alterações no saldo de uma conta financeira. Um pré-lançamento representa uma entrada ou saída já reconhecida na conta, ainda pendente de classificação ou informações finais. Um lançamento confirmado possui o mesmo efeito monetário, acrescido de classificação econômica, dimensões aplicáveis, origem e demais validações integrais.

O lançamento pode ser criado manualmente ou originado por outro módulo. Rascunhos ajudam a preparar e revisar dados sem alterar saldos. Pré-lançamentos usam os mesmos dados mínimos do rascunho, mas já compõem o saldo na data de efetivação e permanecem explicitamente pendentes até serem completados e confirmados. Enquanto sua data estiver aberta, pré-lançamentos e lançamentos confirmados podem ser revisados ou cancelados com histórico integral e recálculo dos saldos afetados.

> [!IMPORTANT]
> Lançamento financeiro não é conta a pagar, conta a receber, compra de cartão, linha de extrato, transferência completa, documento fiscal nem lançamento contábil. Essas funcionalidades possuem ciclos próprios e usam o lançamento financeiro somente quando precisam reconhecer um efeito monetário em uma conta.

> [!NOTE]
> O saldo interno de uma conta é derivado do saldo de abertura, dos pré-lançamentos e dos lançamentos confirmados com data de efetivação até o instante consultado. Rascunhos, previsões, obrigações, direitos, limites e linhas bancárias externas permanecem separados.

## Clarifications

### Session 2026-07-22

- Q: Como reconhecer no saldo uma movimentação real ainda sem todas as informações necessárias à confirmação? → A: O lançamento pode assumir o estado de pré-lançamento. Ele exige os mesmos dados mínimos do rascunho — conta, direção, data, valor e descrição — e já afeta o saldo, mas permanece pendente de classificação, contraparte, alocação ou outros dados finais. Sua conclusão preserva identidade e efeito monetário, sem produzir nova movimentação. Rascunhos e pré-lançamentos dentro do limite proposto impedem o avanço do fechamento até serem confirmados, movidos para data aberta ou descartados conforme seu estado.
- Q: Todo lançamento confirmado deve estar integralmente classificado por categorias? → A: Todo fato econômico confirmado deve estar integralmente classificado, e eventual categoria “A classificar” será uma categoria comum do tenant. Fatos estruturais que apenas movem ou substituem principal — pontas validadas de `financial-transfers` e componentes de principal de reparcelamento de cartão — dispensam categorias e dimensões, pois não representam receita ou despesa; toda diferença econômica, como juros e tarifas, permanece classificada.
- Q: Quais dados um rascunho precisa possuir e preservar? → A: Para ser salvo, o rascunho exige conta, direção, data, valor e descrição; os demais dados finais podem ser preenchidos e preservados progressivamente, ainda incompletos. A validação integral ocorre somente na confirmação, e data futura não agenda confirmação nem afeta saldo.
- Q: Quem pode estornar ou corrigir lançamento originado por outro módulo? → A: Somente o módulo de origem pode coordenar a correção e solicitar o respectivo estorno financeiro. O financeiro corrige diretamente apenas lançamentos manuais e não oferece atalho administrativo comum para ignorar o ciclo de origem.
- Q: Como impedir novos efeitos retroativos depois de uma conferência financeira? → A: Cada tenant possui uma única data de fechamento opcional. Datas iguais ou anteriores ficam bloqueadas para novos fatos; retroceder ou remover a data reabre o intervalo correspondente, com chave específica, motivo e auditoria. Não existem períodos financeiros nem snapshots de saldo nesta camada.
- Q: O lançamento precisa de código sequencial ou número legível próprio? → A: Não. O lançamento terá somente identidade técnica interna, sem código sequencial ou número equivalente exposto como identificação de negócio. Usuários o localizarão por data, valor, categoria e demais filtros funcionais.
- Q: Quando um lançamento confirmado pode ser alterado diretamente? → A: Enquanto sua data de efetivação estiver posterior à data de fechamento financeiro do tenant. Toda alteração preserva identidade e histórico completo, registra autoria, motivo, valores anteriores e novos e recalcula os saldos posteriores. Uma vez bloqueado, somente novos fatos compensatórios em data aberta podem corrigi-lo.

## User Scenarios & Testing

### User Story 1 - Registrar entrada ou saída efetivada (Priority: P1)

Um usuário autorizado prepara um lançamento manual, informa conta, direção, valor, data, descrição e classificações e o confirma depois de revisar seu efeito.

**Why this priority**: Este é o menor fluxo capaz de explicar uma alteração real no saldo sem permitir edição direta da conta.

**Independent Test**: Criar e confirmar uma saída manual válida e comprovar que ela afeta somente o saldo da conta indicada, na data correta e uma única vez.

**Acceptance Scenarios**:

1. **Given** conta ativa e usuário autorizado, **When** confirma saída válida, **Then** o lançamento recebe identidade estável e passa a compor o saldo da conta na data de efetivação.
2. **Given** lançamento ainda em rascunho, **When** usuário altera valor ou classificação, **Then** o rascunho é atualizado sem qualquer efeito no saldo.
3. **Given** repetição técnica da mesma confirmação, **When** a solicitação é processada novamente, **Then** nenhum segundo efeito monetário é produzido.
4. **Given** rascunho com os dados mínimos, **When** usuário o transforma em pré-lançamento, **Then** seu efeito passa a compor o saldo e o item permanece sinalizado como pendente.
5. **Given** pré-lançamento já refletido no saldo, **When** usuário completa e confirma suas informações, **Then** a identidade e o efeito monetário são preservados sem duplicar a movimentação.

---

### User Story 2 - Classificar e ratear o lançamento (Priority: P1)

Um usuário autorizado divide o valor entre uma ou várias categorias e informa distribuições dimensionais conjuntas exigidas por cada parcela.

**Why this priority**: Um saldo sem finalidade econômica e perspectiva analítica não atende aos controles e relatórios que motivam o módulo financeiro.

**Independent Test**: Dividir uma saída entre duas categorias com políticas dimensionais distintas e comprovar soma exata, validação por parcela e preservação dos cruzamentos dimensionais.

**Acceptance Scenarios**:

1. **Given** lançamento com duas parcelas de categoria, **When** a soma corresponde ao valor e cada distribuição é válida, **Then** a confirmação preserva parcelas e componentes dimensionais.
2. **Given** diferença monetária entre lançamento e parcelas, **When** confirmação é solicitada, **Then** o sistema rejeita a operação e informa a diferença.
3. **Given** dimensão obrigatória ausente ou dimensão proibida presente em uma parcela, **When** confirmação é solicitada, **Then** o lançamento permanece sem efeito até a correção.

---

### User Story 3 - Consultar extrato interno e saldo histórico (Priority: P1)

Um usuário autorizado consulta lançamentos de uma conta em ordem determinística, visualiza o saldo acumulado e filtra os fatos por período, direção, categoria, dimensão, contraparte, origem e estado.

**Why this priority**: O usuário precisa explicar como cada conta chegou ao saldo atual e reproduzir o saldo em qualquer data controlada.

**Independent Test**: Confirmar lançamentos em datas diferentes, incluir um lançamento retroativo permitido e comprovar os saldos acumulados atual e histórico.

**Acceptance Scenarios**:

1. **Given** abertura, pré-lançamentos e lançamentos confirmados, **When** usuário consulta uma data, **Then** o saldo corresponde à abertura e aos fatos efetivados até aquele instante, distinguindo os que ainda estão pendentes.
2. **Given** lançamento retroativo autorizado, **When** ele é confirmado, **Then** os saldos derivados posteriores são recalculados sem alterar os lançamentos existentes.
3. **Given** contas de moedas diferentes, **When** são consultadas em conjunto, **Then** os valores permanecem separados por moeda sem total enganoso.

---

### User Story 4 - Revisar ou compensar sem apagar histórico (Priority: P1)

Um usuário autorizado corrige diretamente um lançamento em data aberta ou, quando sua data estiver bloqueada, registra compensação vinculada em data permitida, sempre preservando o histórico.

**Why this priority**: Erros operacionais são inevitáveis; períodos abertos precisam admitir correção simples, enquanto histórico e fechamento impedem que alterações se tornem inexplicáveis.

**Independent Test**: Alterar lançamento em data aberta e comprovar histórico e recálculo; depois bloquear a data e comprovar que a mesma correção exige compensação vinculada.

**Acceptance Scenarios**:

1. **Given** lançamento confirmado em data aberta, **When** usuário autorizado altera valor, data ou classificação com motivo, **Then** a mesma identidade assume o conteúdo atual, a versão anterior é preservada e os saldos afetados são recalculados.
2. **Given** lançamento confirmado em data aberta, **When** usuário autorizado o cancela, **Then** seu efeito deixa os saldos atuais sem que o registro ou seu histórico sejam excluídos.
3. **Given** lançamento com data bloqueada, **When** correção é solicitada, **Then** o original permanece imutável e a correção exige novo fato compensatório em data aberta.

---

### User Story 5 - Receber lançamento originado por módulo (Priority: P1)

Um módulo autorizado reconhece pagamento, recebimento, compra, resgate, diferença de caixa ou outro evento e solicita atomicamente o lançamento correspondente, preservando sua própria identidade de origem.

**Why this priority**: O financeiro deve servir a diversos módulos sem duplicar fatos nem incorporar seus ciclos de negócio ao lançamento básico.

**Independent Test**: Simular duas entregas do mesmo evento de origem e comprovar um único lançamento, vínculo estável e ausência de campos específicos do módulo no núcleo financeiro.

**Acceptance Scenarios**:

1. **Given** evento válido de módulo do mesmo tenant, **When** sua confirmação solicita lançamento, **Then** o efeito financeiro é criado uma única vez e mantém referência à origem.
2. **Given** mesma identidade de origem repetida, **When** nova solicitação equivalente chega, **Then** o lançamento existente é retornado sem duplicar saldo.
3. **Given** tentativa com origem de outro tenant ou contrato incompatível, **When** solicitação é processada, **Then** ela é rejeitada sem revelar dados nem produzir efeito parcial.

---

### User Story 6 - Registrar valor originalmente denominado em outra moeda (Priority: P2)

Um usuário ou módulo registra que o valor efetivado na moeda da conta resultou de montante originalmente denominado em outra moeda, preservando a memória de conversão.

**Why this priority**: Compras, tarifas, receitas e documentos podem nascer em moeda diferente da conta liquidada, e o valor convertido sozinho não explica o fato.

**Independent Test**: Confirmar lançamento com moeda original distinta e comprovar valor original, valor efetivado, taxa, fonte, data e arredondamento sem criar saldo na moeda original.

**Acceptance Scenarios**:

1. **Given** moeda original diferente da conta, **When** lançamento é confirmado, **Then** todos os elementos da conversão são preservados e somente o valor na moeda da conta afeta o saldo.
2. **Given** memória de conversão incompleta, **When** confirmação é solicitada, **Then** o sistema rejeita o lançamento.
3. **Given** alteração posterior de cotação de referência, **When** histórico é consultado, **Then** a conversão originalmente confirmada permanece inalterada.

### Edge Cases

- Valor zero, negativo ou com precisão incompatível com a moeda.
- Entrada registrada com categoria que admite somente saída, ou o inverso.
- Conta inativa, encerrada ou de outro tenant durante a confirmação.
- Categoria, dimensão, valor dimensional ou contraparte desativados entre a edição e a confirmação.
- Soma correta nas categorias, mas incorreta dentro de uma distribuição dimensional.
- Dois componentes dimensionais com a mesma combinação.
- Confirmação concorrente do mesmo rascunho ou evento de origem.
- Alteração concorrente transforma o mesmo rascunho em pré-lançamento mais de uma vez.
- Pré-lançamento é corrigido, removido ou devolvido a rascunho depois de já afetar saldos posteriores.
- Avanço do fechamento encontra rascunhos ou pré-lançamentos dentro do limite proposto.
- Lançamento retroativo que altera todos os saldos acumulados posteriores.
- Lançamento futuro apresentado como fato já efetivado.
- Mudança de fuso horário entre criação, confirmação e consulta.
- Estorno solicitado com data bloqueada pelo fechamento ou depois de encerramento da conta.
- Estorno de lançamento que já participa de transferência, conciliação, pagamento ou outro processo.
- Revisão muda a data de efetivação entre dois intervalos abertos e exige recálculo a partir da menor data afetada.
- Revisão tenta mover lançamento de ou para data bloqueada.
- Cancelamento lógico de lançamento aberto que já participa de processo dependente.
- Contraparte excluída ou alterada depois da confirmação.
- Valor original e convertido cuja multiplicação exige arredondamento.
- Origem externa repete identificador com conteúdo divergente.
- Operação falha depois de validar classificações, mas antes de produzir o saldo.
- Usuário pode consultar lançamento, mas não saldo, contraparte ou detalhes de origem.

## Requirements

### Conceitos e Fronteiras

- **FR-FTR-BOUND-001**: Todo lançamento, rascunho, pré-lançamento, parcela, distribuição, vínculo e estorno DEVE pertencer exclusivamente a um tenant e somente poderá referenciar dados válidos desse tenant.
- **FR-FTR-BOUND-002**: Pré-lançamento ou lançamento confirmado DEVE representar exatamente um efeito monetário de entrada ou saída em uma conta financeira.
- **FR-FTR-BOUND-003**: Criar lançamento NÃO DEVE criar conta a pagar, conta a receber, transferência completa, compra de cartão, cartão associado, linha de extrato, documento fiscal, posição de investimento ou lançamento contábil.
- **FR-FTR-BOUND-004**: Rascunho, previsão, obrigação, direito, autorização, limite ou linha externa NÃO DEVE compor o saldo interno efetivado.
- **FR-FTR-BOUND-005**: Saldo DEVE ser derivado da abertura da conta e dos pré-lançamentos e lançamentos confirmados aplicáveis; lançamento NÃO DEVE armazenar nem permitir editar um novo saldo arbitrário.
- **FR-FTR-BOUND-006**: Um lançamento DEVE afetar somente uma conta; operações que exigem efeitos coordenados em duas ou mais contas pertencem a `financial-transfers` ou ao módulo de origem.
- **FR-FTR-BOUND-007**: Limites comerciais de quantidade, retenção operacional ou funcionalidades pertencem a `plans-entitlements` e NÃO DEVEM comprometer histórico confirmado.

### Identidade, Direção e Valor

- **FR-FTR-CORE-001**: Lançamento DEVE possuir identidade estável e imutável, independente de descrição, referência, classificação, origem ou apresentação.
- **FR-FTR-CORE-002**: Lançamento DEVE declarar direção `ENTRADA` ou `SAÍDA` e valor absoluto maior que zero na moeda da conta.
- **FR-FTR-CORE-003**: O efeito no saldo DEVE ser positivo para entrada e negativo para saída, sem exigir valor monetário negativo como entrada do usuário.
- **FR-FTR-CORE-004**: Valor DEVE respeitar moeda e precisão da conta; excesso de precisão NÃO DEVE ser truncado silenciosamente.
- **FR-FTR-CORE-005**: Lançamento DEVE possuir descrição compreensível, data de efetivação, instante de criação, ator ou origem responsável e estado, além dos instantes em que se tornou pré-lançamento e foi confirmado quando essas transições ocorrerem.
- **FR-FTR-CORE-006**: Lançamento PODERÁ possuir referência textual, observação, contraparte e data de competência independentes, sem que esses campos alterem o saldo.
- **FR-FTR-CORE-007**: Data de competência DEVE servir somente à classificação econômica e relatórios que explicitamente a utilizem; a data de efetivação DEVE determinar o saldo financeiro.
- **FR-FTR-CORE-008**: Pré-lançamento ou lançamento confirmado com data futura DEVE permanecer programado, compor projeções aplicáveis e NÃO DEVE afetar saldo atual nem ser apresentado como fato já efetivado antes da data.
- **FR-FTR-CORE-009**: Ordem de eventos no mesmo instante DEVE ser determinística e reproduzível para cálculo de saldo acumulado.
- **FR-FTR-CORE-010**: O sistema NÃO DEVE gerar código sequencial, número de lançamento ou identificador de negócio equivalente; a identidade técnica interna NÃO DEVE ser apresentada como número funcional nas jornadas comuns.

### Rascunho, Pré-lançamento e Confirmação

- **FR-FTR-POST-001**: Rascunho DEVE poder ser criado, alterado e excluído por usuário autorizado sem afetar saldo ou histórico de fatos confirmados.
- **FR-FTR-POST-002**: Confirmação DEVE validar atomicamente conta, moeda, data, valor, categoria, dimensões, contraparte, origem, autorização e fechamento financeiro aplicáveis.
- **FR-FTR-POST-003**: Confirmação bem-sucedida DEVE produzir um único lançamento; quando partir de pré-lançamento, DEVE preservar o efeito já existente sem produzir uma segunda movimentação.
- **FR-FTR-POST-004**: Falha em qualquer validação ou efeito DEVE preservar o saldo anterior e NÃO DEVE deixar parcelas, distribuições, vínculos ou auditorias de sucesso parciais.
- **FR-FTR-POST-005**: Lançamento confirmado com data aberta PODE ser revisado ou cancelado logicamente por fluxo autorizado; lançamento com data bloqueada NÃO DEVE ser alterado nem excluído por usuário, administrador, módulo consumidor, importação ou conciliação.
- **FR-FTR-POST-006**: Revisão DEVE preservar a identidade do lançamento, o conteúdo anterior completo, o novo conteúdo, motivo, ator ou origem, instante e efeito nos saldos derivados.
- **FR-FTR-POST-007**: Conta DEVE estar ativa e apta à operação no momento da confirmação; inativação posterior NÃO DEVE remover o lançamento do histórico.
- **FR-FTR-POST-008**: Efetivação como pré-lançamento, confirmação, revisão, retorno a rascunho ou cancelamento retroativo autorizado DEVE recalcular saldos derivados posteriores sem modificar identidades nem históricos de outros lançamentos.
- **FR-FTR-POST-013**: Revisão que altere data DEVE exigir que as datas anterior e nova estejam abertas e recalcular os saldos a partir da menor delas.
- **FR-FTR-POST-014**: Cancelamento de lançamento em data aberta DEVE retirar seu efeito dos saldos derivados, preservar o registro em estado cancelado e impedir cancelamento repetido.
- **FR-FTR-POST-015**: Revisão ou cancelamento de lançamento originado por módulo DEVE ser coordenado pela origem e atualizar processo e efeito financeiro atomicamente.
- **FR-FTR-POST-009**: Para ser salvo, rascunho DEVE possuir conta financeira, direção, data, valor e descrição; conta e direção PODERÃO ser predeterminadas pelo contexto em que a criação foi iniciada.
- **FR-FTR-POST-010**: Rascunho DEVE permitir preencher e preservar progressivamente contraparte, referência, competência, categorias, dimensões, observações, memória de conversão e demais dados admitidos no lançamento final, mesmo enquanto o conjunto estiver incompleto.
- **FR-FTR-POST-011**: Salvamento do rascunho DEVE validar a estrutura e a autorização dos dados já informados, mas somente a confirmação DEVE exigir classificação integral e todas as regras finais de consistência e vigência.
- **FR-FTR-POST-012**: Rascunho PODERÁ possuir data futura, mas NÃO DEVE ser confirmado automaticamente, agendar confirmação nem afetar saldo por essa razão.
- **FR-FTR-POST-016**: Rascunho PODERÁ ser transformado em pré-lançamento somente quando possuir conta financeira, direção, data, valor e descrição válidos e o usuário ou origem estiver autorizado a reconhecer o efeito monetário.
- **FR-FTR-POST-017**: Pré-lançamento DEVE afetar saldo na data de efetivação, ainda que categoria, dimensões, contraparte, alocação ou demais dados exigidos para confirmação permaneçam incompletos.
- **FR-FTR-POST-018**: Pré-lançamento DEVE permanecer visivelmente pendente e NÃO DEVE ser apresentado como lançamento confirmado, integralmente classificado ou liquidante de obrigação ou direito sem os respectivos vínculos.
- **FR-FTR-POST-019**: Confirmação de pré-lançamento DEVE preservar sua identidade, conta, direção e efeito monetário vigentes, aplicar todas as validações finais e registrar a mudança de estado sem duplicar saldo.
- **FR-FTR-POST-020**: Enquanto sua data estiver aberta, pré-lançamento PODERÁ ser alterado, cancelado ou devolvido a rascunho por usuário autorizado; toda mudança DEVE ser auditada e recalcular saldos derivados desde a menor data afetada.
- **FR-FTR-POST-021**: Falha ao confirmar pré-lançamento DEVE mantê-lo no estado anterior e preservar seu efeito vigente, sem produzir classificação, alocação, vínculo ou movimentação parcial adicional.

### Categorias e Dimensões

- **FR-FTR-CLASS-001**: Todo lançamento confirmado que represente fato econômico DEVE estar integralmente classificado por categorias, sem parcela monetária implícita ou temporariamente não classificada. Somente fatos estruturais validados que movam ou substituam principal sem gerar resultado — pontas de `financial-transfers` e componentes de principal de reparcelamento de cartão — DEVEM dispensar categorias e dimensões.
- **FR-FTR-CLASS-002**: Lançamento DEVE permitir uma ou várias parcelas de categoria, cada uma com categoria e valor próprios na moeda da conta.
- **FR-FTR-CLASS-003**: Soma de todas as parcelas de categoria DEVE corresponder exatamente ao valor total do lançamento, sem diferença oculta de arredondamento.
- **FR-FTR-CLASS-004**: Direção do lançamento DEVE ser compatível com cada categoria, exceto em estorno vinculado que preserve a classificação original.
- **FR-FTR-CLASS-005**: Cada parcela DEVE ser validada separadamente contra regras dimensionais vigentes da categoria na data da confirmação.
- **FR-FTR-CLASS-006**: Distribuição dimensional conjunta DEVE preservar montantes exatos, combinações e no máximo um valor por dimensão em cada componente.
- **FR-FTR-CLASS-007**: Sugestões explícitas ou históricas PODERÃO preencher uma proposta, mas NÃO DEVERÃO confirmar nem modificar classificações sem revisão permitida pelo contexto.
- **FR-FTR-CLASS-008**: Confirmação e revisão DEVEM preservar identidades, versões e apresentação histórica suficiente de categorias, dimensões e valores usados em cada estado do lançamento.
- **FR-FTR-CLASS-009**: Alteração ou desativação cadastral posterior NÃO DEVE reclassificar lançamento sem ação autorizada e auditada sobre ele.
- **FR-FTR-CLASS-010**: Estorno integral DEVE espelhar parcelas e distribuições originais com efeito monetário oposto, sem reaplicar sugestões ou regras atuais.
- **FR-FTR-CLASS-011**: Tenant PODERÁ criar categoria comum, inclusive neutra, para identificar valores conscientemente mantidos “a classificar”, mas o sistema NÃO DEVE fornecer categoria privilegiada capaz de dispensar regras, dimensões ou autorizações comuns.
- **FR-FTR-CLASS-012**: Dispensa de classificação DEVE decorrer exclusivamente de origem estrutural validada de transferência ou reparcelamento de cartão e NÃO DEVE ser selecionável, atribuível ou simulável por lançamento manual ou por módulo sem esse contrato.
- **FR-FTR-CLASS-013**: Em reparcelamento de cartão, neutralização e reapresentação do principal DEVEM possuir valores correspondentes e vínculo atômico; qualquer diferença econômica DEVE ser explicitada em lançamentos categorizados de juros, multas, impostos, tarifas ou outros encargos.

### Contraparte e Memória Histórica

- **FR-FTR-PARTY-001**: Lançamento PODERÁ referenciar uma contraparte do mesmo tenant sem exigir papel específico, salvo regra adicional do módulo de origem.
- **FR-FTR-PARTY-002**: Contraparte desativada NÃO DEVE ser oferecida para novo lançamento manual comum, mas DEVE permanecer identificável no histórico.
- **FR-FTR-PARTY-003**: Confirmação com contraparte DEVE preservar referência à identidade e apresentação mínima usada naquele instante, sem copiar dados pessoais desnecessários.
- **FR-FTR-PARTY-004**: Alteração ou exclusão legítima do cadastro atual NÃO DEVE mudar a apresentação histórica preservada no lançamento.
- **FR-FTR-PARTY-005**: Lançamento sem contraparte DEVE ser permitido quando a natureza da operação não a exigir.

### Moeda Original e Conversão

- **FR-FTR-FX-001**: O valor que afeta saldo DEVE estar sempre na moeda da conta financeira.
- **FR-FTR-FX-002**: Quando a operação for originalmente denominada em outra moeda, o lançamento DEVE preservar moeda e valor originais, moeda e valor efetivados, taxa aplicada, convenção da taxa, fonte, data ou instante de referência e regra de arredondamento.
- **FR-FTR-FX-003**: Memória de conversão incompleta ou matematicamente inconsistente além da tolerância monetária declarada DEVE impedir confirmação.
- **FR-FTR-FX-004**: Diferença de arredondamento DEVE ser explícita e determinística e NÃO DEVE alterar silenciosamente o valor original.
- **FR-FTR-FX-005**: Cotação posterior NÃO DEVE recalcular lançamento confirmado.
- **FR-FTR-FX-006**: Informar moeda original NÃO DEVE criar saldo, conta, posição de câmbio nem segunda perna de transferência nessa moeda.

### Origem, Integração e Idempotência

- **FR-FTR-SOURCE-001**: Origem DEVE distinguir lançamento `MANUAL` de lançamento `ORIGINADO_POR_MÓDULO`.
- **FR-FTR-SOURCE-002**: Lançamento originado DEVE preservar módulo, tipo de evento, identidade estável da origem e, quando aplicável, versão ou sequência do evento.
- **FR-FTR-SOURCE-003**: A mesma identidade de origem somente PODERÁ produzir um lançamento por efeito financeiro esperado no tenant e conta aplicáveis.
- **FR-FTR-SOURCE-004**: Repetição equivalente DEVE retornar o resultado existente; repetição com conteúdo financeiro divergente DEVE ser rejeitada e sinalizada para investigação.
- **FR-FTR-SOURCE-005**: Contrato de origem DEVE transportar todos os dados necessários sem expor entidades internas nem permitir campos arbitrários que mudem a semântica do núcleo.
- **FR-FTR-SOURCE-006**: Lançamento originado por outro módulo somente DEVE ser revisado, cancelado, estornado ou corrigido por solicitação coordenada pelo módulo de origem; o financeiro somente DEVE coordenar diretamente alterações de lançamentos manuais.
- **FR-FTR-SOURCE-007**: Falha do lançamento financeiro DEVE impedir que o módulo de origem considere concluído o efeito monetário quando ambos fizerem parte da mesma confirmação de negócio.
- **FR-FTR-SOURCE-008**: Consulta autorizada DEVE permitir navegar do lançamento à referência de origem e da origem ao efeito financeiro sem duplicar o documento original.
- **FR-FTR-SOURCE-009**: Interface financeira DEVE identificar a origem e direcionar o usuário ao fluxo responsável quando ele tentar revisar, cancelar ou corrigir lançamento originado, sem oferecer alteração financeira isolada.
- **FR-FTR-SOURCE-010**: Indisponibilidade, desativação ou falha do módulo de origem NÃO DEVE liberar correção direta comum; inconsistência excepcional DEVE permanecer bloqueada e ser tratada por procedimento operacional externo, auditado e fora da interface ordinária.
- **FR-FTR-SOURCE-011**: Alteração coordenada pela origem DEVE atualizar o processo de negócio e revisar, cancelar ou compensar o efeito financeiro como uma única operação bem-sucedida ou não produzir nenhum dos efeitos.

### Estorno e Correção

- **FR-FTR-REV-001**: Lançamento em data aberta DEVE ser corrigível por revisão versionada ou cancelamento lógico; lançamento em data bloqueada somente DEVE ser corrigido por novos fatos vinculados em data aberta.
- **FR-FTR-REV-002**: Estorno integral DEVE criar lançamento na mesma conta e moeda, com valor idêntico, direção oposta, classificações espelhadas, data permitida, motivo e vínculo recíproco com o original.
- **FR-FTR-REV-003**: Estorno NÃO DEVE reutilizar regras ou sugestões vigentes para substituir categoria, dimensão, contraparte ou conversão originalmente confirmadas.
- **FR-FTR-REV-004**: Um lançamento NÃO DEVE possuir mais de um estorno integral efetivado nem ser estornado enquanto outro estorno estiver em processamento.
- **FR-FTR-REV-005**: Correção que exija novo conteúdo DEVE poder vincular original, estorno e substituto em uma cadeia explicável.
- **FR-FTR-REV-006**: Ajuste parcial em data aberta PODE revisar o lançamento e seu histórico; quando a data estiver bloqueada, DEVE ser registrado como novo lançamento compensatório explicitamente relacionado.
- **FR-FTR-REV-007**: O sistema DEVE impedir estorno direto quando dependências de outro módulo exigirem desfazer previamente conciliação, liquidação, transferência ou outro vínculo.
- **FR-FTR-REV-008**: Revisão, cancelamento, estorno e correção DEVEM exigir autorização específica, motivo não vazio e prévia do efeito nos saldos.
- **FR-FTR-REV-009**: Encerramento posterior da conta NÃO DEVE liberar alteração; eventual revisão exige conta apta e data aberta, e correção de fato bloqueado exige compensação conforme o contrato de reabertura aplicável.
- **FR-FTR-REV-010**: Vínculo com transferência, conciliação, liquidação, documento ou processo dependente PODE impedir revisão ou cancelamento direto até que a dependência seja desfeita pelo fluxo responsável.

### Datas, Fechamento e Saldo

- **FR-FTR-DATE-001**: Data de efetivação DEVE representar quando o valor passou a afetar a conta, enquanto os instantes de pré-lançamento e confirmação DEVEM representar quando o sistema reconheceu o efeito e quando concluiu suas informações, respectivamente.
- **FR-FTR-DATE-002**: Todas as datas e instantes DEVEM possuir interpretação inequívoca no fuso aplicável ao tenant e preservar ordenação histórica.
- **FR-FTR-DATE-003**: Toda confirmação, revisão, cancelamento, estorno ou correção DEVE validar as datas afetadas contra a data de fechamento do tenant, conforme `financial-closing-control`.
- **FR-FTR-DATE-004**: Estorno em data diferente da original DEVE preservar ambas e explicar quando cada efeito ocorreu.
- **FR-FTR-DATE-005**: Consulta de saldo DEVE declarar instante ou data de referência e incluir pré-lançamentos e lançamentos confirmados efetivados até esse limite, distinguindo o efeito pendente do confirmado.
- **FR-FTR-DATE-006**: Rascunho, pré-lançamento ou lançamento confirmado com data futura NÃO DEVE compor saldo atual nem ser apresentado como fato já efetivado.
- **FR-FTR-DATE-007**: Saldo negativo resultante DEVE ser permitido e sinalizado, sem incorporar limite de crédito nem bloquear o lançamento somente por insuficiência de saldo.
- **FR-FTR-DATE-008**: Saldos derivados por lançamento, dia e intervalo DEVEM ser reproduzíveis a partir da abertura e do conteúdo vigente dos pré-lançamentos e lançamentos confirmados, enquanto seus históricos explicam todas as transições, revisões, cancelamentos e inclusões retroativas permitidas.
- **FR-FTR-DATE-009**: Quando houver data de fechamento, data de efetivação igual ou anterior a ela NÃO DEVE aceitar novo pré-lançamento ou lançamento confirmado, revisão, cancelamento, estorno, correção nem efeito solicitado por módulo.
- **FR-FTR-DATE-010**: Novo rascunho NÃO DEVE aceitar data bloqueada, e a data de fechamento NÃO DEVE avançar enquanto existir rascunho ou pré-lançamento não cancelado com data de efetivação igual ou anterior ao limite proposto.
- **FR-FTR-DATE-011**: Quando a data do fato original estiver bloqueada, seu estorno DEVE usar data posterior à data de fechamento e preservar separadamente a data do original e a data da compensação.
- **FR-FTR-DATE-012**: Retroceder ou remover a data de fechamento NÃO DEVE modificar lançamentos automaticamente, mas DEVE voltar a permitir revisões e cancelamentos autorizados no intervalo reaberto.
- **FR-FTR-DATE-013**: Fechamentos contábeis, fiscais ou de módulos futuros PODERÃO impor bloqueios adicionais, mas NÃO DEVERÃO relaxar a data de fechamento financeiro.
- **FR-FTR-DATE-014**: Data de efetivação, instantes de confirmação e revisão, ator, origem, versões e vínculos de correção DEVEM permitir identificar tudo que foi incluído, alterado ou cancelado em intervalo temporariamente reaberto.
- **FR-FTR-DATE-015**: Pré-lançamento NÃO DEVE ser alcançado por novo fechamento; antes do avanço ele DEVE ser confirmado, movido para data posterior ao limite ou cancelado por fluxo autorizado. Retorná-lo a rascunho sem removê-lo do intervalo NÃO DEVE deixar de bloquear o fechamento.

### Consulta e Extrato Interno

- **FR-FTR-SEARCH-001**: Usuário autorizado DEVE poder pesquisar por conta, período de efetivação, confirmação, direção, faixa de valor, categoria, dimensão, contraparte, origem, referência, estado e vínculo de correção.
- **FR-FTR-SEARCH-002**: Extrato interno DEVE apresentar ordem determinística, efeito individual e saldo acumulado na moeda da conta.
- **FR-FTR-SEARCH-003**: Consulta conjunta de contas DEVE separar totais por moeda, salvo quando conversão explícita pertencente a relatório futuro for solicitada.
- **FR-FTR-SEARCH-004**: Rascunhos, pré-lançamentos, programados, confirmados, revisados, cancelados, estornos e lançamentos estornados DEVEM ser visualmente distinguíveis sem ocultar o efeito vigente.
- **FR-FTR-SEARCH-005**: Usuário DEVE poder navegar entre original, estorno, substituto e origem quando possuir autorização para cada conteúdo.
- **FR-FTR-SEARCH-006**: Linha de extrato externo e lançamento interno DEVEM permanecer registros distintos, ainda que futuramente conciliados.
- **FR-FTR-SEARCH-007**: Exportações e relatórios DEVEM aplicar as mesmas autorizações, filtros e regras monetárias das consultas interativas.

### Autorização, Auditoria e Isolamento

- **FR-FTR-SEC-001**: Acesso a rascunhos, pré-lançamentos, lançamentos confirmados, saldos e classificações DEVE ser negado por padrão e liberado por chaves específicas do tenant.
- **FR-FTR-SEC-002**: Consultar lançamento, consultar valores e saldo, criar ou alterar rascunho, reconhecer ou alterar pré-lançamento, confirmar, revisar lançamento aberto, cancelar lançamento aberto, lançar retroativamente, estornar fato bloqueado, corrigir, consultar histórico, consultar origem e exportar DEVEM poder ser autorizados separadamente quando o risco diferir.
- **FR-FTR-SEC-003**: Autorização para usar uma conta NÃO DEVE conceder automaticamente acesso a todas as categorias, dimensões, contrapartes ou origens relacionadas.
- **FR-FTR-SEC-004**: Interfaces, módulos internos, importações e operações em lote DEVEM aplicar o mesmo isolamento e contrato de autorização.
- **FR-FTR-SEC-005**: Criação, alteração e exclusão de rascunho; reconhecimento, alteração, retorno ou cancelamento de pré-lançamento; confirmação, revisão e cancelamento de lançamento, tentativa repetida divergente, estorno, correção, lançamento retroativo, negação sensível e exportação DEVEM registrar ator, tenant, instante, origem, motivo, resultado e efeito.
- **FR-FTR-SEC-008**: Histórico de revisão DEVE preservar integralmente valores anteriores e posteriores de todos os campos financeiros, classificações, datas, contraparte, origem e estado, sem depender somente de mensagem textual de auditoria.
- **FR-FTR-SEC-006**: Logs, erros, métricas e auditorias comuns NÃO DEVEM expor valores, saldos, dados pessoais, classificações ou referências além da finalidade e autorização necessárias.
- **FR-FTR-SEC-007**: Usuário sem acesso a valor, saldo, contraparte ou origem NÃO DEVE inferi-los por totais, contagens, filtros, mensagens ou vínculos.

### Experiência e Acessibilidade

- **FR-FTR-UX-001**: Formulário DEVE apresentar claramente conta, direção, valor, moeda, data de efetivação, descrição, contraparte, parcelas de categoria, dimensões e origem antes da confirmação.
- **FR-FTR-UX-002**: Antes de confirmar, usuário DEVE visualizar efeito no saldo, diferenças de classificação, regras dimensionais e memória de conversão aplicável.
- **FR-FTR-UX-008**: Pré-lançamento DEVE exibir sinalização persistente de que afeta saldo, mas ainda exige conclusão, e listar os dados pendentes sem depender exclusivamente de cor.
- **FR-FTR-UX-003**: Sugestões DEVEM permanecer distinguíveis de dados confirmados e explicar sua origem sem automatizar consentimento.
- **FR-FTR-UX-004**: Erros de soma, precisão, vigência, autorização, fechamento ou repetição DEVEM indicar o campo ou parcela afetada e como regularizar, sem revelar dados protegidos.
- **FR-FTR-UX-005**: Revisão, cancelamento, estorno e correção DEVEM apresentar conteúdo atual, alteração proposta, efeito nos saldos, datas e dependências antes da confirmação.
- **FR-FTR-UX-006**: Direção, estado, valor negativo derivado, estorno e inconsistência NÃO DEVEM depender exclusivamente de cor.
- **FR-FTR-UX-007**: Jornadas principais DEVEM ser realizáveis por teclado e tecnologias assistivas, inclusive edição de parcelas e distribuições.

### Decisões de Infraestrutura Auditáveis

> Decisões de infraestrutura: esta feature não agenda recorrências, não mantém tokens externos e não realiza rotação criptográfica própria. Recorrências, integrações bancárias e anexos serão definidos nas respectivas features.

- **FR-FTR-INFRA-IDEMP**: Toda confirmação DEVE possuir identidade idempotente no escopo do tenant e da origem, de forma que repetição técnica produza no máximo um efeito financeiro.
- **FR-FTR-INFRA-LOCK**: Pré-lançamentos, confirmações, revisões, cancelamentos, estornos, correções, inclusões retroativas e avanços de fechamento concorrentes DEVEM preservar unicidade, histórico, ordenação e saldo determinístico em todas as instâncias da aplicação.
- **FR-FTR-INFRA-ATOMIC**: Estado do lançamento, parcelas, distribuições, vínculos, efeito no saldo e auditoria de sucesso DEVEM ser alterados ou rejeitados como uma única operação de negócio.
- **FR-FTR-INFRA-BACKUP**: Rascunhos, pré-lançamentos, lançamentos confirmados, classificações, memórias de conversão, vínculos, estados e auditorias DEVEM participar dos backups e restaurações do tenant segundo `tenant-data-governance`.

### Key Entities

- **Rascunho de Lançamento**: preparação mutável e sem efeito em saldo, com conta, direção, data, valor e descrição mínimos e demais dados finais preenchíveis progressivamente até a confirmação.
- **Pré-lançamento**: lançamento com os mesmos dados mínimos do rascunho que já afeta o saldo na data de efetivação, mas permanece pendente de classificação, contraparte, alocação ou demais informações finais.
- **Lançamento Financeiro**: fato monetário de entrada ou saída em uma conta, com identidade técnica estável, conteúdo vigente, histórico integral de revisões, valor, moeda, datas, classificação, origem e estado; torna-se imutável quando sua data é bloqueada.
- **Revisão de Lançamento**: versão histórica completa que preserva conteúdo anterior e novo, motivo, autoria, instante e efeito da alteração permitida.
- **Parcela de Categoria**: parte monetária do lançamento associada a uma categoria e validada independentemente.
- **Componente Dimensional**: parte de uma parcela que preserva combinação conjunta de valores dimensionais.
- **Contraparte do Lançamento**: referência opcional à pessoa envolvida e apresentação histórica mínima necessária.
- **Memória de Conversão**: evidência do valor originalmente denominado em outra moeda e de como se tornou o valor efetivado.
- **Origem do Lançamento**: referência estável ao usuário ou evento de módulo que solicitou o efeito financeiro.
- **Estorno Financeiro**: lançamento oposto integralmente vinculado ao original e criado para neutralizar seu efeito sem apagá-lo.
- **Cadeia de Correção**: vínculo explicável entre fato original, compensação e eventual substituto.
- **Saldo Derivado**: resultado reproduzível da abertura, dos pré-lançamentos e dos lançamentos confirmados até a referência consultada.

## Success Criteria

### Measurable Outcomes

- **SC-FTR-001**: Em 100% dos testes cruzados, rascunhos, lançamentos, classificações, origens e saldos de um tenant não são consultados nem afetados por outro tenant.
- **SC-FTR-002**: Em 100% das confirmações válidas, o lançamento possui exatamente um efeito monetário: a confirmação direta o produz uma vez e a confirmação de pré-lançamento preserva o efeito existente; em qualquer falha, nenhum efeito parcial adicional permanece.
- **SC-FTR-003**: Em 100% das consultas testadas, o saldo é reproduzível pela abertura, pelos pré-lançamentos e pelos lançamentos confirmados até a referência solicitada.
- **SC-FTR-004**: Em 100% dos testes, rascunhos e eventos futuros não alteram o saldo atual, enquanto pré-lançamentos já efetivados o alteram e permanecem identificáveis como pendentes.
- **SC-FTR-005**: Em 100% dos testes monetários, valores respeitam a precisão da moeda e parcelas e distribuições exigidas somam exatamente o montante aplicável.
- **SC-FTR-006**: Em 100% dos testes dimensionais, regras obrigatórias, opcionais e proibidas são avaliadas por parcela sem perder cruzamentos.
- **SC-FTR-007**: Em 100% dos testes, revisão ou cancelamento em data aberta preserva identidade e histórico completo e recalcula saldos; fato em data bloqueada permanece imutável e somente aceita compensação rastreável.
- **SC-FTR-008**: Em 100% das repetições equivalentes, no máximo um lançamento é produzido; conteúdo divergente com a mesma origem é rejeitado.
- **SC-FTR-009**: Em 100% dos testes multimoeda, somente a moeda da conta afeta saldo e a memória original explica deterministicamente a conversão confirmada.
- **SC-FTR-010**: Usuários autorizados concluem um lançamento manual simples e categorizado em até 90 segundos nos testes de usabilidade.
- **SC-FTR-011**: Pelo menos 95% das consultas comuns de extrato e saldo retornam resultado utilizável em até 2 segundos nas condições operacionais da primeira versão.
- **SC-FTR-012**: Em 100% das inclusões, revisões e cancelamentos retroativos permitidos, saldos posteriores são recalculados corretamente sem modificar identidades nem apagar versões anteriores.
- **SC-FTR-013**: Em 100% dos testes de autorização parcial, valores, saldos, contrapartes e origens não podem ser inferidos por caminhos alternativos.
- **SC-FTR-014**: Todas as jornadas principais podem ser concluídas somente por teclado e sem bloqueios críticos para tecnologias assistivas.
- **SC-FTR-015**: Logs, erros e métricas examinados não contêm valores, saldos nem dados pessoais sem finalidade e proteção aprovadas.
- **SC-FTR-016**: Em 100% dos testes com origem modular, tentativa de alteração financeira isolada é bloqueada, enquanto o fluxo da origem atualiza seu processo e revisa, cancela ou compensa o efeito de forma integral e rastreável.
- **SC-FTR-017**: Em 100% dos testes de fechamento, data bloqueada rejeita novos efeitos, retrocesso ou remoção do fechamento não altera fatos existentes e estorno posterior preserva as datas original e compensatória.
- **SC-FTR-018**: Nas jornadas comuns testadas, usuários localizam lançamentos por atributos e filtros funcionais sem depender de código sequencial, número de lançamento ou exposição da identidade técnica como referência de negócio.
- **SC-FTR-019**: Em 100% das confirmações de pré-lançamento, identidade e efeito monetário são preservados sem duplicação da movimentação.
- **SC-FTR-020**: Em 100% das tentativas de avanço do fechamento, a existência de rascunho ou pré-lançamento dentro do limite proposto bloqueia a operação sem alterar o fechamento vigente.

## Fora do Escopo

- Transferências coordenadas entre contas, inclusive câmbio entre suas moedas.
- Contas a pagar, contas a receber, vencimentos, parcelas de títulos e recorrências.
- Contas e cartões de crédito, compras, parcelas e faturas.
- Sessões, contagens e fechamentos operacionais de caixa.
- Linhas de extrato, importação OFX, conexão bancária e conciliação.
- Anexos, comprovantes e gestão documental.
- Orçamentos, previsões, limites e fluxos de aprovação.
- Posições de investimento, quantidades, cotações e valorização de ativos.
- Documentos fiscais e escrituração contábil.
- Conversão consolidada de relatórios entre moedas.
