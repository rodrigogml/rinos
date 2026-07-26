# Interface Specification: Cadastro e Ciclo Inicial do Usuário

**Feature**: `user-registration`
**Created**: 2026-07-25
**Status**: Approved
**Spec**: [Feature Specification](./spec.md)
**Plan**: [Implementation Plan](./plan.md)
**Surface Catalog**: [Interaction Surface Architecture](../../architecture/interaction-surfaces.md)

## Interface Coverage

| Surface ID | Type | Users | Coverage | Included Scope | Deferred or Excluded Scope |
|------------|------|-------|----------|----------------|----------------------------|
| SURF-WEB-RINOS | WEB | Pessoas não autenticadas iniciando, retomando, ativando ou cancelando o próprio cadastro | FULL | Cadastro local, continuação Google, ativação, reenvio, novos aceites obrigatórios, cancelamento e encaminhamento à recuperação | Login, recuperação de senha, sessão autenticada e conteúdo do Painel de Usuário pertencem às features posteriores |

## Current-State Evidence

| Surface ID | Existing Route, Command, or Component | Evidence | Current Behavior |
|------------|---------------------------------------|----------|------------------|
| SURF-WEB-RINOS | Aplicação hospedeira ainda não criada; rota pública desejada `/login` | [Implementation Plan](./plan.md) | Não existe interface executável no Rinos |
| SURF-WEB-RINOS | `RFWAccessComponent` e renderer padrão | `modules/RFW.Platform/src/main/java/br/eng/rodrigogml/rfw/platform/ui/access/` | Oferece cadastro, continuação externa, ativação, cancelamento, feedback, Google, Turnstile, i18n, tema e responsividade reutilizáveis |
| SURF-WEB-RINOS | Laboratório de acesso do showroom | `modules/RFW.Platform/modules/rfw.showroom/src/main/java/br/eng/rodrigogml/rfw/showroom/view/component/access/AccessDemonstrationComponent.java` | Demonstra as etapas e configurações públicas que o Rinos deverá compor |

O comportamento desejado reutiliza o componente existente, sem criar formulário paralelo. As extensões necessárias
para novo aceite durante a ativação, encaminhamento direto à recuperação e preservação seletiva do formulário foram
incorporadas ao RFW no commit `fb59049ef916f0854b53159542b71591db24cb8f`, conforme a
[RFW Compatibility Analysis](./rfw-gap-analysis.md).

## Interaction Inventory

| Interaction ID | Surface ID | Kind | Change Type | Name | Entry Point |
|----------------|------------|------|-------------|------|-------------|
| INT-WEB-REG-001 | SURF-WEB-RINOS | SCREEN | NEW | Cadastro local | Ação Criar conta na rota `/login` |
| INT-WEB-REG-002 | SURF-WEB-RINOS | SCREEN | NEW | Ativação e retomada do cadastro | Resultado do cadastro, link de e-mail ou entrada segura na rota `/login` |
| INT-WEB-REG-003 | SURF-WEB-RINOS | SCREEN | NEW | Conclusão do cadastro Google | Resultado tipado do Google na rota `/login` |
| INT-WEB-REG-004 | SURF-WEB-RINOS | SCREEN | NEW | Solicitação de cancelamento | Ação Cancelar cadastro na ativação |
| INT-WEB-REG-005 | SURF-WEB-RINOS | SCREEN | NEW | Confirmação do cancelamento | Resultado da solicitação ou link de confirmação |

## Interaction Details

### INT-WEB-REG-001 — Cadastro local

