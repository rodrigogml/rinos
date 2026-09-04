# Evidências da fase 6.1 — fundação

**Data**: 2026-08-16
**Estado**: 6.1.1 concluída; 6.1.2 e 6.1.3 permanecem abertas

## Integração concluída

- `TenantFoundationOperations` publica operações tipadas para conta, membership, plano e auditoria;
- `MembershipFacade` constrói internamente a requisição canônica e autoriza antes de convite, reenvio, revogação ou
  lifecycle persistente;
- `SpringMembershipFacadeAdapter` deriva ator, associação, tenant e garantia da `UI` exata;
- requests de navegador não contêm ator, tenant, associação, garantia nem código de chave;
- lifecycle usa `tenant.membership.manage` como operação sensível; convite usa `tenant.membership.invite`;
- papel colaborador, contador, parceiro ou administrador não modifica a chave exigida nem concede acesso;
- negação por falta de permissão ou bloqueio impede qualquer interação com os serviços persistentes.

## Contrato preparado para os próximos consumidores

`TenantPartyOperations` contém as operações canônicas já documentadas para cadastro de pessoas, relacionamentos e dados
de pagamento. Revelações completas são compostas: a leitura base e a chave específica precisam ser permitidas.

Os módulos persistentes `party-registration`, `party-relationships-roles` e `party-payment-details` ainda não existem no
repositório. Por isso a subtarefa 6.1.2 não foi marcada como concluída: o catálogo está pronto, mas não há serviço real
ao qual conectar a decisão. Isso é dependência de implementação, não ambiguidade documental nem motivo para criar um
adapter que simule persistência inexistente.

## Testes

- `MembershipFacadeImplTest`: negação antes da persistência, chave canônica, tenant exato e mutação sensível;
- `TenantConsumerOperationsTest`: escopo, operações compostas e ausência de chaves globais/fundação em pessoas;
- `MembershipPublicContractTest`: imutabilidade, isolamento de pacotes e ausência de campos confiáveis no request.

Validação completa: `mvn verify` aprovado com 745 testes unitários e 133 testes de integração/MySQL, sem falhas ou
erros; 18 integrações permaneceram ignoradas por condições documentadas do ambiente.
