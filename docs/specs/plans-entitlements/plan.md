# Implementation Plan — Planos, contratos e direitos

## Objetivo do slice

Entregar persistência e avaliação para dois contratos independentes, bootstrap dos dois planos `FREE`, limite de dez
usuários associados no tenant e adapters para cadastro de usuário, cadastro de conta, membership e access-control.

## Fronteiras

- `api/module/plans`: contratos públicos, descritores, facades e portas consumidas;
- `backend/module/plans`: domínio, serviços, repositórios, bootstrap, auditoria e adapters;
- `identity`: consome porta idempotente de contrato pessoal durante ativação;
- `account`: consome porta idempotente de contrato tenant durante provisionamento;
- `membership`: consome reserva/ocupação de capacidade em todas as entradas;
- `access`: envia requisito tipado e recebe decisão minimizada.

Nenhum consumidor consulta entity ou repository de planos.

## Fluxos

### Ativação pessoal

1. Validar identidade pronta para ativação.
2. Resolver o padrão `PERSONAL` publicado.
3. Criar ou confirmar contrato único e atribuição vigente.
4. Concluir a ativação somente após sucesso.

### Ativação tenant

1. Resolver tenant e conta aceitos.
2. Criar ou confirmar contrato `TENANT` e atribuição `TENANT/FREE`.
3. Confirmar ocupação do fundador.
4. Concluir o checkpoint `DEFAULT_PLAN` e somente então ativar a conta.

### Convite e inclusão

1. Resolver contrato tenant e versão efetiva.
2. Bloquear o agregado de capacidade.
3. Reutilizar ocupação da mesma identidade ou reserva da mesma intenção.
4. Rejeitar se ocupações mais reservas distintas forem dez.
5. Persistir reserva antes do envio ou ocupação junto da inclusão manual.
6. No aceite, converter reserva em ocupação na mesma transação da associação.

Todos os caminhos alternativos — importação, ativação e reativação — passam pela mesma facade de capacidade.

## Transações e integridade

- índices únicos para contrato por titular e escopo, plano por `(scope, code)`, padrão por escopo e atribuição vigente;
- escopo redundante em FKs compostas quando necessário para impedir combinação incompatível no banco;
- optimistic version em contrato e pessimistic/advisory row lock no agregado de capacidade;
- idempotency key em bootstrap, atribuição, reserva e conversão;
- outbox e auditoria gravadas na mesma transação do fato.

## Avaliação

`EntitlementEvaluationRequest` contém sujeito, código, operação, instante e correlação. A engine valida cadeia, resolve
atribuição ou fallback do mesmo escopo, carrega composição imutável e retorna disponibilidade/valor/motivo. Limites que
dependem de recurso usam provider do módulo proprietário; a engine não consulta membership diretamente.

## Falhas

Catálogo duplicado, padrão inválido, contrato incompatível, provider indisponível, timeout ou erro de cache retornam
indisponibilidade segura. Nenhum fallback cruza escopo. Diagnóstico e regularização exigem autorização própria.

## Entrega incremental

1. contratos públicos e testes de invariantes;
2. schema global, bootstrap e backfill;
3. domínio de catálogo/contrato/atribuição;
4. avaliação e adapters de access-control;
5. capacidade membership e concorrência;
6. integrações de ativação pessoal e tenant;
7. auditoria, outbox, observabilidade e testes MySQL multi-instância;
8. documentação de evidências.

## Fora do slice

Billing, pagamento, upgrade/downgrade, catálogo administrável por UI, franquias periódicas, outros limites e data plane
pessoal. A interface permanece especificada para ciclo posterior.
