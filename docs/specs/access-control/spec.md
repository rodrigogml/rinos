# Feature Specification: Controle de Acesso por Grupos e Chaves

**Feature**: `access-control`
**Created**: 2026-07-19
**Last Clarified**: 2026-09-01
**Status**: ciclo documental consolidado; backlog criado; schema e código não iniciados

## Escopo

Esta feature define a autorização por negação padrão do Rinos, baseada em chaves de acesso registradas pelos módulos,
regras explícitas de permissão ou bloqueio e grupos sem aninhamento. O modelo opera separadamente no contexto global da
plataforma e no contexto de cada tenant, permite administração por software, preserva auditoria e explica por que uma
operação foi permitida ou negada.

Papéis como colaborador, parceiro externo, administrador da conta e administrador do sistema identificam o ator, mas
não concedem acesso. Direitos de plano e autorização também são controles independentes: a operação somente pode
prosseguir quando ambos permitirem seu efeito. A seleção e a validação do tenant ativo pertencem a
`tenant-context-isolation`; esta feature recebe o contexto já identificado e confirma que as regras usadas pertencem
exatamente a ele.

## Clarifications

### Session 2026-08-16

- Q: Chave global condicionada a plano sempre consulta o plano pessoal? -> A: Não. O requisito declara `PERSONAL` ou `TENANT`; operações administrativas globais sem requisito não consultam o contrato pessoal do ator.

### Session 2026-09-01

- Q: Qual fator forte deve preparar o fundador administrativo inicial? -> A: O usuário definido pelo e-mail exclusivo de bootstrap em `application.properties` conclui TOTP obrigatório ainda no fluxo de criação da sua identidade global. A conclusão desse cadastro não cria tenant; o bootstrap global continua sendo uma etapa posterior, idempotente e separada.

### Session 2026-07-19

- Q: Usuários podem receber chaves diretamente além de participar de grupos? -> A: Sim; o acesso efetivo combina
  regras diretas e regras provenientes de grupos, mantendo cada origem explicitamente auditável.

### Session 2026-07-20

- Q: Grupos de acesso podem ser aninhados e como as chaves serão organizadas para navegação? -> A: Grupos de acesso não serão aninhados; o catálogo de chaves terá categorias hierárquicas próprias para organização e UI, sem herança ou efeito na autorização.

### Session 2026-07-24

- Q: Como criar o primeiro administrador global sem transformar o primeiro usuário cadastrado em superusuário nem exigir intervenção direta no banco? -> A: Uma definição exclusiva de origem `PROPERTY_FILE` no `application.properties` indicará o e-mail autorizado ao bootstrap, com `admin@rinos.com.br` como padrão de código. O usuário deverá concluir o cadastro normal, confirmar o e-mail e configurar TOTP obrigatório. Somente enquanto o bootstrap nunca tiver sido concluído, o sistema atribuirá atomicamente a identificação de ator e o grupo global protegido, registrará auditoria e gravará marcador permanente; alterações posteriores da propriedade nunca concederão acesso.

### Session 2026-08-14

- Q: Qual é o alcance de uma regra de bloqueio? -> A: O bloqueio aplica-se somente à chave exata e ao contexto exato
  da regra. Bloqueio global não se propaga para tenants, bloqueio de um tenant não alcança outro e categorias não
  propagam efeito autorizativo.
- Q: Como representar a troca entre permissão e bloqueio para a mesma origem? -> A: Para a mesma origem, chave e
  contexto haverá somente uma regra corrente. Trocar o efeito substitui atomicamente a regra corrente e preserva a
  alteração no histórico; regras de origens distintas podem divergir e qualquer bloqueio vigente prevalece.
- Q: Permissões e bloqueios podem ter vigência? -> A: Ambos aceitam início e término opcionais. Regra futura ou
  expirada não produz efeito, mas permanece auditável.
- Q: Como preservar continuidade administrativa? -> A: Toda mudança relevante será validada pelo resultado efetivo.
  A operação será rejeitada atomicamente se eliminar o último administrador mínimo ativo e com 2FA compatível;
  grupos protegidos não podem bloquear nem perder suas chaves mínimas versionadas.
