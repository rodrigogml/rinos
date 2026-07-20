# Feature Specification: Planos e Direitos de Uso

**Feature**: `plans-entitlements`
**Created**: 2026-07-20
**Status**: Clarified — pronta para planejamento

## Escopo

Esta feature estabelece a fundação que relaciona cada conta a um plano e transforma as funcionalidades, capacidades e franquias declaradas pelos módulos em direitos de uso verificáveis. Ela permite que novas features publiquem definições estáveis sem conhecer os nomes dos planos comerciais e que os planos atribuam valores a essas definições sem conceder permissões aos usuários.

No primeiro momento, o único plano concreto obrigatório é o `FREE`, apresentado como `Free`, sem cobrança. Nenhum outro plano, preço, franquia numérica ou composição comercial será inventado nesta especificação. O catálogo de ofertas e os valores serão definidos depois que as respectivas features existirem e declararem o que pode ser disponibilizado ou limitado.

Inclui catálogo e ciclo de vida de planos, definições de direitos, versões publicadas, atribuição por conta, avaliação contextual, medição de uso, apresentação de disponibilidade e base segura para transições futuras. Não inclui cobrança, assinatura financeira, meios de pagamento, emissão fiscal, promoções, cupons, negociação comercial, marketplace, catálogo definitivo de módulos nem números de franquias futuras.

> [!IMPORTANT]
> Direito do plano e autorização são controles independentes. O plano determina se a conta dispõe de uma capacidade; grupos e chaves determinam se o usuário pode exercê-la. Uma condição nunca substitui a outra.

## Clarifications

### Session 2026-07-20

- Q: O que acontece com contas existentes quando uma nova versão do plano é publicada ou o plano deixa de ser comercializado? -> A: Cada conta permanece no plano e na versão atualmente atribuídos, inclusive quando se tornam legados. A saída ocorre por escolha própria ou por transição compulsória baseada em regra explícita futura, como inadimplência ou encerramento necessário do plano; publicação, retirada comercial ou exclusão cadastral nunca migram a conta silenciosamente.
- Q: Direitos podem ser alterados diretamente para uma conta, fora de seu plano? -> A: Não. Toda diferença deve ser representada por um plano ou versão própria; a fundação não admite exceções temporárias ou permanentes por conta.
- Q: Como a conta deve operar quando sua atribuição ou composição de plano estiver inválida? -> A: Deve usar como fallback ao menos os direitos da versão vigente do único plano marcado como padrão no sistema, sem apagar seu vínculo histórico. O plano inicial `FREE` é gratuito e padrão, mas outros planos gratuitos podem existir ou tornar-se legados. Se o próprio plano padrão estiver inválido, módulos condicionados são bloqueados e permanecem apenas configuração, diagnóstico e regularização autorizados.
- Q: O que acontece com recursos existentes quando o novo plano ou fallback permite quantidade menor? -> A: A conta deve escolher quais recursos permanecerão ativos. Transição planejada somente conclui após uma seleção válida; em fallback inesperado, nenhum recurso é escolhido ou apagado automaticamente e as operações afetadas ficam em regularização até a escolha autorizada. Consumo histórico de franquia periódica não participa dessa seleção.
- Q: Onde pertencem catálogo, atribuições e dados detalhados de consumo? -> A: Catálogo, definições, versões e atribuições por conta pertencem ao contexto global; períodos, eventos detalhados de uso e seleções de recursos pertencem exclusivamente ao tenant correspondente. O global não referencia estruturas internas de consumo do tenant.

## Itens Deliberadamente Adiados

- Nomes, quantidade, público e preços dos planos além do `Free`.
- Funcionalidades concretas incluídas no `Free` e em futuros planos, exceto a fundação mínima necessária para manter a conta.
- Valores numéricos de franquias, cotas, limites e períodos de renovação.
- Regras comerciais de upgrade, downgrade, teste gratuito, cobrança proporcional e inadimplência.
- Limites que somente poderão ser descobertos durante a especificação de cada módulo.

Cada feature futura deverá propor suas próprias definições de direito e seus valores candidatos. A composição comercial será consolidada em revisão posterior desta especificação, antes de qualquer oferta pública que dependa dela.

## User Scenarios & Testing

### User Story 1 - Iniciar toda conta no plano padrão (Priority: P1)

Um usuário cria uma conta e recebe automaticamente o único plano vigente marcado como padrão. Inicialmente ele é o plano gratuito `FREE`, apresentado como `Free`, sem que sua existência libere funcionalidades ainda não declaradas.

**Why this priority**: o cadastro de conta já depende de um plano padrão válido e não pode aguardar a definição do catálogo comercial completo.

**Independent Test**: criar uma conta com o plano padrão publicado e comprovar que ela recebe exatamente uma atribuição vigente, sem cobrança quando o padrão for gratuito, módulo implícito ou direito inexistente.

**Acceptance Scenarios**:

