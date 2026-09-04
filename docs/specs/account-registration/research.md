# Pesquisa Técnica — Cadastro de Contas

**Feature**: `account-registration`
**Data**: 2026-08-15
**Baseline**: Java 25, Spring Boot 4, MySQL 9, Vaadin e RFW 2.0

## Decisões

### 1. Conta e tenant nascem no banco global

A identidade funcional da conta, a identidade imutável do tenant, a intenção idempotente e a fila durável pertencem
ao banco global. Nenhum schema de tenant é criado dentro da transação de aceite. O provisionamento físico continua
pertencendo a `tenant-storage-provisioning`.

### 2. Aceite usa transação local e outbox

Uma transação cria `account_tenant`, `account_account`, `account_creationIntent`, auditoria e evento de outbox. O
dispatcher somente publica depois do commit. Assim, queda entre banco e fila não perde a solicitação e não exige
transação distribuída.

### 3. Idempotência pertence ao criador e ao payload

A chave opaca fornecida pelo cliente é única por identidade criadora. O registro conserva também hash canônico do
payload. Repetição com a mesma chave e mesmo hash retorna o resultado anterior; mesma chave com payload diferente
resulta em conflito seguro.

### 4. Identificadores internos não são identificadores de interface

`idAccount` e `idTenant` são `BIGINT` internos. Conta, tenant, protocolo e intenção possuem UUIDs públicos distintos,
não sequenciais e imutáveis. Nome nunca participa da identidade ou unicidade.

### 5. Ativação é uma saga coordenada, não parte do primeiro slice

A conta permanece `CREATING` até confirmações independentes de armazenamento, founding membership, bootstrap ACL e
plano padrão. Cada confirmação é idempotente. Ausência de qualquer adapter mantém a conta não operacional.

### 6. Portas entre módulos

- `TenantProvisioningRequestPort`: recebe conta, tenant, protocolo e finalidade sistêmica.
- `FoundingMembershipBootstrapPort`: cria/consulta a associação fundadora.
- `TenantAccessBootstrapPort`: cria grupo protegido e baseline explícita.
- `DefaultPlanAssignmentPort`: atribui exatamente um plano padrão vigente.
- `AccountOperationalStatePort`: expõe estado minimizado para membership, ACL e isolamento.

Defaults inexistentes nunca simulam sucesso. A outbox pode permanecer pendente até o consumidor existir.

### 7. Autorização

Aceitar uma nova conta exige identidade ativa, autenticação recente e política antiabuso, mas não usa uma chave de
tenant ainda inexistente. Consulta e manutenção posteriores usam as chaves canônicas `tenant.account.view`,
`tenant.account.update` e `tenant.account.lifecycle.manage` pelo `AuthorizationFacade`.

### 8. Turnstile e origem

O Rinos reutiliza `RFWTurnstileComponent`, `RFWHumanVerificationProvider` e os VOs públicos do RFW. O token é transitório,
consumido uma vez e nunca persistido ou registrado. A origem passa pela política de proxies confiáveis já usada na
identidade.

### 9. Concorrência

Uniques de banco protegem UUIDs e idempotência; `@Version` protege alterações cadastrais; processamento da outbox usa
claim com lease/estado e compare-and-set. Somente uma transição válida pode ativar, suspender ou cancelar a conta.

### 10. Cache e sessão

Conta ou tenant ativo não é armazenado em `HttpSession`, principal ou `VaadinSession`. O primeiro slice não requer cache:
estado operacional é lido por identificador e otimizações futuras devem usar revisão/invalidação sem ampliar acesso.

## Pesquisa RFW

Foram consultados `docs/architecture/rfw-platform-usage.md`, README e AGENTS do submódulo, showroom e API pública.

| Necessidade | Recurso RFW | Decisão |
|---|---|---|
| Prova antiabuso | `RFWTurnstileComponent`, `RFWHumanVerificationProvider` | reutilizar após fechar a operação catalogada |
| Reautenticação | `RFWReauthenticationChallengeProvider` e outcomes tipados | reutilizar |
| Ações | `UIFactory`, `RFWButtonDefinitions`, variantes e ícones | reutilizar |
| Feedback persistente | `RFWBannerComponent` | reutilizar |
| Feedback breve | `RFWToastService` | reutilizar |
| Agrupamento de controles | `RFWControlGroup` | reutilizar quando aplicável |
| Formulário e layout | componentes Vaadin compostos com tokens/classes RFW | composição local de domínio |

Foi encontrada uma lacuna semântica: `RFWHumanVerificationOperationEnum` não representa criação de conta, embora o
componente aceite a action `account-creation`. A proposta aditiva, compatibilidade e impacto estão registrados em
[`rfw-gap-analysis.md`](rfw-gap-analysis.md). A lacuna foi resolvida na revisão RFW `ba1bfda`; o adapter Rinos usa agora
a action catalogada e continua falhando de modo seguro diante de indisponibilidade.

## Alternativas descartadas

- Criar schema do tenant sincronicamente: aumenta latência e produz falha parcial distribuída.
- Usar nome como slug/identidade: impede nomes repetidos e cria acoplamento mutável.
- Publicar diretamente na fila antes/depois do commit sem outbox: permite evento fantasma ou solicitação perdida.
- Ativar com adapters ausentes: ampliaria acesso e violaria negação por padrão.
- Guardar token Turnstile ou tenant selecionado na sessão: amplia exposição e conflita entre abas.
