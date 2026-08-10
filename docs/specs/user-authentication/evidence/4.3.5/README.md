# Evidências da tarefa 4.3.5

## Escopo comprovado

- o ID token permanece uma credencial efêmera dentro do validador Google da RFW;
- o Rinos recebe somente a identidade já validada e reduz o contrato de autenticação a `issuer`, `subject`,
  instante de validação e correlação;
- `identity_externalIdentity` persiste exclusivamente a referência opaca, propriedade, provedor, `issuer`, `subject`,
  estado e marcos temporais do vínculo;
- o schema não possui coluna de ID token, access token, refresh token ou mapa de claims;
- a indisponibilidade do provider Google resulta em erro recuperável na própria etapa de login;
- depois desse erro, o provider local por senha continua disponível e recebe normalmente uma nova submissão.

## Fronteira de credenciais

O contrato público da RFW recebe a credencial Google apenas em `RFWExternalIdentityRequestDTO`. Depois da validação
criptográfica, `RFWExternalIdentityResolverAdapter` entrega à fachada de autenticação somente os identificadores
estáveis. O e-mail verificado é usado apenas na continuação transitória de um cadastro novo; credencial e mapa de
claims não atravessam essa API.

`AuthenticationDatabaseSchemaIT.externalIdentity_shouldPersistOnlyStableIdentityMetadata` fixa a lista completa de
colunas persistentes do vínculo. A adição futura de qualquer campo exige revisão explícita desse gate e o teste rejeita
nomes relacionados a token ou claims.

## Fallback independente

`RFWPlatformIntegrationTest.login_shouldKeepPasswordAvailable_afterExternalIdentityFailure` monta o componente real de
acesso com providers externos e locais independentes. O cenário devolve indisponibilidade no Google, confirma que a
tela permanece em `SIGN_IN` e, em seguida, submete senha e comprova a chamada da fachada local. Nenhuma tentativa
automática de fallback reutiliza a credencial externa.

## Validações executadas

```text
mvn -q -DskipITs '-Dtest=RFWPlatformIntegrationTest' test
Exit code: 0

mvn -q -DskipUnitTests '-Dit.test=AuthenticationDatabaseSchemaIT' verify
Exit code: 0
```

> [!NOTE]
> Esta tarefa não alterou o submódulo RFW. A validação detalhada de JWKS indisponível e dos demais cenários
> adversariais permanece reservada à tarefa 4.3.6.
