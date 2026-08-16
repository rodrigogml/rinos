# Evidência — Lifecycle Persistente

Em 2026-08-16, `MembershipPersistenceIT` no MySQL 9.7.2 comprovou:

- alteração de papel sem alterar grupos ou regras de acesso;
- suspensão e reativação preservando a mesma associação e seu histórico;
- remoção encerrando o ciclo, eliminando o marcador vigente e permitindo futura reentrada somente em nova associação;
- optimistic version e locks pessimistas em ordem estável, com duas mutações concorrentes convergindo em uma alteração
  e um conflito, sem sobrescrita ou exceção;
- autenticação forte recente obrigatória, tentativa negada auditada e nenhuma alteração no estado;
- continuidade negada ou indisponível impedindo atomicamente suspensão, remoção ou saída;
- evento, outbox e revisão do contexto emitidos somente para alteração confirmada.

`AccessMembershipContextInvalidationAdapterTest` comprova incremento transacional da revisão ACL do tenant e
invalidação local agendada após commit. O avaliador persistente de continuidade — permissões, bloqueios, vigências e
2FA de todos os candidatos — permanece explicitamente na tarefa `access-control` 4.2 e a porta ausente nega por padrão.
