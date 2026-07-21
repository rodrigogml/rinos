# Feature Specification: Operações da Plataforma

**Feature**: `platform-operations`
**Created**: 2026-07-20
**Status**: Clarified

## Escopo

Esta feature oferece visibilidade segura sobre a saúde estrutural da instalação, o provisionamento automático de novos tenants e as migrações automáticas executadas durante o deploy de uma nova versão. Ela permite que o usuário acompanhe na própria tela a preparação de uma conta nova e que administradores do sistema autorizados identifiquem estados, incompatibilidades, falhas, quarentenas e alertas, sem receber comandos para manipular diretamente bancos ou processos estruturais.

O provisionamento é iniciado pela criação válida de uma conta e prepara automaticamente seu armazenamento exclusivo na versão estrutural vigente. Migrações de bancos existentes são iniciadas automaticamente pelo deploy: o global é migrado primeiro e bloqueia toda a aplicação até ficar compatível; depois, os tenants pendentes são processados, e cada tenant somente é liberado após sua própria compatibilidade.

Falhas que esgotem a recuperação automática segura exigem intervenção externa do pessoal de infraestrutura. A aplicação não oferece botões para repetir ou corrigir migrações falhas, alterar versões, executar scripts, acessar terminal, criar backup ou restaurar dados. Backup, retenção física, teste de cópias e restauração são procedimentos operacionais executados diretamente no servidor e permanecem fora da interface e do controle do Rinos.

Todas as visões administrativas usam contexto global e chaves específicas do sistema. O papel “Administrador do Sistema” identifica o ator, mas não concede acesso por si próprio. Não inclui acesso ao conteúdo interno de contas, representação de usuários, edição geral de configurações, administração de planos, escolha de ferramentas físicas ou operação do servidor.

## Clarifications

### Session 2026-07-20

- Q: Quais operações estruturais devem ser comandadas pela aplicação e quais pertencem à infraestrutura? -> A: O provisionamento de tenant novo permanece automático e acompanhado em tela; migrações são automáticas no deploy, bloqueando toda a aplicação se o global falhar e somente o tenant afetado se sua migração falhar. Depois de falha não recuperável, a solução é externa pela infraestrutura. Backup e restauração não possuem interface nem controle dentro do Rinos.
- Q: Quantos tenants podem ser migrados simultaneamente depois que o global estiver compatível? -> A: O limite é configurável por instalação, com padrão inicial de um tenant por vez; o processamento permanece automático e nunca executa concorrência acima do limite vigente.
- Q: O que acontece quando uma conta é criada enquanto existem tenants na fila de migração? -> A: Seu provisionamento entra na mesma fila de criação e atualização de tenants, depois das operações já enfileiradas, e aguarda a vez respeitando o limite de concorrência vigente.
- Q: Quanto detalhe o criador da conta deve visualizar durante o provisionamento? -> A: Somente os estados Aguardando, Preparando, Pronta e Problema encontrado, acompanhados de orientação segura; etapas técnicas, topologia, versões e logs ficam fora dessa visão.
- Q: Como os problemas operacionais serão comunicados inicialmente? -> A: Problemas de tenant ficam visíveis na área administrativa enquanto a aplicação estiver disponível; falhas globais ou anteriores à inicialização dependem dos logs e do monitoramento externo da infraestrutura. A primeira versão não envia e-mail nem webhook operacional.

## User Scenarios & Testing

### User Story 1 - Compreender a saúde operacional da instalação (Priority: P1)

Um administrador autorizado consulta a situação estrutural conhecida e identifica rapidamente dependências degradadas, tenants incompatíveis, provisionamentos parados e migrações falhas, sem abrir dados funcionais de nenhuma conta.

**Why this priority**: toda resposta operacional depende de reconhecer o impacto real e diferenciar uma falha global de um problema isolado.

**Independent Test**: simular estados saudáveis, degradados, indisponíveis e desconhecidos em componentes globais e tenants, verificando resumo, filtros, alertas, proteção de dados e atualização do diagnóstico.

**Acceptance Scenarios**:

