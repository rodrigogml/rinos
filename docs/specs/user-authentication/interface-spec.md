# Interface Specification: Autenticação e Recuperação do Usuário

**Feature**: `user-authentication`
**Created**: 2026-08-08
**Status**: Approved for quality checklist
**Spec**: [Feature Specification](./spec.md)
**Plan**: [Implementation Plan](./plan.md)
**Surface Catalog**: [Interaction Surface Architecture](../../architecture/interaction-surfaces.md)

## Interface Coverage

| Surface ID | Type | Users | Coverage | Included Scope | Deferred or Excluded Scope |
|------------|------|-------|----------|----------------|----------------------------|
| SURF-WEB-RINOS | WEB | Pessoas não autenticadas, usuários ativos e administradores sujeitos a garantia adicional | FULL | Login por senha/passkey/Google, MFA, aceite legal pós-login, recuperação, reautenticação e gestão de métodos/sessões | Conteúdo geral do painel, alteração de e-mail, contas, tenants e autorizações pertencem a outras features |

## Current-State Evidence

| Surface ID | Existing Route, Command, or Component | Evidence | Current Behavior |
|------------|---------------------------------------|----------|------------------|
| `SURF-WEB-RINOS` | `/login` | `src/main/java/br/com/rinos/app/ui/module/identity/view/LoginView.java` | Hospeda `RFWAccessComponent`, trata ativação/redefinição por deep link e navega para `/user` após outcome autenticado |
| `SURF-WEB-RINOS` | composição de acesso | `src/main/java/br/com/rinos/app/ui/module/identity/component/RinosAccessComponentFactory.java` | Configura cadastro/documentos e callback; não existe provider real de login, MFA, sessão ou gestão |
| `SURF-WEB-RINOS` | `/user` | `src/main/java/br/com/rinos/app/ui/module/user/view/UserDashboardEntryView.java` | Rota protegida reservada, ainda sem conteúdo |
| `SURF-WEB-RINOS` | recuperação mínima | `src/main/java/br/com/rinos/app/ui/config/RFWPasswordRecoveryProviderAdapter.java` | Solicitação e redefinição já operam no componente de acesso, sem gestão concreta de sessões |
| `SURF-WEB-RINOS` | componentes RFW | `modules/RFW.Platform/src/main/java/br/eng/rodrigogml/rfw/ui/access/` e `ui/securitysettings/` | Entrega shell, renderers e gestão genérica; lacunas desta feature estão em [RFW Gap Analysis](./rfw-gap-analysis.md) |
| `SURF-WEB-RINOS` | showroom RFW | `modules/RFW.Platform/modules/rfw.showroom/src/main/resources/showroom/content/access/` | Documenta integração, providers, autenticação e configurações de segurança reutilizáveis |

Não há captura visual nova porque as capabilities de autenticação ainda não estão registradas no Rinos; executar a
rota atual mostraria somente o estado parcial de cadastro/recuperação. Os paths acima e o laboratório do showroom são
a evidência verificável do estado atual. O comportamento desejado não cria formulário paralelo.

## Interaction Inventory

| Interaction ID | Surface ID | Kind | Change Type | Name | Entry Point |
|----------------|------------|------|-------------|------|-------------|
| INT-WEB-AUTH-001 | SURF-WEB-RINOS | SCREEN | MODIFIED | Entrar no Rinos | `/login` ou redirecionamento de rota protegida |
| INT-WEB-AUTH-002 | SURF-WEB-RINOS | SCREEN | NEW | Confirmar segundo fator | Continuação do primeiro fator em `/login` |
| INT-WEB-AUTH-003 | SURF-WEB-RINOS | SCREEN | NEW | Aceitar documento obrigatório após autenticação | Continuação tipada antes da sessão |
| INT-WEB-AUTH-004 | SURF-WEB-RINOS | SCREEN | MODIFIED | Recuperar acesso e redefinir senha | Ação “Esqueci minha senha” ou deep link `/login?step=password-reset&proof=...` |
| INT-WEB-AUTH-005 | SURF-WEB-RINOS | SCREEN | NEW | Configurações de segurança | `/user/security` a partir do Painel de Usuário |
| INT-WEB-AUTH-006 | SURF-WEB-RINOS | DIALOG | NEW | Gerenciar segundo fator e códigos de recuperação | Seções de fatores/recuperação em `/user/security` |
| INT-WEB-AUTH-007 | SURF-WEB-RINOS | PANEL | NEW | Gerenciar passkeys | Seção Passkeys em `/user/security` |
| INT-WEB-AUTH-008 | SURF-WEB-RINOS | PANEL | NEW | Gerenciar vínculo Google e senha | Seção Métodos de acesso em `/user/security` |
| INT-WEB-AUTH-009 | SURF-WEB-RINOS | PANEL | NEW | Reconhecer e encerrar sessões | Seção Sessões em `/user/security` |
| INT-WEB-AUTH-010 | SURF-WEB-RINOS | DIALOG | NEW | Reautenticar para operação sensível | Ação protegida com autenticação recente insuficiente |

## Interaction Details

### INT-WEB-AUTH-001 — Entrar no Rinos

