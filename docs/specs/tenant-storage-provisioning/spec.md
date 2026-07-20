# Feature Specification: Provisionamento do Armazenamento de Tenant

**Feature**: `tenant-storage-provisioning`
**Created**: 2026-07-20
**Status**: Clarified — pronta para planejamento

## Escopo

Esta feature cria, inicializa, versiona, atualiza, recupera e desativa o armazenamento físico exclusivo de cada tenant. Uma conta somente pode operar quando seu armazenamento estiver íntegro, identificado, compatível com a versão vigente da aplicação e marcado como pronto.

Inclui o registro global da localização do tenant, provisionamento durável e idempotente, aplicação ordenada das estruturas e dados iniciais, acompanhamento de progresso, migrações individuais e em lote, isolamento de falhas, retomada segura e desativação controlada. Não inclui regras de cadastro da conta, grupos e chaves, seleção do contexto ativo, conteúdo funcional dos módulos, política completa de backup e retenção, transferência de dados entre contas nem desenho detalhado das relações entre dados globais e de tenant.

## Clarifications

### Session 2026-07-20

- Q: Qual parte do sistema deve ficar indisponível diante de armazenamento ausente, falho ou incompatível? -> A: Incompatibilidade global impede a inicialização operacional da aplicação; incompatibilidade individual coloca somente o tenant afetado em quarentena, preservando os demais e permitindo diagnóstico administrativo.
- Q: Qual deve ser a disponibilidade durante uma migração? -> A: O armazenamento em migração fica sempre indisponível. Migração global indisponibiliza a aplicação; migração de tenant indisponibiliza somente o tenant afetado até sua conclusão compatível.
- Q: Como devem ser iniciadas as migrações de uma nova versão? -> A: Automaticamente: a migração global ocorre primeiro e bloqueia a operação da aplicação; depois os tenants são enfileirados e liberados individualmente, sem que a falha de um interrompa os seguintes.
- Q: Como o sistema deve repetir operações após uma falha? -> A: Falhas transitórias de provisionamento recebem até três tentativas automáticas por padrão, com limite configurável; esgotado o limite, exigem intervenção autorizada. Migrações com falha nunca são repetidas automaticamente.
- Q: Qual política deve ser adotada para reversão de migrações? -> A: Migrações são progressivas (`forward-only`) e nunca são desfeitas automaticamente. Uma falha é recuperada por nova migração corretiva, com identidade própria, ou pela restauração autorizada de um ponto de recuperação validado.

## User Scenarios & Testing

### User Story 1 - Preparar o armazenamento de uma nova conta (Priority: P1)

Ao criar uma conta, o usuário acompanha a preparação de um espaço de dados exclusivo e somente recebe acesso operacional quando todas as etapas obrigatórias terminam com sucesso.

**Why this priority**: nenhuma funcionalidade de conta pode operar com segurança sem um armazenamento íntegro e isolado.

**Independent Test**: criar uma conta válida, acompanhar o provisionamento e comprovar que ela recebe um único armazenamento exclusivo, atualizado e utilizável somente depois da conclusão integral.

**Acceptance Scenarios**:

1. **Given** criação válida de conta ainda sem armazenamento, **When** o provisionamento é aceito, **Then** o sistema reserva uma localização interna exclusiva e inicia um processo durável
2. **Given** todas as etapas concluídas, **When** integridade e versão são confirmadas, **Then** o armazenamento torna-se pronto e a criação da conta pode prosseguir para ativação
3. **Given** falha em qualquer etapa, **When** o processo termina, **Then** a conta não se torna operacional e nenhum armazenamento parcial é apresentado como pronto
4. **Given** repetição da mesma intenção de criação, **When** o pedido é processado novamente, **Then** o processo existente é retornado ou retomado sem criar outro armazenamento

---

### User Story 2 - Recuperar provisionamento incompleto (Priority: P1)

Um administrador autorizado identifica uma falha, consulta seu estágio e retoma ou reinicia apenas as etapas seguras sem perder a identidade reservada para o tenant.

