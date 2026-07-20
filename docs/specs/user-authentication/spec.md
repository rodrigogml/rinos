# Feature Specification: Autenticação e Recuperação do Usuário

**Feature**: `user-authentication`
**Created**: 2026-07-19
**Status**: Clarified — pronta para planejamento

## Escopo

Esta feature permite que um usuário ativo prove sua identidade por senha, passkey ou Google, mantenha e encerre sessões, configure autenticação de dois fatores e recupere o acesso com segurança.

Inclui proteção contra automação e descoberta de usuários, gestão dos métodos de autenticação, vínculo explícito com Google, recuperação de senha e fatores, auditoria e elevação de segurança para acessos administrativos.

Não inclui criação da identidade inicial, configuração dos dados pessoais do usuário, criação ou seleção de contas, definição de papéis, grupos ou chaves de acesso, nem autorização sobre dados de tenant.

## Clarifications

### Session 2026-07-19

- Q: Qual duração máxima, timeout por inatividade e política de “lembrar-me” devem ser adotados? -> A: Sessão normal de 12 horas com 30 minutos de inatividade; “lembrar-me” por 30 dias com 7 dias de inatividade; reautenticação após 15 minutos para operações sensíveis ou administrativas.
- Q: Qual deve ser o gatilho padrão do Turnstile no login? -> A: Após 3 falhas relacionadas ao mesmo e-mail informado ou IP em 15 minutos, permanecendo obrigatório até 15 minutos sem nova falha; parâmetros configuráveis.
- Q: Quantos códigos de recuperação devem ser emitidos e quando expiram? -> A: 10 códigos de uso único, sem expiração fixa; o conjunto é invalidado ao gerar outro, desativar o 2FA ou concluir recuperação que altere os fatores.

## User Scenarios & Testing

### User Story 1 - Entrar com senha (Priority: P1)

Um usuário ativo informa seu e-mail e senha e, quando aplicável, conclui o segundo fator para acessar seu Painel de Usuário.

**Why this priority**: é o método básico de acesso para usuários com credencial local e também sustenta recuperação e vínculo de outros métodos.

**Independent Test**: partir de um usuário ativo com senha local, autenticar com dados válidos e verificar que apenas uma sessão do próprio usuário foi criada.

**Acceptance Scenarios**:

1. **Given** usuário ativo com senha válida, **When** conclui as verificações exigidas, **Then** o sistema inicia uma sessão e direciona ao Painel de Usuário
2. **Given** e-mail inexistente ou senha incorreta, **When** ocorre uma tentativa, **Then** o sistema retorna a mesma mensagem neutra e não cria sessão
3. **Given** usuário bloqueado, desativado ou cancelado, **When** apresenta credenciais válidas, **Then** o sistema nega acesso sem revelar detalhes indevidos do estado
4. **Given** autenticação de dois fatores obrigatória, **When** o primeiro fator é válido, **Then** nenhuma sessão autenticada é liberada antes do segundo fator válido

---

### User Story 2 - Entrar com passkey (Priority: P1)

Um usuário utiliza uma passkey sincronizada ou chave de segurança para entrar sem digitar e-mail ou senha, com verificação local de biometria ou PIN.

**Why this priority**: oferece autenticação resistente a phishing e pode satisfazer o requisito de múltiplos fatores com uma jornada simples.

**Independent Test**: partir de usuário ativo com passkey registrada, autenticar sem informar e-mail e comprovar que desafio, origem e verificação local foram validados.

**Acceptance Scenarios**:

1. **Given** passkey válida vinculada a usuário ativo, **When** a verificação local é concluída, **Then** o sistema cria sessão para o usuário correto
2. **Given** desafio expirado, reutilizado ou de outra origem, **When** uma resposta é apresentada, **Then** o sistema rejeita a autenticação
3. **Given** passkey sem verificação local comprovada, **When** o contexto exige 2FA, **Then** o sistema exige outro fator antes de liberar o acesso protegido

---

### User Story 3 - Entrar ou vincular Google (Priority: P1)

Um usuário utiliza sua identidade Google já vinculada ou, após provar acesso a uma identidade Rinos existente, confirma explicitamente um novo vínculo.

**Why this priority**: reduz atrito sem permitir tomada de conta por coincidência de e-mail.

**Independent Test**: autenticar uma identidade Google vinculada por emissor e identificador estável e verificar que o e-mail não é usado como chave do vínculo.