- Q: Como autorizar uma operação que exige várias capacidades? -> A: A operação declara todas as chaves obrigatórias.
  Cada chave precisa de ao menos uma permissão vigente e nenhuma pode possuir bloqueio vigente no mesmo contexto.

### Session 2026-08-15

- Q: As chaves globais devem ser carregadas no login e as chaves de tenant na seleção da conta? -> A: Não. Login
  autentica a identidade e seleção estabelece apenas uma referência mínima do contexto da área de trabalho. Toda
  operação global ou de tenant resolve autorização em tempo de execução.
- Q: Como duas abas da mesma sessão podem operar em tenants diferentes? -> A: Cada `UI`/área de trabalho mantém seu
  próprio tenant e associação. `HttpSession`, `VaadinSession`, `RFWSessionState`, `SecurityContext` e principal não
  armazenam um tenant ativo compartilhado nem chaves efetivas.
- Q: Como equilibrar consulta, memória e efetivação de mudanças? -> A: Cache local limitado guarda fontes imutáveis por
  sujeito e contexto. Toda nova operação confirma a revisão persistida; vigências são reaplicadas pelo relógio e a
  entrada não ultrapassa a próxima fronteira temporal. Notificações apenas antecipam invalidação.

## User Scenarios & Testing

### User Story 1 - Autorizar uma operação (Priority: P1)

O sistema avalia condições estruturais, direitos aplicáveis, garantia da autenticação e todas as chaves exigidas antes
de executar uma operação protegida.

**Why this priority**: é o núcleo de segurança do sistema e impede acesso implícito por papel, interface, rota ou
conhecimento de identificadores.

**Independent Test**: executar a mesma operação variando contexto, permissão, bloqueio e vigência e comprovar que ela
somente é autorizada quando todas as condições são satisfeitas.

**Acceptance Scenarios**:

1. **Given** participante ativo com permissão vigente na conta correta, **When** solicita operação protegida, **Then**
   a operação é autorizada
2. **Given** participante sem permissão para a chave, **When** solicita a mesma operação, **Then** o acesso é negado
3. **Given** permissão direta e bloqueio vigente proveniente de grupo para a mesma chave, **When** a operação é
   avaliada, **Then** o bloqueio prevalece e a operação é negada
4. **Given** permissão equivalente em outro tenant ou no contexto global, **When** solicita a operação no tenant atual,
   **Then** o acesso é negado
5. **Given** operação que exige várias chaves, **When** uma delas está ausente ou bloqueada, **Then** nenhum efeito da
   operação é executado
6. **Given** ator identificado como administrador sem regra de permissão aplicável, **When** solicita operação
   administrativa, **Then** o acesso é negado

---

### User Story 2 - Administrar grupos e regras de um tenant (Priority: P1)

Um participante autorizado cria grupos do tenant, associa participantes e mantém regras de permissão ou bloqueio
usando exclusivamente chaves registradas e disponíveis naquele contexto.

**Why this priority**: torna o modelo administrável sem alteração de código e permite exceções restritivas sem
confundir papel com autorização.

**Independent Test**: criar grupo, registrar permissões e bloqueios, associar participantes e verificar que o efeito
não alcança outro tenant.

**Acceptance Scenarios**:

1. **Given** administrador autorizado, **When** cria grupo válido e registra regras, **Then** o grupo fica utilizável
   somente naquele tenant
2. **Given** tentativa de usar chave global, inexistente ou de outro contexto, **When** salva a regra, **Then** a
   alteração é rejeitada sem efeito parcial
3. **Given** participante removido de grupo permissivo, **When** tenta nova operação dependente dele, **Then** a
   autorização é reavaliada e pode ser revogada imediatamente
4. **Given** regra permissiva direta e grupo bloqueador, **When** o administrador consulta a matriz, **Then** a UI
   apresenta a contradição e o resultado efetivo negado
5. **Given** alteração que eliminaria o último administrador mínimo apto, **When** tenta confirmá-la, **Then** a operação
   é rejeitada atomicamente com prévia de impacto segura