1. **Given** instalação saudável, **When** o administrador abre a visão operacional, **Then** identifica o estado global, a última avaliação válida e a ausência de ocorrências conhecidas
2. **Given** falha restrita a um tenant, **When** a saúde é consolidada, **Then** somente o tenant afetado aparece bloqueado ou degradado e os demais permanecem distinguíveis
3. **Given** dependência global incompatível ou indisponível, **When** a aplicação tenta iniciar, **Then** nenhuma funcionalidade comum ou administrativa é disponibilizada e o diagnóstico necessário fica disponível à infraestrutura fora da interface
4. **Given** ator sem chave de consulta operacional, **When** tenta abrir a visão administrativa, **Then** o acesso é negado independentemente de seu papel
5. **Given** um indicador não pode ser atualizado, **When** a visão é apresentada, **Then** mostra a última informação conhecida com sua idade e estado desconhecido, sem reutilizá-la como confirmação atual

---

### User Story 2 - Acompanhar o provisionamento de uma nova conta (Priority: P1)

Ao criar uma conta, o usuário acompanha na própria tela a preparação automática do armazenamento do tenant e somente recebe acesso quando todas as etapas obrigatórias forem concluídas e validadas.

**Why this priority**: a criação de contas precisa ser autônoma para o usuário, mas nenhum tenant parcial ou incompatível pode receber operações de negócio.

**Independent Test**: criar uma conta, acompanhar seu provisionamento, induzir falhas transitórias e definitivas e comprovar progresso seguro, repetição idempotente, bloqueio e isolamento.

**Acceptance Scenarios**:

1. **Given** criação válida de conta, **When** a solicitação é confirmada, **Then** o provisionamento automático é iniciado e seu estado aparece ao usuário sem credenciais ou localização física
2. **Given** preparação concluída e validada, **When** o processo termina, **Then** a conta pode ser ativada com um único tenant na versão vigente
3. **Given** falha transitória reconhecida e tentativas disponíveis, **When** a política automática atua, **Then** a etapa é repetida sem duplicar estruturas ou dados iniciais
4. **Given** falha não recuperável ou tentativas esgotadas, **When** o processo termina, **Then** a conta permanece indisponível, o usuário recebe orientação segura e a solução passa à infraestrutura
5. **Given** repetição da mesma confirmação de criação, **When** ela é processada, **Then** o processo existente é retornado ou continuado sem criar outro armazenamento
6. **Given** falha isolada em uma criação, **When** outras contas operam, **Then** nenhum outro tenant recebe estado, contexto ou efeito do provisionamento falho
7. **Given** tenants antigos já enfileirados para migração, **When** uma conta nova é criada, **Then** seu provisionamento entra depois das operações existentes e a tela o apresenta como aguardando, não como falho

---

### User Story 3 - Executar migrações automaticamente no deploy (Priority: P1)

Ao implantar uma nova versão, a plataforma migra automaticamente o armazenamento global e os tenants para a versão estrutural exata esperada, mantendo cada escopo indisponível até sua validação e entregando falhas não recuperáveis à infraestrutura.

**Why this priority**: código e banco permanecerão sempre compatíveis; indisponibilidade controlada é preferível a executar sobre uma estrutura intermediária ou desconhecida.

**Independent Test**: iniciar uma versão com migrações globais e de tenants pendentes, induzir falhas globais e isoladas e comprovar ordem, bloqueio, isolamento, ausência de repetição interna e liberação seletiva.

**Acceptance Scenarios**:

1. **Given** versão global anterior à esperada, **When** o novo deploy inicia, **Then** a migração global começa automaticamente antes de qualquer disponibilidade operacional
2. **Given** migração global bem-sucedida e validada, **When** existem tenants pendentes, **Then** eles são processados automaticamente e liberados individualmente após sucesso
3. **Given** migração global falha, **When** a inicialização avalia o resultado, **Then** toda a aplicação permanece bloqueada e aguarda solução externa da infraestrutura
4. **Given** migração de um tenant falha, **When** os demais são processados, **Then** somente o tenant falho permanece em quarentena e a fila dos outros tenants continua
5. **Given** falha global ou de tenant registrada, **When** alguém procura uma ação na interface, **Then** não encontra comando para repetir, corrigir, reverter ou marcar a migração como concluída
6. **Given** versão ausente, alterada, duplicada, fora de ordem ou desconhecida, **When** a compatibilidade é avaliada, **Then** o escopo afetado permanece bloqueado para intervenção externa
7. **Given** global compatível e vários tenants pendentes, **When** a fila automática é processada com a configuração padrão, **Then** somente um tenant é migrado por vez e os seguintes aguardam sem receber seu contexto

