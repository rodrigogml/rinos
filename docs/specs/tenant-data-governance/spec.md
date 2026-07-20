# Feature Specification: Governança de Dados de Tenant

**Feature**: `tenant-data-governance`
**Created**: 2026-07-20
**Status**: Clarified — pronta para planejamento

## Escopo

Esta feature estabelece as regras transversais que preservam integridade, rastreabilidade, recuperabilidade e destinação adequada dos dados globais e dos dados pertencentes a cada tenant. Ela define contratos que todos os módulos deverão cumprir para classificar informações, criar relacionamentos, registrar auditoria, participar de backups, restaurar estados consistentes, aplicar retenções e impedimentos, eliminar ou anonimizar dados e produzir cópias portáveis autorizadas.

Também inclui a transferência operacional de um armazenamento de tenant entre localizações físicas sem alterar sua identidade funcional. Não inclui o conteúdo funcional de módulos, a classificação da conta como pessoa física ou jurídica, parecer jurídico sobre prazos legais específicos, atendimento completo de incidentes de segurança, descoberta eletrônica, faturamento de serviços de backup, nem a escolha de ferramentas ou meios físicos de armazenamento.

> [!IMPORTANT]
> Prazos legais e fiscais variam conforme categoria, finalidade, localidade e módulo. Esta feature exige um catálogo explícito e versionado de retenção; não presume que um único prazo seja válido para todos os dados.

## Clarifications

### Session 2026-07-20

- Q: Quais objetivos iniciais de recuperação devem orientar a produção? -> A: A implantação de referência utilizará backup diário, perda máxima planejada de até 24 horas (RPO) e retorno operacional planejado em até 8 horas (RTO). Esses valores são compromissos operacionais; a aplicação deve permitir configurá-los, observá-los, alertar descumprimentos e validar restaurações.
- Q: Como global e tenants devem compor o backup diário? -> A: Cada ciclo diário produz um conjunto coordenado de recuperação, formado pelo componente global e por um componente de cada tenant elegível. O conjunto permite restaurar toda a instalação ou selecionar um tenant individual com sua referência global compatível.
- Q: Qual janela operacional deve ser aplicada após o cancelamento de uma conta? -> A: Noventa dias por padrão, com prazo configurável por instalação. A conta permanece cancelada e indisponível para operações comuns; a janela preserva os dados somente para exportação, investigação ou recuperação excepcional autorizada antes da destinação por categoria.
- Q: Por quanto tempo os conjuntos diários de recuperação devem ser mantidos? -> A: A implantação de referência mantém os 30 conjuntos diários mais recentes, com quantidade configurável por instalação; a estratégia física de cópias completas ou incrementais será definida no planejamento.
- Q: Quais exportações devem existir na primeira versão? -> A: O próprio usuário poderá exportar seus dados globais pessoais, e um participante com chaves específicas poderá exportar os dados da conta. Transferência direta para outro fornecedor fica fora do escopo inicial.

## User Scenarios & Testing

### User Story 1 - Preservar a integridade entre dados globais e de tenant (Priority: P1)

Um responsável por um módulo define onde cada dado pertence e quais vínculos podem existir, garantindo que dados globais nunca dependam fisicamente de um tenant e que a remoção de um registro global não fique bloqueada por referências desconhecidas espalhadas entre contas.

**Why this priority**: a direção incorreta de uma dependência pode romper o isolamento, impedir manutenção global ou produzir dados órfãos em toda a instalação.

**Independent Test**: validar um conjunto de entidades globais e de tenant, remover referências globais opcionais e dependentes e comprovar que a integridade segue a política declarada sem consulta cruzada ou bloqueio originado pelo tenant.

**Acceptance Scenarios**:

1. **Given** dado pertencente a um tenant, **When** é criado ou alterado, **Then** permanece no armazenamento do tenant explicitamente validado
2. **Given** registro global, **When** seu modelo é validado, **Then** não possui dependência nem referência física para armazenamento de tenant
3. **Given** vínculo opcional de tenant para global, **When** o registro global deixa de existir, **Then** o dado do tenant sobrevive com o vínculo removido
4. **Given** dado de tenant integralmente dependente de um registro global, **When** o registro global é removido, **Then** o dependente é removido de forma controlada e auditável
5. **Given** tentativa de criar vínculo global para tenant ou regra que bloqueie a exclusão global por dependência de tenant, **When** o contrato é validado, **Then** a alteração é rejeitada antes de operar em produção

---

### User Story 2 - Consultar uma trilha de auditoria confiável (Priority: P1)

Um usuário ou administrador autorizado investiga uma alteração sensível e identifica quem ou qual processo agiu, em qual contexto, quando, a partir de qual origem validada e qual efeito foi produzido, sem expor segredos ou dados pessoais desnecessários.

**Why this priority**: operações administrativas e financeiras precisam ser explicáveis, e uma trilha incompleta ou mutável compromete segurança, suporte e conformidade.

**Independent Test**: executar operações permitidas e negadas em contextos global e de tenant, consultar a auditoria com concessões adequadas e confirmar conteúdo, isolamento, imutabilidade lógica e proteção de campos sensíveis.

**Acceptance Scenarios**:

