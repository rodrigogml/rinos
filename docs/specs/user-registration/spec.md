# Feature Specification: Cadastro e Ciclo Inicial do Usuário

**Feature**: `user-registration`
**Created**: 2026-07-17
**Status**: Clarified — quality gate de requisitos aprovado

## Escopo

Esta feature cria a identidade global e única do usuário no Rinos e conduz o cadastro até sua ativação. A identidade existe no nível do sistema e não pertence a um tenant específico. Após a ativação, seu único acesso inicial é ao próprio Painel de Usuário.

Inclui validação dos dados cadastrais, criação de credencial local ou vínculo inicial com identidade Google, comprovação do identificador primário, aceite dos documentos legais e tratamento de cadastros pendentes.

Não inclui autenticação de sessões, implementação da recuperação de acesso, detalhamento do Painel de Usuário, cadastro de contas pessoais ou empresas, associação ou convite para contas, planos, papéis, grupos, chaves de acesso ou administração de usuários ativos. Embora pertença a `user-authentication`, a recuperação mínima de senha é dependência obrigatória para liberar `user-registration` em produção.

## Clarifications

### Session 2026-07-17

- Q: Como reconhecer solicitações pertencentes ao mesmo cadastro pendente? -> A: Todas as solicitações com o mesmo e-mail normalizado convergem para o mesmo cadastro enquanto ele estiver pendente.
- Q: O cadastro pode informar que um e-mail já está associado a um usuário? -> A: Sim. O sistema informa que o e-mail já existe e oferece direcionamento para a recuperação de senha, sem revelar outros dados do usuário.
- Q: O e-mail pode ser alterado depois que um cadastro pendente foi aceito? -> A: Não. Após o envio da confirmação, o e-mail torna-se imutável; um endereço incorreto exige novo cadastro, e todo cadastro não validado é excluído automaticamente após 15 dias.
- Q: Quais informações pessoais são obrigatórias no cadastro inicial? -> A: Somente e-mail, senha e aceites legais obrigatórios; os demais dados pessoais serão tratados futuramente no Painel de Usuário.
- Q: Como proteger o cadastro contra bots e criação automatizada em massa? -> A: Integrar Cloudflare Turnstile por meio do RFW, com limiar configurável de cadastros por origem e padrão zero, tornando o desafio sempre obrigatório; manter também limites temporários configuráveis por IP.

### Session 2026-07-17

- Q: Qual política de senha deve ser aplicada no cadastro inicial? -> A: Exigir de 10 a 128 caracteres, incluindo maiúscula, minúscula, número e caractere especial, rejeitando senhas comuns ou comprometidas e sem expiração periódica sem indício de comprometimento.
- Q: Quando a autenticação de dois fatores deve ser obrigatória? -> A: Deve ser opcional para usuários comuns e obrigatória para administradores do sistema e administradores de contas antes do exercício de acessos administrativos; a decisão foi encaminhada para `user-authentication`.
- Q: Como cadastrar uma identidade Google que ainda não possui usuário ativo no Rinos? -> A: Validar a identidade e o e-mail no Google, bloquear o e-mail no formulário, exigir somente os aceites legais, dispensar senha e confirmação adicional, reutilizar com segurança eventual cadastro pendente e ativar o usuário vinculado por emissor e `sub`.

### Session 2026-07-18

- Q: Uma passkey pode satisfazer a exigência de autenticação de dois fatores? -> A: Sim, desde que a verificação local do usuário seja obrigatória e comprovada; senha continua exigindo fator adicional nos contextos protegidos.
- Q: Código enviado ao mesmo e-mail Google pode satisfazer o 2FA obrigatório de administradores após login Google? -> A: Não. Nesse fluxo, administradores devem confirmar com TOTP ou passkey com verificação local; a decisão foi encaminhada para `user-authentication`.

### Session 2026-07-26

