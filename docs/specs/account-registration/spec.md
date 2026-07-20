# Feature Specification: Cadastro de Contas

**Feature**: `account-registration`
**Created**: 2026-07-19
**Status**: Clarified — pronta para planejamento

## Escopo

Esta feature permite que um usuário ativo crie e mantenha contas. Cada conta constitui um tenant independente, com identidade, configurações básicas, ciclo de vida e isolamento próprios.

Inclui a criação segura do tenant, a associação fundadora do criador, a atribuição inicial do plano `Free`, a proteção contra automação, a edição dos dados cadastrais básicos e a suspensão ou cancelamento controlado da conta. Não inclui classificação da conta como pessoa física ou jurídica, dados fiscais, convites e associações adicionais, desenho geral de grupos e chaves, seleção do contexto ativo, definição completa de planos, cobrança ou módulos de negócio.

## Clarifications

### Session 2026-07-19

- Q: A conta inicial deve distinguir pessoa física e pessoa jurídica, e quantas contas um usuário pode criar ou participar? -> A: A conta será genérica, sem classificação PF/PJ ou identificador fiscal nesta etapa; um usuário poderá criar e participar de várias contas distintas.
- Q: Com qual plano e estado uma nova conta deve iniciar? -> A: A conta é ativada com o plano básico `Free`; suas franquias e funcionalidades serão definidas em `plans-entitlements`.

## User Scenarios & Testing

### User Story 1 - Criar uma conta (Priority: P1)

Um usuário ativo cria um espaço separado de sua identidade global para organizar dados e utilizar futuramente os módulos liberados para aquela conta.

**Why this priority**: estabelece o tenant mínimo que sustentará participantes, planos e módulos sem exigir antecipadamente uma natureza jurídica.

**Independent Test**: autenticar um usuário sem contas, concluir um cadastro válido e verificar a criação de um único tenant isolado com o criador como participante fundador.

**Acceptance Scenarios**:

1. **Given** usuário ativo e autorizado a criar conta, **When** fornece os dados mínimos e passa pelas proteções exigidas, **Then** uma única conta e seu tenant são criados e ativados no plano `Free`
2. **Given** falha em qualquer parte da criação, **When** a operação termina, **Then** não permanece tenant parcial, associação órfã ou concessão incompleta
3. **Given** repetição da mesma confirmação, **When** a solicitação é reenviada, **Then** o sistema retorna a conta já criada sem duplicá-la

---

### User Story 2 - Manter contas distintas (Priority: P1)

Um usuário cria mais de uma conta e participa de outras contas sem que identidades, configurações ou dados de um tenant sejam confundidos com os demais.

**Why this priority**: a participação multi-conta é parte central do produto e impede que a identidade global do usuário seja tratada como um tenant.

**Independent Test**: criar duas contas pelo mesmo usuário e verificar identificadores, tenants, associações fundadoras e configurações totalmente distintos.

**Acceptance Scenarios**:

1. **Given** usuário que já criou uma conta, **When** cria outra conta válida, **Then** o sistema mantém tenants e configurações independentes
2. **Given** usuário que participa de várias contas, **When** consulta a relação disponível, **Then** cada conta é identificável sem exposição de dados internos dos tenants
3. **Given** duas criações concorrentes pelo mesmo usuário, **When** representam intenções diferentes, **Then** ambas podem resultar em contas distintas sem compartilhamento de estado

---

### User Story 3 - Consultar e atualizar a conta (Priority: P2)

Um participante com concessões administrativas suficientes consulta e atualiza os dados básicos da conta, preservando histórico e sem alterar a identidade global dos usuários associados.

**Why this priority**: dados cadastrais mudam ao longo do tempo e precisam permanecer corretos e auditáveis.

**Independent Test**: partir de conta existente e concessão administrativa explícita, alterar um dado permitido e verificar auditoria, isolamento e preservação dos valores anteriores relevantes.

**Acceptance Scenarios**:

1. **Given** participante com chaves necessárias, **When** altera dado permitido, **Then** a conta é atualizada e a mudança é auditada
2. **Given** participante sem chaves necessárias, **When** tenta alterar a conta, **Then** a operação é negada independentemente do papel exibido
3. **Given** usuário associado a duas contas, **When** atualiza uma delas, **Then** nenhum dado da outra conta é alterado ou exposto

---

### User Story 4 - Suspender ou encerrar uma conta (Priority: P3)

Um administrador devidamente autorizado interrompe o uso ou solicita o encerramento de uma conta com confirmação reforçada, conhecendo os efeitos sobre participantes, planos e dados.

**Why this priority**: completa o ciclo de vida sem permitir exclusão acidental ou perda de rastreabilidade.

**Independent Test**: suspender uma conta ativa e verificar bloqueio de novas operações de tenant, preservação dos dados e possibilidade controlada de reversão conforme o estado.

**Acceptance Scenarios**:

1. **Given** administrador autorizado e reautenticado, **When** suspende a conta, **Then** novas operações de negócio são bloqueadas sem apagar dados
2. **Given** solicitação de cancelamento, **When** existem impedimentos legais, financeiros ou de responsabilidade, **Then** o sistema não conclui o encerramento e informa as pendências
3. **Given** conta encerrada, **When** um antigo participante tenta acessá-la, **Then** o sistema nega operações sem reutilizar o tenant como outra conta

### Edge Cases

- O usuário envia a criação repetidamente após perda da resposta.
- Duas instâncias processam simultaneamente a mesma intenção de criação.
- O Turnstile ou seu serviço de validação fica indisponível.
- Muitos usuários legítimos compartilham o mesmo IP de origem.
- O criador é bloqueado durante a criação da conta.
- A criação do tenant funciona, mas a associação fundadora falha.
- A moeda-base ou o fuso horário são alterados após existirem dados de módulos futuros.
- Uma conta muda nome, moeda-base ou fuso horário.
- A conta possui apenas um administrador capaz de concluir operações sensíveis.
- Uma restauração contém conta já cancelada ou criação parcialmente concluída.

## Requirements

### Modelo e Identidade da Conta

- **FR-ACC-001**: Toda conta DEVE possuir identidade própria, identificador imutável, nome de exibição, estado, moeda-base, fuso horário, criação e atualização.
- **FR-ACC-002**: A conta inicial DEVE ser genérica e NÃO DEVE exigir nem inferir classificação como pessoa física ou jurídica.
- **FR-ACC-003**: Toda conta criada DEVE corresponder a exatamente um tenant e todo dado futuro da conta DEVE ser associado explicitamente a esse tenant.
- **FR-ACC-004**: A identidade da conta NÃO DEVE substituir, copiar como autoridade nem alterar a identidade global de qualquer usuário.
- **FR-ACC-005**: Um usuário DEVE poder criar várias contas e participar de várias contas distintas, sujeito somente a concessões e limites futuros explicitamente aplicáveis.
- **FR-ACC-006**: A criação DEVE exigir somente nome de exibição, moeda-base e fuso horário como dados cadastrais da conta nesta etapa.
- **FR-ACC-007**: CPF, CNPJ, razão social, natureza jurídica e demais identificadores fiscais NÃO DEVEM ser exigidos, inferidos nem usados para unicidade nesta feature.
- **FR-ACC-008**: Nome de exibição NÃO DEVE ser utilizado como identificador único da conta.
- **FR-ACC-009**: O identificador imutável do tenant NÃO DEVE ser reutilizado após cancelamento, exclusão lógica ou restauração.
- **FR-ACC-010**: Classificação jurídica, filiais, estabelecimentos e estruturas organizacionais internas NÃO fazem parte desta feature.

### Criação da Conta

