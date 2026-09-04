# Contrato de Autorização

**Feature**: `access-control`
**Tipo**: contrato Java interno entre módulos; não é API REST pública.

## `AccessKeyDescriptor`

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `code` | `String` | sim | identidade interna estável; não exposta na UI |
| `scope` | `AccessScope` | sim | `GLOBAL` ou `TENANT` |
| `categoryCode` | `String` | sim | navegação, sem efeito autorizativo |
| `ownerModule` | `String` | sim | contributor responsável |
| `nameI18nKey` / `descriptionI18nKey` | `String` | sim | textos localizados |
| `status` | `AccessKeyStatus` | sim | `ACTIVE` ou `INACTIVE` |
| `entitlementRequirement` | `EntitlementRequirement` | não | `subjectScope` (`PERSONAL`/`TENANT`) e código; gate independente |

`AccessScope.GLOBAL` não implica `ContractScope.PERSONAL`. Chave administrativa global normalmente não declara
requisito; capacidade pessoal global declara `PERSONAL`, e capacidade tenant condicionada declara `TENANT`.

## `AccessRuleEffect`

| Valor | Significado |
|-------|-------------|
| `PERMITIR` | contribui para a chave quando vigente |
| `BLOQUEAR` | impede a chave quando vigente, mesmo diante de permissões |

## `AuthorizationContext`

| Campo | Tipo | Regra |
|-------|------|-------|
| `scope` | `AccessScope` | obrigatório |
| `tenantId` | `Long` | obrigatório somente em `TENANT`; ausente em `GLOBAL` |
| `contextRevision` | `Long` | observacional, preenchido na decisão |

## `AuthorizationRequest`

| Campo | Tipo | Regra |
|-------|------|-------|
| `actor` | `AuthorizationActor` | identidade humana ou origem sistêmica tipada |
| `membershipId` | `Long` | obrigatório para ator humano em tenant |
| `context` | `AuthorizationContext` | obrigatório |
| `operationCode` | `String` | operação auditável do módulo |
| `requiredKeys` | `Set<AccessKeyDescriptor>` | não vazio; todas são obrigatórias |
| `assurance` | `AuthenticationAssurance` | autenticação recente e fatores comprovados |
| `sensitive` | `boolean` | controla auditoria de negação |
| `explanationMode` | enum | `NONE`, `SAFE`, `ADMINISTRATIVE` |

`AuthorizationRequest` não recebe grupos, regras ou resultado de plano fornecidos pelo chamador. Esses valores são
resolvidos pelo serviço para evitar falsificação ou cache obsoleto.

Em entradas humanas, `actor` e `assurance` são derivados da autenticação corrente pelo adapter, nunca de campos
livremente enviados pela UI. `context` e `membershipId` expressam o destino pretendido, não prova de acesso: o serviço
confirma identidade, associação, tenant e conta. As chaves vêm do contrato tipado da operação consumidora e são
avaliadas em lote; a interface não escolhe nem envia códigos técnicos arbitrários.

Entradas humanas não constroem esse request diretamente. O módulo consumidor publica um `AuthorizationOperation`
imutável com código, descriptors e sensibilidade; o `SpringAuthorizationAdapter` deriva o ator do
`SecurityContext`, revalida a referência opaca da sessão pelo `AuthorizationAuthenticationFacade` e combina esses
dados com um `AuthorizationWorkspaceContext` explícito. Este último contém somente contexto e associação pretendidos,
sem revisão, regra, chave ou decisão.

Os primeiros catálogos consumidores concretos são `TenantFoundationOperations` e `TenantPartyOperations`. Fundação
publica operações de conta, membership, plano e auditoria; pessoas publica cadastro, relacionamentos e dados de
pagamento. Revelar identificador de pessoa exige cumulativamente consulta e revelação, e revelar dado de pagamento
exige cumulativamente consulta mascarada e revelação completa. Nenhum catálogo recebe código de chave em runtime.

Para ator sistêmico, `actor` contém um identificador de origem registrado e não possui `membershipId` nem garantia
humana. O registro imutável da origem fixa `operationCode`, escopos e `requiredKeys` autorizáveis. Correspondência
exata e origem ativa produzem uma `SYSTEM_SOURCE` permissiva por chave; divergência, origem desconhecida ou tentativa
de combinar grupo/regra humana nega. Isso não cria usuário técnico nem concede participação em tenant.

Cada módulo publica essas capacidades por `SystemOperationContributor`, usando `SystemOperationDescriptor`. O registry
rejeita duplicidade de origem, operação e escopo durante a inicialização. A execução autorizada registra a origem e a
finalidade em `access_auditEvent`. O próprio sincronizador do catálogo usa a origem registrada
`ACCESS_CATALOG_READINESS`, em vez de bypass ou identidade simulada.

## `AuthorizationDecision`

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `allowed` | `boolean` | resultado geral |
| `context` | `AuthorizationContext` | contexto avaliado |
| `keyResults` | `List<AuthorizationKeyResult>` | resultado individual por chave |
| `structuralGates` | `List<AuthorizationGateResult>` | identidade, contexto, associação e conta |
| `entitlementGates` | `List<AuthorizationGateResult>` | plano e capability |
| `assuranceGates` | `List<AuthorizationGateResult>` | reautenticação, TOTP ou passkey |
| `safeReasonCodes` | `Set<String>` | motivos seguros para público autorizado |
| `revision` | `long` | revisão usada na decisão |
| `decidedAt` | `Instant` | instante UTC autoritativo usado para vigência |
| `correlationId` | `String` | rastreio |

