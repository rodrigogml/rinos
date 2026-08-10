# Evidência 4.2.3 — Assertion WebAuthn no orquestrador

## Fluxo entregue

1. O Spring Security valida a assertion, a credential, o challenge, o RP, a origin e a verificação local.
2. O RFW remove a autenticação do contexto e emite uma referência de uso único vinculada à sessão HTTP.
3. `RFWPasskeyAuthenticationProviderAdapter` aceita somente `WebAuthnAuthentication` com `FACTOR_WEBAUTHN`.
4. Apenas `userHandle`, instante validado e correlação técnica atravessam a fachada pública.
5. `PasskeyAuthenticationFacadeImpl` revalida owner, usuário, atualidade e disponibilidade do método.
6. O orquestrador recebe `PASSKEY` com `userVerification=true` e decide garantia, MFA e consentimento legal.
7. Somente um resultado `READY` pode seguir para o lifecycle oficial de criação e publicação da sessão.

Assertion, credential ID, chave pública, assinatura e dados do cliente não atravessam a UI nem o contrato público.

## Resolução exigida pelo Spring

O provider WebAuthn consulta um `UserDetailsService` depois de validar a credential. O adapter do Rinos resolve apenas
usuários ativos, não concede authorities e gera uma senha sintética aleatória a cada leitura. Isso atende ao contrato
técnico sem criar uma credencial estável capaz de abrir uma rota alternativa de autenticação por senha.

## Cobertura focada

```powershell
mvn "-Dtest=PasskeyAuthenticationRequestDTOTest,PasskeyAuthenticationFacadeImplTest,RFWPasskeyAuthenticationProviderAdapterTest,SpringWebAuthnUserDetailsServiceTest,AuthenticationOrchestrationServiceTest" test
```

Resultado observado: 25 testes, sem falhas, erros ou testes ignorados.

A validação integral foi executada com `mvn verify`: 571 testes unitários e 100 testes de integração sem falhas ou
erros; 15 integrações externas opcionais foram ignoradas.

| Invariante | Evidência |
|---|---|
| Tipo Spring concreto e authority WebAuthn | `RFWPasskeyAuthenticationProviderAdapterTest` |
| Handle defensivo e diagnóstico redigido | `PasskeyAuthenticationRequestDTOTest` e `PublicContractSecurityTest` |
| Owner ativo, prova atual e método ainda disponível | `PasskeyAuthenticationFacadeImplTest` |
| Passkey com UV satisfaz garantia multifator | `AuthenticationOrchestrationServiceTest` |
| Consentimento legal continua bloqueando principal | `AuthenticationOrchestrationServiceTest` e teste do adapter |
| Resolução Spring não cria senha reutilizável | `SpringWebAuthnUserDetailsServiceTest` |
