# Evidência 3.4.6 — Bordas do gate legal e da reautenticação

## Escopo validado

- uma versão legal obrigatória publicada enquanto o gate está aberto substitui a seleção antiga, não grava aceite
  parcial e volta ao RFW sem autenticação;
- recusar/fechar o gate cancela a continuação e não altera evidências legais anteriores;
- uma prova de senha recusada não inspeciona, consome ou atualiza fluxo e sessão;
- um desafio expirado volta como `EXPIRED` e um método removido durante o diálogo volta como `CONFLICT`, sem atualizar
  garantia nem consumir a continuação;
- Google seguido de código enviado ao mesmo e-mail continua classificado como fator único; TOTP/passkey independentes
  permanecem necessários para a garantia administrativa;
- o componente RFW executa o supplier da operação original somente depois de `COMPLETED`, uma única vez; expiração,
  conflito ou cancelamento não executam a mutação;
- depois da reautenticação, a operação original revalida seu alvo. Um outcome `STALE`/`CONFLICT` solicita refresh
  apenas da seção afetada e não é reaplicado automaticamente.

## Execução reproduzível no Rinos

```powershell
mvn -q "-Dtest=ReauthenticationServiceTest,RFWReauthenticationChallengeProviderAdapterTest,RFWAuthenticationConsentProviderAdapterTest,AuthenticationOrchestrationServiceTest,AuthenticationAssurancePolicyServiceTest" test
```

```powershell
mvn -q verify
```

## Execução reproduzível do contrato RFW consumido

```powershell
Set-Location modules/RFW.Platform
mvn -q "-Dtest=RFWSecuritySettingsComponentTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false" test
```

## Rastreabilidade

| Tema | Prova principal |
|------|-----------------|
| Nova versão legal concorrente | `AuthenticationOrchestrationServiceTest.completeLegalConsent_shouldRefreshChallengeWithoutRecording_whenCatalogSelectionIsStale` |
| Nova versão atravessa o provider sem autenticar | `RFWAuthenticationConsentProviderAdapterTest.completeAuthenticationConsent_shouldPresentNewRequiredVersionWithoutAuthentication` |
| Recusa/cancelamento legal | `AuthenticationOrchestrationServiceTest.cancel_shouldFallbackToLegalFlowAndRemainIdempotent`, `RFWAuthenticationConsentProviderAdapterTest.cancelAuthenticationConsent_shouldCancelOpaqueContinuation` |
| Prova recusada sem efeito | `ReauthenticationServiceTest.complete_shouldRejectInvalidProofWithoutInspectingOrMutatingFlow` |
| Timeout e método revogado | `ReauthenticationServiceTest`, `RFWReauthenticationChallengeProviderAdapterTest` |
| Google e e-mail no mesmo canal | `AuthenticationAssurancePolicyServiceTest.calculate_shouldNotCountGoogleAndEmailAsIndependentChannels` |
| Retomada única, cancelamento, expiração e conflito | `RFWSecuritySettingsComponentTest` no RFW Platform |
| Outcome stale e refresh dirigido | `RFWSecuritySettingsComponentTest.staleMutation_shouldPublishOutcomeAndRefreshOnlyAffectedSection` no RFW Platform |
