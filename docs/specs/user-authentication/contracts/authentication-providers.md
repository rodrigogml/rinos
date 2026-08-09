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

## Password Authentication

**RFW contract**: `RFWPasswordAuthenticationProvider`<br>
**Rinos facade proposta**: `AuthenticationFacade.authenticatePassword(PasswordAuthenticationRequestDTO)`

### Input

| Field | Required | Validation |
|-------|----------|------------|
| `identifier` | yes | e-mail normalizado; limite de comprimento antes de custo criptográfico |
| `password` | yes | efêmera; aceita Unicode/espaços conforme credencial registrada |
| `rememberMe` | yes | booleano explícito |
| `turnstileToken` | conditional | exigido pela política por origem/identificador |
| `origin` | yes | fornecida pelo adapter de origem confiável, nunca por campo do cliente |

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

## Second Factor

**RFW contracts**: `RFWSecondFactorProvider`, `RFWSecondFactorEmissionRequestDTO`,
`RFWSecondFactorEmissionOutcomeVO` e `RFWSecondFactorEmissionVO`<br>
**Rinos facade proposta**: `SecondFactorFacade`

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
indisponibilidade não afirma entrega. Passkey abre opções WebAuthn vinculadas ao mesmo fluxo.

### Verify

| Field | Required | Validation |
|-------|----------|------------|
| `challengeReference` | yes | uso único, finalidade correta e ainda válido |
| `method` | yes | método permitido e vinculado ao usuário do fluxo |
| `proof` | conditional | código efêmero para TOTP/e-mail/recovery; ausente no callback WebAuthn |

O resultado final pode autenticar, exigir aceite legal, rejeitar com tentativas restantes não enumeráveis ou limitar.
Nenhuma falha cria sessão parcial.

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

Na mesma fronteira transacional, a facade bloqueia o fluxo, reconsulta `LegalDocumentFacade`, insere apenas as novas
evidências imutáveis, consome a continuação e prepara a conclusão da autenticação. Se o catálogo mudou, nenhuma
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
**Rinos facades propostas**: `PasskeyAuthenticationFacade` e `PasskeyManagementFacade`

O adapter de persistence converte sem perda entre `CredentialRecord` e `PasskeyCredential`. O endpoint de assertion
devolve uma prova validada para o orquestrador RFW; não redireciona como autenticado antes dos gates do Rinos.

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

O guard consulta `validate(...)` antes de liberar toda requisição autenticada. `INVALID`, `EXPIRED`, `REVOKED` e
`BLOCKED` encerram estado remoto, contexto e cookie; `UNAVAILABLE` bloqueia com HTTP 503 sem revogar a credencial.
`close(...)` participa do logout programático e HTTP e deve ser idempotente. Aplicações sem o provider mantêm o
comportamento local anterior.

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

## Reauthentication

**RFW contracts**: `RFWReauthenticationChallengeProvider`, `RFWReauthenticationBeginRequestDTO`,
`RFWReauthenticationVerificationRequestDTO`, `RFWReauthenticationChallengeVO` e
`RFWReauthenticationOutcomeVO`<br>
**Rinos facade proposta**: `ReauthenticationFacade`

Uma consulta inicial verifica se `lastStrongAuthAt` já está dentro dos 15 minutos e se o nível/método satisfaz a
operação. Quando não estiver, devolve referência opaca, validade, rótulo humano e catálogo de senha, TOTP e/ou
passkey. A conclusão atualiza somente a sessão corrente e registra evento; não cria outra sessão e não concede
authority.

`begin(...)` pode responder `ALREADY_RECENT` ou `CHALLENGE_REQUIRED`. `verify(...)` recebe a referência, o método
escolhido e uma prova transitória e só permite continuar a operação original em `COMPLETED`. A referência deve estar
vinculada ao usuário, sessão e operação, expirar, ser consumida uma única vez e ser cancelada quando a UI fechar. O
`operationId` permanece interno; somente o rótulo i18n humano do desafio é exibido. O provider tipado tem precedência
sobre `RFWReauthenticationProvider`, mantido como adapter legado de senha.

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