- Q: Quais campos podem ser preservados quando um desafio anti-automação precisa ser renovado? -> A: Somente e-mail e aceites legais; senha, confirmação, token e qualquer outra prova permanecem efêmeros e devem ser reinformados.
- Q: O cadastro pode ser liberado em produção antes de existir recuperação de senha? -> A: Não. Pode ser implementado primeiro, mas sua liberação em produção depende do fluxo mínimo de recuperação fornecido por `user-authentication`.
- Q: Qual padrão e gate de acessibilidade devem ser adotados? -> A: WCAG 2.2 nível AA, sem violações automatizadas críticas ou sérias e sem bloqueios nas jornadas principais avaliadas manualmente por teclado e leitor de tela.
- Q: Como medir a meta de entrega do e-mail sem confirmação de caixa postal? -> A: Medir a aceitação pelo SMTP em até dois minutos após o commit; entrega final, bounce e atraso serão métricas externas somente quando o provedor oferecer eventos.
- Q: Qual amostra mínima torna os percentuais de usabilidade e ativação verificáveis no MVP? -> A: Dez participantes alheios ao desenvolvimento, sem orientação durante a jornada, com ao menos quatro testes em telefone e quatro em desktop; o gate exige sucesso de pelo menos nove participantes.
- Q: Um Turnstile válido pode ultrapassar o limite máximo temporário de cadastros por IP? -> A: Não. O limite permanece absoluto, com padrão de 20 novas pendências de cadastro local por origem em 24 horas; somente a criação efetiva de uma nova pendência é contabilizada, e o bloqueio informa o tempo restante sem se tornar permanente.
- Q: O primeiro incremento deve possuir outbox e retentativa automática para e-mails de comprovação? -> A: Não. O envio ocorre diretamente pelo RFW depois do commit, com timeout explícito; em caso de falha, o cadastro permanece pendente e a recuperação ocorre por reenvio solicitado pela pessoa, sem persistir token ou URL secreta para despacho posterior.
- Q: Como calibrar o custo do Argon2id antes da primeira liberação em produção? -> A: Usar como piso 19.456 KiB de memória, duas iterações, paralelismo um, salt de 16 bytes e hash de 32 bytes; medir no mínimo 50 operações após aquecimento no servidor-alvo, ajustar para mediana entre 500 ms e um segundo e exigir percentil 95 de até 1,5 segundo, com resultado registrado no checklist de produção.

### Session 2026-07-27

- Q: Como impedir que múltiplas instâncias executem simultaneamente a limpeza diária? -> A: Consumir a coordenação automática de manutenção definida em `platform-operations`: lease no banco global, `instanceId` em properties, sessão por inicialização, heartbeat de 30 minutos, expiração de quatro horas, `epoch` de fencing e estabilização de 10 minutos. O job continua idempotente e revalida o cadastro antes de excluir.
- Q: Como armazenar a origem usada nos controles de abuso? -> A: Armazenar diretamente o IP normalizado em formato binário na tabela global compartilhada `security_originWindow`, sem HMAC ou rotação de chave, com acesso restrito, sem cópia em auditoria permanente e exclusão automática até 30 dias depois do fim da janela.
- Q: Como validar o SLO SMTP sem uma previsão de carga ou infraestrutura de referência? -> A: Remover o gate indefinido de carga, executar 100 cadastros nominais contra SMTP local controlado e exigir ao menos 95 aceitações em até dois minutos após o commit; no SMTP real, executar somente um smoke test sem declarar capacidade ou throughput.
- Q: Como impedir escritas da coordenadora antiga depois que uma nova liderança estiver estabilizada? -> A: Limitar cada transação de lote a no máximo cinco minutos, sempre menos que os 10 minutos de estabilização; a sessão antiga pode somente concluir ou abortar o lote já iniciado, e a nova líder não começa antes de comprovar novamente o lease estabilizado.
- Q: Como executar o gate SMTP de 100 cadastros sem que o próprio limite padrão de 20 pendências por origem invalide a medição? -> A: Isolar o gate SMTP com configuração de teste que permita ao menos 100 novas pendências e verificação humana controlada, mantendo a política padrão de 20 e o Turnstile real cobertos separadamente nos testes de abuso.
- Q: Qual rotina garante a exclusão dos IPs depois da retenção permitida? -> A: O job diário coordenado de manutenção também exclui `security_originWindow` vencidas em lotes próprios, sob o mesmo lease, timeout transacional, métricas e idempotência da limpeza de cadastros.
- Q: Quantos cenários do quickstart compõem a suíte documentada? -> A: Todos os 20 cenários devem ser executados, ainda que Argon2id e SMTP também possuam gates especializados.

## User Scenarios & Testing

### User Story 1 - Concluir um novo cadastro (Priority: P1)

Uma pessoa ainda não cadastrada informa seu e-mail e define uma senha local ou apresenta uma identidade Google válida, aceita os documentos obrigatórios, comprova o identificador primário quando necessário e recebe uma identidade ativa no Rinos.

