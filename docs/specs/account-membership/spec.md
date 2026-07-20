# Feature Specification: Participação de Usuários em Contas

**Feature**: `account-membership`
**Created**: 2026-07-19
**Status**: Clarified — pronta para planejamento

## Escopo

Esta feature vincula a identidade global de um usuário a uma ou mais contas por associação fundadora ou convite. Ela define convite, aceite, recusa, expiração, suspensão, remoção e saída, preservando isolamento e histórico.

Papéis identificam o ator no contexto da conta, mas não concedem acesso. Grupos e chaves pertencem a `access-control`; seleção da conta ativa pertence a `tenant-context-isolation`. Esta feature também não cria usuários automaticamente nem permite acesso antes do aceite e das concessões exigidas.

## Clarifications

### Session 2026-07-19

- Q: Um convite pode ser enviado a pessoa ainda não cadastrada? -> A: Sim; o convite conduz ao cadastro, permanece vinculado ao e-mail confirmado e somente cria associação após ativação da identidade e aceite explícito.
- Q: Por quanto tempo um convite permanece válido? -> A: 15 dias; após expirar, torna-se inutilizável e somente um novo convite pode substituir o ciclo encerrado.
- Q: O último participante com capacidade administrativa pode sair, ser removido, suspenso ou rebaixado? -> A: Não; a operação fica bloqueada até outro participante ativo possuir as chaves administrativas mínimas e 2FA compatível. Exceções pertencem à administração do sistema.

## User Scenarios & Testing

### User Story 1 - Convidar participante (Priority: P1)

Um administrador com chave apropriada convida uma pessoa pelo e-mail para participar de uma conta, informa como o ator será identificado e acompanha o estado do convite.

**Why this priority**: permite formar a equipe da conta sem duplicar identidades ou conceder acesso implicitamente.

**Independent Test**: convidar um e-mail ainda não associado à conta e verificar a criação de um único convite pendente sem associação ativa.

**Acceptance Scenarios**:

1. **Given** administrador com chave de convite, **When** informa e-mail válido e papel de ator, **Then** o sistema cria convite pendente e envia instrução segura
2. **Given** usuário sem chave de convite, **When** tenta convidar, **Then** a operação é negada independentemente de seu papel
3. **Given** e-mail já associado ativamente, **When** novo convite é solicitado, **Then** nenhuma associação ou convite duplicado é criado

---

### User Story 2 - Aceitar ou recusar convite (Priority: P1)

Uma pessoa convidada autentica ou conclui seu cadastro, conhece a conta e o papel proposto e decide aceitar ou recusar sem receber acesso antes da decisão.

**Why this priority**: garante consentimento e vincula a associação à identidade correta.

**Independent Test**: consumir convite válido com identidade correspondente e comprovar que exatamente uma associação ativa é criada para aquela conta.

**Acceptance Scenarios**:

1. **Given** convite válido e identidade correspondente, **When** o usuário aceita, **Then** a associação torna-se ativa uma única vez
2. **Given** convite válido, **When** o usuário recusa, **Then** ele é encerrado sem criar associação
3. **Given** convite inválido, expirado, revogado ou destinado a outro e-mail, **When** alguém tenta aceitá-lo, **Then** nenhum vínculo é criado
4. **Given** convidado ainda não cadastrado, **When** acessa o convite, **Then** o sistema conduz ao cadastro sem perder o vínculo pendente conforme política definida

---

### User Story 3 - Administrar participantes (Priority: P2)

Um administrador autorizado consulta participantes e convites da conta, altera a identificação de ator, suspende ou remove associações e revoga convites.

**Why this priority**: mantém a equipe correta e permite resposta rápida a mudanças ou riscos.

**Independent Test**: suspender um participante e verificar bloqueio imediato do contexto daquela conta sem afetar suas outras contas.

**Acceptance Scenarios**:

1. **Given** administrador autorizado, **When** suspende participante, **Then** o acesso daquela associação é interrompido sem afetar outras contas do usuário
2. **Given** alteração de papel, **When** é confirmada, **Then** apenas a identificação do ator muda; permissões não são inferidas
3. **Given** participante removido, **When** tenta usar sessão existente na conta, **Then** o acesso é negado imediatamente

