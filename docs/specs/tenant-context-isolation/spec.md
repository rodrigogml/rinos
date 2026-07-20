# Feature Specification: Contexto e Isolamento de Tenant

**Feature**: `tenant-context-isolation`
**Created**: 2026-07-20
**Status**: Clarified — pronta para planejamento

## Escopo

Esta feature permite que um usuário autenticado selecione conscientemente uma conta, estabeleça um contexto ativo exclusivo para trabalhar nela e alterne entre contas sem misturar dados, autorizações ou operações. O contexto de conta deve ser distinto do painel pessoal do usuário e do contexto global da plataforma.

Inclui seleção e troca de conta, isolamento entre abas ou áreas de trabalho, validação contínua da identidade, associação e estado da conta, propagação segura do contexto para operações síncronas e assíncronas, invalidação de contextos que perderam validade e auditoria das mudanças. Não inclui provisionamento do armazenamento físico, criação ou migração de schemas, definição de grupos e chaves, regras de planos, administração excepcional do sistema ou regras internas dos módulos de negócio.

## Clarifications

### Session 2026-07-20

- Q: Quando uma revogação ocorre durante operação já aceita, ela deve interromper a execução? -> A: Não. A operação aceita com contexto e autorização válidos pode terminar no tenant original; a revogação bloqueia novas operações, repetições e trabalhos ainda não iniciados.
- Q: O contexto ativo deve ser recuperado ao duplicar ou restaurar uma aba? -> A: A aba duplicada pode manter a mesma conta após revalidação independente; aba restaurada após fechamento ou reinício deve exigir nova seleção.
- Q: Como o usuário deve acessar ações de uma conta suspensa? -> A: A conta pode ser selecionada em contexto restrito, no qual somente ações explicitamente autorizadas de consulta ou regularização ficam disponíveis.
- Q: Qual deve ser a política padrão quando um trabalho assíncrono solicitado por usuário ainda não iniciou e o acesso mudou? -> A: Revalidar o acesso ao iniciar, salvo quando a feature declarar explicitamente que o trabalho aceito passou a ser responsabilidade do sistema.
- Q: Quais contas devem aparecer no seletor de contexto? -> A: Somente contas ativas e suspensas; contas em criação, com erro ou canceladas permanecem no painel de administração das contas.

## User Scenarios & Testing

### User Story 1 - Selecionar uma conta para trabalhar (Priority: P1)

Um usuário autenticado visualiza as contas das quais pode participar e seleciona explicitamente aquela em que deseja trabalhar, sem que o sistema confunda sua identidade global com a conta escolhida.

**Why this priority**: estabelece o contexto mínimo necessário para qualquer módulo de conta operar com segurança.

**Independent Test**: autenticar um usuário participante de duas contas ativas, selecionar uma delas e verificar que somente operações e informações dessa conta ficam disponíveis no novo contexto.

**Acceptance Scenarios**:

1. **Given** usuário ativo com associação ativa em uma conta ativa, **When** seleciona explicitamente essa conta, **Then** o sistema estabelece um contexto de conta válido para sua área de trabalho atual
2. **Given** usuário com somente uma conta disponível, **When** acessa seu painel pessoal, **Then** nenhuma conta é selecionada automaticamente e ele pode escolhê-la conscientemente
3. **Given** usuário sem associação vigente na conta informada, **When** tenta selecioná-la por qualquer caminho, **Then** a seleção é negada sem revelar dados internos da conta
4. **Given** conta em criação, com erro ou cancelada, **When** o usuário tenta iniciar operações comuns nela, **Then** nenhum contexto operacional de conta é estabelecido
5. **Given** usuário com contas em diferentes estados, **When** abre o seletor, **Then** visualiza somente contas ativas e suspensas, com a suspensão claramente indicada

---

### User Story 2 - Trabalhar em contas diferentes simultaneamente (Priority: P1)

Um usuário abre áreas de trabalho distintas para contas diferentes e executa atividades em cada uma sem que a seleção ou navegação de uma altere o contexto da outra.

**Why this priority**: usuários multiempresa precisam comparar ou alternar atividades sem risco de registrar dados na conta errada.

**Independent Test**: abrir duas áreas de trabalho do mesmo usuário, selecionar uma conta diferente em cada uma e comprovar que leituras, gravações, arquivos, caches e autorizações permanecem separados.

**Acceptance Scenarios**:

1. **Given** duas abas ou áreas de trabalho autenticadas do mesmo usuário, **When** cada uma seleciona uma conta distinta, **Then** cada área mantém seu próprio contexto ativo
2. **Given** uma operação iniciada na conta A, **When** outra área seleciona a conta B, **Then** a operação original permanece vinculada exclusivamente à conta A
3. **Given** identificadores iguais ou semelhantes em duas contas, **When** um recurso é consultado na conta A, **Then** o recurso homônimo da conta B não é retornado nem considerado
4. **Given** cache, arquivo, evento ou resultado temporário criado para uma conta, **When** outra conta solicita conteúdo semelhante, **Then** o conteúdo da primeira conta não é reutilizado ou exposto
5. **Given** área de trabalho da conta A duplicada conscientemente pelo usuário, **When** a nova área é aberta, **Then** ela pode estabelecer contexto independente da mesma conta somente após revalidá-lo
6. **Given** aba antiga restaurada após fechamento ou reinício do navegador, **When** volta a ficar disponível, **Then** inicia sem tenant ativo e exige nova seleção

---

### User Story 3 - Trocar ou encerrar o contexto ativo (Priority: P1)

Um usuário troca de conta ou retorna ao painel pessoal sem conservar autorizações, dados transitórios ou referências da conta anterior.

**Why this priority**: uma troca incompleta pode causar lançamento, consulta ou decisão de acesso no tenant incorreto.

**Independent Test**: selecionar a conta A, trocar para a conta B e verificar que a nova área contém somente dados e concessões vigentes da conta B, preservando A quando a troca falhar.

**Acceptance Scenarios**:

1. **Given** contexto válido da conta A, **When** o usuário troca com sucesso para a conta B, **Then** identidade, associação, estado, concessões e funcionalidades aplicáveis são reavaliados antes da substituição completa do contexto
2. **Given** falha durante a validação da conta B, **When** a troca é recusada, **Then** nenhum contexto parcial da conta B permanece e a conta A continua ativa
3. **Given** alterações ainda não confirmadas na área atual, **When** o usuário solicita troca ou encerramento do contexto, **Then** recebe aviso claro antes de perder o trabalho transitório
4. **Given** usuário que retorna ao painel pessoal ou encerra a sessão, **When** a transição termina, **Then** nenhuma operação posterior reutiliza implicitamente o último tenant

---

### User Story 4 - Reagir a mudanças de acesso e estado (Priority: P2)

O sistema interrompe o uso de um contexto quando o usuário, sua associação ou a conta deixa de satisfazer as condições necessárias, sem afetar suas demais contas.

**Why this priority**: suspensão, remoção e revogação precisam produzir efeito mesmo em sessões ou áreas de trabalho já abertas.

**Independent Test**: manter uma conta aberta, suspender a associação do usuário em outra sessão administrativa e comprovar que a próxima operação é negada e o contexto inválido é encerrado.

**Acceptance Scenarios**:

1. **Given** associação suspensa, removida ou encerrada após a seleção, **When** o usuário tenta nova operação naquela conta, **Then** o acesso é negado e o contexto deixa de ser utilizável
2. **Given** usuário global bloqueado, desativado ou cancelado, **When** qualquer contexto de conta tenta nova operação, **Then** todos os seus contextos deixam de autorizar acesso
3. **Given** conta suspensa durante uma sessão ativa, **When** ocorre nova operação, **Then** somente ações explicitamente autorizadas de regularização podem prosseguir
4. **Given** revogação de uma chave sem perda da associação, **When** o usuário executa nova operação protegida, **Then** a decisão utiliza as concessões vigentes e não uma autorização antiga da seleção

---

### User Story 5 - Executar operações globais e assíncronas com contexto seguro (Priority: P2)

Operações da plataforma, tarefas em segundo plano e integrações identificam expressamente se atuam no sistema ou em uma conta, sem depender de contexto residual da sessão que as originou.

**Why this priority**: processos fora da interação imediata do usuário são uma fonte recorrente de vazamento entre tenants.

**Independent Test**: disparar tarefas equivalentes para duas contas e uma operação global, executar em ordem e instâncias diferentes e comprovar que cada uma utiliza somente o contexto declarado.

**Acceptance Scenarios**:

1. **Given** operação global autorizada, **When** é executada, **Then** utiliza contexto de sistema explícito e não herda uma conta ativa
2. **Given** operação de conta sem tenant válido, **When** tenta acessar dados de tenant, **Then** falha de forma segura sem recorrer ao contexto global ou a outra conta
3. **Given** tarefa assíncrona criada para uma conta, **When** é executada posteriormente, **Then** restaura e valida o tenant declarado antes de acessar qualquer dado
4. **Given** processamento que percorre várias contas, **When** muda de uma conta para outra, **Then** cada unidade de trabalho recebe contexto independente e o contexto anterior é encerrado