`AuthorizationKeyResult` contém `key`, `allowed`, `permitSources`, `blockingSources`, `missingPermit` e
`ignoredSources` (expiradas, futuras ou inativas). Bloqueios só aparecem quando o modo e o consulente permitirem.

## `AccessExplanation`

A consulta usa `AccessExplanationRequest`, que separa obrigatoriamente:

- `requester`, `requesterMembershipId` e `requesterAssurance`, derivados da autenticação corrente do consulente; e
- `targetRequest`, que descreve o sujeito e a operação cuja decisão será explicada.

A facade ignora qualquer chave de visibilidade fornecida pelo chamador e constrói internamente uma decisão sensível
para `global.access.explain` ou `tenant.access.explain`, conforme o contexto do alvo. A associação do consulente é
revalidada nesse mesmo tenant. O alvo somente é resolvido depois que essa decisão permite a consulta e quando seu modo
é `ADMINISTRATIVE`; falha de contexto, associação, permissão, plano ou garantia retorna
`ACL_EXPLANATION_FORBIDDEN` sem confirmar a existência do alvo.

`AccessExplanation` é, portanto, retornado somente a quem possui a chave administrativa de explicação no mesmo
contexto. Inclui:

- condições estruturais e gates avaliados;
- permissões e bloqueios com suas origens autorizadas;
- vigência e estado de cada origem;
- resultado individual por chave;
- condição decisiva; e
- correlação com auditoria, quando permitida.

Nunca inclui informação de tenant diferente, segredo de fator, regras de origem invisível ou detalhes que permitam
enumerar usuários.

## Semântica obrigatória

```text
allowed(request) = structural gates
                   AND entitlement gates
                   AND assurance gates
                   AND for every required key:
                       hasCurrentPermit(key)
                       AND NOT hasCurrentBlock(key)
```

`hasCurrentPermit` e `hasCurrentBlock` consideram apenas o contexto, o sujeito, grupos ativos, chave ativa e vigência
do instante UTC de decisão. Falha interna resulta em decisão negada com motivo técnico seguro, nunca em fallback
permissivo.

Login e seleção de tenant não produzem uma decisão durável. Nenhuma implementação do contrato pode armazenar chaves,
regras ou decisões efetivas no principal, `HttpSession`, `VaadinSession`, `RFWSessionState` ou estado compartilhado
entre áreas de trabalho. A UI exata pode manter apenas a referência mínima do contexto selecionado; toda operação cria
nova requisição e revalida essa referência.

Cache é detalhe interno do serviço. Uma entrada somente pode reutilizar fontes do mesmo sujeito e contexto quando a
revisão persistida coincidir e o instante atual for anterior à sua próxima fronteira temporal. Plano, estado e garantia
continuam gates correntes. Notificação de mudança pode remover entradas antecipadamente, mas nunca autoriza o uso de
snapshot sem confirmação da revisão.

## Entradas Vaadin e Spring

`WorkspaceAuthorizationContextAdapter` mantém `AuthorizationWorkspaceContext` por instância de `UI` usando dados do
próprio componente Vaadin. Ele não grava tenant no `HttpSession`, `VaadinSession`, `RFWSessionState`, principal ou
`RFWExecutionContext`. Assim, duas UIs da mesma sessão podem apontar para tenants distintos.

O contexto de execução público da RFW continua sendo usado como fotografia transversal conforme seu contrato, mas não
é fonte de autenticação, tenant ou autorização. O Spring Security é a fonte do principal; a sessão global persistida
é consultada novamente para produzir `AuthenticationAssurance`. Sessão inválida, expirada, revogada, de outra
identidade ou sem métodos comprovados falha com `ACL_INVALID_AUTHENTICATION` antes da autorização.

## Trabalhos originados por usuário

`UserInitiatedAuthorizationJob` conserva somente ator humano, contexto/associação pretendidos, operação tipada e os
instantes da garantia comprovada na submissão. Não conserva decisão, revisão nem fontes ACL.

`UserInitiatedJobAuthorizationService.execute(...)` cria uma nova `AuthorizationRequest`, chama
`AuthorizationFacade.require(...)` e somente então executa o primeiro efeito fornecido. Revogação, bloqueio, mudança
de plano, identidade, associação ou expiração temporal ocorrida após a submissão impede o início; depois do primeiro
efeito, a política do módulo originador decide se a unidade já iniciada pode terminar, sem reutilizar a autorização
para repetição, retomada ou novo lote.

Para ator sistêmico, `hasCurrentPermit` significa correspondência exata com a `SYSTEM_SOURCE` registrada e ativa;
`hasCurrentBlock` não consulta regras humanas. Todos os demais gates estruturais aplicáveis ao contexto, catálogo,
estado operacional e direito de plano continuam obrigatórios. A decisão identifica a origem sistêmica e é sempre
auditável.

## Erros de uso do contrato

| Código | Situação |
|--------|----------|
| `ACL_INVALID_CONTEXT` | escopo e tenant incompatíveis |
| `ACL_EMPTY_REQUIRED_KEYS` | operação protegida sem chave declarada |
| `ACL_UNKNOWN_KEY` | descriptor não registrado ou incompatível |
| `ACL_INVALID_ACTOR` | ator ou associação incompatível com contexto |
| `ACL_SYSTEM_SOURCE_MISMATCH` | origem sistêmica desconhecida, inativa ou incompatível com operação, contexto ou chaves |
| `ACL_EXPLANATION_FORBIDDEN` | chamador sem direito de explicação |
| `ACL_DECISION_UNAVAILABLE` | revisão, catálogo ou regras indisponíveis; resultado negado |