1. **Given** alteração sensível concluída, **When** a auditoria é consultada, **Then** apresenta ator ou processo, contexto, ação, instante, origem, alvo e resultado
2. **Given** tentativa negada ou falha relevante, **When** ocorre, **Then** existe evento suficiente para investigação sem registrar credenciais ou provas reutilizáveis
3. **Given** auditor de uma conta, **When** pesquisa eventos, **Then** recebe somente eventos autorizados daquele tenant
4. **Given** administrador do sistema sem acesso concedido ao tenant, **When** consulta a visão global, **Then** visualiza apenas metadados operacionais globais permitidos e não o conteúdo funcional da conta
5. **Given** tentativa de editar ou remover evento de auditoria preservado, **When** é processada, **Then** a alteração comum é impedida e a tentativa também é registrada

---

### User Story 3 - Recuperar dados após falha (Priority: P1)

Um operador autorizado acompanha a proteção dos dados globais e de cada tenant, seleciona um ponto de recuperação válido, executa restauração controlada e somente libera o sistema ou a conta após comprovar identidade, integridade, versão e coerência temporal.

**Why this priority**: dados financeiros e administrativos não podem depender de cópias nunca testadas nem retornar à operação em estado incompatível.

**Independent Test**: gerar pontos de recuperação, simular perda global e de um tenant, restaurar cada escopo e confirmar que nenhuma outra conta é sobrescrita, que artefatos temporários inválidos não são reativados e que o armazenamento só é liberado após validação.

**Acceptance Scenarios**:

1. **Given** dados globais e de tenant elegíveis, **When** a proteção programada ocorre, **Then** todos os conjuntos obrigatórios participam de um ponto de recuperação rastreável
2. **Given** falha restrita a um tenant, **When** sua restauração é autorizada, **Then** os demais tenants continuam inalterados e disponíveis
3. **Given** restauração global necessária, **When** é executada, **Then** a aplicação permanece operacionalmente indisponível até validar a coerência global e as referências dos tenants
4. **Given** ponto de recuperação inválido, incompleto, incompatível ou de outro tenant, **When** é selecionado, **Then** a restauração é impedida antes de substituir dados utilizáveis
5. **Given** restauração concluída tecnicamente, **When** sessões, provas, convites e operações posteriores ao ponto são reconciliados, **Then** nenhum artefato expirado, consumido ou revogado volta a ser válido
6. **Given** rotina periódica de teste de restauração, **When** termina, **Then** seu tempo, escopo, validações e resultado ficam registrados e consultáveis
7. **Given** implantação de referência em produção, **When** a proteção e a recuperação são avaliadas, **Then** existe ponto válido com no máximo 24 horas e o procedimento testado prevê retorno operacional em até 8 horas
8. **Given** ciclo diário concluído, **When** o inventário de recuperação é consultado, **Then** apresenta um conjunto coordenado com o global e cada tenant elegível, permitindo selecionar a instalação completa ou um tenant individual
9. **Given** implantação de referência com ciclos diários sucessivos, **When** a retenção é aplicada, **Then** os 30 conjuntos mais recentes permanecem elegíveis e conjuntos excedentes recebem destinação segura quando não houver impedimento

---

### User Story 4 - Reter e destinar dados de forma controlada (Priority: P1)

Um responsável autorizado acompanha o ciclo de vida de dados ativos, cancelados ou cuja finalidade terminou, aplicando a política correspondente sem apagar registros sujeitos a obrigação, investigação ou impedimento vigente e sem conservar indefinidamente aquilo que já deve ser eliminado ou anonimizado.

**Why this priority**: cancelamento não pode causar perda prematura, mas backup e auditoria também não podem se transformar em retenção ilimitada sem finalidade.

**Independent Test**: cancelar uma conta com categorias em políticas diferentes, aplicar e remover impedimento de eliminação, avançar os prazos e comprovar a destinação correta em dados ativos, auditoria, exportações e cópias de recuperação.

**Acceptance Scenarios**:

1. **Given** categoria de dado sem política vigente, **When** um módulo tenta utilizá-la em produção, **Then** o uso é bloqueado até existir finalidade e destinação aprovadas
2. **Given** conta cancelada ainda dentro da janela de recuperação, **When** o prazo é consultado, **Then** os dados permanecem indisponíveis para uso comum e preservados para ações autorizadas
3. **Given** retenção obrigatória ou impedimento de eliminação vigente, **When** chega a data normal de descarte, **Then** somente os dados cobertos permanecem preservados com motivo e revisão rastreáveis
4. **Given** finalidade encerrada, prazo cumprido e nenhum impedimento, **When** a destinação é executada, **Then** os dados são eliminados ou anonimizados conforme a política e não podem ser reativados por restauração comum
5. **Given** cópias de recuperação ainda contêm dados já destinados no ambiente ativo, **When** essas cópias alcançam seu próprio vencimento, **Then** são eliminadas de acordo com a política e qualquer restauração intermediária reaplica as destinações já devidas
6. **Given** conta cancelada, **When** ainda não decorreram os 90 dias da janela operacional padrão, **Then** seus dados permanecem protegidos e recuperáveis somente para exportação, investigação ou recuperação excepcional autorizada, sem reativação da conta

---

### User Story 5 - Obter uma cópia autorizada dos dados (Priority: P2)

Um usuário ou administrador da conta com concessões suficientes solicita uma cópia legível e portável dos dados sob sua responsabilidade, acompanha o preparo e recebe o resultado por meio protegido, sem incluir segredos, dados indevidos de outros participantes ou conteúdo de outro tenant.

**Why this priority**: transparência e portabilidade reduzem aprisionamento de dados e apoiam solicitações legítimas dos titulares e responsáveis pela conta.

