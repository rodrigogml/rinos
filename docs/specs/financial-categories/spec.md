# Feature Specification: Categorias Financeiras

**Feature**: `financial-categories`
**Created**: 2026-07-21
**Status**: Clarified — pronta para planejamento

## Escopo

Esta feature estabelece o cadastro das categorias usadas pelo tenant para declarar a finalidade econômica de lançamentos financeiros, como vendas, salários, aluguel, tarifas, juros, impostos e correções. A categoria responde principalmente **o que o valor representa**, sem substituir conta financeira, contraparte, centro de custo, projeto, documento de origem ou conta contábil.

As categorias devem permanecer estáveis e reutilizáveis entre lançamentos manuais, contas a pagar e receber, cartões, conciliação e demais módulos. Novos módulos poderão declarar contextos de uso sem exigir novos campos específicos no cadastro de categoria.

> [!IMPORTANT]
> Categoria financeira não determina sozinha o sinal do lançamento nem a escrituração contábil. Direção na conta, natureza econômica, contexto de origem e futuro mapeamento contábil são conceitos relacionados, mas independentes.

> [!NOTE]
> Esta primeira versão cadastra, organiza e disponibiliza categorias. Ela não cria lançamentos, centros de custo, projetos, orçamentos, relatórios financeiros ou escrituração contábil.

## Clarifications

### Session 2026-07-21

- Q: Centros de custo e demais dimensões analíticas pertencem a esta feature? → A: Não. A próxima feature, `financial-dimensions`, definirá dimensões configuráveis pelo tenant, inclusive dimensões personalizadas e dimensões vinculadas a entidades de módulos. Cada categoria poderá marcar cada dimensão aplicável como obrigatória, opcional ou proibida.
- Q: Como relacionar natureza econômica e direção monetária da categoria? → A: Cada categoria possui natureza econômica `RECEITA`, `DESPESA` ou `NEUTRA` e, independentemente, permite direção `ENTRADA`, `SAÍDA` ou `AMBAS`. Estornos e devoluções preservam a natureza original mesmo quando invertem a direção.
- Q: Como organizar categorias em hierarquias sem limitar perspectivas diferentes? → A: Categorias permanecem independentes e podem participar de múltiplas estruturas versionadas. O tenant mantém uma estrutura principal para navegação, e cada categoria aparece no máximo uma vez em cada versão de estrutura.

## User Scenarios & Testing

### User Story 1 - Cadastrar categoria financeira (Priority: P1)

Um usuário autorizado cria uma categoria do tenant com nome, código, descrição e classificação econômica suficientes para que ela seja localizada e utilizada de maneira consistente.

**Why this priority**: Lançamentos sem finalidade identificável produzem saldos, mas não permitem compreender receitas, despesas e demais movimentos financeiros.

**Independent Test**: Cadastrar uma categoria válida, consultá-la no tenant atual e comprovar que ela não existe nem pode ser referenciada em outro tenant.

**Acceptance Scenarios**:

1. **Given** um usuário autorizado no tenant ativo, **When** cadastra uma categoria válida, **Then** o sistema cria uma identidade estável exclusivamente daquele tenant.
2. **Given** um código já usado por outra categoria do tenant, **When** o usuário tenta confirmar o cadastro, **Then** o sistema rejeita a duplicidade sem alterar a categoria existente.
3. **Given** nome semelhante ao de categoria ativa, **When** o usuário confirma que a finalidade é diferente, **Then** o sistema permite o cadastro sem fundir as identidades.

---

### User Story 2 - Disponibilizar categorias por contexto de uso (Priority: P1)

Um usuário autorizado define em quais contextos uma categoria pode ser selecionada, enquanto cada módulo declara seus contextos canônicos sem acrescentar novos atributos booleanos ao cadastro.

**Why this priority**: A mesma lista indiscriminada em todos os fluxos aumenta erros; campos fixos para cada módulo tornam o cadastro rígido e difícil de evoluir.