**Surface**: SURF-WEB-RINOS
**Surface Type**: WEB
**Change Type**: MODIFIED
**Purpose**: Comprovar uma identidade global por senha, passkey ou Google sem revelar usuários ou criar contexto de conta.
**Actors and Permissions**: Pessoa não autenticada. A rota é anônima; sucesso comprova identidade e garantia, mas não concede papel, grupo, chave ou permissão.
**Entry and Navigation**: `/login` é canônica. Rota protegida preserva destino interno permitido e retorna somente depois da sessão plena. Sucesso sem destino navega para `/user`. Voltar do cadastro/recuperação retorna a esta etapa limpa.
**Content and Data**: Marca/título; e-mail; senha com mostrar/ocultar; “lembrar-me”; ação Entrar; “Esqueci minha senha”; “Criar conta”; separador “ou”; Entrar com passkey; Entrar com Google quando configurado; Turnstile somente quando exigido. Não exibir estado ou métodos de uma identidade antes do primeiro fator.
**Actions and Behavior**: Permitir colagem/gerenciador; senha submit com Enter; passkey inicia sem e-mail e somente após ação explícita; Google usa nonce novo; submissão limpa senha e token Turnstile do estado reutilizável; falha preserva apenas e-mail e escolha “lembrar-me”; fator válido abre INT-WEB-AUTH-002/003 ou conclui sessão; repetir clique durante processamento não duplica tentativa.
**Validation and Feedback**: E-mail/senha ausentes recebem erro de campo; credencial, usuário ou vínculo rejeitado usa uma mensagem neutra. Limitação informa quando tentar novamente sem indicar e-mail/IP. Turnstile indisponível quando obrigatório orienta retorno posterior. Cancelamento/indisponibilidade de passkey ou Google preserva métodos independentes.
**Responsive/Adaptive Behavior**: Card central RFW em desktop/tablet; largura fluida e ações empilhadas em telefone; teclado virtual não encobre ação; botões externos ocupam largura útil; orientação não altera ordem; mouse, toque e teclado possuem paridade.
**Accessibility**: `main`, heading único, labels persistentes, descrição de “lembrar-me”, foco inicial no e-mail sem deep link, ordem e-mail/senha/lembrar/entrar/recuperar/criar/passkey/Google; erros anunciados e foco no primeiro inválido; passkey e Google possuem nomes acessíveis e falha textual; contraste/tokens RFW, zoom/reflow 400%, reduced motion e alvos mínimos do design system.
**Localization**: Chaves i18n por contexto; `pt-BR` inicial; expansão e pluralização; texto não pressupõe mouse/biometria; nenhum e-mail, origem, método existente ou causa interna interpolado em erro neutro.
**Components and Design System**: `RFWAccessComponent`, renderer `SIGN_IN`, `RFWPasskeyComponent`, `RFWGoogleSignInComponent`, `RFWTurnstileComponent`, `UIFactory`, tokens/part IDs públicos. Evoluções seguem gaps 001, 005, 006 e 009; nenhum CSS estrutural local.
**Integration and Contracts**: [Authentication Providers](./contracts/authentication-providers.md) — senha, Google, passkey e lifecycle; [External Services](./contracts/external-services.md). Estado é sempre reconsultado; não há cache de usuário no browser.
**Telemetry**: `authentication_sign_in_viewed/submitted/completed/rejected`; método solicitado, resultado público, Turnstile exigido, duração e correlation ID. Excluir identificador, senha, token, IP, credential ID e causa de existência.
**Wireframe Requirement**: REQUIRED
**Wireframe**: Embedded

**States**:

| State | Expected Presentation | Available Actions | Transition/Exit |
|-------|-----------------------|-------------------|-----------------|
| initial | Formulário limpo; integrações disponíveis aparecem por capability | Preencher ou escolher método | ready/processing |
| loading | N/A — capabilities/configuração crítica são resolvidas antes da rota; falha de startup não publica UI incompleta | N/A | N/A |
| empty | N/A — não representa coleção | N/A | N/A |
| ready | Controles editáveis; Turnstile visível somente pela política | Entrar, recuperar, cadastrar, passkey, Google | processing |
| processing | `aria-busy`, ação causadora bloqueada, senha/prova removida do estado reutilizável | Aguardar/cancelar somente cerimônia externa | success/challenge/error |
| success | Feedback breve sem dados; navegação somente após sessão plena | Continuar automático | destino ou `/user` |
| validation-error | Campo ausente/formato local; mensagem neutra para autenticação rejeitada | Corrigir/repetir/outro método | ready |
| remote-error | Integração indisponível identificada pelo método, sem expor identidade | Repetir ou escolher método independente | ready/processing |
| offline | Overlay Vaadin; tentativa não é presumida | Reconectar | ready ou initial se sessão expirou |
| access-denied | Usuário não ativo recebe a mesma rejeição pública de credencial | Recuperar ou tentar depois | ready |
| partial-stale | Configuração/capability mudou durante a tela; ação afetada é removida após refresh sem apagar e-mail | Recarregar/usar método restante | initial/ready |

### INT-WEB-AUTH-002 — Confirmar segundo fator

**Surface**: SURF-WEB-RINOS
**Surface Type**: WEB
**Change Type**: NEW
**Purpose**: Concluir um fluxo já identificado usando TOTP, OTP de e-mail, passkey verificada ou código de recuperação.
**Actors and Permissions**: Pessoa que comprovou o primeiro fator e possui referência opaca aberta; ainda não há sessão plenamente autenticada.
**Entry and Navigation**: Continuação interna do `RFWAccessComponent`; URL permanece `/login`. Voltar cancela/invalida o fluxo e retorna ao login. Sucesso segue ao gate legal ou destino final.
**Content and Data**: Título “Confirme sua identidade”; explicação; métodos permitidos. Priorizar passkey como recomendada sem abrir prompt automaticamente, depois TOTP, e-mail e “Usar código de recuperação”. Método escolhido mostra campo/protocolo, expiração, destino mascarado quando e-mail, reenviar e trocar método.
**Actions and Behavior**: Selecionar método chama begin quando necessário; e-mail só é enviado depois da escolha; reenvio invalida OTP anterior; passkey exige ação explícita; TOTP/recovery submetem com Enter; troca limpa prova; cancelar invalida fluxo; Google administrativo nunca oferece e-mail do mesmo canal.
**Validation and Feedback**: Prova ausente/forma inválida junto ao campo; prova errada usa mensagem sem revelar detalhes e respeita tentativas; expiração oferece reiniciar login; reenvio limitado mostra espera; falha de um método mantém outros elegíveis.
**Responsive/Adaptive Behavior**: Mesmo shell do login; seletor vira lista vertical no telefone; código segmentado visualmente sem múltiplos campos; teclado numérico é sugestão, não restrição; prompt WebAuthn é do navegador.
**Accessibility**: Foco no heading e depois método/campo; mudança de método anuncia conteúdo; `autocomplete=one-time-code`; códigos aceitam colagem; expiração não depende apenas de contagem animada; foco retorna ao método após cancelamento WebAuthn; recuperação tem aviso claro de uso único.
**Localization**: Datas/tempo no locale e fuso da sessão; pluralização de espera; nomes TOTP, passkey e código de recuperação acompanhados de explicação compreensível.
**Components and Design System**: renderer RFW de challenge evoluído pelos gaps 001, 003 e 009; `RFWPasskeyComponent`, seletores, fields, alerts e buttons RFW.
**Integration and Contracts**: `SecondFactorFacade` em [Authentication Providers](./contracts/authentication-providers.md); SMTP/WebAuthn em [External Services](./contracts/external-services.md). Referência e catálogo não são cacheados além do fluxo aberto.
**Telemetry**: `authentication_mfa_method_selected/submitted/completed/rejected`; método, resultado público, duração e expiração; excluir prova/destino.
**Wireframe Requirement**: REQUIRED
**Wireframe**: Embedded

