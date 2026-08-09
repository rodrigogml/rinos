# Evidência 3.3.4 — Logout e revogação cross-instance

## Escopo validado

- logout programático e HTTP chamam o lifecycle global, revogam a sessão corrente e limpam contexto/cookies;
- listagem e mutações exigem usuário ativo e a referência corrente ainda ativa e vigente;
- usuário e sessões ativas são bloqueados em ordem determinística;
- a listagem pública devolve somente sessões ativas, marca a atual e não expõe cookie, hash ou IP;
- uma sessão remota, todas as outras ou todas podem ser revogadas;
- alvo de outro usuário é indistinguível de ausente e permanece intacto;
- alvo já terminal produz conclusão idempotente sem novo evento;
- uma segunda instância observa imediatamente cada revogação pelo MySQL;
- revogar a atual faz operações autenticadas posteriores falharem e o guard encerrar o contexto local.

> [!NOTE]
> A fachada de gestão está pronta, mas o provider visual do RFW só será registrado junto da reautenticação e da
> tela de segurança. Isso evita anunciar uma capability parcialmente protegida.

## Execução reproduzível

```powershell
mvn -q "-Dtest=SessionManagementFacadeImplTest,PublicContractSecurityTest,RFWAuthenticationSessionLifecycleProviderAdapterTest,RFWPersistentLoginProviderAdapterTest" "-Dit.test=AuthenticationSessionRepositoryIT" verify
```

Ambiente: Java 25, MySQL 9.7.2 e schema temporário exclusivo removido pelo harness.

## Rastreabilidade

| Tema | Prova principal |
|------|-----------------|
| Escopo público e marcador da atual | `SessionManagementFacadeImplTest` |
| Redação e ausência de tipos internos | `PublicContractSecurityTest` |
| Revogação remota/outras/todas e isolamento entre usuários | `AuthenticationSessionRepositoryIT` |
| Logout local/global e limpeza persistente | adapters RFW do lifecycle e login persistente |