### Edge Cases

- O usuário informa manualmente na navegação um identificador de conta que não integra sua lista disponível.
- A associação ou a conta muda de estado durante uma operação já iniciada.
- Duas trocas de conta são solicitadas quase simultaneamente na mesma área de trabalho.
- A resposta da troca é repetida após perda de conexão.
- Uma aba é restaurada pelo navegador depois que a sessão, associação ou conta perdeu validade.
- O usuário abre várias abas para a mesma conta e encerra o contexto em apenas uma delas.
- Uma tarefa assíncrona começa depois que o usuário originador perdeu a concessão usada para solicitá-la.
- Uma tarefa falha entre o estabelecimento e a limpeza do contexto.
- Um evento é repetido, reordenado ou processado por outra instância da aplicação.
- Uma chave de cache ou caminho de arquivo coincide entre contas diferentes.
- Uma conta suspensa possui operações de regularização permitidas, mas módulos comuns permanecem bloqueados.
- O contexto aponta para conta válida, mas a localização ou versão de seus dados está indisponível.

## Requirements

### Tipos e Identidade do Contexto

- **FR-TCI-CTX-001**: Toda operação protegida DEVE declarar que atua no contexto pessoal do usuário, no contexto global do sistema ou no contexto de uma conta específica.
- **FR-TCI-CTX-002**: Contexto de conta DEVE identificar de forma imutável a conta e o tenant correspondentes, sem usar nome de exibição como identidade.
- **FR-TCI-CTX-003**: O identificador ou a localização física do armazenamento do tenant NÃO DEVE ser informado pelo usuário nem exposto como parte necessária da seleção.
- **FR-TCI-CTX-004**: Contexto pessoal, contexto de sistema e contexto de conta DEVEM ser incompatíveis entre si e não poderão satisfazer implicitamente operações destinadas a outro tipo.
- **FR-TCI-CTX-005**: Ausência, ambiguidade, expiração ou inconsistência do contexto DEVE resultar em negação segura.
- **FR-TCI-CTX-006**: Contexto de conta NÃO DEVE ser inferido apenas pela identidade do usuário, por possuir uma única associação, por uma rota aberta anteriormente ou por concessões existentes.
- **FR-TCI-CTX-007**: Cada decisão e operação DEVE utilizar uma única conta; processamento de várias contas DEVE ser decomposto em unidades de contexto independentes.

### Seleção da Conta

- **FR-TCI-SEL-001**: O usuário DEVE selecionar explicitamente a conta antes de utilizar qualquer funcionalidade contextual dela.
- **FR-TCI-SEL-002**: O sistema NÃO DEVE selecionar automaticamente uma conta, mesmo quando apenas uma estiver disponível.
- **FR-TCI-SEL-003**: A lista disponível DEVE ser derivada da identidade autenticada e exibir somente contas com associação visível e vigente para aquele usuário.
- **FR-TCI-SEL-003A**: O seletor de contexto DEVE listar somente contas ativas e suspensas; contas suspensas DEVEM ser identificadas antes da seleção.
- **FR-TCI-SEL-003B**: Contas em criação, com erro ou canceladas NÃO DEVEM aparecer no seletor e DEVEM permanecer consultáveis no painel de administração das contas conforme as autorizações aplicáveis.
- **FR-TCI-SEL-004**: A operação de listar as próprias contas NÃO DEVE aceitar como autoridade um identificador de usuário fornecido pela interface.
- **FR-TCI-SEL-005**: A seleção DEVE validar simultaneamente sessão, usuário, conta, tenant e associação antes de estabelecer o contexto.
- **FR-TCI-SEL-006**: Conhecer ou manipular o identificador de uma conta NÃO DEVE permitir selecioná-la nem confirmar sua existência além das informações já autorizadas ao usuário.
- **FR-TCI-SEL-007**: Conta ativa com associação ativa DEVE poder estabelecer contexto operacional, sujeito às chaves e funcionalidades vigentes.
- **FR-TCI-SEL-008**: Conta em criação, com erro ou cancelada NÃO DEVE estabelecer contexto para operações comuns de tenant.
- **FR-TCI-SEL-009**: Conta suspensa DEVE admitir somente contexto restrito às ações de consulta ou regularização explicitamente permitidas por suas concessões e regras de estado.
- **FR-TCI-SEL-009A**: O contexto restrito de conta suspensa DEVE permanecer claramente identificado na interface e NÃO DEVE apresentar módulos ou ações comuns como disponíveis.
- **FR-TCI-SEL-010**: A interface DEVE identificar de maneira persistente e acessível qual conta está ativa antes de o usuário confirmar uma operação contextual.

