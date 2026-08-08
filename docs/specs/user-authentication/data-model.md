# Data Model: Autenticação e Recuperação do Usuário

O modelo pertence integralmente ao schema global `rinos_global`. Nenhuma tabela recebe `tenantId`. Identidade,
métodos e sessão demonstram quem é o usuário; não armazenam concessões de acesso de conta.

Os nomes seguem o padrão do projeto: prefixo funcional em inglês, tabelas e colunas em `camelCase`, PK
`BIGINT AUTO_INCREMENT`, constraints nomeadas em `snake_case`, `TIMESTAMP(6)` em UTC e InnoDB.

## Existing Entities Reused

### `identity_user`

Permanece a raiz global. Somente `ACTIVE` inicia ou mantém sessão. Mudança para `BLOCKED`, `DEACTIVATED` ou
`CANCELLED` invalida sessões, fluxos e provas.

### `identity_localCredential`

A tabela existente recebe evolução incremental:

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `passwordChangedAt` | `TIMESTAMP(6)` | NOT NULL | Inicialmente igual à criação/última substituição |
| `compromisedAt` | `TIMESTAMP(6)` | NULL | Impede login por senha até redefinição |
| `lastUsedAt` | `TIMESTAMP(6)` | NULL | Atualizado depois de autenticação válida |

`passwordHash` mantém o formato do `DelegatingPasswordEncoder`. Uma redefinição substitui o hash, limpa
`compromisedAt`, atualiza `passwordChangedAt` e invalida todas as sessões e provas aplicáveis na mesma operação.

### `identity_externalIdentity`

Vínculos `ACTIVE` continuam localizados somente por `(issuer, subject)`. A feature acrescenta `lastUsedAt` e preserva
o e-mail externo fora da chave. Remoção é bloqueada quando o vínculo for o último método utilizável.

### `identity_passwordRecovery`

A recuperação mínima já implementada continua como prova opaca de redefinição de senha. A migration da feature deve
alinhar estados, retenção e invalidação total com as novas sessões/fatores, sem criar uma segunda tabela equivalente.

### `security_originWindow`

Reutilizada para a dimensão IP das políticas `SIGN_IN`, `PASSWORD_RECOVERY` e `EMAIL_OTP`. A origem permanece binária,
tem retenção curta e não é copiada para eventos permanentes.

### `identity_legalDocumentVersion` e `identity_legalConsent`

Continuam autoridades do gate legal. Novas decisões são inseridas; nenhuma evidência anterior é atualizada.

## Entity: AuthenticationFlow

**Tabela proposta**: `identity_authenticationFlow`

Continuação transitória entre uma prova inicial e a criação da sessão.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto increment | Não exposto |
| `referenceHash` | `BINARY(32)` | NOT NULL, UK | SHA-256 da referência opaca de 256 bits |
| `idUser` | `BIGINT` | NULL, FK | Nulo para tentativa que não deve revelar ausência |
| `purpose` | `VARCHAR(32)` | NOT NULL | `SIGN_IN`, `REAUTHENTICATION`, `FACTOR_RECOVERY`, `LEGAL_CONSENT` |
| `primaryMethod` | `VARCHAR(32)` | NULL | Método já comprovado |
| `requiredAssurance` | `VARCHAR(24)` | NOT NULL | `SINGLE_FACTOR`, `MULTI_FACTOR`, `PHISHING_RESISTANT` |
| `persistentLoginRequested` | `BOOLEAN` | NOT NULL | Escolha feita no início e preservada por todas as continuações |
| `status` | `VARCHAR(24)` | NOT NULL | `OPEN`, `USED`, `INVALIDATED`, `EXPIRED` |
| `failureCount` | `INT` | NOT NULL | Tentativas rejeitadas do fluxo |
| `issuedAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `expiresAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `usedAt` | `TIMESTAMP(6)` | NULL | UTC |
| `invalidatedAt` | `TIMESTAMP(6)` | NULL | UTC |
| `correlationId` | `BINARY(16)` | NOT NULL | Correlação sanitizada |
| `createdAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `updatedAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `version` | `BIGINT` | NOT NULL | `@Version` |

### Constraints and rules

- UK em `referenceHash` e índice em `(idUser, purpose, status, expiresAt)`.
- `OPEN` vencido é rejeitado imediatamente, ainda que o job não o tenha removido.
- Uma conclusão bloqueia a linha e a transiciona uma única vez.
- O cliente não redefine `persistentLoginRequested` nas continuações; a conclusão usa o valor vinculado ao fluxo.
- Fluxo sem `idUser` nunca pode terminar em autenticação; ele existe apenas quando necessário para equalizar resposta
  e custo observável.