**States**:

| State | Expected Presentation | Available Actions | Transition/Exit |
|-------|-----------------------|-------------------|-----------------|
| initial | Métodos permitidos, recomendação e nenhum envio automático | Escolher método/voltar | loading/ready |
| loading | Emissão de OTP ou opções WebAuthn sendo preparadas | Cancelar retorno | ready/remote-error |
| empty | N/A — fluxo sem método permitido é erro de invariant e não renderiza seleção vazia | N/A | remote-error |
| ready | Método ativo com campo/ação e validade | Confirmar, reenviar, trocar, voltar | processing |
| processing | Prova limpa, controles bloqueados e busy anunciado | Aguardar | success/error |
| success | Confirmação curta sem mostrar código | Continuar | INT-003 ou sessão |
| validation-error | Campo/prova rejeitada; tentativa preserva somente método | Corrigir/trocar/reiniciar | ready |
| remote-error | Envio/WebAuthn indisponível; demais métodos permanecem | Tentar novamente/trocar | loading/ready |
| offline | Nenhum consumo presumido | Reconectar | ready ou login se fluxo venceu |
| access-denied | Fluxo inválido/usuário indisponível encerra a continuação com mensagem neutra | Voltar ao login | INT-001 |
| partial-stale | Método foi revogado/estado mudou; lista é reconsultada e não usa a prova antiga | Escolher método ainda válido/reiniciar | initial/ready |

### INT-WEB-AUTH-003 — Aceitar documento obrigatório após autenticação

**Surface**: SURF-WEB-RINOS
**Surface Type**: WEB
**Change Type**: NEW
**Purpose**: Registrar versões obrigatórias vigentes antes de liberar sessão para usuário antigo ou recém-autenticado.
**Actors and Permissions**: Pessoa com fatores concluídos e continuação legal opaca; acesso apenas aos documentos públicos e à própria decisão.
**Entry and Navigation**: Continuação no shell `/login`; não há acesso a `/user`. Voltar/cancelar encerra o fluxo e retorna ao login. Aceite válido continua a criação da sessão.
**Content and Data**: Heading, explicação de bloqueio, lista somente de versões pendentes, título/finalidade, link ao conteúdo íntegro, checkbox obrigatório e ação “Aceitar e continuar”. Opcionais atualizados não bloqueiam nem aparecem como aceitos automaticamente.
**Actions and Behavior**: Reconsultar catálogo antes de renderizar e antes do commit; abrir documento em nova aba; exigir todos os obrigatórios; submeter referência opaca; mudança durante a tela substitui a lista e exige decisão da nova versão.
**Validation and Feedback**: Aceite ausente marca checkbox e move foco ao primeiro; documento indisponível/integridade inválida falha fechado; concorrência/versão nova mostra lista atualizada sem registrar decisão parcial.
**Responsive/Adaptive Behavior**: Lista vertical com links quebráveis; ação fixa somente se não encobrir conteúdo; telefone mantém documento/checkbox próximos e sem rolagem horizontal.
**Accessibility**: Foco no heading; documentos em lista semântica; checkbox contém título/version; erro associado; links informam nova aba; atualização de catálogo anunciada; operação completa por teclado/leitor de tela.
**Localization**: Conteúdo/título da versão publicada; datas no locale; recusa/erro não usa linguagem coercitiva nem afirma aceite.
**Components and Design System**: evolução do `RFWAccessComponent`, provider legal atual e renderer Markdown sanitizado; gap 007.
**Integration and Contracts**: legal facade já existente e outcome pós-login em [Authentication Providers](./contracts/authentication-providers.md). Sem cache: catálogo vigente é autoridade.
**Telemetry**: `authentication_legal_gate_viewed/completed/cancelled`; tipos/quantidade e resultado, sem conteúdo ou identidade.
**Wireframe Requirement**: REQUIRED
**Wireframe**: Embedded

**States**:

| State | Expected Presentation | Available Actions | Transition/Exit |
|-------|-----------------------|-------------------|-----------------|
| initial | Lista vigente pendente e explicação | Abrir/selecionar/voltar | ready |
| loading | Catálogo sendo reconsultado | Aguardar | ready/remote-error |
| empty | Nenhum obrigatório pendente: não exibir tela e continuar automaticamente | N/A | sessão |
| ready | Checkboxes e links disponíveis | Aceitar e continuar/voltar | processing |
| processing | Decisões bloqueadas contra repetição | Aguardar | success/error/partial-stale |
| success | Evidência registrada e continuação consumida | Continuar automático | sessão/destino |
| validation-error | Primeiro obrigatório não aceito em foco | Aceitar/abrir/voltar | ready |
| remote-error | Catálogo/integridade indisponível; sem sessão | Repetir/voltar | loading/INT-001 |
| offline | Decisão não presumida | Reconectar | loading/ready |
| access-denied | Fluxo/usuário inválido encerra continuação | Voltar ao login | INT-001 |
| partial-stale | Nova versão entrou em vigor; lista atualizada e decisão antiga da tela descartada | Revisar novamente | ready |

### INT-WEB-AUTH-004 — Recuperar acesso e redefinir senha

**Surface**: SURF-WEB-RINOS
**Surface Type**: WEB
**Change Type**: MODIFIED
**Purpose**: Solicitar prova neutra e substituir senha local com segurança, ampliando o recorte já implementado para invalidação de sessões/fatores aplicável.
**Actors and Permissions**: Pessoa não autenticada; conhecer um e-mail não concede confirmação de existência.
**Entry and Navigation**: Ação no login abre `RECOVERY_REQUEST`. Deep link canônico abre `PASSWORD_RESET`, remove a prova da URL visível e conserva-a apenas no estado efêmero. Voltar retorna ao login.
**Content and Data**: Solicitação: e-mail, Turnstile quando exigido, ação Enviar instruções. Resultado sempre neutro e inclui apenas orientação genérica para voltar ao login e utilizar método já configurado quando não houver senha local; não informar se Google ou passkey existem. Redefinição: nova senha, confirmação, regras visíveis, mostrar/ocultar e ação Redefinir. Não listar métodos existentes.
**Actions and Behavior**: Reutilizar provider já entregue; limpar prova/senhas após submit; nova solicitação invalida anterior; redefinição válida encerra todas as sessões e provas; usuário exclusivamente externo não recebe senha silenciosamente.
**Validation and Feedback**: Sintaxe local do e-mail pode ser indicada; resposta remota permanece neutra. Política de senha detalha cada violação e HIBP indisponível falha fechado. Prova inválida/expirada orienta nova solicitação sem revelar usuário.
**Responsive/Adaptive Behavior**: Mesmo shell RFW e formulário de coluna única; regras quebram linha; teclado virtual não encobre ação.
**Accessibility**: Foco em e-mail/nova senha conforme etapa; autocomplete apropriado; regras associadas; erros anunciados; mostrar senha informa estado; somente teclado.
**Localization**: Mensagem neutra não afirma entrega; requisitos e validade localizados; sem e-mail completo em feedback.
**Components and Design System**: renderers `RECOVERY_REQUEST`/`PASSWORD_RESET`, fields e feedback RFW; nenhuma mudança estrutural além dos outcomes de sessão.
**Integration and Contracts**: provider existente, Pwned Passwords e SMTP em [External Services](./contracts/external-services.md); extensão de invalidação no backend.
**Telemetry**: `authentication_recovery_requested/reset_submitted/reset_completed`; resultado público e duração; excluir e-mail, prova e senha.
**Wireframe Requirement**: N/A
**Wireframe**: N/A — sem alteração estrutural de layout.

