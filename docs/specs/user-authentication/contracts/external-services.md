# Contracts: Serviços Externos da Autenticação

São contratos de saída ou protocolos de navegador. A UI Vaadin e as facades Java continuam no mesmo processo; não há
API REST pública do Rinos nesta feature.

## Cloudflare Turnstile Siteverify

O contrato técnico permanece o definido em
[`user-registration/contracts/external-services.md`](../../user-registration/contracts/external-services.md), com
operação RFW `sign-in` no login e `password-recovery` na recuperação.

### Authentication policy

- Tornar obrigatório depois de três falhas relacionadas ao mesmo identificador informado **ou** IP dentro de 15
  minutos.
- Manter obrigatório até transcorrerem 15 minutos sem nova falha relacionada.
- Revalidar no servidor `hostname`, `action`, validade e uso único.
- Indisponibilidade quando obrigatório falha fechada para a tentativa afetada.
- O resultado visível não informa qual dimensão exigiu o desafio nem se a identidade existe.

O RFW possui o cliente Siteverify; o Rinos possui contagem, decisão e origem validada.

## Google OpenID Connect

O contrato criptográfico e os claims aceitos permanecem os definidos no cadastro. Para autenticação:

1. O RFW gera nonce e valida assinatura, `iss`, `aud`, `exp`, `iat`, nonce e `email_verified`.
2. O Rinos recebe somente a identidade validada e localiza `ExternalIdentity.ACTIVE` por `(issuer, subject)`.
3. Vínculo encontrado com usuário `ACTIVE` inicia o fluxo de autenticação; estado inválido recebe resposta neutra.
4. Vínculo ausente não é criado por coincidência de e-mail.
5. A conclusão revalida MFA e documentos legais antes de criar sessão.

### Administrative assurance

Google é um primeiro fator federado. Quando uma operação futura exigir garantia administrativa, código enviado ao
mesmo e-mail Google não é aceito como elevação. O desafio oferece TOTP ou passkey com user verification.

### Persistence

ID token, access token, refresh token e mapa integral de claims não são persistidos. `issuer`, `subject`, instante da
validação e evento sanitizado são suficientes. O Rinos não solicita escopos de outros serviços Google.

## WebAuthn Browser and Spring Security

**Browser API**: `navigator.credentials.create/get`<br>
**Server protocol**: endpoints Spring Security WebAuthn configurados pelo RFW<br>
**Relying Party production**: `app.rinos.com.br` sobre HTTPS

### Registration

| Step | Contract |
|------|----------|
| options | POST com CSRF; usuário autenticado e reautenticado; challenge temporário associado à sessão/cerimônia |
| browser | `navigator.credentials.create` com user verification requerida/preferida conforme política aprovada |
| response | attestation e client data Base64URL; limite de payload antes de parsing |
| persistence | Spring valida origem, RP ID, challenge e credential; adapters persistem owner/credential nas tabelas globais sem substituir material imutável |

### Authentication

| Step | Contract |
|------|----------|
| options | POST com CSRF; descoberta sem e-mail permitida; challenge temporário |
| browser | `navigator.credentials.get`; nenhuma biometria sai do autenticador |
| assertion | validar origin, RP ID hash, challenge, assinatura, credential, flags e user verification |
| completion | devolver prova validada ao fluxo RFW/Rinos; não publicar sessão antes dos demais gates |

### Failure mapping

| Condition | Public result |
|-----------|---------------|
| navegador sem suporte/cancelamento local | orientação para outro método, sem marcar credencial como revogada |
| challenge ausente, vencido ou reutilizado | rejeição e reinício da cerimônia |
| origin/RP/signature/credential inválido | rejeição neutra e evento de segurança |
| credential revogada ou usuário indisponível | rejeição neutra |
| contador/backup state anormal | decisão de risco; nunca revogar outros métodos automaticamente |

Nenhuma chave privada, PIN ou biometria é recebida pelo Rinos.

### Persistência adaptada

`SpringWebAuthnUserRepositoryAdapter` implementa `PublicKeyCredentialUserEntityRepository`: resolve somente usuário
global `ACTIVE`, preserva um `userHandle` aleatório e estável e nunca cria ou exclui `identity_user`.
`SpringWebAuthnCredentialRepositoryAdapter` implementa `UserCredentialRepository`: reconstrói `CredentialRecord`
somente para credential e usuário ativos, conserva tipo, ID, chave COSE, user verification, transports, flags de
backup, attestation, label e datas, e permite em saves posteriores apenas contador, backup state e último uso.

