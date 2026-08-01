# Evidência da tarefa 6.3.4

Data da validação: 2026-08-01

## Escopo

A continuação real do cadastro Google agora encerra o fluxo com uma identidade autenticada e navega para a rota
global protegida `/user`. A integração reutiliza o serviço de sessão e o callback públicos do RFW; o Rinos não cria
uma segunda forma de publicar a autenticação.

Esta tarefa reserva somente a entrada do Painel de Usuário. Conteúdo, dados e operações do painel continuam fora do
escopo de `user-registration` e pertencem à feature `user-dashboard`.

## Contrato comprovado

O retorno autenticado percorre a seguinte cadeia:

```text
RFWExternalRegistrationRequestDTO
  → RFWExternalRegistrationProviderAdapter
  → ExternalRegistrationFacade.complete
  → transação confirmada com RinosUserPrincipalVO
  → RFWAuthenticationOutcomeVO.AUTHENTICATED
  → RFWAuthenticationSessionService.completeAuthentication
  → callback onAuthenticated
  → UserDashboardEntryView em /user
```

O RFW publica a autenticação na sessão antes de executar o callback. A navegação do Rinos ocorre exclusivamente pelo
`onAuthenticated`, após o retorno autenticado do provider. A rota de destino usa `@PermitAll`, não possui
`@AnonymousAllowed` e, portanto, exige uma identidade autenticada pelo controle de acesso do Vaadin.

`UserDashboardEntryView` não renderiza conteúdo, consulta dados nem oferece operações. Também não cria tenant, conta,
papel, grupo ou concessão de acesso. Essa separação evita antecipar o desenho do Painel e mantém o cadastro limitado à
identidade global do usuário.

## Validação automatizada

O teste integrado `RFWPlatformIntegrationTest` usa o provider real do Rinos e comprova que:

- a facade devolve o principal criado pela conclusão Google;
- o componente entrega uma autenticação válida ao serviço de sessão do RFW;
- a sessão é publicada antes de o callback navegar;
- a navegação termina em `UserDashboardEntryView`.

`UserDashboardEntryViewTest` comprova separadamente que `/user` é uma rota protegida e não anônima.

Teste focal:

```powershell
mvn "-Dtest=RFWPlatformIntegrationTest,UserDashboardEntryViewTest" test
```

Resultado:

```text
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Gate completo:

```powershell
mvn verify
```

Resultado com Java 25 e MySQL 9.7.2:

```text
Testes unitários:      294; 0 falhas; 0 erros; 0 ignorados
Testes de integração:   49; 0 falhas; 0 erros; 4 E2E de navegador opt-in
BUILD SUCCESS
```

Os quatro E2E ignorados são jornadas de navegador opt-in já existentes. A inspeção visual e responsiva da continuação
Google permanece na tarefa 6.3.7.

## Compatibilidade com o RFW 2.0

Antes desta implementação, o submódulo foi atualizado para o commit
`6f953eee2310a0c9fd312cdecf0a2f560f07df36`. O Rinos migrou para os namespaces `br.eng.rodrigogml.rfw.*`, as
propriedades `rfw.*`, o recurso estático `/rfw/` e as auto-configurações independentes da versão consolidada. O RFW
foi validado isoladamente com 297 testes, sem falhas, antes da validação da aplicação hospedeira.

## Conclusão

A tarefa 6.3.4 está concluída. A continuação Google confirma a identidade, publica a sessão pelo RFW e navega para
`/user`, sem antecipar qualquer capacidade do futuro Painel de Usuário.