- **FR-ACC-CREATE-001**: Somente usuário ativo, autenticado e com autenticação recente DEVE iniciar a criação de conta.
- **FR-ACC-CREATE-002**: O sistema DEVE validar os dados cadastrais mínimos antes de criar o tenant.
- **FR-ACC-CREATE-003**: Nome de exibição, moeda-base e fuso horário DEVEM ser confirmados explicitamente antes da criação.
- **FR-ACC-CREATE-004**: A criação DEVE produzir atomicamente a conta, o tenant, a associação fundadora, as concessões mínimas de administração e a atribuição do plano `Free`; falha em qualquer parte NÃO DEVE deixar estado parcial utilizável.
- **FR-ACC-CREATE-005**: O criador DEVE ser identificado como administrador fundador da conta, mas seu acesso efetivo DEVE decorrer de grupo e chaves iniciais explícitos.
- **FR-ACC-CREATE-006**: O papel de administrador fundador NÃO DEVE conceder permissões fora das chaves explicitamente atribuídas.
- **FR-ACC-CREATE-007**: As concessões fundadoras DEVEM permitir manter a conta e preparar futuras associações, sem conceder acesso administrativo global ao sistema.
- **FR-ACC-CREATE-008**: Uma solicitação repetida com a mesma intenção DEVE retornar o resultado anterior ou estado consistente sem criar outra conta.
- **FR-ACC-CREATE-009**: A criação DEVE ser auditada com usuário fundador, origem, instante, identificador da conta e resultado, sem registrar provas anti-bot ou segredos.
- **FR-ACC-CREATE-010**: O usuário DEVE receber confirmação clara da criação, do plano `Free` atribuído e dos próximos passos disponíveis, sem apresentar funcionalidades não liberadas.

### Proteção contra Automação

- **FR-ACC-ABUSE-001**: A aplicação DEVE permitir configurar quantas contas podem ser criadas por IP dentro de uma janela antes de tornar o Turnstile obrigatório para novas tentativas dessa origem.
- **FR-ACC-ABUSE-002**: O limiar padrão DEVE ser zero, tornando o Turnstile obrigatório em toda tentativa de criação de conta até configuração diferente.
- **FR-ACC-ABUSE-003**: Quando obrigatório, o Turnstile DEVE ser validado no servidor antes de criar qualquer estado persistente da conta.
- **FR-ACC-ABUSE-004**: Token ausente, inválido, expirado ou reutilizado DEVE impedir a criação.
- **FR-ACC-ABUSE-005**: Além do desafio, a aplicação DEVE possuir limite rígido configurável de criações por IP e respectiva janela de restrição.
- **FR-ACC-ABUSE-006**: Limites DEVEM considerar ambientes com IP compartilhado e não podem conceder acesso cruzado nem bloquear permanentemente uma identidade por ação de terceiros.
- **FR-ACC-ABUSE-007**: A origem DEVE ser determinada de forma segura quando a aplicação estiver atrás de proxy reverso.
- **FR-ACC-ABUSE-008**: Se o Turnstile estiver indisponível quando obrigatório, a conta NÃO DEVE ser criada e o usuário DEVE ser orientado a tentar novamente.
- **FR-ACC-ABUSE-009**: Valores, janelas e períodos de bloqueio DEVEM ser configuráveis e auditáveis operacionalmente.

### Estado e Disponibilidade

