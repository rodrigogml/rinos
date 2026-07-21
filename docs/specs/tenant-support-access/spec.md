# Feature Specification: Acesso Temporário de Suporte ao Tenant

**Feature**: `tenant-support-access`
**Created**: 2026-07-20
**Status**: Especificada e clarificada

## Escopo

Esta feature permite conceder a um operador de suporte devidamente autorizado acesso excepcional, temporário, limitado e integralmente auditado ao conteúdo de um único tenant para diagnóstico ou assistência solicitada. O acesso de suporte é um contexto próprio e visivelmente distinto: não transforma o operador em participante da conta, não concede grupos comuns do tenant e não decorre do papel Administrador do Sistema.

Toda concessão deve possuir solicitação, finalidade, justificativa, prazo, solicitante, operador e estado. Qualquer participante ativo pode solicitar ajuda no tenant em que participa; essa solicitação autoriza um operador diferente a visualizar, por tempo limitado, somente os mesmos dados que o solicitante ainda puder visualizar. O operador continua usando sua identidade global, precisa estar habilitado globalmente para suporte e nunca recebe capacidade de escrita. A ausência de qualquer condição resulta em negação.

Não inclui recuperação da administração de conta órfã, alteração comum de participantes ou grupos, exportação administrativa, restauração, migração, acesso direto ao armazenamento, atendimento jurídico a titulares, investigação irrestrita ou credenciais de infraestrutura. Também não autoriza visualizar senhas, tokens, segredos, chaves privadas, provas de autenticação ou dados de outro tenant.

## Clarifications

### Session 2026-07-20

- Q: O operador atua com identidade própria ou representa um participante da conta durante o suporte? -> A: Atua sempre com sua própria identidade em contexto de suporte distinto; impersonação ou representação de participante não é permitida.
- Q: Quem pode solicitar e aprovar o acesso normal de suporte e qual alcance é concedido? -> A: Qualquer participante ativo pode solicitar ajuda no próprio tenant; a solicitação é a autorização e concede a outro usuário habilitado como operador somente leitura e visualização do mesmo alcance que o solicitante possuir, pelo prazo máximo fixado no `application.properties`.

### Session 2026-07-21

- Q: Como o operador responsável por uma solicitação normal é escolhido? -> A: O sistema atribui automaticamente um único operador elegível e disponível, diferente do solicitante; os demais operadores não recebem acesso à solicitação nem ao tenant.
- Q: A primeira versão permite acesso emergencial ao tenant sem solicitação de um participante? -> A: Não. Sem solicitação válida de participante ativo, nenhum administrador ou operador global acessa o conteúdo do tenant.
- Q: Qual é a duração máxima padrão de uma sessão de suporte? -> A: Quatro horas, configuráveis exclusivamente no `application.properties`; o prazo começa no início efetivo da sessão e mudanças exigem reinicialização.

## User Scenarios & Testing

### User Story 1 - Solicitar acesso de suporte (Priority: P1)

Qualquer participante ativo descreve o problema e solicita assistência no tenant em que participa. A própria solicitação autoriza um operador diferente a visualizar temporariamente o mesmo alcance de dados que o solicitante puder visualizar, sem permitir alterações.

**Why this priority**: o consentimento direto e a limitação pelo próprio alcance do solicitante tornam o suporte simples sem converter uma função global em acesso permanente aos dados das contas.

**Independent Test**: criar solicitações por participantes com alcances diferentes e comprovar que somente um operador distinto e habilitado obtém visualização equivalente, temporária e sem escrita.

**Acceptance Scenarios**:

1. **Given** participante ativo no tenant, **When** informa finalidade e descrição válidas, **Then** uma solicitação rastreável é criada para atendimento somente leitura
2. **Given** solicitante pode visualizar apenas parte dos dados da conta, **When** pede ajuda, **Then** o suporte fica limitado dinamicamente ao mesmo alcance de visualização
3. **Given** usuário sem participação ativa no tenant, **When** tenta solicitar ajuda nele, **Then** a operação é negada
4. **Given** solicitante e operador são a mesma identidade, **When** o atendimento tenta iniciar, **Then** a operação é impedida
5. **Given** solicitação cancelada ou expirada, **When** um operador tenta utilizá-la, **Then** nenhum contexto de suporte é criado
6. **Given** solicitação válida e operadores elegíveis, **When** ela entra para atendimento, **Then** o sistema atribui exatamente um operador diferente do solicitante sem expor o tenant aos demais