**States**:

| State | Expected Presentation | Available Actions | Transition/Exit |
|-------|-----------------------|-------------------|-----------------|
| initial | Formulário da etapa escolhida | Preencher/voltar | ready |
| loading | N/A — provider/catálogo resolvidos antes de renderizar | N/A | N/A |
| empty | N/A — formulário não é coleção | N/A | N/A |
| ready | Campos e ação disponíveis | Enviar/redefinir/voltar | processing |
| processing | Segredos limpos e busy anunciado | Aguardar | success/error |
| success | Solicitação neutra ou redefinição concluída | Voltar para entrar | INT-001 |
| validation-error | Erro de sintaxe/política/prova no campo | Corrigir/recomeçar | ready |
| remote-error | Dependência indisponível sem efeito parcial afirmado | Repetir/voltar | ready |
| offline | Operação não presumida | Reconectar | ready/initial |
| access-denied | N/A — fluxo público; prova rejeitada usa validation-error neutro | N/A | N/A |
| partial-stale | Nova prova invalidou a aberta; orientar nova solicitação | Solicitar novamente | initial |

### INT-WEB-AUTH-005 — Configurações de segurança

**Surface**: SURF-WEB-RINOS
**Surface Type**: WEB
**Change Type**: NEW
**Purpose**: Oferecer uma visão única e privada do nível de proteção, métodos e sessões do usuário atual.
**Actors and Permissions**: Usuário `ACTIVE` com sessão válida; alvo é sempre o principal atual. Papel não altera dados exibidos, mas exigência administrativa pode gerar alerta de MFA.
**Entry and Navigation**: `/user/security`, acessada pelo Painel de Usuário. Voltar retorna a `/user`. Sessão inválida redireciona ao login; após login, destino seguro pode retornar aqui.
**Content and Data**: Heading e resumo; alerta acionável de MFA quando aplicável; seções na ordem Métodos de acesso (senha/Google), Passkeys, Segundo fator, Códigos de recuperação e Sessões. Cada seção mostra estado seguro e ação; não exibe segredos.
**Actions and Behavior**: Carregar seções independentemente para que falha externa não esconda outras; refresh após mudança; ações sensíveis chamam INT-010; nunca confiar no estado visual para último método.
**Validation and Feedback**: Falha de uma seção fica contida com Recarregar; usuário bloqueado encerra sessão; estado alterado por outra aba é reconsultado antes de ação e depois de conflito.
**Responsive/Adaptive Behavior**: Desktop usa conteúdo central amplo com seções empilhadas/accordion RFW; telefone mantém coluna única e ações abaixo do item; nenhum dado essencial só em tooltip; touch/teclado equivalentes.
**Accessibility**: `main`, h1 e h2 por seção; skip/foco inicial; status de proteção textual; refresh e alertas anunciados; ordem DOM igual à visual; foco retorna à ação originadora após diálogo; reflow/zoom e contraste RFW.
**Localization**: Datas de criação/último uso no fuso/locale; nomes de métodos localizados; labels do usuário permanecem como digitados com limite seguro.
**Components and Design System**: `RFWSecuritySettingsComponentFactory`, componente/sections/slots/renderers/tokens públicos, evoluções dos gaps 008 e 010. View Rinos apenas compõe heading/navegação/alerta de domínio.
**Integration and Contracts**: providers autenticados em [Authentication Providers](./contracts/authentication-providers.md). Dados sempre recarregados ao entrar e após mutação; sem cache persistente no browser.
**Telemetry**: `security_settings_viewed/section_load_failed`; seção e resultado, sem labels, sessão, origem ou identificador de método.
**Wireframe Requirement**: REQUIRED
**Wireframe**: Embedded

**States**:

| State | Expected Presentation | Available Actions | Transition/Exit |
|-------|-----------------------|-------------------|-----------------|
| initial | Shell protegido, heading e placeholders por seção | Voltar | loading |
| loading | Skeleton/ocupado por seção, sem dados antigos | Voltar | ready/empty/remote-error |
| empty | Seção sem itens explica ausência e oferece criação quando permitido | Adicionar método | diálogo/painel |
| ready | Resumo e seções atuais | Gerenciar/voltar/logout | interações 006–010 |
| processing | Somente seção/ação afetada bloqueada | Usar seções independentes | ready/error |
| success | Toast/status localizado e dados recarregados | Continuar | ready |
| validation-error | Erro pertence ao diálogo/ação, não apaga outras seções | Corrigir | interação filha |
| remote-error | Cartão da seção falha sem expor dados residuais | Recarregar/usar outras seções | loading/ready |
| offline | Overlay global; dados visíveis ficam inativos | Reconectar/logout local | loading |
| access-denied | Limpar tela/contexto e redirecionar | Entrar novamente | INT-001 |
| partial-stale | Conflito em outra sessão; aviso e refresh obrigatório | Recarregar | loading |

### INT-WEB-AUTH-006 — Gerenciar segundo fator e códigos de recuperação