## Entity: AuthenticationFlowMethod

**Tabela proposta**: `identity_authenticationFlowMethod`

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto increment | |
| `idAuthenticationFlow` | `BIGINT` | NOT NULL, FK | |
| `method` | `VARCHAR(32)` | NOT NULL | Enum fechado permitido no fluxo |
| `createdAt` | `TIMESTAMP(6)` | NOT NULL | UTC |

UK em `(idAuthenticationFlow, method)`. A coleção é inserida junto do fluxo e não recebe valores livres do cliente.

## Entity: AuthenticationProof

**Tabela proposta**: `identity_authenticationProof`

Prova efêmera associada a um fluxo, como OTP de e-mail ou continuação legal.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto increment | |
| `idAuthenticationFlow` | `BIGINT` | NOT NULL, FK | Fluxo proprietário |
| `type` | `VARCHAR(32)` | NOT NULL | `EMAIL_OTP`, `LEGAL_CONSENT`, `FACTOR_RECOVERY` |
| `proofDigest` | `VARBINARY(96)` | NOT NULL | MAC/hash versionado; nunca prova bruta |
| `keyVersion` | `VARCHAR(32)` | NULL | Obrigatório para MAC; nulo para hash opaco |
| `status` | `VARCHAR(24)` | NOT NULL | `OPEN`, `USED`, `INVALIDATED`, `EXPIRED` |
| `activeMarker` | `BOOLEAN` | NULL | `TRUE` somente enquanto `OPEN`; `NULL` em histórico |
| `attemptCount` | `INT` | NOT NULL | Contador atômico |
| `issuedAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `expiresAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `usedAt` | `TIMESTAMP(6)` | NULL | UTC |
| `invalidatedAt` | `TIMESTAMP(6)` | NULL | UTC |
| `createdAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `updatedAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `version` | `BIGINT` | NOT NULL | `@Version` |

UK em `(idAuthenticationFlow, type, activeMarker)` garante no máximo uma prova aberta por tipo, usando o mesmo padrão
de nulidade de `OriginWindow`. Nova emissão bloqueia o fluxo, encerra a prova corrente e só então insere a vencedora.

## Entity: TotpFactor

**Tabela proposta**: `identity_totpFactor`

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto increment | |
| `idUser` | `BIGINT` | NOT NULL, FK | Um TOTP ativo no primeiro incremento |
| `reference` | `BINARY(16)` | NOT NULL, UK | Referência opaca exibível em Base64URL/UUID |
| `label` | `VARCHAR(100)` | NOT NULL | Nome reconhecível |
| `encryptedSecret` | `VARBINARY(512)` | NOT NULL | Ciphertext AEAD |
| `encryptionNonce` | `BINARY(12)` | NOT NULL | Nonce único por cifra |
| `keyVersion` | `VARCHAR(32)` | NOT NULL | Chave necessária para leitura |
| `status` | `VARCHAR(24)` | NOT NULL | `PENDING`, `ACTIVE`, `REVOKED` |
| `lastAcceptedStep` | `BIGINT` | NULL | Impede replay na janela já consumida |
| `confirmedAt` | `TIMESTAMP(6)` | NULL | UTC |
| `lastUsedAt` | `TIMESTAMP(6)` | NULL | UTC |
| `revokedAt` | `TIMESTAMP(6)` | NULL | UTC |
| `createdAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `updatedAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `version` | `BIGINT` | NOT NULL | `@Version` |

### Rules

- Uma linha `PENDING` não é método utilizável e possui retenção curta.
- O segredo e a URI `otpauth` são apresentados somente no enrollment; depois da confirmação, nenhuma facade os
  retorna.
- A confirmação bloqueia a linha, valida um time-step ainda não usado e muda para `ACTIVE`.
- AAD da cifra inclui usuário, referência e versão de chave, impedindo troca de ciphertext entre registros.

## Entity: EmailFactor

**Tabela proposta**: `identity_emailFactor`