**Independent Test**: solicitar exportações da identidade e de uma conta com atores distintos, verificar autorização, escopo, isolamento, conteúdo, expiração e auditoria do acesso ao resultado.

**Acceptance Scenarios**:

1. **Given** solicitação autenticada e escopo autorizado, **When** a cópia é preparada, **Then** contém os dados abrangidos e informações suficientes para interpretação
2. **Given** solicitante sem concessão sobre a conta ou categoria, **When** pede exportação, **Then** a solicitação é negada sem revelar a existência de dados protegidos
3. **Given** conta com vários participantes, **When** a exportação é produzida, **Then** dados pessoais de terceiros são limitados ao necessário para representar os registros legitimamente pertencentes à conta
4. **Given** resultado pronto, **When** é disponibilizado, **Then** o acesso exige autenticação recente, expira e não expõe localização física permanente
5. **Given** solicitação repetida, cancelada ou falha, **When** é processada, **Then** não produz cópias ilimitadas e preserva histórico do resultado
6. **Given** usuário autenticado e revalidado, **When** solicita seus dados globais pessoais, **Then** acompanha e obtém a própria exportação sem depender de administrador do sistema
7. **Given** participante com chaves específicas no tenant, **When** solicita os dados da conta, **Then** acompanha e obtém a exportação no escopo autorizado sem receber dados de outra conta

---

### User Story 6 - Transferir o armazenamento sem trocar o tenant (Priority: P2)

Um operador autorizado move um tenant entre localizações físicas para manutenção, capacidade ou evolução da infraestrutura, mantendo a mesma identidade funcional, impedindo escritas divergentes e liberando a conta somente após validação completa.

**Why this priority**: a topologia física precisa evoluir sem obrigar mudanças nos módulos ou criar uma nova conta para os mesmos dados.

**Independent Test**: transferir um tenant para outra localização, provocar falha antes e depois da mudança de autoridade e verificar identidade estável, cópia consistente, ausência de escrita dividida, retomada segura e descarte posterior controlado da origem.

**Acceptance Scenarios**:

1. **Given** tenant elegível e destino preparado, **When** a transferência começa, **Then** operações de escrita incompatíveis são bloqueadas durante a janela necessária
2. **Given** cópia concluída e validada, **When** o destino assume autoridade, **Then** o tenant mantém a mesma identidade e nenhuma interface recebe sua localização física
3. **Given** falha antes da troca de autoridade, **When** o processo é retomado ou cancelado, **Then** a origem permanece autoritativa e o destino parcial não aceita operações
4. **Given** falha após a troca de autoridade, **When** a recuperação ocorre, **Then** existe uma única localização autoritativa e não há retorno silencioso à origem desatualizada
5. **Given** transferência concluída, **When** termina o prazo de segurança e não há impedimento, **Then** a cópia anterior recebe destinação auditada sem reutilização da identidade física

### Edge Cases

- O cadastro global é restaurado para um instante diferente do armazenamento de um tenant.
- Um registro global referenciado por muitos tenants precisa ser eliminado.
- Um tenant contém referência opcional para dado global já anonimizado ou removido.
- Uma operação sensível conclui, mas a persistência de seu evento de auditoria falha.
- O relógio ou fuso apresentado ao usuário difere da referência temporal da auditoria.
- Um backup termina parcialmente, perde sua evidência de integridade ou não pode ser lido.
- A chave necessária para recuperar uma cópia protegida foi rotacionada ou está indisponível.
- Uma restauração reintroduz solicitações expiradas, permissões revogadas ou conta cancelada.
- Uma ordem de preservação chega enquanto a eliminação está em execução.
- Uma política de retenção muda com dados já existentes e prazos em andamento.
- O mesmo dado está sujeito simultaneamente a categorias e prazos diferentes.
- Uma exportação fica pronta depois que a concessão do solicitante foi revogada.
- A exportação contém anexos grandes, dados corrompidos ou referências que já não existem.
- Uma transferência é interrompida exatamente durante a mudança da localização autoritativa.
- A localização de origem reaparece depois de o destino já receber novas escritas.

## Requirements

### Classificação, Titularidade e Finalidade

- **FR-TDG-CLASS-001**: Toda categoria persistente DEVE declarar se pertence ao contexto global, à identidade de um usuário ou a exatamente um tenant.
- **FR-TDG-CLASS-002**: Todo dado de tenant DEVE permanecer associado à identidade imutável de uma única conta durante todo seu ciclo de vida.
- **FR-TDG-CLASS-003**: Cada categoria DEVE declarar finalidade, público autorizado, sensibilidade, origem, política de retenção e destinação antes de ser utilizada em produção.
- **FR-TDG-CLASS-004**: Coleta e conservação DEVEM limitar-se aos dados necessários às finalidades declaradas e vigentes.
- **FR-TDG-CLASS-005**: Alteração material de finalidade ou classificação DEVE ser versionada, revisada e aplicada sem reclassificar silenciosamente registros históricos.
- **FR-TDG-CLASS-006**: Segredos, provas reutilizáveis e dados financeiros ou pessoais sensíveis DEVEM possuir classificação explícita e controles proporcionais ao risco.
- **FR-TDG-CLASS-007**: Cópias, índices, caches, anexos, eventos e exportações DEVEM herdar ou reforçar a classificação e o tenant dos dados de origem.
- **FR-TDG-CLASS-008**: Dados derivados somente poderão perder o vínculo de proteção quando a anonimização for comprovadamente irreversível para os meios razoavelmente disponíveis.