**Surface**: SURF-WEB-RINOS
**Surface Type**: WEB
**Change Type**: NEW
**Purpose**: Permitir que uma pessoa crie uma única identidade global pendente usando somente e-mail, senha e decisões sobre documentos legais.
**Actors and Permissions**: Pessoa não autenticada; rota anônima; nenhuma conta, tenant, papel, grupo ou permissão é criada ou consultada.
**Entry and Navigation**: A ação Criar conta da etapa de login abre `RFWAccessStepEnum.REGISTRATION` na mesma rota `/login`. Voltar retorna ao login sem persistir o formulário. Cadastro aceito abre `ACTIVATION`. E-mail já ativo oferece ação direta Recuperar senha com o e-mail preenchido, condicionada à capability futura de recuperação.
**Content and Data**: Título e explicação da finalidade; e-mail; senha; confirmação da senha; requisitos de senha visíveis antes do envio; documentos legais vigentes com obrigatório ou opcional; Turnstile quando exigido; ação principal Criar conta; ações secundárias Recuperar senha quando disponível e Voltar para entrar. Não solicitar nome, telefone, documento, conta ou empresa.
**Actions and Behavior**: Validar localmente presença, formato, confirmação da senha e aceites obrigatórios; submeter pelo `RFWRegistrationProvider`; aceitar colagem e gerenciadores; abrir documentos em nova aba sem perder o preenchimento; impedir submissões repetidas enquanto processa; preservar e-mail e aceites após rejeição recuperável, mas sempre limpar senha, confirmação e token Turnstile.
**Validation and Feedback**: Mostrar erro junto ao campo e resumo com `role="alert"`; focar o primeiro campo inválido; explicar cada regra de senha não atendida; informar explicitamente e-mail existente sem revelar outros dados; informar indisponibilidade do verificador de senha, Turnstile ou cadastro e permitir nova tentativa segura; informar bloqueio temporário e tempo restante; renovar Turnstile sem apagar e-mail e aceites.
**Responsive/Adaptive Behavior**: Card central limitado pelo token público do RFW em desktop e tablet; em telefone ocupa a largura disponível com espaçamento seguro, campos e ações empilhados e sem rolagem horizontal; teclado virtual não encobre campo ou ação; links legais quebram linha; mouse, toque e teclado possuem o mesmo alcance funcional.
**Accessibility**: Um `main` e um título de nível coerente; labels persistentes; instruções da senha associadas ao campo; required e invalid expostos semanticamente; ordem de foco e-mail, senha, confirmação, aceites, Turnstile quando aplicável, ação principal e ações secundárias; foco vai ao primeiro erro; mensagens assíncronas anunciadas; alvos de toque adequados; zoom e reflow sem perda; não depender de cor, animação ou CAPTCHA visual isolado.
**Localization**: Todos os textos usam o prefixo i18n do Rinos sobre o contrato do RFW; primeiro locale `pt-BR`; suportar expansão de texto e futuras traduções; não interpolar e-mail, IP ou detalhes técnicos em mensagens genéricas; documentos exibem seus títulos vigentes e links localizados quando disponíveis.
**Components and Design System**: `RFWAccessComponent`, `RFWAccessComponentFactory`, renderer padrão de `REGISTRATION`, `RFWLegalDocumentVO`, `RFWTurnstileComponent`, feedback e tokens públicos do RFW. O renderer oferece recuperação direta e restaura somente e-mail e IDs de documentos; não criar cópia local do formulário.
**Integration and Contracts**: `RFWRegistrationProvider.register(RFWRegistrationRequestDTO)`, política `RFWHumanVerificationRequirementProvider`, Siteverify, Pwned Passwords e SMTP descritos em [External Services](./contracts/external-services.md). DTO contém apenas e-mail, senha efêmera, IDs aceitos e token efêmero.
**Telemetry**: Eventos `registration_local_viewed`, `registration_local_submitted`, `registration_local_rejected` e `registration_local_accepted`; propriedades limitadas a resultado público, regra de validação, Turnstile exigido ou não, duração e correlation ID técnico; nunca registrar e-mail, senha, token, prova, IP bruto ou conteúdo de documento.
**Wireframe Requirement**: REQUIRED
**Wireframe**: Embedded em Wireframes, fluxo INT-WEB-REG-001

**States**:

| State | Expected Presentation | Available Actions | Transition/Exit |
|-------|-----------------------|-------------------|-----------------|
| initial | Card de cadastro com campos vazios, requisitos e documentos vigentes; Turnstile conforme política da origem | Preencher, abrir documento, voltar | Primeira interação leva a ready |
| loading | N/A — documentos e capabilities devem estar resolvidos antes de renderizar a view; falha de composição impede publicar a rota | N/A | N/A |
| empty | N/A — formulário não representa coleção vazia | N/A | N/A |
| ready | Dados editáveis, ação principal habilitada e nenhum segredo fora dos campos efêmeros | Criar conta, abrir documento, recuperar senha quando disponível, voltar | Validação local ou processing |
| processing | Card com `aria-busy`; controles bloqueados contra repetição; senha e token já removidos do estado reutilizável | Aguardar | Activation, validation-error ou remote-error |
| success | Confirma que a instrução foi enviada ao e-mail informado e abre a etapa de ativação | Informar código, reenviar, cancelar, voltar | INT-WEB-REG-002 |
| validation-error | Campos inválidos identificados; e-mail e aceites preservados; senha e Turnstile devem ser reinformados | Corrigir e reenviar, abrir documento, recuperar senha quando aplicável | ready ou processing |
| remote-error | Mensagem pública acionável; sem identidade ativa parcial; e-mail e aceites preservados quando seguro | Tentar novamente ou voltar | ready, processing ou login |
| offline | Overlay de desconexão do Vaadin; nenhuma submissão presumida; valores não secretos permanecem apenas durante reconexão segura da mesma UI | Reconectar ou voltar depois | ready; se a sessão expirou, initial |
| access-denied | N/A — etapa é pública; requisição inválida é tratada como erro, não como autorização | N/A | N/A |
| partial-stale | Documentos mudaram durante o preenchimento; aviso informa atualização, substitui versões e remove aceites obsoletos; senha permanece descartada | Ler e aceitar versões atuais, reenviar | ready ou processing |