**Acceptance Scenarios**:

1. **Given** identidade Google válida já vinculada, **When** o usuário conclui o fluxo, **Then** o sistema autentica a identidade Rinos correspondente
2. **Given** e-mail Google igual ao de usuário ativo sem vínculo, **When** ocorre login Google, **Then** o sistema não associa automaticamente e exige autenticação da identidade Rinos existente
3. **Given** usuário autenticado no Rinos, **When** confirma o vínculo com Google válido, **Then** o sistema registra o vínculo e o disponibiliza para logins futuros
4. **Given** administrador autenticado pelo Google, **When** tenta exercer acesso administrativo, **Then** o sistema exige TOTP ou passkey com verificação local; código enviado ao mesmo e-mail não é suficiente

---

### User Story 4 - Configurar autenticação de dois fatores (Priority: P2)

Um usuário protege sua identidade com TOTP ou códigos por e-mail e recebe meios de contingência para recuperar o acesso.

**Why this priority**: reduz o impacto de senha comprometida e é obrigatório antes do exercício de acessos administrativos.

**Independent Test**: ativar TOTP para um usuário, encerrar a sessão e comprovar que senha isolada não inicia nova sessão.

**Acceptance Scenarios**:

1. **Given** usuário comum autenticado, **When** ativa e confirma TOTP, **Then** o método passa a ser exigido nos próximos logins configurados
2. **Given** usuário identificado como administrador sem fator compatível, **When** tenta exercer acesso administrativo, **Then** o sistema bloqueia a operação e orienta a configuração
3. **Given** passkey com verificação local comprovada, **When** o contexto exige 2FA, **Then** a passkey satisfaz a exigência sem desafio adicional
4. **Given** login Google administrativo, **When** o usuário escolhe código por e-mail como segundo fator, **Then** o sistema rejeita esse método e oferece TOTP ou passkey

---

### User Story 5 - Recuperar acesso (Priority: P2)

Um usuário que perdeu a senha ou um segundo fator recupera o acesso sem suporte humano, sem revelar a existência de identidades e sem enfraquecer os demais métodos.

**Why this priority**: métodos fortes sem recuperação segura aumentam bloqueios permanentes e custos operacionais.

**Independent Test**: partir de usuário ativo sem acesso à senha, concluir recuperação pelo e-mail comprovado e verificar invalidação de sessões e provas anteriores.

**Acceptance Scenarios**:

1. **Given** qualquer e-mail informado, **When** uma recuperação é solicitada, **Then** a resposta visível é neutra e não revela se o usuário existe
2. **Given** prova de recuperação válida e não utilizada, **When** o usuário define nova senha, **Then** sessões existentes e provas anteriores são invalidadas
3. **Given** usuário sem senha local e com Google vinculado, **When** solicita recuperação de senha, **Then** o sistema orienta usar o método existente sem criar senha silenciosamente
4. **Given** usuário sem acesso ao segundo fator principal, **When** usa código de recuperação válido, **Then** o código é consumido uma única vez e o evento é auditado

---

### User Story 6 - Gerenciar sessões e métodos (Priority: P3)

Um usuário consulta suas sessões e métodos de autenticação, encerra sessões desconhecidas e adiciona ou remove métodos sem deixar a identidade inacessível.

**Why this priority**: oferece controle após perda de dispositivo, suspeita de comprometimento ou mudança de provedor.

**Independent Test**: criar duas sessões, encerrar remotamente uma delas e verificar que somente a sessão selecionada deixa de funcionar.

**Acceptance Scenarios**:

1. **Given** várias sessões ativas, **When** o usuário encerra uma sessão remota, **Then** ela perde acesso imediatamente e as demais permanecem válidas
2. **Given** último método de autenticação utilizável, **When** o usuário tenta removê-lo, **Then** o sistema impede a remoção até que outro método seja confirmado
3. **Given** alteração de senha, passkey, Google ou 2FA, **When** a operação é concluída, **Then** o sistema registra auditoria e aplica a política de invalidação de sessões

### Edge Cases