**Surface**: SURF-WEB-RINOS
**Surface Type**: WEB
**Change Type**: NEW
**Purpose**: Ativar/remover TOTP ou e-mail e gerar/consumir meios de contingência sem expor segredo posteriormente.
**Actors and Permissions**: Usuário ativo reautenticado; administrador não pode remover fator necessário.
**Entry and Navigation**: Ações nas seções Segundo fator/Códigos em INT-005. Enrollment e apresentação única usam diálogos modais; fechar retorna foco à ação.
**Content and Data**: TOTP: nome, QR, segredo copiável alternativo, código de confirmação. E-mail: endereço mascarado e confirmação. Recovery: quantidade disponível e ação Gerar novamente; resultado mostra exatamente 10 códigos com copiar/baixar texto local e confirmação “Guardei os códigos”.
**Actions and Behavior**: INT-010 antes de mutação; TOTP só ativa após código válido; fechar enrollment invalida pendência; regenerar exige confirmação destrutiva e invalida conjunto anterior no commit; diálogo de códigos não fecha por Esc/clique externo antes da confirmação; remover fator revalida último método/obrigação.
**Validation and Feedback**: Código inválido mantém diálogo sem segredo em log; QR possui alternativa; falha após gerar códigos não pode reexibi-los e orienta gerar novo conjunto; conflito recarrega seção.
**Responsive/Adaptive Behavior**: Diálogo ocupa largura segura no desktop e tela útil no telefone; QR não força overflow; códigos quebram por grupo, com copiar todos; teclado numérico sugerido.
**Accessibility**: Trap/retorno de foco; heading/descrição; QR com alternativa textual; live status para cópia; códigos em lista/texto selecionável; confirmação destrutiva explícita; nenhuma ação só por ícone/cor.
**Localization**: Nome do emissor Rinos; instruções neutras a apps autenticadores; tempos e contagem localizados; códigos não são traduzidos.
**Components and Design System**: `RFWSecuritySettingsComponent`, evolução de enrollment gap 002, dialogs/buttons/QR acessível do RFW, `RFWTotpService` e `RFWRecoveryCodesVO`.
**Integration and Contracts**: factor management em [Authentication Providers](./contracts/authentication-providers.md); SMTP em [External Services](./contracts/external-services.md). Refresh obrigatório após toda conclusão.
**Telemetry**: `security_factor_enrollment_started/completed/cancelled`, `recovery_codes_regenerated`; tipo e resultado, nunca segredo/código.
**Wireframe Requirement**: REQUIRED
**Wireframe**: Embedded

**States**:

| State | Expected Presentation | Available Actions | Transition/Exit |
|-------|-----------------------|-------------------|-----------------|
| initial | Diálogo explica operação antes de revelar/emitir segredo | Continuar/cancelar | loading/INT-010 |
| loading | Enrollment/códigos sendo preparados | Cancelar quando ainda seguro | ready/remote-error |
| empty | Sem fator/código: seção oferece criar; diálogo não usa estado vazio | Adicionar/gerar | initial |
| ready | Dados de apresentação única ou confirmação disponíveis | Confirmar/copiar/cancelar | processing |
| processing | Prova limpa e ação bloqueada | Aguardar | success/error |
| success | Fator confirmado/removido ou códigos aguardando confirmação de guarda | Confirmar retorno | INT-005 |
| validation-error | Código/nome inválido com foco no campo | Corrigir | ready |
| remote-error | Operação não concluída; segredo efêmero removido quando segurança exigir | Recomeçar/fechar | initial/INT-005 |
| offline | Nenhuma confirmação presumida; segredo removido ao perder sessão | Reconectar e recomeçar | INT-005 |
| access-denied | Reautenticação/invariant falhou | Fechar/reautenticar | INT-005/010 |
| partial-stale | Conjunto/fator mudou em outra sessão | Fechar e recarregar | INT-005 |

### INT-WEB-AUTH-007 — Gerenciar passkeys

**Surface**: SURF-WEB-RINOS
**Surface Type**: WEB
**Change Type**: NEW
**Purpose**: Cadastrar, reconhecer, nomear e revogar passkeys individuais.
**Actors and Permissions**: Usuário ativo; cadastro/revogação exigem reautenticação; último método utilizável não pode ser revogado.
**Entry and Navigation**: Seção Passkeys em INT-005. Adicionar abre nome e cerimônia WebAuthn; renomear/revogar atua no item e retorna foco a ele/seção.
**Content and Data**: Lista com label, criação, último uso, tipo sincronizável/chave quando seguro e estado; ação Adicionar passkey; por item Renomear e Revogar. Não exibir credential ID, chave pública, contador ou biometria.
**Actions and Behavior**: Reautenticar, informar nome e só então abrir `navigator.credentials.create`; cancelamento não cria registro; renomear valida 1–100 caracteres; revogar exige confirmação e revalida invariant.
**Validation and Feedback**: Navegador sem suporte informa método alternativo; duplicate credential/rejeição usa erro acionável; cancelamento local não é erro de segurança; anomalia de uso não acusa clonagem ao usuário sem decisão confirmada.
**Responsive/Adaptive Behavior**: Itens empilhados no telefone; ações visíveis/menu acessível sem hover; prompt do sistema externo à página.
**Accessibility**: Nome antes do prompt; foco retorna após cancelamento; status WebAuthn anunciado; confirmação de revogação identifica label; operação por teclado até o prompt do sistema.
**Localization**: Datas localizadas; label do usuário não traduzido; termos “passkey” e “chave de segurança” explicados.
**Components and Design System**: section renderer e `RFWPasskeyComponent` evoluídos pelos gaps 001, 008 e 009.
**Integration and Contracts**: passkey management/provider e repositories em [Authentication Providers](./contracts/authentication-providers.md); protocolo em [External Services](./contracts/external-services.md).
**Telemetry**: `security_passkey_registration_started/completed/cancelled`, `renamed/revoked`; resultado e tipo seguro, sem label/credential.
**Wireframe Requirement**: REQUIRED
**Wireframe**: Embedded

**States**:

| State | Expected Presentation | Available Actions | Transition/Exit |
|-------|-----------------------|-------------------|-----------------|
| initial | Seção ainda não carregada | N/A | loading |
| loading | Placeholder da lista/cerimônia busy | Cancelar cerimônia quando navegador permitir | ready/error |
| empty | Explica benefício e oferece adicionar | Adicionar | INT-010/processing |
| ready | Lista reconhecível e ações | Adicionar/renomear/revogar | processing/dialog |
| processing | Item/ação afetada bloqueada | Usar itens independentes | success/error |
| success | Mensagem e lista reconsultada | Continuar | ready |
| validation-error | Label/protocolo rejeitado com erro associado | Corrigir/repetir | ready |
| remote-error | WebAuthn/servidor indisponível sem alterar lista | Repetir/outro método | ready |
| offline | Cerimônia não é presumida concluída | Reconectar | loading |
| access-denied | Reautenticação ou último método impede ação | Fechar/configurar outro método | ready/INT-010 |
| partial-stale | Passkey já alterada/revogada | Recarregar | loading |

