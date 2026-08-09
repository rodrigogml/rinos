# Evidência 3.4.4 — Retomada única da operação sensível

## Escopo validado

- o desafio reutiliza `AuthenticationFlow` com finalidade `REAUTHENTICATION`, validade de cinco minutos e referência
  bruta nunca persistida;
- `identity_reauthenticationContext` vincula de forma imutável o fluxo opaco à sessão corrente e a uma operação do
  catálogo fechado;
- a conclusão bloqueia e revalida identidade, fluxo, contexto, sessão, vencimentos e disponibilidade atual do método;
- uma referência apresentada por outra identidade ou sessão não é consumida;
- somente uma prova previamente validada por senha, TOTP ou passkey pode ser registrada pelo coordenador;
- a conclusão consome o fluxo uma única vez, acrescenta evidência sanitizada e atualiza apenas
  `lastStrongAuthAt`/`assuranceLevel` da sessão vinculada;
- nenhuma sessão ou authority é criada durante a reautenticação;
- a UI retoma uma vez o supplier original; a mutação retomada relê seu alvo e revalida versão e invariantes, sem
  callback ou payload persistido no desafio;
- init limpo e cadeia incremental produzem o mesmo schema global na versão `20260809002`.

## Execução reproduzível

```powershell
mvn -q "-Dtest=ReauthenticationPolicyServiceTest,ReauthenticationServiceTest,GlobalDatabaseUpdateCatalogTest" `
  "-Dit.test=AuthenticationDatabaseSchemaIT,GlobalDatabaseMigrationIT" verify
```

## Rastreabilidade

| Tema | Prova principal |
|------|-----------------|
| Emissão vinculada a sessão e operação | `ReauthenticationServiceTest` |
| Consumo e atualização exclusiva da sessão | `ReauthenticationServiceTest` |
| Recusa de outra sessão sem consumo | `ReauthenticationServiceTest` |
| Catálogo e garantia recente | `ReauthenticationPolicyServiceTest` |
| Equivalência init/update e constraints MySQL | `AuthenticationDatabaseSchemaIT` |
| Bootstrap desde versões anteriores | `GlobalDatabaseMigrationIT` |