- Tentativas simultâneas de login usam a mesma prova temporária.
- Um usuário é bloqueado ou desativado durante uma sessão ativa.
- A senha está correta, mas o segundo fator expira antes da confirmação.
- O relógio do dispositivo TOTP possui pequena diferença em relação ao servidor.
- A passkey foi removida do dispositivo, mas continua registrada no Rinos.
- Uma passkey sincronizada apresenta contador diferente do esperado.
- O usuário perde todos os dispositivos com passkey e TOTP.
- O e-mail Google muda depois do vínculo inicial.
- O Google fica indisponível, mas o usuário possui outro método local.
- O Turnstile fica indisponível quando exigido no login.
- Muitas pessoas legítimas acessam a partir do mesmo IP compartilhado.
- Uma redefinição de senha e um login acontecem simultaneamente.
- O usuário tenta remover o último método de autenticação.
- Uma restauração de backup contém sessões ou provas já revogadas.

## Requirements

### Autenticação e Estado do Usuário

- **FR-AUTH-001**: Somente usuários no estado ativo DEVEM iniciar novas sessões.
- **FR-AUTH-002**: Usuários bloqueados, desativados ou cancelados NÃO DEVEM iniciar ou manter sessões autenticadas.
- **FR-AUTH-003**: O login por senha DEVE usar o e-mail normalizado como identificador da identidade Rinos.
- **FR-AUTH-004**: Respostas a e-mail inexistente, senha incorreta, vínculo externo ausente ou estado não autorizado DEVEM ser equivalentes em conteúdo e comportamento observável.
- **FR-AUTH-005**: O sistema DEVE informar falha de autenticação em linguagem clara sem revelar qual dado, fator ou estado provocou a rejeição.
- **FR-AUTH-006**: Cada autenticação bem-sucedida DEVE criar uma nova sessão e renovar qualquer identificador de sessão anterior apresentado no fluxo.
- **FR-AUTH-007**: Nenhuma sessão plenamente autenticada DEVE ser criada antes da conclusão de todos os fatores exigidos para o contexto.
- **FR-AUTH-008**: Falhas e sucessos de autenticação DEVEM ser auditados com instante, método, origem validada e resultado, sem registrar segredos ou provas reutilizáveis.
- **FR-AUTH-009**: Um usuário ativo DEVE possuir ao menos um método de autenticação utilizável.
- **FR-AUTH-010**: O sistema DEVE impedir a remoção ou invalidação do último método utilizável sem que outro tenha sido confirmado.
- **FR-AUTH-011**: Alterações de estado do usuário para bloqueado, desativado ou cancelado DEVEM invalidar todas as sessões e provas temporárias existentes.
- **FR-AUTH-012**: Alterações sensíveis de métodos de autenticação DEVEM exigir reautenticação recente e ser registradas em auditoria.

### Senha Local

- **FR-AUTH-PWD-001**: Senhas locais DEVEM possuir de 10 a 128 caracteres, com ao menos uma letra maiúscula, uma minúscula, um número e um caractere especial.
- **FR-AUTH-PWD-002**: O sistema DEVE aceitar colagem, preenchimento por gerenciadores de senha, espaços e caracteres Unicode compatíveis com o canal de entrada.
- **FR-AUTH-PWD-003**: Senhas comuns, previsíveis, relacionadas ao Rinos ou conhecidamente comprometidas DEVEM ser rejeitadas com orientação clara para escolha diferente.
- **FR-AUTH-PWD-004**: Senhas NÃO DEVEM ser armazenadas ou exibidas em formato recuperável.
- **FR-AUTH-PWD-005**: O sistema NÃO DEVE exigir troca periódica de senha sem solicitação do usuário ou evidência de comprometimento.
- **FR-AUTH-PWD-006**: Quando houver evidência de comprometimento, o sistema DEVE impedir novo login por senha até sua redefinição segura.
- **FR-AUTH-PWD-007**: O usuário DEVE poder alterar sua senha após informar a senha atual ou concluir reautenticação equivalente.
- **FR-AUTH-PWD-008**: Uma alteração ou redefinição de senha DEVE invalidar todas as provas de redefinição anteriores e aplicar a política definida para sessões existentes.
- **FR-AUTH-PWD-009**: O sistema DEVE permitir visualizar temporariamente a senha digitada sem expô-la por padrão.
- **FR-AUTH-PWD-010**: Comparações, auditorias e mensagens NÃO DEVEM registrar a senha, partes dela ou indicadores que permitam reconstruí-la.

### Proteção contra Automação e Descoberta

