# Evidência 4.1.5 — Seleção contextual do segundo fator

## Escopo validado

- senha oferece somente TOTP, código por e-mail e recovery code que estejam ativos no instante do desafio;
- passkey persistida não é anunciada antes de existir verificador real da fase 4.2;
- primeiro fator Google nunca oferece nem aceita `EMAIL_CODE` do mesmo endereço;
- o catálogo é recalculado antes do consumo, impedindo uso de fator revogado depois da criação do fluxo;
- TOTP, OTP por e-mail e recovery code avançam a mesma continuação; o último recovery code consumido ainda conclui
  sua tentativa;
- prova e avanço pertencem à mesma transação e um avanço terminalmente inconsistente reverte o consumo;
- falhas de TOTP/recovery contam no limite do fluxo e falhas de OTP já contabilizadas não são duplicadas;
- o limite máximo invalida o fluxo e suas provas abertas;
- o provider RFW declara apenas e-mail como método de emissão, não envia automaticamente ao abrir o challenge e
  preserva TOTP/recovery como métodos sem emissão;
- DTOs e outcomes não expõem prova, destinatário completo ou referência opaca em representação textual.

## Decisões de composição

`AuthenticationSecondFactorPolicyService` é a única política de catálogo implementado. Ela não conhece papel,
grupo, chave ou tenant: exigência administrativa futura poderá solicitar garantia multifator, mas autorização
continua fora desta feature. A mesma regra remove o e-mail depois de Google, enquanto `EmailOtpService` aplica uma
segunda defesa na autoridade que emite e consome a prova.

`SecondFactorService` obedece à ordem de lock usuário → fluxo → fator. O estado persistido do desafio é apenas o
limite superior: o método também precisa continuar ativo e suportado no instante do uso. O provider
`RFWSecondFactorProviderAdapter` usa os contratos públicos existentes na baseline RFW
`911aa5d94c88d4760aed7c72e34bc56ee57284fb`; nenhuma alteração no submódulo foi necessária.

## Execução reproduzível

```powershell
mvn -q "-Dtest=AuthenticationSecondFactorPolicyServiceTest,SecondFactorServiceTest,PasswordAuthenticationFacadeImplTest,RFWSecondFactorProviderAdapterTest,EmailOtpServiceTest" test
```

```powershell
mvn -q verify
```

## Rastreabilidade

| Tema | Prova principal |
|------|-----------------|
| Catálogo implementado e defesa de mesmo canal | `AuthenticationSecondFactorPolicyServiceTest` |
| Revalidação, consumo e avanço dos três fatores | `SecondFactorServiceTest` |
| Oferta correta depois de senha | `PasswordAuthenticationFacadeImplTest` |
| Emissão explícita, reenvio e redação na borda RFW | `RFWSecondFactorProviderAdapterTest` |
| Defesa adicional e tentativa única do OTP | `EmailOtpServiceTest` |