---

### User Story 2 - Atuar em contexto explícito de suporte (Priority: P1)

Um operador global autorizado inicia uma sessão vinculada à solicitação e ao tenant exato, visualiza permanentemente que está em suporte e consulta somente dados que o solicitante ainda poderia visualizar, sem executar alterações.

**Why this priority**: o valor da feature é permitir assistência real sem enfraquecer isolamento, menor privilégio ou rastreabilidade da identidade efetiva.

**Independent Test**: iniciar uma sessão válida, tentar leituras permitidas, leituras fora do alcance e qualquer escrita e comprovar identidade do operador, tenant exclusivo, limitações e auditoria.

**Acceptance Scenarios**:

1. **Given** operador diferente do solicitante, com chave global de suporte, 2FA compatível e solicitação válida, **When** inicia o atendimento, **Then** entra em contexto de suporte exclusivo do tenant e da solicitação
2. **Given** sessão de suporte ativa, **When** o operador consulta dado ainda visível ao solicitante, **Then** a leitura usa a identidade real do operador e registra a finalidade do atendimento
3. **Given** escrita ou leitura que o solicitante não pode realizar, **When** é solicitada, **Then** é negada mesmo que o operador possua outra chave global ou participação comum na conta
4. **Given** operador possui participação comum no mesmo tenant, **When** inicia suporte, **Then** os dois contextos permanecem distintos e suas concessões não são combinadas
5. **Given** operador tenta alternar para outro tenant, **When** mantém a sessão de suporte, **Then** a operação é impedida e exige encerramento ou nova concessão própria

---

### User Story 3 - Revogar ou encerrar o atendimento (Priority: P1)

O solicitante ou o próprio operador encerra o atendimento, e o sistema também o encerra automaticamente no vencimento ou quando identidade, participação, conta ou habilitação do operador deixam de ser válidas.

**Why this priority**: um acesso excepcional somente é seguro quando termina de modo previsível, imediato e verificável.

**Independent Test**: revogar manualmente e expirar automaticamente sessões em vários estados, verificando bloqueio de novas operações e preservação do histórico.

**Acceptance Scenarios**:

1. **Given** acesso ativo, **When** responsável autorizado o revoga, **Then** novas operações são negadas imediatamente em todas as instâncias
2. **Given** prazo máximo alcançado, **When** nova operação é tentada, **Then** a sessão está encerrada mesmo que o processo periódico ainda não tenha executado
3. **Given** operador perde chave global, sessão válida ou aptidão de segurança, **When** tenta continuar, **Then** o contexto de suporte é invalidado
4. **Given** atendimento concluído, **When** operador o encerra, **Then** não pode reabri-lo sem nova concessão válida
5. **Given** operação já aceita antes da revogação, **When** está em execução, **Then** seu tratamento segue o contrato da feature consumidora e nenhuma repetição ou nova etapa inicia após a revogação

---

### User Story 4 - Acompanhar e auditar o acesso excepcional (Priority: P1)

Responsáveis autorizados pela conta e pela plataforma acompanham solicitações, sessões, operações e resultados dentro de seus respectivos escopos, distinguindo solicitante e operador sem expor conteúdo além do necessário.

**Why this priority**: suporte sobre dados de terceiros precisa ser explicável durante e depois do atendimento, inclusive para contestação e investigação.

**Independent Test**: executar ciclo completo com solicitação, início, leituras permitidas e negadas, revogação e consulta do histórico nos escopos global e do tenant.

**Acceptance Scenarios**:

1. **Given** solicitação ou sessão muda de estado, **When** o histórico é consultado, **Then** apresenta atores, tenant, finalidade, escopo, prazo, instante e resultado permitidos
2. **Given** operação sobre conteúdo do tenant, **When** é auditada, **Then** distingue solicitante, operador efetivo e solicitação de suporte
3. **Given** participante autorizado da conta, **When** consulta os atendimentos, **Then** visualiza somente solicitações e eventos daquele tenant
4. **Given** administrador global sem concessão de suporte ao tenant, **When** consulta a visão global, **Then** recebe metadados protegidos e não o conteúdo funcional acessado
5. **Given** sessão iniciada, revogada, expirada ou encerrada, **When** a mudança ocorre, **Then** os públicos definidos recebem notificação segura e rastreável

### Edge Cases

- A solicitação é criada no mesmo instante em que a participação do solicitante é suspensa ou encerrada.
- O operador perde 2FA, é bloqueado ou tem sua sessão global invalidada durante o atendimento.
- A conta é suspensa, cancelada, entra em migração ou apresenta incompatibilidade estrutural com uma sessão pendente ou ativa.
- A solicitação menciona uma feature que não está liberada no plano ou não existe na versão atual.
- Duas solicitações de suporte tratam o mesmo problema e tenant simultaneamente.
- O operador também participa normalmente da conta e possui permissões mais amplas fora do contexto de suporte.
- Uma operação assíncrona é aceita antes do vencimento, mas ainda não iniciou quando o acesso expira.
- A auditoria obrigatória fica indisponível no momento de uma operação de suporte.
- O alcance de visualização do solicitante aumenta ou diminui durante a sessão de suporte.
- Uma concessão é restaurada externamente depois de revogada ou expirada.
- Uma sessão permanece aberta quando alcança a duração máxima fixa da instalação.
- O operador tenta copiar dados para outro tenant, exportação ou canal não abrangido.
- A interface é fechada sem encerramento explícito da sessão de suporte.
- O tenant não possui participante apto a decidir sobre a solicitação.
- O mesmo operador tenta manter contextos de suporte simultâneos para contas diferentes.

## Requirements

### Identidade e Modelo de Atuação

- **FR-TSA-ID-001**: Operador DEVE atuar sempre com sua própria identidade global em contexto de suporte distinto e NÃO DEVE representar, impersonar ou produzir ações atribuídas a participante da conta.
- **FR-TSA-ID-002**: Papel Administrador do Sistema, vínculo empregatício, conhecimento do tenant ou acesso à infraestrutura NÃO DEVEM conceder acesso de suporte.
- **FR-TSA-ID-003**: Operador DEVE possuir identidade global ativa, e-mail confirmado, chave global específica de operação de suporte e 2FA compatível.
- **FR-TSA-ID-004**: Iniciar suporte NÃO DEVE criar associação, grupo, concessão comum, papel ou participação permanente no tenant.
- **FR-TSA-ID-005**: Contexto de suporte e contexto comum de participante NÃO DEVEM combinar chaves, permissões ou origem de auditoria.
- **FR-TSA-ID-006**: Cada operação DEVE preservar a identidade real do operador e a referência da concessão excepcional que a autorizou.
- **FR-TSA-ID-007**: Credenciais, sessões ou provas do participante solicitante NÃO DEVEM ser compartilhadas, copiadas ou utilizadas pelo operador.

### Solicitação e Autorização