---

### User Story 4 - Acompanhar alertas e histórico operacional (Priority: P2)

Um administrador autorizado prioriza ocorrências visíveis, reconhece quais exigem infraestrutura e consulta o histórico de processos e mudanças de estado para explicar a condição presente.

**Why this priority**: falhas estruturais não podem desaparecer, parecer resolvidas por omissão ou exigir acesso a dados dos tenants para serem diagnosticadas.

**Independent Test**: produzir alertas repetidos e correlacionados, reconhecer ocorrências informativas, encaminhar falhas externas e confirmar histórico, acessibilidade e ausência de dados sensíveis.

**Acceptance Scenarios**:

1. **Given** várias falhas com a mesma causa, **When** os alertas são consolidados, **Then** o administrador identifica a ocorrência principal, os escopos afetados e os eventos individuais
2. **Given** falha que exige infraestrutura, **When** é apresentada, **Then** a interface deixa claro que não existe correção interna e fornece referência segura para o diagnóstico externo
3. **Given** administrador reconhece uma ocorrência, **When** registra a decisão, **Then** responsável, justificativa e instante compõem o histórico sem alterar a condição técnica
4. **Given** condição reaparece depois de encerrada, **When** nova avaliação a detecta, **Then** uma nova ocorrência ou reabertura explícita é registrada sem apagar o histórico anterior
5. **Given** ocorrência relacionada a tenant, **When** o detalhe é consultado, **Then** apresenta somente metadados operacionais necessários e não conteúdo funcional da conta
6. **Given** falha global impede a inicialização, **When** a infraestrutura investiga, **Then** utiliza logs e monitoramento externo porque nenhuma área administrativa da aplicação está disponível
7. **Given** ocorrência de tenant conhecida enquanto a aplicação está disponível, **When** administrador autorizado consulta a área operacional, **Then** encontra o problema sem depender de e-mail ou webhook

### Edge Cases

- A instalação inicia sem conseguir determinar a versão do armazenamento global.
- Duas instâncias tentam provisionar simultaneamente a mesma conta.
- A aplicação reinicia durante a preparação de um tenant novo.
- Uma etapa de provisionamento termina, mas sua confirmação não pode ser persistida.
- Uma conta é cancelada enquanto o provisionamento ainda está em andamento.
- O fundador é bloqueado ou perde a sessão durante o provisionamento.
- Uma falha transitória persiste até esgotar o limite automático.
- Uma migração de tenant falha depois de aplicar parcialmente uma evolução.
- Uma definição de migração já aplicada não corresponde mais à evidência registrada.
- Um tenant é criado enquanto a fila de migrações do deploy está em execução.
- Várias instâncias iniciam simultaneamente sobre uma versão global anterior.
- A infraestrutura reinicia a aplicação sem corrigir a causa da migração falha.
- O armazenamento disponível termina durante provisionamento ou migração.
- O relógio das instâncias diverge durante a ordenação de etapas e eventos.
- Uma dependência oscila entre saudável e indisponível e produz alertas repetidos.
- O administrador tenta inserir comando, localização física ou identificador de versão não reconhecido.
- Uma restauração externa entrega banco antigo, incompleto ou incompatível à aplicação.

## Requirements

### Contexto, Autorizações e Segurança

