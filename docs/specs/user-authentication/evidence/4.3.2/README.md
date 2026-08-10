# Evidência da tarefa 4.3.2

## Resultado

A localização de uma identidade Google para login usa exclusivamente a combinação validada de issuer e subject. O
e-mail Google não participa da assinatura do serviço, da consulta ao repository ou da decisão de correspondência.

## Decisões

- vínculo `ACTIVE` com usuário `ACTIVE` produz `MATCHED` e somente então entrega o identificador interno ao próximo
  boundary;
- vínculo ausente produz `NOT_FOUND` sem consultar usuário por e-mail nem criar vínculo;
- vínculo `PENDING` ou proprietário `PENDING_VERIFICATION`, `BLOCKED`, `DEACTIVATED` ou `CANCELLED` produz `REJECTED`
  sem expor usuário;
- `NOT_FOUND` permanece distinto somente no backend para que a camada de composição possa oferecer continuação segura
  de cadastro; a resposta anônima não deve revelar essa distinção;
- a chave física `(issuer, subject)` já possui unicidade no MySQL e consulta própria com lock pessimista.

Como o resolvedor não recebe e-mail, uma mudança de e-mail no Google não altera o e-mail principal do Rinos. Da mesma
forma, coincidência entre o e-mail verificado pelo Google e uma conta existente não concede login nem cria associação.

## Validação reproduzível

```powershell
mvn -q -DskipITs -DskipTests=false "-Dtest=GoogleAuthenticationIdentityServiceTest,ExternalIdentityServiceTest" test
mvn -q -DskipITs=false "-Dit.test=IdentityRepositoryIT" verify
mvn -q verify
```

`GoogleAuthenticationIdentityServiceTest` cobre correspondência nominal, ausência, vínculo pendente e todos os estados
não ativos do usuário. `IdentityRepositoryIT` mantém as provas de unicidade e concorrência da chave externa estável.
