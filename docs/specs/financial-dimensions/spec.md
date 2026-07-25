# Feature Specification: Dimensões Financeiras

**Feature**: `financial-dimensions`
**Created**: 2026-07-21
**Status**: Clarified — pronta para planejamento

## Escopo

Esta feature estabelece dimensões analíticas configuráveis por tenant para classificar valores financeiros por eixos independentes da finalidade econômica, como centro de custo, projeto, departamento, unidade, contrato, veículo, evento ou qualquer organização criada pelo usuário. Uma dimensão responde **sob qual perspectiva o valor deve ser analisado ou controlado**, enquanto a categoria responde **o que o valor representa**.

Dimensões podem ter valores administrados diretamente pelo tenant ou fornecidos por entidades de outros módulos. Cada dimensão possui política padrão de aplicabilidade, e cada categoria pode torná-la obrigatória, opcional ou proibida. O lançamento oferece uma única experiência de distribuição dimensional, mas cada dimensão respeita a base monetária formada pelas parcelas principais às quais se aplica; componentes especiais herdam proporcionalmente essas bases sem aplicar as políticas dimensionais de suas categorias automáticas.

> [!IMPORTANT]
> Dimensão financeira não é campo personalizado livre. Ela é um eixo categórico com identidade, valores controlados, estado atual, validação e contratos de uso. Textos, números ou datas livres pertencem aos documentos e módulos que originam a operação.

> [!NOTE]
> Esta primeira versão cadastra dimensões, valores, estruturas, aplicabilidades e modelos de distribuição. Ela não cria lançamentos, orçamentos, aprovações, permissões por dimensão, projetos nem entidades dos módulos provedores.

## Clarifications

### Session 2026-07-21

- Q: Dimensões podem ser configuradas pelo tenant e ter obrigatoriedade definida por categoria? → A: Sim. O tenant pode criar dimensões próprias e usar dimensões fornecidas por módulos. Cada dimensão possui política padrão, e cada categoria principal pode sobrescrevê-la como obrigatória, opcional ou proibida para delimitar sua participação na base dimensional do lançamento.
- Q: Como preservar rateios e cruzamentos entre várias dimensões? → A: A distribuição é conjunta no lançamento. Cada dimensão respeita a base formada pelas parcelas principais em que é aplicável, componentes especiais são repartidos proporcionalmente entre essas bases e uma matriz monetária balanceada preserva totais, combinações e arredondamento global.
- Q: Como combinar regras deliberadas e sugestões aprendidas pelo uso? → A: A primeira versão adota modelo híbrido: modelos explícitos, condicionais e priorizados administrados pelo tenant coexistem com sugestões históricas pontuadas a partir de distribuições confirmadas; nenhuma sugestão confirma operação automaticamente.
- Q: Como organizar hierarquicamente os valores de uma dimensão? → A: Valores permanecem independentes e podem participar de múltiplas estruturas atuais da mesma dimensão. Uma estrutura ativa é marcada como principal para navegação padrão, sem impedir perspectivas alternativas nem permitir que o financeiro altere a hierarquia pertencente ao módulo provedor.

### Session 2026-07-24

- Q: Dimensões, valores, aplicabilidades e modelos devem possuir períodos de vigência ou versões históricas próprias? → A: Não. Eles usam somente o estado atual no instante da confirmação, independentemente da data financeira. Rascunhos e pré-lançamentos são revalidados, distribuições confirmadas preservam as identidades efetivamente usadas e datas funcionais de projetos, contratos ou outras entidades permanecem sob responsabilidade do módulo provedor. Estruturas hierárquicas constituem decisão separada.
- Q: Inativar dimensão ou valor deve impedir a liquidação, correção ou reversão de fato já confirmado que o utilizou? → A: Não. A inativação impede novas classificações, inclusive confirmação de rascunhos, pré-lançamentos e propostas recorrentes ainda não materializadas, mas distribuições vinculadas a fatos confirmados podem preservar a dimensão ou valor original para liquidação, correção ou estorno. Modelos e sugestões incompatíveis deixam de ser oferecidos, e substitutos nunca são aplicados automaticamente.
- Q: Estruturas hierárquicas devem manter versões e vigências para reconstruir sua organização passada? → A: Não. Cada dimensão pode possuir múltiplas estruturas atuais e exatamente uma principal, sem versionamento nem vigência. Alterações afetam somente a apresentação atual; distribuições preservam os valores dimensionais, mas relatórios não reconstroem automaticamente a posição hierárquica antiga. O usuário poderá duplicar uma estrutura como perspectiva independente antes de reorganizá-la.
- Q: Qual política deve iniciar uma nova dimensão e como a categoria deve representar a herança desse padrão? → A: A política padrão é obrigatória e configurável, mas inicia pré-selecionada como `OPCIONAL`. Cada categoria apresenta `PADRÃO DA DIMENSÃO`, `PROIBIDA`, `OPCIONAL` e `OBRIGATÓRIA`; a primeira opção significa ausência de sobrescrita persistida e herança dinâmica do padrão atual, cujo resultado resolvido deve ser exibido ao usuário.

### Session 2026-07-25

- Q: Como distribuir dimensionalmente um lançamento com várias categorias principais e campos especiais como juros, multa, desconto e acréscimo? → A: A distribuição é operada uma única vez no lançamento, mas a base máxima de cada dimensão é a soma das parcelas principais às quais ela se aplica. Componentes especiais usam categorias mapeadas pelo tenant somente para classificação econômica, ignoram as políticas dimensionais dessas categorias e são repartidos proporcionalmente pelas parcelas principais. O sistema persiste o modo percentual, recalcula quando o total muda e produz uma única matriz monetária balanceada pelo método dos maiores resíduos, preservando totais econômicos e dimensionais.

## User Scenarios & Testing

### User Story 1 - Criar dimensão personalizada (Priority: P1)

Um usuário autorizado cria no tenant um eixo analítico próprio, define sua identificação, finalidade e política padrão e começa a cadastrar valores que poderão classificar operações futuras.

**Why this priority**: Diferentes pessoas e empresas analisam finanças por estruturas distintas; fixar somente centro de custo ou projeto impediria adaptação sem alteração do sistema.

