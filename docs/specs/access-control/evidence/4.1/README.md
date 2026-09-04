# Evidência 4.1 — Grupos, sujeitos e regras

- `AccessAdministrationMutationService` cria, altera e desativa logicamente grupos; atribui, reativa e encerra vínculos
  de identidades globais ou memberships de tenant sem permitir grupos aninhados.
- Nomes são normalizados e continuam únicos por contexto. Escopo global aceita somente identidade; escopo tenant exige
  membership confirmado pelo `AccountMembershipAccessPort` no mesmo tenant.
- Cada mutação efetiva grava evento minimizado com valores anterior e atual, incrementa a revisão do contexto e agenda
  invalidação local somente após commit. Repetições idempotentes não incrementam revisão.
- `AccessRuleMutationService` mantém uma regra corrente por origem/chave, troca efeito ou vigência na mesma linha e agora
  também oferece desativação lógica com histórico append-only.
- Grupos protegidos não podem ser desativados nem perder regras correspondentes às chaves do baseline. O encerramento
  de sujeitos é permitido somente quando o resultado efetivo ainda preserva outro administrador apto.
- Testes unitários cobrem ordem transacional, isolamento de membership entre tenants, proteção conservadora e histórico
  de desativação. `AccessRulePersistenceIT` valida no MySQL 9.7.2 criação/desativação de grupo, atribuição/encerramento
  de sujeito, desativação de regra, auditoria, histórico e revisão, além de duas transações concorrentes sobre o
  mesmo grupo, das quais uma é rejeitada pelo `@Version` otimista.