**Why this priority**: criação física e inicialização não são indivisíveis; falhas precisam ser recuperáveis sem improvisação manual ou reutilização perigosa.

**Independent Test**: provocar falha depois da criação física e antes da conclusão, reiniciar a aplicação e verificar que o processo permanece conhecido, indisponível para uso e passível de retomada idempotente.

**Acceptance Scenarios**:

1. **Given** processo interrompido por reinicialização, **When** o serviço volta, **Then** reconhece o último estado persistido e não declara o tenant pronto indevidamente
2. **Given** falha recuperável, **When** uma nova tentativa autorizada é executada, **Then** retoma do último ponto seguro sem duplicar estruturas ou dados iniciais
3. **Given** falha que exige intervenção, **When** o administrador consulta o processo, **Then** identifica tenant, etapa, tentativas e orientação segura sem receber credenciais ou detalhes sensíveis
4. **Given** localização parcial ou órfã, **When** a reconciliação operacional a encontra, **Then** ela é vinculada ao processo conhecido ou colocada em quarentena, nunca atribuída a outra conta
5. **Given** falha transitória durante o provisionamento, **When** ainda há tentativas disponíveis, **Then** o sistema repete automaticamente a etapa sem exceder o limite configurado nem duplicar efeitos
6. **Given** limite de tentativas automáticas esgotado, **When** o processo permanece incompleto, **Then** ele fica indisponível e aguarda intervenção autorizada

---

### User Story 3 - Manter cada tenant na versão compatível (Priority: P1)

O operador atualiza a aplicação e acompanha a evolução controlada dos armazenamentos dos tenants, preservando a disponibilidade dos tenants não afetados por eventual falha.

**Why this priority**: módulos e dados evoluem continuamente, e operar sobre versão desconhecida pode corromper informações ou romper o isolamento.

**Independent Test**: partir de vários tenants em versão anterior, aplicar a evolução e verificar ordem, exclusividade, versão final e isolamento de uma falha induzida em apenas um tenant.

**Acceptance Scenarios**:

1. **Given** tenant em versão anterior suportada, **When** sua atualização é iniciada, **Then** as mudanças pendentes são aplicadas na ordem definida e cada resultado é registrado
2. **Given** tenant já na versão esperada, **When** o processo é repetido, **Then** nenhuma mudança é reaplicada nem produz efeito duplicado
3. **Given** falha durante atualização de um tenant, **When** outros tenants são processados, **Then** a falha não altera seus dados nem associa o contexto defeituoso às execuções seguintes
4. **Given** versão ausente, adulterada, desconhecida ou incompatível, **When** uma operação de negócio tenta utilizar o tenant, **Then** o acesso é bloqueado até diagnóstico e recuperação
5. **Given** armazenamento global incompatível, **When** a aplicação inicia, **Then** sua inicialização operacional falha explicitamente sem disponibilizar tenants
6. **Given** somente um tenant incompatível, **When** a instalação atende outras contas, **Then** apenas o tenant afetado permanece em quarentena e os demais continuam disponíveis
7. **Given** nova versão com migração global pendente, **When** a aplicação inicia, **Then** a migração global é executada automaticamente antes da disponibilidade operacional
8. **Given** armazenamento global compatível e tenants com migrações pendentes, **When** o processamento automático começa, **Then** os tenants são enfileirados e liberados individualmente após sucesso, sem que a falha de um interrompa os seguintes
9. **Given** falha em uma migração global ou de tenant, **When** o processo registra a falha, **Then** nenhuma repetição automática é iniciada e uma nova execução depende de intervenção autorizada
10. **Given** migração aplicada integral ou parcialmente e depois considerada falha, **When** a recuperação é autorizada, **Then** o sistema utiliza uma nova migração corretiva ou restaura um ponto de recuperação validado sem executar reversão automática da evolução original

---

### User Story 4 - Administrar o ciclo de vida físico do tenant (Priority: P2)

