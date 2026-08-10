# Contracts: Providers de Autenticação e Segurança

Contratos Java internos entre os componentes RFW e as facades do Rinos. Não constituem API REST pública. Adapters em
`br.com.rinos.app.ui.config` convertem os tipos públicos do RFW para DTOs/VOs da camada `api`; não acessam entities,
repositories ou services diretamente.

## General Rules

- Toda referência de fluxo, sessão ou método é opaca e não equivale ao ID do banco.
- Providers não publicam `SecurityContext`; devolvem resultado tipado ao RFW.
- Senha, OTP, token, segredo, assertion WebAuthn e ID token existem somente durante a chamada que os consome.
- Exceção técnica é convertida em erro público estável e evento sanitizado; stack trace não chega à UI.
- Capability só é anunciada quando o provider real e todas as dependências obrigatórias estiverem disponíveis.
- Um resultado autenticado só pode existir depois de usuário, fatores e documentos legais terem sido revalidados.
- Toda transição crítica recompõe os métodos utilizáveis no banco, sem cache da fotografia do fluxo. Evidência
  cujo método foi comprometido, desativado, revogado ou esgotado rejeita a transição; alternativa ainda não usada
  que deixou de existir é removida do desafio oferecido.

## Authentication Orchestration Core

**Rinos facade**: `AuthenticationOrchestrationFacade`
**RFW outcome adapter**: `RFWAuthenticationOutcomeAdapter`

O núcleo recebe somente fatores que o serviço especializado já comprovou e conserva no banco a fotografia dos
métodos verificados. `start(...)` abre o fluxo depois do primeiro fator, `advance(...)` acrescenta uma evidência
permitida, `complete(...)` revalida e devolve uma conclusão `READY` ainda aberta, e `cancel(...)` invalida a
continuação idempotentemente. Nenhum desses métodos publica `SecurityContext` nem consome o fluxo.

