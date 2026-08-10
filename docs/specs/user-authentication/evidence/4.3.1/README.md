# Evidência da tarefa 4.3.1

## Resultado

Quando `rfw.authentication.google.enabled=true`, o Rinos compõe o provider Google real da RFW com suporte JOSE e o
resolvedor da aplicação. A identidade somente cruza essa fronteira depois de o ID token passar por validação
criptográfica e protocolar.

## Contrato efetivo

- `application.properties.model` declara habilitação, client ID, issuer, timeout e clock skew com origem exclusiva no
  arquivo de propriedades;
- `spring-security-oauth2-jose` está no classpath da aplicação;
- a auto-configuração da RFW exige simultaneamente habilitação, JOSE e `RFWExternalIdentityResolver` antes de publicar
  `RFWGoogleIdentityProvider`;
- `NimbusJwtDecoder` usa discovery/JWKS do issuer e valida assinatura, issuer, audience contendo o client ID, `exp`,
  `iat`, intervalo temporal e tolerância configurada;
- o nonce recebido do componente Google deve ser igual ao claim do token;
- somente `RFWVerifiedExternalIdentityVO` alcança o adapter do Rinos; a credencial e o conjunto completo de claims não
  atravessam a API pública do domínio.

## Evolução reutilizável da RFW

O commit `696a86f` amplia o teste de integração local do provider com chave não confiável, issuer divergente e
audience divergente. A documentação correspondente foi atualizada em todos os idiomas do showroom e o RFW passou em
`mvn -q verify` isoladamente.

## Validação reproduzível

```powershell
cd modules/RFW.Platform
mvn -q -DskipITs=false "-Dit.test=RFWGoogleIdentityProviderIT" verify
mvn -q -DskipITs -DskipTests=false "-Dtest=RFWGoogleIdentityProviderTest,RFWGoogleTimestampValidatorTest,RFWGoogleAuthenticationAutoConfigurationTest" test
mvn -q verify

cd ../..
mvn -q -DskipITs -DskipTests=false "-Dtest=RFWPlatformIntegrationTest,RFWExternalIdentityResolverAdapterTest" test
mvn -q verify
```

`RFWPlatformIntegrationTest` comprova que o contexto efetivo contém um único provider Google real, expõe a capability
externa e preserva exatamente client ID, issuer, timeout e clock skew definidos pela aplicação.