Um administrador do sistema acompanha suspensão, cancelamento, restauração ou desativação sem reutilizar identificadores e sem remover dados antes das autorizações e retenções aplicáveis.

**Why this priority**: o ciclo físico precisa acompanhar a conta sem transformar suspensão ou cancelamento em perda imediata e irreversível.

**Independent Test**: suspender e cancelar uma conta, verificar a preservação do armazenamento e comprovar que eventual desativação não permite reutilizá-lo como outro tenant.

**Acceptance Scenarios**:

1. **Given** conta suspensa, **When** seu estado muda, **Then** o armazenamento permanece preservado para os contextos restritos autorizados
2. **Given** conta cancelada, **When** novas operações comuns são tentadas, **Then** permanecem bloqueadas sem exclusão física automática dos dados
3. **Given** desativação física autorizada após cumprimento das políticas aplicáveis, **When** é executada, **Then** a localização deixa de ser utilizável e seu identificador não pode ser atribuído novamente
4. **Given** restauração autorizada, **When** os dados recuperados são validados, **Then** retornam vinculados ao mesmo tenant e nunca substituem silenciosamente outro armazenamento

---

### User Story 5 - Monitorar e auditar a frota de tenants (Priority: P2)

Um administrador do sistema consulta o estado dos armazenamentos, identifica processos parados ou incompatíveis e executa somente as ações operacionais para as quais possui chaves explícitas.

**Why this priority**: uma instalação com muitos tenants precisa detectar falhas antes que usuários descubram indisponibilidades ou inconsistências.

**Independent Test**: criar tenants em estados distintos e verificar inventário, filtros, alertas, histórico e isolamento das ações administrativas.

**Acceptance Scenarios**:

1. **Given** tenants prontos, em processamento, falhos e incompatíveis, **When** o administrador consulta o inventário, **Then** identifica estado, versão, última transição e ação possível de cada um
2. **Given** processo sem progresso além do limite operacional, **When** a monitoração o detecta, **Then** produz alerta rastreável sem alterar automaticamente dados de outro tenant
3. **Given** administrador sem chave operacional exigida, **When** tenta retomar, migrar ou desativar armazenamento, **Then** a ação é negada independentemente de seu papel
4. **Given** ação operacional concluída ou negada, **When** a auditoria é consultada, **Then** apresenta ator, tenant, instante, origem, etapas e resultado seguro

### Edge Cases

- Duas instâncias tentam provisionar simultaneamente a mesma conta.
- Duas contas são criadas concorrentemente com nomes de exibição iguais.
- A localização física é criada, mas o registro global não confirma a etapa seguinte.
- O registro global existe, mas a localização física correspondente não existe ou está inacessível.
- A aplicação reinicia durante criação, carga inicial, migração, retomada ou desativação.
- Uma execução perde a resposta depois de concluir a etapa e é repetida.
- Um arquivo de migração conhecido muda de conteúdo depois de já ter sido aplicado.
- Existe lacuna, duplicidade ou ordem inválida no histórico de versões.
- Uma migração é compatível com alguns tenants, mas falha em outro por estado inesperado.
- Um tenant recebe solicitação de uso enquanto está migrando ou em recuperação.
- A conta é cancelada enquanto seu armazenamento ainda está sendo provisionado.
- O usuário fundador é bloqueado durante o provisionamento.
- O processo encontra estrutura desconhecida criada fora do mecanismo oficial.
- Uma restauração contém identidade de tenant ou versão incompatível com o destino.
- O espaço disponível termina durante criação ou migração.
- Um tenant permanece em processamento sem heartbeat ou progresso observável.
- Uma migração futura precisar modificar referências entre tenant e global.

## Requirements

### Identidade e Topologia do Armazenamento

