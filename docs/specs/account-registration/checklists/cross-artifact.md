# Análise Cruzada

## Lacuna RFW registrada em 2026-08-15

- [x] A action `account-creation` foi confrontada com o catálogo público de operações.
- [x] A ausência de operação semântica própria foi documentada em `rfw-gap-analysis.md`.
- [x] O submódulo somente foi alterado após autorização; core/showroom foram validados antes do ponteiro Rinos.

| Tema | Spec | Plano/modelo/contrato | Tarefa |
|---|---|---|---|
| identidade conta/tenant | FR-ACC-001..009 | `data-model.md` | 2.1 |
| aceite idempotente | FR-ACC-CREATE-004,008,011..013 | `plan.md`, contrato | 3.1 |
| antiabuso | FR-ACC-ABUSE-* | `research.md`, interface | 3.2 |
| provisionamento | FR-ACC-CREATE-014, BOUND-006 | outbox + porta | 4.1 |
| founder e ACL | FR-ACC-CREATE-005..007 | portas de bootstrap | 4.2 |
| plano padrão | FR-ACC-PLAN-* | porta de assignment | 4.3 |
| ativação | FR-ACC-STATE-003 | checkpoints/saga | 4.4 |
| manutenção | FR-ACC-MAINT-* | facade autorizada | 5.1 |
| lifecycle | FR-ACC-STATE-004..009 | reauth + auditoria | 5.2 |
| interface/RFW | SC-ACC-001,012 | `interface-spec.md` | 6.1 |

## Resultado

- Não há contradição conhecida com constituição ou catálogo ACL.
- A lacuna RFW está isolada e explicitamente bloqueada; não é tratada como capacidade entregue.
- O primeiro slice não satisfaz ativação; isso está explicitamente fora de seu critério de conclusão.
- Membership, storage, access bootstrap e plano são dependências rastreadas, não sucessos simulados.
- `account-registration/spec.md` ainda chama o plano inicial de `Free`; o código estável continua `FREE` e a autoridade
  final permanece `plans-entitlements`.
- A documentação está apta a iniciar schema do slice básico.
