# Quickstart — Participação em Contas

1. Bootstrap fundador recebe protocolo/conta/tenant públicos e identidade fundadora; duas chamadas retornam a mesma
   associação, sem duplicar ciclo.
2. Gate ACL consulta `membershipId`; somente associação ativa, do ator e tenant pedidos, com conta operacional passa a
   condição estrutural.
3. Administrador autorizado emite convite; o digest valida a prova, enquanto um envelope AEAD apagável permite a
   entrega durável pela outbox sem token em payload, log ou auditoria.
4. Identidade com e-mail confirmado correspondente aceita; convite é consumido e uma associação ativa nasce na mesma
   transação, após capacidade do plano.
5. Suspensão ou remoção confirma revisão/invalidação; outra aba ou job revalida e é negado imediatamente.
6. Tentativa de remover o último administrador efetivo apto é revertida atomicamente.

Gates mínimos: testes de contrato, MySQL 9 para constraints/locks/rollback/corrida, autorização cross-tenant,
continuidade, logs sem segredos e interface acessível RFW.