---

### User Story 3 - Administrar acessos globais (Priority: P2)

Um administrador do sistema com permissões suficientes mantém grupos, associações e regras globais sem herdar acesso
ao conteúdo de tenants.

**Why this priority**: separa administração da plataforma da administração dos tenants e evita superusuário implícito.

**Independent Test**: permitir uma chave global a uma identidade e comprovar que ela autoriza operação da plataforma,
mas não leitura ou alteração de dados de tenant.

**Acceptance Scenarios**:

1. **Given** usuário com permissão global vigente, **When** executa operação global correspondente, **Then** o sistema
   autoriza sem selecionar tenant
2. **Given** a mesma identidade sem participação ou acesso excepcional em um tenant, **When** tenta ler dados dele,
   **Then** o sistema nega
3. **Given** regra global de bloqueio, **When** a identidade opera em tenant onde possui permissão própria, **Then** o
   bloqueio global não participa daquela decisão
4. **Given** alteração que retiraria a última administração global apta, **When** é confirmada, **Then** a transação é
   rejeitada

---

### User Story 4 - Auditar e explicar o acesso efetivo (Priority: P2)

Um administrador autorizado consulta a decisão efetiva para entender condições estruturais, permissões, bloqueios,
vigências e controles externos à chave sem receber informações de outro contexto.

**Why this priority**: reduz erros administrativos, sustenta suporte e torna conflitos previsíveis sem enfraquecer o
isolamento.

**Independent Test**: consultar a explicação de uma operação permitida e outra negada e verificar a origem de cada
regra, o resultado por chave e o motivo decisivo seguro.

**Acceptance Scenarios**:

1. **Given** acesso permitido por grupo, **When** administrador consulta a explicação, **Then** identifica contexto,
   chave, origem, efeito e vigência
2. **Given** dois grupos permitem e um grupo bloqueia, **When** a explicação é consultada, **Then** todas as origens
   visíveis são apresentadas e o bloqueio é indicado como decisivo
3. **Given** operação negada por plano, identidade bloqueada ou autenticação insuficiente, **When** a explicação é
   gerada, **Then** o motivo não é rotulado como bloqueio de chave
4. **Given** consulta sobre outro tenant, **When** o solicitante não pode conhecê-lo, **Then** grupos, usuários e regras
   daquele contexto não são revelados

---

### User Story 5 - Preservar continuidade administrativa (Priority: P1)

O sistema impede que alterações comuns deixem a plataforma ou um tenant sem administrador mínimo ativo e com fator
forte compatível.

**Why this priority**: evita bloqueio administrativo causado por revogação, bloqueio, expiração, suspensão ou perda de
2FA.

**Independent Test**: tentar retirar, bloquear ou expirar o último caminho efetivo de administração e comprovar
rejeição integral da alteração.

**Acceptance Scenarios**:

1. **Given** dois administradores mínimos aptos, **When** um perde acesso, **Then** a alteração pode ser concluída e o
   outro preserva a continuidade
2. **Given** somente um administrador mínimo apto, **When** uma mudança eliminaria sua aptidão, **Then** a operação é
   rejeitada sem alteração parcial
3. **Given** grupo protegido, **When** alguém tenta bloquear ou remover uma chave mínima dele, **Then** a operação é
   rejeitada
4. **Given** ausência administrativa causada por falha ou evento externo já ocorrido, **When** a recuperação
   excepcional é iniciada, **Then** aplica-se o fluxo de `system-directory-administration`, não um desvio da validação
   comum

### Edge Cases