### INT-WEB-REG-002 — Ativação e retomada do cadastro

**Surface**: SURF-WEB-RINOS
**Surface Type**: WEB
**Change Type**: NEW
**Purpose**: Confirmar a prova de e-mail, retomar um cadastro pendente, solicitar nova prova e ativar a identidade uma única vez.
**Actors and Permissions**: Pessoa não autenticada que controla a prova enviada ao e-mail do cadastro; nenhuma consulta a outros usuários ou tenants.
**Entry and Navigation**: Aberta após cadastro local aceito ou por deep link HTTPS para `/login` com etapa e prova opaca. A URL não contém e-mail nem ID interno. Ação Voltar retorna ao login. Ativação concluída publica a autenticação e navega ao Painel de Usuário.
**Content and Data**: Explicação de validade; identificador apenas quando necessário para código manual; código ou prova de ativação; destino mascarado quando seguro; expiração; ação Ativar; Reenviar confirmação; Cancelar cadastro; Voltar para entrar. Se os documentos obrigatórios mudaram, uma continuação separada mostra somente as novas versões antes da ativação.
**Actions and Behavior**: Consumir a prova uma única vez; aceitar link ou código; reenvio invalida provas anteriores e respeita três solicitações por quinze minutos; repetição de prova já concluída não repete efeitos; novo aceite legal usa referência opaca própria; cancelamento abre INT-WEB-REG-004.
**Validation and Feedback**: Diferenciar instrução ausente, inválida, expirada, usada e cadastro encerrado sem expor dados adicionais; oferecer reenvio quando permitido; informar tempo restante de limite; falha de SMTP mantém a pendência e permite nova tentativa; mudança legal impede ativação até aceite das versões vigentes.
**Responsive/Adaptive Behavior**: Mesmo shell responsivo do RFW; códigos usam campo de largura confortável e teclado apropriado; ações secundárias quebram em linhas ou empilham no telefone; links de e-mail abrem a mesma experiência responsiva.
**Accessibility**: Foco inicial no código quando a prova não veio preenchida e na ação Ativar quando veio; autocomplete `one-time-code`; avisos de expiração e reenvio anunciados; contagem de bloqueio textual; confirmação de documento operável por teclado; foco preservado ou movido ao primeiro novo aceite obrigatório.
**Localization**: Datas e expiração apresentadas no locale, persistidas em UTC; mensagens de prova e limite usam pluralização; não revelar prova, e-mail completo ou estado interno; títulos legais usam as versões vigentes.
**Components and Design System**: `RFWAccessComponent`, renderers padrão de `ACTIVATION` e `ACTIVATION_CONSENT`, `RFWAccessEntryRequestVO`, feedback, ações e tokens RFW.
**Integration and Contracts**: `RFWRegistrationProvider.activate(RFWActivationRequestDTO)` e `resendActivation(String)`; `RFWActivationConsentProvider.completeActivationConsent(RFWActivationConsentRequestDTO)` com `RFWActivationConsentChallengeVO`; SMTP em [External Services](./contracts/external-services.md). A continuação usa referência opaca e IDs de documentos, sem reutilizar credencial externa.
**Telemetry**: Eventos `registration_activation_viewed`, `registration_activation_submitted`, `registration_activation_resend_requested`, `registration_activation_consent_required` e `registration_activated`; registrar resultado, idade aproximada da prova, limite e duração; excluir identificador e prova.
**Wireframe Requirement**: REQUIRED
**Wireframe**: Embedded em Wireframes, fluxo INT-WEB-REG-002

**States**:

| State | Expected Presentation | Available Actions | Transition/Exit |
|-------|-----------------------|-------------------|-----------------|
| initial | Código preenchido pelo deep link ou campo vazio para entrada manual; explicação e ações secundárias | Informar prova, ativar, reenviar, cancelar, voltar | ready |
| loading | N/A — a etapa renderiza com a entrada tipada já disponível | N/A | N/A |
| empty | N/A — ausência de prova é estado inicial validável | N/A | N/A |
| ready | Prova editável e ações disponíveis conforme capability e limite | Ativar, reenviar, cancelar, voltar | processing ou validation-error |
| processing | Prova removida do campo e controles bloqueados; estado ocupado anunciado | Aguardar | success, validation-error, remote-error ou partial-stale |
| success | Identidade ativa; confirmação breve antes da navegação segura | Continuar automaticamente ao Painel de Usuário | Painel de Usuário |
| validation-error | Prova ausente ou inválida identificada; orientação oferece reenvio quando cabível | Corrigir, reenviar, cancelar, voltar | ready ou processing |
| remote-error | Indisponibilidade ou falha de envio explicada sem duplicar cadastro | Tentar novamente, reenviar quando permitido, voltar | ready ou login |
| offline | Nenhuma prova é considerada consumida pelo cliente; framework informa desconexão | Reconectar | ready ou initial se a UI expirou |
| access-denied | N/A — etapa é pública; prova não autorizada é tratada como inválida | N/A | N/A |
| partial-stale | Prova válida, mas uma versão legal obrigatória mudou; tela mostra somente novos aceites e mantém referência opaca da continuação | Abrir documentos, aceitar e concluir, voltar | processing ou success |

### INT-WEB-REG-003 — Conclusão do cadastro Google

**Surface**: SURF-WEB-RINOS
**Surface Type**: WEB
**Change Type**: NEW
**Purpose**: Concluir com aceites legais o cadastro iniciado por uma identidade Google já validada, sem senha local nem nova confirmação de e-mail.
**Actors and Permissions**: Pessoa não autenticada que concluiu o protocolo Google; acesso limitado à referência opaca de continuação emitida para aquela tentativa.
**Entry and Navigation**: O Google é iniciado na etapa de login do `RFWAccessComponent`. Resultado `EXTERNAL_REGISTRATION_REQUIRED` abre automaticamente `EXTERNAL_REGISTRATION` na mesma rota. Voltar descarta a continuação visual e retorna ao login. Conclusão autentica e navega ao Painel de Usuário.
**Content and Data**: Provedor Google identificado; e-mail verificado visível e somente leitura; documentos legais vigentes; ação Concluir cadastro; Voltar para entrar. Não mostrar senha, confirmação de e-mail, perfil Google adicional ou permissões a outros serviços.
**Actions and Behavior**: Aceitar documentos obrigatórios; permitir decisões opcionais separadas; enviar somente referência opaca e IDs aceitos; revalidar expiração, uso único, emissor e subject no backend; ao reutilizar pendência invalidar senha e provas anteriores antes de ativar; usuário ativo com mesmo e-mail não é vinculado e segue ao fluxo futuro de reautenticação.
**Validation and Feedback**: E-mail não pode ser editado; aceites inválidos focam o primeiro documento; referência expirada, usada, identidade conflitante ou Google inválido não cria usuário; indisponibilidade oferece tentar Google novamente ou usar cadastro local; nunca revelar outro usuário relacionado ao e-mail ou à identidade externa.
**Responsive/Adaptive Behavior**: Card e documentos seguem o mesmo reflow do cadastro local; e-mail somente leitura continua selecionável e legível; ações empilham no telefone; popup ou redirecionamento Google retorna ao mesmo contexto responsivo.
**Accessibility**: E-mail anunciado como somente leitura; nome do provedor e finalidade descritos; checkboxes e links legais rotulados; foco inicial no primeiro aceite obrigatório ou na ação principal se todos já estiverem satisfeitos; retorno de foco adequado após falha do Google; sem dependência exclusiva do popup.
**Localization**: Textos e documentos usam locale ativo; nome Google permanece marca do provedor; mensagens não incluem subject, issuer, token ou e-mail completo em telemetria; suportar expansão de texto.
**Components and Design System**: `RFWGoogleSignInComponent`, `RFWExternalIdentityProvider`, `RFWAccessComponent`, `RFWExternalRegistrationChallengeVO`, renderer `EXTERNAL_REGISTRATION`, `RFWLegalDocumentVO` e tokens públicos do RFW.
**Integration and Contracts**: `RFWExternalIdentityResolver`, `RFWAuthenticationOutcomeVO.externalRegistrationRequired`, `RFWExternalRegistrationProvider.completeExternalRegistration` e [Google OpenID Connect](./contracts/external-services.md). A combinação persistida é issuer e subject; o ID token nunca chega ao formulário de conclusão.
**Telemetry**: Eventos `registration_google_started`, `registration_google_continuation_viewed`, `registration_google_submitted`, `registration_google_rejected` e `registration_google_activated`; registrar provider ID, classe pública do resultado e duração, sem token, nonce, issuer, subject ou e-mail.
**Wireframe Requirement**: REQUIRED
**Wireframe**: Embedded em Wireframes, fluxo INT-WEB-REG-003