- **FR-PO-CTX-001**: Toda visão administrativa desta feature DEVE executar em contexto global de sistema e NÃO DEVE estabelecer contexto funcional de tenant.
- **FR-PO-CTX-002**: O papel Administrador do Sistema NÃO DEVE conceder acesso; consultas e administração de ocorrências DEVEM exigir chaves globais específicas.
- **FR-PO-CTX-003**: Usuário criador DEVE poder acompanhar somente o provisionamento das contas que iniciou, sem receber visão global ou detalhes de infraestrutura.
- **FR-PO-CTX-004**: Operador global NÃO DEVE tornar-se participante, receber grupo ou chave de conta nem obter conteúdo funcional de tenant em decorrência da consulta operacional.
- **FR-PO-CTX-005**: Entradas da interface NÃO DEVEM ser tratadas como localização física, comando, credencial, versão confiável ou prova de autorização.
- **FR-PO-CTX-006**: A interface NÃO DEVE oferecer execução arbitrária de comando, script, consulta, migração, backup ou restauração.
- **FR-PO-CTX-007**: Mensagens, alertas e auditorias NÃO DEVEM revelar senha, segredo, token, chave privada, detalhes de conexão, comando completo nem localização física desnecessária.
- **FR-PO-CTX-008**: Alteração de ocorrência administrativa DEVE revalidar identidade, chave global e estado imediatamente antes de ser persistida.

### Inventário e Saúde Operacional

- **FR-PO-HEALTH-001**: A visão operacional DEVE apresentar separadamente saúde global, dependências conhecidas, capacidade, provisionamentos, migrações e tenants afetados.
- **FR-PO-HEALTH-002**: Cada indicador DEVE declarar estado saudável, degradado, indisponível ou desconhecido, instante da última avaliação válida e impacto conhecido.
- **FR-PO-HEALTH-003**: Estado desconhecido, desatualizado ou parcial NÃO DEVE ser agregado nem apresentado como saudável.
- **FR-PO-HEALTH-004**: A visão DEVE diferenciar falha global, falha compartilhada e falha isolada por tenant sem combinar dados funcionais dos tenants.
- **FR-PO-HEALTH-005**: Administrador autorizado DEVE poder filtrar por estado, tipo de processo, versão, atraso, severidade, tenant seguro e intervalo temporal.
- **FR-PO-HEALTH-006**: A visão DEVE mostrar quantidade e identidade segura dos tenants prontos, provisionando, migrando, em quarentena, incompatíveis, falhos e pendentes.
- **FR-PO-HEALTH-007**: Capacidade insuficiente ou próxima do limite operacional conhecido DEVE ser visível antes de bloquear novos processos quando essa informação estiver disponível à aplicação.
- **FR-PO-HEALTH-008**: Ausência de progresso além do limite configurado DEVE marcar o processo como possivelmente parado sem declará-lo falho ou concluído automaticamente.
- **FR-PO-HEALTH-009**: Cada estado agregado DEVE permitir chegar à evidência operacional segura que o compõe, respeitando as chaves e a proteção de dados.
- **FR-PO-HEALTH-010**: Atualização da visão NÃO DEVE reutilizar dados residuais de outro filtro, tenant ou consulta anterior.
- **FR-PO-HEALTH-011**: Falha global anterior à disponibilidade da aplicação DEVE ser diagnosticável por registros e sinais externos apropriados à infraestrutura, pois a interface administrativa também permanecerá bloqueada.

### Provisionamento Automático

