# Contracts: Serviços Externos do Cadastro de Usuário

Estes são contratos de saída da aplicação. O Rinos não expõe API REST pública nesta feature; a UI Vaadin consome facades Java no mesmo processo.

## Cloudflare Turnstile Siteverify

**Owner técnico**: RFW Platform  
**Owner da política**: Rinos `user-registration`  
**Method**: `POST https://challenges.cloudflare.com/turnstile/v0/siteverify`  
**Auth**: secret key enviada somente pelo backend

### Request

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `secret` | string | yes | Vem exclusivamente de configuração protegida |
| `response` | string | yes | Máximo de 2048 caracteres; nunca persistido |
| `remoteip` | string | no | Origem já resolvida por política de proxy confiável |
| `idempotency_key` | UUID | yes no Rinos | Novo por tentativa de validação |

### Accepted response

| Field | Type | Validation |
|-------|------|------------|
| `success` | boolean | Deve ser `true` |
| `hostname` | string | Deve pertencer à allowlist configurada |
| `action` | string | Deve ser a ação esperada para cadastro |
| `challenge_ts` | timestamp | Deve ser parseável e compatível com token ainda válido |
| `error-codes` | string[] | Registrados somente de forma sanitizada quando houver falha |

### Failure mapping

| Condition | Rinos result |
|-----------|--------------|
| Token ausente, inválido, expirado ou reutilizado | `HUMAN_VERIFICATION_REQUIRED` com renovação do widget |
| `hostname` ou `action` divergente | `HUMAN_VERIFICATION_INVALID` e evento de segurança |
| Timeout, HTTP 5xx ou resposta inválida | `HUMAN_VERIFICATION_UNAVAILABLE`; nenhuma persistência |
| Configuração ausente/inválida quando obrigatória | Falha explícita de inicialização |

## Google OpenID Connect

**Owner técnico**: adapter de identidade externa do Rinos, reutilizável por autenticação  
**Flow**: Authorization Code + PKCE + `state` + `nonce`  
**Auth**: client credentials da instalação quando exigidas

### Authorization request

| Field | Required | Validation |
|-------|----------|------------|
| `client_id` | yes | Origem exclusiva em properties |
| `redirect_uri` | yes | Match exato da URI cadastrada |
| `response_type=code` | yes | Valor fixo |
| `scope=openid email` | yes | Não solicitar outros serviços Google |
| `state` | yes | Aleatório, armazenado somente como hash |
| `nonce` | yes | Aleatório, comparado ao ID token |
| PKCE challenge | yes | S256 |

### ID token claims accepted

| Claim | Validation |
|-------|------------|
| `iss` | Emissor Google explicitamente permitido |
| `aud` | Contém o client ID da instalação |
| `exp`, `iat` | Válidos com tolerância de relógio limitada |
| `nonce` | Corresponde à tentativa aberta e não usada |
| `sub` | Presente; usado com `iss` como identidade estável |
| `email` | Sintaticamente válido e normalizado pelo Rinos |
| `email_verified` | Obrigatoriamente `true` |

### Failure mapping

| Condition | Rinos result |
|-----------|--------------|
| `state`, PKCE ou `nonce` inválido/reutilizado | `EXTERNAL_IDENTITY_REJECTED`; nenhuma escrita de usuário |
| Assinatura, issuer, audience ou tempo inválido | `EXTERNAL_IDENTITY_REJECTED` |
| E-mail não verificado | `EXTERNAL_EMAIL_NOT_VERIFIED` |
| `issuer + sub` já vinculado a outro usuário | `EXTERNAL_IDENTITY_CONFLICT` sem expor o outro usuário |
| E-mail de usuário ativo sem vínculo | `EXISTING_USER_REAUTHENTICATION_REQUIRED` |
| Timeout/indisponibilidade | `EXTERNAL_IDENTITY_UNAVAILABLE`; oferecer cadastro local |

Access token, refresh token, ID token e authorization code não são persistidos nem registrados.

## Pwned Passwords Range API

**Owner técnico**: adapter de política de senha do Rinos  
**Method**: `GET https://api.pwnedpasswords.com/range/{prefix}`  
**Auth**: não exigida para Pwned Passwords

### Request

| Element | Validation |
|----------|------------|
| `prefix` | Exatamente os primeiros 5 caracteres uppercase do SHA-1 calculado localmente |
| `User-Agent` | Identifica o Rinos conforme configuração |
| `Add-Padding` | Habilitado para reduzir inferência pelo tamanho da resposta |

### Response

O adapter procura localmente o sufixo restante do SHA-1 entre as linhas `SUFFIX:COUNT`. O prefixo, a resposta e o sufixo comparado não são persistidos.

### Failure mapping

| Condition | Rinos result |
|-----------|--------------|
| Sufixo encontrado com contagem positiva | `PASSWORD_COMPROMISED` |
| Sufixo ausente | Senha passa nesta verificação e segue para hashing |
| Timeout, HTTP não esperado ou payload inválido | `PASSWORD_CHECK_UNAVAILABLE`; cadastro local não persiste |

## SMTP por RFW

**Owner técnico**: RFW Platform `EmailDispatchService`/`EmailDispatcher`  
**Transport**: SMTP configurado pela instalação

### Application request

| Field | Required | Validation |
|-------|----------|------------|
| template lógico | yes | Recurso existente no classpath |
| destinatário | yes | E-mail imutável do cadastro |
| URL de confirmação | yes | HTTPS em produção; token presente somente no link |
| expiração exibida | yes | Deve corresponder ao `expiresAt` persistido |
| locale | no | Fallback documentado pelo RFW |

### Failure mapping

| Condition | Rinos result |
|-----------|--------------|
| Mensagem aceita pelo dispatcher | Cadastro permanece pendente e UI confirma envio |
| Falha de template ou transporte | Cadastro permanece pendente; UI oferece reenvio |
| Reenvio | Nova comprovação invalida anteriores antes do novo dispatch |

O log registra apenas resultado operacional e correlation ID; não registra destinatário completo, conteúdo nem URL secreta.