- **FR-TSP-ID-001**: Cada conta DEVE corresponder a exatamente um tenant e possuir exatamente uma localização física ativa de armazenamento por ambiente operacional.
- **FR-TSP-ID-002**: Cada tenant DEVE possuir armazenamento logicamente e fisicamente separado dos demais tenants, além de um cadastro global que resolva sua localização.
- **FR-TSP-ID-003**: A identidade funcional do tenant DEVE permanecer independente de nome da conta, nome físico, servidor, instância ou localização atual dos dados.
- **FR-TSP-ID-004**: O identificador físico DEVE ser interno, imutável, gerado pelo sistema e nunca informado pelo usuário.
- **FR-TSP-ID-005**: Identificador físico reservado, utilizado, cancelado, desativado ou restaurado NÃO DEVE ser reutilizado para outro tenant.
- **FR-TSP-ID-006**: A interface comum e os contratos funcionais NÃO DEVEM expor nome físico, credenciais ou detalhes de conexão do armazenamento.
- **FR-TSP-ID-007**: A resolução física DEVE permitir evolução futura da distribuição dos tenants sem alterar sua identidade funcional ou referências externas estáveis.
- **FR-TSP-ID-008**: Divergência entre tenant, registro global e localização física DEVE bloquear o uso até reconciliação segura.

### Estados e Disponibilidade

- **FR-TSP-STATE-001**: O armazenamento DEVE possuir estados suficientes para distinguir ao menos solicitado, provisionando, migrando, inicializando, pronto, falho, desativando e inativo.
- **FR-TSP-STATE-002**: Somente armazenamento no estado pronto e em versão compatível DEVE aceitar operações comuns de tenant.
- **FR-TSP-STATE-003**: Estados intermediários ou falhos NÃO DEVEM ser apresentados como prontos nem permitir ativação da conta.
- **FR-TSP-STATE-004**: Cada transição DEVE registrar estado anterior, novo estado, etapa, instante, origem e resultado.
- **FR-TSP-STATE-005**: Transições inválidas, regressões implícitas ou saltos de etapa não reconhecidos DEVEM ser rejeitados.
- **FR-TSP-STATE-006**: A conta somente DEVE tornar-se ativa depois que armazenamento, associação fundadora, concessões mínimas e plano inicial estiverem confirmados pelos respectivos processos.
- **FR-TSP-STATE-007**: Suspensão da conta NÃO DEVE alterar automaticamente o armazenamento para inativo nem impedir ações restritas autorizadas.
- **FR-TSP-STATE-008**: Cancelamento da conta DEVE bloquear operações comuns sem provocar exclusão física imediata.
- **FR-TSP-STATE-009**: Armazenamento global ausente, falho ou incompatível DEVE impedir explicitamente a inicialização operacional da aplicação e o acesso aos tenants.
- **FR-TSP-STATE-010**: Armazenamento individual ausente, falho ou incompatível DEVE colocar somente o tenant afetado em quarentena, mantendo os demais disponíveis e permitindo diagnóstico administrativo pelo contexto global válido.

### Provisionamento Inicial

- **FR-TSP-PROV-001**: O provisionamento DEVE ser executado como processo durável, independente da permanência da sessão ou conexão que o solicitou.
- **FR-TSP-PROV-002**: Antes de criar a localização física, o sistema DEVE reservar de maneira consistente a identidade, o identificador físico e a intenção de provisionamento.
- **FR-TSP-PROV-003**: A localização nova DEVE ser preparada diretamente na versão vigente suportada, com todas as estruturas e dados iniciais obrigatórios.
- **FR-TSP-PROV-004**: O estado final de uma localização criada do zero DEVE ser equivalente ao estado obtido pela aplicação ordenada de todas as evoluções vigentes.
- **FR-TSP-PROV-005**: Dados iniciais obrigatórios DEVEM possuir identidade estável e não poderão ser duplicados por repetição ou retomada.
- **FR-TSP-PROV-006**: O provisionamento NÃO DEVE depender de tenant ativo na área de trabalho do usuário e DEVE operar com contexto de sistema explicitamente autorizado.
- **FR-TSP-PROV-007**: Conclusão parcial NÃO DEVE permitir operações de negócio, seleção de contexto comum ou ativação da conta.
- **FR-TSP-PROV-008**: Repetição da mesma intenção DEVE retornar, retomar ou concluir o processo existente sem reservar outro armazenamento.
- **FR-TSP-PROV-009**: Intenções distintas e válidas do mesmo usuário DEVEM poder criar contas e armazenamentos independentes.
- **FR-TSP-PROV-010**: O solicitante DEVE poder acompanhar estado e progresso em linguagem segura sem acesso a credenciais, comandos ou localização física.

