# Evidência 5.1.1 — Providers reais no componente RFW

## Providers registrados

O component scan do Rinos registra os adapters reais abaixo, e a `RFWAccessComponentFactory` os descobre por capability:

- `RFWPasswordAuthenticationProviderAdapter` — senha;
- `RFWPasskeyAuthenticationProviderAdapter` — passkey;
- `RFWExternalIdentityResolverAdapter` — Google/identidade externa;
- `RFWHumanVerificationRequirementProviderAdapter` e a integração Turnstile — anti-bot;
- `RFWAuthenticationSessionLifecycleProviderAdapter` — preparação/publicação/validação da sessão;
- `RFWPasswordRecoveryProviderAdapter` — recuperação e redefinição.

O comportamento é o contrato documentado no showroom: bean presente ativa a capability, e a view apenas compõe o
`RFWAccessComponent` por `RinosAccessComponentFactory`.

## Validação

- `RFWPlatformIntegrationTest` confirma a descoberta de capabilities em contextos RFW e a composição da view usa a
  factory pública sem componentes paralelos.
- Os adapters possuem testes de contrato individuais em `src/test/java/br/com/rinos/app/ui/config/`.