**Independent Test**: Habilitar uma categoria para lançamento manual e contas a pagar, desabilitá-la para conciliação e verificar que cada seleção respeita o contexto solicitado.

**Acceptance Scenarios**:

1. **Given** uma categoria ativa habilitada para determinado contexto, **When** módulo autorizado solicita categorias elegíveis, **Then** ela é apresentada.
2. **Given** categoria ativa sem habilitação para o contexto solicitado, **When** o módulo consulta opções, **Then** ela não é apresentada como seleção comum.
3. **Given** um novo contexto fornecido por módulo instalado, **When** ele é disponibilizado ao tenant, **Then** categorias podem ser associadas a ele sem alteração da identidade ou dos demais contextos.

---

### User Story 3 - Organizar categorias para navegação e análise (Priority: P2)

Um usuário autorizado organiza categorias em estruturas compreensíveis, com nós totalizadores e ordenação, sem transformar os agrupadores em categorias que possam receber lançamentos.

**Why this priority**: Uma lista plana perde usabilidade conforme o tenant acumula categorias, e uma única apresentação pode não atender gestão, orçamento e relatórios diferentes.

**Independent Test**: Montar uma estrutura com nós e categorias, consultar seus totais simulados e comprovar que nós não podem ser selecionados como categoria de lançamento.

**Acceptance Scenarios**:

1. **Given** uma estrutura válida, **When** o usuário move um nó ou categoria, **Then** somente a organização da estrutura muda e a identidade da categoria permanece intacta.
2. **Given** tentativa de criar ciclo entre nós, **When** a alteração é confirmada, **Then** o sistema rejeita a estrutura inválida.
3. **Given** um nó apenas totalizador, **When** um módulo solicita categorias selecionáveis, **Then** o nó não é retornado como opção de lançamento.

---

### User Story 4 - Manter histórico e substituir categorias (Priority: P2)

Um usuário autorizado altera dados descritivos, desativa categorias que não devem receber novos usos e cria substitutas sem apagar a classificação histórica.

**Why this priority**: A organização financeira evolui, mas relatórios e lançamentos passados precisam continuar explicáveis.

**Independent Test**: Utilizar uma categoria, desativá-la, indicar uma substituta e verificar que novos lançamentos usam somente a substituta enquanto o histórico conserva a categoria original.

**Acceptance Scenarios**:

1. **Given** categoria nunca utilizada, **When** usuário autorizado solicita exclusão, **Then** o sistema a remove definitivamente se não houver nenhuma referência ou dependência.
2. **Given** categoria já utilizada, **When** usuário autorizado tenta excluí-la, **Then** o sistema impede a exclusão e oferece desativação.
3. **Given** categoria desativada com substituta indicada, **When** um módulo abre nova seleção, **Then** apresenta a substituta sem reclassificar silenciosamente lançamentos antigos.

---

### User Story 5 - Preparar classificação de lançamentos compostos (Priority: P2)

Um módulo consumidor poderá futuramente distribuir um lançamento entre várias categorias, preservando o valor atribuído a cada finalidade e mantendo dimensões analíticas independentes.

**Why this priority**: Uma única movimentação pode representar finalidades diferentes, e misturar categoria com centro de custo impediria análises corretas.

**Independent Test**: Validar o contrato de seleção com duas categorias ativas e comprovar que a soma das parcelas deve corresponder ao valor classificado, sem criar centro de custo ou lançamento nesta feature.

**Acceptance Scenarios**:

1. **Given** duas categorias elegíveis, **When** módulo consumidor prepara uma classificação dividida, **Then** cada parcela preserva categoria e valor próprios.
2. **Given** parcelas cuja soma difere do valor a classificar, **When** o consumidor tenta confirmar a operação futura, **Then** o contrato exige correção da diferença.
3. **Given** categoria com sugestão analítica futura, **When** ela é selecionada, **Then** a sugestão não altera a identidade nem a finalidade econômica da categoria.