---

### User Story 4 - Sair de uma conta (Priority: P2)

Um participante encerra voluntariamente sua associação com uma conta sem cancelar sua identidade global nem afetar suas demais participações.

**Why this priority**: garante autonomia ao usuário e separação entre identidade e tenants.

**Independent Test**: sair de uma conta e verificar que somente a associação escolhida é encerrada.

**Acceptance Scenarios**:

1. **Given** participante ativo comum, **When** confirma a saída, **Then** sua associação é encerrada e outras contas permanecem inalteradas
2. **Given** último participante com chaves administrativas mínimas, **When** tenta sair, **Then** o sistema bloqueia a operação até existir substituto ativo e apto
3. **Given** saída concluída, **When** o usuário consulta suas contas, **Then** a conta não aparece como participação ativa

### Edge Cases

- Convites concorrentes são enviados ao mesmo e-mail e conta.
- O e-mail principal do convidado muda após a emissão.
- O convidado abre o convite autenticado como outra identidade.
- O convite é aceito simultaneamente em dois dispositivos.
- O participante é suspenso enquanto usa a conta.
- A conta é suspensa ou cancelada com convites abertos.
- O último administrador tenta sair ou ser removido.
- Um participante removido é convidado novamente.
- A entrega do e-mail falha após a criação do convite.
- Uma restauração contém convite expirado, aceito ou revogado.

## Requirements

### Associação e Identidade do Ator

- **FR-MEM-001**: Uma associação DEVE vincular exatamente um usuário global a exatamente uma conta.
- **FR-MEM-002**: A mesma identidade DEVE poder possuir associações independentes com várias contas.
- **FR-MEM-003**: Usuário e conta DEVEM possuir no máximo uma associação vigente entre si.
- **FR-MEM-004**: A associação DEVE possuir ao menos os estados convidada, ativa, suspensa, encerrada por saída e encerrada por remoção.
- **FR-MEM-005**: A associação DEVE registrar o papel contextual do ator: colaborador, contador ou parceiro externo, ou administrador da conta.
- **FR-MEM-006**: Papel de ator NÃO DEVE conceder grupo, chave ou permissão por si próprio.
- **FR-MEM-007**: A associação fundadora criada com a conta DEVE integrar o mesmo modelo de participação, preservando sua origem e auditoria.
- **FR-MEM-008**: Consultas e alterações DEVEM exigir conta explícita e jamais usar associação de outro tenant.
- **FR-MEM-009**: Bloqueio, desativação ou cancelamento do usuário global DEVE impedir o uso de todas as associações sem apagar seus históricos.
- **FR-MEM-010**: Suspensão ou cancelamento da conta DEVE impedir uso de suas associações conforme o estado da conta.

### Convites

- **FR-MEM-INV-001**: Somente participante ativo com chave explícita para convidar DEVE emitir convite.
- **FR-MEM-INV-002**: O convite DEVE identificar conta, e-mail destinatário, ator convidante, papel contextual proposto, criação, validade e estado.
- **FR-MEM-INV-003**: Convite destinado a pessoa ainda não cadastrada DEVE conduzi-la ao cadastro, permanecer vinculado ao e-mail destinatário normalizado e somente poderá ser aceito após a ativação de uma identidade cujo e-mail principal confirmado corresponda ao convite.
- **FR-MEM-INV-004**: O convite DEVE usar prova imprevisível, temporária, vinculada à finalidade e utilizável uma única vez.
- **FR-MEM-INV-005**: O convite DEVE permanecer válido por 15 dias após sua emissão, salvo revogação, substituição ou consumo anterior.
- **FR-MEM-INV-006**: O e-mail de um convite emitido NÃO DEVE ser alterado; correção exige revogação e novo convite.
- **FR-MEM-INV-007**: Novo convite pendente para a mesma conta e e-mail DEVE substituir o anterior ou retornar o pendente existente sem produzir múltiplas provas válidas.
- **FR-MEM-INV-008**: Usuário já associado ativamente NÃO DEVE receber novo convite para a mesma conta.
- **FR-MEM-INV-009**: O convidante DEVE poder reenviar ou revogar convite pendente conforme limites configuráveis.
- **FR-MEM-INV-010**: Reenvio DEVE invalidar a prova anterior e manter histórico da tentativa.
- **FR-MEM-INV-010A**: Convite expirado NÃO DEVE ser reativado; nova tentativa DEVE criar novo convite e preservar o ciclo expirado no histórico.
- **FR-MEM-INV-011**: Mensagens públicas NÃO DEVEM revelar se o e-mail pertence a usuário cadastrado ou participa de outras contas.
- **FR-MEM-INV-012**: A criação do convite NÃO DEVE criar usuário, associação ativa, sessão ou permissão.
- **FR-MEM-INV-012A**: A conclusão do cadastro NÃO DEVE aceitar automaticamente o convite; o usuário ativado DEVE visualizar a proposta e decidir explicitamente.
- **FR-MEM-INV-013**: Conta suspensa ou cancelada NÃO DEVE emitir novos convites; seus convites abertos DEVEM deixar de ser aceitáveis.
- **FR-MEM-INV-014**: Envio e reenvio DEVEM possuir limites configuráveis por conta, convidante, destinatário e origem.

