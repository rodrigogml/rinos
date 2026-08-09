# Evidência 3.4.1 — Continuação legal opaca

## Escopo validado

- o gate só é alcançado depois que a garantia de autenticação exigida foi satisfeita;
- o catálogo vigente e os aceites imutáveis existentes determinam somente as versões obrigatórias pendentes;
- a continuação original de `SIGN_IN` é encerrada antes da emissão da continuação `LEGAL_CONSENT`;
- a nova continuação preserva usuário, método primário, métodos comprovados, garantia, correlação, validade e a escolha
  de login persistente;
- nenhum principal autenticado é exposto enquanto houver aceite pendente;
- a referência aleatória bruta é entregue uma única vez e somente seu SHA-256 é persistido no MySQL;
- indisponibilidade ou inconsistência do catálogo falha fechada sem produzir continuação autenticada.

> [!NOTE]
> A própria referência opaca de `AuthenticationFlow` é a continuação legal definida pelo contrato. Uma segunda
> credencial não aumentaria a proteção e criaria dois lifecycles concorrentes para o mesmo gate. A tarefa 3.4.2
> continua responsável pelo registro atômico dos aceites e pelo consumo dessa referência.

## Execução reproduzível

```powershell
mvn -q "-Dtest=AuthenticationOrchestrationServiceTest" "-Dit.test=AuthenticationFlowRepositoryIT" verify
```

Ambiente: Java 25, MySQL 9.7.2 e schema temporário exclusivo removido pelo harness.

## Rastreabilidade

| Tema | Prova principal |
|------|-----------------|
| Decisão pelo catálogo e troca do fluxo | `AuthenticationOrchestrationServiceTest` |
| Ausência de principal antes do aceite | `AuthenticationOrchestrationServiceTest` |
| Digest persistido e fotografia preservada | `AuthenticationFlowRepositoryIT` |
| Consumo único e locks pessimistas da referência | `AuthenticationFlowRepositoryIT` |
