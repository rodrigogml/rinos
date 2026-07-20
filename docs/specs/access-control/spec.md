# Feature Specification: Controle de Acesso por Grupos e Chaves

**Feature**: `access-control`
**Created**: 2026-07-19
**Status**: Clarified — pronta para planejamento

## Escopo

Esta feature define autorização por negação padrão, baseada em chaves de acesso explícitas e agrupadas nos contextos global do sistema e específico de cada conta. Ela permite administrar grupos, associar participantes, atribuir chaves e explicar por que uma operação foi permitida ou negada.

Papéis de ator não concedem acesso. O plano limita quais funcionalidades podem existir para a conta, mas também não concede autorização ao usuário. Seleção e validação do tenant ativo pertencem a `tenant-context-isolation`; cada decisão desta feature recebe um contexto já explicitamente identificado.

## Clarifications

### Session 2026-07-19

- Q: Como resolver conflito entre concessões e negações? -> A: O modelo inicial será aditivo, sem negações explícitas; o acesso é negado quando nenhuma concessão vigente fornece a chave exigida.
- Q: Usuários podem receber chaves diretamente além de participar de grupos? -> A: Sim; o acesso efetivo combina de forma aditiva chaves diretas e chaves obtidas por grupos, mantendo cada origem explicitamente auditável.

### Session 2026-07-20

- Q: Grupos de acesso podem ser aninhados e como as chaves serão organizadas para navegação? -> A: Grupos de acesso não serão aninhados; o catálogo de chaves terá categorias hierárquicas próprias para organização e UI, sem herança ou efeito na autorização.

## User Scenarios & Testing

### User Story 1 - Autorizar uma operação (Priority: P1)

O sistema avalia identidade, associação vigente, contexto e chave exigida antes de executar qualquer operação protegida, negando quando faltar uma condição.

**Why this priority**: é o núcleo de segurança do sistema e bloqueia acesso implícito por papel, interface ou conhecimento de endereço.

**Independent Test**: executar a mesma operação com e sem a chave exigida e comprovar concessão apenas no contexto correto.

**Acceptance Scenarios**:

1. **Given** participante ativo com chave vigente na conta correta, **When** solicita operação protegida, **Then** a operação é autorizada
2. **Given** participante sem a chave, **When** solicita a mesma operação, **Then** o acesso é negado
3. **Given** chave equivalente em outra conta, **When** solicita a operação no tenant atual, **Then** o acesso é negado
4. **Given** ator identificado como administrador sem concessão, **When** solicita operação administrativa, **Then** o acesso é negado

---

### User Story 2 - Administrar grupos da conta (Priority: P1)

Um participante com chaves administrativas cria grupos da conta, escolhe chaves conhecidas do sistema e associa participantes, aplicando menor privilégio.

**Why this priority**: torna o modelo utilizável sem alterar código ou confundir papel com permissão.

**Independent Test**: criar grupo no tenant, atribuir uma chave e um participante e verificar que o efeito não alcança outra conta.

**Acceptance Scenarios**:

1. **Given** administrador autorizado, **When** cria grupo válido e atribui chaves disponíveis, **Then** o grupo fica utilizável somente naquela conta
2. **Given** tentativa de atribuir chave indisponível ao plano ou escopo, **When** salva o grupo, **Then** a alteração é rejeitada
3. **Given** participante removido do grupo, **When** tenta nova operação dependente dele, **Then** a autorização é revogada imediatamente

---

### User Story 3 - Administrar acessos globais (Priority: P2)

Um administrador do sistema com concessões suficientes mantém grupos e atribuições globais sem herdar automaticamente acesso aos dados de contas.

**Why this priority**: separa administração da plataforma da administração dos tenants e evita superusuário implícito.

**Independent Test**: conceder chave global a um usuário e comprovar que ela autoriza operação do sistema, mas não leitura de dados de conta sem concessão contextual própria.

**Acceptance Scenarios**:

1. **Given** usuário com chave global, **When** executa operação global correspondente, **Then** a operação é autorizada
2. **Given** o mesmo usuário sem chave em uma conta, **When** tenta acessar seus dados, **Then** o acesso é negado
3. **Given** usuário apenas identificado como administrador do sistema, **When** não possui chave global exigida, **Then** a operação é negada

---

### User Story 4 - Auditar e explicar acessos (Priority: P2)

Um administrador autorizado consulta concessões e alterações para entender de quais grupos ou atribuições surgiu um acesso, sem expor dados de outros tenants.