### Integridade Global e de Tenant

- **FR-TDG-INT-001**: Dados globais NÃO DEVEM possuir referência física, dependência de existência ou regra de integridade direcionada a dados armazenados em tenant.
- **FR-TDG-INT-002**: Dados de tenant PODEM referenciar dados globais somente quando o vínculo possuir finalidade explícita e não permitir acesso a outro tenant.
- **FR-TDG-INT-003**: Referência opcional de tenant para global DEVE remover o vínculo quando o registro global for eliminado, preservando o filho que continua válido (`ON DELETE SET NULL`).
- **FR-TDG-INT-004**: Referência de tenant para global em que o filho não possa existir sem o pai DEVE eliminar o filho dependente de forma controlada (`ON DELETE CASCADE`).
- **FR-TDG-INT-005**: Referência de tenant para global NÃO DEVE impedir a alteração ou eliminação global por restrição originada no tenant; `ON DELETE RESTRICT` e `ON DELETE NO ACTION` são proibidos nessa direção.
- **FR-TDG-INT-006**: A eliminação em cascata DEVE ser limitada aos dependentes integrais declarados e produzir impacto previamente avaliável e auditável.
- **FR-TDG-INT-007**: Integridade lógica entre o cadastro global e um tenant DEVE ser verificável sem permitir consulta funcional entre tenants.
- **FR-TDG-INT-008**: Divergência de identidade, versão, vínculo ou localização DEVE colocar somente o escopo afetado em condição segura até reconciliação autorizada.
- **FR-TDG-INT-009**: Identificadores funcionais e físicos eliminados NÃO DEVEM ser reutilizados para outra identidade.
- **FR-TDG-INT-010**: Mudanças de integridade estrutural DEVEM ser progressivas, rastreáveis e compatíveis com as regras de migração de `tenant-storage-provisioning`.

### Auditoria

- **FR-TDG-AUD-001**: Cada feature DEVE declarar quais sucessos, negações, falhas e mudanças de estado são auditáveis conforme seu risco.
- **FR-TDG-AUD-002**: Evento de auditoria DEVE identificar ator ou processo, contexto global ou tenant, ação, alvo seguro, instante, origem validada, resultado e correlação da operação.
- **FR-TDG-AUD-003**: Evento originado por representação, suporte ou automação DEVE distinguir solicitante, executor efetivo e motivo aplicável.
- **FR-TDG-AUD-004**: Auditoria NÃO DEVE armazenar senha, segredo, chave privada, código, token, conteúdo integral de prova nem dado pessoal ou financeiro além do necessário à investigação.
- **FR-TDG-AUD-005**: Valores anteriores e posteriores DEVEM ser registrados somente quando necessários, com proteção ou redução adequada à classificação do campo.
- **FR-TDG-AUD-006**: Eventos preservados NÃO DEVEM ser alteráveis ou removíveis por operações comuns, inclusive pelo ator que originou a ação.
- **FR-TDG-AUD-007**: Falha ao registrar auditoria obrigatória de uma alteração crítica DEVE impedir que a alteração seja declarada concluída ou produzir evidência alternativa durável e reconciliável.
- **FR-TDG-AUD-008**: Consulta de auditoria DEVE exigir chaves explícitas no mesmo contexto dos eventos e negar acesso por papel isolado.
- **FR-TDG-AUD-009**: Visão global NÃO DEVE revelar conteúdo funcional de tenant sem concessão específica sobre a conta; poderá apresentar somente metadados operacionais necessários e protegidos.
- **FR-TDG-AUD-010**: Consulta, exportação, tentativa de alteração e destinação da própria auditoria DEVEM ser auditadas.
- **FR-TDG-AUD-011**: Eventos DEVEM usar referência temporal uniforme, preservando também o fuso ou origem necessários para apresentação e investigação.
- **FR-TDG-AUD-012**: Auditoria DEVE permanecer pesquisável por contexto, período, ação, ator ou processo, resultado e correlação, respeitando limites de acesso.

### Proteção e Pontos de Recuperação

