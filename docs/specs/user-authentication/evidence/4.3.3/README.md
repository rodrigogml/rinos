# Evidência da tarefa 4.3.3

## Resultado

Uma identidade Google vinculada e ativa agora inicia o mesmo fluxo persistente usado por senha e passkey. O
orquestrador continua sendo a única autoridade sobre garantia multifator, aceite legal e conclusão anterior à criação
da sessão.

## Decisões comprovadas

- issuer e subject já validados são os únicos dados externos entregues à fachada de login; e-mail, ID token e claims
  não atravessam a fronteira;
- vínculo ausente pode continuar o cadastro externo, enquanto vínculo existente inválido recebe rejeição neutra e
  nunca cai no cadastro;
- após Google, `EMAIL_CODE` é removido do catálogo de segundo fator; TOTP e passkey permanecem alternativas
  independentes e recovery code continua disponível como recuperação do 2FA voluntário;
- a presença isolada de passkey não ativa 2FA voluntário, mas uma passkey escolhida em desafio já aberto pode elevar
  a garantia com `userVerification=true`;
- a passkey de segundo fator somente avança o fluxo quando o owner WebAuthn corresponde ao usuário da continuação;
- `LEGAL_CONSENT_REQUIRED` é transportado ao RFW sem autenticação parcial ou sessão;
- autenticação Google não concede papel ou permissão. A futura feature de autorização deve identificar o acesso
  administrativo e exigir TOTP ou passkey verificada, nunca e-mail do mesmo canal.

## Evolução reutilizável da RFW

O commit `c143861` faz o renderer de segundo fator executar WebAuthn quando `PASSKEY` for escolhido e acrescenta o
método compatível `RFWPasskeyAuthenticationProvider.authenticateSecondFactor(...)`. A implementação padrão rejeita
de forma segura; cada hospedeira precisa validar a propriedade da passkey antes de avançar sua continuação. O contrato
foi documentado nos seis idiomas do showroom e validado isoladamente.

## Validação reproduzível

```powershell
cd modules/RFW.Platform
mvn -q "-Dtest=RFWAccessComponentTest,RFWDefaultAccessStepRendererTest" test
mvn -q verify

cd ../..
mvn -q "-Dtest=GoogleAuthenticationFacadeImplTest,RFWExternalIdentityResolverAdapterTest,AuthenticationSecondFactorPolicyServiceTest,PasskeyAuthenticationFacadeImplTest,RFWPasskeyAuthenticationProviderAdapterTest,PasswordAuthenticationFacadeImplTest,SecondFactorServiceTest,PublicContractSecurityTest,PublicFacadeContractTest" test
mvn -q verify
```

Os testes da fachada cobrem vínculo ausente, vínculo rejeitado, filtro do mesmo canal, desafio TOTP/passkey e gate
legal. Os testes dos adapters cobrem o mapeamento RFW, a continuidade exclusiva de cadastro, a propriedade do fluxo e
a ausência de autenticação parcial.