### Edge Cases

- Categoria com nome igual em estruturas ou finalidades diferentes.
- Código que difere apenas por caixa, espaços ou pontuação não significativa.
- Categoria desativada ainda referenciada por lançamento, recorrência ou regra futura.
- Categoria substituta posteriormente desativada.
- Cadeia ou ciclo de substituições.
- Contexto de uso removido porque um módulo foi desativado.
- Categoria associada a contexto fornecido por módulo não mais instalado.
- Estrutura que omite categorias ativas.
- Categoria repetida na mesma estrutura ou em estruturas diferentes.
- Alteração da classificação econômica após a categoria ter sido utilizada.
- Divisão de lançamento com arredondamento entre várias categorias.
- Operações concorrentes alteram categoria, contexto, substituta ou estrutura.
- Tentativa de associar categoria, contexto ou estrutura de outro tenant.

## Requirements

### Conceitos e Fronteiras

- **FR-FCAT-BOUND-001**: Toda categoria financeira DEVE pertencer exclusivamente a um tenant e somente poderá ser criada, consultada, organizada ou alterada em contexto válido desse tenant.
- **FR-FCAT-BOUND-002**: Categoria DEVE representar a finalidade econômica de um valor e NÃO DEVE representar conta financeira, forma de pagamento, instrumento, contraparte, documento, centro de custo, projeto, orçamento ou conta contábil.
- **FR-FCAT-BOUND-003**: Cadastrar categoria NÃO DEVE criar lançamento, saldo, regra contábil, dimensão analítica, permissão ou configuração de outro módulo.
- **FR-FCAT-BOUND-004**: A identidade da categoria DEVE ser imutável e independente de código, nome, contexto de uso, estrutura, estado ou futura associação contábil.
- **FR-FCAT-BOUND-005**: Um lançamento futuro DEVE poder preservar separadamente categoria econômica, direção na conta, contraparte, documento de origem e dimensões analíticas.
- **FR-FCAT-BOUND-006**: Centros de custo e demais dimensões analíticas DEVEM ser especificados em `financial-dimensions`, imediatamente após esta feature e antes de `financial-transactions`.
- **FR-FCAT-BOUND-007**: O tenant DEVE poder criar dimensões personalizadas e usar dimensões fornecidas por módulos, mas categorias apenas DEVEM declarar sua aplicabilidade; cadastro de dimensão, valores, hierarquias e vínculos com entidades pertencem à feature própria.

### Identificação e Classificação Econômica

- **FR-FCAT-ID-001**: Toda categoria DEVE possuir nome apresentável, código único no tenant e PODERÁ possuir descrição, cor, ícone, rótulos e ordem preferencial.
- **FR-FCAT-ID-002**: Código DEVE ser normalizado para unicidade e permanecer estável depois do primeiro uso; alteração posterior exige substituição ou procedimento excepcional auditado que preserve referências.
- **FR-FCAT-ID-003**: Nomes iguais ou semelhantes DEVEM gerar alerta e permanecer permitidos quando código e finalidade distinguirem as categorias.
- **FR-FCAT-ID-004**: Código, nome e descrição NÃO DEVEM incorporar a identidade de conta financeira, pessoa, centro de custo ou módulo consumidor.
- **FR-FCAT-NATURE-001**: Categoria DEVE declarar classificação econômica suficiente para distinguir entradas econômicas, saídas econômicas e movimentos que não representam receita nem despesa.
- **FR-FCAT-NATURE-002**: A direção de crédito ou débito na conta financeira DEVE permanecer independente da classificação econômica da categoria.
- **FR-FCAT-NATURE-003**: Estorno, devolução ou reversão DEVE preservar referência à operação original e NÃO DEVE exigir que a categoria seja reinterpretada apenas porque a direção monetária foi invertida.
- **FR-FCAT-NATURE-004**: Categoria DEVE declarar exatamente uma natureza econômica entre `RECEITA`, `DESPESA` e `NEUTRA`.
- **FR-FCAT-NATURE-005**: Categoria NÃO DEVE determinar sozinha débito, crédito, conta de contrapartida ou efeito patrimonial da futura escrituração contábil.
- **FR-FCAT-NATURE-006**: Transferências entre contas do tenant, movimentações de principal, adiantamentos, ajustes patrimoniais e operações semelhantes DEVEM poder usar classificação neutra sem inflar receitas ou despesas.
- **FR-FCAT-NATURE-007**: Categoria DEVE declarar, independentemente da natureza econômica, se admite direção monetária `ENTRADA`, `SAÍDA` ou `AMBAS`.
- **FR-FCAT-NATURE-008**: Natureza econômica NÃO DEVE ser inferida da direção monetária nem a direção DEVE ser inferida da natureza.
- **FR-FCAT-NATURE-009**: Módulo consumidor DEVE rejeitar novo uso cuja direção seja incompatível com a categoria, exceto estorno ou reversão explicitamente vinculado à operação original e autorizado pelo respectivo contrato.