### INT-WEB-AUTH-008 — Gerenciar vínculo Google e senha

**Surface**: SURF-WEB-RINOS
**Surface Type**: WEB
**Change Type**: NEW
**Purpose**: Vincular/desvincular Google explicitamente e criar/alterar senha local sem associação automática por e-mail.
**Actors and Permissions**: Usuário ativo reautenticado; operação nunca afeta outro usuário com e-mail coincidente.
**Entry and Navigation**: Seção Métodos de acesso em INT-005. Google abre componente oficial e confirmação; senha abre diálogo de criar/alterar.
**Content and Data**: Estado “Senha configurada/ausente/precisa ser redefinida”, ações Criar/Alterar; Google vinculado com label seguro e ação Desvincular ou “Vincular Google”. Formulário de senha mostra atual quando aplicável, nova, confirmação e regras.
**Actions and Behavior**: INT-010; Google validado só vincula após confirmação explícita; conflito não mostra proprietário; desvincular/revogar senha revalida último método. Nova senha usa HIBP/Argon2 e invalida sessões conforme política; campo atual pode ser dispensado se reautenticação equivalente já comprovou identidade.
**Validation and Feedback**: Regras de senha por campo; Google cancelado não muda estado; indisponibilidade de Google não bloqueia senha; conflito concorrente recarrega.
**Responsive/Adaptive Behavior**: Cards/itens empilhados; formulários coluna única; ações não dependem de popup sem fallback informado.
**Accessibility**: Labels/requisitos associados; mostrar senha acessível; confirmação identifica método; Google possui nome/estado textual; foco retorna à seção.
**Localization**: Mensagens não afirmam que e-mails foram associados; regras e estados localizados; label externo sanitizado.
**Components and Design System**: nova seção/provider de senha do gap 010, Google component/provider e outcomes de gestão do gap 008.
**Integration and Contracts**: external identity/password management em [Authentication Providers](./contracts/authentication-providers.md); Google/HIBP em [External Services](./contracts/external-services.md).
**Telemetry**: `security_password_created/changed`, `google_link_started/completed/unlinked`; resultado, sem e-mail/token/senha.
**Wireframe Requirement**: REQUIRED
**Wireframe**: Embedded

**States**:

| State | Expected Presentation | Available Actions | Transition/Exit |
|-------|-----------------------|-------------------|-----------------|
| initial | Seção sem snapshot reutilizado | N/A | loading |
| loading | Placeholders independentes | Voltar | ready/error |
| empty | Senha/Google ausente é estado funcional com ação | Criar/vincular | INT-010 |
| ready | Métodos e ações atuais | Criar/alterar/vincular/desvincular | dialog/processing |
| processing | Somente método afetado busy | Aguardar | success/error |
| success | Mensagem e refresh | Continuar | ready |
| validation-error | Senha/campo/confirmação inválida | Corrigir | dialog ready |
| remote-error | HIBP/Google indisponível; nenhum vínculo/hash parcial | Repetir/fechar | ready |
| offline | Prova externa/senha removida do estado | Reconectar e recomeçar | ready |
| access-denied | Último método/reautenticação impede alteração | Adicionar outro método/fechar | ready |
| partial-stale | Estado mudou em outra sessão | Recarregar | loading |

### INT-WEB-AUTH-009 — Reconhecer e encerrar sessões

**Surface**: SURF-WEB-RINOS
**Surface Type**: WEB
**Change Type**: NEW
**Purpose**: Permitir reconhecer a sessão atual e revogar uma, as demais ou todas as sessões próprias.
**Actors and Permissions**: Usuário ativo; referências do cliente são sempre limitadas ao principal atual.
**Entry and Navigation**: Seção Sessões em INT-005. Revogar remota usa confirmação simples; encerrar atual/todas conclui em logout e navega ao login.
**Content and Data**: Cada sessão mostra dispositivo seguro, origem aproximada, criação, última atividade e marcador “Esta sessão”. Ações Encerrar para remotas, “Encerrar outras sessões” e “Encerrar todas”. Não exibir cookie, IP bruto ou ID técnico.
**Actions and Behavior**: Reautenticar para revogar todas quando política exigir; revogação remota imediata e idempotente; revogar atual limpa contexto/cookie; item já encerrado desaparece após refresh; atualização de atividade não reordena agressivamente durante leitura.
**Validation and Feedback**: Confirmação destrutiva informa alcance; conflito “já encerrada” é sucesso idempotente; falha preserva item e permite repetir; usuário bloqueado encerra toda a tela.
**Responsive/Adaptive Behavior**: Lista vertical; metadados quebram linha; ações abaixo no telefone; item atual sempre identificado por texto.
**Accessibility**: Lista/heading, ação nomeia dispositivo, confirmação e retorno de foco, datas com texto compreensível, sem dependência de ícone/cor.
**Localization**: Datas relativas podem ter timestamp absoluto acessível; dispositivo desconhecido/origem indisponível possuem fallback localizado.
**Components and Design System**: session section do `RFWSecuritySettingsComponent`, outcomes do gap 008.
**Integration and Contracts**: `RFWSessionManagementProvider`/facade em [Authentication Providers](./contracts/authentication-providers.md); lista sempre fresh no ingresso/refresh.
**Telemetry**: `security_session_revoked/revoke_all`; escopo e resultado; sem referência, origem ou user agent.
**Wireframe Requirement**: REQUIRED
**Wireframe**: Embedded

**States**:

| State | Expected Presentation | Available Actions | Transition/Exit |
|-------|-----------------------|-------------------|-----------------|
| initial | Seção aguarda consulta | N/A | loading |
| loading | Placeholders sem sessão anterior | Voltar | ready/error |
| empty | N/A — a sessão atual válida deve existir; ausência é invariant e encerra acesso | N/A | access-denied |
| ready | Sessões reconhecíveis e atual marcada | Revogar uma/outras/todas | processing/INT-010 |
| processing | Alvo e ação abrangente bloqueados | Aguardar | success/error |
| success | Item removido/mensagem; se atual, logout | Continuar ou entrar | ready/INT-001 |
| validation-error | N/A — referências são selecionadas da lista; valor obsoleto é partial-stale | N/A | N/A |
| remote-error | Lista permanece sem afirmar revogação | Repetir/recarregar | ready/loading |
| offline | Nenhuma revogação presumida | Reconectar | loading |
| access-denied | Sessão atual inválida; dados removidos da UI | Entrar novamente | INT-001 |
| partial-stale | Sessão já encerrou/expirou | Recarregar e tratar como sucesso idempotente | loading/ready |

