# Data Model: Cadastro e Ciclo Inicial do Usuário

O modelo pertence integralmente ao schema global. Nenhuma tabela desta feature possui `tenantId` ou referência para schema de tenant.

Os nomes físicos seguem as regras MySQL do projeto: prefixo funcional em inglês, tabelas e colunas em `camelCase`, PK `id BIGINT AUTO_INCREMENT`, constraints nomeadas em `snake_case` e engine InnoDB.

## Entity: User

**Tabela proposta**: `identity_user`

Identidade global estável. O identificador interno nunca é exibido como código funcional.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto increment | Identificador global não reutilizável |
| `email` | `VARCHAR(320)` | NOT NULL | E-mail sem espaços externos, preservado para apresentação |
| `normalizedEmail` | `VARCHAR(320)` | NOT NULL, UK | Lowercase com `Locale.ROOT`; não aplica regras específicas de provedor |
| `status` | `VARCHAR(32)` | NOT NULL | Enum persistido por valor estável |
| `activatedAt` | `TIMESTAMP(6)` | NULL | Preenchido uma única vez |
| `blockedAt` | `TIMESTAMP(6)` | NULL | Gerido por feature administrativa futura |
| `deactivatedAt` | `TIMESTAMP(6)` | NULL | Gerido por feature futura |
| `createdAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `updatedAt` | `TIMESTAMP(6)` | NOT NULL | UTC, controle otimista |
| `version` | `BIGINT` | NOT NULL | `@Version` JPA |

### Constraints and indexes

- `uk_identity_user_normalized_email` em `normalizedEmail`.
- Índice em `(status, createdAt)` para expiração de pendências.
- O banco reforça comprimento e nulidade; normalização e sintaxe completa pertencem ao service.

### State Transitions

```text
PENDING_VERIFICATION -> ACTIVE
PENDING_VERIFICATION -> CANCELLED -> removido/minimizado
PENDING_VERIFICATION -> expirado por prazo -> removido
ACTIVE -> BLOCKED
ACTIVE -> DEACTIVATED
BLOCKED -> ACTIVE
BLOCKED -> DEACTIVATED
DEACTIVATED -> ACTIVE (somente por fluxo futuro explicitamente autorizado)
```

Esta feature executa somente as transições originadas em `PENDING_VERIFICATION`. As demais existem no contrato da identidade, mas pertencem à administração/autenticação futura.

## Entity: Registration

**Tabela proposta**: `identity_registration`

Processo temporário 1:1 que conduz um usuário pendente até ativação, cancelamento ou expiração.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto increment | |
| `idUser` | `BIGINT` | NOT NULL, FK, UK | Um processo por identidade pendente |
| `method` | `VARCHAR(24)` | NOT NULL | `LOCAL` ou `GOOGLE` |
| `status` | `VARCHAR(32)` | NOT NULL | Estado do processo |
| `expiresAt` | `TIMESTAMP(6)` | NOT NULL | `createdAt + 15 dias`; não é prorrogado por reenvio |
| `completedAt` | `TIMESTAMP(6)` | NULL | Ativação concluída |
| `cancelledAt` | `TIMESTAMP(6)` | NULL | Cancelamento concluído |
| `createdAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `updatedAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `version` | `BIGINT` | NOT NULL | Controle otimista |

### Relationships

- `User` 1:0..1 `Registration` via `idUser`.
- Exclusão de usuário pendente remove seu processo.

### State Transitions

```text
PENDING_VERIFICATION -> ACTIVE
PENDING_VERIFICATION -> CANCELLED
PENDING_VERIFICATION -> EXPIRED
```

`ACTIVE`, `CANCELLED` e `EXPIRED` são estados terminais. A linha pode ser removida/minimizada conforme a política depois do registro do evento.

## Entity: LocalCredential

**Tabela proposta**: `identity_localCredential`

Credencial local separada da identidade. Esta feature cria ou invalida a senha inicial; autenticação e recuperação pertencem a `user-authentication`.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto increment | |
| `idUser` | `BIGINT` | NOT NULL, FK, UK | No máximo uma senha local vigente por usuário |
| `passwordHash` | `VARCHAR(255)` | NOT NULL | Formato do `DelegatingPasswordEncoder`, nunca retornado |
| `status` | `VARCHAR(24)` | NOT NULL | `ACTIVE` ou `INVALIDATED` |
| `invalidatedAt` | `TIMESTAMP(6)` | NULL | Preenchido antes de ativação Google que reutiliza pendência |
| `createdAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `updatedAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `version` | `BIGINT` | NOT NULL | Controle otimista |

### Security rules

- A senha em claro existe somente durante validação e hashing.
- O hash não aparece em DTO, VO, auditoria, mensagem de exceção ou log.
- No reaproveitamento por Google, a credencial pendente é invalidada e removida antes do commit da ativação.

## Entity: Verification

**Tabela proposta**: `identity_verification`

Comprovação de controle do e-mail no cadastro local.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto increment | |
| `idRegistration` | `BIGINT` | NOT NULL, FK | |
| `purpose` | `VARCHAR(32)` | NOT NULL | Inicialmente `REGISTRATION_EMAIL` ou `REGISTRATION_CANCEL` |
| `tokenHash` | `BINARY(32)` | NOT NULL, UK | SHA-256 do token aleatório |
| `status` | `VARCHAR(24)` | NOT NULL | `OPEN`, `USED`, `INVALIDATED`, `EXPIRED` |
| `issuedAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `expiresAt` | `TIMESTAMP(6)` | NOT NULL | 24 horas |
| `usedAt` | `TIMESTAMP(6)` | NULL | |
| `invalidatedAt` | `TIMESTAMP(6)` | NULL | |
| `createdAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `updatedAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `version` | `BIGINT` | NOT NULL | Controle otimista |