### Contextos de Uso e Aplicabilidade

- **FR-FCAT-CTX-001**: Contexto de uso DEVE possuir chave canônica estável, nome apresentável, módulo provedor, descrição e estado.
- **FR-FCAT-CTX-002**: Sistema e módulos DEVEM poder fornecer contextos como lançamento manual, contas a pagar, contas a receber, cartão, conciliação, correção, diferença de caixa, tributo, folha e transferência sem acrescentar campo específico à categoria.
- **FR-FCAT-CTX-003**: Categoria ativa DEVE poder ser habilitada para zero, um ou vários contextos compatíveis.
- **FR-FCAT-CTX-004**: Ausência de habilitação para um contexto DEVE impedir seleção comum nesse contexto, mas NÃO DEVE invalidar referências históricas existentes.
- **FR-FCAT-CTX-005**: Contexto indisponível ou inativo NÃO DEVE apagar associações nem categorias; deve impedir novos usos enquanto preservar histórico e permitir futura reativação compatível.
- **FR-FCAT-CTX-006**: Módulo consumidor DEVE solicitar categorias por chave de contexto e tenant e NÃO DEVE inferir aplicabilidade por nome, posição ou campo específico de outro módulo.
- **FR-FCAT-CTX-007**: Contextos DEVEM poder restringir naturezas econômicas compatíveis e informar quando a categoria é obrigatória, opcional ou indisponível para o fluxo consumidor.
- **FR-FCAT-CTX-008**: Alteração de aplicabilidade DEVE produzir auditoria e afetar somente novas seleções, sem remover a categoria de operações existentes.

### Estruturas e Hierarquias