### INT-WEB-AUTH-010 — Reautenticar para operação sensível

**Surface**: SURF-WEB-RINOS
**Surface Type**: WEB
**Change Type**: NEW
**Purpose**: Confirmar identidade/garantia recente sem criar outra sessão ou conceder acesso.
**Actors and Permissions**: Usuário ativo com sessão válida; métodos oferecidos dependem da operação e da garantia atual.
**Entry and Navigation**: Intercepta ação sensível. Se a garantia já estiver válida há no máximo 15 minutos, não abre diálogo. Caso contrário, modal identifica a ação em linguagem humana; cancelar retorna sem efeito à origem.
**Content and Data**: Explicação, métodos permitidos, senha/TOTP/passkey conforme usuário/operação, ação Confirmar e Cancelar. Google administrativo exclui código do mesmo e-mail. Não exibir detalhes técnicos de `operationId`.
**Actions and Behavior**: Escolher método, comprovar e retomar exatamente uma ação original; referência de operação é de uso único; sucesso atualiza a sessão atual; falha/cancelamento não executa mutação; timeout fecha continuação e exige reinício.
**Validation and Feedback**: Prova ausente/errada associada; limite mostra espera; método revogado força refresh; operação original que se tornou inválida mostra conflito sem reaplicar automaticamente.
**Responsive/Adaptive Behavior**: Modal responsivo/tela cheia no telefone; ação original resumida; prompt WebAuthn externo e foco de retorno.
**Accessibility**: Modal nomeado, trap e retorno de foco; descrição da razão; métodos por radiogroup/lista; prova com autocomplete; erro live; Esc equivale a Cancelar quando não houver segredo de apresentação única.
**Localization**: Nome humano da operação por chave i18n; nunca mostrar `operationId`; tempos localizados.
**Components and Design System**: evolução do provider/dialog RFW gap 004, renderer de challenge e passkey RFW.
**Integration and Contracts**: `ReauthenticationFacade` em [Authentication Providers](./contracts/authentication-providers.md); estado da operação original é revalidado depois da prova.
**Telemetry**: `reauthentication_prompted/completed/rejected/cancelled`; operação em enum de baixa cardinalidade, método e resultado; sem prova.
**Wireframe Requirement**: REQUIRED
**Wireframe**: Embedded

**States**:

| State | Expected Presentation | Available Actions | Transition/Exit |
|-------|-----------------------|-------------------|-----------------|
| initial | Motivo e métodos; nenhum método externo iniciado automaticamente | Escolher/confirmar/cancelar | ready/loading |
| loading | Preparando OTP/WebAuthn quando escolhido | Cancelar | ready/error |
| empty | Nenhum método compatível: orientar configuração/recuperação sem executar ação | Fechar/ir à segurança | INT-005 |
| ready | Prova/método disponível | Confirmar/trocar/cancelar | processing |
| processing | Prova limpa e ação bloqueada | Aguardar | success/error |
| success | Modal fecha e ação original é revalidada uma vez | Continuar automático | tela de origem |
| validation-error | Prova inválida com foco | Corrigir/trocar | ready |
| remote-error | Método indisponível; ação original não executada | Repetir/trocar/cancelar | ready |
| offline | Ação original não executada | Reconectar e reiniciar | tela de origem |
| access-denied | Sessão/usuário deixou de ser válido | Entrar novamente | INT-001 |
| partial-stale | Operação original ou método mudou após prova | Fechar/recarregar estado | tela de origem |

## Cross-Surface Rules

### Navigation and Parity

Há uma única superfície. `/login` concentra acesso anônimo e continuações; `/user/security` concentra segurança
autenticada. Nenhum deep link contém e-mail, ID de usuário/sessão ou segredo além das provas opacas já previstas, que
são removidas da URL visível após entrega à UI. Botão Voltar nunca repete mutação nem restaura segredo.

### Shared Content and Terminology

- “Entrar” para autenticação; “Confirmar sua identidade” para MFA/reauth; “Método de acesso” para senha, Google ou
  passkey; “Segundo fator” para TOTP/e-mail; “Sessão” para continuidade reconhecível.
- “Lembrar-me” explica os 30 dias/7 dias sem sugerir segurança permanente.
- Mensagem neutra: “Não foi possível entrar com os dados informados.”
- Sucesso de e-mail afirma envio/aceitação pelo serviço, não entrega na caixa postal.
- Passkey nunca é chamada apenas de biometria; PIN/chave de segurança continuam válidos.

### Shared Accessibility and Input

Todas as jornadas usam teclado, toque e pointer; ordem DOM igual à visual; busy, erro, atualização e timeout são
anunciados; foco inicial, primeiro erro e retorno de modal são determinísticos. Campos aceitam colagem e gerenciadores.
Nenhum CAPTCHA visual isolado, gesto, hover, cor ou contagem regressiva é a única fonte de informação. Seguir WCAG 2.2
AA, tokens de contraste RFW, zoom/reflow e `prefers-reduced-motion`.

### Sensitive State

Senha, confirmação, OTP, recovery code, segredo/URI TOTP, ID token, assertion, token Turnstile e cookie não entram em
estado serializável do componente, URL (salvo prova opaca de deep link já autorizada), telemetria ou log. Campos são
limpos antes de callback assíncrono e não reaparecem após refresh/reconexão.

## Traceability

