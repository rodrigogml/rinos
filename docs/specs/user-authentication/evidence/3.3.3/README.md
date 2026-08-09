# Evidência 3.3.3 — Guard global e atividade amortizada

## Escopo validado

- o `RFWAuthenticationSessionValidationFilter` consulta o lifecycle do Rinos antes de toda requisição que já
  contenha autenticação não anônima;
- requests e heartbeats do Vaadin usam a mesma cadeia Spring Security, sem scheduler ou estado paralelo;
- sessão e usuário são revalidados no banco global para observar revogação cross-instance;
- expiração absoluta e por inatividade são verificadas antes da renovação;
- atingir exatamente o limite expira a sessão;
- atividade anterior a cinco minutos não altera os timestamps persistidos;
- a primeira atividade no limite renova `lastActivityAt` e aplica
  `min(now + idleTimeout, absoluteExpiresAt)`;
- indisponibilidade da autoridade global bloqueia com HTTP 503 sem revogar estado potencialmente válido.

## Execução reproduzível

```powershell
mvn -q "-Dtest=AuthenticationSessionLifecycleServiceTest,RFWAuthenticationSessionLifecycleProviderAdapterTest" test
```

Os testes de filtro e serviço de sessão pertencem ao gate completo do RFW e foram novamente exercitados pela suíte
completa do Rinos, que consome a versão instalada do submódulo.

## Rastreabilidade

| Tema | Prova principal |
|------|-----------------|
| Intervalo amortizado e fronteiras temporais | `AuthenticationSessionLifecycleServiceTest` |
| Tradução de estados persistentes | `RFWAuthenticationSessionLifecycleProviderAdapterTest` |
| Validação por request, 503 e limpeza terminal | `RFWAuthenticationSessionValidationFilterTest` no RFW |