1. **Given** plano padrão válido e disponível, **When** uma conta é ativada, **Then** recebe uma única atribuição vigente desse plano sem escolha adicional
2. **Given** plano padrão ausente, inativo ou inconsistente, **When** a criação da conta é concluída, **Then** a ativação falha sem deixar conta operacional ou atribuição parcial
3. **Given** direito ainda não declarado por nenhuma feature, **When** a conta utiliza o plano `Free`, **Then** esse direito não é inferido nem concedido
4. **Given** repetição da mesma criação, **When** a atribuição é processada novamente, **Then** não surge segunda atribuição vigente nem histórico duplicado
5. **Given** catálogo inicial, **When** é validado, **Then** o plano `FREE` é gratuito, possui versão publicada válida e é o único marcado como padrão

---

### User Story 2 - Declarar uma capacidade sem conhecer os planos (Priority: P1)

Um responsável por uma feature registra uma definição estável para indicar disponibilidade, quantidade máxima ou consumo periódico, descrevendo sua unidade e comportamento sem decidir em qual plano ela será oferecida.

**Why this priority**: módulos precisam permanecer independentes do catálogo comercial e fornecer semântica suficiente para que limites futuros sejam coerentes e testáveis.

**Independent Test**: registrar definições de disponibilidade, limite e franquia, validar seus contratos e comprovar que nenhuma delas fica automaticamente liberada por existir.

**Acceptance Scenarios**:

1. **Given** nova funcionalidade, **When** sua definição é publicada, **Then** recebe código estável, tipo, unidade, descrição e responsável sem referência obrigatória a plano
2. **Given** definição de disponibilidade, **When** um plano não atribui valor explícito, **Then** a capacidade permanece indisponível para esse plano
3. **Given** definição quantitativa, **When** é publicada, **Then** declara de forma inequívoca o que é contado, em qual escopo e quando o consumo ocorre
4. **Given** definição já utilizada, **When** seu significado precisa mudar, **Then** uma nova definição ou versão compatível é criada sem reinterpretar silenciosamente o histórico

---

### User Story 3 - Avaliar direito e autorização em conjunto (Priority: P1)

Um participante tenta executar uma ação em uma conta e recebe acesso somente quando a conta está operacional, seu plano oferece a capacidade e suas chaves de acesso autorizam a ação no tenant ativo.

**Why this priority**: confundir direito comercial com autorização de segurança poderia conceder acesso indevido ou produzir comportamento diferente entre telas, serviços e tarefas assíncronas.

**Independent Test**: combinar conta, plano, direito e chave em estados permitidos e negados e comprovar que todas as entradas produzem a mesma decisão contextual sem atravessar tenants.

**Acceptance Scenarios**:

1. **Given** plano com capacidade disponível e usuário com chave vigente, **When** a ação é solicitada no tenant correto, **Then** ela pode prosseguir para as demais validações funcionais
2. **Given** plano com capacidade disponível e usuário sem chave, **When** tenta a ação, **Then** o acesso é negado sem concessão implícita pelo plano
3. **Given** usuário com chave e plano sem capacidade, **When** tenta a ação, **Then** o uso é negado sem remover sua autorização
4. **Given** direito ou chave de outra conta, **When** é apresentado no tenant ativo, **Then** não influencia a decisão
5. **Given** operação assíncrona aceita anteriormente, **When** começa a executar, **Then** revalida conta, responsabilidade e direitos conforme o contrato da feature solicitante
6. **Given** atribuição ou composição inválida de uma conta operacional, **When** um direito é avaliado, **Then** a conta recebe ao menos os direitos do plano padrão vigente sem obter autorização adicional
7. **Given** o próprio plano padrão está inválido, **When** uma operação condicionada é solicitada, **Then** ela é bloqueada, mas configuração, diagnóstico e regularização autorizados permanecem disponíveis

---

### User Story 4 - Medir e aplicar limites declarados (Priority: P1)

Uma conta utiliza uma funcionalidade limitada e acompanha quantidade disponível, consumo e próxima renovação quando aplicável, enquanto operações concorrentes não conseguem ultrapassar silenciosamente o valor vigente.

**Why this priority**: franquias e limites futuros só serão confiáveis se sua semântica for uniforme antes de receber valores comerciais.

**Independent Test**: atribuir valores de teste a direitos quantitativos, executar operações válidas, concorrentes, canceladas e repetidas e confirmar medição, bloqueio, compensação e isolamento da conta.

**Acceptance Scenarios**:

1. **Given** direito com quantidade máxima de recursos ativos, **When** a conta alcança o limite, **Then** nova criação é bloqueada sem apagar ou ocultar recursos existentes
2. **Given** franquia de consumo periódico, **When** uma operação confirmada é contabilizada, **Then** o saldo e o período refletem exatamente um consumo
3. **Given** operações concorrentes próximas ao limite, **When** são avaliadas, **Then** somente a quantidade permitida é confirmada
4. **Given** operação falha, é cancelada ou repetida, **When** sua medição é reconciliada, **Then** não permanece consumo indevido nem crédito duplicado
5. **Given** direito explicitamente ilimitado, **When** há uso válido, **Then** nenhuma quantidade arbitrária é tratada como teto oculto
6. **Given** transição planejada para quantidade menor que os recursos ativos, **When** a conta ainda não escolheu quais permanecerão ativos, **Then** a mudança não é concluída
7. **Given** fallback inesperado com recursos acima do limite padrão, **When** a contingência começa, **Then** nenhum recurso é escolhido ou eliminado automaticamente e as operações afetadas aguardam regularização
8. **Given** seleção autorizada dentro do novo limite, **When** é confirmada, **Then** somente os recursos escolhidos permanecem ativos e os demais são preservados como inativos
9. **Given** duas contas com o mesmo plano e direito, **When** registram uso, **Then** cada medição e seleção permanece exclusivamente no tenant de origem e não altera a outra conta

