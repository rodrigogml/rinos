# Evidência parcial da tarefa 7.2.6

## Resultado

Em 31 de julho de 2026, a revisão comprovou que os fluxos de ativação disponíveis
não criam tenant, conta, plano, associação, grupo, papel, chave ou permissão. Também
comprovou que a autenticação produzida pela conclusão Google usa um principal mínimo
e nenhuma autoridade implícita.

O gate completo ainda não pode ser declarado concluído. A autenticação de sessões
pertence a `user-authentication`, e o conteúdo e as operações do Painel pertencem a
`user-dashboard`; essas features ainda não oferecem uma rota autenticada sobre a qual
seja possível testar o acesso do próprio usuário e a negação de dados alheios.

## Fronteiras comprovadas

### Persistência global

O catálogo SQL atual cria somente estruturas de plataforma e identidade:

- `platform_maintenanceLease`;
- `identity_user`;
- `identity_registration`;
- `identity_localCredential`;
- `identity_verification`;
- `identity_legalDocumentVersion`;
- `identity_legalConsent`;
- `identity_externalIdentity`;
- `security_originWindow`;
- `identity_event`.

Não existe tabela, coluna ou migration de tenant, conta, plano, associação, grupo,
papel, chave ou permissão nesta feature. Todos os dados de cadastro pertencem ao
schema global e nenhuma tabela possui `tenantId`.

### Ativação local e Google

`RegistrationActivationService` depende somente de serviços de comprovação,
consentimento, lifecycle e auditoria da identidade. A ativação local:

1. consome a comprovação;
2. transiciona `User` e `Registration` para `ACTIVE`;
3. invalida comprovações abertas e eventual vínculo externo pendente;
4. registra os eventos de auditoria.

`ExternalRegistrationCompletionService` acrescenta somente a substituição da
credencial local pelo vínculo Google verificado. Nenhum dos dois fluxos possui porta,
repositório ou chamada capaz de provisionar tenant ou conceder acesso de conta.

### Principal e autoridades

`RinosUserPrincipalVO` contém exclusivamente `userId` e e-mail. Seu contrato proíbe
entity, credencial, vínculo externo, tenant ou concessões. O adapter da conclusão
Google cria um `UsernamePasswordAuthenticationToken` autenticado com lista vazia de
autoridades.

As únicas rotas atuais são `/login` e `/legal-document/**`, ambas públicas por
necessidade do cadastro. A configuração de segurança mantém qualquer rota futura
protegida por autenticação por padrão, mas a autorização específica de conta deverá
ser acrescentada pelas features que introduzirem tais superfícies.

## Validação executada

Comando:

```powershell
mvn "-Dtest=RegistrationActivationServiceTest,ExternalRegistrationCompletionServiceTest,RFWExternalRegistrationProviderAdapterTest,PublicContractSecurityTest" test
```

Resultado literal:

```text
Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Os testes cobrem transições da ativação local, conclusão transacional Google,
publicação do principal somente depois do caso de uso concluído, ausência de
autoridades e proteção dos contratos públicos contra tipos de persistência e
diagnósticos com dados sensíveis.

## Condições para concluir o gate

A tarefa poderá mudar de bloqueada para concluída quando existirem evidências reais
para todas as condições abaixo:

1. `user-authentication` criar e restaurar a sessão usando a identidade global sem
   papel, grupo, chave ou permissão implícita;
2. `user-dashboard` oferecer a rota autenticada e consultar somente configurações da
   identidade representada pela própria sessão;
3. acessos anônimos, de outro usuário e a qualquer tenant ou conta não concedida
   forem rejeitados em testes de integração;
4. cadastro local e Google concluírem a navegação até o Painel sem criar registros de
   tenant, conta, plano ou associação no banco;
5. os cenários 1 e 17 do `quickstart.md` forem executados de ponta a ponta.

> [!IMPORTANT]
> Criar uma rota fictícia ou uma autoridade temporária apenas para fechar este gate
> antecipadamente ampliaria o escopo de `user-registration` e produziria uma falsa
> garantia de autorização. O bloqueio deve permanecer visível até que as superfícies
> reais existam.