Os métodos `delete(...)` dos adapters técnicos rejeitam a operação. Revogação não é uma exclusão física e precisa
passar pela gestão do Rinos para aplicar último método utilizável, garantia administrativa, reautenticação e evento
sanitizado. Essa barreira também impede que endpoints genéricos contornem invariantes do domínio.

## SMTP: OTP e Notificações

**Owner técnico de transporte**: serviço de e-mail do RFW<br>
**Owner da política e templates**: Rinos `user-authentication`

### Message types

| Template | Trigger | Contains secret? |
|----------|---------|------------------|
| `authentication-email-code` | usuário escolhe/reenvia fator de e-mail | OTP de uso único |
| `authentication-new-session` | navegador não reconhecido nas sessões retidas dos 30 dias anteriores | no |
| `authentication-method-changed` | fator/vínculo/passkey alterado | no |
| `authentication-recovery-completed` | senha ou fatores recuperados | no |
| `authentication-repeated-failures` | janela por identificador atinge o limiar do Turnstile fora do cooldown | no |

### OTP dispatch

- Materializar mensagem somente depois do commit da prova.
- Destino é o e-mail principal confirmado relido na transação.
- Código, hash/MAC, corpo e destinatário completo não aparecem em logs ou métricas.
- Falha de SMTP não registra prova como entregue e permite reenvio limitado que invalida a prova anterior.
- Mensagem informa expiração real e que nova emissão invalida a anterior.

O template concreto é `authentication-email-code`. O Rinos passa somente código e expiração ao serviço de templates
da RFW e aguarda o resultado do dispatcher pós-commit. O SMTP não recebe link de autenticação, cookie, referência do
fluxo ou dados de sessão. A compensação de falha usa o digest já persistido, nunca o código em claro.

### Notification dispatch

Notificações de segurança não revertem a alteração já confirmada. Falha fica observável e pode ser reprocessada apenas
quando existir contrato seguro de entrega; não se persiste segredo para viabilizar a retentativa.

- Mudança de método e recuperação concluída notificam uma vez por operação confirmada.
- Falhas repetidas notificam somente usuário existente quando a janela por identificador alcança o limiar do
  Turnstile, com cooldown configurável de 24 horas por padrão.
- Nova sessão usa `authentication-new-session` quando o `userAgentDigest` não aparece nas sessões retidas do mesmo
  usuário nos 30 dias anteriores; digest e IP completo nunca entram na mensagem.

## Pwned Passwords

O contrato k-anonymous já definido em `user-registration` é reutilizado em alteração e redefinição de senha. Não é
consultado em login normal. Indisponibilidade falha fechada somente para a operação que define uma nova senha; outros
métodos independentes continuam disponíveis.

## Reverse Proxy Boundary

- Origem pública canônica: `https://app.rinos.com.br`.
- Porta interna: 7070.
- Links externos usam `rinos.application.public-base-url`, não `Host`/`Forwarded` recebidos.
- Somente proxies explicitamente confiáveis podem fornecer a cadeia de IP.
- Em múltiplas instâncias, o proxy deve manter afinidade do `HttpSession` Vaadin.
- Cookie de autenticação usa `Secure`, `HttpOnly`, path `/`, nome fixo não conflituoso e `SameSite` compatível com os
  retornos Google/WebAuthn. Qualquer exceção de `SameSite` deve ser testada nos navegadores suportados e limitada ao
  cookie estritamente necessário.

## Timeouts and Resilience

| Dependency | Behavior |
|------------|----------|
| Google discovery/JWKS | timeout RFW já configurado; oferecer método local independente |
| Turnstile | timeout explícito; fail-closed quando obrigatório |
| SMTP OTP | timeout explícito; desafio continua sem falsa confirmação e pode ser reemitido |
| Pwned Passwords | timeout explícito; falha fechada para nova senha |
| WebAuthn | challenge local temporário; cancelamento não altera métodos existentes |

Retries automáticos não reapresentam token, OTP, assertion ou operação de uso único. Repetição segura ocorre por novo
fluxo idempotente com nova prova.