- **FR-PO-PROV-001**: Criação válida de conta DEVE iniciar automaticamente um único processo durável de provisionamento do tenant correspondente.
- **FR-PO-PROV-002**: Antes da preparação física, o processo DEVE reservar de forma consistente a identidade, a localização interna e a intenção de provisionamento.
- **FR-PO-PROV-003**: Armazenamento novo DEVE ser preparado diretamente na versão estrutural vigente, com todas as estruturas e dados iniciais obrigatórios.
- **FR-PO-PROV-004**: A preparação DEVE utilizar os scripts de inicialização vigentes e resultar no mesmo estado estrutural final produzido pela aplicação ordenada de todos os updates existentes.
- **FR-PO-PROV-005**: A criação física do database ou schema DEVE ocorrer antes dos scripts de inicialização e fora do conteúdo do script `01-ddl.sql`.
- **FR-PO-PROV-006**: A capacidade técnica de criar armazenamento DEVE permanecer separada das credenciais comuns usadas para operações funcionais do tenant.
- **FR-PO-PROV-007**: Dados iniciais obrigatórios DEVEM possuir identidade estável e não poderão ser duplicados por repetição ou retomada.
- **FR-PO-PROV-008**: Conclusão parcial NÃO DEVE permitir ativação, seleção de contexto ou operação de negócio.
- **FR-PO-PROV-009**: Usuário criador DEVE acompanhar estado e progresso na própria tela sem acesso a credenciais, comandos ou localização física.
- **FR-PO-PROV-010**: Falha transitória reconhecida DEVE receber repetição automática enquanto houver tentativas disponíveis, com limite configurável e padrão inicial de três tentativas.
- **FR-PO-PROV-011**: Repetição DEVE partir do último ponto seguro e verificar efeitos já existentes antes de continuar.
- **FR-PO-PROV-012**: Repetição da mesma intenção NÃO DEVE criar outro armazenamento nem duplicar estrutura, seed, vínculo ou transição.
- **FR-PO-PROV-013**: Falha definitiva, desconhecida ou após esgotamento das tentativas DEVE manter a conta e o tenant indisponíveis e exigir solução externa da infraestrutura.
- **FR-PO-PROV-014**: A interface NÃO DEVE oferecer comando para ignorar a falha, alterar o estado para pronto ou executar correção estrutural manual.
- **FR-PO-PROV-015**: Correção externa seguida de nova inicialização ou processamento DEVE reavaliar o estado real e continuar somente de ponto comprovadamente seguro.
- **FR-PO-PROV-016**: Falha em um provisionamento NÃO DEVE alterar estado, versão, dados ou processo de outro tenant.
- **FR-PO-PROV-017**: Provisionamentos e migrações de tenant DEVEM compartilhar uma fila estrutural de criação e atualização sujeita ao mesmo limite configurado de concorrência.
- **FR-PO-PROV-018**: Provisionamento solicitado durante fila existente DEVE ser posicionado depois das operações já enfileiradas e permanecer no estado aguardando até possuir capacidade de execução.
- **FR-PO-PROV-019**: Estado aguardando DEVE ser distinguido de falha e informar ao usuário, de forma segura, que a preparação ainda não começou por existir operação estrutural anterior.
- **FR-PO-PROV-020**: A visão do criador DEVE limitar o estado do provisionamento a Aguardando, Preparando, Pronta ou Problema encontrado.
- **FR-PO-PROV-021**: Aguardando DEVE indicar que a operação está na fila; Preparando, que a execução começou; Pronta, que o tenant foi integralmente validado; Problema encontrado, que a conta continua indisponível e requer tratamento.
- **FR-PO-PROV-022**: A visão do criador NÃO DEVE exibir nomes físicos, localização, versão estrutural, scripts, etapas internas, tentativas detalhadas, causa técnica, stack trace ou logs.
- **FR-PO-PROV-023**: Problema encontrado DEVE oferecer orientação segura para aguardar ou buscar suporte, sem botão para repetir, ignorar, corrigir ou marcar o provisionamento como pronto.

### Migrações Automáticas no Deploy

