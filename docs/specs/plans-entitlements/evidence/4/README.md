# Evidência da fase 4 — núcleo

Data: 2026-08-16.

## Avaliação e cache (4.2)

- contrato e atribuição efetiva são consultados em tempo de execução para o titular explícito;
- fallback usa exclusivamente o plano padrão publicado do mesmo escopo;
- composição publicada é copiada para cache local imutável, indexado por `planVersionId`;
- uso de capacidade não é cacheado e permanece atual a cada decisão;
- contrato ausente ou suspenso, composição ausente, inconsistência e falha de banco negam com motivo seguro;
- o adapter real implementa `PlanEntitlementAccessPort` e substitui o fallback indisponível do access-control quando há
  `DataSource`;
- `PERSONAL` e `TENANT` percorrem a mesma engine, sem inferência ou fallback entre escopos.

## Validação

- teste unitário comprova `SOURCE_UNAVAILABLE` e snapshot fail-closed quando a fonte falha;
- teste em MySQL real comprova leitura ao vivo do contrato e do uso, limite `10`, transição para `LIMIT_REACHED` e cache
  imutável da composição publicada;
- `mvn verify`: 725 testes unitários e 138 testes de integração executados, 18 cenários condicionais ignorados, sem
  falhas ou erros.

## Contratos e capacidade (4.1, 4.3–4.5)

- bootstrap `PERSONAL/FREE` e `TENANT/FREE` cria ou confirma contrato e atribuição sob lock do titular;
- o bootstrap tenant ocupa permanentemente a vaga do fundador e repetições devolvem o mesmo contrato;
- reserva, ocupação, conversão e liberação pré-aceite são idempotentes pelas identidades públicas da intenção;
- toda mutação autoritativa bloqueia o contrato tenant no MySQL, expira reservas vencidas e reconta ocupações e reservas
  dentro da mesma transação;
- uma ocupação nunca é removida por mudança posterior de estado da associação;
- auditoria e outbox são gravadas na mesma transação do fato; métricas usam apenas operação e resultado seguros;
- `inspect` e todos os resultados públicos expõem somente limite, totais e códigos seguros, sem titular, e-mail ou regra
  interna.

O teste `PlansTransactionalCoreIT` comprova bootstrap repetido, teto dez, repetição de reserva/liberação/conversão e duas
instâncias concorrendo pela última vaga. A corrida resulta em exatamente uma reserva e um `LIMIT_REACHED`, mantendo
`occupied + reserved = 10`.