---

### User Story 5 - Administrar catálogo e versões com segurança (Priority: P2)

Um administrador do sistema com concessões específicas prepara e publica uma versão de plano, consulta contas atribuídas e desativa ofertas futuras sem reescrever silenciosamente os direitos já aplicados.

**Why this priority**: mesmo com apenas o plano `Free`, a definição precisa evoluir de forma auditável à medida que as features forem especificadas.

**Independent Test**: criar rascunho, adicionar direitos, publicar, atribuir e retirar disponibilidade futura, verificando imutabilidade da versão publicada, histórico e autorização.

**Acceptance Scenarios**:

1. **Given** administrador autorizado e reautenticado, **When** prepara uma versão em rascunho, **Then** pode revisá-la sem alterar a versão vigente
2. **Given** versão consistente, **When** é publicada, **Then** torna-se imutável e seus direitos podem ser avaliados de forma determinística
3. **Given** versão publicada já atribuída, **When** alguém tenta editá-la, **Then** a alteração é rejeitada e exige nova versão
4. **Given** plano retirado para novas atribuições, **When** uma conta existente é avaliada, **Then** seu histórico não é apagado nem transferido silenciosamente
5. **Given** ator identificado como administrador do sistema mas sem chaves específicas, **When** tenta alterar o catálogo, **Then** a ação é negada independentemente do papel
6. **Given** nova versão publicada para um plano, **When** contas permanecem atribuídas à versão anterior, **Then** seus direitos continuam inalterados até uma migração explícita e auditada
7. **Given** plano legado retirado de comercialização, **When** uma conta já o possui, **Then** ela conserva o plano e a versão atribuídos até saída voluntária ou transição compulsória regida por política explícita
8. **Given** necessidade de composição diferente para uma conta, **When** um administrador tenta alterar seus direitos diretamente, **Then** a ação é rejeitada e exige plano ou versão própria

---

### User Story 6 - Compreender o plano e os limites da conta (Priority: P2)

Um participante autorizado consulta o plano vigente da conta, as capacidades disponíveis e os limites aplicáveis em linguagem clara, sem visualizar condições internas de outras contas ou ofertas ainda não publicadas.

**Why this priority**: negações por plano ou franquia precisam ser explicáveis e indicar o próximo passo sem se confundirem com falta de permissão.

**Independent Test**: apresentar contas com direitos distintos a usuários com diferentes chaves e confirmar que cada um visualiza somente as informações permitidas do tenant ativo.

**Acceptance Scenarios**:

1. **Given** participante com acesso às informações do plano, **When** consulta a conta, **Then** visualiza plano vigente, capacidades aplicáveis, consumo conhecido e renovação quando houver
2. **Given** operação bloqueada por ausência de direito, **When** o usuário recebe a resposta, **Then** ela é distinguível de uma negação por autorização sem revelar ofertas internas
3. **Given** participante sem chave para consultar plano, **When** abre área relacionada, **Then** não recebe composição, consumo ou histórico da conta
4. **Given** catálogo contém rascunho ou oferta não publicada, **When** um usuário comum consulta o plano, **Then** nenhuma informação futura é apresentada como disponível

### Edge Cases

- O plano `Free` existe, mas não possui versão publicada válida.
- Duas versões do mesmo plano são publicadas concorrentemente.
- Uma conta é criada enquanto a versão vigente do `Free` muda.
- Uma definição de direito é retirada enquanto ainda aparece em versões atribuídas.
- Um plano referencia definição inexistente, incompatível ou de unidade diferente.
- O plano permite uma capacidade, mas o módulo correspondente está indisponível na instalação.
- O usuário possui chave de acesso, mas o plano deixa de oferecer a capacidade durante a sessão.
- A conta troca de plano enquanto existem operações assíncronas ainda não iniciadas.
- Duas operações concorrentes consomem a última unidade disponível.
- Uma operação reserva consumo e falha antes de concluir.
- Uma mensagem repetida tenta consumir ou devolver a mesma unidade novamente.
- O período de franquia muda enquanto existem medições abertas.
- O relógio operacional atravessa a renovação durante uma operação.
- Uma restauração reintroduz atribuição, consumo ou versão que já havia sido substituída.
- A conta está suspensa ou cancelada, mas ainda possui plano e direitos registrados.
- Uma configuração representa explicitamente ilimitado, zero ou indisponível.

## Requirements

### Princípios e Separação de Responsabilidades