Marca a escolha do e-mail confirmado como fator adicional; não duplica o endereço.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto increment | |
| `idUser` | `BIGINT` | NOT NULL, FK, UK | No máximo uma configuração |
| `reference` | `BINARY(16)` | NOT NULL, UK | Referência opaca |
| `status` | `VARCHAR(24)` | NOT NULL | `ACTIVE`, `DISABLED` |
| `activatedAt` | `TIMESTAMP(6)` | NOT NULL | E-mail já foi confirmado na identidade |
| `lastUsedAt` | `TIMESTAMP(6)` | NULL | UTC |
| `disabledAt` | `TIMESTAMP(6)` | NULL | UTC |
| `createdAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `updatedAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `version` | `BIGINT` | NOT NULL | `@Version` |

Troca do e-mail principal não remove a configuração, mas invalida OTPs enviados ao endereço anterior. O fator usa
sempre o e-mail principal confirmado no instante da emissão.

## Entity: RecoveryCodeSet

**Tabela proposta**: `identity_recoveryCodeSet`

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto increment | |
| `idUser` | `BIGINT` | NOT NULL, FK | |
| `reference` | `BINARY(16)` | NOT NULL, UK | Identifica o conjunto sem autenticar |
| `status` | `VARCHAR(24)` | NOT NULL | `ACTIVE`, `INVALIDATED`, `EXHAUSTED` |
| `activeMarker` | `BOOLEAN` | NULL | `TRUE` somente no conjunto ativo |
| `issuedAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `invalidatedAt` | `TIMESTAMP(6)` | NULL | UTC |
| `createdAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `updatedAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `version` | `BIGINT` | NOT NULL | `@Version` |

UK em `(idUser, activeMarker)` e lock do usuário impedem dois conjuntos ativos concorrentes; conjuntos encerrados usam
`NULL` e preservam o histórico permitido.

## Entity: RecoveryCode

**Tabela proposta**: `identity_recoveryCode`

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto increment | |
| `idRecoveryCodeSet` | `BIGINT` | NOT NULL, FK | |
| `codeHash` | `VARCHAR(255)` | NOT NULL | Hash independente do código normalizado |
| `ordinal` | `SMALLINT` | NOT NULL | 1 a 10, não secreto |
| `status` | `VARCHAR(24)` | NOT NULL | `AVAILABLE`, `USED`, `INVALIDATED` |
| `usedAt` | `TIMESTAMP(6)` | NULL | UTC |
| `createdAt` | `TIMESTAMP(6)` | NOT NULL | UTC |

UK em `(idRecoveryCodeSet, ordinal)`. O código bruto nunca é persistido ou reapresentado.

## Entity: PasskeyUser

**Tabela proposta**: `identity_passkeyUser`

Adapta o usuário ao `PublicKeyCredentialUserEntityRepository` do Spring Security.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto increment | |
| `idUser` | `BIGINT` | NOT NULL, FK, UK | |
| `userHandle` | `VARBINARY(64)` | NOT NULL, UK | Aleatório, estável, não derivado do e-mail/ID |
| `createdAt` | `TIMESTAMP(6)` | NOT NULL | UTC |

O nome apresentado ao autenticador pode ser resolvido do e-mail atual durante o protocolo, mas não integra a chave.

## Entity: PasskeyCredential

**Tabela proposta**: `identity_passkeyCredential`

Os campos binários finais devem corresponder sem perda ao `CredentialRecord` da versão Spring Security fixada no POM.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto increment | |
| `idPasskeyUser` | `BIGINT` | NOT NULL, FK | |
| `reference` | `BINARY(16)` | NOT NULL, UK | Referência de gestão |
| `credentialType` | `VARCHAR(32)` | NOT NULL | Tipo público do `CredentialRecord` |
| `credentialId` | `VARBINARY(1024)` | NOT NULL, UK | ID WebAuthn binário |
| `publicKey` | `BLOB` | NOT NULL | Chave pública/COSE conforme adapter Spring |
| `signatureCount` | `BIGINT UNSIGNED` | NOT NULL | Último contador aceito |
| `uvInitialized` | `BOOLEAN` | NOT NULL | Estado exigido pelo `CredentialRecord` Spring |
| `backupEligible` | `BOOLEAN` | NOT NULL | Flag BE |
| `backupState` | `BOOLEAN` | NOT NULL | Última flag BS |
| `transports` | `VARCHAR(255)` | NULL | Enumerações canônicas, sem dado livre |
| `attestationObject` | `BLOB` | NOT NULL | Registro necessário para reconstruir `CredentialRecord` |
| `attestationClientDataJson` | `BLOB` | NOT NULL | Client data de registro exigido pelo contrato Spring |
| `label` | `VARCHAR(100)` | NOT NULL | Nome definido pelo usuário |
| `status` | `VARCHAR(24)` | NOT NULL | `ACTIVE`, `REVOKED` |
| `createdAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `lastUsedAt` | `TIMESTAMP(6)` | NULL | UTC |
| `revokedAt` | `TIMESTAMP(6)` | NULL | UTC |
| `version` | `BIGINT` | NOT NULL | `@Version` |