**States**:

| State | Expected Presentation | Available Actions | Transition/Exit |
|-------|-----------------------|-------------------|-----------------|
| initial | Continuação com e-mail verificado bloqueado e documentos vigentes | Abrir documento, marcar decisões, concluir, voltar | ready |
| loading | O botão Google usa o estado de processamento do RFW antes desta etapa; nesta tela a referência já está resolvida | Aguardar somente se o retorno ainda estiver sendo processado | initial ou remote-error |
| empty | N/A — ausência da challenge é erro de integração, não estado vazio válido | N/A | remote-error |
| ready | E-mail somente leitura, aceites editáveis e ação principal disponível | Concluir, abrir documento, voltar | processing ou validation-error |
| processing | Controles bloqueados; referência e aceites submetidos; nenhuma credencial externa mantida na UI | Aguardar | success, validation-error ou remote-error |
| success | Identidade ativa pelo Google e autenticação publicada | Continuar ao Painel de Usuário | Painel de Usuário |
| validation-error | Aceites ausentes ou versões inválidas destacados; identidade não ativada | Corrigir e reenviar | ready ou processing |
| remote-error | Google ou continuação indisponível, expirada ou conflitante; mensagem segura | Tentar Google novamente, usar cadastro local, voltar | login ou INT-WEB-REG-001 |
| offline | Retorno não confirmado permanece sem efeito; referência deve ser revalidada após reconexão | Reconectar ou reiniciar Google | ready, login ou remote-error |
| access-denied | N/A — referência inválida é rejeitada como continuação, sem revelar autorização | N/A | N/A |
| partial-stale | Documento mudou enquanto a continuação estava aberta; versões atuais substituem as antigas e exigem nova decisão | Ler, aceitar e reenviar | ready ou processing |

### INT-WEB-REG-004 — Solicitação de cancelamento

**Surface**: SURF-WEB-RINOS
**Surface Type**: WEB
**Change Type**: NEW
**Purpose**: Permitir que a pessoa solicite uma prova para cancelar um cadastro ainda pendente sem revelar publicamente se ele existe.
**Actors and Permissions**: Pessoa não autenticada; o controle do e-mail ainda não está comprovado nesta etapa.
**Entry and Navigation**: Ação Cancelar cadastro em INT-WEB-REG-002 abre `REGISTRATION_CANCELLATION_REQUEST`, preenchendo o identificador quando já conhecido. Voltar retorna ao login; resposta que exige confirmação abre INT-WEB-REG-005.
**Content and Data**: Explicação das consequências; identificador; Turnstile conforme política; ação Solicitar cancelamento; Voltar para entrar. A mensagem pública da solicitação permanece neutra.
**Actions and Behavior**: Validar identificador; aplicar política Turnstile da operação `REGISTRATION_CANCELLATION`; solicitar prova; não cancelar antes da confirmação; impedir repetição durante processamento; preservar identificador em rejeição recuperável e descartar token.
**Validation and Feedback**: Erro junto ao identificador; indisponibilidade e limitação com orientação; resposta neutra não confirma existência, estado ou destino; se houver pendência válida, enviar prova de uso único; Turnstile inválido é renovado.
**Responsive/Adaptive Behavior**: Mesmo card; ações empilham em telefone; teclado virtual e touch não encobrem o botão; desafio mantém alternativa acessível da Cloudflare.
**Accessibility**: Consequência explicada antes da ação; foco no identificador; erros anunciados; ordem identificador, Turnstile, solicitar e voltar; nenhuma confirmação depende apenas de cor; estado ocupado anunciado.
**Localization**: Mensagens neutras e tempo de bloqueio localizados; termos cancelar cadastro e excluir pendência são consistentes; não interpolar identificador completo na confirmação pública.
**Components and Design System**: `RFWAccessComponent`, renderer `REGISTRATION_CANCELLATION_REQUEST`, `RFWTurnstileComponent`, feedback e tokens RFW.
**Integration and Contracts**: `RFWRegistrationCancellationProvider.requestCancellation`, `RFWRegistrationCancellationRequestDTO` e validação Turnstile; SMTP envia a prova quando aplicável.
**Telemetry**: Eventos `registration_cancellation_requested` e `registration_cancellation_request_rejected`; registrar resultado, Turnstile exigido, limite e duração; excluir identificador, IP bruto, token e informação sobre existência.
**Wireframe Requirement**: REQUIRED
**Wireframe**: Embedded em Wireframes, fluxo INT-WEB-REG-004

**States**:

| State | Expected Presentation | Available Actions | Transition/Exit |
|-------|-----------------------|-------------------|-----------------|
| initial | Explicação, identificador preenchido quando conhecido e Turnstile condicional | Editar, solicitar, voltar | ready |
| loading | N/A — política e capabilities são resolvidas antes da renderização | N/A | N/A |
| empty | N/A — identificador vazio é validação do formulário | N/A | N/A |
| ready | Identificador editável e ação disponível | Solicitar cancelamento, voltar | processing ou validation-error |
| processing | Controles bloqueados e token consumido | Aguardar | success, validation-error ou remote-error |
| success | Mensagem neutra informa que instruções serão enviadas quando aplicável; challenge válida abre confirmação | Informar prova ou voltar | INT-WEB-REG-005 ou login |
| validation-error | Identificador ou desafio inválido indicado sem revelar existência | Corrigir, renovar desafio, reenviar | ready ou processing |
| remote-error | Serviço indisponível ou limitado; nenhuma alteração aplicada | Tentar novamente depois ou voltar | ready ou login |
| offline | Solicitação sem resposta não é presumida; identificador pode ser restaurado na mesma UI | Reconectar | ready ou initial |
| access-denied | N/A — operação pública; identidade inexistente recebe resposta neutra | N/A | N/A |
| partial-stale | N/A — a solicitação não exibe dados versionados | N/A | N/A |

### INT-WEB-REG-005 — Confirmação do cancelamento

**Surface**: SURF-WEB-RINOS
**Surface Type**: WEB
**Change Type**: NEW
**Purpose**: Confirmar controle do e-mail e cancelar definitivamente o cadastro pendente, invalidando todas as provas abertas.
**Actors and Permissions**: Pessoa não autenticada em posse da prova de cancelamento válida e de uso único.
**Entry and Navigation**: Aberta pelo resultado tipado `REGISTRATION_CANCELLATION_REQUIRED` ou por deep link HTTPS sem ID interno. Voltar retorna ao login sem cancelar. Sucesso apresenta confirmação e oferece iniciar novo cadastro ou entrar.
**Content and Data**: Identificador quando necessário; código ou prova de cancelamento; consequência irreversível para a pendência; ação principal Confirmar cancelamento; Voltar para entrar.
**Actions and Behavior**: Exigir confirmação explícita por prova; limpar prova do campo ao submeter; cancelar apenas se a pendência continuar válida; invalidar provas de ativação e cancelamento; repetição não restaura nem repete efeitos; não oferecer desfazer.
**Validation and Feedback**: Prova ausente, inválida, expirada, usada ou de processo encerrado não cancela nada; mensagem orienta nova solicitação quando aplicável; sucesso informa que provas anteriores não funcionam mais; falha transacional mantém estado anterior integral.
**Responsive/Adaptive Behavior**: Card responsivo; explicação e ação destrutiva permanecem próximas; botões empilham no telefone; nenhuma confirmação depende de hover.
**Accessibility**: Título e descrição anunciam a consequência; foco inicial na prova; autocomplete `one-time-code`; ação possui nome inequívoco Confirmar cancelamento; erro anunciado e foco devolvido ao campo; reduzir animações conforme preferência.
**Localization**: Linguagem direta sem códigos internos; datas e expiração localizadas quando exibidas; ação destrutiva não usa texto ambíguo; não registrar prova ou identificador.
**Components and Design System**: `RFWAccessComponent`, renderer `REGISTRATION_CANCELLATION_CONFIRMATION`, feedback, botões e tokens públicos RFW.
**Integration and Contracts**: `RFWRegistrationCancellationProvider.confirmCancellation`, `RFWRegistrationCancellationConfirmationDTO` e `RFWAuthenticationOutcomeVO.completed`.
**Telemetry**: Eventos `registration_cancellation_confirmed` e `registration_cancellation_rejected`; registrar classe pública do resultado, motivo seguro e duração; excluir identificador, prova e IDs persistentes.
**Wireframe Requirement**: REQUIRED
**Wireframe**: Embedded em Wireframes, fluxo INT-WEB-REG-005

**States**:

