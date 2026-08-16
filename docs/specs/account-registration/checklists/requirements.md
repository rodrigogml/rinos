# Checklist de Requisitos — Account Registration

- [x] Conta e tenant possuem identidade imutável e não baseada no nome.
- [x] Dados mínimos, estados e transições estão modelados.
- [x] Aceite idempotente e concorrência possuem constraints e algoritmo.
- [x] Fluxo assíncrono usa outbox e não presume transação distribuída.
- [x] Founder, bootstrap ACL, storage e plano estão separados por portas.
- [x] Ativação exige todas as confirmações e falha fechada.
- [x] Turnstile, limites e origem confiável estão planejados.
- [x] Manutenção e lifecycle referenciam chaves canônicas.
- [x] Auditoria, observabilidade, privacidade e retenção estão delimitadas.
- [x] Cenários mensuráveis possuem tarefas de testes.
- [x] Primeiro slice reduz escopo sem declarar ativação incompleta.