**Independent Test**: Criar uma dimensão “Obra”, cadastrar dois valores e comprovar que dimensão e valores existem somente no tenant atual.

**Acceptance Scenarios**:

1. **Given** usuário autorizado no tenant ativo, **When** cadastra dimensão personalizada válida, **Then** o sistema cria identidade estável exclusivamente daquele tenant.
2. **Given** nome normalizado de dimensão já utilizado no tenant, **When** usuário confirma novo cadastro, **Then** o sistema rejeita a duplicidade sem alterar a dimensão existente.
3. **Given** dimensão ativa com valores ativos, **When** módulo consumidor solicita dimensões aplicáveis, **Then** recebe definição e valores compatíveis sem conhecer um tipo fixo como centro de custo.

---

### User Story 2 - Usar dimensão fornecida por módulo (Priority: P1)

Um usuário autorizado habilita uma dimensão cujo conjunto de valores é mantido por outro módulo, como projetos, unidades ou contratos, sem duplicar esses cadastros no financeiro.

**Why this priority**: Cópias independentes criam divergência, referências obsoletas e trabalho duplicado; a dimensão deve reutilizar a fonte funcional quando ela existir.

**Independent Test**: Disponibilizar uma dimensão de teste fornecida por módulo, consultar seus valores no mesmo tenant e impedir edição financeira de atributos pertencentes à entidade original.

**Acceptance Scenarios**:

1. **Given** módulo que fornece dimensão e valores ativos, **When** usuário habilita a dimensão, **Then** os valores ficam selecionáveis sem serem copiados como cadastros independentes.
2. **Given** valor fornecido por módulo, **When** usuário tenta alterar seu nome ou identidade pelo financeiro, **Then** o sistema impede a alteração e orienta para o módulo responsável.
3. **Given** módulo indisponível ou desativado, **When** histórico é consultado, **Then** referências dimensionais existentes continuam identificáveis e novos usos seguem a política de indisponibilidade definida.

---

### User Story 3 - Organizar valores dimensionais (Priority: P2)

Um usuário autorizado organiza valores de uma dimensão em estruturas hierárquicas para navegação, totalização e diferentes perspectivas de análise, sem transformar os nós em valores selecionáveis.

**Why this priority**: Dimensões podem conter centenas de projetos, unidades ou centros de custo e precisam permanecer navegáveis e úteis para diferentes relatórios.

**Independent Test**: Criar estrutura hierárquica para uma dimensão, mover valores entre nós e comprovar que o valor mantém identidade e referências históricas.

**Acceptance Scenarios**:

1. **Given** estrutura válida, **When** valor é movido entre nós, **Then** somente sua organização muda e nenhuma classificação histórica é reescrita.
2. **Given** tentativa de criar ciclo, **When** alteração é confirmada, **Then** o sistema rejeita a hierarquia inválida.
3. **Given** nó totalizador, **When** módulo solicita valores selecionáveis, **Then** o nó não é retornado como valor da dimensão.

---

### User Story 4 - Configurar aplicabilidade por categoria (Priority: P1)

Um usuário autorizado determina se uma dimensão é obrigatória, opcional ou proibida para cada categoria, usando a política padrão da dimensão quando não houver regra específica.

**Why this priority**: A dimensão somente agrega qualidade quando é exigida onde faz sentido e impedida onde produziria informação irrelevante.

**Independent Test**: Tornar “Projeto” obrigatório para “Material de construção”, opcional para “Combustível” e proibido para “Papel de escritório”, validando os três comportamentos separadamente.

**Acceptance Scenarios**:

1. **Given** dimensão obrigatória para categoria, **When** parcela futura não contém classificação válida, **Then** sua confirmação é rejeitada.
2. **Given** dimensão opcional para categoria, **When** parcela futura omite a dimensão, **Then** a ausência é aceita sem valor implícito.
3. **Given** dimensão proibida para categoria, **When** preenchimento manual ou automático tenta atribuí-la, **Then** a confirmação é rejeitada até a remoção explícita.

---

### User Story 5 - Distribuir valor entre classificações (Priority: P1)

Um usuário autorizado prepara no lançamento uma distribuição que respeita as bases das categorias principais, reparte componentes especiais e preserva exatamente valores, proporções e combinações dimensionais necessárias para análise futura.

**Why this priority**: Um gasto pode pertencer a vários projetos, departamentos ou unidades, e percentuais independentes que não preservem suas combinações podem produzir cruzamentos incorretos.

**Independent Test**: Dividir o principal em 20% numa categoria aplicável a projetos e 80% numa categoria aplicável a departamentos, acrescentar juros e comprovar bases ajustadas, soma exata e arredondamento global.

**Acceptance Scenarios**:

1. **Given** categorias principais com dimensões distintas, **When** o lançamento é distribuído, **Then** cada dimensão fica limitada à soma ajustada das parcelas principais em que é aplicável.
2. **Given** percentuais que produzem frações da menor unidade monetária, **When** confirmação é solicitada, **Then** o sistema distribui globalmente os resíduos, exibe o resultado e preserva soma exata.
3. **Given** valor dimensional inativo em distribuição histórica, **When** consulta é realizada, **Then** o valor permanece identificável sem ficar disponível para novo uso comum.
4. **Given** juros, multa, desconto ou acréscimo, **When** seu valor altera o total, **Then** o sistema reaplica os percentuais persistidos e recalcula uma única matriz sem exigir novo rateio manual.

---

### User Story 6 - Manter dimensões sem perder histórico (Priority: P2)

Um usuário autorizado desativa dimensões ou valores, indica substitutos quando apropriado e preserva classificações existentes.

**Why this priority**: Projetos terminam, departamentos mudam e estruturas são reorganizadas, mas operações passadas precisam continuar explicáveis.

**Independent Test**: Usar um valor, desativá-lo, indicar substituto e verificar que novos usos seguem a substituição sem alterar o histórico.

**Acceptance Scenarios**:

1. **Given** dimensão ou valor nunca utilizado e sem dependências, **When** usuário autorizado solicita exclusão, **Then** o sistema permite remoção definitiva.
2. **Given** dimensão ou valor já utilizado, **When** exclusão é solicitada, **Then** o sistema impede e oferece desativação.
3. **Given** valor inativo com substituto ativo, **When** nova seleção é aberta, **Then** o sistema orienta para o substituto sem reclassificar operações antigas.

### Edge Cases

- Dimensão e valor com nomes que diferem apenas por caixa, acentuação ou espaços não significativos.
- Duas dimensões possuem valores com o mesmo nome local.
- Dimensão personalizada passa a ter equivalente fornecido por módulo.
- Entidade que fornece valor dimensional é desativada, removida ou transferida de estado.
- Módulo provedor fica temporariamente indisponível.
- Categoria exige dimensão que foi desativada.
- Categoria proíbe dimensão preenchida por modelo de distribuição.
- Distribuição envolve várias categorias com políticas dimensionais diferentes.
- Categorias principais distintas tornam a mesma dimensão obrigatória, opcional ou proibida em bases monetárias diferentes.
- Componente especial positivo ou negativo produz frações ao ser repartido pelas categorias principais.
- Alteração de juros, multa, desconto ou acréscimo muda o total depois do preenchimento percentual.
- Percentuais válidos produzem frações menores que a precisão da moeda.
- Valor substituto posteriormente é desativado.
- Cadeia ou ciclo de substituições.
- Estrutura omite valores ativos ou repete o mesmo valor.
- Alteração de estrutura ou regra durante confirmação concorrente.
- Tentativa de associar dimensão, valor, categoria ou entidade de outro tenant.
- Limite do plano reduzido abaixo da quantidade de dimensões ou valores existentes.

## Requirements

### Conceitos e Fronteiras

- **FR-FDIM-BOUND-001**: Toda dimensão, valor, estrutura, regra e distribuição DEVE pertencer exclusivamente a um tenant e somente poderá ser usada em contexto válido desse tenant.
- **FR-FDIM-BOUND-002**: Dimensão DEVE representar eixo categórico de análise ou controle e NÃO DEVE representar categoria econômica, conta financeira, lançamento, campo livre, documento, permissão ou conta contábil.
- **FR-FDIM-BOUND-003**: Criar dimensão NÃO DEVE criar projeto, departamento, centro de custo, pessoa, lançamento, orçamento, fluxo de aprovação ou permissão.
- **FR-FDIM-BOUND-004**: Identidades de dimensão e valor DEVEM ser imutáveis e independentes de nome, estrutura, estado, política, origem ou associação futura.
- **FR-FDIM-BOUND-005**: Classificação dimensional DEVE permanecer separada da categoria, ser operada no lançamento e usar as parcelas principais categorizadas para determinar as bases monetárias aplicáveis.
- **FR-FDIM-BOUND-006**: Dimensão NÃO DEVE alterar valor, moeda, direção ou natureza econômica da parcela classificada.
- **FR-FDIM-BOUND-007**: Limites comerciais de quantidade de dimensões, valores, estruturas ou regras pertencem a `plans-entitlements`; redução de franquia NÃO DEVE apagar nem invalidar histórico existente.

### Definição e Origem da Dimensão

- **FR-FDIM-DEF-001**: Toda dimensão DEVE possuir nome apresentável e único normalizado no tenant, descrição, estado, política padrão de aplicabilidade e origem, sem código de negócio exposto ao usuário.
- **FR-FDIM-DEF-002**: Origem DEVE distinguir dimensão `PERSONALIZADA`, cujos valores são administrados pelo tenant, e dimensão `FORNECIDA_POR_MÓDULO`, cujos valores referenciam entidades do módulo provedor.
- **FR-FDIM-DEF-003**: Tenant DEVE poder criar dimensões personalizadas sem lista fechada de finalidades, desde que respeite o contrato categórico da feature.
- **FR-FDIM-DEF-004**: Módulo provedor DEVE declarar chave estável da dimensão, identidade estável dos valores, nome apresentável, disponibilidade e regras de consulta no tenant.
- **FR-FDIM-DEF-005**: Dimensão fornecida por módulo NÃO DEVE copiar identidade nem atributos funcionais da entidade para cadastro independente; poderá preservar apresentação histórica mínima quando necessário.
- **FR-FDIM-DEF-006**: Usuário financeiro NÃO DEVE alterar pelo cadastro dimensional atributos pertencentes à entidade fornecedora, mas PODERÁ administrar preferências locais explicitamente permitidas, como ordem, rótulo ou estrutura.
- **FR-FDIM-DEF-007**: Nova versão de módulo NÃO DEVE reutilizar chave de dimensão ou identidade de valor para semântica incompatível.
- **FR-FDIM-DEF-008**: Dimensão PODERÁ declarar finalidade sugerida, como centro de custo, projeto ou unidade, sem que essa finalidade restrinja novas dimensões personalizadas.

### Valores Dimensionais

- **FR-FDIM-VAL-001**: Valor de dimensão personalizada DEVE possuir nome apresentável e único normalizado dentro da dimensão, descrição opcional e estado, sem código de negócio exposto ao usuário.
- **FR-FDIM-VAL-002**: Valores de dimensões diferentes PODERÃO possuir o mesmo nome sem compartilhar identidade.
- **FR-FDIM-VAL-003**: Valor fornecido por módulo DEVE referenciar uma única entidade do mesmo tenant e preservar identidade mesmo quando sua apresentação mudar.
- **FR-FDIM-VAL-004**: Usuário autorizado DEVE poder pesquisar valores por dimensão, correspondência parcial de nome e descrição, estado, origem e estrutura.
- **FR-FDIM-VAL-005**: Valor somente DEVE ser elegível quando dimensão e valor estiverem ativos e disponíveis para o contexto consumidor no instante da confirmação.
- **FR-FDIM-VAL-006**: Alteração de nome ou descrição NÃO DEVE reclassificar distribuições confirmadas; alteração incompatível de significado depois do primeiro uso DEVE exigir substituição, sem versionamento histórico do valor.
- **FR-FDIM-VAL-007**: Valor não encontrado ou temporariamente indisponível no módulo provedor NÃO DEVE ser substituído silenciosamente por outro valor.