| State | Expected Presentation | Available Actions | Transition/Exit |
|-------|-----------------------|-------------------|-----------------|
| initial | Consequência, identificador seguro e campo de prova preenchido pelo link quando disponível | Informar prova, confirmar, voltar | ready |
| loading | N/A — entrada tipada já está disponível ao renderizar | N/A | N/A |
| empty | N/A — prova vazia é validação do formulário | N/A | N/A |
| ready | Prova editável, ação inequívoca e retorno sem efeito | Confirmar cancelamento, voltar | processing ou validation-error |
| processing | Prova removida do campo e controles bloqueados | Aguardar | success, validation-error ou remote-error |
| success | Confirma cancelamento e invalidação das provas anteriores | Criar novo cadastro ou voltar para entrar | INT-WEB-REG-001 ou login |
| validation-error | Prova inválida identificada; cadastro permanece pendente | Corrigir ou solicitar nova prova | ready ou INT-WEB-REG-004 |
| remote-error | Falha íntegra sem cancelamento parcial | Tentar novamente ou voltar | ready ou login |
| offline | Nenhum cancelamento é presumido sem resposta; prova deve ser revalidada | Reconectar | ready, success idempotente ou validation-error |
| access-denied | N/A — prova inválida é tratada como rejeição segura | N/A | N/A |
| partial-stale | Cadastro ativado, expirado ou cancelado em outra tentativa; informar que a operação já não se aplica sem expor detalhes adicionais | Voltar ou iniciar novo cadastro quando permitido | login ou INT-WEB-REG-001 |

## Cross-Surface Rules

### Navigation and Parity

Existe uma única superfície humana. A rota pública `/login` hospeda uma instância do `RFWAccessComponent` e troca
etapas sem criar páginas paralelas. Deep links transportam somente intenção e prova opaca. Após autenticação concluída,
o callback seguro do RFW navega ao Painel de Usuário. O botão Voltar do navegador não pode reenviar operações nem
restaurar senha, token ou prova já consumida.

Fluxo principal:

```text
login
  -> cadastro local -> ativação -> Painel de Usuário
  -> Google -> aceites do cadastro externo -> Painel de Usuário

ativação
  -> reenvio
  -> novos aceites, somente quando versões mudaram
  -> solicitação de cancelamento -> confirmação -> resultado
```

### Shared Content and Terminology

- Usar usuário para a identidade global já criada e cadastro para o processo temporário.
- Usar e-mail, criar conta, ativar cadastro, reenviar confirmação, cancelar cadastro e recuperar senha.
- Não chamar conta de tenant, empresa ou organização nesta jornada.
- Mensagens públicas podem informar que um e-mail já existe somente no cadastro, conforme decisão funcional explícita.
- Nenhuma outra mensagem revela existência, estado, vínculo Google ou dados de usuário.
- A interface não expõe IDs técnicos, códigos internos de erro, issuer, subject ou correlation ID.

### Shared Accessibility and Input

- Operação completa por teclado, toque, mouse e leitor de tela.
- Um único foco lógico por transição; primeiro erro recebe foco; mensagens assíncronas usam região de alerta.
- Campos preservam labels visíveis, autocomplete adequado e instruções associadas.
- Componentes respeitam contraste, zoom, reflow, tema claro/escuro e `prefers-reduced-motion`.
- Turnstile não pode ser a única informação visual nem bloquear tecnologia assistiva.
- Segredos são limpos antes da operação assíncrona e nunca retornam ao DOM após re-renderização.

### Shared Security and Privacy

- Senhas, confirmações, ID tokens Google, nonces, tokens Turnstile, códigos e provas são efêmeros.
- E-mail e aceites podem ser preservados apenas na mesma tentativa e somente para recuperar erro.
- URLs não contêm e-mail, ID de usuário, ID de cadastro ou estado interno.
- Toda operação de escrita é revalidada no servidor; controle visual não substitui autorização ou constraint.
- Telemetria usa resultado público e correlation ID técnico sem PII.

## Traceability

| Interaction ID | User Stories | Functional Requirements | Success Criteria | Contracts |
|----------------|--------------|-------------------------|------------------|-----------|
| INT-WEB-REG-001 | User Story 1, User Story 2 | FR-USR-003 a FR-USR-005, FR-USR-010 a FR-USR-012, FR-REG-001 a FR-REG-011, FR-REG-028, FR-REG-030 a FR-REG-042 | SC-UR-001, SC-UR-004, SC-UR-007 a SC-UR-010 | [External Services](./contracts/external-services.md), `RFWRegistrationProvider`, `RFWHumanVerificationRequirementProvider` |
| INT-WEB-REG-002 | User Story 1, User Story 2 | FR-USR-006 a FR-USR-009, FR-USR-013 a FR-USR-015, FR-REG-012 a FR-REG-024-INFRA-SCHED, FR-REG-029 a FR-REG-033 | SC-UR-002 a SC-UR-006 | [External Services](./contracts/external-services.md), `RFWRegistrationProvider`, `RFWActivationConsentProvider` |
| INT-WEB-REG-003 | User Story 1 | FR-USR-003 a FR-USR-005, FR-USR-011, FR-USR-015, FR-REG-016 a FR-REG-020, FR-REG-043 a FR-REG-052 | SC-UR-004 a SC-UR-007, SC-UR-011 a SC-UR-013 | [External Services](./contracts/external-services.md), `RFWExternalIdentityResolver`, `RFWExternalRegistrationProvider` |
| INT-WEB-REG-004 | User Story 3 | FR-REG-025 a FR-REG-030, FR-REG-034 a FR-REG-042 | SC-UR-005, SC-UR-007 a SC-UR-010 | `RFWRegistrationCancellationProvider.requestCancellation`, `RFWHumanVerificationRequirementProvider` |
| INT-WEB-REG-005 | User Story 3 | FR-USR-006, FR-USR-007, FR-USR-012, FR-REG-025 a FR-REG-030 | SC-UR-004, SC-UR-005, SC-UR-007 | `RFWRegistrationCancellationProvider.confirmCancellation`, `RFWAuthenticationOutcomeVO` |