### Aceite e Recusa

- **FR-MEM-ACCEPT-001**: Aceite ou recusa DEVE exigir usuário autenticado cujo e-mail principal confirmado corresponda ao destinatário normalizado.
- **FR-MEM-ACCEPT-002**: O usuário DEVE visualizar identificação suficiente da conta, convidante e papel proposto antes de decidir.
- **FR-MEM-ACCEPT-003**: O aceite DEVE revalidar convite, usuário, conta, ausência de associação vigente e limites do plano antes de criar a associação.
- **FR-MEM-ACCEPT-004**: Aceite válido DEVE consumir a prova e ativar exatamente uma associação de forma atômica.
- **FR-MEM-ACCEPT-005**: Recusa DEVE consumir o convite sem criar associação e sem informar ao convidante dados além da decisão necessária.
- **FR-MEM-ACCEPT-006**: Prova inválida, expirada, revogada, consumida ou de outro destinatário NÃO DEVE criar associação.
- **FR-MEM-ACCEPT-007**: A associação ativa NÃO DEVE conceder acesso funcional antes das atribuições explícitas exigidas por `access-control`.
- **FR-MEM-ACCEPT-008**: Aceite, recusa e falha DEVEM ser auditados sem registrar a prova secreta.

### Administração e Ciclo de Vida

- **FR-MEM-LIFE-001**: Listar participantes ou convites DEVE exigir chaves específicas no tenant e exibir somente dados necessários à administração.
- **FR-MEM-LIFE-002**: Alterar papel contextual DEVE exigir chave específica e NÃO DEVE alterar implicitamente grupos ou chaves.
- **FR-MEM-LIFE-003**: Suspender, reativar ou remover associação DEVE exigir chave específica, autenticação recente, confirmação e auditoria.
- **FR-MEM-LIFE-004**: Suspensão ou remoção DEVE invalidar imediatamente o uso do contexto da conta em todas as sessões do participante afetado.
- **FR-MEM-LIFE-005**: Suspensão NÃO DEVE apagar a associação nem seu histórico e poderá ser revertida por operação autorizada.
- **FR-MEM-LIFE-006**: Remoção DEVE encerrar a associação sem apagar autoria ou rastreabilidade de ações anteriores.
- **FR-MEM-LIFE-007**: Nova entrada de usuário removido DEVE exigir novo convite e novo aceite, preservando o ciclo anterior.
- **FR-MEM-LIFE-008**: Um participante ativo DEVE poder sair após autenticação recente e confirmação explícita.
- **FR-MEM-LIFE-009**: Saída DEVE afetar apenas a associação selecionada e nunca cancelar o usuário global.
- **FR-MEM-LIFE-010**: Saída, remoção, suspensão, rebaixamento de papel ou retirada de concessões NÃO DEVEM deixar a conta sem ao menos um participante ativo com todas as chaves administrativas mínimas e 2FA compatível.
- **FR-MEM-LIFE-010A**: O papel de administrador, isoladamente, NÃO DEVE satisfazer a continuidade; as chaves vigentes e a capacidade de cumprir o 2FA obrigatório DEVEM ser verificadas no momento da operação.
- **FR-MEM-LIFE-010B**: Procedimento excepcional de recuperação por administrador do sistema fica fora desta feature e DEVE ser definido em `system-administration` com confirmação reforçada e auditoria.
- **FR-MEM-LIFE-011**: Nenhuma alteração de associação DEVE transferir automaticamente propriedade, responsabilidades ou dados para outro usuário.
- **FR-MEM-LIFE-012**: Operações concorrentes sobre a mesma associação DEVEM detectar conflito e não sobrescrever silenciosamente estado vigente.