**Why this priority**: sem uma identidade ativa não é possível utilizar nenhuma capacidade posterior da plataforma.

**Independent Test**: iniciar sem usuário existente, completar todo o cadastro com dados válidos e comprovar que foi criada exatamente uma identidade ativa, ainda sem conta, associação, papel ou permissão.

**Acceptance Scenarios**:

1. **Given** uma pessoa não cadastrada, **When** informa dados válidos, aceita os documentos vigentes e comprova o identificador primário, **Then** o sistema ativa uma única identidade cujo acesso inicial se limita ao próprio Painel de Usuário
2. **Given** dados obrigatórios ausentes ou inválidos, **When** a pessoa tenta prosseguir, **Then** o sistema indica cada correção necessária sem criar uma identidade ativa
3. **Given** um e-mail já associado a uma identidade, **When** uma nova tentativa de cadastro é enviada, **Then** o sistema informa que o e-mail já existe, não cria duplicidade e oferece direcionamento para recuperação de senha
4. **Given** um cadastro ainda não comprovado, **When** alguém tenta utilizá-lo como identidade ativa, **Then** o sistema rejeita a operação
5. **Given** uma identidade Google válida com e-mail verificado e sem usuário ativo correspondente, **When** a pessoa aceita os documentos legais, **Then** o sistema ativa uma única identidade sem exigir senha local ou confirmação adicional do e-mail
6. **Given** uma identidade Google válida cujo e-mail corresponde a cadastro pendente, **When** a pessoa conclui os aceites, **Then** o sistema reutiliza a identidade pendente após invalidar sua senha e suas comprovações anteriores
7. **Given** uma identidade Google válida cujo e-mail corresponde a usuário ativo, **When** a pessoa tenta entrar pelo Google ainda não vinculado, **Then** o sistema não associa automaticamente as identidades e exige autenticação segura do usuário Rinos existente

---

### User Story 2 - Retomar um cadastro pendente (Priority: P2)

Uma pessoa que iniciou o cadastro, mas ainda não comprovou seu identificador, pode retomar o processo e solicitar uma nova comprovação sem criar outro usuário.

**Why this priority**: falhas ou atrasos na entrega da comprovação não devem obrigar a pessoa a reiniciar o cadastro nem gerar identidades duplicadas.

**Independent Test**: partir de uma identidade pendente, solicitar nova comprovação e concluir a ativação, verificando que o mesmo registro foi reutilizado.

**Acceptance Scenarios**:

1. **Given** um cadastro pendente dentro do prazo, **When** a pessoa solicita nova comprovação, **Then** o sistema envia uma nova instrução e mantém uma única identidade pendente
2. **Given** uma comprovação expirada ou já utilizada, **When** a pessoa tenta utilizá-la, **Then** o sistema rejeita a tentativa e oferece a solicitação de uma nova comprovação
3. **Given** várias solicitações em curto intervalo, **When** o limite de segurança é excedido, **Then** o sistema bloqueia temporariamente novos envios e informa quando será possível tentar novamente

---

### User Story 3 - Cancelar um cadastro pendente (Priority: P3)

Uma pessoa que ainda não ativou sua identidade pode cancelar o cadastro iniciado e impedir sua ativação posterior.

**Why this priority**: a pessoa deve manter controle sobre dados fornecidos em um processo que decidiu não concluir.

**Independent Test**: partir de uma identidade pendente, cancelar o processo e verificar que nenhuma comprovação anterior permite ativá-la.

**Acceptance Scenarios**:

1. **Given** um cadastro pendente válido, **When** a pessoa confirma o cancelamento, **Then** o sistema invalida as comprovações abertas e impede a ativação daquele cadastro
2. **Given** um cadastro já cancelado, **When** uma comprovação anterior é apresentada, **Then** o sistema a rejeita
3. **Given** um identificador pertencente apenas a cadastro cancelado e já liberado pela política de retenção, **When** a pessoa inicia novo cadastro, **Then** o sistema permite um novo processo sem restaurar automaticamente os dados cancelados

### Edge Cases