- **FR-TDG-BKP-001**: Dados globais, registros de tenants, armazenamentos de tenant, anexos, configurações, concessões e auditorias DEVEM participar da política de proteção aplicável.
- **FR-TDG-BKP-002**: Cada ambiente operacional DEVE declarar objetivos máximos de perda de dados e de tempo de recuperação antes de receber uso de produção; a implantação inicial de referência DEVE adotar RPO de 24 horas e RTO de 8 horas.
- **FR-TDG-BKP-003**: A implantação inicial de referência DEVE gerar proteção ao menos diariamente; frequência, abrangência e retenção dos pontos DEVEM ser suficientes para cumprir os objetivos declarados e permanecer observáveis.
- **FR-TDG-BKP-004**: Cada ponto DEVE possuir identidade, escopo, instante de referência, estado, versão estrutural e evidência verificável de integridade.
- **FR-TDG-BKP-005**: Falha parcial NÃO DEVE apresentar um conjunto como recuperável e DEVE gerar alerta rastreável.
- **FR-TDG-BKP-006**: Dados protegidos DEVEM permanecer confidenciais e íntegros durante criação, transporte, conservação, teste e descarte.
- **FR-TDG-BKP-007**: Acesso a cópias e operações de proteção DEVE exigir contexto de sistema e chaves operacionais explícitas, sem conceder acesso funcional automático aos tenants contidos.
- **FR-TDG-BKP-008**: As cópias DEVEM permanecer separadas do estado operacional o suficiente para que falha, corrupção ou exclusão comum não destrua simultaneamente todos os pontos válidos.
- **FR-TDG-BKP-009**: A disponibilidade das proteções DEVE ser monitorada, e ausência de ponto válido além do objetivo declarado DEVE gerar alerta prioritário.
- **FR-TDG-BKP-010**: Ponto vencido DEVE receber destinação segura e auditável, salvo impedimento vigente e explicitamente registrado.
- **FR-TDG-BKP-011**: Rotação de mecanismos de proteção NÃO DEVE tornar cópias ainda vigentes irrecuperáveis nem manter indefinidamente material já vencido.
- **FR-TDG-BKP-012**: Cada ciclo diário da implantação de referência DEVE produzir um conjunto coordenado de recuperação identificado de forma única.
- **FR-TDG-BKP-013**: O conjunto coordenado DEVE relacionar exatamente um componente global e um componente para cada tenant elegível no início do ciclo, sem combinar componentes de ciclos distintos como se fossem um único ponto.
- **FR-TDG-BKP-014**: O conjunto DEVE registrar o instante de referência, versão, integridade e resultado de cada componente, inclusive tenants criados, cancelados, indisponíveis ou falhos durante o ciclo.
- **FR-TDG-BKP-015**: O conjunto somente DEVE ser declarado apto à recuperação completa quando todos os componentes obrigatórios estiverem íntegros; falha individual DEVE permanecer visível e não poderá ser ocultada pelo sucesso dos demais.
- **FR-TDG-BKP-016**: Componente individual de tenant somente DEVE ser elegível à restauração quando acompanhado da referência global do mesmo conjunto e quando sua compatibilidade puder ser validada sem substituir indevidamente o estado global vigente.
- **FR-TDG-BKP-017**: A quantidade de conjuntos diários mantidos DEVE ser configurável por instalação e adotar 30 conjuntos mais recentes na implantação de referência.
- **FR-TDG-BKP-018**: A retenção lógica de 30 conjuntos NÃO DEVE exigir uma técnica física específica; qualquer estratégia adotada DEVE preservar recuperabilidade, integridade, confidencialidade e restauração individual.
- **FR-TDG-BKP-019**: Conjunto que exceder a quantidade configurada DEVE receber destinação segura e auditada quando não estiver abrangido por impedimento vigente.

### Restauração e Continuidade

- **FR-TDG-RST-001**: Restauração DEVE exigir solicitação identificada, escopo exato, justificativa, autorização explícita, autenticação recente e segundo fator compatível com a sensibilidade.
- **FR-TDG-RST-002**: Antes de substituir dados, o sistema DEVE validar identidade, escopo, integridade, versão, legibilidade e compatibilidade do ponto selecionado.
- **FR-TDG-RST-003**: Restauração de tenant DEVE preservar sua identidade funcional e NÃO DEVE sobrescrever, combinar ou assumir a identidade de outro tenant.
- **FR-TDG-RST-004**: Restauração global DEVE manter a aplicação operacionalmente indisponível até reconciliar cadastro, localizações, estados e referências dos tenants.
- **FR-TDG-RST-005**: Restauração individual DEVE manter somente o tenant afetado indisponível e preservar os demais quando não houver dependência global comprometida.
- **FR-TDG-RST-006**: Dados restaurados NÃO DEVEM ser liberados antes de alcançar a versão estrutural exata esperada pela aplicação.
- **FR-TDG-RST-007**: Sessões, provas, códigos, convites, exportações temporárias e demais artefatos com validade DEVEM ser reavaliados pelo estado e pelo tempo atuais e não poderão ser reativados apenas por existirem no ponto restaurado.
- **FR-TDG-RST-008**: Revogações, cancelamentos, destinações e impedimentos conhecidos fora do ponto restaurado DEVEM ser reconciliados antes da liberação para impedir reversão indevida de decisões posteriores.
- **FR-TDG-RST-009**: Restauração DEVE registrar etapas, responsáveis, ponto utilizado, validações, divergências, duração e resultado sem expor conteúdo protegido.
- **FR-TDG-RST-010**: O processo DEVE permitir validação sem substituir previamente o único estado operacional conhecido como íntegro.
- **FR-TDG-RST-011**: Cada política de proteção DEVE possuir teste periódico de restauração, com resultado e ações corretivas rastreáveis.
- **FR-TDG-RST-012**: Falha de restauração DEVE manter o escopo indisponível, preservar evidências e permitir retomada segura ou retorno explícito ao último estado autoritativo comprovado.

### Retenção, Impedimento e Destinação