- **FR-TSA-APR-001**: Qualquer participante ativo DEVE poder solicitar suporte no tenant em que participa sem chave administrativa específica; a própria solicitação constitui autorização limitada para o atendimento descrito.
- **FR-TSA-APR-002**: Solicitação DEVE identificar tenant, solicitante, finalidade específica, descrição segura do problema, criação, validade e estado.
- **FR-TSA-APR-003**: Solicitação NÃO DEVE copiar conteúdo sensível do tenant para campos globais de descrição ou evidência.
- **FR-TSA-APR-004**: Solicitação DEVE exigir sessão válida e confirmação explícita do tenant, finalidade, natureza somente leitura, prazo máximo e identidade distinta do futuro operador.
- **FR-TSA-APR-005**: O alcance autorizado DEVE ser derivado dinamicamente de tudo e somente o que o solicitante puder visualizar no tenant, incluindo restrições contextuais além das chaves de acesso.
- **FR-TSA-APR-006**: A solicitação NÃO DEVE conceder acesso antes de um operador diferente e elegível iniciar uma sessão e todas as condições serem revalidadas.
- **FR-TSA-APR-007**: O solicitante NÃO DEVE escolher acesso superior, adicionar capacidades de escrita ou delegar alcance que não possua.
- **FR-TSA-APR-008**: A criação da solicitação NÃO DEVE exigir chave administrativa, segundo fator ou aprovação adicional de outro participante na primeira versão.
- **FR-TSA-APR-009**: Solicitação aguardando atendimento DEVE possuir validade máxima de 7 dias e expirar quando não iniciada nesse prazo.
- **FR-TSA-APR-010**: Negação, cancelamento ou expiração DEVE ser definitiva para a solicitação; nova tentativa exige nova solicitação.
- **FR-TSA-APR-011**: Alteração de tenant, finalidade ou solicitante DEVE exigir nova solicitação; mudança no alcance normal de visualização do solicitante DEVE refletir-se dinamicamente sem criar permissão própria para o operador.
- **FR-TSA-APR-012**: Antes de iniciar e durante cada leitura, o sistema DEVE revalidar conta, armazenamento, solicitante, participação, alcance de visualização, operador, habilitação global e prazo.
- **FR-TSA-APR-013**: O sistema DEVE atribuir automaticamente cada solicitação normal a exatamente um operador elegível e disponível, sem permitir escolha manual pelo solicitante ou assunção livre por operadores.
- **FR-TSA-APR-014**: Operador elegível DEVE ser identidade ativa, diferente do solicitante, possuir habilitação global vigente para suporte, 2FA compatível e não manter outro contexto de suporte ativo.
- **FR-TSA-APR-015**: A atribuição DEVE ser indivisível e impedir que operadores concorrentes se tornem responsáveis pela mesma solicitação.
- **FR-TSA-APR-016**: Operadores não atribuídos NÃO DEVEM visualizar a descrição, o tenant, o solicitante, o conteúdo ou iniciar contexto a partir da solicitação; poderão receber somente métricas agregadas permitidas.
- **FR-TSA-APR-017**: Ausência de operador elegível DEVE manter a solicitação em espera, informar o solicitante sem revelar identidades internas e NÃO DEVE conceder acesso a qualquer operador.
- **FR-TSA-APR-018**: Rejeição, indisponibilidade ou perda de elegibilidade antes do início DEVE liberar a atribuição e permitir nova seleção automática, preservando histórico; o mesmo operador NÃO DEVE ser escolhido repetidamente para a mesma solicitação sem mudança de condição.

### Escopo e Capacidades

- **FR-TSA-SCP-001**: Cada concessão DEVE pertencer a exatamente um tenant e NÃO DEVE poder ser reutilizada em outro contexto.
- **FR-TSA-SCP-002**: O alcance de suporte NÃO DEVE ser selecionado manualmente nem usar rótulo de “acesso total”; DEVE acompanhar as decisões de visualização efetivas do solicitante.
- **FR-TSA-SCP-003**: A autorização efetiva DEVE exigir simultaneamente habilitação global do operador para suporte, solicitação vigente e autorização atual do solicitante para visualizar o dado ou recurso.
- **FR-TSA-SCP-004**: Chave global comum, participação própria do operador, papel ou plano isoladamente NÃO DEVEM ampliar o alcance derivado do solicitante.
- **FR-TSA-SCP-005**: Contexto de suporte normal DEVE ser estritamente somente leitura e NÃO DEVE permitir criar, alterar, excluir, confirmar, cancelar, executar processo ou produzir qualquer outro efeito funcional.
- **FR-TSA-SCP-006**: Dado de maior sensibilidade somente PODERÁ ser visualizado quando o próprio solicitante ainda puder visualizá-lo e a feature proprietária o declarar elegível para suporte observacional.
- **FR-TSA-SCP-007**: A concessão NÃO DEVE autorizar administração de participantes, grupos ou chaves, troca de responsável, alteração de plano ou cobrança, cancelamento da conta, exportação integral, restauração, migração ou destinação de dados.
- **FR-TSA-SCP-008**: A concessão NÃO DEVE autorizar leitura ou alteração de senha, hash, token, código, segredo TOTP, chave privada, credencial de integração ou prova reutilizável.
- **FR-TSA-SCP-009**: Feature consumidora DEVE declarar quais visualizações podem ser expostas ao suporte, sua sensibilidade, forma de aplicar o alcance do solicitante e auditoria, sem disponibilizar operações de escrita.
- **FR-TSA-SCP-010**: Funcionalidade indisponível ou incompatível com o estado do tenant NÃO DEVE ser habilitada apenas pela concessão de suporte.
- **FR-TSA-SCP-011**: Escrita ou leitura fora do alcance atual do solicitante DEVE ser negada e auditada sem revelar meios de ampliar o acesso.

