# Evidência da fase 5 — integrações

Data: 2026-08-16.

## Identidade e conta

- a primeira transição da identidade de `PENDING_VERIFICATION` para `ACTIVE` exige que `PERSONAL/FREE` seja criado ou
  confirmado na mesma transação;
- falha ou rejeição do contrato impede a alteração do estado da identidade e faz o fluxo público responder
  indisponibilidade;
- `DefaultPlanAssignmentPort` delega ao bootstrap canônico `TENANT/FREE` e preserva os estados idempotentes do checkpoint.

## Membership

- o convite só produz outbox de entrega depois que a vaga foi reservada;
- aceite converte a reserva em ocupação na mesma transação que cria a associação;
- recusa, revogação, substituição e expiração liberam exatamente a reserva ainda não convertida;
- reativação confirma a ocupação permanente e nunca cria nova vaga para uma identidade já contabilizada;
- o adapter deriva o tenant da conta e envia ao módulo de planos somente fingerprint do destinatário, nunca e-mail em
  claro;
- a porta `occupy` está pronta para inclusão manual/importada; não existe ainda no produto uma entrada persistente de
  criação manual/importação a conectar, portanto nenhum fluxo fictício foi criado.

## Access-control

- `PlanEntitlementAccessPort` usa o sujeito explícito e a engine persistente entregue na Fase 4.2;
- operações sem requisito de plano continuam sem inferir contrato pessoal.

## Validação focada

- ativação exige contrato pessoal antes de alterar o estado;
- adapter do checkpoint tenant preserva contrato e referência pública;
- `MembershipPersistenceIT` executa 14 cenários sobre o MySQL real com a autoridade real de planos, incluindo emissão e
  aceite concorrentes, revogação, reenvio, expiração, lifecycle e continuidade administrativa.

## Validação integral

- comando: `mvn verify`;
- resultado: `BUILD SUCCESS` em 2026-08-16;
- testes unitários: 728 executados, sem falhas ou erros;
- testes de integração: 138 executados, sem falhas ou erros, com 18 cenários condicionais ignorados;
- inclui empacotamento Spring Boot, build de produção do frontend e inicialização real com configuração externa.
