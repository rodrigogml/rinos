# Evidência — Persistência básica

- Roadmap corrigido e ciclo documental concluído: pesquisa, plano, modelo, contrato, interface RFW, quickstart,
  checklists e backlog.
- Schema global `20260815_002` cria tenant, conta, intenção, quatro checkpoints, outbox e auditoria; init de banco novo
  publica a mesma versão.
- `GlobalDatabaseUpdateCatalogTest` e `GlobalDatabaseMigrationIT` passaram contra MySQL 9.7.2 para bootstrap, update
  incremental, banco atual, versão futura e falha parcial `20260815_003`.
- Contratos públicos iniciais validam nome, moeda, fuso, confirmação e censuram token/idempotência no diagnóstico.
- Entidades e repositories mapeiam IDs públicos UUID binários, estados fechados, relação conta/tenant e optimistic lock.
- `AccountCreationAcceptanceService` confirma tenant, conta `CREATING`, intenção, checkpoints, auditoria e outbox em
  uma transação; replay sequencial compara hash canônico e payload divergente retorna conflito.
- `AccountOperationalStateAdapter` publica estado minimizado sem permissões, participantes ou dados cadastrais.

- `AccountPersistenceIT`, executado contra MySQL 9.7.2, comprova aceite/replay, conflito de idempotência, rollback
  integral por falha tardia, convergência de duas execuções em uma única intenção e status restrito ao criador.
- `AccountCreationRequestTest` comprova validação, normalização e redação de dados sensíveis.
- As portas de storage, membership, ACL e plano estão publicadas sem defaults que simulem sucesso.

Permanece pendente antes de concluir a fase 3 a integração de identidade, garantia, rate limit e prova antiabuso. A
operação semântica ausente no RFW está descrita em `rfw-gap-analysis.md` e exige autorização separada.