A garantia é calculada sobre os métodos comprovados. Passkey com user verification satisfaz garantia resistente a
phishing; dois canais independentes satisfazem MFA; Google e código enviado ao mesmo e-mail não contam como canais
independentes. O principal mínimo somente é devolvido por uma conclusão `READY`, ainda não consumida. O consumo e a
criação compensável da sessão pertencem ao lifecycle descrito em
[Authentication Session Lifecycle](#authentication-session-lifecycle),
e o registro atômico dos novos aceites pertence ao provider descrito em
[Legal Consent after Authentication](#legal-consent-after-authentication).

O adapter de outcome é reutilizado pelos providers concretos de primeiro fator e não representa uma capability
autônoma. Ele realiza um mapeamento fechado: `READY` cria a autenticação provisória contendo o principal mínimo e a
continuação efêmera exigida pelo lifecycle; `CHALLENGE_REQUIRED` e `LEGAL_CONSENT_REQUIRED` produzem os desafios
tipados do RFW sem autenticação parcial. `REJECTED`, `EXPIRED`, `CONFLICT` e `UNAVAILABLE` usam, respectivamente, as
chaves públicas estáveis `authentication.credentials.invalid`, `authentication.flow.expired`,
`authentication.flow.conflict` e `authentication.temporarily-unavailable`. Nenhuma dessas mensagens inclui
identidade, método existente, causa interna ou referência opaca.

## Password Authentication

**RFW contract**: `RFWPasswordAuthenticationProvider`<br>
**Rinos facade**: `PasswordAuthenticationFacade.authenticate(PasswordAuthenticationRequestDTO)`

`PasswordCredentialAuthenticationService` normaliza o e-mail e bloqueia usuário → credencial na mesma transação
que abre o fluxo. A comparação Argon2id ocorre exatamente uma vez por tentativa: quando identidade ou credencial não
existem, o serviço compara a entrada com um hash sentinela gerado em memória pelo mesmo `PasswordEncoder` e pelos
mesmos parâmetros vigentes. O array recebido é apagado em todos os resultados. O valor sentinela não representa uma
conta e nunca é persistido.

Para reduzir diferenças grosseiras de tempo e de acesso ao banco, identidade existente, ausente ou identificador
malformado executam o mesmo formato mínimo: uma consulta indexada de usuário, uma consulta indexada de credencial e
uma comparação Argon2id. Os casos ausentes usam valores sentinela impossíveis (`normalizedEmail` fora da gramática e
`userId=0`) sem criar linhas no banco. Isso não promete tempo constante em rede, JVM ou MySQL; o contrato é evitar
atalhos controlados pela existência da identidade e manter idênticos conteúdo, status e navegação públicos.

Depois de uma correspondência válida, `PasswordEncoder.upgradeEncoding` decide se o hash precisa ser recalculado
com os parâmetros atuais. O upgrade ocorre ainda sob o lock da credencial, antes de apagar a senha, e não modifica
`passwordChangedAt`, pois o segredo escolhido pelo usuário não mudou. Hash vigente não produz escrita adicional.
Uma credencial com `compromisedAt` preenchido continua pagando a comparação do hash real, mas produz a mesma rejeição
pública de qualquer credencial inválida e não sofre upgrade. Somente uma substituição validada da senha limpa a marca
de comprometimento e torna o método local utilizável novamente.

Quando TOTP, fator de e-mail ou conjunto de recovery codes estiver ativo, a fachada exige
`MULTI_FACTOR`; métodos adicionais utilizáveis, inclusive passkey, são entregues como alternativas ao orquestrador.
Google e a própria senha nunca são oferecidos como segundo fator desse fluxo. A presença isolada de uma passkey como
método alternativo não ativa implicitamente o 2FA voluntário.

### Input

| Field | Required | Validation |
|-------|----------|------------|
| `identifier` | yes | e-mail normalizado; limite de comprimento antes de custo criptográfico |
| `password` | yes | efêmera; aceita Unicode/espaços conforme credencial registrada |
| `rememberMe` | yes | booleano explícito |
| `turnstileToken` | conditional | consumido e validado no servidor pelo RFW antes de chamar o provider hospedeiro |
| `origin` | yes | fornecida pelo adapter de origem confiável, nunca por campo do cliente |
| `correlationId` | yes | UUID técnico criado pelo adapter; não contém nem permite derivar identidade ou origem |

### Outcome mapping

| Domain outcome | RFW/UI outcome |
|----------------|----------------|
| Primeiro fator e todos os gates concluídos | `AUTHENTICATED` com principal mínimo; o RFW inicia o lifecycle da sessão |
| Segundo fator necessário | `CHALLENGE_REQUIRED` com referência, validade e métodos permitidos |
| Novo aceite obrigatório | continuação legal tipada, sem sessão plena |
| Credencial/estado/usuário inválido | `REJECTED` com mensagem neutra |
| Limite atingido | `RATE_LIMITED` sem revelar a dimensão que disparou |
| Turnstile necessário/inválido | desafio renovável conforme protocolo RFW |
| Dependência crítica indisponível | erro público temporário; nenhuma sessão |

Falhas de senha atualizam, na mesma transação, duas janelas deslizantes independentes: e-mail normalizado informado
e IP canônico resolvido pela política de proxy. Ambos são persistidos somente como HMAC-SHA-256 versionado e com
separação de domínio; a ordem de lock segue a ordenação binária da chave do índice, e não o papel semântico, para
evitar ciclos de gap lock na criação concorrente. A resposta combina a política mais restritiva:
maior contador, maior espera progressiva e exigência de Turnstile em qualquer dimensão. Cada nova falha estende a
janela, portanto a exigência só termina depois do intervalo configurado sem outra falha.

O `PasswordAuthenticationResultVO` transporta a decisão sem expor contador, digest, e-mail ou IP: entrega apenas o
outcome do orquestrador, `turnstileRequired` e `retryAfter`. Desde o RFW `911aa5d`, a política de verificação humana
recebe também o identificador efêmero no submit. O Rinos consulta as janelas protegidas na ordem identificador → origem
e exige Turnstile se qualquer dimensão determinar. Antes de chamar `RFWPasswordAuthenticationProvider`, o próprio RFW
valida token, hostname, action, validade e uso único no servidor; ausência, rejeição ou indisponibilidade falha fechada
sem executar a fachada de senha. O adapter cria um `correlationId` por tentativa e nunca registra identificador, senha,
token ou origem.

## Second Factor

**RFW contracts**: `RFWSecondFactorProvider`, `RFWSecondFactorEmissionRequestDTO`,
`RFWSecondFactorEmissionOutcomeVO` e `RFWSecondFactorEmissionVO`<br>
**Rinos facades**: `EmailOtpFacade` para emissão por e-mail e `SecondFactorFacade` para seleção, consumo e avanço
contextual do fluxo

### Begin/resend

| Field | Required | Validation |
|-------|----------|------------|
| `challengeReference` | yes | hash corresponde a fluxo `OPEN` e não expirado |
| `method` | yes | pertence aos métodos permitidos do fluxo |
| `origin` | yes | origem validada |

TOTP e recovery code não exigem emissão. E-mail emite um OTP novo após commit, invalida o anterior e devolve destino
mascarado, validade e primeiro instante de reenvio. A UI só oferece e-mail quando
`RFWSecondFactorProvider.getEmissionMethods()` o declara e só chama `begin(...)` depois da seleção explícita. Um
`resend(...)` bem-sucedido substitui atomicamente a prova anterior; limitação usa erro público com `retryAfter`, e
indisponibilidade não afirma entrega. Passkey somente ingressa no catálogo quando o verificador WebAuthn da fase 4.2
estiver registrado; uma credencial persistida sem provider executável não é anunciada pela UI.

O contrato concreto de e-mail conserva a referência do próprio fluxo como `challengeReference`; o reenvio troca a
prova protegida sob essa mesma continuação, de modo que o código anterior deixa de corresponder imediatamente. O
backend limita cada usuário a três emissões em uma janela móvel de 15 minutos e exige 1 minuto entre emissões por
fluxo, por padrão. Esses valores são propriedades fixas de `rinos.authentication.mfa.*`. O código possui a validade
do desafio, limitada também pela expiração anterior do fluxo, e no máximo cinco tentativas por padrão.

`EmailOtpFacade.begin/resend` só conclui com `EMITTED` depois que o SMTP aceitou a mensagem no callback pós-commit.
Falha de template, transporte ou rollback nunca publica destino/validade como se houvesse envio; uma compensação em
nova transação invalida somente o digest daquela emissão. A comparação exata impede que uma falha atrasada cancele
um reenvio concorrente posterior.

### Verify

| Field | Required | Validation |
|-------|----------|------------|
| `challengeReference` | yes | uso único, finalidade correta e ainda válido |
| `method` | yes | método permitido e vinculado ao usuário do fluxo |
| `proof` | conditional | código efêmero para TOTP/e-mail/recovery; ausente no callback WebAuthn |

Antes do consumo, o backend bloqueia usuário e fluxo, recompõe os métodos atualmente ativos e intersecta essa lista
com a fotografia originalmente permitida. TOTP, e-mail e recovery code usam a mesma fronteira; o último recovery
code (`EXHAUSTED`) ainda vale para a tentativa que o consumiu. Após primeiro fator Google, `EMAIL_CODE` é removido do
catálogo e também rejeitado pela autoridade do OTP, mesmo diante de continuação antiga ou inconsistente.

Consumo da prova e avanço do fluxo compartilham uma transação. Se o fator for válido, mas o orquestrador não puder
produzir challenge posterior, gate legal ou `READY`, o consumo é revertido. Falhas de TOTP/recovery incrementam o
limite compartilhado do fluxo; o OTP por e-mail já incrementa esse contador em sua comparação e não é contado duas
vezes. Ao atingir o máximo configurado, fluxo e provas abertas são invalidados. O resultado final pode autenticar,
exigir aceite legal ou rejeitar de forma neutra; nenhuma falha cria sessão parcial.

## Legal Consent after Authentication

**RFW contracts**: `RFWAuthenticationConsentProvider`, `RFWAuthenticationConsentChallengeVO`,
`RFWAuthenticationConsentRequestDTO` e `RFWAuthenticationOutcomeVO`<br>
**Rinos facade proposta**: `AuthenticationConsentFacade`

O gate só começa depois que usuário e todos os fatores exigidos foram revalidados. O outcome
`AUTHENTICATION_CONSENT_REQUIRED` não contém `Authentication`; leva apenas referência opaca, versões obrigatórias
pendentes, validade e a escolha de login persistente. O registro global de `AuthenticationFlow` vinculado à
referência conserva no backend `idUser`, métodos comprovados e política de sessão. Nenhuma senha, OTP, assertion ou
ID token sobrevive à chamada que a consumiu.

### Complete

| Field | Required | Validation |
|-------|----------|------------|
| `continuationReference` | yes | hash corresponde a fluxo `OPEN`, finalidade legal, usuário ativo e validade vigente |
| `acceptedLegalDocumentIds` | yes | conjunto sem duplicidade e exatamente compatível com todas as versões obrigatórias correntes |

Na mesma fronteira transacional, a facade bloqueia o fluxo, reconsulta `LegalDocumentFacade`, consome o marcador
`AuthenticationProof.LEGAL_CONSENT`, insere apenas as novas evidências imutáveis e prepara a conclusão da autenticação.
O `AuthenticationFlow` permanece aberto somente até o lifecycle oficial preparar/publicar a sessão e então consumi-lo.
Se o catálogo mudou, nenhuma
evidência parcial é gravada: a resposta contém uma nova continuação e a UI descarta as seleções anteriores. Somente
`AUTHENTICATED` permite ao RFW preparar e publicar a sessão global/local conforme o lifecycle oficial.

### Cancel

`cancelAuthenticationConsent(reference)` invalida de forma idempotente o fluxo ainda aberto e não altera aceites
anteriores. A UI só retorna ao login depois do sucesso; indisponibilidade mantém o gate fechado e permite nova
tentativa. Catálogo vazio, ID ausente, duplicado, documento opcional ou integridade indisponível nunca liberam sessão.

O adapter Rinos não será registrado antes da entrega do schema e da facade reais. Ausência do bean mantém a capability
`AUTHENTICATION_CONSENT` fora da composição, sem provider provisório ou sucesso artificial.

## External Identity

**RFW contracts**: `RFWExternalIdentityProvider`, `RFWExternalIdentityResolver` e
`RFWExternalIdentityManagementProvider`<br>
**Rinos facades propostas**: `GoogleAuthenticationFacade` e `ExternalIdentityManagementFacade`

- O provider técnico valida o ID token e entrega `RFWVerifiedExternalIdentityVO`.
- O Rinos localiza login por `issuer + subject`.
- Para login, vínculo ausente sempre produz rejeição/continuação segura, nunca associação por e-mail.
- Para vínculo autenticado, a requisição inclui operação de reautenticação já aprovada e confirmação explícita.
- `link`/`unlink` aplicam unicidade e invariantes do último método em transação.

## Passkey Authentication and Management

**RFW/Spring contracts**: endpoints WebAuthn, `RFWPasskeyComponent`,
`PublicKeyCredentialUserEntityRepository`, `UserCredentialRepository` e `RFWPasskeyManagementProvider`<br>
**Rinos facades**: `PasskeyAuthenticationFacade` e, na etapa de gestão, `PasskeyManagementFacade`

O adapter de persistence converte sem perda entre `CredentialRecord` e `PasskeyCredential`. O endpoint de assertion
devolve uma prova validada para o orquestrador RFW; não redireciona como autenticado antes dos gates do Rinos.

`RFWPasskeyAuthenticationProviderAdapter` aceita exclusivamente `WebAuthnAuthentication` com a authority
`FACTOR_WEBAUTHN`, extrai apenas o `userHandle` do principal validado e descarta qualquer autenticação genérica. A
`PasskeyAuthenticationFacade` resolve novamente o owner e seus métodos ativos, rejeita prova futura ou mais antiga
que a validade configurada do desafio e inicia o orquestrador com `PASSKEY` e `userVerification=true`. A garantia
phishing-resistant pode satisfazer uma exigência multifator, mas não contorna o gate legal nem o lifecycle oficial da
sessão.

O `SpringWebAuthnUserDetailsService` existe somente porque o provider WebAuthn do Spring exige essa resolução depois
da validação da credential. Ele devolve usuário global ativo, nenhuma authority de aplicação e uma senha sintética
aleatória, diferente a cada leitura; portanto não cria uma credencial alternativa utilizável pelo provider de senha
do Spring.

Operações de gestão:

| Operation | Precondition | Effect |
|-----------|--------------|--------|
| list | usuário atual ativo | somente referências, label, criação/último uso e estado seguro |
| register | reautenticação recente | cria credential ativa após protocolo válido e nome informado |
| rename | própria credential ativa | altera somente label |
| revoke | reautenticação recente e outro método utilizável | revoga uma credential sem apagar as demais |

## Session Management

**RFW contract**: `RFWSessionManagementProvider`<br>
**Rinos facade proposta**: `SessionManagementFacade`

### List result

| Field | Description |
|-------|-------------|
| `reference` | valor opaco somente para gestão |
| `current` | calculado contra a sessão autenticada atual, nunca recebido do cliente |
| `createdAt` | criação UTC |
| `lastActivityAt` | última atividade persistida |
| `deviceDescription` | descrição sanitizada e localizada |
| `locationDescription` | origem aproximada; não entrega IP bruto por padrão |
| `status` | ativa/revogada/expirada quando a UI precisar distinguir |

`revoke(reference)` autoriza somente sessão do próprio usuário. `revokeAll(keepCurrent)` bloqueia o usuário e as
sessões alvo na mesma transação. Revogar a sessão corrente limpa também o contexto local e o cookie.

A autoridade de backend exige simultaneamente `userId` e a referência da sessão corrente ainda ativa; depois
bloqueia o usuário e todas as sessões ativas em ordem determinística antes de localizar o alvo. Uma referência de
outro usuário é tratada como alvo ausente e nunca revela nem altera a sessão estrangeira. Revogação remota repetida
é sucesso idempotente; revogar a atual ou todas faz o próximo guard remover contexto e cookie em qualquer instância.

O `SessionManagementFacade` e seus contratos seguros já materializam listar, revogar uma, as outras e todas. O
`RFWSessionManagementProvider` permanece propositalmente sem bean até a tarefa de interface integrar também a
reautenticação exigida para a ação abrangente; assim a capability não aparece prematuramente.

## Authentication Session Lifecycle

**RFW contracts**: `RFWAuthenticationSessionLifecycleProvider`,
`RFWAuthenticationSessionPreparationVO`, `RFWAuthenticationSessionValidationVO`,
`RFWAuthenticationSessionStatusEnum` e `RFWAuthenticationSessionPrincipal`<br>
**Rinos facade**: `AuthenticationSessionLifecycleFacade`<br>
**Rinos adapter**: `RFWAuthenticationSessionLifecycleProviderAdapter`

O lifecycle da sessão global segue uma ordem fechada:

1. `prepare(...)` persiste um estado ainda não utilizável e devolve o principal com referência opaca;
2. o RFW renova a sessão HTTP e salva o `SecurityContext` local;
3. `publish(...)` ativa a sessão global;
4. o cookie persistente, quando solicitado, é criado somente depois da publicação global;
5. falha em qualquer etapa posterior à preparação limpa contexto/cookie e chama `abort(...)` de forma idempotente.

`prepare(...)` revalida propriedade do fluxo, usuário `ACTIVE`, garantia, evidências, escolha persistente e
disponibilidade atual dos métodos comprovados e documentos legais vigentes. Ela grava `AuthSession.PREPARED` ligada
de forma única ao fluxo, mas não consome o fluxo
nem registra sucesso. `publish(...)` repete as validações críticas e, numa única transação, consome o fluxo, muda a
sessão para `ACTIVE` e registra `AUTHENTICATION_SUCCEEDED` e `AUTHENTICATION_SESSION_CREATED`. A continuação efêmera
fica em `Authentication.details` apenas até a preparação; o principal final conserva somente identidade e referência
opaca não autenticadora.

Uma preparação abortada é revogada e perde o vínculo exclusivo com o fluxo aberto, permitindo nova preparação.
Se a falha ocorrer depois da publicação, `abort(...)` revoga a sessão ativa. Em ambos os casos, repetir a compensação
não produz transição ou evento adicional.

Chamadas concorrentes de `prepare(...)` são serializadas pela ordem usuário → fluxo → sessão e devolvem a mesma
preparação persistida. Falha ao consumir o fluxo, ativar a sessão ou gravar qualquer evento reverte toda a publicação:
o fluxo permanece `OPEN`, a sessão permanece `PREPARED` e uma repetição segura continua possível.

O guard consulta `validate(...)` antes de liberar toda requisição autenticada. `INVALID`, `EXPIRED`, `REVOKED` e
`BLOCKED` encerram estado remoto, contexto e cookie; `UNAVAILABLE` bloqueia com HTTP 503 sem revogar a credencial.
`close(...)` participa do logout programático e HTTP e deve ser idempotente. Aplicações sem o provider mantêm o
comportamento local anterior.

O filtro cobre requests normais, chamadas protegidas e heartbeats autenticados do Vaadin. Cada passagem consulta a
autoridade global para observar revogação cross-instance imediatamente, mas somente atualiza `lastActivityAt` e
`idleExpiresAt` quando os cinco minutos configurados desde a última gravação tiverem transcorrido. O limite de
inatividade é sempre `min(now + idleTimeout, absoluteExpiresAt)`; alcançar exatamente qualquer limite expira antes
de tentar renovar atividade.

Bloqueio, desativação ou cancelamento passam pelo lifecycle operacional da identidade, que troca o estado e revoga
todas as sessões sob a mesma transação e correlação. A substituição de uma senha existente possui uma operação
separada da criação inicial: grava o novo hash e revoga todas as sessões atomicamente. A recuperação existente será
conectada a essa operação na tarefa 4.4.1, junto da invalidação adicional de provas e fatores aplicáveis.

A referência implementada por `RFWAuthenticationSessionPrincipal` não é segredo nem credencial, não equivale ao ID
sequencial do banco e não aparece em URL, mensagem ou log. Preparações abandonadas por queda do processo devem
expirar pela política da hospedeira.

## Remember-me

**RFW contracts**: `RFWPersistentLoginProvider`, `RFWPersistentLoginOutcomeVO` e
`RFWPersistentLoginStatusEnum`<br>
**Rinos facade proposta**: `PersistentLoginFacade`

`RFWPersistentLoginProvider` mantém o lifecycle completo:

1. criar cookie apenas depois da sessão global persistida;
2. resolver cookie em nova requisição sem sessão local;
3. rotacionar o validador atomicamente antes de retornar `RESTORED`;
4. limpar cookie após expiração, bloqueio ou revogação;
5. construir `Authentication` somente a partir de sessão e usuário ainda válidos.

O filtro RFW não substitui autenticação existente. `INVALID`, `EXPIRED`, `REVOKED`, `BLOCKED` e
`REPLAY_DETECTED` limpam o cookie; o último exige que a facade já tenha revogado a família. `UNAVAILABLE` mantém o
cookie sem autenticar a requisição. Logout pelo serviço RFW ou pela cadeia HTTP aciona revogação e limpeza. O callback
`RFWRememberMeProvider` continua compatível apenas para criação e nunca ativa restauração automática.

O contrato público não expõe hash, selector interno, validator, token bruto ou `HttpSession` ID. O lifecycle global
já é registrado sobre o modelo persistente; o provider de login persistente somente será registrado quando a emissão,
leitura e rotação do cookie estiverem completas, sem capability provisória.

A implementação usa uma fachada HTTP deliberadamente restrita: ela recebe request/response servlet, manipula o
cookie dentro da própria fronteira e devolve somente status, principal mínimo e referência não autenticadora. Essa
exceção técnica evita transportar selector/validator por DTO, VO, estado Vaadin ou pelo adapter RFW. O cookie usa
`Path=/`, `HttpOnly`, `Secure` conforme a configuração do ambiente, `SameSite=Strict` e `Max-Age` limitado ao
vencimento absoluto persistido. Cookies ausentes, duplicados ou vazios nunca alcançam a consulta autenticadora.

## Reauthentication

**RFW contracts**: `RFWReauthenticationChallengeProvider`, `RFWReauthenticationBeginRequestDTO`,
`RFWReauthenticationVerificationRequestDTO`, `RFWReauthenticationChallengeVO` e
`RFWReauthenticationOutcomeVO`<br>
**Rinos facade**: `ReauthenticationFacade`<br>
**Rinos adapter**: `RFWReauthenticationChallengeProviderAdapter`

Uma consulta inicial verifica se `lastStrongAuthAt` já está dentro dos 15 minutos e se o nível/método satisfaz a
operação. Quando não estiver, devolve referência opaca, validade, rótulo humano e catálogo de senha, TOTP e/ou
passkey. A conclusão atualiza somente a sessão corrente e registra evento; não cria outra sessão e não concede
authority.

O catálogo fechado inicial reconhece criação/alteração de senha, nomeação/cadastro/revogação de passkey,
inclusão/remoção de fator, regeneração de recovery codes, vínculo/desvínculo Google e revogação de uma ou todas as
sessões. A política nunca oferece `GOOGLE`, `EMAIL_CODE` ou `RECOVERY_CODE` como prova interativa de reautenticação;
ela intersecta `PASSWORD`, `TOTP` e `PASSKEY` com os métodos atualmente utilizáveis. Uma sessão Google recente, por
si só, não dispensa o desafio de uma operação sensível. O limite de 15 minutos é inclusivo: somente instantes
posteriores exigem nova prova.

`begin(...)` pode responder `ALREADY_RECENT` ou `CHALLENGE_REQUIRED`. `verify(...)` recebe a referência, o método
escolhido e uma prova transitória e só permite continuar a operação original em `COMPLETED`. A referência deve estar
vinculada ao usuário, sessão e operação, expirar, ser consumida uma única vez e ser cancelada quando a UI fechar. O
`operationId` permanece interno; somente o rótulo i18n humano do desafio é exibido. O provider tipado tem precedência
sobre `RFWReauthenticationProvider`, mantido como adapter legado de senha.

O adapter obtém identidade e referência de sessão exclusivamente do principal autenticado; esses valores não
são aceitos do componente. O catálogo apresentado é a interseção entre métodos ativos, métodos permitidos pela
operação e verificadores criptográficos realmente implementados. Nesta etapa somente `PASSWORD` possui verificador
real. TOTP e passkey permanecem ocultos até suas cerimônias das tarefas 4.1 e 4.2, mesmo que a credencial persistida
já exista. A prova é transitória, nunca entra em entity, auditoria, retorno ou `SecurityContext`.

O gate legal pós-autenticação é publicado por `RFWAuthenticationConsentProviderAdapter`. Ele encaminha os IDs das
versões aceitas para `AuthenticationConsentFacade`, e somente um resultado `READY` volta ao lifecycle oficial do RFW.
O token intermediário continua sem authorities; a sessão global utilizável só nasce no provider de lifecycle.
Cancelamento não publica autenticação e a expiração persistente continua sendo o fallback seguro quando o backend
estiver indisponível.

O vínculo persistente não armazena callback, payload da tela nem fotografia do alvo. `COMPLETED` autoriza somente a
retomada que o componente RFW manteve em memória. Essa operação é chamada novamente e deve reler seu alvo, conferir a
sessão recente e reaplicar versão e invariantes transacionalmente; se o estado tiver mudado durante o diálogo, devolve
`CONFLICT`/`STALE` sem reaplicação automática. Assim, consumo único da prova e concorrência do recurso protegido são
controles complementares, não uma persistência perigosa da operação original.

## Security Settings

**RFW component**: `RFWSecuritySettingsComponent`<br>
**Rinos facades**: providers autenticados específicos.

| Section | Provider | Rinos authority |
|---------|----------|-----------------|
| password | `RFWPasswordManagementProvider` | estado e criação/substituição da senha local do usuário atual |
| passkeys | `RFWPasskeyManagementProvider` | passkeys do usuário atual |
| factors | `RFWSecondFactorManagementProvider` | TOTP/e-mail, enrollment e último método |
| external identities | `RFWExternalIdentityManagementProvider` | vínculos `issuer + sub` |
| sessions | `RFWSessionManagementProvider` | sessões do usuário atual |
| recovery codes | `RFWSecondFactorManagementProvider` | conjunto ativo e regeneração |

Toda operação sensível executa novamente a invariant no backend depois da reautenticação; ocultar/desabilitar botão
na UI não é controle de segurança.

O enrollment TOTP concreto usa `TotpManagementFacade` e o adapter
`RFWSecondFactorManagementProviderAdapter`. O adapter deriva o usuário exclusivamente do principal autenticado e
entrega à RFW referência opaca, validade, URI `otpauth://` e segredo manual somente na resposta inicial. A RFW gera o
QR localmente a partir da URI; nenhuma imagem ou segredo é enviado a serviço externo. Fechar o diálogo cancela a
pendência, e nova emissão revoga qualquer pendência anterior do mesmo usuário.

A confirmação bloqueia usuário e fator no banco global, aplica a janela `±1` definida em
`rfw.authentication.second-factor`, persiste o passo exato aceito e recusa qualquer passo igual ou anterior. O
segredo fica cifrado por AEAD com vínculo a usuário e referência; resultados terminais, listagem e representação
textual não o devolvem. A validade e o máximo de tentativas vêm de `rinos.authentication.mfa`; dígitos, período,
janela e parâmetros dos protocolos RFW pertencem exclusivamente a `rfw.authentication.second-factor`, sem segunda
origem no Rinos.

`RecoveryCodeManagementFacade` usa `RFWRecoveryCodeService` para produzir exatamente 10 códigos distintos e criar
um Argon2id independente para cada valor. A configuração
`rfw.authentication.second-factor.recovery-code-count` permanece a fonte do protocolo, mas o Rinos falha no startup
se ela divergir de 10, pois essa quantidade faz parte do contrato funcional. A geração bloqueia o usuário ativo,
invalida o conjunto anterior e persiste o novo conjunto numa única transação; somente depois do commit a fachada
devolve os códigos legíveis. Não existe consulta capaz de reapresentá-los, e `toString()` dos valores transitórios os
redige. O consumo percorre apenas hashes `AVAILABLE` do conjunto ativo sob a ordem de lock usuário → conjunto →
códigos e possui um único vencedor concorrente; o último uso muda o conjunto para `EXHAUSTED`.

A fachada pública está pronta para a apresentação de uso único já suportada pela RFW. O consumo de
`RECOVERY_CODE` já está vinculado atomicamente ao fluxo pelo `SecondFactorFacade`; o adapter de gestão autenticada
será conectado à tela na tarefa 5.4.1, preservando a reautenticação sensível existente.

Para senha, `getPasswordCredential()` devolve somente `RFWPasswordCredentialVO`: estado `ABSENT`, `CONFIGURED` ou
`COMPROMISED`, datas públicas e versão concorrente. `changePassword(...)` recebe `RFWPasswordChangeRequestDTO` com
senha e confirmação transitórias e `expectedVersion`; não recebe nem devolve hash. O provider deve revalidar a
garantia recente, política, comprometimento e versão dentro da operação protegida, produzir Argon2id no domínio,
auditar sem segredo e aplicar a política de invalidação de sessões. Rejeições por campo usam exclusivamente
`newPassword` e `confirmation`; conflito ou estado obsoleto solicitam refresh da seção. A criação passwordless segue
o mesmo contrato e nunca vincula automaticamente uma identidade externa.

Consultas e mutações usam preferencialmente os métodos `*Outcome` dos providers e devolvem
`RFWSecurityManagementOutcomeVO<T>`. O status distingue `COMPLETED`, `REJECTED`, `CONFLICT`, `LAST_METHOD`,
`INSUFFICIENT_ASSURANCE`, `STALE` e `UNAVAILABLE`; falhas carregam somente `RFWAccessErrorVO` público. `CONFLICT` e
`STALE` exigem `refreshRequired=true`. O Rinos deve revalidar invariants transacionalmente e devolver outcome, sem
pedir à UI que deduza o resultado por exceção ou pela fotografia anteriormente carregada.

Cada seção é consultada e atualizada de forma independente. Uma falha não pode apagar dados de outra seção; respostas
assíncronas antigas não substituem uma consulta mais nova. Após sucesso ou refresh obrigatório, somente a seção
afetada é reconsultada. Remover/revogar algo já ausente produz `COMPLETED` quando a operação for idempotente. Diálogos
fecham apenas após `COMPLETED`; rejeições permanecem associadas à ação originadora.

`RFWAuthenticationMethodVO` transporta `createdAt` e estado seguro (`PENDING`, `ACTIVE`, `DISABLED`, `REVOKED`), sem
credential ID, chave, segredo ou prova. Os métodos legados de `List`/`Void` permanecem adaptados para compatibilidade,
mas providers reais do Rinos devem implementar os outcomes tipados para preservar conflitos e invariants.

## Passkey UI Status

**RFW contracts**: `RFWPasskeyStatusEnum`, `RFWPasskeyStatusEvent` e `RFWPasskeyMessagesVO`<br>
**Rinos consumers**: composição de acesso, configurações de segurança e telemetria sanitizada.

O componente publica `STARTED`, `COMPLETED`, `CANCELLED`, `UNAVAILABLE` e `REJECTED` sem transportar credential ID,
assertion, challenge ou causa interna do browser. Esses estados são observacionais: somente o evento de conclusão
gerado depois dos endpoints WebAuthn e da orquestração aplicável pode avançar o fluxo autenticado.

Durante a cerimônia, somente o botão de passkey fica ocupado; senha, Google ou outro método independente continuam
disponíveis. Cancelamento, incompatibilidade e falha remota preservam a tela, anunciam texto localizado em região
`role=status` e devolvem o foco à ação. O Rinos pode contabilizar status e operação, mas não deve registrar mensagem
livre, detalhe da exceção do browser ou material criptográfico.

## Error Contract

| Category | Public behavior | Internal evidence |
|----------|-----------------|-------------------|
| validation | erro associado ao campo, sem valor secreto | código fechado e correlation ID |
| rejected | mensagem neutra | causa real sanitizada quando houver usuário |
| rate limited | espera aplicável sem dimensão disparadora | política e contador sem e-mail/IP em tag |
| unavailable | tentar novamente/outro método independente | integração, timeout e correlation ID |
| conflict | recarregar estado atual | constraint/versão concorrente |
| security invariant | ação impedida e orientação | invariant, usuário e operação; sem prova |