O adapter da versão Spring Security 7.0.6 reconstrói integralmente `CredentialRecord`, incluindo attestation de
registro, sem serialização Java opaca. Assertions de autenticação temporárias não são persistidas. Nenhum dado
biométrico chega ao Rinos.

## Entity: AuthSession

**Tabela proposta**: `identity_authSession`

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto increment | |
| `idUser` | `BIGINT` | NOT NULL, FK | |
| `publicReference` | `BINARY(16)` | NOT NULL, UK | Usada apenas para listar/revogar |
| `selectorHash` | `BINARY(32)` | NOT NULL, UK | Localização do cookie sem guardar seletor bruto |
| `validatorDigest` | `VARBINARY(96)` | NOT NULL | Verificador protegido/versionado |
| `keyVersion` | `VARCHAR(32)` | NOT NULL | Versão do MAC |
| `remembered` | `BOOLEAN` | NOT NULL | Seleciona política de duração |
| `status` | `VARCHAR(24)` | NOT NULL | `ACTIVE`, `REVOKED`, `EXPIRED` |
| `primaryMethod` | `VARCHAR(32)` | NOT NULL | Método inicial |
| `assuranceLevel` | `VARCHAR(24)` | NOT NULL | Garantia calculada, não autoridade |
| `authenticatedAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `lastStrongAuthAt` | `TIMESTAMP(6)` | NOT NULL | Base da janela de 15 minutos |
| `lastActivityAt` | `TIMESTAMP(6)` | NOT NULL | Atualização limitada |
| `absoluteExpiresAt` | `TIMESTAMP(6)` | NOT NULL | Nunca prorrogado |
| `idleExpiresAt` | `TIMESTAMP(6)` | NOT NULL | Prorrogado até o limite absoluto |
| `deviceDescription` | `VARCHAR(255)` | NULL | Descrição sanitizada e limitada |
| `originAddress` | `VARBINARY(16)` | NULL | IP validado, retido somente com a sessão e seu prazo operacional |
| `userAgentDigest` | `BINARY(32)` | NULL | Reconhecimento sem conservar header integral |
| `revokedAt` | `TIMESTAMP(6)` | NULL | UTC |
| `revocationReason` | `VARCHAR(48)` | NULL | Código fechado |
| `createdAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `updatedAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `version` | `BIGINT` | NOT NULL | `@Version` |

### Session rules

- Cookie bruto, `HttpSession` ID e segredo de validação não são retornados pela gestão.
- `publicReference` não localiza o cookie e não autentica.
- Sessão é válida somente quando `status=ACTIVE`, usuário permanece `ACTIVE`, `now < absoluteExpiresAt` e
  `now < idleExpiresAt`.
- Atualização da atividade usa `min(now + idleTimeout, absoluteExpiresAt)` e ocorre no máximo no intervalo técnico
  configurado.
- Revogação é compare-and-set idempotente. O guard limpa o contexto local na primeira observação.

## Entity: AuthSessionMethod

**Tabela proposta**: `identity_authSessionMethod`

Registra cada método que efetivamente contribuiu para a garantia da sessão.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto increment | |
| `idAuthSession` | `BIGINT` | NOT NULL, FK | |
| `method` | `VARCHAR(32)` | NOT NULL | Senha, Google, passkey, TOTP, e-mail ou recovery code |
| `factorOrder` | `SMALLINT` | NOT NULL | Ordem da comprovação |
| `verifiedAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `userVerification` | `BOOLEAN` | NULL | Aplicável a WebAuthn |

UK em `(idAuthSession, factorOrder)`. Os registros explicam a garantia, mas não concedem authorities.

## Entity: AuthenticationAttemptWindow

**Tabela proposta**: `security_authenticationWindow`