- Duas solicitações simultâneas usam o mesmo identificador primário.
- A comprovação chega depois que o cadastro foi cancelado ou expirou.
- A pessoa altera a grafia ou capitalização do identificador entre tentativas.
- A pessoa percebe que informou um e-mail incorreto depois que a confirmação foi enviada.
- O serviço de entrega da comprovação fica temporariamente indisponível.
- O serviço de validação do Cloudflare Turnstile fica temporariamente indisponível.
- O serviço de identidade Google fica indisponível ou retorna uma identidade inválida.
- Uma identidade Google corresponde a um cadastro pendente iniciado anteriormente por outra pessoa.
- O mesmo identificador estável do Google já está vinculado a outro usuário Rinos.
- O navegador é fechado e o cadastro é retomado em outro dispositivo.
- Os termos de uso ou a política de privacidade mudam durante um cadastro pendente.
- Uma solicitação automatizada tenta criar cadastros em massa.
- Várias pessoas legítimas iniciam cadastros a partir do mesmo IP compartilhado.
- Uma falha ocorre depois do envio dos dados, mas antes da confirmação visual para a pessoa.
- A pessoa utiliza dispositivo móvel ou recursos de tecnologia assistiva durante o cadastro.

## Requirements

### Identidade do Usuário

- **FR-USR-001**: O sistema DEVE representar cada pessoa cadastrada por uma identidade global única, independente das contas às quais ela venha a ser associada.
- **FR-USR-002**: O sistema DEVE atribuir a cada usuário um identificador interno único que não dependa de nome, contato, documento, conta ou papel e que não seja reutilizado para outra pessoa.
- **FR-USR-003**: O cadastro local DEVE exigir somente e-mail, senha e aceites legais obrigatórios. O cadastro por Google DEVE exigir uma identidade Google válida com e-mail verificado e os aceites legais, sem exigir senha local. Nome, telefone e demais dados pessoais NÃO DEVEM ser exigidos nesta feature e serão tratados futuramente no Painel de Usuário.
- **FR-USR-004**: O e-mail DEVE ser o identificador primário único. O sistema DEVE normalizá-lo antes de comparar ou verificar unicidade, desconsiderando diferenças de capitalização e espaços externos.
- **FR-USR-005**: O sistema NÃO DEVE permitir mais de uma identidade vigente com o mesmo identificador primário normalizado.
- **FR-USR-006**: A identidade DEVE possuir, no mínimo, os estados pendente de comprovação, ativa, bloqueada, desativada e cancelada.
- **FR-USR-007**: Toda transição de estado DEVE registrar o estado anterior, o novo estado, o instante, a origem da ação e o motivo aplicável.
- **FR-USR-008**: A criação de um usuário NÃO DEVE conceder automaticamente papel, grupo, chave de acesso ou permissão em qualquer conta. A única concessão sistêmica inicial DEVE ser o acesso mínimo ao próprio Painel de Usuário.
- **FR-USR-009**: A identidade global DEVE permanecer a mesma quando o usuário for posteriormente associado a uma ou mais contas.
- **FR-USR-010**: O sistema DEVE armazenar apenas dados pessoais necessários para finalidades declaradas e permitir identificar a finalidade aplicável a cada dado coletado.
- **FR-USR-011**: O sistema DEVE registrar a versão e o instante do aceite de cada documento legal obrigatório.
- **FR-USR-012**: Credenciais e evidências de comprovação NÃO DEVEM ser expostas em consultas, mensagens, registros de auditoria ou logs.
- **FR-USR-013**: Um usuário ativo sem associação a contas DEVE conseguir acessar apenas seu Painel de Usuário e as configurações pertencentes à própria identidade.
- **FR-USR-014**: O acesso ao Painel de Usuário NÃO DEVE permitir consultar ou alterar dados de contas, tenants ou outros usuários.
- **FR-USR-015**: Todo usuário ativo DEVE possuir ao menos um método de autenticação válido, que pode ser senha local, passkey ou identidade externa vinculada.

### Início do Cadastro