### Aplicabilidade por Categoria

- **FR-FDIM-APP-001**: Dimensão DEVE declarar exatamente uma política padrão entre `OBRIGATÓRIA`, `OPCIONAL` e `PROIBIDA` para categorias sem regra específica; novo cadastro DEVE iniciar com `OPCIONAL` pré-selecionada, sem impedir que o usuário escolha outra antes da confirmação.
- **FR-FDIM-APP-002**: Cada categoria DEVE poder herdar dinamicamente o padrão ou sobrescrevê-lo para cada dimensão ativa e aplicável do tenant.
- **FR-FDIM-APP-003**: Regra específica da categoria DEVE prevalecer sobre o padrão da dimensão; opção `PADRÃO DA DIMENSÃO` DEVE representar ausência de sobrescrita persistida e resolver dinamicamente a política atual.
- **FR-FDIM-APP-004**: Contexto consumidor PODERÁ impor restrição adicional documentada em sua própria feature, mas NÃO DEVE relaxar dimensão obrigatória nem permitir dimensão proibida pela categoria.
- **FR-FDIM-APP-005**: Política obrigatória DEVE exigir distribuição válida; política opcional DEVE aceitar ausência ou distribuição válida; política proibida DEVE exigir ausência.
- **FR-FDIM-APP-006**: Regras DEVEM manter somente o estado atual e afetar confirmações posteriores, inclusive de rascunhos, pré-lançamentos e fatos com data financeira retroativa, preservando distribuições já confirmadas.
- **FR-FDIM-APP-007**: Desativação de dimensão obrigatória DEVE bloquear novos usos das categorias afetadas até que a regra ou a dimensão seja regularizada explicitamente.
- **FR-FDIM-APP-008**: Interface DEVE permitir revisar, filtrar e alterar em lote políticas de categorias para determinada dimensão, com prévia do impacto.
- **FR-FDIM-APP-009**: Edição de categoria DEVE apresentar as quatro escolhas `PADRÃO DA DIMENSÃO`, `PROIBIDA`, `OPCIONAL` e `OBRIGATÓRIA`, deixando claro que somente as três últimas criam sobrescrita.
- **FR-FDIM-APP-010**: Interface DEVE exibir a política efetivamente resolvida ao lado da origem, como `Padrão da dimensão (Opcional)`, e alteração do padrão DEVE afetar confirmações posteriores de todas as categorias sem sobrescrita.
- **FR-FDIM-APP-011**: Antes de alterar o padrão, o sistema DEVE apresentar impacto sobre categorias herdadas e operações ainda não confirmadas que possam exigir revisão, sem modificar distribuições confirmadas.

### Distribuições Dimensionais

- **FR-FDIM-ALLOC-001**: Distribuição dimensional DEVE pertencer ao lançamento e classificar seu valor líquido total por meio das bases monetárias derivadas das parcelas principais categorizadas, sem exigir rateio separado dos componentes especiais.
- **FR-FDIM-ALLOC-002**: Distribuição DEVE distinguir os modos `PERCENTUAL` e `VALOR`; no modo percentual, o sistema DEVE persistir percentuais, ordem estável, valores monetários resultantes e valor total usado no cálculo.
- **FR-FDIM-ALLOC-003**: Alteração de principal, juros, multa, desconto, acréscimo ou outro componente que modifique o total DEVE reaplicar automaticamente os percentuais persistidos e recalcular toda a distribuição antes da confirmação.
- **FR-FDIM-ALLOC-004**: Toda distribuição confirmada DEVE preservar valores monetários exatos na moeda do lançamento; percentuais são intenção de cálculo, e os montantes resultantes são a representação monetária efetiva.
- **FR-FDIM-ALLOC-005**: Para cada dimensão, a base máxima distribuível DEVE corresponder à soma das parcelas principais em que a dimensão seja obrigatória ou opcional efetivamente escolhida, excluídas as parcelas em que seja proibida ou opcionalmente omitida.
- **FR-FDIM-ALLOC-006**: Dimensão obrigatória DEVE abranger integralmente a base aplicável; dimensão opcional DEVE ser aplicada integralmente à parcela principal escolhida ou omitida; dimensão proibida NÃO DEVE compor sua base.
- **FR-FDIM-ALLOC-007**: Juros, multa, desconto incondicional, desconto condicional, acréscimo e demais componentes especiais DEVEM ser repartidos proporcionalmente entre as parcelas principais e ajustar suas bases dimensionais, sem aplicar as políticas dimensionais das categorias automáticas usadas para classificá-los.
- **FR-FDIM-ALLOC-008**: Soma algébrica das bases principais ajustadas pelos componentes especiais DEVE corresponder exatamente ao valor líquido total do lançamento.
- **FR-FDIM-ALLOC-009**: Distribuição DEVE preservar combinações conjuntas quando duas ou mais dimensões classificarem a mesma base; cada componente conjunto DEVE possuir montante e no máximo um valor de cada dimensão aplicável.
- **FR-FDIM-ALLOC-010**: Totais de dimensões independentes NÃO DEVEM ser somados entre si como novos valores monetários; o mesmo montante poderá ser observado integralmente por projeto, departamento ou outro eixo simultâneo.
- **FR-FDIM-ALLOC-011**: O sistema DEVE calcular uma única matriz monetária balanceada capaz de preservar simultaneamente valor líquido do lançamento, valores algébricos dos componentes econômicos, proporções das parcelas principais, bases dimensionais e combinações informadas.
- **FR-FDIM-ALLOC-012**: Frações da menor unidade monetária DEVEM ser resolvidas globalmente pelo método dos maiores resíduos; empates DEVEM usar ordem estável e o resultado DEVE ser o mais homogêneo possível sem diferença oculta.
- **FR-FDIM-ALLOC-013**: Repetição do cálculo com as mesmas entradas e ordem DEVE produzir exatamente a mesma matriz; nenhum resíduo DEVE criar categoria, dimensão ou componente privilegiado de arredondamento.
- **FR-FDIM-ALLOC-014**: Cada componente DEVE referenciar somente dimensões e valores do mesmo tenant, ativos e disponíveis no instante da confirmação.
- **FR-FDIM-ALLOC-015**: Valor zero NÃO DEVE criar componente persistente nem satisfazer dimensão obrigatória.
- **FR-FDIM-ALLOC-016**: Dois componentes com a mesma combinação dimensional dentro da mesma base DEVEM ser consolidados ou rejeitados antes da confirmação, evitando duplicidade indistinguível.
- **FR-FDIM-ALLOC-017**: Distribuição conjunta DEVE permitir recuperar totais por dimensão isolada, por categoria econômica, por componente especial e pelas combinações preservadas, sem presumir produto cartesiano.
- **FR-FDIM-ALLOC-018**: Alteração posterior de dimensão, valor, estrutura ou regra NÃO DEVE modificar matriz nem distribuição já confirmadas.
- **FR-FDIM-ALLOC-019**: Distribuição negativa ou estorno DEVE preservar a matriz original com sinais compatíveis e seguir o contrato de reversão de `financial-transactions`, sem reaplicar regras atuais.
- **FR-FDIM-ALLOC-020**: No modo `VALOR`, alteração do total NÃO DEVE redistribuir silenciosamente montantes fixos; diferença resultante DEVE ser apresentada para resolução explícita antes da confirmação.

