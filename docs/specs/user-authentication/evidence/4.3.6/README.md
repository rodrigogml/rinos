# Evidências da tarefa 4.3.6

## Cenários cobertos

- discovery OIDC sem resposta: o provider Google retorna `ui.access.error.externalIdentityUnavailable`;
- discovery concluído com endpoint JWKS HTTP 503: a exceção de construção lazy do decoder é convertida para
  `UNAVAILABLE`, sem chamar o resolvedor do Rinos e sem autenticação parcial;
- nonce divergente/replay: uma credencial assinada com nonce anterior é rejeitada; o componente oficial gira o nonce
  antes de publicar cada callback, portanto repetir a credencial não reutiliza a tentativa anterior;
- continuação de cadastro externa usada: a segunda submissão não repete ativação nem publica sessão;
- e-mail Google coincidente com usuário ativo: exige reautenticação e não cria vínculo automático;
- usuário bloqueado, desativado ou cancelado: a identidade externa válida não produz autenticação;
- concorrência de vínculo: duas transações que escrevem o mesmo `(issuer, subject)` produzem um único vencedor;
  a constraint MySQL rejeita o outro e a fachada traduz a corrida para conflito neutro;
- indisponibilidade Google no componente real de acesso: o login permanece em `SIGN_IN` e a senha local segue utilizável.

## Alterção isolada da RFW

O submódulo foi atualizado para `77c8e5f089fc9470aa090f111b6ab31bd8e95db5`.
`RFWGoogleIdentityProvider.verify(...)` trata falhas de runtime durante a construção lazy do decoder como falhas
temporárias do provider, preservando a distinção entre credencial inválida e dependência indisponível. A decisão
foi documentada nos seis `providers*.md` do showroom.

## Validações executadas

No RFW:

```text
mvn -q '-Dtest=RFWGoogleIdentityProviderTest,RFWGoogleIdentityProviderIT' test
Exit code: 0

mvn -q verify
Exit code: 0
```

No Rinos:

```text
mvn -q -DskipITs '-Dtest=RFWPlatformIntegrationTest,GoogleIdentityResolutionServiceTest,GoogleAuthenticationIdentityServiceTest,ExternalRegistrationCompletionServiceTest,ExternalIdentityManagementFacadeImplTest' test
Exit code: 0

mvn -q -DskipUnitTests '-Dit.test=IdentityRepositoryIT' verify
Exit code: 0
```

Os testes MySQL executaram contra o MySQL 9.7.2 local, em schema descartável gerenciado pelo harness; nenhum schema
de aplicação foi reutilizado ou destruído.