- Permissão é removida ou bloqueio é criado durante uma sessão ativa.
- O mesmo usuário pertence a vários grupos com regras contraditórias.
- Uma chave é descontinuada enquanto permanece referenciada por regras históricas.
- O plano da conta deixa de liberar a funcionalidade associada à chave.
- Duas alterações concorrentes modificam a mesma origem, chave e contexto.
- Uma regra possui início futuro, término exato no instante da avaliação ou intervalo inválido.
- Um administrador tenta permitir a si próprio uma chave que não pode administrar.
- A última capacidade administrativa mínima depende simultaneamente de regra direta e grupo.
- A expiração futura de regra ou de fator forte deixaria o contexto sem administrador apto.
- Uma conta é suspensa com regras vigentes.
- Uma operação assíncrona foi aceita antes de uma revogação, mas ainda não começou.
- Uma operação aceita começou antes da revogação e tenta repetir ou encadear novo efeito depois dela.
- O usuário indicado para o bootstrap ainda não se cadastrou, não confirmou o e-mail ou não configurou fator forte.
- Duas instâncias avaliam simultaneamente a elegibilidade do primeiro administrador global.
- A propriedade do e-mail inicial é alterada depois de o bootstrap ter sido concluído.
- Uma restauração contém o marcador de bootstrap concluído, mas não contém administrador global apto.
- O cache de autorização está indisponível, atrasado ou contém versão anterior das regras.
- Duas abas da mesma sessão operam em tenants diferentes e uma delas troca de conta.
- Uma regra futura inicia ou uma regra vigente expira sem qualquer mutação ou incremento de revisão naquele instante.
- Uma regra de grupo altera o acesso de muitos sujeitos e uma notificação de invalidação é perdida ou chega fora de ordem.

## Requirements

### Chaves de Acesso e Categorias

- **FR-ACL-KEY-001**: Toda operação protegida DEVE declarar uma ou mais chaves de acesso estáveis e o contexto global
  ou de tenant em que serão avaliadas.
- **FR-ACL-KEY-002**: Cada chave DEVE ser registrada pelo sistema ou módulo proprietário com código interno único e
  imutável, nome e descrição localizados, escopo, categoria, módulo proprietário, estado e referência aos requisitos
  consumidores.
- **FR-ACL-KEY-003**: Administradores NÃO DEVEM criar códigos arbitrários; somente selecionar chaves registradas e
  administráveis no contexto.
- **FR-ACL-KEY-004**: Códigos técnicos NÃO DEVEM ser apresentados ao usuário final; pesquisa, seleção e explicação
  DEVEM usar nomes e descrições localizados.
- **FR-ACL-KEY-005**: Chave global NÃO DEVE ser satisfeita por regra de tenant, e chave de tenant NÃO DEVE produzir
  efeito no contexto global nem em outro tenant.
- **FR-ACL-KEY-006**: Chave desativada ou retirada do catálogo NÃO DEVE autorizar novas operações, mas suas referências
  históricas DEVEM permanecer explicáveis.
- **FR-ACL-KEY-007**: Mudança incompatível no significado de uma chave DEVE criar novo código, preservando o contrato
  anterior para auditoria e migração explícita.
- **FR-ACL-KEY-008**: O catálogo DEVE impedir duplicidade semântica entre módulos e identificar chaves
  administrativas mínimas globais e de tenant.
- **FR-ACL-KEY-009**: Cada categoria DEVE possuir identificador estável, nome e descrição localizados, ordem, estado e
  categoria pai opcional, formando árvore sem ciclos.
- **FR-ACL-KEY-010**: Cada chave DEVE possuir uma categoria canônica; busca pode apresentá-la em resultados adicionais
  sem mudar essa classificação.
- **FR-ACL-KEY-011**: Categoria, posição hierárquica, nome semelhante ou ancestralidade NÃO DEVEM conceder, herdar,
  bloquear ou ampliar acesso.
- **FR-ACL-KEY-012**: Renomear ou mover categoria NÃO DEVE alterar códigos de chaves, regras, grupos ou decisões.

### Grupos

- **FR-ACL-GRP-001**: Grupo DEVE possuir nome único no contexto, descrição, escopo, estado, proteção opcional e versão
  explícita de seu conjunto mínimo quando protegido.
- **FR-ACL-GRP-002**: Grupo de tenant DEVE pertencer a exatamente um tenant e aceitar somente associações e regras
  daquele tenant.