### Falha, Retomada e Reconciliação

- **FR-TSP-REC-001**: Toda etapa relevante DEVE possuir resultado persistido suficiente para distinguir não iniciada, em execução, concluída e falha.
- **FR-TSP-REC-002**: Reinicialização ou troca de instância NÃO DEVE apagar o processo nem fazer uma etapa concluída voltar a parecer pendente.
- **FR-TSP-REC-003**: Retomada DEVE partir do último ponto comprovadamente seguro e revalidar os efeitos existentes antes de continuar.
- **FR-TSP-REC-004**: Etapa repetida NÃO DEVE duplicar estrutura, dado inicial, vínculo ou transição de estado.
- **FR-TSP-REC-005**: Falha DEVE marcar o armazenamento como indisponível, preservar evidências necessárias ao diagnóstico e impedir uso parcial.
- **FR-TSP-REC-006**: Falha em um tenant NÃO DEVE alterar estado, versão, dados ou processo de outro tenant.
- **FR-TSP-REC-007**: Localização sem registro correspondente ou registro sem localização válida DEVE ser detectável e tratada como divergência operacional.
- **FR-TSP-REC-008**: Localização divergente NÃO DEVE ser removida, adotada ou atribuída automaticamente sem reconciliação autorizada e auditada.
- **FR-TSP-REC-009**: Tentativas automáticas e manuais DEVEM preservar contagem, causa anterior e resultado de cada execução.
- **FR-TSP-REC-010**: Falha definitiva ou não reconhecida DEVE exigir intervenção autorizada e não poderá ser ocultada por tentativas ilimitadas.
- **FR-TSP-REC-011**: Etapa de provisionamento com falha reconhecida como transitória DEVE ser repetida automaticamente enquanto houver tentativas disponíveis.
- **FR-TSP-REC-012**: O limite de tentativas automáticas de provisionamento DEVE ser configurável e adotar três tentativas por padrão.
- **FR-TSP-REC-013**: Ao esgotar o limite de tentativas automáticas, o processo DEVE permanecer indisponível e exigir intervenção autorizada para nova execução.

### Versionamento e Migrações