- **FR-AUTH-ABUSE-001**: O sistema DEVE aplicar limites progressivos a falhas de autenticação considerando identidade informada, IP de origem validado e padrões anormais de tentativa.
- **FR-AUTH-ABUSE-002**: Limites de falha NÃO DEVEM bloquear permanentemente um usuário por ações de terceiros.
- **FR-AUTH-ABUSE-003**: A quantidade de falhas, a janela de observação e o tempo de espera DEVEM ser configuráveis.
- **FR-AUTH-ABUSE-004**: O Turnstile DEVE tornar-se obrigatório após 3 falhas relacionadas ao mesmo e-mail informado ou IP de origem validado dentro de 15 minutos e permanecer obrigatório para novas tentativas relacionadas até transcorrerem 15 minutos sem nova falha. A quantidade de falhas, a janela de observação e o período de exigência DEVEM ser configuráveis.
- **FR-AUTH-ABUSE-005**: Quando o Turnstile for obrigatório, o token DEVE ser validado no servidor e tentativas com token ausente, inválido, expirado ou reutilizado DEVEM ser rejeitadas.
- **FR-AUTH-ABUSE-006**: Se o Turnstile estiver indisponível quando obrigatório, o sistema NÃO DEVE prosseguir com o login afetado e DEVE orientar nova tentativa posterior.
- **FR-AUTH-ABUSE-007**: O desafio e os limites NÃO DEVEM produzir respostas diferentes que revelem a existência, o estado ou os métodos configurados de um usuário.
- **FR-AUTH-ABUSE-008**: A origem DEVE ser determinada de forma segura quando a aplicação estiver atrás de proxy reverso.
- **FR-AUTH-ABUSE-009**: O sistema DEVE permitir alertar o usuário após atividade suspeita confirmada sem enviar notificações a e-mails inexistentes.
- **FR-AUTH-ABUSE-010**: O fluxo de recuperação DEVE possuir limites próprios e não poderá ser usado para contornar os limites de login.

### Passkeys e Chaves de Segurança

- **FR-AUTH-PK-001**: O sistema DEVE permitir que um usuário registre múltiplas passkeys sincronizadas ou chaves de segurança.
- **FR-AUTH-PK-002**: Cada passkey DEVE possuir identificador, nome definido pelo usuário, instante de criação, instante do último uso e estado de revogação.
- **FR-AUTH-PK-003**: A chave privada DEVE permanecer sob controle do autenticador e nunca ser recebida, armazenada ou reconstruída pelo Rinos.
- **FR-AUTH-PK-004**: O registro e o login por passkey DEVEM usar desafios imprevisíveis, temporários, vinculados ao fluxo e utilizáveis uma única vez.
- **FR-AUTH-PK-005**: O sistema DEVE validar domínio, origem, desafio, assinatura, credencial e verificação local antes de aceitar uma passkey.
- **FR-AUTH-PK-006**: A verificação local por biometria, PIN ou mecanismo equivalente DEVE ser obrigatória para que a passkey satisfaça a exigência de 2FA.
- **FR-AUTH-PK-007**: O usuário DEVE poder iniciar login por passkey sem informar previamente o e-mail.
- **FR-AUTH-PK-008**: Respostas de descoberta de passkey NÃO DEVEM revelar se um usuário possui ou não esse método configurado.
- **FR-AUTH-PK-009**: O cadastro ou remoção de passkey DEVE exigir reautenticação recente por método compatível.
- **FR-AUTH-PK-010**: O usuário DEVE poder nomear e revogar individualmente cada passkey.
- **FR-AUTH-PK-011**: A revogação DEVE impedir imediatamente novos logins pela passkey afetada sem remover outras credenciais.
- **FR-AUTH-PK-012**: Comportamentos anormais de uma passkey DEVEM ser auditados e tratados sem bloquear automaticamente todas as demais credenciais do usuário.

### Identidade Google

