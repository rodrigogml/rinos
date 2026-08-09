# Evidência 3.4.3 — Política de garantia recente

## Escopo validado

- operações sensíveis aceitas são identificadas por catálogo fechado, nunca por texto livre;
- cada operação possui chave i18n humana, garantia mínima e conjunto explícito de métodos compatíveis;
- o período configurado de 15 minutos inclui exatamente o instante da fronteira e expira depois dele;
- recência exige garantia suficiente e ao menos uma evidência de sessão compatível;
- sessão Google recente não satisfaz sozinha uma operação sensível;
- somente senha, TOTP e passkey podem ser oferecidos para nova prova;
- Google, código do mesmo e-mail e recovery code são excluídos mesmo quando estão utilizáveis;
- o catálogo oferecido é a interseção entre política da operação e métodos atualmente utilizáveis;
- ausência de método compatível produz negação, não um desafio impossível;
- a decisão pura não contém usuário, sessão, `operationId` ou material de prova.

## Execução reproduzível

```powershell
mvn -q "-Dtest=ReauthenticationPolicyServiceTest" test
```

## Rastreabilidade

| Tema | Prova principal |
|------|-----------------|
| Fronteira temporal inclusiva | `ReauthenticationPolicyServiceTest` |
| Compatibilidade e filtro de canais | `ReauthenticationPolicyServiceTest` |
| Google recente não reutilizável | `ReauthenticationPolicyServiceTest` |
| Ausência de método e operação desconhecida | `ReauthenticationPolicyServiceTest` |
