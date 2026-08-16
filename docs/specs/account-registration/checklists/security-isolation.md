# Checklist de Segurança e Isolamento

- [x] IDs sequenciais não aparecem em contratos/UI.
- [x] Tenant é explícito em toda operação posterior ao aceite.
- [x] Principal e sessão não armazenam tenant ativo ou permissões.
- [x] Papel fundador não autoriza.
- [x] Tokens, IP puro, provas e segredos não são persistidos/logados.
- [x] Status por protocolo restringe-se ao criador ou chave administrativa adequada.
- [x] Indisponibilidade nunca ativa conta nem amplia acesso.
- [x] Outbox é idempotente e concorrente entre instâncias.
- [x] Cancelamento é lógico e IDs não são reutilizados.
- [x] MySQL real testará FKs, uniques, rollback, locks e isolamento.
- [x] Interface não revela outro tenant, storage ou motivos internos.
- [x] RFW foi consultado e não será alterado neste ciclo.
