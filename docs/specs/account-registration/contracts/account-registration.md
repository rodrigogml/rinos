# Contrato Público — Cadastro de Contas

## Tipos

### `AccountCreationRequest`

- `idempotencyKey`: UUID obrigatório gerado uma vez pela UI para a intenção;
- `displayName`: 1..160 após trim/normalização de espaços;
- `baseCurrency`: código ISO 4217 suportado;
- `timeZoneId`: identificador IANA;
- `humanVerificationToken`: valor transitório e redigido, somente quando exigido;
- `confirmed`: confirmação explícita dos três dados.

Identidade, garantia, origem e correlação são derivados por adapter confiável, não por campos livres da UI.
Antes de consumir prova humana ou cota, a fachada verifica a intenção existente do mesmo usuário: replay idêntico devolve o
protocolo anterior e payload divergente devolve conflito. Isso preserva a repetição segura depois de perda de conexão sem
reutilizar um token Turnstile de uso único.

### `AccountCreationResult`

- `status`: `ACCEPTED`, `REPLAYED`, `CONFLICT`, `REJECTED`, `UNAVAILABLE`;
- `protocolId`: somente para aceite/replay;
- `accountPublicId`: somente para aceite/replay;
- `publicStage`;
- `safeReasonCode` e `retryAfter`, quando aplicáveis.

### `AccountCreationStatus`

- protocolo, conta pública, nome, estágio público, instante de aceite/atualização;
- próxima ação segura;
- nunca expõe schema, host, fila, tentativa, stack trace, membership, grupo, regra ou plano interno.

### `AccountSummary`

UUID público, nome, moeda, fuso e estado público. IDs internos não saem da facade.

## Facades

```java
interface AccountCreationFacade {
  AccountCreationResult request(AccountCreationRequest request);
  AccountCreationStatus status(UUID protocolId);
}

interface AccountMaintenanceFacade {
  AccountSummary get(UUID accountId, AuthorizationContext context);
  AccountSummary update(AccountUpdateRequest request, AuthorizationContext context);
  AccountLifecycleResult changeState(AccountLifecycleRequest request,
      AuthorizationContext context);
}
```

`AccountMaintenanceFacade` sempre exige `AuthorizationFacade` e as chaves canônicas do contexto tenant.

## Portas consumidas

```java
interface TenantProvisioningRequestPort {
  AccountBootstrapResult requestProvisioning(AccountBootstrapRequest request);
}

interface FoundingMembershipBootstrapPort {
  FoundingMembershipOutcome ensure(FoundingMembershipRequest request);
}

interface TenantAccessBootstrapPort {
  TenantAccessBootstrapOutcome ensure(TenantAccessBootstrapRequest request);
}

interface TenantContractBootstrapPort {
  ContractBootstrapResult ensure(TenantContractBootstrapRequest request);
}
```

`DefaultPlanAssignmentPort` permanece como adapter transitório para `TenantContractBootstrapPort`. O resultado somente
é concluído depois de criar/confirmar contrato `TENANT`, atribuição `TENANT/FREE` e ocupação do fundador.

Todas as portas de bootstrap recebem o mesmo `AccountBootstrapRequest` — protocolo, UUIDs públicos de conta e tenant,
fundador e correlação — e devolvem `AccountBootstrapResult`. Os estados `ACCEPTED`, `ALREADY_COMPLETED`, `REJECTED` e
`UNAVAILABLE` carregam referência opaca e preservam a idempotência da saga. Cada módulo traduz internamente o estado
genérico para o seu checkpoint, sem expor tipos de transporte incompatíveis entre account e storage.

## Porta publicada

```java
interface AccountOperationalStatePort {
  AccountOperationalSnapshot inspect(long tenantId);
}
```

Snapshot minimizado: `sourceAvailable`, `exists`, `accountId`, `tenantId`, estado da conta e estado operacional do
tenant. Essa porta não informa permissões, plano, participantes ou dados cadastrais.

## Motivos seguros iniciais

`ACCOUNT_INPUT_INVALID`, `ACCOUNT_CONFIRMATION_REQUIRED`, `ACCOUNT_IDEMPOTENCY_CONFLICT`,
`ACCOUNT_IDENTITY_INACTIVE`, `ACCOUNT_RECENT_AUTH_REQUIRED`, `ACCOUNT_HUMAN_VERIFICATION_REQUIRED`,
`ACCOUNT_HUMAN_VERIFICATION_REJECTED`, `ACCOUNT_RATE_LIMITED`, `ACCOUNT_CREATION_UNAVAILABLE`.

## Garantias

- `toString()` de requests/outcomes censura identidade, token e origem.
- Repetição nunca cria outra conta para a mesma intenção.
- Exceção interna não atravessa o contrato público.
- `UNAVAILABLE` e ausência de plano/permissão permanecem motivos diferentes.
- A criação exige a garantia recente da operação de reautenticação catalogada `create-account`; a interface futura abre o
  protocolo RFW correspondente quando a sessão já não a satisfaz.
- A janela de origem e o bloqueio temporário são globais e técnicos. A origem somente é persistida no contador temporário,
  nunca no registro da conta ou em sua auditoria.