- **FR-FCAT-ORG-001**: Categorias DEVEM permanecer independentes de hierarquia e PODERÃO participar de múltiplas estruturas versionadas de navegação, análise ou relatório.
- **FR-FCAT-ORG-002**: Estrutura DEVE possuir identidade, nome, finalidade, estado e versão próprios, sem receber lançamento ou manter valor independente.
- **FR-FCAT-ORG-003**: Nó de estrutura DEVE poder conter outros nós e referências a categorias, com nome, ação de apresentação e ordem definidos.
- **FR-FCAT-ORG-004**: Estrutura NÃO DEVE admitir ciclos, referências cruzadas entre tenants nem nó simultaneamente subordinado a pais incompatíveis dentro da mesma versão.
- **FR-FCAT-ORG-005**: Nó totalizador DEVE derivar valores das categorias e subnós incluídos e NÃO DEVE ser selecionável como categoria de lançamento.
- **FR-FCAT-ORG-006**: Estrutura DEVE poder identificar categorias omitidas, repetidas ou incompatíveis antes de sua ativação, sem alterar as categorias.
- **FR-FCAT-ORG-007**: Alteração ativada de estrutura DEVE possuir vigência e preservar versão anterior suficiente para reproduzir relatórios históricos segundo a estrutura válida na data solicitada.
- **FR-FCAT-ORG-008**: Uma versão em edição NÃO DEVE afetar seleções, relatórios ou versões ativas até ser validada e ativada.
- **FR-FCAT-ORG-009**: Desativar estrutura NÃO DEVE desativar categorias nem apagar composições históricas.
- **FR-FCAT-ORG-010**: Cada categoria DEVE aparecer no máximo uma vez dentro da mesma versão de estrutura, mas PODERÁ ocupar posições diferentes em estruturas distintas.
- **FR-FCAT-ORG-011**: Tenant com estruturas ativas DEVE possuir exatamente uma estrutura marcada como principal para navegação comum; estruturas adicionais NÃO DEVEM alterar a identidade nem a classificação econômica das categorias.
- **FR-FCAT-ORG-012**: Alteração da estrutura principal DEVE ser auditada e afetar somente a navegação padrão, sem reclassificar lançamentos nem alterar relatórios que indiquem explicitamente outra estrutura ou versão.

### Divisão e Dimensões Analíticas

- **FR-FCAT-ALLOC-001**: Contrato da categoria DEVE permitir que um lançamento futuro seja classificado em uma ou várias parcelas de categoria.
- **FR-FCAT-ALLOC-002**: Cada parcela futura DEVE possuir valor na moeda do lançamento e referência a uma única categoria elegível.
- **FR-FCAT-ALLOC-003**: Soma das parcelas de categoria DEVE corresponder exatamente ao valor classificado após aplicar a política monetária e de arredondamento da futura feature de lançamentos.
- **FR-FCAT-ALLOC-004**: Dimensões analíticas, como centro de custo, departamento, projeto, unidade, contrato ou finalidade gerencial, DEVEM permanecer independentes das parcelas de categoria e poder classificá-las conforme feature própria.
- **FR-FCAT-ALLOC-005**: Sugestão ou distribuição analítica padrão associada futuramente a categoria DEVE ser tratada como valor inicial revisável, salvo quando regra do contexto consumidor a tornar obrigatória.
- **FR-FCAT-ALLOC-006**: Alterar sugestão analítica futura NÃO DEVE reclassificar lançamentos existentes.
- **FR-FCAT-ALLOC-007**: Cada categoria DEVE poder declarar, para cada dimensão ativa e aplicável do tenant, a política `OBRIGATÓRIA`, `OPCIONAL` ou `PROIBIDA`.
- **FR-FCAT-ALLOC-008**: Política `OBRIGATÓRIA` DEVE impedir confirmação de parcela futura da categoria sem valor ou distribuição válida naquela dimensão.
- **FR-FCAT-ALLOC-009**: Política `OPCIONAL` DEVE aceitar ausência ou preenchimento válido da dimensão sem criar valor implícito.
- **FR-FCAT-ALLOC-010**: Política `PROIBIDA` DEVE impedir que a parcela futura da categoria carregue valor naquela dimensão, inclusive por preenchimento padrão ou automação.
- **FR-FCAT-ALLOC-011**: A política DEVE ser validada por parcela de categoria; em um mesmo lançamento dividido, uma categoria poderá exigir dimensão que outra torne opcional ou proibida.
- **FR-FCAT-ALLOC-012**: Dimensão recém-criada DEVE aplicar sua política padrão às categorias sem regra específica, e usuário autorizado DEVE poder revisar e sobrescrever essa política por categoria.
- **FR-FCAT-ALLOC-013**: Regra específica da categoria DEVE prevalecer sobre a política padrão da dimensão, sem impedir que o contexto consumidor aplique restrição adicional expressamente definida em sua própria feature.
- **FR-FCAT-ALLOC-014**: Alteração de política dimensional DEVE possuir vigência, afetar somente novas confirmações e NÃO DEVE invalidar ou modificar classificações históricas.
- **FR-FCAT-ALLOC-015**: Dimensão desativada ou indisponível NÃO DEVE permitir nova confirmação quando ainda for obrigatória para a categoria; a inconsistência DEVE ser resolvida explicitamente antes do uso.

