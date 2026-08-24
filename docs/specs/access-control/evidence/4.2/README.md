# Evidência 4.2 — Continuidade administrativa efetiva

- `AdministrativeContinuityEvaluationService` resolve o baseline ativo explícito, sem curinga, e exige que ao menos uma
  identidade global ou membership ativo possua todas as chaves mínimas permitidas, nenhuma bloqueada, identidade ativa
  e TOTP ou passkey com verificação de usuário.
- A avaliação cobre o instante da alteração e todas as fronteiras futuras conhecidas de regras e vínculos de grupo.
  Fonte indisponível, baseline vazio ou referência inconsistente falham de modo fechado.
- Regra, grupo e sujeito tomam primeiro o lock pessimista da revisão do contexto. Mutações que podem reduzir acesso são
  aplicadas e `flush`adas, reavaliadas e somente então recebem histórico, auditoria, nova revisão e invalidação pós-commit.
  Uma nova regra `PERMITIR` continua disponível para bootstrap ou recuperação e não é bloqueada por um estado anterior
  já degradado.
- O lifecycle de membership usa a mesma linha de guarda antes dos locks das associações e rejeita atomicamente mudança
  de papel, suspensão, remoção ou saída que elimine o último administrador apto.
- Revogação de TOTP ativo ou passkey administrativa bloqueia, em ordem canônica, o contexto global e todos os tenants em
  que a identidade possui membership corrente. Depois do `flush` do fator, todos os contextos são reavaliados; somente
  quando todos permitem as revisões são incrementadas e os caches invalidados.
- Transições de `identity_user` que retiram uma identidade de `ACTIVE` seguem a mesma ordem: a guarda global é a
  primeira leitura transacional, antes da descoberta dos memberships, para não fixar snapshot anterior no MySQL;
  depois bloqueiam tenants em ordem crescente, bloqueiam a identidade, aplicam e fazem `flush` do novo estado, reavaliam a
  continuidade e somente então revogam sessões. A falha reverte a transação antes da revogação e da revisão de cache.
- `AdministrativeContinuityEvaluationServiceTest` cobre permissão efetiva, ausência de fator, precedência de bloqueio,
  remoção do último administrador, expiração futura e contexto global. `AccessRulePersistenceIT`, no MySQL 9.7.2,
  comprova rollback conjunto da troca `PERMITIR` → `BLOQUEAR`, histórico, auditoria e revisão, além da concorrência
  entre bloqueio de identidade e regra do outro administrador: somente uma alteração que reduziria a administração
  pode concluir, e a outra é revertida. Os testes do adapter de fator e de identidade comprovam a ordem global/tenants
  e ausência de incremento diante de negação.

Ref: FR-ACL-CONT-*; SC-ACL-010
