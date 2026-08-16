# Evidência 3.2 — Gates externos e decisão composta

- `AuthorizationFacadeImpl` é a fronteira única de decisão, exigência e explicação.
- Providers separados avaliam estrutura, entitlement e garantia de autenticação; cada motivo permanece em seu grupo
  próprio e indisponibilidade produz negação segura.
- O provider estrutural valida identidade global e consome `AccountMembershipAccessPort` para conferir, sem confiar na
  requisição, identidade, tenant, estado da associação e estado operacional da conta. Enquanto o módulo de membership
  não publicar o adapter concreto, o adapter default nega por `ACL_ASSOCIATION_UNAVAILABLE`.
- O provider de plano consome `PlanEntitlementAccessPort`. Direito ausente é `ACL_PLAN_REQUIRED`, indisponibilidade do
  provider é `ACL_PLAN_UNAVAILABLE`, e nenhum dos dois é transformado em bloqueio ACL.
- Operações sensíveis exigem autenticação forte recente por TOTP ou passkey (`ACL_ASSURANCE_REQUIRED`).
- Testes cobrem autorização quando todos os gates e chaves permitem, operação composta com uma chave ausente,
  associação pertencente a outra identidade, direito de plano ausente, provider indisponível, fator insuficiente e
  propagação de motivos seguros.

As integrações concretas com membership, conta operacional e serviço de planos ainda dependem das portas desses módulos;
por isso as tarefas `3.2.1` a `3.2.3` permanecem em andamento e os defaults continuam fail-safe.