- **FR-ACL-GRP-003**: Grupo global DEVE aceitar somente identidades globais e regras sobre chaves globais.
- **FR-ACL-GRP-004**: Grupos NÃO DEVEM conter outros grupos; cada associação entre sujeito e grupo é direta.
- **FR-ACL-GRP-005**: Participação em grupo DEVE ser auditável, possuir estado e aceitar vigência opcional quando o
  caso de uso exigir acesso temporário.
- **FR-ACL-GRP-006**: Desativar grupo ou remover associação DEVE deixar de produzir efeito em novas decisões sem
  apagar o histórico.
- **FR-ACL-GRP-007**: Grupo protegido NÃO DEVE aceitar regra de bloqueio nem perda de permissão para suas chaves
  mínimas versionadas.
- **FR-ACL-GRP-008**: Nova chave de catálogo NÃO DEVE ser incluída automaticamente em grupo protegido ou comum.

### Regras de Acesso

- **FR-ACL-RULE-001**: Regra DEVE relacionar origem, chave, efeito `PERMITIR` ou `BLOQUEAR`, contexto, estado, início
  opcional e término opcional.
- **FR-ACL-RULE-002**: Regra direta global DEVE ter como beneficiária uma identidade global; regra direta de tenant
  DEVE ter como beneficiária a associação do usuário àquele tenant.
- **FR-ACL-RULE-003**: Regra de grupo DEVE usar grupo e chave do mesmo escopo e contexto.
- **FR-ACL-RULE-004**: Para a mesma origem, chave e contexto DEVE existir no máximo uma regra corrente; trocar o
  efeito DEVE substituir a regra atomicamente e preservar o histórico da mudança.
- **FR-ACL-RULE-005**: Permissões e bloqueios de origens distintas PODEM coexistir e divergir.
- **FR-ACL-RULE-006**: Regra somente produz efeito quando ativa e dentro de sua vigência; início inclusivo e término
  exclusivo DEVEM usar instante UTC.
- **FR-ACL-RULE-007**: Intervalo com término anterior ou igual ao início DEVE ser rejeitado.
- **FR-ACL-RULE-008**: Regra futura, expirada, desativada ou associada a chave ou origem inativa NÃO DEVE produzir
  efeito, mas DEVE permanecer no histórico.
- **FR-ACL-RULE-009**: A versão inicial NÃO DEVE oferecer curingas, regra por categoria, herança entre chaves nem
  políticas booleanas arbitrárias.
- **FR-ACL-RULE-010**: Toda criação, troca de efeito, alteração de vigência, desativação ou remoção lógica DEVE
  registrar autor ou origem sistêmica, contexto, valores anteriores e posteriores, instante e justificativa quando
  exigida.

### Decisão de Autorização

- **FR-ACL-AUTHZ-001**: Toda decisão DEVE começar negada e somente autorizar quando identidade, contexto, associação
  quando aplicável, estado da conta, direito de plano, garantia de autenticação e todas as chaves exigidas forem
  válidos.
- **FR-ACL-AUTHZ-002**: Para cada chave exigida DEVE existir ao menos uma regra vigente `PERMITIR` e nenhuma regra
  vigente `BLOQUEAR` entre regras diretas e todos os grupos ativos do sujeito.
- **FR-ACL-AUTHZ-003**: Um único bloqueio vigente DEVE prevalecer sobre qualquer quantidade de permissões vigentes para
  a mesma chave e contexto.
- **FR-ACL-AUTHZ-004**: Bloqueio de chave NÃO DEVE ser confundido com o estado global de identidade bloqueada; este
  último invalida todos os contextos independentemente das regras.
- **FR-ACL-AUTHZ-005**: Operação composta DEVE declarar todas as chaves correspondentes aos efeitos produzidos e
  aplicar semântica cumulativa `TODAS`; ausência ou bloqueio de uma chave nega toda a operação.
- **FR-ACL-AUTHZ-006**: Uma regra global NÃO DEVE interferir em decisão de tenant e uma regra de tenant NÃO DEVE
  interferir em decisão global ou de outro tenant.
- **FR-ACL-AUTHZ-007**: Direitos de plano, estado, contexto e garantia de autenticação DEVEM ser avaliados como gates
  independentes e não podem ser convertidos em regras de bloqueio.