- **FR-PE-001**: Plano DEVE determinar direitos disponíveis para uma conta e NÃO DEVE conceder permissão a qualquer usuário.
- **FR-PE-002**: Grupos e chaves DEVEM continuar determinando autorização do usuário e NÃO DEVEM habilitar capacidade ausente no plano vigente.
- **FR-PE-003**: Uma operação condicionada DEVE exigir cumulativamente conta operacional, tenant validado, direito vigente e chaves de acesso aplicáveis.
- **FR-PE-004**: Papel de ator, natureza futura da conta, nome do plano ou existência do tenant NÃO DEVEM inferir direitos nem autorizações.
- **FR-PE-005**: Módulos e features NÃO DEVEM codificar comportamento com base em nomes comerciais de planos; DEVEM consultar definições de direito estáveis.
- **FR-PE-006**: Direito de uma conta NÃO DEVE ser reutilizado, somado ou transferido implicitamente para outra conta do mesmo usuário.
- **FR-PE-007**: Avaliação administrativa global DEVE usar contexto de sistema explícito e não poderá assumir tenant da interface.
- **FR-PE-008**: Negação por plano, limite, estado da conta ou autorização DEVE permanecer distinguível internamente e ser apresentada conforme a informação que o ator pode conhecer.
- **FR-PE-009**: Catálogo de planos, definições de direito, versões publicadas e atribuições por conta DEVEM pertencer ao contexto global.
- **FR-PE-010**: Períodos de uso, eventos detalhados de medição e seleções de recursos DEVEM pertencer exclusivamente ao armazenamento do tenant correspondente.
- **FR-PE-011**: Estruturas globais NÃO DEVEM possuir referência física nem dependência de existência direcionada a período, evento, recurso ou seleção armazenados em tenant.
- **FR-PE-012**: Dados do tenant PODEM referenciar definições e versões globais conforme as regras de integridade de `tenant-data-governance`, sem permitir acesso a outra conta.
- **FR-PE-013**: Avaliação quantitativa DEVE usar somente medições e recursos do tenant explicitamente validado, mesmo quando plano e atribuição sejam globais.
- **FR-PE-014**: Resumo global de consumo, cobrança ou consolidação entre contas NÃO faz parte desta primeira versão e somente poderá ser criado por contrato futuro que preserve a independência do global em relação ao tenant.
- **FR-PE-015**: Restauração global ou de tenant DEVE reconciliar atribuição, versão e medições aplicáveis antes de liberar operações condicionadas, sem combinar componentes incompatíveis.

### Catálogo de Planos

- **FR-PE-PLAN-001**: Cada plano DEVE possuir código estável, nome de exibição, descrição, estado, indicação de gratuidade, indicação de padrão e disponibilidade para novas atribuições.
- **FR-PE-PLAN-002**: O código de um plano publicado NÃO DEVE ser alterado nem reutilizado para outra oferta.
- **FR-PE-PLAN-003**: O catálogo DEVE possuir exatamente um plano ativo marcado como padrão em cada instante; ausência ou multiplicidade DEVE tornar o catálogo inconsistente.
- **FR-PE-PLAN-004**: O catálogo inicial DEVE conter o plano gratuito de código `FREE`, nome `Free`, versão publicada válida e marcação de plano padrão.
- **FR-PE-PLAN-005**: O cadastro do plano `Free` NÃO DEVE inferir módulos, funcionalidades ou quantidades não atribuídos explicitamente em versão publicada.
- **FR-PE-PLAN-006**: Plano DEVE possuir ao menos os estados rascunho, ativo e retirado para novas atribuições.
- **FR-PE-PLAN-007**: Plano em rascunho NÃO DEVE ser atribuído nem aparecer como oferta disponível ao usuário comum.
- **FR-PE-PLAN-008**: Plano retirado NÃO DEVE receber novas atribuições, mas seu código, versões e histórico DEVEM permanecer reconhecíveis.
- **FR-PE-PLAN-009**: Plano associado a histórico de conta NÃO DEVE ser fisicamente eliminado por operação comum.
- **FR-PE-PLAN-010**: Criação, publicação, retirada e tentativa negada de manutenção DEVEM ser auditadas.
- **FR-PE-PLAN-011**: Gratuidade e condição de plano padrão DEVEM ser atributos independentes; vários planos gratuitos PODEM existir, mas somente um plano pode ser padrão por vez.
- **FR-PE-PLAN-012**: Alterar o plano padrão DEVE ser operação indivisível, autorizada e auditada, sem migrar contas com atribuições válidas.
- **FR-PE-PLAN-013**: Plano padrão DEVE possuir versão publicada válida e disponível para novas atribuições e fallback.

### Definições de Direito