- **FR-AUTH-GGL-001**: O sistema DEVE permitir login por identidade Google previamente vinculada.
- **FR-AUTH-GGL-002**: Toda identidade Google DEVE ser validada no servidor quanto a assinatura, emissor, audiência, validade, proteção contra repetição e demais provas obrigatórias antes do uso.
- **FR-AUTH-GGL-003**: O vínculo DEVE ser localizado pela combinação de emissor e `sub`, nunca pelo e-mail retornado pelo Google.
- **FR-AUTH-GGL-004**: Alteração do e-mail no Google NÃO DEVE alterar automaticamente o e-mail principal do usuário Rinos.
- **FR-AUTH-GGL-005**: Se o e-mail Google coincidir com usuário ativo sem vínculo, o sistema NÃO DEVE associar automaticamente as identidades.
- **FR-AUTH-GGL-006**: Um novo vínculo Google DEVE exigir sessão Rinos recente, reautenticação por método existente e confirmação explícita do usuário.
- **FR-AUTH-GGL-007**: Uma identidade Google NÃO DEVE ser vinculada a mais de um usuário Rinos.
- **FR-AUTH-GGL-008**: O usuário DEVE poder remover o vínculo Google somente quando outro método utilizável permanecer confirmado.
- **FR-AUTH-GGL-009**: Se o Google estiver indisponível, usuários com outro método DEVEM poder utilizá-lo normalmente.
- **FR-AUTH-GGL-010**: O Rinos NÃO DEVE persistir tokens de acesso ou atualização do Google quando não houver autorização explícita para consumir outros serviços Google.
- **FR-AUTH-GGL-011**: Login Google de administrador NÃO DEVE usar código enviado ao mesmo e-mail como segundo fator; TOTP ou passkey verificada DEVE ser exigido.
- **FR-AUTH-GGL-012**: Usuário bloqueado, desativado ou cancelado NÃO DEVE obter sessão mesmo que a identidade Google seja válida.

### Autenticação de Dois Fatores

- **FR-AUTH-MFA-001**: Usuários comuns DEVEM poder ativar ou desativar 2FA voluntariamente após reautenticação recente.
- **FR-AUTH-MFA-002**: Administradores do sistema e administradores de contas DEVEM possuir fator compatível antes do exercício de acessos administrativos.
- **FR-AUTH-MFA-003**: A exigência de 2FA NÃO DEVE conceder papel, grupo, chave ou permissão ao usuário.
- **FR-AUTH-MFA-004**: O sistema DEVE oferecer TOTP e código enviado ao e-mail principal confirmado.
- **FR-AUTH-MFA-005**: A ativação de TOTP DEVE exigir confirmação de um código válido antes de considerar o método utilizável.
- **FR-AUTH-MFA-006**: Cada segredo TOTP DEVE ser individual por usuário e não poderá ser exibido novamente após a confirmação inicial.
- **FR-AUTH-MFA-007**: Códigos TOTP DEVEM possuir uso limitado à janela temporal aceita e não poderão ser reutilizados no mesmo intervalo.
- **FR-AUTH-MFA-008**: Códigos enviados por e-mail DEVEM ser imprevisíveis, temporários, vinculados ao fluxo e de uso único.
- **FR-AUTH-MFA-009**: O envio e a tentativa de códigos por e-mail e TOTP DEVEM possuir limites configuráveis.
- **FR-AUTH-MFA-010**: Passkey com verificação local comprovada DEVE satisfazer a exigência de 2FA sem fator adicional.
- **FR-AUTH-MFA-011**: Passkey sem verificação local comprovada NÃO DEVE satisfazer a exigência de 2FA.
- **FR-AUTH-MFA-012**: Após login Google, código enviado ao mesmo e-mail Google NÃO DEVE satisfazer o 2FA administrativo.
- **FR-AUTH-MFA-013**: O usuário DEVE receber um conjunto de 10 códigos de recuperação de uso único. Os códigos NÃO DEVEM possuir expiração fixa enquanto o conjunto permanecer válido.
- **FR-AUTH-MFA-014**: Gerar novo conjunto de códigos de recuperação, desativar o 2FA ou concluir uma recuperação que altere os fatores de autenticação DEVE invalidar imediatamente todo o conjunto anterior.
- **FR-AUTH-MFA-015**: Códigos de recuperação NÃO DEVEM ser exibidos novamente após sua emissão e DEVEM ser armazenados de forma não recuperável.
- **FR-AUTH-MFA-016**: Remover ou substituir um fator DEVE exigir reautenticação recente e, quando possível, confirmação por outro fator.
- **FR-AUTH-MFA-017**: O sistema DEVE impedir que a remoção de fatores deixe um administrador incapaz de satisfazer o 2FA obrigatório.
- **FR-AUTH-MFA-018**: Alterações de 2FA, uso de recuperação e falhas repetidas DEVEM ser auditados e notificados ao usuário.