- **FR-ACL-AUTHZ-019**: Gate de plano DEVE declarar `subjectScope` e código; contexto global NÃO DEVE inferir escopo
  pessoal, e contrato de um titular NÃO DEVE avaliar outro.
- **FR-ACL-AUTHZ-008**: Interface, facade, serviço, trabalho assíncrono e qualquer outra entrada DEVEM aplicar o mesmo
  contrato de autorização; ocultar ação na UI não substitui a verificação no serviço.
- **FR-ACL-AUTHZ-009**: Trabalhos assíncronos originados por usuário DEVEM revalidar identidade, contexto, associação,
  plano, permissões e bloqueios imediatamente antes de iniciar.
- **FR-ACL-AUTHZ-010**: Operação aceita e já iniciada PODE terminar conforme a política de isolamento da feature
  originadora, mas revogação DEVE impedir repetição, retomada ou novo efeito ainda não iniciado.
- **FR-ACL-AUTHZ-011**: Operações autônomas DEVEM usar origem sistêmica explícita, limitada e auditável, sem simular
  usuário ou participar de grupos humanos. A origem DEVE estar previamente registrada com operação, contextos e
  chaves exatos; quando compatível e ativa, ela constitui a fonte permissiva sistêmica de cada chave, sem consultar ou
  combinar regras humanas.
- **FR-ACL-AUTHZ-012**: Cache, timeout, falha de persistência ou indisponibilidade interna NUNCA DEVEM ampliar acesso;
  quando a decisão segura não puder ser produzida, o resultado DEVE ser negado.
- **FR-ACL-AUTHZ-013**: Login e seleção de tenant NÃO DEVEM carregar ou congelar chaves, regras ou decisões efetivas no
  principal, `HttpSession`, `VaadinSession`, `RFWSessionState` ou outro estado compartilhado entre áreas de trabalho.
- **FR-ACL-AUTHZ-014**: Cada área de trabalho DEVE conservar somente a referência mínima do contexto selecionado e toda
  operação DEVE revalidar ator, contexto e associação; duas áreas da mesma sessão podem operar em tenants distintos sem
  alterar uma à outra.
- **FR-ACL-AUTHZ-015**: Cache ACL DEVE ser local, limitado e particionado por sujeito e contexto; NÃO DEVE materializar
  como unidade a ACL completa do tenant nem armazenar decisão final como autoridade.
- **FR-ACL-AUTHZ-016**: Antes de reutilizar snapshot, cada operação independente DEVE confirmar a revisão monotônica
  persistida. Compartilhamento de uma leitura somente é permitido dentro da mesma operação composta.
- **FR-ACL-AUTHZ-017**: Snapshot DEVE reavaliar vigências no instante UTC corrente e tornar-se inelegível, no máximo,
  na próxima fronteira temporal conhecida, mesmo sem mudança de revisão.
- **FR-ACL-AUTHZ-018**: Invalidação local pós-commit e notificação entre instâncias PODEM antecipar descarte, mas perda,
  atraso ou reordenação de notificação NÃO DEVEM comprometer a correção garantida pela revisão persistida.

### Explicação, Auditoria e Suporte

- **FR-ACL-EXP-001**: A decisão DEVE produzir resultado geral e resultado individual para cada chave exigida.
- **FR-ACL-EXP-002**: Explicação administrativa DEVE apresentar condições estruturais, permissões e suas origens,
  bloqueios e suas origens, vigências, resultado por chave e condição decisiva.
- **FR-ACL-EXP-003**: Impedimento por plano, identidade, conta, contexto ou garantia de autenticação DEVE permanecer
  distinguível de bloqueio ou ausência de permissão.
- **FR-ACL-EXP-004**: Mensagem pública DEVE ser segura e não revelar catálogo, grupo, usuário, regra ou existência de
  outro tenant além do que o solicitante pode conhecer.
- **FR-ACL-EXP-005**: Tentativas negadas de operações sensíveis e toda mutação administrativa DEVEM ser auditáveis
  conforme criticidade, sem registrar segredo de autenticação ou dado desnecessário.