### Sessão e Contexto de Suporte

- **FR-TSA-SES-001**: Cada início de atendimento DEVE criar contexto exclusivo vinculado ao operador atribuído, tenant, solicitação, concessão, finalidade, alcance de leitura e prazo.
- **FR-TSA-SES-002**: Somente o operador atribuído DEVE poder confirmar conscientemente o tenant e iniciar o contexto de suporte.
- **FR-TSA-SES-003**: Interface DEVE apresentar indicação persistente, inequívoca e acessível de suporte, tenant, finalidade, capacidades e tempo restante.
- **FR-TSA-SES-004**: Contexto de suporte NÃO DEVE ser inferido de rota, aba, último tenant utilizado, associação comum ou estado de outra sessão.
- **FR-TSA-SES-005**: Cada aba ou área de trabalho DEVE manter contexto independente e revalidado, sem propagação implícita para outras abas.
- **FR-TSA-SES-006**: Uma sessão de suporte DEVE abranger somente um tenant e uma concessão; troca exige encerramento e novo início explícito.
- **FR-TSA-SES-007**: O mesmo operador NÃO DEVE manter simultaneamente mais de um contexto de suporte ativo.
- **FR-TSA-SES-008**: Sessão de suporte DEVE possuir duração máxima definida exclusivamente no `application.properties`, com padrão de 4 horas no código quando ausente, e alteração somente produzirá efeito após reinicialização do serviço.
- **FR-TSA-SES-009**: Renovação ou extensão NÃO DEVE ser automática; depois do vencimento, o participante deverá criar nova solicitação.
- **FR-TSA-SES-010**: Fechamento da interface NÃO DEVE prolongar o prazo; restauração posterior DEVE exigir novo início e revalidação da concessão ainda vigente.
- **FR-TSA-SES-011**: Toda operação DEVE revalidar identidade, sessão global, 2FA aplicável, tenant, concessão, capacidade, estado e prazo.
- **FR-TSA-SES-012**: Falha, ambiguidade ou indisponibilidade em qualquer validação DEVE resultar em negação segura.
- **FR-TSA-SES-013**: A duração da sessão DEVE começar no início confirmado do contexto de suporte, e não na criação da solicitação nem na atribuição do operador.

### Revogação, Expiração e Concorrência

- **FR-TSA-REV-001**: Solicitante, operador ou administrador global com chave de segurança específica DEVEM poder encerrar ou revogar o acesso conforme seu escopo.
- **FR-TSA-REV-002**: Revogação, expiração, encerramento, bloqueio do operador, perda de chave global ou invalidação da conta DEVEM impedir novas operações imediatamente em todas as instâncias.
- **FR-TSA-REV-003**: Sessão revogada, expirada ou encerrada NÃO DEVE ser reativada; retomada exige concessão ainda válida e novo início quando permitido, ou nova solicitação.
- **FR-TSA-REV-004**: Operação aceita antes da revogação PODERÁ terminar somente quando a feature consumidora declarar esse comportamento; novas etapas, repetições e trabalhos ainda não iniciados DEVEM ser negados.
- **FR-TSA-REV-005**: Trabalho assíncrono ainda não iniciado DEVE revalidar todo o acesso no início, salvo contrato explícito que o tenha transferido à responsabilidade do sistema sem ampliar seu escopo.
- **FR-TSA-REV-006**: Solicitação, início, revogação e encerramento concorrentes DEVEM produzir um único estado efetivo e explicável.
- **FR-TSA-REV-007**: Restauração externa NÃO DEVE reativar solicitação, concessão ou sessão cujo vencimento passou ou cuja revogação possa ser comprovada por evidência vigente.
- **FR-TSA-REV-008**: Depois de iniciada a sessão, perda de elegibilidade do operador DEVE encerrá-la e NÃO DEVE transferir o contexto ou seu tempo restante a outro operador; continuidade exige nova atribuição e novo início ainda permitido pela solicitação.

