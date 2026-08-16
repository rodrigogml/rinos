# Evidência da fase 6 — validação cruzada

Data: 2026-08-16.

## Matriz de cenários

- isolamento: `PlansTransactionalCoreIT` mantém contratos `PERSONAL` e `TENANT` separados e comprova que reservas e
  ocupações de um tenant não alteram a capacidade de outro;
- teto e concorrência: `PlansTransactionalCoreIT` cobre o limite dez em convite e inclusão direta, repetição e duas
  instâncias concorrendo pela última vaga; `MembershipPersistenceIT` cobre aceite concorrente na transação real;
- ocupação permanente: o núcleo comprova que suspensão, remoção, saída e bloqueio da identidade não apagam nem deixam
  de contar a ocupação histórica;
- liberação pré-aceite: reserva liberada duas vezes permanece com um único efeito; revogação, reenvio e expiração são
  exercitados pelo `MembershipPersistenceIT` sem liberar ocupação já convertida;
- backfill: `GlobalDatabaseMigrationIT.startup_shouldBackfillPlansContractsAndCapacityIdempotently` executa o updater
  duas vezes, preserva contrato existente, cria uma ocupação por identidade distinta e reconcilia convite pendente;
- restauração e fallback: `JdbcEntitlementEvaluationServiceIT` rejeita a reativação estruturalmente inconsistente de
  atribuição encerrada, mantém o fallback do mesmo escopo e nega quando não existe padrão aplicável;
- indisponibilidade: testes unitários dos adapters e da engine comprovam negação segura sem provider, contrato ou fonte;
- repetição: bootstrap, reserva, conversão e liberação devolvem resultados idempotentes sem duplicar efeito.

## Resultado integral

- comando: `mvn verify`;
- resultado: `BUILD SUCCESS` em 2026-08-16;
- testes unitários: 728 executados, sem falhas ou erros;
- testes de integração: 141 executados, sem falhas ou erros, com 18 cenários condicionais ignorados;
- inclui MySQL real, concorrência cross-instance, migração/backfill, empacotamento Spring Boot, frontend de produção e
  inicialização real da aplicação.