- **FR-ACL-EXP-006**: Acesso de suporte DEVE derivar somente as capacidades efetivas de leitura do solicitante após
  aplicar seus bloqueios e demais gates; permissões próprias do operador não podem ser combinadas dentro do tenant.

### Continuidade Administrativa e Bootstrap

- **FR-ACL-CONT-001**: O catálogo DEVE identificar o conjunto explícito e versionado de chaves administrativas mínimas
  globais e de tenant.
- **FR-ACL-CONT-002**: Toda alteração de regra, grupo, associação, estado da identidade ou associação, vigência ou fator
  forte que possa afetar administração DEVE validar o resultado efetivo após a mudança.
- **FR-ACL-CONT-003**: A alteração DEVE ser rejeitada atomicamente quando deixaria o contexto sem ao menos um
  administrador mínimo ativo, com todas as chaves mínimas e TOTP ou passkey compatível, agora ou em qualquer intervalo
  futuro determinável pelas vigências conhecidas no momento da alteração.
- **FR-ACL-CONT-004**: A validação DEVE considerar regras diretas, grupos, bloqueios, expirações, suspensão da
  associação e perda de fator forte, avaliando cada fronteira relevante de início e término, e não apenas o instante
  da requisição.
- **FR-ACL-CONT-005**: Recuperação administrativa excepcional somente DEVE ser usada quando a ausência administrativa
  já existir por falha ou evento externo, nunca para contornar uma rejeição comum.
- **FR-ACL-BOOT-001**: O bootstrap global DEVE usar exclusivamente o e-mail configurado no `application.properties`,
  com `admin@rinos.com.br` como padrão documental, sem tornar o primeiro cadastrado administrador.
- **FR-ACL-BOOT-002**: O bootstrap somente DEVE ocorrer para identidade ativa, e-mail confirmado e TOTP confirmado,
  enquanto o marcador permanente indicar que nunca foi concluído. O enrollment obrigatório pertence ao fluxo de
  criação da identidade fundadora e não cria tenant nem antecipa a concessão administrativa.
- **FR-ACL-BOOT-003**: Conclusão do bootstrap DEVE atribuir atomicamente identificação de ator, associação ao grupo
  global protegido, auditoria e marcador permanente, com proteção contra concorrência entre instâncias.
- **FR-ACL-BOOT-004**: Alterar o e-mail configurado após a conclusão NÃO DEVE conceder, transferir nem remover acesso.
- **FR-ACL-BOOT-005**: O fundador de tenant DEVE receber associação ao grupo inicial protegido do tenant e suas regras
  explícitas; papel de fundador ou administrador não concede permissão implícita.

### Administração e Integração

- **FR-ACL-ADM-001**: Manter catálogo, categorias, grupos, associações, regras e explicações DEVE exigir chaves
  administrativas específicas do mesmo contexto.
- **FR-ACL-ADM-002**: Administrador global NÃO DEVE receber acesso automático ao conteúdo de tenants.
- **FR-ACL-ADM-003**: Operações sensíveis definidas pelos módulos DEVEM poder exigir autenticação recente e TOTP ou
  passkey além das chaves.
- **FR-ACL-ADM-004**: Alteração concorrente DEVE usar controle otimista ou equivalente e rejeitar sobrescrita
  silenciosa.
- **FR-ACL-ADM-005**: Os módulos consumidores DEVEM usar o catálogo canônico e não duplicar a semântica das chaves em
  suas especificações, código ou interface.
- **FR-ACL-ADM-006**: Registro modular de chaves DEVE ser idempotente, detectar colisão de código ou semântica e falhar
  fechado quando uma operação protegida exigir chave não registrada.

### Key Entities

- **Access Key Descriptor**: contrato estável da chave com código interno, textos localizados, escopo, categoria,
  módulo proprietário, estado e rastreabilidade para requisitos consumidores.