## Wireframes

Os wireframes são de baixa fidelidade. O renderer e os tokens do RFW continuam sendo a fonte visual; os diagramas
registram hierarquia, ordem de leitura e transformação responsiva.

### INT-WEB-REG-001

```text
Desktop e tablet                         Telefone
┌──────────────────────────────┐         ┌──────────────────────┐
│ Marca Rinos                  │         │ Marca Rinos          │
│ Criar sua conta              │         │ Criar sua conta      │
│ Finalidade dos dados         │         │ Finalidade dos dados │
│ E-mail                       │         │ E-mail               │
│ Senha                        │         │ Senha                │
│ Confirme a senha             │         │ Confirme a senha     │
│ Requisitos da senha          │         │ Requisitos da senha  │
│ □ Termos de uso              │         │ □ Termos de uso      │
│ □ Política de privacidade    │         │ □ Política...        │
│ □ Comunicação opcional      │         │ □ Comunicação...     │
│ Turnstile, se obrigatório    │         │ Turnstile            │
│ ( Criar conta )              │         │ ( Criar conta )      │
│ Recuperar senha              │         │ Recuperar senha      │
│ Voltar para entrar           │         │ Voltar para entrar   │
│ Idioma   Tema                │         │ Idioma   Tema        │
└──────────────────────────────┘         └──────────────────────┘
```

### INT-WEB-REG-002

```text
┌──────────────────────────────┐
│ Ative seu cadastro           │
│ Instrução e validade         │
│ E-mail, quando necessário    │
│ Código de ativação           │
│ ( Ativar cadastro )          │
│ Reenviar confirmação         │
│ Cancelar cadastro            │
│ Voltar para entrar           │
└──────────────────────────────┘

Quando houver nova versão legal:
┌──────────────────────────────┐
│ Atualizamos os documentos    │
│ □ Nova versão obrigatória    │
│ ( Aceitar e ativar )         │
│ Voltar para entrar           │
└──────────────────────────────┘
```

### INT-WEB-REG-003

```text
┌──────────────────────────────┐
│ Conclua o cadastro Google    │
│ E-mail verificado            │
│ usuario@exemplo.com          │ somente leitura
│ □ Termos de uso              │
│ □ Política de privacidade    │
│ □ Comunicação opcional      │
│ ( Concluir cadastro )        │
│ Voltar para entrar           │
└──────────────────────────────┘
```

### INT-WEB-REG-004

```text
┌──────────────────────────────┐
│ Cancelar cadastro pendente   │
│ Consequência e confirmação   │
│ E-mail                       │
│ Turnstile, se obrigatório    │
│ ( Solicitar cancelamento )   │
│ Voltar para entrar           │
└──────────────────────────────┘
```

### INT-WEB-REG-005

```text
┌──────────────────────────────┐
│ Confirme o cancelamento      │
│ Consequência irreversível    │
│ E-mail, quando necessário    │
│ Código de cancelamento       │
│ ( Confirmar cancelamento )   │
│ Voltar para entrar           │
└──────────────────────────────┘
```

## Validation Summary

- Coverage matrix reviewed: yes
- All inventory items detailed: yes
- Canonical states resolved: yes
- Required wireframes present: yes
- Accessibility requirements resolved: yes
- Contract mappings verified: yes — contratos finais disponíveis no RFW `fb59049ef916f0854b53159542b71591db24cb8f`
- Placeholders or open decisions remaining: 0
- Structural validator: PASS em 2026-07-25
- Semantic gate: PASS em 2026-07-26
- Visual validation: laboratório compilado e testes de componente aprovados; inspeção manual permanece recomendada
  antes da implementação porque nenhum navegador controlável estava disponível nesta sessão