### Modelos e Regras de Distribuição

- **FR-FDIM-RULE-001**: A primeira versão DEVE oferecer modelos explícitos condicionais e priorizados e sugestões históricas pontuadas, apresentados de forma distinta e sem confirmação automática.
- **FR-FDIM-RULE-002**: Modelo de distribuição NÃO DEVE confirmar operação sem revisão quando o contexto consumidor exigir confirmação do usuário.
- **FR-FDIM-RULE-003**: Resultado sugerido DEVE respeitar aplicabilidade e estados atuais da categoria, dimensões e valores, além de soma exata, antes de ser aceito.
- **FR-FDIM-RULE-004**: Conflito entre sugestões NÃO DEVE combinar distribuições incompatíveis silenciosamente; a precedência e o resultado DEVEM ser explicáveis.
- **FR-FDIM-RULE-005**: Alteração de modelo ou regra NÃO DEVE modificar classificações históricas nem operações futuras já confirmadas.
- **FR-FDIM-RULE-006**: Uso de modelo DEVE preservar a identidade da regra e o resultado efetivamente aceito quando essa informação for necessária para auditoria, sem depender de versão histórica operacional do modelo.
- **FR-FDIM-RULE-007**: Modelo explícito DEVE possuir nome, prioridade, estado, condições compostas por atributos canônicos oferecidos pelo sistema ou módulo consumidor e resultado de distribuição conjunta.
- **FR-FDIM-RULE-008**: Usuário NÃO DEVE cadastrar scripts, expressões executáveis nem consultas arbitrárias como condição; novos tipos de condição DEVEM ser fornecidos por contrato versionado do sistema ou módulo.
- **FR-FDIM-RULE-009**: Quando mais de um modelo explícito corresponder ao contexto, o sistema DEVE apresentar precedência determinística por prioridade e especificidade e NÃO DEVE fundir resultados conflitantes silenciosamente.
- **FR-FDIM-RULE-010**: Sugestão histórica DEVE ser derivada exclusivamente de distribuições confirmadas no mesmo tenant.
- **FR-FDIM-RULE-011**: O sistema DEVE normalizar históricos pelas proporções das parcelas principais, bases e combinações dimensionais, desconsiderar a ordem quando ela não afetar arredondamento, agrupar conjuntos equivalentes e escalá-los deterministicamente para o valor atual.
- **FR-FDIM-RULE-012**: A pontuação histórica DEVE exigir composição compatível de categorias principais e considerar frequência, recência e semelhança de atributos canônicos, como contraparte, contexto de origem, conta financeira e cartão associado quando aplicável.
- **FR-FDIM-RULE-013**: Cada sugestão histórica DEVE explicar os fatores relevantes de sua posição sem revelar operações de origem que o usuário não esteja autorizado a consultar.
- **FR-FDIM-RULE-014**: Antes da apresentação, toda sugestão DEVE ser filtrada pelo tenant, contexto e estados atuais da categoria, dimensões e valores; candidato incompatível ou indisponível DEVE ser descartado.
- **FR-FDIM-RULE-015**: Frequência histórica NÃO DEVE transformar sugestão em obrigação, regra de negócio nem precedência sobre modelo explícito ou aplicabilidade dimensional.
- **FR-FDIM-RULE-016**: Somente distribuição efetivamente confirmada DEVE alimentar o histórico; exibir, ignorar, editar ou descartar sugestão NÃO DEVE contar como uso confirmado.
- **FR-FDIM-RULE-017**: Na ausência de histórico compatível, o sistema DEVE informar que não há sugestão histórica e permitir distribuição manual ou uso de modelo explícito.
- **FR-FDIM-RULE-018**: Interface DEVE distinguir modelos explícitos de sugestões históricas e permitir ao usuário escolher, revisar, editar ou criar nova distribuição antes da confirmação.
- **FR-FDIM-RULE-019**: Confirmação originada de sugestão DEVE registrar seu tipo, identidade ou conjunto normalizado, resultado aceito e alterações feitas pelo usuário, preservando explicabilidade sem criar dependência da operação histórica de origem nem de versão operacional do modelo.
- **FR-FDIM-RULE-020**: Modelo que referencie dimensão ou valor inativo ou indisponível NÃO DEVE produzir sugestão confirmável até ser corrigido; candidato histórico incompatível DEVE ser descartado antes da apresentação.

### Estruturas e Hierarquias