### Ausência de Solicitação

- **FR-TSA-EMG-001**: A primeira versão NÃO DEVE permitir acesso emergencial, urgente ou extraordinário ao conteúdo do tenant sem solicitação válida de participante ativo.
- **FR-TSA-EMG-002**: Papel, chave global, urgência declarada, indisponibilidade de participante, gravidade alegada ou acesso à infraestrutura NÃO DEVEM substituir a solicitação.
- **FR-TSA-EMG-003**: Operações globais de saúde, suspensão, migração ou recuperação DEVEM permanecer limitadas aos metadados e contratos de suas próprias features e NÃO DEVEM abrir contexto funcional de suporte.
- **FR-TSA-EMG-004**: Toda tentativa de contornar a solicitação DEVE ser negada e auditada como evento de segurança sem revelar conteúdo do tenant.
- **FR-TSA-EMG-005**: Uma capacidade emergencial futura DEVE exigir nova especificação e revisão dos contratos de isolamento, autorização, auditoria e transparência; configuração isolada NÃO DEVE habilitá-la.

### Auditoria, Transparência e Notificações

- **FR-TSA-AUD-001**: Solicitação, atribuição, reatribuição, rejeição, início, consulta, operação, negação, revogação, expiração, encerramento e tentativa de contorno DEVEM ser auditados conforme o risco.
- **FR-TSA-AUD-002**: Evento DEVE distinguir tenant, solicitante, operador efetivo, solicitação, alcance de leitura aplicado, finalidade, alvo seguro, instante, origem validada, correlação e resultado.
- **FR-TSA-AUD-003**: Visão global DEVE armazenar somente metadados necessários; detalhes e conteúdo funcional do atendimento DEVEM permanecer no tenant e sujeitos às chaves contextuais.
- **FR-TSA-AUD-004**: Auditoria NÃO DEVE copiar senha, segredo, token, prova reutilizável ou conteúdo pessoal, financeiro ou empresarial além do estritamente necessário.
- **FR-TSA-AUD-005**: Valores anteriores e posteriores somente DEVEM ser registrados quando exigidos pela feature consumidora, com redução ou proteção compatível com sua classificação.
- **FR-TSA-AUD-006**: Falha na auditoria obrigatória DEVE impedir operação de suporte crítica ou produzir evidência alternativa durável e reconciliável antes de declará-la concluída.
- **FR-TSA-AUD-007**: Participante com chave de auditoria no tenant DEVE poder consultar histórico de suporte daquela conta sem receber eventos de outro tenant.
- **FR-TSA-AUD-008**: Consulta global do atendimento DEVE exigir chave própria e não conceder acesso ao conteúdo funcional do tenant.
- **FR-TSA-AUD-009**: Solicitante e públicos autorizados do tenant DEVEM ser notificados sobre solicitação, início, revogação, expiração e encerramento.
- **FR-TSA-AUD-010**: Notificação DEVE informar ação, tenant, operador identificável, finalidade, escopo, duração e canal de contestação sem copiar conteúdo protegido.
- **FR-TSA-AUD-011**: Falha de entrega de notificação NÃO DEVE prolongar nem ampliar acesso, mas DEVE permanecer visível e sujeita a nova tentativa.
- **FR-TSA-AUD-012**: Registros DEVEM seguir classificação, retenção, impedimento, proteção externa, restauração e destinação de `tenant-data-governance`.

### Segurança e Experiência