| Interaction ID | User Stories | Functional Requirements | Success Criteria | Contracts |
|----------------|--------------|-------------------------|------------------|-----------|
| INT-WEB-AUTH-001 | US1, US2, US3 | `FR-AUTH-001..014`, `PWD-001..010`, `ABUSE-001..010`, `PK-007..008`, `GGL-001..005,009..012`, `SES-002,006,009..011` | `SC-AUTH-001..009,015..016` | [providers](./contracts/authentication-providers.md), [external](./contracts/external-services.md) |
| INT-WEB-AUTH-002 | US1, US2, US4 | `FR-AUTH-007`, `PK-006,009`, `MFA-004..015`, `INFRA-LOCK,IDEMP` | `SC-AUTH-005,008..010,014..015` | [providers](./contracts/authentication-providers.md), [external](./contracts/external-services.md) |
| INT-WEB-AUTH-003 | US1–US3 | `FR-AUTH-013..014` | `SC-AUTH-016` | [providers](./contracts/authentication-providers.md) |
| INT-WEB-AUTH-004 | US5 | `FR-AUTH-REC-001..012`, `PWD-001..010`, `SES-006` | `SC-AUTH-003,005,011,014..015` | [providers](./contracts/authentication-providers.md), [external](./contracts/external-services.md) |
| INT-WEB-AUTH-005 | US4, US6 | `FR-AUTH-009..012`, `MFA-001..003,016..018`, `SES-003,005,007..008` | `SC-AUTH-010..013,015` | [providers](./contracts/authentication-providers.md) |
| INT-WEB-AUTH-006 | US4–US6 | `FR-AUTH-MFA-001..018`, `REC-009..011` | `SC-AUTH-005,008..010,013..015` | [providers](./contracts/authentication-providers.md), [external](./contracts/external-services.md) |
| INT-WEB-AUTH-007 | US2, US6 | `FR-AUTH-PK-001..012`, `AUTH-009..012` | `SC-AUTH-002,009,012..015` | [providers](./contracts/authentication-providers.md), [external](./contracts/external-services.md) |
| INT-WEB-AUTH-008 | US3, US6 | `FR-AUTH-GGL-001..012`, `PWD-003..010`, `AUTH-009..012` | `SC-AUTH-006..008,012..015` | [providers](./contracts/authentication-providers.md), [external](./contracts/external-services.md) |
| INT-WEB-AUTH-009 | US6 | `FR-AUTH-SES-001..012` | `SC-AUTH-004,011..012,015` | [providers](./contracts/authentication-providers.md) |
| INT-WEB-AUTH-010 | US4, US6 | `FR-AUTH-012`, `MFA-002,010..012,016..017`, `SES-008` | `SC-AUTH-008..009,013..015` | [providers](./contracts/authentication-providers.md) |

## Wireframes

### WF-A — Login e recuperação

```text
┌──────────────────────── Rinos ────────────────────────┐
│ Entrar                                                │
│ E-mail                                                │
│ (_______________________________________________)     │
│ Senha                                      <mostrar>  │
│ (_______________________________________________)     │
│ ( ) Lembrar-me neste navegador                       │
│ < Entrar >                    Esqueci minha senha     │
│ ------------------------ ou ------------------------- │
│ < Entrar com passkey >                               │
│ < Entrar com Google  >                               │
│ Ainda não tem acesso? Criar conta                    │
└───────────────────────────────────────────────────────┘
Recuperação substitui o corpo do mesmo card e Voltar retorna a Entrar.
```

### WF-B — Segundo fator

```text
┌────────────────── Confirme sua identidade ────────────┐
│ Escolha um método                                     │
│ <Passkey recomendada> <Aplicativo> <E-mail>           │
│                                                       │
│ Código do aplicativo                                  │
│ (________________)                                    │
│ < Confirmar >                                         │
│ Usar código de recuperação              Voltar        │
└───────────────────────────────────────────────────────┘
No telefone, os métodos e ações ficam em uma única coluna.
```

### WF-C — Gate legal

```text
┌──────────────── Atualização dos documentos ───────────┐
│ Revise antes de continuar                             │
│ ( ) Termos de Uso — versão vigente        <Abrir ↗>   │
│ ( ) Política de Privacidade — versão ...  <Abrir ↗>   │
│                                                       │
│ < Aceitar e continuar >                    Voltar     │
└───────────────────────────────────────────────────────┘
```

### WF-D — Configurações de segurança

```text
┌─ Painel / Segurança ───────────────────────────────────┐
│ Segurança da sua identidade                           │
│ (alerta de MFA quando aplicável)                      │
│                                                       │
│ Métodos de acesso      Senha configurada   <Alterar>  │
│                         Google vinculado  <Desvinc.>   │
│ Passkeys               Notebook             <Ações>   │
│ Segundo fator          Aplicativo ativo      <Ações>   │
│ Códigos de recuperação 8 disponíveis        <Gerar>   │
│ Sessões                Este navegador        (Atual)   │
│                         Firefox • ontem      <Encerrar> │
│                         <Encerrar outras sessões>      │
└───────────────────────────────────────────────────────┘
```

### WF-E — Gestão de métodos

```text
┌──────────────── Adicionar aplicativo ─────────────────┐
│ 1. Leia o QR       ( QR )                             │
│    Alternativa: (segredo copiável uma única vez)      │
│ 2. Informe o código (____________)                    │
│ < Cancelar >                           < Confirmar >   │
└───────────────────────────────────────────────────────┘

Itens de passkey/Google/senha usam a mesma hierarquia:
(nome e estado) (metadados seguros) (ação principal) (ação destrutiva).
```

### WF-F — Reautenticação

```text
┌──────────────── Confirme sua identidade ──────────────┐
│ Para continuar com “Encerrar todas as sessões”        │
│ ( ) Passkey   ( ) Senha   ( ) Aplicativo              │
│ Prova (________________________________________)       │
│ < Cancelar >                           < Confirmar >   │
└───────────────────────────────────────────────────────┘
```

| Interaction ID | Requirement | Artifact | Notes |
|----------------|-------------|----------|-------|
| `INT-WEB-AUTH-001` | REQUIRED | WF-A embutido | Mudança estrutural do login |
| `INT-WEB-AUTH-002` | REQUIRED | WF-B embutido | Seleção adaptativa de fatores |
| `INT-WEB-AUTH-003` | REQUIRED | WF-C embutido | Nova continuação bloqueante |
| `INT-WEB-AUTH-004` | N/A | WF-A apenas para navegação | Renderer existente sem alteração estrutural |
| `INT-WEB-AUTH-005` | REQUIRED | WF-D embutido | Nova tela/hierarquia |
| `INT-WEB-AUTH-006` | REQUIRED | WF-E embutido | Apresentação única de segredo/códigos |
| `INT-WEB-AUTH-007` | REQUIRED | WF-E embutido | Hierarquia comum de métodos |
| `INT-WEB-AUTH-008` | REQUIRED | WF-E embutido | Hierarquia comum de métodos |
| `INT-WEB-AUTH-009` | REQUIRED | WF-D embutido | Lista e ações de sessão |
| `INT-WEB-AUTH-010` | REQUIRED | WF-F embutido | Novo diálogo transversal |

## Validation Summary

- Coverage matrix reviewed: yes
- All inventory items detailed: yes
- Canonical states resolved: yes
- Required wireframes present: yes
- Accessibility requirements resolved: yes
- Contract mappings verified: yes
- Placeholders or open decisions remaining: 0
