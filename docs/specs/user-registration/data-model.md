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
- O valor segue o formato do `DelegatingPasswordEncoder` e conserva identificador, parâmetros e salt necessários para validar hashes produzidos por configurações anteriores.
- No reaproveitamento por Google, a credencial pendente é invalidada e removida antes do commit da ativação.

## Entity: Verification

**Tabela proposta**: `identity_verification`

Comprovação de controle do e-mail no cadastro local.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto increment | |
| `idRegistration` | `BIGINT` | NOT NULL, FK | |
| `purpose` | `VARCHAR(32)` | NOT NULL | `REGISTRATION_EMAIL`, `REGISTRATION_CANCEL` ou `EXTERNAL_REGISTRATION` |
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

`EXTERNAL_REGISTRATION` representa a continuação opaca emitida depois que o RFW validou a identidade Google. O
token bruto existe somente na resposta que abre a tela de aceites; o banco conserva apenas seu SHA-256. Nova
resolução válida da mesma pendência invalida a continuação anterior, sem prorrogar os 15 dias do cadastro.

Quando uma prova `REGISTRATION_EMAIL` é válida, mas surgiram novas versões legais obrigatórias, a própria prova
permanece `OPEN` e vira a referência da continuação. Ela só passa a `USED` na transação que revalida os documentos,
registra os aceites e ativa a identidade. Assim, repetir a primeira chamada devolve a mesma referência sem persistir
segredo recuperável nem emitir uma segunda capacidade.

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

O vínculo `PENDING` pode ser criado na resolução da identidade, antes dos aceites. Quando uma pendência local é
reutilizada, sua senha e suas comprovações locais permanecem intactas nessa etapa e só são invalidadas dentro da
transação que valida a continuação, registra os novos aceites, ativa o vínculo e ativa o usuário.

Enquanto o usuário estiver pendente, existe no máximo uma identidade externa candidata em `PENDING`. Se outra
identidade Google validada reutilizar o mesmo e-mail pendente, o sistema bloqueia o usuário e seus vínculos, remove a
candidata anterior e emite uma nova continuação para a candidata atual. A emissão invalida a prova externa anterior.
Essa regra vincula inequivocamente a referência opaca ao único vínculo que poderá ser ativado, sem persistir o token
Google ou acrescentar a identidade externa à URL.

Na conclusão, a ordem transacional é: bloquear cadastro e prova; bloquear a candidata externa; revalidar e registrar
documentos vigentes; consumir a prova; invalidar e remover eventual credencial local; invalidar as demais provas;
ativar vínculo, usuário e cadastro; e registrar a auditoria sanitizada. O principal de sessão só é construído pela
camada RFW depois que essa transação retorna com o commit concluído. Replay da prova usada não autentica novamente.

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
- A apresentação recebe referências e conteúdo exclusivamente por `LegalDocumentFacade`: a
  fotografia do cadastro exige os dois documentos-base vigentes, versões futuras não são
  publicadas e versões históricas já vigentes continuam legíveis pela referência vinculada ao
  aceite.
- Antes de entregar o conteúdo Markdown à rota pública, a facade verifica seu SHA-256 e falha
  fechado em caso de divergência. A renderização usa o componente sanitizado do RFW.

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
- O início do cadastro registra a versão publicada efetivamente apresentada, mesmo que ela tenha sido retirada antes da
  submissão. Esse registro permanece histórico e não satisfaz a ativação quando existir outra versão obrigatória
  vigente.

## Entity: OriginWindow

**Tabela proposta**: `security_originWindow`