- **Access Key Category**: nó hierárquico de navegação e pesquisa sem efeito autorizativo.
- **Access Group**: agrupamento global ou de tenant, sem aninhamento, que reúne sujeitos e regras.
- **Group Membership**: associação auditável entre identidade ou participação de tenant e grupo do mesmo contexto.
- **Access Rule**: regra corrente de uma origem sobre uma chave, com efeito permitir ou bloquear, estado e vigência.
- **Access Rule History**: registro imutável das mudanças de regra, inclusive troca de efeito e vigência.
- **Authorization Context**: contexto global ou tenant explicitamente identificado e validado.
- **Authorization Request**: ator, associação aplicável, contexto, operação, chaves obrigatórias e garantia atual da
  autenticação.
- **Authorization Decision**: resultado geral, resultado por chave, motivos seguros, permissões, bloqueios decisivos e
  demais gates.
- **Access Explanation**: visão administrativa auditável e limitada ao contexto que o consulente pode conhecer.
- **Protected Group Baseline**: versão explícita das chaves administrativas mínimas exigidas de grupo protegido.
- **Bootstrap Completion Marker**: registro permanente e idempotente da conclusão do primeiro administrador global.

## Success Criteria

### Measurable Outcomes

- **SC-ACL-001**: 100% das operações protegidas inventariadas declaram contexto e chaves canônicas no catálogo.
- **SC-ACL-002**: Todos os testes de ausência de permissão resultam em negação sem efeito parcial.
- **SC-ACL-003**: Permissão direta vigente e permissão por grupo vigente autorizam isoladamente no contexto correto.
- **SC-ACL-004**: Bloqueio direto vence permissão de grupo e bloqueio de grupo vence permissão direta em todos os casos
  automatizados.
- **SC-ACL-005**: Um grupo bloqueador vence múltiplos grupos permissivos sem depender da ordem de avaliação.
- **SC-ACL-006**: Regras globais, de outro tenant, futuras, expiradas ou inativas não alteram a decisão corrente.
- **SC-ACL-007**: Troca de efeito preserva uma única regra corrente e histórico completo, sem janela de dupla vigência.
- **SC-ACL-008**: Operação composta falha integralmente quando ao menos uma chave estiver ausente ou bloqueada.
- **SC-ACL-009**: Falta de direito de plano, identidade bloqueada e garantia insuficiente são explicadas sem serem
  registradas como bloqueio de chave.
- **SC-ACL-010**: Toda tentativa de eliminar o último administrador mínimo apto é rejeitada atomicamente.
- **SC-ACL-011**: Revogação, bloqueio ou expiração afeta novas operações e trabalhos ainda não iniciados no primeiro
  acesso após a confirmação da mudança.
- **SC-ACL-012**: Acesso de suporte nunca excede as capacidades efetivas de leitura do solicitante.
- **SC-ACL-013**: Explicações não revelam identidades, grupos, regras ou existência de outro tenant.
- **SC-ACL-014**: Alterar papel sem alterar regras não muda o resultado de autorização.
- **SC-ACL-015**: Ocultar ou exibir ação na interface corresponde à decisão do serviço, mas tentativa direta continua
  sendo revalidada pelo backend.
- **SC-ACL-016**: Falha de cache ou indisponibilidade interna resulta em negação e evidência operacional, nunca em
  permissão ampliada.
- **SC-ACL-017**: O bootstrap global é concluído no máximo uma vez, somente para `admin@rinos.com.br` ou substituto
  explicitamente configurado antes da conclusão, com fator forte e auditoria.
- **SC-ACL-018**: Usuários autorizados localizam uma chave por nome ou descrição sem visualizar nem informar seu código
  técnico.
- **SC-ACL-019**: Em 100% dos testes multiaba, decisões nos tenants A e B usam sujeitos e snapshots distintos, e trocar
  uma área não altera o contexto nem a autorização da outra.
- **SC-ACL-020**: Em 100% dos testes de fronteira temporal, regra futura ou expirada muda de elegibilidade no instante
  correto mesmo sem mutação, scheduler ou incremento de revisão.
- **SC-ACL-021**: Em 100% dos testes com notificação perdida, atrasada ou fora de ordem, a próxima operação confirma a
  revisão persistida e nunca usa snapshot obsoleto para ampliar acesso.