- **FR-TSA-SEC-001**: Todas as operações DEVEM ser negadas por padrão e verificadas tanto na interface quanto na capacidade executada.
- **FR-TSA-SEC-002**: Início do atendimento DEVE exigir autenticação do operador ocorrida há no máximo 15 minutos por TOTP ou passkey compatível.
- **FR-TSA-SEC-003**: Ausência de associação comum do operador ao tenant NÃO DEVE impedir suporte solicitado, e existência de associação comum NÃO DEVE ampliá-lo.
- **FR-TSA-SEC-004**: Dados visualizados em suporte NÃO DEVEM ser reutilizados para finalidade incompatível, treinamento, teste, demonstração ou outro atendimento.
- **FR-TSA-SEC-005**: Cópia, download, impressão ou exportação de conteúdo DEVEM permanecer bloqueados no contexto normal de suporte.
- **FR-TSA-SEC-006**: Diagnósticos e mensagens de erro DEVEM evitar revelar existência de dados, participantes ou estrutura de outro tenant.
- **FR-TSA-SEC-007**: A interface NÃO DEVE apresentar ações de escrita como disponíveis e a operação protegida DEVE recusá-las mesmo quando invocadas fora da interface.
- **FR-TSA-SEC-008**: Jornadas DEVEM ser navegáveis por teclado, compreensíveis sem depender somente de cor e manter o indicador de suporte perceptível em diferentes tamanhos de tela.

### Decisões de Infraestrutura Auditáveis

- **FR-TSA-INFRA-SCHED**: Solicitações, concessões e sessões vencidas DEVEM ser reconciliadas automaticamente ao menos a cada minuto e também rejeitadas no momento de toda decisão, sem depender somente do processo periódico.
- **FR-TSA-INFRA-LOCK**: Assunção, início, revogação e encerramento DEVEM permanecer consistentes entre múltiplas instâncias e impedir duas transições incompatíveis.
- **FR-TSA-INFRA-IDEMP**: Cada intenção sensível DEVE possuir identidade estável suficiente para repetição sem duplicar solicitação, sessão, revogação, evento ou efeito funcional.
- **FR-TSA-INFRA-CACHE**: Cópias temporárias de concessão ou autorização DEVEM perder validade imediatamente após revogação, expiração ou mudança de condição, sem ampliar acesso durante falha.
- **FR-TSA-INFRA-BACKUP**: Solicitações, concessões, sessões e auditorias DEVEM participar da proteção externa aplicável; restauração NÃO DEVE reativar acesso vencido ou comprovadamente revogado.
- **FR-TSA-INFRA-CLOCK**: Validade, duração e ordenação DEVEM usar referência temporal confiável e negar acesso quando divergência puder prolongar indevidamente a concessão.

### Limites com Features Relacionadas

- **FR-TSA-BOUND-001**: `access-control` permanece autoridade sobre chaves globais, chaves do tenant e decisões comuns; esta feature exige uma composição excepcional explícita sem criar grupos ou concessões comuns.
- **FR-TSA-BOUND-002**: `tenant-context-isolation` permanece autoridade sobre contexto comum; o contexto de suporte é distinto, explícito e sujeito ao mesmo princípio de isolamento inviolável.
- **FR-TSA-BOUND-003**: `system-directory-administration` localiza usuários e contas e trata intervenções globais, mas não concede acesso ao conteúdo do tenant.
- **FR-TSA-BOUND-004**: `account-membership` permanece autoridade sobre participação comum; suporte não cria associação nem convite.
- **FR-TSA-BOUND-005**: `user-authentication` permanece autoridade sobre sessão, autenticação recente e fatores compatíveis.
- **FR-TSA-BOUND-006**: `tenant-data-governance` permanece autoridade sobre auditoria transversal, classificação, retenção, exportação, restauração e destinação.
- **FR-TSA-BOUND-007**: `platform-operations` apresenta saúde e ocorrências estruturais sem conteúdo; acesso funcional de suporte somente ocorre por esta feature.
- **FR-TSA-BOUND-008**: A duração máxima da sessão pertence exclusivamente ao `application.properties` como configuração fixa da instalação; `platform-configuration` NÃO DEVE duplicá-la no banco.
- **FR-TSA-BOUND-009**: Cada módulo funcional permanece responsável por declarar operações elegíveis para suporte e seus efeitos, sem enfraquecer as proibições transversais.

### Key Entities

