# Evidência 3.3 — Cache local revisionado

- `AuthorizationSnapshotCache` mantém LRU local por sujeito e contexto, limitado por peso e inatividade configuráveis
  em `rinos.access.cache.max-weight` e `rinos.access.cache.idle-timeout`.
- O valor contém somente `AccessSourceSnapshot` imutável; decisão final, plano, identidade e garantia não são cacheados.
- Toda operação independente consulta `access_contextRevision`; divergência remove a entrada mesmo quando uma
  notificação entre instâncias é perdida.
- Vigência é recalculada no instante de cada decisão e a entrada fica inelegível ao alcançar sua próxima fronteira.
- `AccessContextCacheInvalidationService` invalida o contexto local somente em `afterCommit`; rollback não descarta
  fotografia ainda válida.
- Testes cobrem revisão divergente, duas instâncias com notificação perdida, tenants distintos, fronteira temporal,
  inatividade, LRU por peso, reutilização de fontes com nova consulta de revisão e invalidação após commit.
- `AccessRulePersistenceIT` voltou a passar contra MySQL 9.7.2 após a integração da invalidação transacional.