- **FR-REG-001**: O sistema DEVE permitir cadastro público iniciado pela própria pessoa, sem exigir convite ou vínculo prévio com uma conta.
- **FR-REG-002**: O sistema DEVE informar, antes do envio, quais dados são obrigatórios e para qual finalidade serão utilizados.
- **FR-REG-003**: O sistema DEVE validar presença, formato, tamanho e consistência dos dados antes de aceitar a solicitação.
- **FR-REG-004**: No cadastro local, a pessoa DEVE definir uma senha de 10 a 128 caracteres contendo ao menos uma letra maiúscula, uma letra minúscula, um número e um caractere especial. O sistema DEVE aceitar colagem e gerenciadores de senha, rejeitar senhas comuns ou conhecidamente comprometidas, explicar cada rejeição e não exigir troca periódica sem solicitação do usuário ou indício de comprometimento. A senha NÃO DEVE ser revelada nem armazenada em formato recuperável.
- **FR-REG-005**: O sistema DEVE exigir aceite explícito dos termos de uso e da política de privacidade vigentes antes de criar o cadastro pendente.
- **FR-REG-006**: Aceites opcionais, incluindo comunicações promocionais, DEVEM ser apresentados separadamente dos aceites obrigatórios e permanecer desmarcados por padrão.
- **FR-REG-007**: O sistema DEVE impedir a criação de uma nova identidade quando o identificador primário já estiver associado a usuário vigente.
- **FR-REG-008**: Ao detectar um e-mail já associado a uma identidade, o sistema DEVE informar explicitamente que o e-mail já existe e oferecer direcionamento para a recuperação de senha, sem revelar qualquer outro dado da identidade.
- **FR-REG-009**: Todas as solicitações com o mesmo e-mail normalizado DEVEM convergir para uma única identidade enquanto o cadastro estiver pendente, inclusive em dispositivos diferentes, em execuções simultâneas ou após falha de comunicação, sem duplicar usuários ou aceites.
- **FR-REG-010**: Se o cadastro não puder ser aceito integralmente, o sistema NÃO DEVE deixar uma identidade ativa ou credencial utilizável parcialmente criada.
- **FR-REG-011**: Após aceitar os dados iniciais, o sistema DEVE criar a identidade no estado pendente de comprovação.

### Comprovação e Ativação

- **FR-REG-012**: No cadastro local, o sistema DEVE enviar uma instrução de comprovação exclusivamente ao e-mail informado. No cadastro por Google, uma identidade validada com `email_verified` verdadeiro DEVE dispensar confirmação adicional do e-mail.
- **FR-REG-013**: Cada comprovação DEVE ser imprevisível, vinculada a uma única identidade pendente, utilizável uma única vez e válida por 24 horas.
- **FR-REG-014**: A emissão de uma nova comprovação DEVE invalidar as comprovações anteriores ainda não utilizadas para o mesmo cadastro.
- **FR-REG-015**: O sistema DEVE limitar a três novas solicitações de comprovação por cadastro a cada 15 minutos e informar o tempo restante de bloqueio sem expor dados sensíveis.
- **FR-REG-016**: A ativação DEVE exigir o aceite das versões vigentes dos documentos obrigatórios e uma das seguintes comprovações: confirmação válida do e-mail no cadastro local ou identidade Google validada com e-mail verificado.
- **FR-REG-017**: Se um documento obrigatório mudar antes da ativação, o sistema DEVE solicitar aceite da nova versão antes de concluir o cadastro.
- **FR-REG-018**: A ativação DEVE alterar exatamente uma identidade de pendente para ativa e invalidar todas as comprovações abertas.
- **FR-REG-019**: A ativação DEVE ser segura contra repetições: apresentar a mesma comprovação novamente não pode criar outro usuário nem repetir efeitos posteriores.
- **FR-REG-020**: A ativação DEVE encerrar com a identidade ativa e acesso restrito ao próprio Painel de Usuário, sem criar automaticamente conta pessoal, empresa, tenant, plano ou associação.

### Retomada, Expiração e Cancelamento

- **FR-REG-021**: A pessoa DEVE poder retomar um cadastro pendente no mesmo ou em outro dispositivo sem iniciar uma segunda identidade.
- **FR-REG-022**: O sistema DEVE permitir solicitar nova comprovação quando a anterior expirar, respeitando os limites de segurança.
- **FR-REG-023**: Todo cadastro que não for validado dentro de 15 dias após sua criação DEVE ser excluído automaticamente e não poderá ser ativado posteriormente.
- **FR-REG-024-INFRA-SCHED**: O sistema DEVE executar ao menos diariamente a exclusão de cadastros não validados que tenham completado 15 dias. Em instalação com múltiplas instâncias, somente a sessão que comprovar liderança de manutenção vigente e estabilizada conforme `platform-operations` poderá iniciar cada lote; cada transação de lote possui timeout padrão máximo de cinco minutos, inferior à estabilização de 10 minutos, e o job permanece idempotente e deve revalidar estado e expiração antes da exclusão.
- **FR-REG-025**: A pessoa DEVE poder cancelar um cadastro pendente após confirmar controle sobre o identificador primário.
- **FR-REG-026**: O cancelamento DEVE invalidar imediatamente todas as comprovações abertas e impedir a ativação do cadastro cancelado.
- **FR-REG-027**: Dados que precisem ser preservados após cancelamento ou expiração por segurança, auditoria ou obrigação legal DEVEM ser minimizados e possuir prazo de retenção documentado.

