# Contratos públicos — Planos e direitos

## Tipos

```java
enum ContractScope { PERSONAL, TENANT }
enum EntitlementType { AVAILABILITY, MAXIMUM_QUANTITY, PERIODIC_QUOTA }
enum EntitlementDecisionStatus {
  AVAILABLE, UNAVAILABLE, LIMIT_REACHED, INVALID_CONTEXT, INCONSISTENT, SOURCE_UNAVAILABLE
}
```

- `EntitlementSubject`: união fechada `PersonalSubject(userId)` ou `TenantSubject(tenantId)`;
- `EntitlementRequirement`: `subjectScope` e código estável;
- `EntitlementEvaluationRequest`: sujeito, requisitos, operação, instante e correlation;
- `EntitlementDecision`: resultado por requisito, valor tipado, uso conhecido, origem normal/fallback e motivos seguros;
- `ServiceContractDescriptor`: UUID público, escopo, titular público, estado, plano e versão vigentes;
- `PlanDescriptor` e `PlanVersionDescriptor`: apresentação localizada e estado sem expor código técnico a usuário comum.

## Portas de bootstrap

```java
interface PersonalContractBootstrapPort {
  ContractBootstrapResult ensure(PersonalContractBootstrapRequest request);
}

interface TenantContractBootstrapPort {
  ContractBootstrapResult ensure(TenantContractBootstrapRequest request);
}
```

São idempotentes e distinguem `COMPLETED`, `REJECTED` e `UNAVAILABLE`. A segunda implementa o atual
`DefaultPlanAssignmentPort` por adapter de compatibilidade.

## Avaliação

```java
interface EntitlementEvaluationFacade {
  EntitlementDecision evaluate(EntitlementEvaluationRequest request);
}
```

O adapter de access-control substitui código isolado por `EntitlementRequirement`. Operação global administrativa sem
requisito não consulta plano pessoal.

## Capacidade de usuário tenant

```java
interface TenantUserCapacityFacade {
  CapacityReservationResult reserve(InvitationCapacityRequest request);
  CapacityOccupationResult occupy(AssociationCapacityRequest request);
  CapacityOccupationResult convert(InvitationAcceptanceCapacityRequest request);
  CapacityReleaseResult releaseUnaccepted(InvitationCapacityReleaseRequest request);
  CapacityDecision inspect(TenantUserCapacityRequest request);
}
```

`reserve` ocorre antes do envio; `convert` e `occupy` participam da transação da associação. Lifecycle de membership
consulta a mesma facade em toda operação que possa associar/ativar, embora estado nunca libere ocupação.

## Motivos seguros

`PLAN_SCOPE_MISMATCH`, `PLAN_DEFAULT_UNAVAILABLE`, `PLAN_CONTRACT_UNAVAILABLE`, `PLAN_ENTITLEMENT_UNAVAILABLE`,
`PLAN_LIMIT_REACHED`, `PLAN_CAPACITY_RESERVED`, `PLAN_CONTEXT_INVALID` e `PLAN_SOURCE_UNAVAILABLE`.