- **FR-PO-MIG-001**: A aplicação DEVE declarar a versão estrutural exata esperada e comparar essa versão com o global e cada tenant durante o deploy.
- **FR-PO-MIG-002**: Cada update DEVE possuir identidade única, ordem determinística, conteúdo verificável e atualização final da versão do banco.
- **FR-PO-MIG-003**: Update já criado, distribuído ou aplicado NÃO DEVE ser alterado; correção posterior DEVE constituir novo update incremental.
- **FR-PO-MIG-004**: Scripts de inicialização DEVEM representar uma base nova já atualizada e permanecer estruturalmente equivalentes ao resultado dos updates incrementais.
- **FR-PO-MIG-005**: Migração global pendente DEVE iniciar automaticamente no deploy antes de qualquer disponibilidade operacional.
- **FR-PO-MIG-006**: A aplicação inteira DEVE permanecer bloqueada durante a migração global e enquanto sua versão final não puder ser validada.
- **FR-PO-MIG-007**: Falha da migração global DEVE interromper a inicialização operacional, preservar o diagnóstico e exigir solução externa da infraestrutura.
- **FR-PO-MIG-008**: Depois da compatibilidade global, tenants pendentes DEVEM ser processados automaticamente e liberados individualmente somente após alcançarem a versão exata esperada.
- **FR-PO-MIG-009**: Tenant em migração DEVE permanecer indisponível para operações comuns, sem modo online ou somente leitura.
- **FR-PO-MIG-010**: Falha de tenant DEVE colocá-lo em quarentena sem interromper automaticamente o processamento dos demais tenants elegíveis.
- **FR-PO-MIG-011**: Falha global ou de tenant NÃO DEVE iniciar nova tentativa dentro da mesma execução nem oferecer repetição pela interface.
- **FR-PO-MIG-012**: Após solução externa, nova inicialização DEVE reavaliar versão, histórico e efeitos existentes antes de aplicar qualquer etapa ainda válida.
- **FR-PO-MIG-013**: Evolução ausente, duplicada, fora de ordem, alterada ou desconhecida DEVE bloquear o escopo e exigir intervenção externa.
- **FR-PO-MIG-014**: Migrações DEVEM ser progressivas e NÃO DEVEM possuir reversão estrutural automática.
- **FR-PO-MIG-015**: Correção de migração falha DEVE ser representada por novo update quando houver mudança estrutural ou de dados, preservando a evidência anterior.
- **FR-PO-MIG-016**: O progresso agregado DEVE distinguir tenants aguardando, em execução, concluídos, falhos, em quarentena e incompatíveis.
- **FR-PO-MIG-017**: Nenhum armazenamento DEVE aceitar operações comuns sobre versão diferente da esperada pelo código em execução.
- **FR-PO-MIG-018**: Restauração realizada externamente NÃO DEVE dispensar a verificação de identidade, histórico e versão nem autorizar uso antes de eventual migração automática necessária.
- **FR-PO-MIG-019**: A quantidade máxima de tenants migrados simultaneamente DEVE ser configurável por instalação e adotar um tenant por vez como padrão inicial.
- **FR-PO-MIG-020**: A fila automática NÃO DEVE iniciar migrações acima do limite vigente e DEVE manter isolamento completo de contexto, progresso e falha entre execuções concorrentes quando o limite for ampliado.
- **FR-PO-MIG-021**: A fila estrutural DEVE preservar a posição relativa das operações já aceitas; criação posterior de tenant NÃO DEVE ultrapassar migrações anteriormente enfileiradas.

### Alertas, Ocorrências e Auditoria

- **FR-PO-ALERT-001**: Condição operacional relevante conhecida pela aplicação DEVE produzir ocorrência com severidade, origem, escopo seguro, início, estado e evidências necessárias.
- **FR-PO-ALERT-002**: Eventos repetidos da mesma causa DEVEM ser correlacionáveis sem eliminar o histórico individual nem ocultar novos escopos afetados.
- **FR-PO-ALERT-003**: Ocorrência DEVE distinguir aberta, reconhecida, em tratamento externo, resolvida e reaberta.
- **FR-PO-ALERT-004**: Reconhecimento, atribuição, nota e resolução administrativa DEVEM exigir chave aplicável e registrar ator, justificativa e instante.
- **FR-PO-ALERT-005**: Reconhecer ou marcar tratamento externo NÃO DEVE alterar a condição técnica, liberar escopo nem impedir nova avaliação.
- **FR-PO-ALERT-006**: Resolução somente DEVE ser automática quando existir prova objetiva de normalização; nos demais casos exigirá decisão autorizada sem modificar o estado estrutural.
- **FR-PO-ALERT-007**: Reaparecimento após resolução DEVE produzir reabertura explícita ou nova ocorrência correlacionada sem alterar o histórico anterior.
- **FR-PO-ALERT-008**: Administrador DEVE poder consultar linha temporal de processos, alertas, decisões e mudanças de estado por correlação.
- **FR-PO-ALERT-009**: Auditoria DEVE registrar ator ou processo, chave utilizada, escopo, ação, estado anterior, estado resultante, instante, origem, correlação e resultado.
- **FR-PO-ALERT-010**: Falha de persistência da auditoria obrigatória DEVE impedir a confirmação de alteração administrativa da ocorrência.
- **FR-PO-ALERT-011**: Logs, alertas, notas e auditorias NÃO DEVEM copiar conteúdo funcional de tenant nem dados pessoais além do necessário.
- **FR-PO-ALERT-012**: Histórico operacional DEVE seguir classificação, retenção, proteção, exportação e destinação definidas em `tenant-data-governance`.
- **FR-PO-ALERT-013**: As jornadas e estados operacionais DEVEM ser acessíveis por teclado e distinguíveis sem depender somente de cor, animação ou posição.
- **FR-PO-ALERT-014**: Ocorrências de tenant conhecidas enquanto a aplicação estiver operacional DEVEM permanecer visíveis na área administrativa para atores com chave global de consulta.
- **FR-PO-ALERT-015**: Falha global ou anterior à disponibilidade da aplicação DEVE produzir logs e sinais adequados ao monitoramento externo da infraestrutura, pois NÃO haverá interface interna disponível.
- **FR-PO-ALERT-016**: A primeira versão NÃO DEVE enviar alerta operacional por e-mail, webhook ou outra integração externa controlada pela aplicação.
- **FR-PO-ALERT-017**: Ausência de e-mail ou webhook NÃO DEVE ser apresentada como ausência de ocorrência; a área administrativa e os sinais externos mantêm responsabilidades distintas.