### Constraints and indexes

- UK em `tokenHash`.
- Índice em `(idRegistration, purpose, status, issuedAt)`.
- Índice em `(status, expiresAt)` para limpeza.

### State Transitions

```text
OPEN -> USED
OPEN -> INVALIDATED
OPEN -> EXPIRED
```

Somente `OPEN`, dentro da validade e pertencente a cadastro pendente, pode ser consumida. O consumo e a transição de cadastro ocorrem na mesma transação.

## Entity: ExternalIdentity

**Tabela proposta**: `identity_externalIdentity`

Vínculo estável com provedor externo.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto increment | |
| `idUser` | `BIGINT` | NOT NULL, FK | |
| `provider` | `VARCHAR(32)` | NOT NULL | Inicialmente `GOOGLE` |
| `issuer` | `VARCHAR(255)` | NOT NULL | Emissor validado |
| `subject` | `VARCHAR(255)` | NOT NULL | `sub` validado |
| `status` | `VARCHAR(24)` | NOT NULL | `PENDING` enquanto faltam aceites; `ACTIVE` depois da ativação |
| `verifiedAt` | `TIMESTAMP(6)` | NOT NULL | Instante da validação criptográfica |
| `activatedAt` | `TIMESTAMP(6)` | NULL | Preenchido junto da ativação do usuário |
| `createdAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `updatedAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `version` | `BIGINT` | NOT NULL | Controle otimista |

### Constraints and indexes

- `uk_identity_external_identity_issuer_subject` em `(issuer, subject)`.
- Índice em `idUser`.
- O e-mail do provedor não identifica nem integra a chave do vínculo.

### State Transitions

```text
PENDING -> ACTIVE
PENDING -> removido por cancelamento ou expiração
```

O e-mail verificado é o e-mail global do `User` pendente. ID token e claims completos não são persistidos.

## Entity: LegalDocumentVersion

**Tabela proposta**: `identity_legalDocumentVersion`

Versão imutável de um documento legal apresentado no cadastro.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto increment | |
| `documentType` | `VARCHAR(32)` | NOT NULL | Ex.: `TERMS_OF_USE`, `PRIVACY_POLICY`, `MARKETING` |
| `versionName` | `VARCHAR(64)` | NOT NULL | Nome legível e estável da versão |
| `required` | `BOOLEAN` | NOT NULL | Aceite obrigatório ou escolha opcional |
| `content` | `LONGTEXT` | NOT NULL | Conteúdo imutável apresentado |
| `contentHash` | `BINARY(32)` | NOT NULL | Integridade do conteúdo |
| `effectiveAt` | `TIMESTAMP(6)` | NOT NULL | Início de vigência |
| `retiredAt` | `TIMESTAMP(6)` | NULL | Fim de vigência |
| `createdAt` | `TIMESTAMP(6)` | NOT NULL | UTC |

### Constraints and indexes

- UK em `(documentType, versionName)`.
- Índice em `(documentType, effectiveAt, retiredAt)`.
- Uma versão já aceita nunca é alterada; correção cria nova versão.

## Entity: LegalConsent

**Tabela proposta**: `identity_legalConsent`