### Ciclo de Vida e Versionamento

- **FR-FCAT-LIFE-001**: Categoria DEVE possuir estados ativa e inativa.
- **FR-FCAT-LIFE-002**: Categoria ativa e aplicável DEVE estar disponível para novos usos; categoria inativa NÃO DEVE aparecer por padrão em seleções comuns.
- **FR-FCAT-LIFE-003**: Categoria inativa DEVE permanecer pesquisável em consultas históricas autorizadas e PODERÁ ser reativada quando suas classificações continuarem válidas.
- **FR-FCAT-LIFE-004**: Categoria nunca utilizada e sem dependências PODERÁ ser excluída definitivamente por usuário autorizado; caso contrário, somente poderá ser inativada.
- **FR-FCAT-LIFE-005**: Categoria inativa PODERÁ indicar uma categoria substituta ativa e compatível do mesmo tenant para orientar novos usos.
- **FR-FCAT-LIFE-006**: Substituição NÃO DEVE reclassificar silenciosamente lançamentos, recorrências, regras ou documentos existentes.
- **FR-FCAT-LIFE-007**: Cadeia de substituição DEVE terminar em categoria ativa, NÃO DEVE admitir ciclos e DEVE ser apresentada ao usuário antes da confirmação de novo uso.
- **FR-FCAT-LIFE-008**: Nome, descrição, cor, ícone, rótulos e ordem PODERÃO mudar sem criar nova identidade, preservando auditoria quando relevante.
- **FR-FCAT-LIFE-009**: Alteração de código, natureza econômica ou outra semântica depois do primeiro uso DEVE preservar vigência e versão anterior ou exigir categoria substituta; NÃO DEVE reinterpretar silenciosamente o histórico.

### Uso por Outros Módulos e Contabilidade Futura

- **FR-FCAT-USE-001**: Módulo consumidor DEVE referenciar a identidade imutável e, quando aplicável, a versão vigente da categoria no mesmo tenant.
- **FR-FCAT-USE-002**: Seleção DEVE retornar somente categorias ativas, autorizadas e compatíveis com contexto, natureza e data exigidos pelo consumidor.
- **FR-FCAT-USE-003**: Operação histórica DEVE continuar exibindo a categoria e a classificação vigentes quando foi confirmada, mesmo que cadastro atual tenha sido alterado ou desativado.
- **FR-FCAT-USE-004**: Categoria PODERÁ participar de futuro mapeamento contábil, mas a associação DEVE possuir identidade, vigência e regras próprias.
- **FR-FCAT-USE-005**: Mapeamento contábil futuro DEVE poder considerar categoria, conta financeira, contexto, contraparte, dimensões, tributos e operação de origem; NÃO DEVE presumir relação fixa de uma categoria para uma única conta contábil.
- **FR-FCAT-USE-006**: Categoria NÃO DEVE conter campos específicos de documentos fiscais, formas de pagamento ou provedores; esses módulos DEVEM usar contextos ou contratos próprios.
- **FR-FCAT-USE-007**: Importação futura de categorias DEVE detectar conflitos de código e identidade e NÃO DEVE fundir registros silenciosamente.

### Autorização, Auditoria e Isolamento