- **FR-FDIM-ORG-001**: Valores DEVEM possuir identidade independente de estruturas e PODERÃO participar de múltiplas estruturas atuais da mesma dimensão.
- **FR-FDIM-ORG-002**: Estrutura DEVE pertencer a uma única dimensão e possuir identidade, nome, finalidade e estado próprios, sem versão ou período de vigência.
- **FR-FDIM-ORG-003**: Nó DEVE poder conter subnós e referências a valores da dimensão, com nome, ação de apresentação e ordem definidos.
- **FR-FDIM-ORG-004**: Nó NÃO DEVE ser selecionável como valor dimensional nem possuir classificação monetária própria.
- **FR-FDIM-ORG-005**: Estrutura NÃO DEVE admitir ciclos, valor de outra dimensão, referência cruzada entre tenants nem composição incompatível.
- **FR-FDIM-ORG-006**: Alteração de estrutura DEVE ser validada integralmente e aplicada de forma atômica; estado intermediário inválido NÃO DEVE afetar navegação, validação ou relatórios.
- **FR-FDIM-ORG-007**: Alteração aplicada DEVE substituir a organização anterior da mesma estrutura sem manter versão histórica operacional; consultas, inclusive de períodos passados, DEVEM usar a estrutura atual escolhida pelo usuário.
- **FR-FDIM-ORG-008**: Desativar estrutura NÃO DEVE desativar dimensão nem valores.
- **FR-FDIM-ORG-009**: Cada valor DEVE aparecer no máximo uma vez em cada estrutura, mas PODERÁ ocupar posições diferentes em outras estruturas da mesma dimensão.
- **FR-FDIM-ORG-010**: Quando uma dimensão possuir estruturas ativas, exatamente uma DEVE ser a principal e usada como navegação padrão; as demais DEVEM permanecer disponíveis como perspectivas alternativas.
- **FR-FDIM-ORG-011**: Ausência de estrutura NÃO DEVE impedir o uso da dimensão; seus valores DEVEM permanecer disponíveis em lista plana quando elegíveis.
- **FR-FDIM-ORG-012**: Hierarquia fornecida pelo módulo de origem PODERÁ ser exposta como estrutura controlada pelo provedor, mas NÃO DEVE ser alterada pelo financeiro; estruturas locais alternativas somente DEVEM organizar referências sem modificar entidades ou hierarquia de origem.
- **FR-FDIM-ORG-013**: Inclusão, remoção ou mudança de posição de valor em estrutura NÃO DEVE alterar a identidade do valor, sua classificação histórica nem sua elegibilidade funcional.
- **FR-FDIM-ORG-014**: Usuário autorizado DEVE poder duplicar uma estrutura como perspectiva independente antes de reorganizá-la, sem criar vínculo de versão, vigência ou seleção automática por data.
- **FR-FDIM-ORG-015**: A feature NÃO DEVE prometer reconstrução automática da posição hierárquica que um valor ocupava no passado; essa capacidade futura exigirá especificação própria.

### Ciclo de Vida e Substituição

- **FR-FDIM-LIFE-001**: Dimensão e valor DEVEM possuir estados ativo e inativo.
- **FR-FDIM-LIFE-002**: Dimensão ou valor ativo e disponível DEVE poder ser usado quando aplicável; item inativo NÃO DEVE aparecer em nova seleção comum.
- **FR-FDIM-LIFE-003**: Dimensão ou valor inativo DEVE permanecer identificável em histórico autorizado.
- **FR-FDIM-LIFE-004**: Dimensão ou valor nunca utilizado e sem dependências PODERÁ ser excluído definitivamente; caso contrário, somente poderá ser inativado.
- **FR-FDIM-LIFE-005**: Valor inativo PODERÁ indicar substituto ativo da mesma dimensão para orientar novos usos.
- **FR-FDIM-LIFE-006**: Substituição NÃO DEVE reclassificar silenciosamente histórico, recorrências, modelos ou regras existentes.
- **FR-FDIM-LIFE-007**: Cadeia de substituição DEVE terminar em valor ativo, permanecer na mesma dimensão e NÃO DEVE admitir ciclos.
- **FR-FDIM-LIFE-008**: Dimensão fornecida por módulo DEVE refletir indisponibilidade de novos usos sem apagar identidade ou apresentação histórica mínima dos valores referenciados.
- **FR-FDIM-LIFE-009**: Dimensão ou valor inativo NÃO DEVE ser admitido em nova classificação nem na confirmação de rascunho, pré-lançamento ou proposta recorrente ainda não materializada; o usuário DEVE resolver explicitamente a distribuição antes de confirmar.
- **FR-FDIM-LIFE-010**: Liquidação, correção ou estorno explicitamente vinculado a documento ou fato já confirmado DEVE poder preservar sua dimensão, valor e distribuição originais mesmo inativos ou indisponíveis, quando isso for necessário para manter continuidade e simetria.
- **FR-FDIM-LIFE-011**: A exceção para continuidade de fato confirmado NÃO DEVE disponibilizar dimensão ou valor inativo em seleção livre nem autorizar seu uso em fato novo e independente.
- **FR-FDIM-LIFE-012**: Substituto DEVE apenas orientar novas classificações e NÃO DEVE ser aplicado automaticamente a documento confirmado, recorrência, modelo, sugestão, rascunho, pré-lançamento, liquidação, correção ou estorno.
- **FR-FDIM-LIFE-013**: Reclassificação dimensional de fato confirmado DEVE ser explícita, auditada e permitida somente quando o contrato do lançamento e o fechamento financeiro admitirem sua alteração.

### Uso por Outros Módulos

- **FR-FDIM-USE-001**: Módulo consumidor DEVE solicitar dimensões e valores por identidades ou chaves estáveis, tenant, categoria e contexto, usando o estado atual no instante da operação.
- **FR-FDIM-USE-002**: Seleção DEVE retornar somente dimensões aplicáveis e valores ativos, disponíveis, autorizados e compatíveis.
- **FR-FDIM-USE-003**: Módulo consumidor DEVE validar novamente aplicabilidade, valores e soma no momento da confirmação, mesmo quando a interface já tiver validado.
- **FR-FDIM-USE-004**: Dimensões PODERÃO ser consumidas futuramente por relatórios, orçamento, aprovação, autorização contextual, contabilidade e integrações, mas esses efeitos DEVEM ser definidos explicitamente nas respectivas features.
- **FR-FDIM-USE-005**: Existência de valor dimensional NÃO DEVE conceder permissão, alterar orçamento, aprovar operação nem produzir escrituração por si própria.
- **FR-FDIM-USE-006**: Mapeamento contábil futuro DEVE poder considerar combinações dimensionais sem incorporar conta contábil à identidade da dimensão.
- **FR-FDIM-USE-007**: Importação ou sincronização futura DEVE preservar chaves externas, detectar duplicidades e NÃO DEVE fundir dimensões ou valores silenciosamente.

