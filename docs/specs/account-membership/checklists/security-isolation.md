# Checklist de Segurança e Isolamento — Participação em Contas

- [x] Conta/tenant são explícitos e associações de outro tenant não são reutilizadas.
- [x] Prova persiste somente como digest MAC rotacionável e nunca entra em logs.
- [x] Locks, uniques e idempotência cobrem múltiplas instâncias.
- [x] Estado de identidade, conta, associação, plano e autorização é revalidado no uso.
- [x] Cache/sessão não são autoridade e indisponibilidade nega.
- [x] Auditoria minimizada preserva histórico sem segredos.