- **FR-PE-ENT-001**: Cada definição DEVE possuir código global estável, nome, descrição, responsável funcional, grupo de apresentação, tipo, unidade e estado.
- **FR-PE-ENT-002**: Códigos DEVEM ser independentes de plano, conta, preço e texto de interface traduzido.
- **FR-PE-ENT-003**: Definições DEVEM poder representar ao menos disponibilidade booleana, quantidade máxima simultânea e franquia consumível por período.
- **FR-PE-ENT-004**: Uma definição quantitativa DEVE declarar objeto contado, unidade, escopo, momento de consumo, condição de devolução e comportamento ao atingir o limite.
- **FR-PE-ENT-005**: Franquia periódica DEVE declarar evento de início, duração ou calendário, fuso de referência e regra de renovação.
- **FR-PE-ENT-006**: Indisponível, zero e ilimitado DEVEM possuir representações inequívocas e não poderão depender de números sentinela ambíguos.
- **FR-PE-ENT-007**: A simples existência de uma definição NÃO DEVE liberá-la em qualquer plano.
- **FR-PE-ENT-008**: Definição publicada e utilizada NÃO DEVE mudar de significado, tipo ou unidade de forma incompatível; mudança incompatível DEVE receber novo código.
- **FR-PE-ENT-009**: Definição retirada NÃO DEVE ser usada em novas versões, mas deverá permanecer interpretável no histórico.
- **FR-PE-ENT-010**: Definições DEVEM possuir organização hierárquica de apresentação para descoberta por módulo, capacidade e função, sem que essa hierarquia conceda direitos.
- **FR-PE-ENT-011**: Toda feature futura sujeita a plano ou limite DEVE declarar suas definições antes de disponibilizar comportamento em produção.
- **FR-PE-ENT-012**: Cada definição nova DEVE incluir cenários que comprovem indisponibilidade, disponibilidade e limites aplicáveis sem atravessar tenants.

### Versões e Composição

- **FR-PE-VER-001**: Direitos de um plano DEVEM ser agrupados em versões identificáveis, ordenadas e auditáveis.
- **FR-PE-VER-002**: Uma versão DEVE permanecer em rascunho até que todas as definições e valores sejam válidos e revisados.
- **FR-PE-VER-003**: Publicação DEVE ser indivisível e NÃO DEVE expor composição parcial.
- **FR-PE-VER-004**: Versão publicada DEVE ser imutável; correção ou mudança DEVE produzir nova versão.
- **FR-PE-VER-005**: Cada item da versão DEVE relacionar exatamente uma definição a um valor compatível com seu tipo e unidade.
- **FR-PE-VER-006**: O mesmo direito NÃO DEVE aparecer mais de uma vez na mesma versão.
- **FR-PE-VER-007**: Ausência de direito em uma versão DEVE significar indisponibilidade, nunca herança implícita de outro plano ou versão.
- **FR-PE-VER-008**: Publicação DEVE registrar responsável, instante, composição, versão anterior relacionada e resultado.
- **FR-PE-VER-009**: Catálogo com referência ausente, duplicada, incompatível ou cíclica NÃO DEVE ser publicado.
- **FR-PE-VER-010**: Preço e condição de cobrança NÃO fazem parte da composição inicial de direitos.
- **FR-PE-VER-011**: Publicar nova versão NÃO DEVE alterar automaticamente atribuições existentes; cada conta DEVE permanecer vinculada à versão vigente em sua própria atribuição até transição explícita.
- **FR-PE-VER-012**: Migração de contas para outra versão DEVE constituir operação identificada, autorizada, idempotente e auditável, ainda que executada em lote.

### Atribuição por Conta

- **FR-PE-ASG-001**: Cada conta operacional DEVE possuir exatamente uma atribuição de plano vigente.
- **FR-PE-ASG-002**: A atribuição DEVE identificar conta, plano, versão aplicável, início, origem, motivo e estado.
- **FR-PE-ASG-003**: Ativação inicial da conta DEVE criar atomicamente sua atribuição ao plano padrão e à versão publicada válida aplicável; inicialmente, esse plano é o `FREE`.
- **FR-PE-ASG-004**: Falha na atribuição inicial DEVE impedir ativação e não poderá deixar conta ou tenant parcialmente utilizável.
- **FR-PE-ASG-005**: Repetição da mesma intenção DEVE produzir no máximo uma atribuição inicial vigente.
- **FR-PE-ASG-006**: Mudança futura de plano ou versão DEVE preservar histórico e não poderá sobrescrever a atribuição anterior.
- **FR-PE-ASG-007**: Atribuição pertencente a uma conta NÃO DEVE ser usada para avaliar outra conta, ainda que tenham os mesmos participantes.
- **FR-PE-ASG-008**: Suspensão ou cancelamento da conta DEVE impedir operações comuns independentemente dos direitos ainda registrados.
- **FR-PE-ASG-009**: Restauração de dados NÃO DEVE reativar atribuição substituída, plano retirado como nova oferta ou direito posterior revogado.
- **FR-PE-ASG-010**: Criação, mudança, agendamento, cancelamento e falha de atribuição DEVEM ser auditados no contexto aplicável.
- **FR-PE-ASG-011**: Retirar um plano de novas atribuições ou de comercialização NÃO DEVE encerrar nem modificar atribuições existentes, que passam a ser tratadas como legadas.
- **FR-PE-ASG-012**: Conta DEVE permanecer no plano e na versão atribuídos até saída voluntária confirmada ou transição compulsória baseada em política explícita vigente.
- **FR-PE-ASG-013**: Transição compulsória NÃO DEVE decorrer de simples publicação, retirada comercial ou exclusão cadastral do plano; DEVE possuir causa verificável, comunicação aplicável, destino definido e auditoria.
- **FR-PE-ASG-014**: Inadimplência e encerramento necessário de plano PODERÃO originar transição compulsória somente após suas regras comerciais e efeitos serem especificados; não produzem comportamento nesta primeira versão.
- **FR-PE-ASG-015**: Atribuição de conta NÃO DEVE conter substituição, acréscimo, redução ou exceção de direito fora da versão de plano associada.
- **FR-PE-ASG-016**: Necessidade de composição diferenciada DEVE ser representada por plano ou versão própria, publicada e atribuída pelos mesmos controles do catálogo.
- **FR-PE-ASG-017**: Aplicação temporária do fallback padrão NÃO DEVE apagar, sobrescrever nem apresentar como válida a atribuição inconsistente que originou a contingência.
- **FR-PE-ASG-018**: Inadimplência futura PODERÁ produzir transição explícita para o plano padrão, preservando histórico, somente após definição da política comercial correspondente.