### Autorização, Auditoria e Isolamento

- **FR-FDIM-SEC-001**: Acesso a dimensões, valores, estruturas, políticas e modelos DEVE ser negado por padrão e liberado por chaves específicas do tenant.
- **FR-FDIM-SEC-002**: Consultar, criar dimensão, administrar valores, habilitar fonte de módulo, alterar aplicabilidade, administrar estruturas, administrar modelos, inativar, reativar e excluir DEVEM poder ser autorizados separadamente quando o risco diferir.
- **FR-FDIM-SEC-003**: Interfaces e operações internas DEVEM aplicar o mesmo contrato de autorização e isolamento.
- **FR-FDIM-SEC-004**: Criação, alteração semântica, mudança de aplicabilidade, ativação de estrutura, alteração de modelo, substituição, mudança de estado, exclusão e tentativa sensível negada DEVEM registrar ator, tenant, instante, origem, motivo, resultado e efeito.
- **FR-FDIM-SEC-005**: Dimensão fornecida por módulo DEVE respeitar tanto autorização financeira quanto restrições do módulo provedor, sem ampliar visibilidade por meio de pesquisas, mensagens ou totais.

### Pesquisa, Experiência e Acessibilidade

- **FR-FDIM-UX-001**: Usuário autorizado DEVE poder pesquisar dimensões por correspondência parcial de nome e descrição e filtrá-las por origem, finalidade, política e estado, sem depender de código ou identidade técnica.
- **FR-FDIM-UX-002**: Usuário autorizado DEVE poder pesquisar valores por dimensão e correspondência parcial de nome e descrição e filtrá-los por estado, substituto e estrutura.
- **FR-FDIM-UX-003**: Interface DEVE explicar a diferença entre categoria, dimensão, valor dimensional, nó de estrutura e campo livre.
- **FR-FDIM-UX-004**: Configuração de aplicabilidade DEVE apresentar matriz categoria por dimensão, política efetiva e sua origem, filtros e alteração em lote sem esconder sobrescritas existentes.
- **FR-FDIM-UX-005**: Editor de distribuição DEVE apresentar valor original, valor distribuído, diferença e políticas obrigatórias ou proibidas antes da confirmação.
- **FR-FDIM-UX-006**: Origem por módulo, estado inativo, indisponibilidade, substituição e erro de soma DEVEM ser perceptíveis sem depender exclusivamente de cor.
- **FR-FDIM-UX-007**: Jornadas principais DEVEM ser realizáveis por teclado e tecnologias assistivas, com erros associados aos campos correspondentes.

### Decisões de Infraestrutura Auditáveis

> Decisões de infraestrutura: sugestões históricas são produzidas sob demanda e não dependem de agendamento periódico. Não há token externo nem rotação criptográfica próprios desta feature. Sincronizações futuras de valores fornecidos por módulos ou sistemas externos deverão definir política própria na feature provedora.

- **FR-FDIM-INFRA-IDEMP**: Repetição técnica da mesma solicitação confirmada DEVE produzir no máximo uma dimensão, valor, regra, estrutura, alteração estrutural ou mudança de estado.
- **FR-FDIM-INFRA-LOCK**: Operações concorrentes DEVEM preservar unicidade, políticas aplicáveis, soma exata, ausência de ciclos, estrutura principal única e substituições válidas em todas as instâncias da aplicação.
- **FR-FDIM-INFRA-SUGGEST**: Nova distribuição confirmada DEVE tornar-se elegível para solicitações posteriores de sugestão histórica sem depender de tarefa periódica, preservadas consistência e autorização durante atualizações concorrentes.
- **FR-FDIM-INFRA-BACKUP**: Dimensões, valores, origens, regras, estruturas atuais, distribuições, estados, substituições e auditorias DEVEM participar dos backups e restaurações do tenant segundo `tenant-data-governance`.

### Key Entities

- **Dimensão Financeira**: identidade estável de um eixo categórico configurável pelo tenant ou fornecido por módulo.
- **Origem da Dimensão**: contrato que distingue valores administrados pelo tenant de valores referenciados a entidades de módulo.
- **Valor Dimensional**: opção identificável e atualmente ativa ou inativa dentro de uma dimensão, personalizada ou fornecida por entidade.
- **Política Padrão da Dimensão**: aplicabilidade usada quando uma categoria não possui regra específica.
- **Regra Dimensional da Categoria**: sobrescrita atual que torna dimensão obrigatória, opcional ou proibida para determinada categoria.
- **Base Dimensional**: valor máximo que determinada dimensão pode classificar, derivado das parcelas principais em que ela é aplicável e dos componentes especiais proporcionalmente atribuídos.
- **Matriz Monetária Balanceada**: resultado determinístico que concilia componentes econômicos, parcelas principais, bases dimensionais, combinações e precisão da moeda sem diferenças ocultas.
- **Componente de Distribuição**: classificação monetária do lançamento que associa parte de uma base a uma combinação conjunta com no máximo um valor por dimensão aplicável.
- **Modelo Explícito de Distribuição**: regra ativa ou inativa administrada pelo tenant, com prioridade, condições canônicas e distribuição conjunta pretendida, que sugere componentes sem confirmar operação por si própria.
- **Sugestão Histórica de Distribuição**: candidato pontuado derivado de distribuições confirmadas do tenant, sem autoridade própria de regra de negócio.
- **Conjunto Normalizado de Distribuição**: representação proporcional e independente da ordem usada para reconhecer históricos equivalentes e escalá-los ao valor atual.
- **Estrutura Dimensional**: perspectiva atual de organização dos valores de uma única dimensão para navegação, totalização ou relatório.
- **Nó Dimensional**: agrupador hierárquico não selecionável que organiza valores e subnós.
- **Substituição de Valor**: orientação de valor inativo para outro valor ativo da mesma dimensão sem reclassificação automática.
- **Referência Histórica Dimensional**: identidade e apresentação mínimas necessárias para explicar classificação cuja fonte atual esteja indisponível.