- **FR-ACC-STATE-001**: A conta DEVE possuir ao menos os estados em criação, ativa, suspensa e cancelada.
- **FR-ACC-STATE-002**: Estado em criação NÃO DEVE permitir operações de tenant fora da conclusão ou recuperação segura da própria criação.
- **FR-ACC-STATE-003**: Uma conta criada com sucesso DEVE tornar-se ativa somente depois da atribuição válida do plano `Free`.
- **FR-ACC-STATE-004**: Conta suspensa NÃO DEVE aceitar novas operações de negócio, mas seus dados e auditoria DEVEM permanecer preservados e acessíveis apenas às ações autorizadas de regularização.
- **FR-ACC-STATE-005**: Conta cancelada NÃO DEVE aceitar operações de negócio, novos participantes ou reativação implícita.
- **FR-ACC-STATE-006**: Mudança de estado DEVE exigir chave específica, autenticação recente, confirmação explícita e auditoria.
- **FR-ACC-STATE-007**: Bloqueio ou desativação do criador NÃO DEVE alterar automaticamente o estado da conta nem conceder sua administração a outro usuário.
- **FR-ACC-STATE-008**: Suspensão ou cancelamento NÃO DEVE apagar imediatamente dados sujeitos a retenção, auditoria, obrigações legais ou financeiras.
- **FR-ACC-STATE-009**: Cancelamento DEVE verificar impedimentos conhecidos, incluindo responsabilidades administrativas, obrigações e retenções aplicáveis, antes da conclusão.
- **FR-ACC-PLAN-001**: A plataforma DEVE possuir um cadastro de planos com identificador estável, nome, estado e indicação de disponibilidade para atribuição.
- **FR-ACC-PLAN-002**: O cadastro DEVE conter exatamente um plano ativo designado como padrão de criação; inicialmente ele é o plano gratuito de código estável `FREE`, apresentado como `Free`.
- **FR-ACC-PLAN-003**: Toda nova conta DEVE receber o plano padrão vigente sem exigir escolha adicional do usuário; enquanto o padrão for o `FREE`, a atribuição não gera cobrança.
- **FR-ACC-PLAN-004**: Franquias, cotas, funcionalidades, preços, versões, definição do plano padrão e regras de transição DEVEM ser definidos em `plans-entitlements`, não nesta feature.
- **FR-ACC-PLAN-005**: A criação NÃO DEVE concluir se o plano padrão estiver ausente, duplicado, inativo ou indisponível, e NÃO DEVE deixar conta ou tenant parcial.
- **FR-ACC-PLAN-006**: A simples atribuição do plano padrão DEVE liberar somente as funcionalidades e franquias explicitamente vigentes em sua versão atribuída.

### Manutenção dos Dados Cadastrais

- **FR-ACC-MAINT-001**: Consultar ou alterar uma conta DEVE exigir contexto explícito do tenant e chaves de acesso vigentes nesse contexto.
- **FR-ACC-MAINT-002**: Papel, título ou associação isoladamente NÃO DEVE autorizar alteração.
- **FR-ACC-MAINT-003**: Alterações DEVEM validar novamente formato, domínio, país e regras de unicidade aplicáveis.
- **FR-ACC-MAINT-004**: Mudanças de moeda-base ou fuso horário DEVEM apresentar efeitos conhecidos e exigir confirmação reforçada quando puderem afetar dados futuros da conta.
- **FR-ACC-MAINT-005**: Dados que afetem registros históricos futuros NÃO DEVEM reescrever silenciosamente documentos, eventos ou lançamentos anteriores.
- **FR-ACC-MAINT-006**: Cada alteração sensível DEVE registrar autor, tenant, instante, origem, valores anteriores e novos de maneira adequada à privacidade.
- **FR-ACC-MAINT-007**: Atualizações concorrentes NÃO DEVEM sobrescrever silenciosamente alterações confirmadas por outro administrador.
- **FR-ACC-MAINT-008**: Um usuário associado a várias contas DEVE alterar somente a conta explicitamente selecionada e autorizada.
- **FR-ACC-MAINT-009**: Falha de validação ou persistência NÃO DEVE produzir atualização parcial observável.

### Limites com Features Posteriores

- **FR-ACC-BOUND-001**: Convites, entrada, saída, transferência e manutenção de participantes além do fundador pertencem a `account-membership`.
- **FR-ACC-BOUND-002**: Criação e manutenção geral de grupos e chaves pertencem a `access-control`; esta feature define somente o bootstrap mínimo e explícito do fundador.
- **FR-ACC-BOUND-003**: Seleção e propagação do contexto ativo pertencem a `tenant-context-isolation`.
- **FR-ACC-BOUND-004**: Definição completa do catálogo, contratação, cobrança, franquias e liberação de funcionalidades pertencem a `plans-entitlements`; esta feature mantém apenas o registro mínimo e a atribuição inicial necessários ao plano `Free`.
- **FR-ACC-BOUND-005**: Cadastro da conta NÃO DEVE liberar módulo ou funcionalidade por inferência de natureza jurídica, papel do fundador ou simples existência do tenant, mas somente pelos direitos vigentes do plano atribuído.

