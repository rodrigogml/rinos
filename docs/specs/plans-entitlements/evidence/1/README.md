# Evidência — Fase 1: contratos públicos

## Entrega

- contratos imutáveis em `api.module.plans`, sem dependência de entities, repositories ou backend;
- sujeitos fechados `PersonalEntitlementSubject` e `TenantEntitlementSubject`;
- `EntitlementRequirement(subjectScope, code)` integrado ao `AccessKeyDescriptor`;
- método legado `entitlementCode()` limitado à compatibilidade transitória;
- avaliação de acesso resolve contrato pessoal somente quando `PERSONAL` é explícito;
- administração global sem requisito não consulta plano pessoal;
- portas de bootstrap `PERSONAL/FREE` e `TENANT/FREE`;
- facade única de reserva, ocupação, conversão e liberação pré-aceite da capacidade tenant;
- catálogo rejeita entitlement tipado antes da migration de escopo, evitando persistência incompleta.

## Invariantes testadas

- requisito e sujeito devem possuir o mesmo escopo;
- chave global somente admite requisito pessoal e chave tenant somente admite requisito tenant;
- decisão composta exige resultados únicos e todos disponíveis;
- limite e uso são informados juntos e nunca negativos;
- requests protegem identificadores e fingerprint no `toString()`;
- bootstrap não combina referência de sucesso com motivo de falha;
- reserva exige expiração posterior ao instante da solicitação;
- contexto global administrativo não infere `PERSONAL`.

## Validação

Executado em 2026-08-16:

```text
mvn clean -DskipITs test
Tests run: 719, Failures: 0, Errors: 0, Skipped: 0

mvn verify
Unit tests: 719, Failures: 0, Errors: 0, Skipped: 0
Integration tests: 133, Failures: 0, Errors: 0, Skipped: 18
BUILD SUCCESS
```

Nenhum schema, migration, entity ou repository de `plans-entitlements` foi criado nesta fase.