- **FR-TSP-MIG-001**: O armazenamento global e cada armazenamento de tenant DEVEM declarar sua versão estrutural exata e verificável.
- **FR-TSP-MIG-002**: Cada evolução DEVE possuir identificador único, ordem determinística, conteúdo verificável e histórico de aplicação.
- **FR-TSP-MIG-003**: Evolução já distribuída ou aplicada NÃO DEVE ser alterada silenciosamente; correções posteriores DEVEM constituir nova evolução.
- **FR-TSP-MIG-004**: O sistema DEVE detectar evolução ausente, duplicada, fora de ordem, alterada ou desconhecida antes de operar sobre o armazenamento afetado.
- **FR-TSP-MIG-005**: Evoluções pendentes DEVEM ser aplicadas uma única vez e na ordem prevista para cada tenant.
- **FR-TSP-MIG-006**: Apenas uma criação, migração, retomada, restauração ou desativação estrutural DEVE modificar o mesmo tenant por vez.
- **FR-TSP-MIG-007**: Execuções concorrentes em instâncias diferentes DEVEM respeitar a mesma exclusividade por tenant.
- **FR-TSP-MIG-008**: A aplicação DEVE operar somente com a versão estrutural exata esperada pela versão de código em execução e bloquear armazenamento diferente dessa versão.
- **FR-TSP-MIG-009**: Armazenamento em migração DEVE permanecer indisponível para qualquer operação comum, sem modo online ou somente leitura.
- **FR-TSP-MIG-009A**: Migração global DEVE manter toda a aplicação operacionalmente indisponível até conclusão bem-sucedida e compatível.
- **FR-TSP-MIG-009B**: Migração de tenant DEVE manter somente o tenant afetado indisponível até conclusão bem-sucedida e compatível, preservando a operação dos demais tenants.
- **FR-TSP-MIG-010**: Cada etapa aplicada DEVE registrar início, término, versão anterior, versão resultante e resultado.
- **FR-TSP-MIG-011**: Falha de migração DEVE interromper o tenant afetado no último estado conhecido, impedir sua marcação como compatível e preservar os demais tenants.
- **FR-TSP-MIG-012**: Retomada após falha DEVE verificar o que foi efetivamente aplicado antes de repetir ou continuar.
- **FR-TSP-MIG-013**: Migração em lote DEVE permitir acompanhar resultado individual, progresso agregado e tenants ainda pendentes.
- **FR-TSP-MIG-014**: Nenhuma atualização da aplicação DEVE deixar tenant operando sobre versão estrutural desconhecida.
- **FR-TSP-MIG-015**: Migração global obrigatória DEVE ser iniciada automaticamente antes da disponibilidade operacional da aplicação; eventual falha DEVE manter o bloqueio global.
- **FR-TSP-MIG-016**: Após o armazenamento global alcançar a versão exata esperada, tenants pendentes DEVEM ser enfileirados automaticamente e cada tenant DEVE ser liberado somente depois de sua própria conclusão bem-sucedida.
- **FR-TSP-MIG-017**: Falha na migração de um tenant DEVE colocá-lo em quarentena sem interromper o processamento automático dos demais tenants da fila.
- **FR-TSP-MIG-018**: Migração global ou de tenant que falhar NÃO DEVE ser repetida automaticamente; uma nova execução DEVE depender de intervenção autorizada e preservar o diagnóstico da tentativa anterior.
- **FR-TSP-MIG-019**: Migrações DEVEM ser progressivas (`forward-only`) e NÃO DEVEM possuir ou executar reversão estrutural automática.
- **FR-TSP-MIG-020**: Correção posterior de uma migração falha DEVE ser representada por nova evolução, com identidade, conteúdo verificável, ordem e auditoria próprios.
- **FR-TSP-MIG-021**: Como alternativa à migração corretiva, operador autorizado DEVE poder recuperar o armazenamento a partir de ponto de recuperação validado conforme `tenant-data-governance`, sem marcar o armazenamento como pronto antes de alcançar novamente a versão exata esperada.

### Desativação e Restauração

- **FR-TSP-LIFE-001**: Suspensão ou cancelamento da conta NÃO DEVE excluir automaticamente seu armazenamento.
- **FR-TSP-LIFE-002**: Desativação física DEVE depender de autorização global explícita e da confirmação de que retenções, impedimentos e recuperações aplicáveis foram atendidos.
- **FR-TSP-LIFE-003**: Desativação DEVE ser idempotente, auditada e impedir novas conexões ou operações comuns no tenant.
- **FR-TSP-LIFE-004**: Localização desativada ou removida NÃO DEVE ser reutilizada para outra conta.
- **FR-TSP-LIFE-005**: Restauração DEVE validar identidade do tenant, origem, integridade e versão antes de disponibilizar os dados.
- **FR-TSP-LIFE-006**: Restauração NÃO DEVE sobrescrever silenciosamente tenant existente, alterar sua identidade funcional ou reativar conta cancelada por consequência implícita.
- **FR-TSP-LIFE-007**: Armazenamento restaurado DEVE permanecer indisponível até concluir as validações e evoluções exigidas pela versão atual.
- **FR-TSP-LIFE-008**: Exclusão física, retenção, anonimização e recuperação de desastre DEVEM seguir as políticas definidas em `tenant-data-governance`.

### Segurança Operacional

