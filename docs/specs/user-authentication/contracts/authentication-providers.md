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
| Primeiro fator e todos os gates concluídos | `AUTHENTICATED` com principal mínimo e sessão já persistida |
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

## Remember-me and Session Lifecycle

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

O contrato público não expõe hash, selector interno, validator, token bruto ou `HttpSession` ID. Até a sessão global
da tarefa 1.6 ser integrada à ordem de publicação, o Rinos não anuncia nem registra seu provider real.

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
| passkeys | `RFWPasskeyManagementProvider` | passkeys do usuário atual |
| factors | `RFWSecondFactorManagementProvider` | TOTP/e-mail, enrollment e último método |
| external identities | `RFWExternalIdentityManagementProvider` | vínculos `issuer + sub` |
| sessions | `RFWSessionManagementProvider` | sessões do usuário atual |
| recovery codes | `RFWSecondFactorManagementProvider` | conjunto ativo e regeneração |

Toda operação sensível executa novamente a invariant no backend depois da reautenticação; ocultar/desabilitar botão
na UI não é controle de segurança.

## Error Contract

| Category | Public behavior | Internal evidence |
|----------|-----------------|-------------------|
| validation | erro associado ao campo, sem valor secreto | código fechado e correlation ID |
| rejected | mensagem neutra | causa real sanitizada quando houver usuário |
| rate limited | espera aplicável sem dimensão disparadora | política e contador sem e-mail/IP em tag |
| unavailable | tentar novamente/outro método independente | integração, timeout e correlation ID |
| conflict | recarregar estado atual | constraint/versão concorrente |
| security invariant | ação impedida e orientação | invariant, usuário e operação; sem prova |