### Isolamento por Área de Trabalho

- **FR-TCI-WRK-001**: Cada aba ou área de trabalho DEVE manter seu próprio contexto de conta, sem alterar outras áreas autenticadas do mesmo usuário.
- **FR-TCI-WRK-002**: O contexto de autenticação do usuário poderá ser compartilhado entre suas áreas, mas o tenant ativo NÃO DEVE ser compartilhado implicitamente.
- **FR-TCI-WRK-003**: Duplicar uma área de trabalho DEVE criar contexto independente e poderá manter a mesma conta somente após nova validação da sessão, do usuário, da conta e da associação.
- **FR-TCI-WRK-003A**: Restaurar ou reabrir uma área após fechamento da aba, encerramento ou reinício do navegador DEVE iniciar sem tenant ativo e exigir nova seleção explícita.
- **FR-TCI-WRK-004**: Encerrar o contexto em uma área NÃO DEVE encerrar contextos válidos de outras áreas, salvo quando a sessão ou identidade global perder validade.
- **FR-TCI-WRK-005**: Navegação direta, histórico ou favorito NÃO DEVE substituir a seleção e validação do contexto exigido.

### Troca e Encerramento

- **FR-TCI-SWITCH-001**: A troca DEVE validar completamente o novo contexto antes de substituir o contexto anterior.
- **FR-TCI-SWITCH-002**: Falha na troca NÃO DEVE deixar identificadores, concessões, funcionalidades ou dados transitórios do novo tenant no contexto anterior.
- **FR-TCI-SWITCH-003**: Troca repetida para a mesma conta válida DEVE produzir resultado idempotente e não acumular autorizações ou efeitos.
- **FR-TCI-SWITCH-004**: Operação iniciada antes de uma troca DEVE permanecer vinculada ao contexto com que foi aceita e não poderá continuar parcialmente no novo tenant.
- **FR-TCI-SWITCH-005**: Ações com alterações locais não confirmadas DEVEM alertar o usuário antes de troca, retorno ao painel pessoal ou encerramento da sessão.
- **FR-TCI-SWITCH-006**: Retorno ao painel pessoal, logout, expiração ou invalidação da sessão DEVE limpar qualquer contexto ativo da respectiva área.
- **FR-TCI-SWITCH-007**: Após a limpeza, nenhuma operação DEVE reutilizar implicitamente o último tenant selecionado.

### Validade e Autorização

- **FR-TCI-VALID-001**: Toda nova operação de conta DEVE confirmar que usuário, associação e conta continuam em estado compatível com a ação solicitada.
- **FR-TCI-VALID-002**: A seleção da conta NÃO DEVE congelar permissões; cada operação protegida DEVE observar as concessões vigentes definidas em `access-control`.
- **FR-TCI-VALID-003**: Suspensão, remoção ou encerramento de associação DEVE inutilizar novas operações em todos os contextos daquela associação sem afetar outras contas do usuário.
- **FR-TCI-VALID-004**: Bloqueio, desativação ou cancelamento da identidade global DEVE inutilizar novas operações em todos os seus contextos.
- **FR-TCI-VALID-005**: Suspensão ou cancelamento da conta DEVE impedir operações incompatíveis com seu estado em todos os contextos ativos.
- **FR-TCI-VALID-006**: Administrador do sistema NÃO DEVE obter contexto de conta ou acesso a seus dados apenas por seu papel ou por possuir chaves globais.
- **FR-TCI-VALID-007**: Eventual acesso excepcional de suporte ou recuperação administrativa NÃO faz parte desta feature e DEVE ser definido em `system-administration`.
- **FR-TCI-VALID-008**: Operação cuja execução já tenha sido aceita após validação do contexto e da autorização DEVE poder terminar no tenant original mesmo que sua concessão, associação ou conta seja invalidada durante o processamento.
- **FR-TCI-VALID-009**: A conclusão autorizada por **FR-TCI-VALID-008** NÃO DEVE permitir nova operação, repetição, continuação posterior ou mudança para outro tenant; trabalhos enfileirados que ainda não iniciaram a execução DEVEM validar novamente o contexto e a autorização aplicável.

