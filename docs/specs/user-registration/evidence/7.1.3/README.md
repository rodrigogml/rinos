# Matriz Google simulada e persistência real

Data da execução: 2026-08-02

## Limite da simulação

O RFW simula a autenticação concluída pelo Google e entrega ao Rinos somente emissor, `subject` e e-mail já
verificado. Tokens, `nonce` e claims completos não atravessam a API pública do Rinos. A partir dessa fronteira, os
serviços de domínio, as transações e a persistência exercitados são reais.

## Estados comprovados

| Estado encontrado | Decisão e efeito persistente |
|-------------------|------------------------------|
| identidade e e-mail novos | cria usuário e processo Google pendentes, vínculo externo pendente e referência opaca de continuação |
| cadastro local pendente | reutiliza a mesma raiz, preserva a credencial local até o aceite e troca apenas a identidade externa candidata |
| usuário ativo com o mesmo e-mail | exige reautenticação local e não cria vínculo, prova ou nova raiz implicitamente |

A conclusão Google também foi reexecutada: somente após os aceites atuais ela ativa a raiz e o vínculo externo,
remove a credencial local na mesma transação e publica um principal autenticado oriundo do commit.

## Execuções

1. `mvn "-Dit.test=IdentityRepositoryIT" verify`
   - MySQL Community Server 9.7.2;
   - 28 testes de persistência, incluindo os três estados acima e a conclusão Google;
   - nenhuma falha, erro ou omissão.
2. `mvn "-Dtest=GoogleIdentityResolutionServiceTest,ExternalRegistrationCompletionServiceTest,GoogleIdentityResolutionFacadeImplTest,ExternalRegistrationFacadeImplTest,RFWExternalIdentityResolverAdapterTest,RFWExternalRegistrationProviderAdapterTest" test`
   - 31 testes de domínio, facades e adapters com a fronteira Google simulada;
   - nenhuma falha, erro ou omissão.
3. `mvn "-Drinos.ui.e2e.enabled=true" "-Dit.test=RegistrationViewE2EIT#googleRegistration_shouldAuthenticateAndReachUserDashboard_onDesktop+googleRegistration_shouldReflowAndComplete_onPhone" verify`
   - duas jornadas Chromium, desktop e telefone;
   - conclusão autenticada, navegação ao painel e reflow responsivo aprovados.

> [!IMPORTANT]
> Não há associação automática de um usuário ativo pelo simples fato de o Google informar o mesmo e-mail. A
> reautenticação local continua sendo o requisito explícito para um vínculo posterior.
