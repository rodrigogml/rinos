# Modelo de Dados — Participação em Contas

## `membership_accountMembership`

| Campo | Regra |
|---|---|
| `idAccountMembership` | `BIGINT` interno |
| `publicId` | UUID binário, único e imutável |
| `idAccount` | FK global para conta |
| `idUser` | FK global para identidade |
| `roleType` | colaborador, contador, parceiro externo ou administrador |
| `originType` | fundador ou convite |
| `status` | ativa, suspensa, saída ou removida |
| `currentMarker` | `1` enquanto vigente; `NULL` ao encerrar |
| `startedAt`, `endedAt`, `version`, timestamps | lifecycle e concorrência |

Unique `(idAccount, idUser, currentMarker)` impede dois ciclos vigentes e permite múltiplos ciclos encerrados.
Transições usam lock pessimista por IDs em ordem estável e `version` esperado. Suspensão preserva marcador e histórico;
remoção ou saída tornam o marcador nulo e preenchem `endedAt`, permitindo somente um novo ciclo por novo convite.

## `membership_invitation`

Contém UUID público, conta, membership do convidante, e-mail normalizado imutável, papel proposto, digest MAC da prova,
`keyId`, estado, expiração de 15 dias, marcador pendente, contadores e optimistic version. Unique
`(idAccount, normalizedEmail, pendingMarker)` impede duas provas pendentes válidas.

## `membership_event`

Auditoria append-only com conta, associação/convite opcionais, ator humano ou origem sistêmica exclusivos, correlação,
resultado seguro, instante e detalhes JSON minimizados. Nunca contém prova, e-mail puro, IP puro ou payload integral.

## `membership_outboxEvent`

Evento durável para convite, invalidação e integração. Possui event UUID, agregado, payload JSON minimizado, status,
tentativas e lease. Para `INVITATION_ISSUED`, a prova de entrega é persistida separadamente em envelope AES-GCM
versionado (`secretCiphertext`, nonce, chave e expiração), nunca no payload JSON. O dispatcher recupera a prova apenas
em memória, apaga o buffer após montar a mensagem e remove atomicamente o envelope ao publicar, cancelar, consumir,
revogar, substituir ou expirar o convite. Leases vencidos podem ser retomados por outra instância; falhas usam
retentativa exponencial limitada sem ampliar a validade do convite.

## `membership_invitationRateWindow`

Janela fixa configurável por conta, associação convidante, destinatário normalizado e origem canônica. Cada emissão ou
reenvio reserva as quatro dimensões na mesma transação do convite. Se qualquer limite for atingido, toda a reserva é
revertida; assim uma negação em dimensão posterior não consome cota das anteriores. O marcador ativo anulável permite
histórico de janelas e uma única janela vigente por dimensão/chave.

## Invariantes

- associação vigente é `ACTIVE` ou `SUSPENDED` e tem marcador `1`/`endedAt NULL`;
- associação encerrada é `LEFT` ou `REMOVED` e tem marcador `NULL`/`endedAt NOT NULL`;
- somente convite `PENDING` tem marcador `1` e pode ser consumido;
- somente outbox de emissão pendente/em processamento pode conservar envelope; estados terminais eliminam o segredo;
- `expiresAt > createdAt`; o scheduler materializa `EXPIRED`, mas toda leitura também verifica o relógio;
- IDs de access-control que apontem para membership recebem FK após a tabela existir.
- mutação confirmada incrementa a revisão ACL do tenant na mesma transação e invalida o snapshot local após commit;
- redução de capacidade administrativa não confirma sem decisão efetiva disponível e positiva da continuidade.