Contador global persistido por origem, operação, política e janela. A estrutura atende inicialmente ao cadastro e poderá
ser reutilizada por autenticação, recuperação e outras operações de segurança sem transformar o IP em identidade.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `BIGINT` | PK, auto increment | |
| `originAddress` | `VARBINARY(16)` | NOT NULL | IPv4 ou IPv6 normalizado, sem representação textual ambígua |
| `operation` | `VARCHAR(48)` | NOT NULL | Inicialmente `USER_REGISTRATION`; extensível por contrato |
| `policy` | `VARCHAR(32)` | NOT NULL | `TURNSTILE_THRESHOLD` ou `ABSOLUTE_LIMIT` |
| `activeMarker` | `BOOLEAN` | NULL, UK parcial por convenção | `TRUE` somente na janela corrente; `NULL` no histórico |
| `windowStartedAt` | `TIMESTAMP(6)` | NOT NULL | Início determinístico da janela |
| `windowEndsAt` | `TIMESTAMP(6)` | NOT NULL | |
| `eventCount` | `INT` | NOT NULL | Eventos contabilizados conforme operação e política |
| `blockedUntil` | `TIMESTAMP(6)` | NULL | Liberação explícita quando a política bloquear |
| `createdAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `updatedAt` | `TIMESTAMP(6)` | NOT NULL | UTC |
| `version` | `BIGINT` | NOT NULL | Controle otimista ou incremento atômico SQL |

### Constraints and indexes

- UK em `(originAddress, operation, policy, windowStartedAt)`.
- UK em `(originAddress, operation, policy, activeMarker)`; a linha corrente usa `TRUE` e históricos usam `NULL`, impedindo duas janelas ativas mesmo quando instâncias concorrentes calculam instantes diferentes.
- Índice em `windowEndsAt` para limpeza.
- O contador canônico do cadastro usa a política `ABSOLUTE_LIMIT`; o limiar de Turnstile é calculado sobre esse mesmo valor para não manter contagens paralelas divergentes.
- Para `USER_REGISTRATION + ABSOLUTE_LIMIT`, a janela padrão começa na primeira nova pendência contabilizada e termina 24 horas depois.
- O contador é incrementado atomicamente, na mesma transação que cria uma nova `Registration`; repetição idempotente que reutiliza a pendência vencedora não o incrementa.
- Submissões rejeitadas antes da persistência, retomadas, reenvios e cancelamentos não alteram o contador.
- O IP não é copiado para `IdentityEvent`, logs comuns ou auditorias permanentes.
- A linha é excluída automaticamente até 30 dias depois de `windowEndsAt`.

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

- Append-only enquanto a identidade permanece vigente. A única exceção é a minimização terminal autorizada de uma
  pendência cancelada ou expirada: seus eventos relacionados são removidos e substituídos por um único tombstone sem
  FKs e sem PII.
- Não contém e-mail, senha, token, hash de senha, token Google, token Turnstile ou corpo de resposta externa.
- Resultados rejeitados do início local usam `REGISTRATION_REJECTED` e somente o código fechado do resultado público;
  quando não existe identidade, ambas as FKs permanecem nulas.
- Eventos `VERIFICATION_REISSUED` são também a fonte transacional da janela móvel de reenvio;
  `REGISTRATION_STARTED` não consome essa franquia.
- `REGISTRATION_CANCELLATION_REQUESTED` registra somente a emissão efetiva da prova, com correlation ID e códigos
  fechados. Esses eventos são também a fonte transacional da janela móvel própria, com padrão de três emissões em 15
  minutos; solicitações neutras para identificador ausente, inelegível ou já limitado não criam evento nem despacho.
- Os tombstones `REGISTRATION_CANCELLED` e `REGISTRATION_EXPIRED` são eventos já sem FKs e sem PII, retidos por 15
  dias.

## Cross-Entity Invariants

1. `User.ACTIVE` exige ao menos uma `LocalCredential.ACTIVE` ou uma `ExternalIdentity.ACTIVE`.
2. `Registration.ACTIVE` exige `User.ACTIVE`, todos os documentos obrigatórios vigentes aceitos e nenhuma verificação aberta; aceites históricos do início do cadastro não substituem versões obrigatórias posteriores.
3. Cadastro `LOCAL` exige credencial local ativa enquanto pendente; cadastro `GOOGLE` não exige senha.
4. Um `issuer + subject` pertence a no máximo um usuário.
5. Um e-mail normalizado pertence a no máximo um usuário vigente.
6. Cadastro expirado ou cancelado não pode consumir comprovação.
7. Nova comprovação invalida todas as comprovações abertas de mesmo propósito no cadastro.
8. Ativação Google que reutiliza pendência invalida e remove credencial local e comprovações antes de criar o vínculo.
9. O job de limpeza nunca remove `User.ACTIVE`, mesmo que reste um `Registration` inconsistente; essa condição é erro operacional auditável.
10. Cancelamento exige `REGISTRATION_CANCEL` válida para o mesmo e-mail, invalida as demais provas e remove a raiz
    `User` pendente na mesma transação; os `CASCADE` removem credencial, cadastro, consentimentos e vínculos externos,
    liberando a constraint do e-mail somente no commit.

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
| `OriginWindow` | Até 30 dias depois do fim da janela; exclusão física automática ao menos diária, em lotes próprios |
| Tombstone de cancelamento ou expiração sem PII | 15 dias |
| Usuário ativo, vínculo externo e consentimentos aplicáveis | Enquanto a identidade estiver vigente ou conforme governança/obrigação futura |

O job diário depende do lease global `platform_maintenanceLease` definido em `platform-operations`; essa tabela não
pertence ao domínio de identidade. O catálogo do job inclui separadamente a expiração de cadastros pendentes, a
exclusão de `OriginWindow` vencidas e a retenção dos tombstones terminais. A sessão coordenadora comprova lease e
`epoch` antes de cada lote, e cada
transação possui timeout padrão de cinco minutos, obrigatoriamente inferior aos 10 minutos de estabilização. A
transação relê estado e expiração antes de excluir; timeout ou falha de commit não registra progresso e permite
repetição idempotente pela coordenadora vigente.

O heartbeat tenta adquirir `global-maintenance` no primeiro disparo e renova no intervalo configurado. O catálogo
aguarda inicialmente um intervalo de heartbeat, executa no máximo a cada `rinos.cleanup.interval` e contém falha de uma
tarefa sem impedir as demais. O agendamento somente é habilitado quando `spring.datasource.url` foi explicitamente
declarada; sem banco global não existe manutenção interna a executar, e os diagnósticos de migration permanecem a
autoridade de startup.

O cancelamento confirmado não aguarda esse job. A transação bloqueia cadastro e prova, revalida estado, expiração e
e-mail, consome a prova uma única vez, invalida as demais, aplica as transições terminais e exclui a raiz pendente.
Depois da deleção física, cria apenas o tombstone sanitizado. Repetição ou disputa concorrente encontra prova ausente
ou inválida, não restaura dados e não repete o efeito; um novo cadastro pode usar o e-mail após o commit vencedor.