- **Support Access Request**: pedido e autorização rastreável vinculados a tenant, participante solicitante, finalidade, problema descrito de forma segura, validade e estado.
- **Support Assignment**: vínculo exclusivo e auditável entre uma solicitação e o único operador automaticamente selecionado, com elegibilidade, criação, aceite ou rejeição e término.
- **Support Grant**: autorização excepcional e temporária que relaciona tenant, solicitante, operador diferente, alcance de visualização derivado, finalidade, início, término e estado sem criar associação comum.
- **Support Session**: contexto ativo exclusivo que relaciona operador, concessão, tenant, capacidades, início, última atividade e encerramento.
- **Support Read Scope**: alcance dinâmico correspondente às visualizações que o solicitante ainda pode realizar e que a feature declara elegíveis para suporte observacional.
- **Support Audit Event**: evidência protegida de solicitação, atribuição, contexto, operação, negação, transição ou tentativa de contorno.

## Success Criteria

### Measurable Outcomes

- **SC-TSA-001**: Em 100% dos testes, papel, chave global comum ou conhecimento do tenant isoladamente não concedem acesso ao conteúdo da conta.
- **SC-TSA-002**: Em 100% das operações permitidas, existe solicitação ou fundamento excepcional válido, tenant explícito, solicitante ativo, operador diferente identificado, finalidade e prazo rastreáveis.
- **SC-TSA-003**: Em 100% dos testes cruzados, solicitação de suporte de uma conta não autoriza leitura, escrita, inferência ou operação em outra.
- **SC-TSA-004**: Em 100% dos testes, capacidades comuns do operador como participante e capacidades de suporte não são combinadas.
- **SC-TSA-005**: Em 100% dos testes, o operador visualiza no máximo os dados que o solicitante pode visualizar naquele instante e nenhuma escrita produz efeito.
- **SC-TSA-006**: Em 100% das revogações, expirações e perdas de aptidão testadas, novas operações são negadas imediatamente em todas as instâncias.
- **SC-TSA-007**: Em 100% das sessões, o indicador de suporte, tenant, finalidade e tempo restante permanece perceptível durante toda a navegação.
- **SC-TSA-008**: Nenhum teste, auditoria, notificação ou diagnóstico expõe senha, token, código, segredo, chave privada ou prova reutilizável.
- **SC-TSA-009**: Em 100% das operações críticas, a auditoria obrigatória é registrada ou a operação não é declarada concluída.
- **SC-TSA-010**: Em 100% das consultas do tenant, somente solicitações, sessões e eventos da própria conta são retornados.
- **SC-TSA-011**: Em 100% das consultas globais sem concessão contextual, nenhum conteúdo funcional do tenant é exibido.
- **SC-TSA-012**: Em 100% das repetições concorrentes, cada solicitação, início, revogação ou encerramento produz no máximo um efeito.
- **SC-TSA-013**: Pelo menos 90% dos solicitantes de teste criam uma solicitação válida em até 3 minutos sem ajuda.
- **SC-TSA-014**: Pelo menos 90% dos solicitantes de teste compreendem que o atendimento é somente leitura, limitado ao seu próprio alcance e temporário antes de confirmar.
- **SC-TSA-015**: Todas as jornadas podem ser concluídas apenas por teclado e mantêm contexto, risco e estado compreensíveis sem depender somente de cor.
- **SC-TSA-016**: Em 100% das restaurações externas testadas, acesso vencido ou comprovadamente revogado não volta a produzir efeito.
- **SC-TSA-017**: Em 100% das operações de suporte, a autoria permanece vinculada ao operador real e nenhuma ação é apresentada ou auditada como executada por participante representado.
- **SC-TSA-018**: Em 100% das atribuições concorrentes, no máximo um operador elegível e diferente do solicitante recebe a solicitação; operadores não atribuídos não obtêm seus dados nem acesso ao tenant.
- **SC-TSA-019**: Em 100% das tentativas sem solicitação válida de participante ativo, nenhum administrador ou operador global obtém contexto ou conteúdo funcional do tenant.
- **SC-TSA-020**: Com a configuração padrão, 100% das sessões expiram em até 4 horas contadas do início efetivo; alteração do prazo somente produz efeito depois da reinicialização do serviço.