### Avaliação de Direitos

- **FR-PE-EVAL-001**: Cada avaliação DEVE receber explicitamente tenant, conta, definição de direito, instante e operação pretendida.
- **FR-PE-EVAL-002**: Conta, atribuição, versão e direito DEVEM pertencer à mesma cadeia válida antes de retornar disponibilidade.
- **FR-PE-EVAL-003**: Direito ausente ou desconhecido na composição efetivamente aplicável DEVE ser tratado como indisponível.
- **FR-PE-EVAL-004**: Avaliação NÃO DEVE consultar nem combinar atribuição, consumo ou configuração de outro tenant.
- **FR-PE-EVAL-005**: Resultado DEVE distinguir ao menos disponível, indisponível, limite atingido, configuração inconsistente e contexto inválido.
- **FR-PE-EVAL-006**: Resultado positivo de direito NÃO DEVE substituir verificação de grupos, chaves, estado da conta, regras funcionais ou integridade dos dados.
- **FR-PE-EVAL-007**: Interfaces, operações de serviço e tarefas assíncronas DEVEM aplicar a mesma decisão para entradas equivalentes.
- **FR-PE-EVAL-008**: Trabalho ainda não iniciado ou repetido DEVE revalidar direitos; trabalho explicitamente transferido à responsabilidade do sistema DEVE seguir contrato registrado pela feature originadora.
- **FR-PE-EVAL-009**: Mudança de direito DEVE impedir novas operações incompatíveis sem alterar retroativamente operações já concluídas.
- **FR-PE-EVAL-010**: Decisões críticas, negações por inconsistência e alterações de composição DEVEM possuir correlação e auditoria suficientes para explicação.
- **FR-PE-EVAL-011**: Avaliação DEVE obter todo valor exclusivamente da versão efetivamente aplicável — atribuída ou padrão em fallback — sem sobreposição personalizada, configuração oculta ou ajuste direto da conta.
- **FR-PE-EVAL-012**: Quando plano, versão, atribuição ou composição da conta estiverem ausentes ou inconsistentes, a avaliação DEVE aplicar como fallback os direitos válidos da versão vigente do plano padrão.
- **FR-PE-EVAL-013**: Fallback DEVE conceder no mínimo a composição do plano padrão e NÃO DEVE preservar direito adicional do plano problemático sem política explícita de transição.
- **FR-PE-EVAL-014**: Fallback de plano NÃO DEVE conceder chaves, alterar o tenant, tornar conta suspensa ou cancelada operacional nem substituir demais controles funcionais.
- **FR-PE-EVAL-015**: Se o plano padrão, sua versão ou composição também estiverem inconsistentes, operações condicionadas DEVEM ser bloqueadas, preservando somente configuração, diagnóstico e regularização autorizados.
- **FR-PE-EVAL-016**: Ativação, uso e encerramento do fallback DEVEM ser observáveis e auditados com conta, causa, plano original, plano padrão aplicado e resultado.

### Limites, Franquias e Medição