### Segurança, Auditoria e Experiência

- **FR-REG-028**: Antes de aceitar um novo cadastro, o sistema DEVE exigir uma validação bem-sucedida do Cloudflare Turnstile quando a política configurada determinar que o desafio é obrigatório.
- **FR-REG-029**: O sistema DEVE registrar início, reenvio, comprovação, ativação, expiração e cancelamento do cadastro sem registrar credenciais ou evidências secretas.
- **FR-REG-030**: Mensagens de erro DEVEM explicar a ação corretiva em linguagem clara, sem revelar dados pessoais ou detalhes que facilitem abuso.
- **FR-REG-031**: O fluxo completo DEVE ser operável por teclado, compatível com tecnologia assistiva e utilizável em dispositivos móveis e desktop.
- **FR-REG-032**: O envio da comprovação local DEVE ocorrer diretamente pelo serviço de e-mail do RFW depois do commit que persiste o cadastro e a comprovação, com timeouts explícitos. A indisponibilidade temporária do SMTP NÃO DEVE reverter, duplicar nem ativar parcialmente o cadastro já aceito. O primeiro incremento NÃO DEVE persistir token, URL secreta ou mensagem em outbox e NÃO DEVE executar retentativa automática; diante de falha ou interrupção entre commit e envio, a pessoa DEVE receber orientação para retomar o cadastro e solicitar nova comprovação.
- **FR-REG-033**: Depois que os dados forem aceitos, salvos e a confirmação for enviada, o e-mail do cadastro pendente NÃO DEVE ser alterado. Para corrigir o endereço, a pessoa DEVE iniciar outro cadastro com o e-mail correto.
- **FR-REG-034**: Quando o Turnstile for obrigatório, o sistema DEVE validar o token no servidor antes de persistir o cadastro e DEVE rejeitar tokens ausentes, inválidos, expirados ou reutilizados.
- **FR-REG-035**: A pessoa DEVE poder renovar um desafio expirado ou inválido preservando somente o e-mail e os aceites legais já informados. Senha, confirmação, token Turnstile e qualquer outra credencial ou prova NÃO DEVEM ser preservados e DEVEM ser reinformados.
- **FR-REG-036**: A apresentação e o tratamento do Turnstile NÃO DEVEM impedir a conclusão do cadastro por teclado ou tecnologia assistiva.
- **FR-REG-037**: A aplicação DEVE permitir configurar quantos cadastros podem ser criados por IP de origem, dentro de uma janela de contabilização configurável, antes que o Turnstile se torne obrigatório para novas tentativas dessa origem.
- **FR-REG-038**: O limiar que torna o Turnstile obrigatório DEVE ter valor padrão zero. Com esse valor, toda tentativa de cadastro DEVE exigir validação do Turnstile, independentemente do histórico da origem.
- **FR-REG-039**: A identificação da origem DEVE considerar o endereço validado pela aplicação mesmo quando ela operar atrás de proxy reverso, sem confiar em informações fornecidas por intermediários não autorizados. O IP normalizado DEVE ser armazenado diretamente em formato binário na estrutura global compartilhada de janelas de segurança, sem HMAC, criptografia ou rotação de chave. O acesso DEVE ser restrito à prevenção e investigação de abuso; o IP NÃO DEVE ser copiado para auditoria permanente e DEVE ser excluído automaticamente até 30 dias depois do fim da respectiva janela por rotina executada ao menos diariamente, em lotes próprios submetidos à mesma liderança, timeout transacional, idempotência e observabilidade da manutenção global.
- **FR-REG-040**: Além do Turnstile, a aplicação DEVE permitir configurar uma quantidade máxima de novas pendências de cadastro local por IP de origem e a respectiva janela de restrição. Os valores padrão DEVEM permitir 20 novas pendências em uma janela de 24 horas iniciada pela primeira criação contabilizada. O limite é absoluto durante a janela e NÃO DEVE ser ultrapassado por uma validação Turnstile bem-sucedida.
- **FR-REG-041**: Somente a criação efetiva de uma nova pendência DEVE consumir o limite por origem. Submissões rejeitadas antes da persistência, retomadas, reenvios, cancelamentos e repetições idempotentes que convergem para a mesma pendência NÃO DEVEM incrementar o contador. Depois da vigésima criação aceita no padrão, o sistema DEVE rejeitar temporariamente a tentativa seguinte dessa origem, inclusive quando houver Turnstile válido, informar o término da janela vigente e não aplicar bloqueio permanente.
- **FR-REG-042**: Quando o Turnstile for obrigatório e seu serviço de validação estiver indisponível, o sistema NÃO DEVE criar o cadastro e DEVE orientar a pessoa a tentar novamente posteriormente.
- **FR-REG-043**: O sistema DEVE oferecer cadastro por Google e validar no servidor a assinatura, o emissor, a audiência, a validade, a proteção contra repetição e a indicação de e-mail verificado antes de confiar na identidade recebida.
- **FR-REG-044**: O vínculo com Google DEVE usar a combinação imutável de emissor e `sub` como identificador externo. O e-mail recebido NÃO DEVE identificar esse vínculo, pois pode mudar no provedor.
- **FR-REG-045**: Quando não existir usuário ativo com o e-mail verificado pelo Google, o cadastro DEVE apresentar esse e-mail preenchido e não editável, considerá-lo comprovado e exigir somente os aceites legais antes da ativação.
- **FR-REG-046**: O cadastro por Google NÃO DEVE exigir senha local nem enviar confirmação adicional ao e-mail já verificado pelo provedor.
- **FR-REG-047**: Quando existir cadastro pendente com o mesmo e-mail normalizado, o fluxo Google DEVE reutilizar essa identidade, invalidar a senha local, as comprovações e os dados temporários anteriores, registrar novos aceites e somente então vinculá-la e ativá-la.
- **FR-REG-048**: Quando existir usuário ativo com o mesmo e-mail, o sistema NÃO DEVE vincular automaticamente a identidade Google. O vínculo somente poderá ocorrer após autenticação do usuário Rinos existente e confirmação explícita na feature de autenticação.
- **FR-REG-049**: Uma combinação de emissor e `sub` do Google NÃO DEVE ser vinculada a mais de um usuário Rinos.
- **FR-REG-050**: O cadastro Google DEVE solicitar somente os dados de identidade necessários para autenticação e e-mail, sem solicitar acesso a outros serviços ou dados Google.
- **FR-REG-051**: Se a identidade Google não puder ser validada, estiver expirada, tiver sido reutilizada ou não possuir e-mail verificado, o sistema NÃO DEVE criar nem ativar usuário.
- **FR-REG-052**: Se o serviço Google estiver indisponível antes da validação obrigatória, o sistema NÃO DEVE criar o cadastro por esse fluxo e DEVE permitir que a pessoa tente novamente ou utilize cadastro local.