- **FR-TSP-SEC-001**: Criar, migrar, reconciliar, restaurar ou desativar armazenamento DEVE exigir contexto de sistema e chave global específica para a operação.
- **FR-TSP-SEC-002**: Papel de administrador do sistema, isoladamente, NÃO DEVE autorizar operação física.
- **FR-TSP-SEC-003**: Usuário ou administrador de conta NÃO DEVE receber acesso direto à localização física ou capacidade de executar operações estruturais.
- **FR-TSP-SEC-004**: Capacidades estruturais DEVEM permanecer separadas das credenciais e permissões usadas por operações comuns de negócio.
- **FR-TSP-SEC-005**: Falha ao determinar tenant, localização, versão, estado ou autorização DEVE resultar em interrupção segura.
- **FR-TSP-SEC-006**: Entradas externas NÃO DEVEM ser utilizadas diretamente como nome físico, localização, comando ou versão confiável.
- **FR-TSP-SEC-007**: Logs, auditorias e mensagens NÃO DEVEM expor credenciais, segredos, comandos completos ou detalhes de conexão.
- **FR-TSP-SEC-008**: Ações estruturais sensíveis DEVEM exigir autenticação recente, 2FA compatível e confirmação reforçada além da chave aplicável.

### Operação, Progresso e Auditoria

- **FR-TSP-OPS-001**: Cada provisionamento, migração, retomada, reconciliação, restauração ou desativação DEVE possuir protocolo estável de acompanhamento.
- **FR-TSP-OPS-002**: O processo DEVE informar estado, etapa atual, progresso conhecido, tentativas e resultado final em formato compreensível ao público autorizado.
- **FR-TSP-OPS-003**: Administrador autorizado DEVE poder consultar e filtrar tenants por estado, versão, incompatibilidade, falha e ausência de progresso.
- **FR-TSP-OPS-004**: Processo parado além do limite operacional configurado DEVE ser detectado e sinalizado sem ser considerado concluído.
- **FR-TSP-OPS-005**: Falhas, incompatibilidades, divergências e tentativas de ação negadas DEVEM produzir alerta e auditoria proporcionais ao risco.
- **FR-TSP-OPS-006**: Auditoria DEVE registrar ator ou origem de sistema, tenant, protocolo, ação, etapas, versões, instantes e resultado.
- **FR-TSP-OPS-007**: Alteração manual de estado NÃO DEVE substituir reconciliação real nem permitir marcar armazenamento inconsistente como pronto.
- **FR-TSP-OPS-008**: A interface operacional DEVE ser navegável por teclado e diferenciar estado, progresso, sucesso, alerta e falha sem depender somente de cor.

### Limites com Features Relacionadas

- **FR-TSP-BOUND-001**: Identidade, dados cadastrais, estados de negócio e ativação da conta pertencem a `account-registration`; esta feature fornece o estado físico necessário à ativação.
- **FR-TSP-BOUND-002**: Seleção e propagação do tenant ativo pertencem a `tenant-context-isolation`; esta feature informa se o armazenamento está pronto e compatível.
- **FR-TSP-BOUND-003**: Grupos, chaves e concessões pertencem a `access-control`; esta feature apenas exige as chaves globais correspondentes.
- **FR-TSP-BOUND-004**: Planos e funcionalidades liberadas pertencem a `plans-entitlements` e NÃO DEVEM modificar a identidade física do tenant.
- **FR-TSP-BOUND-005**: Regras de FKs, integridade global/tenant, backup, retenção, exclusão, anonimização e transferência pertencem a `tenant-data-governance`.
- **FR-TSP-BOUND-006**: FKs físicas de tenant para global poderão existir segundo `tenant-data-governance`, mas global NÃO DEVE possuir dependência ou FK para armazenamento de tenant.

### Decisões de Infraestrutura Auditáveis