- **FR-TDG-RET-001**: Cada categoria DEVE possuir política versionada com evento inicial, prazo ou condição de término, destinação e justificativa aprovada.
- **FR-TDG-RET-002**: Quando várias políticas legítimas alcançarem o mesmo dado, DEVE prevalecer a conservação necessária de maior duração, limitada ao escopo protegido por sua finalidade.
- **FR-TDG-RET-003**: Suspensão ou cancelamento de conta NÃO DEVE eliminar imediatamente dados nem auditorias sujeitos a janela de recuperação, obrigação ou impedimento.
- **FR-TDG-RET-004**: Conta cancelada DEVE permanecer indisponível para operações comuns enquanto seus dados aguardam destinação.
- **FR-TDG-RET-005**: Impedimento de eliminação DEVE possuir escopo, motivo, autoridade, início, revisão ou expiração e auditoria; não poderá ser permanente por omissão.
- **FR-TDG-RET-006**: Somente usuário ou processo com chave explícita DEVE criar, alterar, revisar ou remover impedimento.
- **FR-TDG-RET-007**: Término da finalidade e dos prazos aplicáveis DEVE produzir eliminação ou anonimização verificável, conforme a política da categoria.
- **FR-TDG-RET-008**: Anonimização DEVE remover a associação razoável com pessoas e tenants quando essa associação não for mais necessária, sem declarar anonimizado dado apenas pseudonimizado.
- **FR-TDG-RET-009**: Eliminação DEVE abranger cópias operacionais, índices, caches, anexos, exportações e derivados controlados, respeitando o ciclo próprio de cópias de recuperação ainda vigentes.
- **FR-TDG-RET-010**: Restauração de cópia antiga DEVE reaplicar destinações, revogações e impedimentos já vencidos ou vigentes antes de liberar o escopo.
- **FR-TDG-RET-011**: Mudança de política DEVE registrar sua vigência e avaliar explicitamente o tratamento de dados preexistentes sem encurtar conservação obrigatória de forma retroativa indevida.
- **FR-TDG-RET-012**: Execução de destinação DEVE ser idempotente, retomável, isolada por tenant e auditada por categoria, quantidade, período e resultado.
- **FR-TDG-RET-013**: Falha parcial de destinação NÃO DEVE ser apresentada como conclusão e DEVE permanecer reconciliável.
- **FR-TDG-RET-014**: Dados mantidos exclusivamente por obrigação ou impedimento NÃO DEVEM voltar a ser utilizados para finalidade comum incompatível.
- **FR-TDG-RET-015**: Cancelamento da conta DEVE iniciar janela operacional de preservação configurável, com prazo padrão de 90 dias contado do cancelamento confirmado.
- **FR-TDG-RET-016**: Durante a janela operacional, a conta DEVE permanecer cancelada e seus dados NÃO DEVEM aceitar operações comuns nem reativação automática ou implícita.
- **FR-TDG-RET-017**: Dados dentro da janela operacional somente DEVEM ser acessíveis para exportação, investigação ou recuperação excepcional por operação explicitamente autorizada e auditada.
- **FR-TDG-RET-018**: Enquanto a janela estiver vigente, o tenant cancelado DEVE continuar elegível aos conjuntos coordenados de recuperação; após seu término, cada categoria DEVE seguir sua política e seus impedimentos aplicáveis.

### Exportação e Portabilidade

- **FR-TDG-EXP-001**: O sistema DEVE distinguir exportação da identidade global, exportação de dados pessoais de um titular e exportação administrativa de uma conta.
- **FR-TDG-EXP-002**: Cada modalidade DEVE exigir identidade, escopo, finalidade, concessão e confirmação compatíveis com os dados solicitados.
- **FR-TDG-EXP-003**: Exportação de conta DEVE exigir chave explícita no tenant, autenticação recente e segundo fator quando contiver dados sensíveis ou de múltiplos participantes.
- **FR-TDG-EXP-004**: O resultado DEVE utilizar formato legível e estruturado, incluir descrição dos conjuntos e preservar relações necessárias à interpretação sem revelar detalhes internos de segurança.
- **FR-TDG-EXP-005**: O resultado NÃO DEVE conter senhas, segredos, chaves privadas, provas reutilizáveis, tokens, dados de outro tenant ou metadados internos que facilitem ataque.
- **FR-TDG-EXP-006**: Dados pessoais de terceiros DEVEM ser omitidos, reduzidos ou protegidos quando não forem necessários para representar registros legitimamente abrangidos.
- **FR-TDG-EXP-007**: Preparação DEVE usar o escopo autorizado no início e revalidar identidade, estado, concessões e impedimentos antes de disponibilizar o resultado.
- **FR-TDG-EXP-008**: Exportação assíncrona DEVE possuir protocolo, progresso, estado, expiração e resultado auditável.
- **FR-TDG-EXP-009**: O acesso ao resultado DEVE ser temporário, individual, revogável e protegido por autenticação recente.
- **FR-TDG-EXP-010**: Cópias vencidas, canceladas ou substituídas DEVEM ser eliminadas automaticamente e não poderão ser recuperadas por URL ou referência anterior.
- **FR-TDG-EXP-011**: Repetição da mesma solicitação NÃO DEVE produzir efeitos ou cópias ilimitadas e DEVE retornar estado consistente.
- **FR-TDG-EXP-012**: Exportação NÃO DEVE alterar, bloquear ou transferir a titularidade operacional dos dados originais.
- **FR-TDG-EXP-013**: A primeira versão DEVE oferecer ao próprio usuário exportação em autosserviço dos dados globais pessoais vinculados à sua identidade, mediante autenticação recente e revalidação no momento da entrega.
- **FR-TDG-EXP-014**: A primeira versão DEVE oferecer exportação em autosserviço dos dados da conta ao participante que possuir chaves específicas vigentes naquele tenant, sem exigir intervenção ordinária de administrador do sistema.
- **FR-TDG-EXP-015**: Transferência direta dos dados para outro fornecedor ou integração de portabilidade entre plataformas NÃO faz parte da primeira versão; o resultado portável será entregue ao solicitante autorizado.

### Transferência Operacional de Armazenamento