## Success Criteria

### Measurable Outcomes

- **SC-FDIM-001**: Em 100% dos testes cruzados, dimensão, valor, regra, estrutura ou distribuição de um tenant não é consultado, alterado nem referenciado por outro tenant.
- **SC-FDIM-002**: Usuários autorizados criam dimensão personalizada com dois valores em até 2 minutos durante testes de usabilidade.
- **SC-FDIM-003**: Em 100% dos testes, nomes normalizados permanecem únicos no escopo correto sem impedir nomes iguais em dimensões distintas, e nenhuma jornada comum exige código ou expõe identidade técnica.
- **SC-FDIM-004**: Em 100% dos testes de fonte por módulo, valor referencia a entidade original sem permitir edição financeira de sua identidade ou atributos funcionais.
- **SC-FDIM-005**: Em 100% dos testes por categoria, dimensão obrigatória ausente e dimensão proibida presente impedem confirmação, enquanto dimensão opcional aceita ausência ou preenchimento válido.
- **SC-FDIM-006**: Em 100% das distribuições confirmáveis, a matriz corresponde exatamente ao valor líquido, aos componentes econômicos e às bases dimensionais ou a diferença é identificada e a operação rejeitada.
- **SC-FDIM-007**: Em 100% dos testes históricos, desativação, substituição, reorganização ou indisponibilidade do módulo não altera a classificação originalmente confirmada.
- **SC-FDIM-008**: Em 100% das estruturas testadas, ciclos e referências cruzadas são rejeitados e nós não são retornados como valores selecionáveis.
- **SC-FDIM-009**: Em 100% dos testes, sugestão incompatível com política ou estado atual não é aplicada silenciosamente.
- **SC-FDIM-010**: Pelo menos 95% das pesquisas, matrizes de aplicabilidade e seleções comuns retornam resultado utilizável em até 2 segundos nas condições operacionais da primeira versão.
- **SC-FDIM-011**: Em 100% dos testes concorrentes e de repetição técnica, cada operação confirmada produz no máximo um efeito e preserva nomes únicos, somas, hierarquias, estrutura principal e substituições válidas.
- **SC-FDIM-012**: Todas as jornadas principais podem ser concluídas apenas por teclado e sem bloqueios críticos para tecnologias assistivas.
- **SC-FDIM-013**: Em 100% dos testes multidimensionais, componentes preservam cruzamentos, respeitam a base máxima de cada dimensão e permitem totalização correta por dimensão isolada, categoria, componente especial e combinação.
- **SC-FDIM-014**: Em 100% dos testes, sugestões históricas derivam apenas de distribuições confirmadas do mesmo tenant, respeitam regras e estados atuais e nunca confirmam operação automaticamente.
- **SC-FDIM-015**: Em 100% dos testes de equivalência, conjuntos históricos com os mesmos componentes proporcionais em ordem ou valores absolutos diferentes são agrupados no mesmo candidato e escalados com soma monetária exata.
- **SC-FDIM-016**: Em 100% das sugestões exibidas, a explicação identifica frequência, recência e correspondências contextuais relevantes sem expor operação de origem não autorizada.
- **SC-FDIM-017**: Em 100% dos conflitos testados, modelo explícito e sugestão histórica aparecem distintos, e o histórico não prevalece silenciosamente sobre intenção administrativa ou regra de aplicabilidade.
- **SC-FDIM-018**: Em 100% dos testes com múltiplas estruturas, cada valor aparece no máximo uma vez por estrutura, pode ocupar posições distintas entre perspectivas e permanece com a mesma identidade e classificações confirmadas após reorganização.
- **SC-FDIM-019**: Em 100% das confirmações, inclusive retroativas e iniciadas antes de alteração cadastral, dimensões, valores, aplicabilidades e modelos são revalidados pelo estado atual, sem consulta a vigência ou versão histórica própria.
- **SC-FDIM-020**: Em 100% dos testes com dimensão ou valor inativo, fatos novos e propostas não materializadas são bloqueados, enquanto liquidação, correção e estorno explicitamente vinculados ao fato confirmado preservam a distribuição original sem substituição automática.
- **SC-FDIM-021**: Em 100% dos testes estruturais, alterações substituem atomicamente a organização anterior sem versão histórica, consultas passadas usam a estrutura atual selecionada e duplicação cria perspectiva independente sem vigência.
- **SC-FDIM-022**: Em 100% dos testes de aplicabilidade, nova dimensão inicia com `OPCIONAL`, opção `PADRÃO DA DIMENSÃO` não cria sobrescrita, a interface identifica a política resolvida e mudanças do padrão alcançam somente categorias herdadas em confirmações posteriores.
- **SC-FDIM-023**: Em 100% dos testes percentuais, alterações de principal ou componente especial reaplicam os percentuais persistidos e produzem matriz exata e determinística pelo método dos maiores resíduos.
- **SC-FDIM-024**: Em 100% dos testes com categorias principais de dimensões distintas, cada base máxima corresponde somente às parcelas aplicáveis ajustadas proporcionalmente, e categorias automáticas dos campos especiais não impõem políticas dimensionais próprias.

## Fora do Escopo

- Cadastro ou execução de lançamentos financeiros.
- Cadastro funcional de projetos, departamentos, unidades, contratos, pessoas ou outras entidades fornecedoras.
- Categorias financeiras e suas estruturas, exceto regras de aplicabilidade dimensional já definidas em `financial-categories`.
- Orçamentos, limites, previsões e alertas de consumo.
- Fluxos de aprovação ou autorização baseados em dimensões.
- Relatórios financeiros finais, painéis e demonstrativos contábeis.
- Plano de contas, diários, partidas dobradas e escrituração contábil.
- Importação, exportação ou sincronização com sistemas externos nesta primeira versão.
- Campos personalizados livres de texto, número, data, arquivo ou formulário.