### Propagação e Fronteiras de Dados

- **FR-TCI-PROP-001**: Leituras, escritas, consultas, relatórios, caches, arquivos, eventos, filas, integrações e operações assíncronas com dados de conta DEVEM transportar e validar o tenant correspondente.
- **FR-TCI-PROP-002**: Chaves de cache, caminhos lógicos de arquivos, identificadores de eventos e resultados temporários DEVEM permanecer particionados pelo tenant quando puderem conter dados de conta.
- **FR-TCI-PROP-003**: Dados ou referências obtidos em um tenant NÃO DEVEM ser reutilizados em outro sem operação explicitamente definida, autorizada e auditada para transferência entre contas.
- **FR-TCI-PROP-004**: Operações globais DEVEM usar contexto de sistema explícito e NÃO DEVEM reutilizar tenant residual de sessão, thread, tarefa ou processamento anterior.
- **FR-TCI-PROP-005**: Operações de conta sem contexto válido NÃO DEVEM usar o armazenamento global como alternativa.
- **FR-TCI-PROP-006**: Toda fronteira que receba identificador de conta DEVE revalidá-lo contra o contexto autorizado, sem tratar o valor recebido como prova de acesso.
- **FR-TCI-PROP-007**: Falha durante processamento DEVE limpar o contexto transitório antes de reutilizar o mesmo recurso de execução em outra operação.

### Operações Assíncronas e Multi-Conta

- **FR-TCI-ASYNC-001**: Toda tarefa, mensagem ou evento persistente relacionado a conta DEVE registrar o tenant de destino e a identidade ou origem responsável quando aplicável.
- **FR-TCI-ASYNC-002**: A execução posterior DEVE estabelecer e validar o contexto declarado, independentemente da sessão que originou o trabalho.
- **FR-TCI-ASYNC-003**: Contexto ausente, inválido ou incompatível na execução DEVE interromper a operação sem redirecioná-la a outro tenant.
- **FR-TCI-ASYNC-004**: Trabalho assíncrono solicitado por usuário DEVE, por padrão, revalidar o contexto e a autorização do originador quando iniciar a execução.
- **FR-TCI-ASYNC-004A**: Uma feature poderá declarar explicitamente que o trabalho validamente aceito passa a ser responsabilidade do sistema e pode iniciar mesmo após perda posterior do acesso do originador; essa transferência DEVE possuir finalidade, momento de aceitação e auditoria definidos.
- **FR-TCI-ASYNC-004B**: Trabalho assumido pelo sistema ainda DEVE validar o tenant e o estado da conta antes de iniciar e NÃO DEVE obter acesso a outro contexto por causa da transferência de responsabilidade.
- **FR-TCI-ASYNC-004C**: Depois que a execução começar sob contexto válido, sua conclusão DEVE seguir **FR-TCI-VALID-008** e **FR-TCI-VALID-009**.
- **FR-TCI-ASYNC-005**: Processamento que percorra contas DEVE isolar falhas e contexto por conta, não permitindo que o término de uma unidade deixe contexto ativo para a seguinte.
- **FR-TCI-ASYNC-006**: Repetição ou reordenação de evento NÃO DEVE produzir efeito em tenant diferente daquele declarado originalmente.

### Auditoria, Privacidade e Experiência

- **FR-TCI-AUD-001**: Seleção, troca, encerramento, invalidação e tentativa negada de contexto DEVE produzir evento auditável com ator, tipo de contexto, conta quando aplicável, instante, origem e resultado.
- **FR-TCI-AUD-002**: Auditoria e logs NÃO DEVEM expor localização física do armazenamento, credenciais, concessões completas ou dados desnecessários de outra conta.
- **FR-TCI-AUD-003**: Mensagem de falha de seleção NÃO DEVE confirmar conta ou associação que o usuário não esteja autorizado a conhecer.
- **FR-TCI-AUD-004**: A interface DEVE permitir selecionar, trocar e encerrar contexto usando teclado e tecnologias assistivas.
- **FR-TCI-AUD-005**: Antes de operação sensível, a conta ativa DEVE permanecer identificável sem depender apenas de cor, posição ou memória da navegação anterior.
- **FR-TCI-AUD-006**: Quando o contexto perder validade, o usuário DEVE receber explicação segura e caminho para retornar ao painel pessoal ou selecionar outra conta disponível.

### Limites com Features Relacionadas

