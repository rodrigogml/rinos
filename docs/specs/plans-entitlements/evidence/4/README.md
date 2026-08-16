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
- `mvn verify`: 725 testes unitários e 136 testes de integração executados, 18 cenários condicionais ignorados, sem
  falhas ou erros.

Os itens 4.1, 4.3, 4.4 e 4.5 continuam abertos e serão concluídos nos próximos lotes do núcleo.