**Why this priority**: reduz erros administrativos e permite investigação e conformidade.

**Independent Test**: consultar a explicação de uma chave efetiva e verificar origem, escopo, vigência e histórico de alteração.

**Acceptance Scenarios**:

1. **Given** acesso concedido por grupo, **When** administrador consulta a explicação, **Then** identifica grupo, chave, escopo e vigência
2. **Given** acesso negado, **When** consulta autorizada é realizada, **Then** a ausência de concessão é explicada sem revelar outro tenant
3. **Given** mudança sensível, **When** auditoria é consultada, **Then** apresenta autor, contexto, instante e efeito

### Edge Cases

- A concessão é removida durante uma sessão ativa.
- O mesmo usuário pertence a vários grupos com chaves sobrepostas.
- Uma chave é descontinuada enquanto permanece em grupos.
- O plano da conta deixa de liberar a funcionalidade associada à chave.
- Duas alterações concorrentes modificam o mesmo grupo.
- Um administrador tenta conceder a si próprio poder superior ao que pode administrar.
- A última concessão administrativa mínima da conta é removida.
- Uma conta é suspensa com concessões vigentes.
- Uma restauração contém concessão revogada ou expirada.

## Requirements

### Chaves de Acesso

- **FR-ACL-KEY-001**: Toda operação protegida DEVE declarar uma chave de acesso estável e um escopo global ou de conta.
- **FR-ACL-KEY-002**: Chaves DEVEM ser registradas pelo sistema ou módulo responsável com código único, descrição, escopo e estado.
- **FR-ACL-KEY-003**: Administradores de contas NÃO DEVEM criar códigos arbitrários de chave; somente selecionar chaves registradas e disponíveis.
- **FR-ACL-KEY-004**: Chave global NÃO DEVE ser satisfeita por concessão de conta, e chave de conta NÃO DEVE valer em outro tenant.
- **FR-ACL-KEY-005**: Chave desativada ou removida do catálogo NÃO DEVE autorizar novas operações.
- **FR-ACL-KEY-006**: Alteração incompatível no significado de uma chave DEVE exigir novo código, preservando o contrato anterior para auditoria.
- **FR-ACL-KEY-007**: Chaves relacionadas a funcionalidade de plano NÃO DEVEM autorizar uso quando a funcionalidade não estiver liberada para a conta.
- **FR-ACL-KEY-008**: A existência da funcionalidade no plano NÃO DEVE dispensar a chave exigida ao usuário.
- **FR-ACL-KEY-009**: O catálogo DEVE organizar as chaves em categorias hierárquicas próprias, distintas dos grupos de acesso.
- **FR-ACL-KEY-010**: Cada categoria de chaves DEVE possuir identificador estável, nome, descrição, ordem de apresentação, estado e categoria pai opcional.
- **FR-ACL-KEY-011**: Uma categoria DEVE poder conter subcategorias e chaves, formando uma árvore navegável sem ciclos.
- **FR-ACL-KEY-012**: Cada chave DEVE possuir uma categoria canônica no catálogo; busca e filtros poderão apresentá-la em outros resultados sem alterar essa classificação.
- **FR-ACL-KEY-013**: A posição de uma chave na hierarquia NÃO DEVE conceder acesso, criar herança entre chaves, ampliar escopo ou participar da decisão de autorização.
- **FR-ACL-KEY-014**: Renomear ou mover categoria NÃO DEVE alterar códigos de chaves, grupos, concessões existentes ou resultados de autorização.
- **FR-ACL-KEY-015**: A interface de gestão DEVE permitir navegar, expandir, recolher, pesquisar e filtrar categorias e chaves disponíveis no contexto administrado.

### Grupos

- **FR-ACL-GRP-001**: Um grupo DEVE possuir nome, descrição, escopo, estado e conjunto explícito de chaves.
- **FR-ACL-GRP-002**: Grupo de conta DEVE pertencer a exatamente um tenant e não poderá conter associações de outro tenant.
- **FR-ACL-GRP-003**: Grupo global DEVE aceitar somente identidades globais e chaves de escopo global.
- **FR-ACL-GRP-004**: Grupos de acesso NÃO DEVEM conter outros grupos. Cada grupo DEVE relacionar diretamente seus usuários e suas chaves.
- **FR-ACL-GRP-005**: Nomes de grupos DEVEM ser únicos dentro do mesmo escopo e contexto, desconsiderando capitalização e espaços externos.
- **FR-ACL-GRP-006**: O sistema DEVE oferecer grupos protegidos necessários ao bootstrap e poderá oferecer modelos iniciais, sem associá-los automaticamente por papel.
- **FR-ACL-GRP-007**: Grupo protegido NÃO DEVE ser excluído ou alterado de modo que viole continuidade administrativa.
- **FR-ACL-GRP-008**: Grupo personalizado DEVE poder ser criado, renomeado, desativado e excluído por usuário com chaves administrativas suficientes.
- **FR-ACL-GRP-009**: Excluir grupo DEVE remover suas concessões futuras sem apagar o histórico.
- **FR-ACL-GRP-010**: Chaves sobrepostas em vários grupos DEVEM resultar em uma única capacidade efetiva explicável por todas as origens vigentes.

