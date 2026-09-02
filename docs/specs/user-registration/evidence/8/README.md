# Evidência da fase 8 — contrato pessoal antes da ativação

Data: 2026-09-01.

## Entrega

- `PersonalContractBootstrapPort` é um contrato público tipado, sem expor entities do módulo de planos;
- as ativações local e Google passam pelo mesmo lifecycle, que somente aceita o resultado concluído ou já concluído no escopo `PERSONAL`;
- ausência da porta, resultado nulo, escopo diferente ou indisponibilidade do catálogo encerram a operação antes da transição da identidade para `ACTIVE`;
- a migration global `20260816_003_update.sql` já regulariza idempotentemente os contratos e as atribuições `PERSONAL/FREE` das identidades preexistentes antes da invariável de ativação.

## Validação

- `UserLifecycleServiceTest` comprova a intenção idempotente de bootstrap e que a identidade continua pendente quando o serviço ou catálogo do contrato está indisponível;
- `RegistrationActivationServiceTest` e `ExternalRegistrationCompletionServiceTest` exercitam as ativações local e Google com o contrato pessoal explícito;
- `GlobalDatabaseMigrationIT.startup_shouldBackfillPlansContractsAndCapacityIdempotently` cria duas identidades ativas legadas, executa o updater global duas vezes e confirma contratos pessoais únicos, atribuições vigentes e catálogo pronto;
- comando executado em MySQL real: `mvn -q "-Dit.test=GlobalDatabaseMigrationIT" verify`;
- resultado: nove testes de integração, sem falhas, erros ou skips.

> [!IMPORTANT]
> O contrato `PERSONAL/FREE` pertence somente à identidade global. Esta entrega não cria tenant, empresa, associação, grupo, chave, permissão ou direito de tenant e não altera a evidência histórica dessa ausência.
