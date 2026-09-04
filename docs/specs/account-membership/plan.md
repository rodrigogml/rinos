# Plano Técnico — Participação em Contas

**Status**: pronto para implementação incremental

## Componentes

```text
MembershipFacade / InvitationFacade
  -> AuthorizationFacade + garantia recente
  -> MembershipService / InvitationService
      -> identity + account + plan ports
      -> membership/invitation/audit/outbox (transação global)

InvitationDeliveryScheduler
  -> claim transacional com lease persistente
  -> envelope AEAD -> URL pública canônica -> EmailDispatchService RFW
  -> publicação, retentativa com backoff ou cancelamento + apagamento do envelope

AccountMembershipAccessAdapter -> gate estrutural do access-control
FoundingMembershipBootstrapAdapter -> saga de account-registration
```

## Fronteiras e transações

- IDs de conta/tenant vêm da autoridade `account-registration`; membership não cria nem ativa conta.
- Convite é criado e outbox de notificação é gravada atomicamente; entrega ocorre após commit.
- Rate limit reserva conta, convidante, destinatário e origem na mesma transação; qualquer bloqueio reverte tudo.
- O dispatcher permite múltiplas instâncias por lock/lease, recupera leases vencidos e nunca envia após a validade.
- Estados terminais do convite cancelam a entrega e eliminam a representação recuperável da prova.
- Aceite/recusa usa lock pessimista no convite. Unique vigente impede duplicação entre instâncias.
- Suspensão, reativação, remoção, saída e alteração de papel usam optimistic version, reautorização e auditoria.
- Operação que possa afetar continuidade chama o avaliador efetivo e é rejeitada antes do commit se não houver outro
  administrador apto agora ou numa expiração já conhecida.

## Ordem incremental

1. contratos, enums e portas;
2. schema, entidades e repositories;
3. bootstrap fundador e adapter estrutural para ACL;
4. convite, digest, outbox, aceite e recusa;
5. lifecycle persistente e invalidação revisionada; continuidade efetiva é integrada pela porta fail-safe do ACL;
6. interface RFW e E2E.

O primeiro slice termina com bootstrap fundador idempotente e leitura estrutural concreta. Ele não simula plano nem
ativa a conta.

## Falhas e cache

Fonte indisponível nega. Cache, se introduzido após medição, usa chave `(membershipId, revision)` e nunca é autoridade.
Mudanças confirmam a revisão no commit e invalidam depois; atraso ou falha de invalidar só pode causar nova leitura ou
negação, nunca ampliar acesso.

## Constitution check

- contexto tenant explícito e validado por conta/associação;
- papel sem efeito autorizativo e negação por padrão;
- bloqueios efetivos prevalecem na continuidade;
- provas, e-mails e IDs internos não vazam em logs/interface;
- interface futura consome APIs públicas RFW.