### Recuperação de Acesso

- **FR-AUTH-REC-001**: Solicitações de recuperação DEVEM retornar resposta neutra independentemente da existência ou do estado do e-mail informado.
- **FR-AUTH-REC-002**: Quando aplicável, o sistema DEVE enviar prova de recuperação somente ao e-mail principal confirmado.
- **FR-AUTH-REC-003**: Cada prova de recuperação DEVE ser imprevisível, vinculada a um usuário e finalidade, utilizável uma única vez e válida por uma hora.
- **FR-AUTH-REC-004**: Emitir nova prova DEVE invalidar as provas anteriores da mesma finalidade.
- **FR-AUTH-REC-005**: O sistema DEVE limitar solicitações e tentativas de recuperação por identidade e origem sem revelar a existência do usuário.
- **FR-AUTH-REC-006**: Redefinir a senha DEVE exigir nova senha compatível com a política vigente.
- **FR-AUTH-REC-007**: Redefinir a senha DEVE invalidar sessões existentes e todas as provas de recuperação abertas.
- **FR-AUTH-REC-008**: Usuário sem senha local DEVE ser orientado a usar Google ou passkey existente e não terá senha criada sem confirmação explícita.
- **FR-AUTH-REC-009**: Recuperação de 2FA DEVE usar código de recuperação ou processo reforçado que não dependa exclusivamente do fator perdido.
- **FR-AUTH-REC-010**: Código de recuperação consumido NÃO DEVE ser aceito novamente.
- **FR-AUTH-REC-011**: Uma recuperação concluída DEVE notificar o usuário e registrar método, origem e fatores alterados sem expor segredos.
- **FR-AUTH-REC-012**: Provas de recuperação não utilizadas DEVEM ser removidas ou anonimizadas após o término da retenção operacional definida.

### Sessões

- **FR-AUTH-SES-001**: Cada sessão DEVE ser vinculada a um único usuário, método de autenticação, instante de criação, última atividade, origem e estado.
- **FR-AUTH-SES-002**: A sessão normal DEVE expirar ao atingir 12 horas de duração máxima ou 30 minutos de inatividade. Quando o usuário escolher “lembrar-me”, a sessão DEVE expirar ao atingir 30 dias de duração máxima ou 7 dias de inatividade.
- **FR-AUTH-SES-003**: O usuário DEVE poder encerrar a sessão atual, uma sessão remota específica ou todas as suas sessões.
- **FR-AUTH-SES-004**: Encerrar uma sessão DEVE impedir seu uso posterior imediatamente em todas as instâncias da aplicação.
- **FR-AUTH-SES-005**: O usuário DEVE visualizar suas sessões com informações suficientes para reconhecer dispositivo, origem aproximada, criação, última atividade e estado.
- **FR-AUTH-SES-006**: Alteração de senha, recuperação de acesso, bloqueio, desativação ou cancelamento DEVE invalidar todas as sessões existentes.
- **FR-AUTH-SES-007**: Adição ou remoção de método de autenticação DEVE permitir ao usuário escolher entre manter sessões reconhecidas ou encerrar todas, salvo evento de risco que exija invalidação total.
- **FR-AUTH-SES-008**: Operações administrativas ou sensíveis DEVEM exigir reautenticação quando a última autenticação válida tiver ocorrido há mais de 15 minutos.
- **FR-AUTH-SES-009**: Sessões não autenticadas usadas durante login ou recuperação DEVEM ser separadas de sessões autenticadas e inutilizadas após conclusão ou expiração.
- **FR-AUTH-SES-010**: Identificadores de sessão NÃO DEVEM aparecer em URLs, logs ou mensagens ao usuário.
- **FR-AUTH-SES-011**: O sistema DEVE notificar criação de sessão em contexto incomum quando o risco justificar, sem expor informações sensíveis.
- **FR-AUTH-SES-012**: Restaurar dados de backup NÃO DEVE reativar sessões, provas, códigos ou credenciais temporárias já expiradas ou revogadas no estado restaurado.

### Decisões de Infraestrutura Auditáveis