- **FR-PE-USG-001**: Cada medição DEVE pertencer a exatamente uma conta, uma definição e, quando aplicável, um período identificado.
- **FR-PE-USG-002**: Quantidade máxima simultânea DEVE considerar o estado vigente dos recursos contados, conforme contrato da definição.
- **FR-PE-USG-003**: Franquia consumível DEVE contabilizar somente o evento definido como consumo confirmado.
- **FR-PE-USG-004**: Operação que possa ultrapassar limite DEVE assegurar disponibilidade antes de produzir efeito irreversível.
- **FR-PE-USG-005**: Operações concorrentes NÃO DEVEM confirmar consumo superior ao valor vigente.
- **FR-PE-USG-006**: Repetição da mesma operação DEVE contabilizar no máximo um consumo.
- **FR-PE-USG-007**: Falha ou cancelamento DEVE liberar reserva ou compensar consumo somente quando o contrato da definição determinar que ele não é devido.
- **FR-PE-USG-008**: Compensação repetida NÃO DEVE produzir saldo adicional nem valor negativo indevido.
- **FR-PE-USG-009**: Renovação periódica DEVE iniciar novo período sem apagar o histórico dos anteriores.
- **FR-PE-USG-010**: Alteração futura de valor NÃO DEVE reescrever consumos confirmados nem períodos encerrados.
- **FR-PE-USG-011**: Direito ilimitado DEVE continuar produzindo medição somente quando ela for necessária para transparência ou planejamento, sem bloquear pelo volume.
- **FR-PE-USG-012**: Estado de uso DEVE ser reconciliável a partir dos eventos e recursos que o contrato da definição declarar como autoridade.
- **FR-PE-USG-013**: Limites e franquias concretos NÃO DEVEM ser definidos nesta versão da spec; cada feature deverá propô-los e a composição comercial os aprovará posteriormente.
- **FR-PE-USG-014**: Transição de plano ou versão DEVE calcular previamente quais limites de quantidade simultânea ficariam excedidos e informar os recursos afetados ao público autorizado.
- **FR-PE-USG-015**: Transição planejada para limite inferior NÃO DEVE ser concluída enquanto a conta não selecionar um conjunto de recursos ativos compatível com o novo valor.
- **FR-PE-USG-016**: Fallback inesperado para limite inferior DEVE aplicar imediatamente os direitos seguros do plano padrão, sem escolher, excluir ou ocultar automaticamente os recursos excedentes.
- **FR-PE-USG-017**: Enquanto a seleção exigida pelo fallback estiver pendente, dados excedentes DEVEM permanecer preservados e identificáveis, mas operações da capacidade afetada DEVEM ficar restritas à consulta e à regularização autorizadas.
- **FR-PE-USG-018**: Seleção confirmada DEVE manter ativos somente recursos até o limite vigente; os não escolhidos DEVEM ficar inativos sem eliminação, podendo participar de seleção posterior quando o contrato da feature permitir.
- **FR-PE-USG-019**: Consultar impacto e confirmar seleção DEVEM exigir tenant explícito, chaves específicas, autenticação recente e auditoria dos recursos ativados e inativados.
- **FR-PE-USG-020**: Seleção de recursos NÃO DEVE alterar consumo histórico de franquia periódica; quando o consumo do período já exceder o novo valor, novas operações consumíveis permanecem bloqueadas até renovação, compensação legítima ou nova transição.

### Administração e Apresentação

- **FR-PE-ADM-001**: Manutenção de planos, definições, versões e atribuições DEVE exigir chaves globais específicas, autenticação recente e segundo fator compatível.
- **FR-PE-ADM-002**: Papel de administrador do sistema NÃO DEVE autorizar manutenção sem concessões explícitas.
- **FR-PE-ADM-003**: A interface administrativa DEVE diferenciar rascunho, publicado, ativo e retirado sem depender somente de cor.
- **FR-PE-ADM-004**: Publicação e transições com impacto em contas DEVEM apresentar resumo dos efeitos conhecidos e exigir confirmação explícita.
- **FR-PE-ADM-005**: Participante da conta somente DEVE consultar plano, direitos e consumo quando possuir chaves específicas no tenant ativo.
- **FR-PE-ADM-006**: A conta DEVE visualizar informações vigentes em linguagem clara, incluindo unidade, valor, uso conhecido, período e renovação quando aplicáveis.
- **FR-PE-ADM-007**: Negação por ausência de direito ou limite DEVE explicar a condição sem simular falta de permissão nem prometer plano futuro inexistente.
- **FR-PE-ADM-008**: Rascunhos, ofertas não publicadas, composição de outras contas e metadados internos NÃO DEVEM aparecer ao participante comum.
- **FR-PE-ADM-009**: Todas as jornadas DEVEM ser utilizáveis por teclado e compatíveis com tecnologias assistivas.

### Evolução e Limites do Escopo Inicial

- **FR-PE-EVO-001**: A fundação DEVE aceitar novos planos, direitos e valores no futuro sem alterar os códigos e históricos já publicados.
- **FR-PE-EVO-002**: Nenhum plano além do `Free` é obrigatório nesta etapa.
- **FR-PE-EVO-003**: A primeira versão NÃO DEVE cobrar, faturar, renovar pagamento, calcular tributo, aplicar cupom ou suspender por inadimplência.
- **FR-PE-EVO-004**: Upgrade, downgrade, testes e regras de transição comercial somente DEVEM ser disponibilizados após especificação própria dos efeitos sobre direitos e recursos existentes.
- **FR-PE-EVO-005**: A ausência de catálogo comercial completo NÃO DEVE impedir que features futuras registrem definições ainda não atribuídas a plano.
- **FR-PE-EVO-006**: A revisão comercial futura DEVE atualizar esta spec ou criar feature dependente antes de publicar novas ofertas.
- **FR-PE-EVO-007**: Política futura de inadimplência ou encerramento de plano legado DEVE definir aviso, prazo, destino, tratamento de excedentes e possibilidade de contestação antes de permitir transição compulsória.
- **FR-PE-EVO-008**: Oferta especial ou negociada futura DEVE ser modelada como plano ou versão rastreável, e não como exceção aplicada diretamente à conta.

### Decisões de Infraestrutura Auditáveis