Evidência imutável da decisão sobre uma versão específica.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto increment | |
| `idUser` | `BIGINT` | NOT NULL, FK | |
| `idRegistration` | `BIGINT` | NULL, FK | Origem inicial do aceite |
| `idLegalDocumentVersion` | `BIGINT` | NOT NULL, FK | |
| `decision` | `VARCHAR(16)` | NOT NULL | `ACCEPTED` ou `DECLINED`; obrigatório só admite `ACCEPTED` |
| `decidedAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `createdAt` | `TIMESTAMP(6)` | NOT NULL | UTC |

### Constraints and indexes

- UK em `(idUser, idLegalDocumentVersion)`.
- O registro não é atualizado; mudança de versão cria nova decisão.

## Entity: RegistrationOriginWindow

**Tabela proposta**: `identity_registrationOriginWindow`

Contador persistido por origem e janela para decidir exigência do Turnstile e bloqueio temporário.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto increment | |
| `originDigest` | `BINARY(32)` | NOT NULL | HMAC-SHA-256 do IP normalizado |
| `policy` | `VARCHAR(32)` | NOT NULL | `TURNSTILE_THRESHOLD` ou `REGISTRATION_LIMIT` |
| `windowStartedAt` | `TIMESTAMP(6)` | NOT NULL | Início determinístico da janela |
| `windowEndsAt` | `TIMESTAMP(6)` | NOT NULL | |
| `attemptCount` | `INT` | NOT NULL | Incremento atômico |
| `createdAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `updatedAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `version` | `BIGINT` | NOT NULL | Controle otimista ou incremento atômico SQL |

### Constraints and indexes

- UK em `(originDigest, policy, windowStartedAt)`.
- Índice em `windowEndsAt` para limpeza.
- O endereço IP em claro não é persistido.

## Entity: IdentityEvent

**Tabela proposta**: `identity_event`

Registro append-only dos eventos exigidos para identidade e cadastro, sem credenciais ou evidências secretas.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto increment | |
| `idUser` | `BIGINT` | NULL, FK | `ON DELETE SET NULL` para permitir minimização |
| `idRegistration` | `BIGINT` | NULL, FK | `ON DELETE SET NULL` |
| `correlationId` | `BINARY(16)` | NOT NULL | Correlação técnica não exposta ao usuário |
| `eventType` | `VARCHAR(48)` | NOT NULL | Início, reenvio, confirmação, ativação, expiração, cancelamento ou mudança de estado |
| `previousStatus` | `VARCHAR(32)` | NULL | Obrigatório em mudança de estado |
| `newStatus` | `VARCHAR(32)` | NULL | Obrigatório em mudança de estado |
| `originType` | `VARCHAR(32)` | NOT NULL | `SELF_SERVICE`, `SCHEDULED_JOB`, `SYSTEM` |
| `reason` | `VARCHAR(255)` | NULL | Código/motivo seguro, sem conteúdo sensível |
| `occurredAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `createdAt` | `TIMESTAMP(6)` | NOT NULL | UTC |

### Audit rules

- Append-only na aplicação.
- Não contém e-mail, senha, token, hash de senha, token Google, token Turnstile ou corpo de resposta externa.
- O tombstone de cancelamento é este evento já sem FKs e sem PII, retido por 15 dias.

## Cross-Entity Invariants

1. `User.ACTIVE` exige ao menos uma `LocalCredential.ACTIVE` ou uma `ExternalIdentity.ACTIVE`.
2. `Registration.ACTIVE` exige `User.ACTIVE`, todos os documentos obrigatórios vigentes aceitos e nenhuma verificação aberta.
3. Cadastro `LOCAL` exige credencial local ativa enquanto pendente; cadastro `GOOGLE` não exige senha.
4. Um `issuer + subject` pertence a no máximo um usuário.
5. Um e-mail normalizado pertence a no máximo um usuário vigente.
6. Cadastro expirado ou cancelado não pode consumir comprovação.
7. Nova comprovação invalida todas as comprovações abertas de mesmo propósito no cadastro.
8. Ativação Google que reutiliza pendência invalida e remove credencial local e comprovações antes de criar o vínculo.
9. O job de limpeza nunca remove `User.ACTIVE`, mesmo que reste um `Registration` inconsistente; essa condição é erro operacional auditável.

## Referential Actions

| Child relationship | `ON DELETE` | Rationale |
|--------------------|-------------|-----------|
| `Registration -> User` | `CASCADE` | O processo temporário não existe sem a identidade pendente. |
| `LocalCredential -> User` | `CASCADE` | A credencial não tem finalidade sem a identidade; deleção de usuário exige fluxo autorizado. |
| `Verification -> Registration` | `CASCADE` | Comprovação não existe sem o processo. |
| `ExternalIdentity -> User` | `CASCADE` | O vínculo externo não é uma identidade Rinos independente. |
| `LegalConsent -> User` | `CASCADE` | Para pendência expirada/cancelada não há obrigação definida de conservar o aceite; deleção de ativo deverá passar pela governança futura. |
| `LegalConsent -> Registration` | `SET NULL` | A evidência de usuário ativo sobrevive à remoção do processo temporário. |
| `LegalConsent -> LegalDocumentVersion` | `RESTRICT` | Uma versão aceita não pode ser excluída enquanto houver evidência. |
| `IdentityEvent -> User` | `SET NULL` | Permite minimização sem apagar o evento permitido. |
| `IdentityEvent -> Registration` | `SET NULL` | Permite remover o processo temporário sem apagar o evento permitido. |

## Retention and Cleanup

| Data | Retention |
|------|-----------|
| Usuário/registro pendente, credencial, comprovações e consentimentos não ativados | Até completar 15 dias desde a criação; exclusão diária idempotente |
| Comprovação usada, invalidada ou expirada de cadastro ainda pendente | Até o cadastro terminar ou expirar |
| `RegistrationOriginWindow` | Até o fim da janela mais margem operacional mínima para execução da limpeza |
| Tombstone de cancelamento sem PII | 15 dias |
| Usuário ativo, vínculo externo e consentimentos aplicáveis | Enquanto a identidade estiver vigente ou conforme governança/obrigação futura |
