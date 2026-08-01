# Evidência da tarefa 6.3.2

Data da validação: 2026-08-01

## Escopo

A continuação do cadastro Google foi validada desde o resultado público produzido pelo adapter real do Rinos até a
etapa `EXTERNAL_REGISTRATION` do `RFWAccessComponent`. A implementação de produção já existia nos contratos e no
renderer do RFW; a tarefa consolidou sua integração na aplicação hospedeira sem criar formulário ou componente
paralelo.

Esta tarefa cobre a composição da etapa e a imutabilidade visual do e-mail verificado. A seleção dos documentos e a
ausência de senha ou dados adicionais pertencem à tarefa 6.3.3. Conclusão, autenticação e navegação pertencem à 6.3.4;
form factors e inspeção visual completa permanecem na 6.3.7.

## Fluxo comprovado

O teste integrado usa os adapters concretos do Rinos e os contratos públicos do RFW:

```text
identidade Google validada
  → RFWExternalIdentityResolverAdapter
  → GoogleIdentityResolutionFacade
  → RFWAuthenticationOutcomeVO.externalRegistrationRequired
  → RFWExternalRegistrationChallengeVO
  → RFWAccessComponent.openExternalRegistration
  → etapa EXTERNAL_REGISTRATION
  → EmailField verificado e somente leitura
```

O resultado público do domínio fornece somente `registrationReference`, `providerId`, `verifiedEmail` e `expiresAt`.
O adapter converte esses valores para a challenge tipada do RFW. O componente mantém a referência no estado do fluxo
e renderiza o e-mail verificado como `EmailField` com `readOnly=true`; o navegador não recebe uma operação capaz de
substituir o e-mail no pedido de conclusão.

O `RFWExternalRegistrationProviderAdapter` real foi registrado no contexto do teste, comprovando que a capability
`EXTERNAL_REGISTRATION` decorre de um provider efetivo do Rinos. A UI continua dependente somente da facade pública e
não acessa backend, entity ou repository.

## Validação automatizada

O cenário adicionado a `RFWPlatformIntegrationTest` comprova que:

- `EXTERNAL_IDENTITY` e `EXTERNAL_REGISTRATION` são descobertas com os adapters reais;
- o resultado da resolução possui status `EXTERNAL_REGISTRATION_REQUIRED` e preserva a expiração tipada;
- a continuação abre exatamente a etapa `EXTERNAL_REGISTRATION`;
- existe um único campo de e-mail;
- o valor apresentado é o e-mail verificado da challenge;
- o campo está bloqueado para edição.

Teste focal:

```powershell
mvn -Dtest=RFWPlatformIntegrationTest test
```

Resultado:

```text
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Gate completo:

```powershell
mvn verify
```

Resultado com Java 25 e MySQL 9.7.2:

```text
Testes unitários:      293; 0 falhas; 0 erros; 0 ignorados
Testes de integração:   49; 0 falhas; 0 erros; 4 E2E de navegador opt-in
BUILD SUCCESS
```

Os quatro E2E opt-in são as jornadas de navegador existentes. A inspeção visual e responsiva da continuação Google
será executada na tarefa 6.3.7, evitando declarar antecipadamente o gate de form factors.

## Conclusão

O Rinos compõe a continuação Google pelo renderer público do RFW, apresenta o e-mail comprovado pelo provedor e impede
sua edição. A tarefa 6.3.2 está concluída sem mudança no submódulo RFW.