> Decisões de infraestrutura aplicáveis: a feature mantém estado temporário de comprovação e integra serviços externos de validação anti-bot e identidade. Validade, uso único, idempotência, limitação de solicitações, exclusão automática, controle por origem e falha dos provedores estão definidos em FR-REG-013, FR-REG-015, FR-REG-019, FR-REG-023, FR-REG-024-INFRA-SCHED, FR-REG-032, FR-REG-034 a FR-REG-043 e FR-REG-051 a FR-REG-052. Tokens Google servem somente para validação transitória e NÃO DEVEM ser persistidos como credenciais locais.

### Key Entities

- **User**: identidade global e única de uma pessoa no Rinos; possui identificador interno, dados cadastrais, identificador primário, estado e histórico de mudanças relevantes.
- **Registration**: processo temporário que reúne os dados e o estado necessários para transformar uma solicitação aceita em usuário ativo.
- **Verification**: comprovação temporária, de uso único e prazo limitado, vinculada a um cadastro pendente.
- **Turnstile Validation**: evidência temporária e de uso único emitida pelo Cloudflare Turnstile e validada pelo sistema quando a política anti-bot exigir.
- **Credential**: segredo inicial que permitirá autenticação futura, protegido de forma não recuperável e indisponível para consulta.
- **External Identity**: vínculo entre um usuário Rinos e uma identidade de provedor externo, identificado de forma estável pelo emissor e pelo identificador atribuído pelo provedor.
- **Legal Consent**: evidência de que o usuário aceitou uma versão específica de documento obrigatório ou fez uma escolha opcional.

## Success Criteria

### Measurable Outcomes