- **FR-FCAT-SEC-001**: Acesso a categorias, contextos e estruturas DEVE ser negado por padrão e liberado por chaves específicas do tenant.
- **FR-FCAT-SEC-002**: Consultar, criar, alterar dados descritivos, alterar semântica, administrar contextos, administrar estruturas, ativar versão, inativar, reativar e excluir DEVEM poder ser autorizados separadamente quando o risco diferir.
- **FR-FCAT-SEC-003**: Interfaces e operações internas DEVEM aplicar o mesmo contrato de autorização e isolamento.
- **FR-FCAT-SEC-004**: Criação, alteração semântica, mudança de contexto, ativação de estrutura, substituição, mudança de estado, exclusão e tentativa sensível negada DEVEM registrar ator, tenant, instante, origem, motivo, resultado e efeito.
- **FR-FCAT-SEC-005**: Auditoria acessível a usuários comuns NÃO DEVE revelar categorias, estruturas ou operações de outro tenant nem permissões que o usuário não possua.

### Pesquisa, Experiência e Acessibilidade

- **FR-FCAT-UX-001**: Usuário autorizado DEVE poder pesquisar e filtrar categorias por código, nome, natureza, contexto, estado e substituta.
- **FR-FCAT-UX-002**: Listagens e seleções DEVEM distinguir categoria, agrupador de estrutura e dimensão analítica e NÃO DEVEM apresentar nó totalizador como categoria.
- **FR-FCAT-UX-003**: Formulário DEVE explicar a diferença entre natureza econômica e direção do dinheiro na conta.
- **FR-FCAT-UX-004**: Antes de alteração semântica, desativação ou substituição, a interface DEVE apresentar o impacto sobre novos usos e a preservação do histórico.
- **FR-FCAT-UX-005**: Seleções frequentes DEVEM priorizar categorias elegíveis, recentes ou favoritas sem ocultar pesquisa completa autorizada.
- **FR-FCAT-UX-006**: Estado inativo, incompatibilidade de contexto e existência de substituta DEVEM ser perceptíveis sem depender exclusivamente de cor.
- **FR-FCAT-UX-007**: Jornadas principais DEVEM ser realizáveis por teclado e tecnologias assistivas, com erros associados aos campos correspondentes.

### Decisões de Infraestrutura Auditáveis

> Decisões de infraestrutura: não há agendamento, token externo nem rotação criptográfica próprios desta feature. Todas as mudanças ocorrem por operações transacionais explícitas.

- **FR-FCAT-INFRA-IDEMP**: Repetição técnica da mesma solicitação confirmada DEVE produzir no máximo uma categoria, associação de contexto, versão de estrutura ou mudança de estado.
- **FR-FCAT-INFRA-LOCK**: Operações concorrentes DEVEM preservar unicidade de código, ausência de ciclos, versão ativa única por estrutura, substituições válidas e histórico determinístico em todas as instâncias da aplicação.
- **FR-FCAT-INFRA-BACKUP**: Categorias, contextos, estruturas, versões, estados, substituições e auditorias DEVEM participar dos backups e restaurações do tenant segundo `tenant-data-governance`.

### Key Entities

- **Categoria Financeira**: identidade estável da finalidade econômica atribuível a parcelas de lançamentos do tenant.
- **Versão da Categoria**: semântica e vigência preservadas quando alteração relevante não pode reinterpretar o histórico.
- **Contexto de Uso da Categoria**: capacidade canônica fornecida pelo sistema ou módulo para declarar onde uma categoria pode ser selecionada.
- **Aplicabilidade da Categoria**: associação entre categoria e contexto, com estado e compatibilidades para novos usos.
- **Estrutura de Categorias**: apresentação versionada destinada a navegação, totalização ou relatório, sem receber lançamentos.
- **Nó da Estrutura**: agrupador hierárquico com ordem e ação de apresentação, capaz de referenciar subnós e categorias.
- **Substituição de Categoria**: orientação auditável de uma categoria inativa para outra ativa em novos usos, sem reclassificação automática.
- **Parcela de Categoria**: futura parte monetária de um lançamento atribuída a uma categoria; seu ciclo de vida pertence a `financial-transactions`.
- **Dimensão Analítica**: eixo independente de análise, como centro de custo, projeto ou departamento, cujo cadastro, valores e alocação pertencem a `financial-dimensions`.
- **Regra Dimensional da Categoria**: política versionada que torna uma dimensão obrigatória, opcional ou proibida para parcelas de determinada categoria.
- **Mapeamento Contábil**: associação futura, versionada e contextual entre classificações financeiras e regras de escrituração.