### Limites com Features Relacionadas

- **FR-MEM-BOUND-001**: Grupos, chaves, permissões e seus modelos pertencem a `access-control`.
- **FR-MEM-BOUND-002**: Papel contextual identifica o ator e NÃO substitui a decisão de autorização.
- **FR-MEM-BOUND-003**: Seleção, troca e propagação da conta ativa pertencem a `tenant-context-isolation`.
- **FR-MEM-BOUND-004**: Limites de participantes e funcionalidades por plano são avaliados por `plans-entitlements`.
- **FR-MEM-BOUND-005**: A associação NÃO DEVE conceder acesso a módulos não liberados pelo plano da conta.

### Decisões de Infraestrutura Auditáveis

- **FR-MEM-INFRA-SCHED**: O sistema DEVE identificar ao menos a cada hora convites expirados e torná-los inutilizáveis sem depender de intervenção manual.
- **FR-MEM-INFRA-LOCK**: Emissão, reenvio, aceite, recusa, suspensão, remoção e saída DEVEM permanecer consistentes entre múltiplas instâncias.
- **FR-MEM-INFRA-IDEMP**: Repetições da mesma decisão DEVEM produzir no máximo um efeito e não recriar convite ou associação consumidos.
- **FR-MEM-INFRA-BACKUP**: Associações, convites e auditorias DEVEM participar dos backups; restaurações NÃO DEVEM reativar provas expiradas, revogadas ou consumidas.
- **FR-MEM-INFRA-KEY**: Provas persistentes de convite DEVEM permanecer protegidas durante rotação de chaves sem recuperar validade perdida.

### Key Entities

- **Membership**: vínculo contextual entre usuário e conta, com estado, papel do ator, origem, criação e encerramento.
- **Founding Membership**: associação criada atomicamente com a conta e marcada por sua origem fundadora.
- **Account Invitation**: proposta temporária enviada a um e-mail para criar uma associação após decisão da identidade correta.
- **Membership Event**: registro auditável de convite, aceite, recusa, alteração, suspensão, reativação, saída ou remoção.

## Success Criteria

### Measurable Outcomes

- **SC-MEM-001**: Em 100% dos testes, uma identidade participa de várias contas sem duplicação do usuário global.
- **SC-MEM-002**: Em 100% dos testes, convite pendente não concede acesso antes do aceite e das concessões explícitas.
- **SC-MEM-003**: Pelo menos 90% dos convidados em teste aceitam ou recusam convite válido sem ajuda em até dois minutos, excluído cadastro e entrega do e-mail.
- **SC-MEM-004**: Em 100% dos testes, prova inválida, expirada, revogada, consumida ou destinada a outro e-mail não cria associação.
- **SC-MEM-005**: Em 100% dos testes concorrentes, um convite produz no máximo uma associação ativa.
- **SC-MEM-006**: Em 100% dos testes, papel contextual isolado não concede grupo, chave ou permissão.
- **SC-MEM-007**: Em 100% dos testes, suspensão, remoção ou saída interrompe o acesso à conta afetada sem atingir outras contas.
- **SC-MEM-008**: Em 100% dos testes, participante sem chave administrativa não lista, convida, altera ou remove outros participantes.
- **SC-MEM-009**: Em 100% dos testes, nova entrada após remoção preserva o ciclo anterior e exige novo convite.
- **SC-MEM-010**: Todas as jornadas podem ser concluídas apenas por teclado e sem bloqueios críticos de acessibilidade.
- **SC-MEM-011**: Em 100% dos testes, nenhuma operação comum deixa a conta sem participante ativo com chaves administrativas mínimas e 2FA compatível.