### Limites com Features Relacionadas

- **FR-PO-BOUND-001**: `tenant-storage-provisioning` define identidade, estados e invariantes do armazenamento; esta feature apresenta sua operação automática sem oferecer atalhos estruturais.
- **FR-PO-BOUND-002**: `tenant-data-governance` define políticas de proteção e continuidade como obrigações operacionais externas; esta feature NÃO DEVE executar, inventariar nem restaurar backups.
- **FR-PO-BOUND-003**: `account-registration` valida os dados e cria a intenção da conta; esta feature acompanha a preparação automática do armazenamento correspondente.
- **FR-PO-BOUND-004**: `system-directory-administration` administra identidades e estados globais; esta feature apenas apresenta impacto operacional seguro.
- **FR-PO-BOUND-005**: `platform-configuration` definirá valores e limites editáveis pela aplicação; configurações e credenciais de infraestrutura permanecem externas.
- **FR-PO-BOUND-006**: `tenant-support-access` definirá acesso excepcional ao conteúdo de tenant; diagnóstico estrutural NÃO DEVE concedê-lo implicitamente.
- **FR-PO-BOUND-007**: Esta feature NÃO DEVE oferecer terminal, consulta arbitrária, edição direta de registros, execução livre de comandos, migração manual, backup ou restauração.
- **FR-PO-BOUND-008**: Deploy do artefato executável, servidor, proxy reverso, backup, restauração e intervenção sobre falhas estruturais pertencem aos procedimentos externos de infraestrutura.
- **FR-PO-BOUND-009**: Conteúdo funcional, validações de negócio e relatórios de módulos pertencem às respectivas features e não compõem a saúde estrutural do tenant.

### Decisões de Infraestrutura Auditáveis

- **FR-PO-INFRA-SCHED**: Avaliações de saúde e a fila compartilhada de provisionamentos e migrações DEVEM ocorrer sem depender de sessão ativa, segundo políticas observáveis e o limite configurado de concorrência.
- **FR-PO-INFRA-LOCK**: Provisionamentos e migrações incompatíveis DEVEM ser serializados por escopo em todas as instâncias, com detecção segura de execução abandonada.
- **FR-PO-INFRA-IDEMP**: Intenções, processos automáticos e etapas DEVEM possuir identidades estáveis que impeçam duplicidade por repetição, concorrência ou resposta incerta.
- **FR-PO-INFRA-RECOVERY**: Estado e progresso conhecidos pela aplicação DEVEM sobreviver a reinício e troca de instância sem inventar sucesso nem perder evidências.
- **FR-PO-INFRA-CLOCK**: Ordenação, duração e correlação DEVEM usar referência temporal confiável e falhar com segurança diante de divergência relevante.
- **FR-PO-INFRA-CAPACITY**: Falta de capacidade conhecida DEVE impedir novas etapas com segurança, preservar estados já válidos e produzir diagnóstico antes de causar corrupção ou mistura de escopos.

### Key Entities

