# Checklist de segurança e isolamento

- [x] Contexto global não implica plano pessoal.
- [x] Requisito de direito identifica explicitamente o titular.
- [x] Plano, direito, contrato e atribuição incompatíveis falham fechados.
- [x] Fallback nunca cruza escopo ou tenant.
- [x] Cache não é autoridade de atribuição nem capacidade.
- [x] Concorrência é serializada no banco entre instâncias.
- [x] Interface não substitui verificação no serviço.
- [x] Erros não revelam contrato, usuário, convite ou tenant alheio.
- [x] Auditoria e outbox acompanham o fato na mesma transação.
- [x] Operações assíncronas revalidam contrato, direito e capacidade antes de iniciar.