## Success Criteria

### Measurable Outcomes

- **SC-FCAT-001**: Em 100% dos testes cruzados, categoria, contexto, estrutura ou substituição de um tenant não é consultado, alterado nem referenciado por outro tenant.
- **SC-FCAT-002**: Usuários autorizados cadastram uma categoria simples e a habilitam para um contexto em até 90 segundos durante testes de usabilidade.
- **SC-FCAT-003**: Em 100% dos testes, código normalizado permanece único no tenant e nomes semelhantes não provocam fusão automática.
- **SC-FCAT-004**: Em 100% das seleções testadas, somente categorias ativas e compatíveis com tenant, contexto, natureza e data solicitados são oferecidas.
- **SC-FCAT-005**: Novo contexto fornecido por módulo pode ser associado a categorias sem acrescentar atributo funcional específico ao cadastro da categoria.
- **SC-FCAT-006**: Em 100% dos testes históricos, desativação, substituição ou nova versão não altera a categoria originalmente referenciada.
- **SC-FCAT-007**: Em 100% das estruturas testadas, ciclos e referências cruzadas entre tenants são rejeitados e agrupadores não são selecionáveis como categorias.
- **SC-FCAT-008**: Em 100% das divisões simuladas, parcelas de categoria correspondem exatamente ao valor classificado ou a operação é rejeitada com diferença identificada.
- **SC-FCAT-009**: Em 100% dos testes, direção monetária invertida por estorno ou devolução não altera automaticamente a natureza econômica da categoria original.
- **SC-FCAT-010**: Em 100% dos testes, categoria não cria conta financeira, dimensão analítica, lançamento, saldo nem conta contábil.
- **SC-FCAT-011**: Pelo menos 95% das pesquisas e seleções comuns retornam resultado utilizável em até 2 segundos nas condições operacionais definidas para a primeira versão.
- **SC-FCAT-012**: Em 100% dos testes concorrentes e de repetição técnica, cada operação confirmada produz no máximo um efeito e preserva códigos, versões, hierarquias e substituições válidos.
- **SC-FCAT-013**: Todas as jornadas principais podem ser concluídas apenas por teclado e sem bloqueios críticos para tecnologias assistivas.
- **SC-FCAT-014**: Em 100% dos testes dimensionais, parcela de categoria sem dimensão obrigatória ou com dimensão proibida é rejeitada, enquanto dimensão opcional aceita ausência ou preenchimento válido.
- **SC-FCAT-015**: Em 100% dos lançamentos simulados com várias categorias, as regras dimensionais são avaliadas separadamente para cada parcela sem preencher ou remover valores silenciosamente.
- **SC-FCAT-016**: Em 100% dos testes, natureza econômica e direção monetária são validadas independentemente, e estorno vinculado preserva a natureza original ao inverter a direção.
- **SC-FCAT-017**: Em 100% dos testes, uma categoria aparece no máximo uma vez por versão de estrutura, pode ocupar posições diferentes em estruturas distintas e existe exatamente uma estrutura principal quando há estruturas ativas.

## Fora do Escopo

- Cadastro de contas financeiras e cálculo de saldos.
- Criação, efetivação, estorno ou conciliação de lançamentos.
- Contas a pagar, contas a receber, cartões, faturas e transferências.
- Plano de contas, diários, partidas dobradas e escrituração contábil.
- Execução de mapeamentos contábeis ou geração de lançamentos contábeis.
- Orçamentos, metas, previsões e alertas de consumo.
- Relatórios financeiros finais e demonstrativos contábeis.
- Importação ou sincronização externa de categorias nesta primeira versão.
