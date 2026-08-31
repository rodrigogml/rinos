# Tarefas — Cadastro de Contas

Legenda: `[ ]` pendente, `[~]` em andamento, `[x]` concluído, `[!]` bloqueado.

## FASE 1 — Contratos e configuração

- [x] 1.1 Publicar DTOs, VOs, enums e facades de criação/status.
- [x] 1.2 Publicar portas de provisioning, membership, bootstrap ACL, plano e estado operacional.
- [x] 1.3 Configurar antiabuso, idempotência, outbox e validação de moeda/fuso.
- [x] 1.4 Testar validações, redação de `toString` e motivos seguros.

## FASE 2 — Schema global e persistência básica

- [x] 2.1 Criar init/update para tenant, conta, intenção, checkpoint, outbox e auditoria.
- [x] 2.2 Criar entities/repositories com constraints e optimistic version.
- [x] 2.3 Testar MySQL 9: uniques, FKs, checks, JSON, rollback e concorrência.

## FASE 3 — Aceite idempotente

- [x] 3.1 Implementar criação atômica e replay pelo mesmo payload.
- [x] 3.2 Integrar identidade ativa, garantia recente, origem, rate limit e Turnstile RFW.
- [x] 3.3 Implementar consulta segura por protocolo.
- [x] 3.4 Testar replay, conflito, falha antes do aceite, duas instâncias e indisponibilidade.

## FASE 4 — Saga de ativação

- [x] 4.1 Publicar outbox e integrar `tenant-storage-provisioning`.
- [x] 4.2 Integrar founding membership e grupo protegido/baseline ACL.
- [x] 4.3 Integrar contrato tenant e atribuição do `TENANT/FREE`, confirmando ocupação do fundador como uma de dez.
- [ ] 4.4 Ativar somente com quatro checkpoints completos; testar retomada e ordem concorrente.

## FASE 5 — Manutenção e lifecycle

- [ ] 5.1 Implementar consulta/alteração com contexto explícito e chaves canônicas.
- [ ] 5.2 Implementar suspensão/cancelamento com reauth, impedimentos e auditoria.
- [ ] 5.3 Publicar `AccountOperationalStatePort` concreto e invalidar consumidores.
- [ ] 5.4 Testar cross-tenant, conflito otimista, bloqueio operacional e não reutilização.

## FASE 6 — Interface e quality gate

- [ ] 6.1 Implementar criação/status com APIs públicas RFW e i18n.
- [ ] 6.2 Implementar manutenção/lifecycle e reautenticação.
- [ ] 6.3 Validar teclado, reflow, contraste, estados e privacidade.
- [ ] 6.4 Executar suíte, MySQL, E2E, segurança, medições e análise cruzada final.

## Dependências

```text
F1 -> F2 -> F3 -> F4 -> F5 -> F6
               |     |     |
        tenant-storage  account-membership + access-control + plans-entitlements
```

O próximo incremento depende da publicação concreta de `tenant-storage-provisioning`. Fase 4 não pode simular
adapters ausentes.