- **FR-PE-INFRA-LOCK**: Publicação, atribuição, reserva, consumo, compensação e renovação DEVEM permanecer consistentes entre todas as instâncias concorrentes.
- **FR-PE-INFRA-IDEMP**: Cada intenção de atribuição e evento de uso DEVE possuir identidade estável suficiente para repetição e reconciliação sem duplicar efeito.
- **FR-PE-INFRA-SCHED**: Ativações agendadas, encerramentos de período e reconciliações futuras DEVEM executar sem depender de sessão ativa, com política configurável e observável.
- **FR-PE-INFRA-BACKUP**: Catálogo, versões, atribuições, medições e auditorias DEVEM participar dos conjuntos coordenados de recuperação e ser reconciliados antes da liberação do tenant restaurado.
- **FR-PE-INFRA-CLOCK**: Vigência e períodos DEVEM usar referência temporal confiável e impedir renovação ou expiração antecipada causada por divergência detectada.

### Key Entities

- **Plan**: identidade global estável de uma oferta, com código, apresentação, estado, gratuidade, condição de padrão e disponibilidade para atribuição.
- **Plan Version**: composição global imutável e publicada dos direitos de um plano em determinado momento.
- **Entitlement Definition**: contrato global independente de plano que descreve uma disponibilidade, limite ou franquia com código, tipo, unidade e semântica estáveis.
- **Plan Entitlement**: valor global atribuído por uma versão de plano a uma definição compatível.
- **Account Plan Assignment**: vínculo global, histórico e vigente entre uma conta e uma versão de plano.
- **Usage Period**: janela pertencente ao tenant durante a qual uma franquia periódica acumula consumo.
- **Usage Event**: registro idempotente, pertencente ao tenant, de reserva, confirmação, liberação ou compensação de consumo da conta.
- **Resource Activation Selection**: decisão armazenada no tenant sobre quais recursos permanecem ativos quando uma transição ou fallback reduz uma quantidade simultânea permitida.
- **Entitlement Decision**: resultado contextual e explicável da avaliação de um direito para uma operação, incluindo eventual origem em fallback padrão.

## Success Criteria

### Measurable Outcomes

- **SC-PE-001**: Em 100% das criações testadas, a conta recebe exatamente uma atribuição vigente ao único plano padrão ou não se torna operacional; no catálogo inicial, esse plano é o `FREE`.
- **SC-PE-002**: Em 100% dos testes, o plano `Free` não produz cobrança nem libera direito ausente de sua versão publicada.
- **SC-PE-003**: Em 100% das definições publicadas, código, tipo, unidade, escopo e momento de consumo são inequívocos e independentes de plano comercial.
- **SC-PE-004**: Em 100% das combinações testadas, uma operação somente é permitida quando conta, direito e autorização estão simultaneamente válidos.
- **SC-PE-005**: Em 100% dos testes de isolamento, direito ou consumo de uma conta não altera decisão de outra conta.
- **SC-PE-006**: Em 100% das repetições e concorrências testadas, atribuição e consumo produzem no máximo um efeito válido por intenção.
- **SC-PE-007**: Em 100% dos testes próximos ao limite, a quantidade confirmada não ultrapassa o valor vigente.
- **SC-PE-008**: Em 100% das falhas e cancelamentos testados, reservas e compensações seguem o contrato sem consumo indevido ou crédito duplicado.
- **SC-PE-009**: Em 100% das publicações, a versão torna-se integralmente disponível ou permanece indisponível, sem composição parcial.
- **SC-PE-010**: Em 100% dos testes, uma versão publicada não pode ser editada nem ter seu significado reescrito.
- **SC-PE-011**: Em 100% das restaurações testadas, atribuições substituídas e consumos compensados não voltam a produzir direitos ou saldo indevidos.
- **SC-PE-012**: Pelo menos 90% dos participantes autorizados identificam plano, disponibilidade e motivo de um bloqueio em até 30 segundos sem ajuda.
- **SC-PE-013**: Nenhum usuário comum visualiza rascunho, oferta não publicada, composição de outra conta ou número comercial ainda não aprovado.
- **SC-PE-014**: Todas as jornadas administrativas e de consulta podem ser concluídas apenas por teclado e sem depender somente de cor.
- **SC-PE-015**: Cada feature futura condicionada a plano ou limite possui definição e cenários de indisponibilidade antes de ser considerada pronta para produção.
- **SC-PE-016**: Em 100% dos testes, publicar versão ou retirar plano de comercialização não altera contas existentes; somente transição explícita modifica a atribuição vigente e preserva o histórico.
- **SC-PE-017**: Em 100% das avaliações testadas, o valor decorre somente da versão atribuída ou da versão padrão em fallback; nenhuma configuração direta da conta amplia ou reduz seus direitos.
- **SC-PE-018**: Em 100% dos testes de atribuição ou composição inconsistente, a conta operacional recebe a composição válida do plano padrão sem autorização adicional; se o padrão também estiver inválido, somente regularização autorizada permanece disponível.
- **SC-PE-019**: Em 100% das transições para limite inferior, nenhum recurso é eliminado ou escolhido automaticamente; após seleção válida, a quantidade ativa respeita o novo limite e os demais recursos permanecem preservados como inativos.
- **SC-PE-020**: Em 100% dos testes de persistência e restauração, catálogo e atribuição permanecem globais, detalhes de uso permanecem no tenant correto e nenhuma estrutura global depende fisicamente de dados internos do tenant.
