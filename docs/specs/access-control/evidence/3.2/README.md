# Evidência 3.2 — Gates externos e decisão composta

- `AuthorizationFacadeImpl` é a fronteira única de decisão, exigência e explicação.
- Providers separados avaliam estrutura, entitlement e garantia de autenticação; cada motivo permanece em seu grupo
  próprio e indisponibilidade produz negação segura.
- O provider estrutural valida identidade global e consome o adapter concreto
  `AccountMembershipAccessAdapter` por `AccountMembershipAccessPort` para conferir, sem confiar na requisição,
  identidade, tenant, estado da associação e estado operacional da conta. A ausência ou indisponibilidade da fonte
  continua negada por `ACL_ASSOCIATION_UNAVAILABLE`.
- O provider de plano consome `PlanEntitlementAccessPort`. Direito ausente é `ACL_PLAN_REQUIRED`, indisponibilidade do
  provider é `ACL_PLAN_UNAVAILABLE`, e nenhum dos dois é transformado em bloqueio ACL.
- Operações sensíveis exigem autenticação forte recente por TOTP ou passkey (`ACL_ASSURANCE_REQUIRED`).
- A UI revalida a sessão persistida por `AuthorizationAuthenticationFacade` antes de formar a requisição; sessão
  ausente, revogada, expirada, de outra identidade ou sem método verificado falha antes da autorização.
- Testes unitários cobrem autorização quando todos os gates e chaves permitem, operação composta com uma chave ausente,
  associação pertencente a outra identidade, direito de plano ausente, provider indisponível, fator insuficiente e
  propagação de motivos seguros. Os testes de integração `MembershipPersistenceIT` e
  `JdbcEntitlementEvaluationServiceIT` exercitam os adapters concretos contra schema MySQL descartável.