- **FR-TSP-INFRA-SCHED**: Provisionamentos e retomadas automáticas DEVEM ser processados sem depender de sessão ativa; política e limites de execução DEVEM ser configuráveis e observáveis.
- **FR-TSP-INFRA-LOCK**: Operações estruturais concorrentes DEVEM ser serializadas por tenant em todas as instâncias e liberar execuções abandonadas de forma segura.
- **FR-TSP-INFRA-IDEMP**: Cada intenção de provisionamento e cada evolução DEVEM possuir identidade estável suficiente para impedir duplicidade durante repetição, concorrência ou recuperação.
- **FR-TSP-INFRA-BACKUP**: Migrações e restaurações DEVEM respeitar a disponibilidade de ponto de recuperação compatível definida em `tenant-data-governance`, sem declarar sucesso quando a proteção exigida estiver ausente.
- **FR-TSP-INFRA-RECOVERY**: Processos em execução DEVEM permanecer recuperáveis após reinício, troca de instância ou perda temporária de dependência.
- **FR-TSP-INFRA-CAPACITY**: Falta de espaço ou capacidade DEVE impedir novas etapas com segurança, preservar tenants existentes e gerar alerta operacional.

### Key Entities

- **Tenant Storage Registry**: cadastro global que relaciona identidade funcional do tenant, localização física interna, estado, versão, criação e última validação.
- **Provisioning Process**: processo durável de preparação de um novo armazenamento, com intenção, etapas, tentativas, progresso e resultado.
- **Provisioning Step**: unidade verificável e repetível do provisionamento ou recuperação, com estado e evidência de conclusão.
- **Storage Version**: identidade exata da estrutura vigente no armazenamento global ou de tenant.
- **Migration Definition**: evolução imutável e ordenada que transforma uma versão conhecida em outra.
- **Migration Execution**: resultado da aplicação de uma evolução em um armazenamento específico, com versões, instantes e falha quando houver.
- **Storage Reconciliation**: diagnóstico e resolução autorizada de divergência entre registro global, localização física e estado observado.
- **Storage Operation Audit Event**: histórico imutável das ações estruturais e seus resultados.

## Success Criteria

### Measurable Outcomes

- **SC-TSP-001**: Em 100% dos testes, cada conta provisionada possui um armazenamento exclusivo e nenhum identificador físico é reutilizado por outro tenant.
- **SC-TSP-002**: Em 100% dos testes de falha, nenhum armazenamento parcial ou incompatível é apresentado como pronto ou aceita operação comum.
- **SC-TSP-003**: Em 100% das repetições e concorrências testadas, uma intenção de criação produz no máximo um armazenamento para o tenant.
- **SC-TSP-004**: Em 100% das reinicializações testadas, processos pendentes ou falhos permanecem identificáveis e podem ser retomados sem duplicar etapas concluídas.
- **SC-TSP-005**: Em 100% dos testes de migração, cada evolução é aplicada uma única vez, na ordem correta e com versão final verificável.
- **SC-TSP-006**: Em 100% das falhas induzidas em um tenant, os dados, estados e versões dos demais tenants permanecem inalterados.
- **SC-TSP-007**: Em 100% dos testes, tenant ausente, divergente ou fora da versão compatível é bloqueado antes de operação de negócio.
- **SC-TSP-008**: Pelo menos 95% dos provisionamentos de uma conta vazia terminam em até 5 minutos no ambiente operacional de referência.
- **SC-TSP-009**: Um administrador autorizado identifica tenant, etapa e causa segura de um processo falho em até 5 minutos.
- **SC-TSP-010**: Em 100% das operações estruturais sensíveis testadas, existe auditoria com ator ou origem, tenant, etapas, versões, instantes e resultado.
- **SC-TSP-011**: Em 100% dos testes de restauração, os dados permanecem vinculados ao tenant original e nenhuma conta cancelada é reativada implicitamente.
- **SC-TSP-012**: Todas as jornadas operacionais podem ser concluídas apenas por teclado e mantêm estados distinguíveis sem depender somente de cor.
- **SC-TSP-013**: Em 100% das falhas de migração testadas, não ocorre reversão nem repetição automática; a recuperação preserva o histórico e somente libera o armazenamento após validação da versão exata esperada.