### Concessões

- **FR-ACL-GRANT-001**: Usuários DEVEM poder receber chaves diretamente e por participação em grupos; a capacidade efetiva DEVE ser a união aditiva de todas as origens vigentes e compatíveis com o contexto.
- **FR-ACL-GRANT-001A**: Concessão direta DEVE ser identificada destacadamente na administração, explicação e auditoria do acesso, sem aparentar origem em grupo.
- **FR-ACL-GRANT-002**: Toda concessão DEVE registrar beneficiário, chave ou grupo, escopo, contexto, concedente, criação, vigência e estado.
- **FR-ACL-GRANT-003**: Concessões DEVEM poder possuir início e término opcionais e deixar de produzir efeito automaticamente fora da vigência.
- **FR-ACL-GRANT-004**: Somente usuário com chave explícita para administrar o recurso DEVE criar, alterar ou revogar concessão.
- **FR-ACL-GRANT-005**: Um administrador NÃO DEVE conceder chave fora do escopo que administra nem contornar limites do plano.
- **FR-ACL-GRANT-006**: O sistema DEVE impedir alterações que deixem a conta sem participante ativo com chaves administrativas mínimas e 2FA compatível.
- **FR-ACL-GRANT-007**: Suspensão ou encerramento da associação DEVE tornar ineficazes todas as concessões daquela conta sem afetar outras contas.
- **FR-ACL-GRANT-008**: Bloqueio, desativação ou cancelamento do usuário DEVE tornar ineficazes suas concessões em todos os contextos.
- **FR-ACL-GRANT-009**: Revogação DEVE afetar novas decisões imediatamente em todas as instâncias.
- **FR-ACL-GRANT-010**: Reativação de associação ou usuário NÃO DEVE restaurar automaticamente concessões encerradas definitivamente.

### Decisão de Autorização

- **FR-ACL-AUTHZ-001**: Toda decisão DEVE negar acesso por padrão.
- **FR-ACL-AUTHZ-002**: Autorização DEVE exigir simultaneamente identidade ativa, sessão válida, contexto compatível, associação ativa quando aplicável, chave vigente e funcionalidade liberada quando aplicável.
- **FR-ACL-AUTHZ-003**: Papel de ator, título, rota visível ou conhecimento de identificador NÃO DEVE conceder acesso.
- **FR-ACL-AUTHZ-004**: O modelo inicial DEVE ser aditivo e NÃO DEVE possuir negações explícitas. A operação DEVE ser negada quando nenhuma concessão vigente fornecer a chave exigida.
- **FR-ACL-AUTHZ-004A**: A remoção de todas as origens vigentes de uma chave DEVE retirar a capacidade correspondente sem depender de uma regra de negação adicional.
- **FR-ACL-AUTHZ-005**: Concessões de vários grupos DEVEM ser combinadas de modo determinístico e explicável.
- **FR-ACL-AUTHZ-006**: Decisão global DEVE usar contexto de sistema explícito e não reutilizar tenant da sessão.
- **FR-ACL-AUTHZ-007**: Decisão de conta DEVE exigir tenant explícito e nunca inferi-lo apenas do usuário ou de uma concessão existente.
- **FR-ACL-AUTHZ-008**: Falha ao determinar contexto, estado ou concessão DEVE resultar em negação segura.
- **FR-ACL-AUTHZ-009**: Operações administrativas sensíveis DEVEM exigir 2FA compatível e autenticação recente além da chave.
- **FR-ACL-AUTHZ-010**: Interface e serviços DEVEM aplicar o mesmo contrato de autorização; ocultar uma ação na interface NÃO substitui a verificação da operação.
- **FR-ACL-AUTHZ-011**: Decisões concorrentes com revogação DEVEM favorecer o estado vigente e impedir uso posterior da concessão revogada.

### Administração e Auditoria

