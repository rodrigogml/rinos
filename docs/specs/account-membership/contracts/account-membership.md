# Contratos — Participação em Contas

## Contratos públicos planejados

- `MembershipDescriptor`: UUID público, conta pública, papel, origem, estado e version.
- `MembershipInvitationRequest`: conta pública, e-mail, papel, idempotência e confirmação.
- `MembershipInvitationResult`: estado seguro, convite/protocolo público e retry opcional.
- `MembershipInvitationDecision`: prova transitória e decisão aceitar/recusar.
- `MembershipMutationRequest`: associação pública, operação, version, confirmação e justificativa segura.
- `MembershipMutationCommand`: comando interno derivado pelo adapter confiável, com ator, operação, optimistic version,
  confirmação, garantia recente, correlação e instante.
- `MembershipMutationResult`: alterado, sem mudança, conflito, rejeitado ou indisponível, com versão e revisão seguras.
- `MembershipFacade`: listar, convidar, reenviar, revogar e administrar no tenant explícito.
- `MembershipInvitationFacade`: visualizar proposta segura e decidir para a identidade autenticada.

Identidade, sessão, origem e tenant autorizado são derivados pelo adapter confiável, não aceitos do navegador.

## Portas entre módulos

- `AccountMembershipAccessPort.inspect(membershipId)`: existência, identidade, tenant, estado ativo e conta operacional.
- `FoundingMembershipBootstrapPort`: criação idempotente da associação fundadora.
- `TenantUserCapacityFacade`: reserva antes do envio, ocupa na inclusão, converte no aceite e libera somente reserva
  revogada/expirada antes do aceite. `MembershipPlanCapacityPort` torna-se adapter de consulta compatível.
- `MembershipAdministrativeContinuityPort`: avalia candidatos efetivos e 2FA em mutações sensíveis.
- `MembershipContextInvalidationPort`: propaga revisão sem ser autoridade da decisão.

O núcleo persistente publica os contratos de mutação e as duas portas. A fronteira administrativa também está
implementada por `MembershipFacade` e `SpringMembershipFacadeAdapter`:

- requests provenientes da interface não carregam ator, tenant, associação, garantia ou chave de acesso;
- o adapter deriva identidade, associação, tenant e garantia da `UI` exata;
- a facade escolhe internamente `tenant.membership.invite` ou `tenant.membership.manage`, sem aceitar código livre;
- `AuthorizationFacade.require(...)` termina antes de qualquer chamada ao serviço persistente;
- mutações de lifecycle são sensíveis e somente recebem o marcador interno de garantia forte depois da decisão;
- papel proposto continua sendo dado cadastral e não participa da escolha da operação ou das chaves.

Todas as entradas de associação usam a facade autoritativa. Ocupação é por identidade distinta e permanece após
suspensão, remoção, saída ou bloqueio; reentrada da mesma identidade não duplica a ocupação.

A facade de decisão do destinatário e as superfícies Vaadin permanecem planejadas para a fase de interface da feature.

## Motivos seguros

Motivos distinguem entrada, autorização, associação, conta, plano, garantia, convite e indisponibilidade, mas não revelam
cadastro do e-mail, grupos, bloqueios ou outro tenant. `toString` censura e-mail, prova, associação e idempotência.