- **SC-UR-001**: Em teste de usabilidade com no mínimo 10 participantes alheios ao desenvolvimento e compatíveis com o público não técnico, pelo menos 9 DEVEM concluir o envio inicial do cadastro em até três minutos, sem orientação durante a jornada. A amostra DEVE conter ao menos quatro execuções em telefone e quatro em desktop.
- **SC-UR-002**: Em amostra de 100 cadastros locais nominais contra servidor SMTP local controlado, ao menos 95 instruções de comprovação DEVEM ser aceitas em até dois minutos após o commit. Esse gate DEVE usar configuração de teste que permita ao menos 100 novas pendências por origem e verificação humana controlada, isolando a medição SMTP; os limites padrão e o Turnstile real permanecem validados separadamente. O ambiente real DEVE receber somente um smoke test de envio antes da liberação, sem que esse teste declare throughput ou capacidade suportada. Entrega final na caixa postal, bounce, classificação como spam e atrasos posteriores DEVEM ser tratados como métricas externas quando o provedor disponibilizar eventos e NÃO constituem gate de release sem infraestrutura capaz de comprová-los.
- **SC-UR-003**: Na mesma amostra mínima definida em `SC-UR-001`, pelo menos 9 de cada 10 participantes que iniciarem uma comprovação válida DEVEM concluir a ativação na primeira tentativa, sem orientação durante a jornada.
- **SC-UR-004**: Em 100% dos testes de duplicidade, concorrência e repetição, um mesmo identificador primário resulta em no máximo uma identidade vigente.
- **SC-UR-005**: Em 100% dos testes de segurança, comprovações inválidas, expiradas, utilizadas, canceladas ou pertencentes a outro cadastro não ativam usuário.
- **SC-UR-006**: Em 100% dos testes, um usuário recém-ativado acessa somente o próprio Painel de Usuário e não possui acesso a contas, tenants, dados de terceiros ou funcionalidades futuras ainda não concedidas.
- **SC-UR-007**: Todas as jornadas principais desta feature DEVEM atender à WCAG 2.2 nível AA, não apresentar violações automatizadas classificadas como críticas ou sérias e ser concluídas sem bloqueios em avaliação manual por teclado e leitor de tela. Falhas menores identificadas DEVEM ser documentadas e transformadas em tarefas antes da liberação.
- **SC-UR-008**: Em 100% dos testes nos quais o Turnstile é obrigatório, um cadastro com token ausente, inválido, expirado ou reutilizado é rejeitado sem criar identidade pendente.
- **SC-UR-009**: Em 100% dos testes, o limite configurado por IP é aplicado durante a janela definida e liberado automaticamente após seu término.
- **SC-UR-010**: Com o limiar anti-bot configurado como zero, 100% das tentativas de cadastro exigem validação bem-sucedida do Turnstile.
- **SC-UR-011**: Em 100% dos testes de cadastro Google válido sem usuário existente, o e-mail é considerado verificado, permanece não editável e a identidade é ativada após os aceites sem senha local ou confirmação adicional.
- **SC-UR-012**: Em 100% dos testes nos quais o Google corresponde a cadastro pendente, credenciais e comprovações anteriores são invalidadas antes da ativação, impedindo acesso por quem iniciou o cadastro original.
- **SC-UR-013**: Em 100% dos testes nos quais o e-mail Google corresponde a usuário ativo sem vínculo, nenhuma associação automática é criada.
- **SC-UR-014**: Antes da liberação em cada novo perfil de servidor de produção, o Argon2id DEVE ser calibrado com no mínimo 19.456 KiB de memória, duas iterações, paralelismo um, salt de 16 bytes e hash de 32 bytes. Depois do aquecimento da JVM, uma amostra mínima de 50 operações DEVE apresentar mediana entre 500 ms e um segundo e percentil 95 de até 1,5 segundo. Hardware, JVM, parâmetros, data e resultados DEVEM ser registrados, e a produção NÃO DEVE ser liberada quando o piso de segurança ou o limite de latência não for atendido.
- **SC-UR-015**: Em 100% dos testes com duas instâncias, somente a sessão com lease de manutenção vigente, `epoch` atual e estabilização concluída inicia cada lote de limpeza. Depois da expiração e nova eleição, eventual lote já iniciado pela sessão anterior conclui ou é abortado em no máximo cinco minutos; a nova sessão não começa antes dos 10 minutos de estabilização, não existe sobreposição de escritas e nenhuma ativação concorrente é excluída.