- **FR-TCI-BOUND-001**: Criação, estados cadastrais e manutenção da conta pertencem a `account-registration`; esta feature apenas aplica seus estados ao contexto.
- **FR-TCI-BOUND-002**: Estados e validade da associação pertencem a `account-membership`; esta feature os revalida antes e durante o uso do contexto.
- **FR-TCI-BOUND-003**: Grupos, chaves, concessões e explicação de acesso pertencem a `access-control`; esta feature não transforma seleção em autorização.
- **FR-TCI-BOUND-004**: Funcionalidades e franquias disponíveis pertencem a `plans-entitlements`; esta feature somente exige que sejam avaliadas no tenant correto.
- **FR-TCI-BOUND-005**: Localização física, criação, migração, recuperação e desativação do armazenamento do tenant pertencem a `tenant-storage-provisioning`.
- **FR-TCI-BOUND-006**: Regras de integridade entre dados globais e de tenant, backup, restauração, retenção e transferência pertencem a `tenant-data-governance`.

### Decisões de Infraestrutura Auditáveis

- **FR-TCI-INFRA-LOCK**: Trocas concorrentes e invalidações de contexto DEVEM produzir estado determinístico e consistente em todas as instâncias da aplicação.
- **FR-TCI-INFRA-IDEMP**: Repetições da mesma seleção, troca ou encerramento DEVEM produzir no máximo uma transição efetiva e nunca acumular dados ou autorizações.
- **FR-TCI-INFRA-SESSION**: Contextos ativos DEVEM acompanhar o ciclo de vida da área de trabalho e perder validade quando a sessão autenticada expirar ou for invalidada.
- **FR-TCI-INFRA-CACHE**: Cópias temporárias usadas para validar contexto DEVEM ser invalidadas ou consideradas obsoletas após mudança relevante, sem ampliar acesso durante indisponibilidade.
- **FR-TCI-INFRA-BACKUP**: Preferências persistidas de seleção e auditorias de contexto DEVEM participar dos backups, mas restauração NÃO DEVE reativar sessão ou contexto anteriormente encerrado.

### Key Entities

- **Execution Context**: delimitação obrigatória de uma operação como pessoal, global ou pertencente a uma conta, contendo somente as identidades necessárias para validação.
- **Account Context**: contexto de execução vinculado a exatamente uma conta e um tenant, com estado de validade próprio para uma área de trabalho.
- **System Context**: contexto explícito para operações globais, incompatível com acesso automático a dados de contas.
- **Context Transition**: tentativa de selecionar, trocar, invalidar ou encerrar contexto, com estado anterior, destino, resultado e rastreabilidade.
- **Context Audit Event**: registro imutável de transições e falhas relevantes sem exposição de dados internos desnecessários.
- **Contextual Work Item**: tarefa, evento ou mensagem que declara o tenant de destino e a origem necessária para execução posterior segura.

## Success Criteria

### Measurable Outcomes

- **SC-TCI-001**: Em 100% dos testes cruzados, uma operação aceita no tenant A não lê, altera, referencia ou autoriza dados do tenant B.
- **SC-TCI-002**: Em 100% dos testes sem contexto de conta válido, operações de tenant são negadas sem utilizar contexto global ou tenant anterior.
- **SC-TCI-003**: Em 100% dos testes multiaba, trocar a conta em uma área de trabalho não altera o contexto das demais.
- **SC-TCI-004**: Em 100% das trocas com falha, o contexto anterior permanece íntegro e nenhum dado ou autorização parcial do destino é observado.
- **SC-TCI-005**: Em 100% das novas operações após suspensão, remoção, bloqueio ou cancelamento aplicável, o contexto inválido deixa de autorizar acesso.
- **SC-TCI-006**: Em 100% dos testes assíncronos, cada trabalho acessa somente o tenant declarado e falha com segurança quando esse contexto não pode ser validado.
- **SC-TCI-007**: Em 100% dos testes, papel ou chave global de administrador do sistema não concede acesso automático a dados de conta.
- **SC-TCI-008**: Usuários conseguem identificar a conta ativa e trocar ou encerrar seu contexto em até 30 segundos durante testes de usabilidade.
- **SC-TCI-009**: Todas as jornadas de seleção, troca e encerramento podem ser concluídas apenas por teclado e sem perda da identificação da conta ativa.
- **SC-TCI-010**: Em 100% das transições e negações sensíveis testadas, existe auditoria com ator, contexto, instante e resultado, sem exposição da localização física do tenant.
