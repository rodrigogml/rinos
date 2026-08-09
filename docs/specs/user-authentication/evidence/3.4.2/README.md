# Evidência 3.4.2 — Aceite legal atômico

## Escopo validado

- cada continuação legal possui um marcador `AuthenticationProof.LEGAL_CONSENT` aberto e de uso único;
- o digest do marcador representa a fotografia ordenada das versões obrigatórias, sem conteúdo jurídico ou token
  bruto;
- a conclusão bloqueia usuário, fluxo e marcador na ordem definida antes de reconsultar catálogo e consentimentos;
- usuário, métodos comprovados e garantia são revalidados antes de qualquer aceite;
- a submissão deve corresponder exatamente às versões obrigatórias ainda pendentes;
- seleção obsoleta não grava evidência, substitui o marcador e devolve a fotografia vigente;
- marcador e todos os aceites obrigatórios vigentes mudam na mesma transação;
- o marcador consumido impede replay, enquanto o fluxo permanece aberto exclusivamente para a publicação oficial da
  sessão;
- referências jurídicas inválidas são rejeitadas na facade sem alcançar o domínio;
- cancelamento continua idempotente e invalida fluxo e marcador sem alterar evidências anteriores.

> [!IMPORTANT]
> Há dois consumos deliberadamente distintos: esta tarefa consome o marcador do gate legal; o lifecycle da sessão
> consome depois o `AuthenticationFlow`. Consumir o fluxo durante o aceite impediria a preparação compensável da
> sessão e abriria uma janela entre consentimento e autenticação.

## Execução reproduzível

```powershell
mvn -q "-Dtest=AuthenticationProofServiceTest,AuthenticationOrchestrationServiceTest,AuthenticationConsentFacadeImplTest,PublicContractSecurityTest" "-Dit.test=AuthenticationFlowRepositoryIT" verify
```

Ambiente: Java 25, MySQL 9.7.2 e schema temporário exclusivo removido pelo harness.

## Rastreabilidade

| Tema | Prova principal |
|------|-----------------|
| Uso único do marcador validado | `AuthenticationProofServiceTest` |
| Reconsulta, aceite e seleção stale | `AuthenticationOrchestrationServiceTest` |
| Conversão/rejeição da facade | `AuthenticationConsentFacadeImplTest` |
| Persistência real: aceites + marcador usado + fluxo aberto | `AuthenticationFlowRepositoryIT` |
| Ausência de entidades/segredos na API | `PublicContractSecurityTest` |