- **FR-TDG-MOVE-001**: Transferência física DEVE preservar a identidade funcional imutável do tenant e manter a localização interna invisível ao usuário.
- **FR-TDG-MOVE-002**: Origem, destino, motivo, escopo, ponto consistente, etapas e autoridade vigente DEVEM ser persistidos em processo durável e auditável.
- **FR-TDG-MOVE-003**: O destino DEVE ser exclusivo, compatível, íntegro e não reutilizado antes de receber autoridade sobre o tenant.
- **FR-TDG-MOVE-004**: O processo DEVE impedir escritas concorrentes que possam criar históricos divergentes entre origem e destino.
- **FR-TDG-MOVE-005**: Em qualquer instante DEVE existir no máximo uma localização autoritativa aceita para novas operações do tenant.
- **FR-TDG-MOVE-006**: A troca da localização autoritativa DEVE ser indivisível do ponto de vista de novas operações e revalidada por cada instância antes do uso.
- **FR-TDG-MOVE-007**: Falha antes da troca de autoridade DEVE preservar a origem como autoritativa e manter o destino em quarentena.
- **FR-TDG-MOVE-008**: Falha depois da troca NÃO DEVE restaurar silenciosamente autoridade à origem desatualizada.
- **FR-TDG-MOVE-009**: O tenant somente DEVE ser liberado no destino após validar identidade, versão, integridade, contagens críticas e referências globais aplicáveis.
- **FR-TDG-MOVE-010**: A origem anterior DEVE permanecer protegida durante janela de segurança e depois receber destinação auditada conforme retenção e impedimentos.
- **FR-TDG-MOVE-011**: Processo repetido ou retomado NÃO DEVE criar destinos concorrentes, duplicar autoridade nem reutilizar identidade física descartada.

### Segurança, Operação e Observabilidade

- **FR-TDG-OPS-001**: Operações de restauração, impedimento, destinação, exportação administrativa e transferência DEVEM ser negadas por padrão e exigir chaves específicas no contexto adequado.
- **FR-TDG-OPS-002**: Papel de administrador do sistema ou da conta NÃO DEVE, isoladamente, conceder acesso ao conteúdo protegido nem autoridade operacional.
- **FR-TDG-OPS-003**: Operações críticas DEVEM exigir autenticação recente, confirmação explícita e segundo fator compatível com o risco.
- **FR-TDG-OPS-004**: Credenciais operacionais DEVEM ter privilégio mínimo, finalidade restrita e não poderão ser exibidas em interface, auditoria ou diagnóstico.
- **FR-TDG-OPS-005**: Cada processo durável DEVE informar protocolo, escopo, estado, progresso conhecido, última atividade, tentativas e resultado ao público autorizado.
- **FR-TDG-OPS-006**: Atraso, ausência de proteção, falha de integridade, retenção vencida, restauração não testada e divergência de autoridade DEVEM gerar alertas priorizados e rastreáveis.
- **FR-TDG-OPS-007**: Métricas e diagnósticos globais DEVEM usar identificadores seguros e não expor conteúdo funcional, dados pessoais, dados financeiros ou localização física a público não autorizado.
- **FR-TDG-OPS-008**: Jornadas administrativas DEVEM ser navegáveis por teclado, compreensíveis sem depender somente de cor e exigir confirmação proporcional ao impacto.

### Decisões de Infraestrutura Auditáveis

- **FR-TDG-INFRA-SCHED**: Proteção, verificação de recuperabilidade, revisão de impedimentos, destinação e expiração de exportações DEVEM ocorrer por política programada configurável, sem depender de sessão ativa.
- **FR-TDG-INFRA-LOCK**: Restauração, destinação e transferência DEVEM ser serializadas por escopo em todas as instâncias, com detecção e recuperação segura de execução abandonada.
- **FR-TDG-INFRA-IDEMP**: Cada solicitação e etapa durável DEVE possuir identidade estável suficiente para repetição, retomada e reconciliação sem duplicar efeitos.
- **FR-TDG-INFRA-BACKUP**: Objetivos, frequência, retenção, separação, testes e alertas das proteções DEVEM ser configurações explícitas por ambiente, com valores de produção aprovados antes da ativação.
- **FR-TDG-INFRA-KEY**: Proteções criptográficas de cópias e exportações DEVEM suportar versionamento, rotação e recuperação durante toda a vigência legítima, sem revalidar material expirado.
- **FR-TDG-INFRA-CLOCK**: Processos de retenção, validade e auditoria DEVEM usar referência temporal confiável e detectar divergência que possa antecipar eliminação ou prolongar acesso indevido.

### Limites com Features Relacionadas

- **FR-TDG-BOUND-001**: `tenant-context-isolation` permanece responsável por selecionar, propagar e revalidar o tenant ativo nas operações comuns.
- **FR-TDG-BOUND-002**: `tenant-storage-provisioning` permanece responsável por criar, versionar, migrar e desativar a estrutura física; esta feature define proteção, restauração e destinação de seus dados.
- **FR-TDG-BOUND-003**: Cada módulo futuro DEVE declarar suas categorias, eventos auditáveis e políticas de retenção antes de persistir dados próprios.
- **FR-TDG-BOUND-004**: Atendimento processual completo a solicitações de titulares, comunicação de incidentes e definição jurídica dos agentes de tratamento PODERÃO ser detalhados em features próprias sem enfraquecer os contratos desta feature.
- **FR-TDG-BOUND-005**: Cancelamento e estados da conta permanecem definidos em `account-registration`; esta feature determina como os dados são preservados e destinados após essas transições.