- **FR-AUTH-INFRA-SCHED**: O sistema DEVE executar limpeza automática ao menos diária de sessões, provas, desafios e códigos temporários expirados.
- **FR-AUTH-INFRA-KEY**: Segredos usados para proteger sessões ou provas persistentes DEVEM suportar versionamento e rotação sem invalidar abruptamente sessões que ainda devam permanecer válidas.
- **FR-AUTH-INFRA-REFRESH**: O login Google NÃO DEVE manter tokens de atualização no escopo desta feature; cada autenticação deve validar uma nova prova emitida pelo provedor.
- **FR-AUTH-INFRA-LOCK**: Consumo de provas, códigos, desafios, sessões revogadas e credenciais de uso único DEVE ser atômico e consistente entre todas as instâncias da aplicação.
- **FR-AUTH-INFRA-BACKUP**: Dados persistentes de autenticação DEVEM participar dos backups do sistema, e a restauração DEVE ser testada sem reativar artefatos temporários expirados ou revogados.
- **FR-AUTH-INFRA-IDEMP**: Repetições da mesma confirmação, redefinição ou revogação DEVEM produzir no máximo um efeito e retornar resultado consistente sem recriar artefatos consumidos.

### Key Entities

- **Authentication Method**: método utilizável por um usuário, com tipo, estado, criação, último uso e informações seguras de identificação.
- **Password Credential**: verificador não recuperável da senha local e informações necessárias para sua política e rotação.
- **Passkey Credential**: vínculo público de WebAuthn com nome, identificador, chave pública, estado e histórico de uso.
- **External Identity**: vínculo entre usuário Rinos e identidade Google identificada por emissor e `sub`.
- **Second Factor**: TOTP ou e-mail confirmado capaz de complementar um primeiro fator conforme o contexto.
- **Recovery Code**: prova de contingência de uso único para recuperar acesso quando o fator principal não está disponível.
- **Authentication Proof**: desafio, código ou token temporário associado a uma finalidade específica e consumível uma única vez.
- **Session**: continuidade autenticada de um usuário, com criação, atividade, origem, método e estado de revogação ou expiração.
- **Authentication Event**: registro auditável de tentativa, sucesso, falha ou alteração sensível sem conteúdo secreto.

## Success Criteria

### Measurable Outcomes

- **SC-AUTH-001**: Pelo menos 95% dos usuários com credenciais válidas concluem login por senha em até 30 segundos, excluído o tempo de entrega do segundo fator.
- **SC-AUTH-002**: Pelo menos 95% dos usuários com passkey compatível concluem o login em até 15 segundos.
- **SC-AUTH-003**: Em 100% dos testes, respostas para e-mail inexistente, senha incorreta e usuário indisponível não revelam qual condição ocorreu.
- **SC-AUTH-004**: Em 100% dos testes, usuário bloqueado, desativado ou cancelado não inicia nem mantém sessão.
- **SC-AUTH-005**: Em 100% dos testes, desafios, provas, tokens e códigos expirados, reutilizados ou de outra finalidade são rejeitados.
- **SC-AUTH-006**: Em 100% dos testes, uma identidade Google é localizada por emissor e `sub`, nunca apenas pelo e-mail.
- **SC-AUTH-007**: Em 100% dos testes de coincidência de e-mail Google, nenhum vínculo com usuário ativo é criado sem autenticação e confirmação explícitas.
- **SC-AUTH-008**: Em 100% dos testes administrativos, senha exige segundo fator e login Google não aceita código enviado ao mesmo e-mail como 2FA.
- **SC-AUTH-009**: Em 100% dos testes, passkey somente satisfaz 2FA quando a verificação local é comprovada.
- **SC-AUTH-010**: Pelo menos 90% dos usuários em teste concluem ativação de TOTP sem ajuda em até três minutos.
- **SC-AUTH-011**: Em 100% dos testes, redefinição de senha, bloqueio ou desativação invalida todas as sessões existentes.
- **SC-AUTH-012**: Em 100% dos testes, revogar uma passkey ou sessão impede seu uso posterior sem afetar credenciais não selecionadas.
- **SC-AUTH-013**: Em 100% dos testes, o último método utilizável de um usuário não pode ser removido.
- **SC-AUTH-014**: Em 100% dos testes de concorrência e repetição, cada prova ou código de uso único produz no máximo um efeito.
- **SC-AUTH-015**: O login, a configuração de 2FA, a recuperação e a gestão de sessões podem ser concluídos apenas por teclado e sem bloqueios críticos de acessibilidade.