- **Operational Health Snapshot**: avaliação temporal dos componentes e escopos conhecidos pela aplicação, com estado, impacto, idade e evidências seguras.
- **Provisioning Process**: intenção durável de preparar o armazenamento de uma conta nova, com protocolo, etapa, tentativas, estado e resultado.
- **Provisioning Step**: unidade verificável e repetível da preparação, incluindo ponto seguro e efeito observado.
- **Storage Version Status**: comparação entre versão esperada, global e tenant, com compatibilidade, migração e bloqueio.
- **Migration Execution**: evidência automática da aplicação ordenada de um update em um armazenamento determinado.
- **Operational Occurrence**: condição conhecida que exige acompanhamento interno ou intervenção externa, com severidade, estado, correlação e histórico.
- **Operational Audit Event**: evidência protegida das consultas administrativas, mudanças de ocorrência e resultados automáticos.

## Success Criteria

### Measurable Outcomes

- **SC-PO-001**: Em pelo menos 95% dos cenários operacionais visíveis, um administrador autorizado identifica em até 5 minutos se a falha é global, compartilhada ou restrita a um tenant.
- **SC-PO-002**: Em 100% dos testes, ator sem a chave específica não consulta a visão administrativa correspondente.
- **SC-PO-003**: Em 100% dos estados desconhecidos ou desatualizados, a plataforma não os apresenta como saudáveis nem libera escopo baseado neles.
- **SC-PO-004**: Em 100% das repetições, reinícios e concorrências de provisionamento, uma intenção produz no máximo um armazenamento e preserva etapas comprovadamente concluídas.
- **SC-PO-005**: Pelo menos 95% dos provisionamentos de uma conta vazia terminam em até 5 minutos no ambiente operacional de referência.
- **SC-PO-006**: Em 100% das falhas definitivas de provisionamento, a conta permanece indisponível e nenhuma ação de interface pode simular prontidão ou executar correção estrutural.
- **SC-PO-007**: Em 100% dos deploys testados, a migração global ocorre antes da disponibilidade da aplicação e os tenants são processados somente após compatibilidade global.
- **SC-PO-008**: Em 100% das falhas globais de migração, toda a aplicação permanece bloqueada para intervenção externa.
- **SC-PO-009**: Em 100% das falhas de migração de tenant, somente o tenant afetado permanece bloqueado e os demais elegíveis continuam o processamento.
- **SC-PO-010**: Em 100% dos testes, nenhuma migração falha pode ser repetida, corrigida, revertida ou marcada como concluída pela interface.
- **SC-PO-011**: Em 100% das atualizações testadas, global e tenants somente aceitam operações comuns sobre a versão estrutural exata esperada.
- **SC-PO-012**: Em 100% dos testes, a aplicação não cria, agenda, inventaria, testa, exclui ou restaura backups.
- **SC-PO-013**: Em 100% das mudanças administrativas de ocorrência, existe auditoria completa ou a mudança não é confirmada.
- **SC-PO-014**: Nenhuma visão, alerta, nota ou auditoria desta feature expõe credencial, segredo, localização física desnecessária ou conteúdo funcional de tenant.
- **SC-PO-015**: Todas as jornadas visíveis podem ser concluídas apenas por teclado e mantêm estado, severidade, alvo, impacto e responsabilidade externa compreensíveis sem depender somente de cor.
- **SC-PO-016**: Em 100% dos deploys testados, a quantidade de migrações simultâneas de tenant não ultrapassa o limite configurado e, com o padrão inicial, no máximo um tenant é migrado por vez.
- **SC-PO-017**: Em 100% das criações realizadas com migrações já enfileiradas, o provisionamento novo aguarda depois das operações existentes, não excede a concorrência configurada e não é apresentado como falho.
- **SC-PO-018**: Em 100% dos testes da visão do criador, somente os quatro estados definidos e orientações seguras são apresentados, sem topologia, versões, etapas técnicas ou logs.
- **SC-PO-019**: Em 100% dos testes, ocorrência de tenant fica consultável na área administrativa quando a aplicação está disponível, falha global permanece diagnosticável externamente e nenhum e-mail ou webhook operacional é enviado.
