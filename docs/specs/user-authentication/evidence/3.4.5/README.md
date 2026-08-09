# Evidência 3.4.5 — Providers RFW de gate legal e reautenticação

## Escopo validado

- `RFWAuthenticationConsentProviderAdapter` conclui ou cancela a continuação legal pela facade pública e devolve o
  resultado ao lifecycle oficial do RFW;
- o token intermediário produzido depois do aceite não possui authorities, tenant ou concessões;
- `RFWReauthenticationChallengeProviderAdapter` deriva identidade e sessão somente do principal autenticado;
- a facade pública converte operação, método, referências e estados sem expor entities ou tipos do backend;
- o desafio anuncia apenas a interseção entre métodos ativos e verificadores reais disponíveis;
- senha é verificada pelo hash Argon2id da identidade autenticada, com custo sentinela para identidade ausente e
  descarte do array temporário;
- TOTP e passkey não são anunciados antes da implementação de suas cerimônias nas tarefas 4.1 e 4.2;
- a conclusão de reautenticação atualiza somente a garantia da sessão existente e não cria autenticação,
  sessão ou authority;
- a auto-configuração RFW descobre os dois providers e publica somente as capabilities correspondentes.

## Execução reproduzível

```powershell
mvn -q "-Dtest=PasswordCredentialAuthenticationServiceTest,ReauthenticationProofServiceTest,ReauthenticationServiceTest,ReauthenticationFacadeImplTest,RFWAuthenticationConsentProviderAdapterTest,RFWReauthenticationChallengeProviderAdapterTest,RFWPlatformIntegrationTest,PublicContractSecurityTest" test
```

```powershell
mvn -q verify
```

## Rastreabilidade

| Tema | Prova principal |
|------|-----------------|
| Senha vinculada à identidade e descarte da prova | `PasswordCredentialAuthenticationServiceTest` |
| Catálogo restrito a verificadores reais | `ReauthenticationProofServiceTest` |
| Conversão da fronteira pública | `ReauthenticationFacadeImplTest`, `PublicContractSecurityTest` |
| Gate legal sem authorities | `RFWAuthenticationConsentProviderAdapterTest` |
| Identidade/sessão derivadas do contexto | `RFWReauthenticationChallengeProviderAdapterTest` |
| Descoberta das capabilities RFW | `RFWPlatformIntegrationTest` |
| Consumo único e garantia da sessão | `ReauthenticationServiceTest` |