Complementa `security_originWindow` para o identificador informado, sem transformar e-mail em dado de auditoria.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto increment | |
| `identifierDigest` | `BINARY(32)` | NOT NULL | MAC da forma normalizada com chave versionada |
| `keyVersion` | `VARCHAR(32)` | NOT NULL | Permite rotação |
| `operation` | `VARCHAR(32)` | NOT NULL | `SIGN_IN`, `PASSWORD_RECOVERY` |
| `windowStartedAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `windowEndsAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `failureCount` | `INT` | NOT NULL | Incremento atômico |
| `turnstileRequiredUntil` | `TIMESTAMP(6)` | NULL | Reiniciado por nova falha |
| `activeMarker` | `BOOLEAN` | NULL | `TRUE` somente na janela corrente |
| `createdAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `updatedAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `version` | `BIGINT` | NOT NULL | `@Version` |

As linhas são removidas no mesmo limite de retenção antifraude definido para janelas de origem. O digest não é usado
para localizar `User` e não atravessa a facade.

## IdentityEvent Evolution

`identity_event` permanece append-only e recebe novos `eventType` fechados: tentativa/sucesso de autenticação,
desafio emitido/consumido, método adicionado/removido, sessão criada/revogada/expirada, recuperação concluída e risco
de passkey. `idUser` pode ser nulo nas falhas públicas.

Não registrar e-mail, IP completo, cookie, selector, validator, senha, hash de senha, OTP, segredo TOTP, código de
recuperação, credential ID completo, token Google ou prova WebAuthn.

## Cross-Entity Invariants

1. Usuário `ACTIVE` possui ao menos uma credencial utilizável entre senha, Google e passkey.
2. Fator de e-mail não conta como método inicial independente e não impede remoção da última senha/Google/passkey.
3. TOTP `PENDING` e passkey `REVOKED` não contam como métodos utilizáveis.
4. Administrador não pode concluir remoção que o deixe sem fator compatível com MFA.
5. `AuthSession.ACTIVE` exige usuário `ACTIVE` e fluxo de autenticação consumido uma única vez.
6. Nenhuma sessão é criada enquanto faltar aceite obrigatório vigente.
7. OTP/TOTP/código de recuperação aceito não pode ser consumido novamente.
8. Google é identificado somente por `issuer + subject`; e-mail coincidente não cria vínculo.
9. Uma sessão Google não usa `EMAIL_CODE` do mesmo e-mail para atingir garantia administrativa.
10. Alteração/redefinição de senha, bloqueio, desativação, cancelamento e recuperação reforçada invalidam todas as
    sessões e provas conforme a spec.
11. Remover ou revogar um método não apaga o evento histórico sanitizado.
12. Restauração não torna válido qualquer registro cujo estado ou `expiresAt` já o torne inválido.

## Referential Actions

| Child relationship | `ON DELETE` | Rationale |
|--------------------|-------------|-----------|
| `AuthenticationFlow -> User` | `CASCADE` | Continuação não tem finalidade sem identidade vigente. |
| `AuthenticationFlowMethod`, `AuthenticationProof -> AuthenticationFlow` | `CASCADE` | Métodos permitidos e provas pertencem ao fluxo temporário. |
| `TotpFactor`, `EmailFactor`, `RecoveryCodeSet`, `PasskeyUser`, `AuthSession -> User` | `CASCADE` | Exclusão da identidade exige fluxo autorizado e remove credenciais. |
| `RecoveryCode -> RecoveryCodeSet` | `CASCADE` | Código não existe fora do conjunto. |
| `PasskeyCredential -> PasskeyUser` | `CASCADE` | Credencial pertence ao user handle da identidade. |
| `AuthSessionMethod -> AuthSession` | `CASCADE` | Evidência da garantia pertence à sessão. |
| `IdentityEvent -> User` | `SET NULL` | Preserva evento permitido após minimização autorizada. |

## Retention and Cleanup

| Data | Retention/behavior |
|------|--------------------|
| Fluxos e provas expirados | Rejeição imediata por tempo; exclusão física diária depois da janela operacional |
| TOTP pendente | Exclusão diária após expiração do enrollment |
| OTP de e-mail | Exclusão diária após expiração/retenção operacional curta |
| Sessão expirada/revogada | 30 dias por padrão para reconhecimento/auditoria; cookie deixa de funcionar imediatamente |
| Janelas antifraude | Até 30 dias depois do fim da janela, alinhado a `OriginWindow` |
| Métodos ativos e consentimentos | Enquanto a identidade permanecer vigente ou conforme governança futura |
| Eventos de autenticação | 365 dias por padrão; depois, exclusão física. Não contêm segredos e usam minimização terminal aplicável |

Os prazos são configuráveis exclusivamente no `application.properties`. O catálogo diário usa o coordenador global
existente. Cada tarefa relê estado e tempo dentro da transação; falha de limpeza não prolonga validade lógica.