### Decisões de Infraestrutura Auditáveis

- **FR-ACC-INFRA-LOCK**: Validação de unicidade, criação do tenant, associação fundadora e transições de estado DEVEM permanecer consistentes entre múltiplas instâncias concorrentes.
- **FR-ACC-INFRA-IDEMP**: A criação DEVE aceitar repetição segura por intenção do usuário e manter a identificação dessa intenção pelo período operacional necessário para impedir duplicidade acidental.
- **FR-ACC-INFRA-BACKUP**: Contas, tenants, associações fundadoras, concessões e auditorias DEVEM participar dos backups; restauração NÃO DEVE reutilizar identificadores nem reativar contas canceladas no estado restaurado.
- **FR-ACC-INFRA-SCHED**: Restrições antiabuso temporárias DEVEM expirar automaticamente conforme suas janelas configuradas sem depender de intervenção manual.

### Key Entities

- **Account**: unidade genérica administrada no Rinos, com identidade, estado e configurações básicas, ainda sem classificação jurídica.
- **Tenant**: limite imutável de isolamento de dados e operações pertencente a uma única conta.
- **Founding Membership**: associação inicial entre o criador e a conta, identificando-o como administrador fundador sem substituir concessões explícitas.
- **Account Access Bootstrap**: conjunto inicial de grupo e chaves que permite ao fundador manter a conta até a gestão completa de acessos estar disponível.
- **Plan Reference**: registro estável de um plano disponível, incluindo obrigatoriamente um único plano padrão vigente; inicialmente, o plano gratuito `FREE`, sem antecipar suas franquias e regras comerciais.
- **Account Plan Assignment**: vínculo vigente entre uma conta e um plano, criado com o plano `Free` durante a ativação inicial.
- **Account Audit Event**: registro de criação, alteração sensível e transição de estado com ator, tenant, instante e efeito.

## Success Criteria

### Measurable Outcomes

- **SC-ACC-001**: Pelo menos 90% dos usuários em teste concluem um cadastro válido de conta em até três minutos sem ajuda.
- **SC-ACC-002**: Em 100% dos testes, cada conta criada corresponde a um tenant exclusivo e não reutilizado.
- **SC-ACC-003**: Em 100% dos testes de falha, não permanece tenant parcial, associação órfã ou concessão incompleta utilizável.
- **SC-ACC-004**: Em 100% dos testes concorrentes e de repetição, uma mesma intenção produz no máximo uma conta.
- **SC-ACC-005**: Em 100% dos testes de isolamento, dados de uma conta não são consultados nem alterados por contexto de outra.
- **SC-ACC-006**: Em 100% dos testes, o fundador recebe somente as concessões explícitas da própria conta e nenhum acesso administrativo global.
- **SC-ACC-007**: Com o limiar anti-bot padrão zero, 100% das tentativas de criação exigem Turnstile válido.
- **SC-ACC-008**: Em 100% dos testes, token anti-bot ausente, inválido, expirado ou reutilizado não cria conta nem estado parcial.
- **SC-ACC-009**: Em 100% dos testes, alteração sem chave vigente é negada mesmo quando o ator é identificado como administrador.
- **SC-ACC-010**: Em 100% dos testes, atualizar uma conta não modifica identidade global do usuário nem dados de outra conta.
- **SC-ACC-011**: Em 100% dos testes, conta suspensa ou cancelada não aceita novas operações de negócio.
- **SC-ACC-012**: Todas as jornadas de cadastro e manutenção podem ser concluídas apenas por teclado e sem bloqueios críticos de acessibilidade.
- **SC-ACC-013**: Em 100% dos testes, uma conta nova somente é ativada com atribuição válida do único plano padrão, sem liberar direitos não definidos; no catálogo inicial, esse plano é o `FREE` sem cobrança.