- **FR-ACL-ADM-001**: Administração global e administração de conta DEVEM utilizar chaves distintas e escopos incompatíveis.
- **FR-ACL-ADM-002**: Administrador do sistema NÃO DEVE possuir acesso automático aos dados de contas.
- **FR-ACL-ADM-003**: Alteração de grupo, chave, associação ou concessão DEVE registrar autor, beneficiário, contexto, instante, valores anteriores e novos e resultado.
- **FR-ACL-ADM-004**: O sistema DEVE permitir explicar as origens vigentes de uma chave efetiva para usuário autorizado a consultar acessos.
- **FR-ACL-ADM-005**: Explicações NÃO DEVEM revelar grupos, participantes ou concessões de outro tenant.
- **FR-ACL-ADM-006**: Tentativas negadas de operações sensíveis DEVEM ser auditadas com motivo seguro, sem expor a estrutura completa de autorização ao solicitante.
- **FR-ACL-ADM-007**: Mudanças em massa DEVEM apresentar impacto, exigir confirmação reforçada e produzir resultado individual rastreável.
- **FR-ACL-ADM-008**: O próprio administrador NÃO DEVE aprovar alteração que exija separação futura de responsabilidades; regras específicas serão definidas pelos módulos que necessitarem.

### Decisões de Infraestrutura Auditáveis

- **FR-ACL-INFRA-SCHED**: O sistema DEVE desativar automaticamente concessões temporárias vencidas ao menos a cada minuto e também rejeitá-las no momento da decisão.
- **FR-ACL-INFRA-LOCK**: Alterações concorrentes de grupos e concessões DEVEM permanecer consistentes entre múltiplas instâncias.
- **FR-ACL-INFRA-IDEMP**: Repetições da mesma concessão ou revogação DEVEM produzir no máximo um efeito.
- **FR-ACL-INFRA-BACKUP**: Catálogo de chaves, grupos, concessões e auditorias DEVEM participar dos backups; restauração NÃO DEVE reativar concessões expiradas ou revogadas.
- **FR-ACL-INFRA-CACHE**: Qualquer cópia temporária usada na decisão DEVE ser invalidada ou tornada obsoleta imediatamente após mudança sensível, sem ampliar acesso durante falha.

### Key Entities

- **Access Key**: capacidade estável registrada pelo sistema, com código, descrição, escopo e estado.
- **Access Key Category**: nó hierárquico do catálogo usado exclusivamente para organizar e apresentar chaves, sem significado autorizativo.
- **Access Group**: conjunto contextual de chaves utilizado para administrar concessões de forma compreensível.
- **Group Membership**: vínculo entre usuário ou associação e grupo no contexto permitido.
- **Access Grant**: atribuição vigente, opcionalmente temporária, que contribui para a capacidade efetiva.
- **Authorization Decision**: resultado permitido ou negado para identidade, chave e contexto, com razões seguras e rastreáveis.
- **Access Audit Event**: histórico imutável de mudança ou tentativa sensível relacionada à autorização.

## Success Criteria

### Measurable Outcomes

- **SC-ACL-001**: Em 100% dos testes, ausência de chave vigente resulta em negação.
- **SC-ACL-002**: Em 100% dos testes cruzados, chave concedida em uma conta não autoriza operação em outra.
- **SC-ACL-003**: Em 100% dos testes, papel de colaborador, parceiro, administrador da conta ou administrador do sistema isoladamente não concede acesso.
- **SC-ACL-004**: Em 100% dos testes, chave global não concede acesso automático a dados de tenant.
- **SC-ACL-005**: Em 100% dos testes, funcionalidade indisponível no plano não é liberada apenas pela chave.
- **SC-ACL-006**: Em 100% dos testes, revogação, suspensão ou expiração impede nova operação protegida imediatamente.
- **SC-ACL-007**: Em 100% dos testes concorrentes, concessão ou revogação repetida produz no máximo um efeito.
- **SC-ACL-008**: Em 100% dos testes, nenhuma alteração comum deixa a conta sem participante apto à administração mínima.
- **SC-ACL-009**: Usuário autorizado identifica em até 30 segundos os grupos e concessões que originam uma chave efetiva.
- **SC-ACL-010**: Todas as jornadas administrativas podem ser concluídas apenas por teclado e sem bloqueios críticos de acessibilidade.
- **SC-ACL-011**: Em 100% dos testes, mover, renomear ou reorganizar categorias de chaves não altera qualquer resultado de autorização ou concessão vigente.
