# Tarefas — Participação em Contas

Legenda: `[ ]` pendente, `[~]` em andamento, `[x]` concluído, `[!]` bloqueado.

## Fase 1 — Contratos e schema

- [~] 1.1 Publicar enums, VOs, facades e portas fail-safe — facade administrativa autorizada concluída; listagem e
  facade pública de decisão do destinatário permanecem na fase de interface.
- [x] 1.2 Criar init/update de membership, convite, evento e outbox.
- [x] 1.3 Mapear entities/repositories e FKs de access-control.
- [~] 1.4 Testar MySQL 9, constraints, rollback e concorrência; lifecycle permanece no próximo slice.

## Fase 2 — Fundador e gate estrutural

- [x] 2.1 Implementar bootstrap fundador idempotente.
- [x] 2.2 Implementar `AccountMembershipAccessPort` concreto.
- [x] 2.3 Revalidar gates ACL e isolamento entre tenants.

## Fase 3 — Convites

- [x] 3.1 Implementar emissão/substituição/reenvio/revogação com MAC e rate limit multidimensional transacional.
- [x] 3.2 Implementar outbox de notificação, envelope AEAD apagável, dispatcher com lease/retentativa e scheduler de expiração.
- [x] 3.3 Implementar visualização persistente, aceite e recusa atômicos; facade pública permanece na fase de interface.
- [~] 3.4 Integrar identidade confirmada e capacidade do plano — identidade ativa/e-mail e porta fail-safe prontos;
  adapter persistente de planos aguarda `plans-entitlements`; deve reservar vaga antes do envio e manter ocupação
  histórica por identidade depois do aceite.

## Fase 4 — Lifecycle e continuidade

- [x] 4.1 Implementar papel, suspensão, reativação, remoção e saída com versionamento, locks, auditoria e outbox.
- [x] 4.2 Integrar autenticação recente e continuidade administrativa efetiva — lifecycle usa o avaliador ACL
  persistente, considera bloqueios, vigências atuais/futuras, identidade ativa e TOTP/passkey administrativo.
- [~] 4.3 Publicar revisão/invalidação imediata e testar múltiplas instâncias — adapter revisionado e concorrência da
  associação prontos; propagação remota continua otimização pendente, sem ser autoridade.

## Fase 5 — Interface e quality gate

- [ ] 5.1 Implementar superfícies com APIs públicas RFW e i18n.
- [ ] 5.2 Validar acessibilidade, privacidade, E2E e análise cruzada final.

Dependências: account-registration F2 -> membership F1/F2 -> account-registration saga; plans-entitlements é obrigatório
antes do aceite de convidados, mas não para criar a associação fundadora não operacional.