### Key Entities

- **Data Category**: classificação versionada de um conjunto de dados, com pertencimento, finalidade, sensibilidade, público, retenção e destinação.
- **Retention Policy**: regra versionada que relaciona categoria, evento inicial, prazo ou condição, justificativa e ação final.
- **Preservation Hold**: impedimento temporário e revisável que suspende a destinação de um escopo por motivo autorizado.
- **Audit Event**: evidência protegida e logicamente imutável de uma ação ou resultado, vinculada ao contexto, ator ou processo e correlação.
- **Recovery Point**: conjunto protegido e verificável, identificado por escopo, instante, versão, integridade, vigência e estado de teste.
- **Recovery Set**: ciclo coordenado que relaciona o componente global, os componentes de todos os tenants elegíveis e o resultado individual necessário à recuperação completa ou seletiva.
- **Restore Process**: operação durável que valida e recupera um escopo, preservando autorização, etapas, reconciliações e resultado.
- **Data Disposition Process**: execução durável de eliminação ou anonimização sobre categorias e períodos elegíveis.
- **Data Export Request**: solicitação autorizada de cópia, com modalidade, escopo, finalidade, validade, progresso e resultado temporário.
- **Tenant Transfer Process**: operação durável que move o armazenamento e controla origem, destino e autoridade sem mudar a identidade do tenant.

## Success Criteria

### Measurable Outcomes

- **SC-TDG-001**: Em 100% dos modelos validados, dados globais não possuem dependência para tenant e toda referência tenant-global segue a política de remoção declarada.
- **SC-TDG-002**: Em 100% dos testes de eliminação global, nenhum tenant impede a operação por vínculo proibido e nenhum dado de outra conta é consultado ou alterado.
- **SC-TDG-003**: Em 100% das operações críticas testadas, existe auditoria completa ou a operação não é declarada concluída.
- **SC-TDG-004**: Nenhum evento, diagnóstico ou exportação testado contém senha, segredo, chave privada, token ou prova reutilizável.
- **SC-TDG-005**: Em 100% dos períodos operacionais da implantação de referência, existe ponto válido com no máximo 24 horas ou é gerado alerta prioritário de descumprimento.
- **SC-TDG-006**: Em 100% dos testes de restauração individual, somente o tenant escolhido fica indisponível e nenhum outro tenant sofre alteração.
- **SC-TDG-007**: Em 100% das restaurações, o escopo somente é liberado após validar identidade, integridade, versão e decisões posteriores que não podem ser reativadas.
- **SC-TDG-008**: Pelo menos 95% dos testes periódicos da implantação de referência concluem a recuperação operacional em até 8 horas; todos os descumprimentos geram ação corretiva rastreável.
- **SC-TDG-009**: Em 100% das categorias persistidas em produção, existe finalidade, classificação e política de destinação vigentes.
- **SC-TDG-010**: Em 100% dos testes, dado elegível é eliminado ou anonimizado até o próximo ciclo programado, e dado sob impedimento permanece protegido somente no escopo aplicável.
- **SC-TDG-011**: Em 100% das restaurações de cópias antigas, destinações, revogações e impedimentos posteriores são reconciliados antes da liberação.
- **SC-TDG-012**: Em 100% das exportações, o resultado contém somente o escopo autorizado, expira conforme a política e não pode ser acessado após revogação ou vencimento.
- **SC-TDG-013**: Pelo menos 90% dos usuários de teste localizam o estado de sua exportação e obtêm o resultado pronto sem ajuda em até dois minutos, excluído o tempo de processamento.
- **SC-TDG-014**: Em 100% das transferências testadas, existe no máximo uma localização autoritativa e o tenant mantém a mesma identidade funcional.
- **SC-TDG-015**: Em 100% das falhas induzidas antes ou depois da troca de autoridade, não ocorre escrita dividida nem retorno silencioso a uma cópia desatualizada.
- **SC-TDG-016**: Todas as jornadas administrativas podem ser concluídas apenas por teclado e mantêm estados e riscos compreensíveis sem depender somente de cor.
- **SC-TDG-017**: Em 100% dos ciclos testados, o conjunto diário identifica o componente global e todos os tenants elegíveis; conjunto incompleto não é apresentado como apto à recuperação integral.
- **SC-TDG-018**: Em 100% dos testes com a configuração padrão, dados de conta cancelada permanecem protegidos por 90 dias sem permitir operação comum ou reativação implícita e, ao final, seguem as políticas de cada categoria.
- **SC-TDG-019**: Em 100% dos testes da implantação de referência, os 30 conjuntos diários mais recentes permanecem elegíveis e qualquer conjunto excedente é destinado ou preservado por impedimento rastreável.
- **SC-TDG-020**: Em 100% dos testes, usuário autorizado obtém em autosserviço sua exportação pessoal ou da conta correspondente, enquanto solicitante sem chaves vigentes não recebe o arquivo nem dados sobre seu conteúdo.

## Referências Normativas

- [Lei nº 13.709/2018 — Lei Geral de Proteção de Dados Pessoais](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709compilado.htm)
- [Guia Orientativo sobre Segurança da Informação para Agentes de Tratamento de Pequeno Porte — ANPD](https://www.gov.br/anpd/pt-br/documentos-e-publicacoes/guia-vf.pdf)
