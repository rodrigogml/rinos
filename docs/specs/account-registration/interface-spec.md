# Especificação de Interface — Cadastro de Contas

## Superfícies

| ID | Superfície | Contexto |
|---|---|---|
| `SURF-ACC-CREATE` | criação de conta | identidade global autenticada, sem tenant ativo |
| `SURF-ACC-STATUS` | acompanhamento | protocolo pertencente ao solicitante |
| `SURF-ACC-MAINTAIN` | dados da conta | tenant explicitamente selecionado |
| `SURF-ACC-LIFECYCLE` | suspensão/cancelamento | tenant + chave + reautenticação |

O primeiro slice implementável cobre contratos de `CREATE` e `STATUS`; os demais permanecem planejados.

## RFW Platform

Foram revisados o guia Rinos/RFW, README, AGENTS, showroom e API pública. Uso obrigatório:

- `UIFactory` e `RFWButtonDefinitions` para salvar, confirmar, cancelar e tentar novamente;
- `RFWButtonVariantEnum.DANGER` para cancelamento irreversível;
- `RFWBannerComponent` para indisponibilidade, conta suspensa e atenção persistente;
- `RFWToastService` apenas para confirmações breves, nunca como única comunicação de estado;
- `RFWTurnstileComponent` com action estável `account-creation` e validação por
  `RFWHumanVerificationProvider` com a operação catalogada `ACCOUNT_CREATION`;
- protocolo `RFWReauthenticationChallengeProvider` para operações sensíveis;
- tokens, classes e ícones públicos do RFW; sem CSS estrutural paralelo.

A composição do formulário é específica do domínio Rinos. Existe uma única lacuna pública bloqueante para a prova
antiabuso, já resolvida na revisão RFW `ba1bfda`: o enum distingue criação de conta de cadastro de identidade. A
análise, compatibilidade e entrega estão registradas em [`rfw-gap-analysis.md`](rfw-gap-analysis.md).

## Jornada de criação

1. Exibir nome, moeda-base e fuso horário em uma única etapa curta.
2. Selecionar moeda e fuso por rótulos humanos pesquisáveis; códigos podem aparecer como complemento reconhecível,
   nunca como único texto.
3. Exibir resumo dos três valores e checkbox/confirmação explícita.
4. Mostrar Turnstile quando a política exigir; token nunca entra em estado serializável.
5. Ação primária fica busy durante uma submissão e impede duplo clique; a chave de idempotência permanece a mesma
   em retry da mesma intenção.
6. Aceite navega para acompanhamento usando protocolo opaco.

## Acompanhamento

Somente quatro estados públicos principais são exibidos:

| Estado | Conteúdo |
|---|---|
| Aceita | solicitação registrada e protocolo copiável |
| Preparando | infraestrutura e configuração em andamento |
| Finalizando | membership, acesso e plano em conclusão |
| Disponível | conta pode ser aberta |

`ATTENTION` é um banner seguro com tentativa posterior ou orientação de suporte; não exibe host, schema, adapter,
tentativa interna ou stack trace. Atualização pode usar polling limitado; indisponibilidade mantém o último estado
confirmado e não anuncia sucesso.

## Manutenção e lifecycle

- Cabeçalho identifica claramente a conta/tenant atual.
- Formulário usa optimistic version; conflito preserva a edição local, recarrega dados e pede reconciliação.
- Mudança de moeda/fuso apresenta impacto conhecido antes de confirmar.
- Suspender e cancelar exigem explicação, reautenticação e confirmação final; fechar/cancelar o diálogo não executa.
- Ocultar botão por autorização não substitui o gate de serviço.

## Estados e falhas

Campos possuem erro associado e resumo acessível. Motivos públicos distinguem entrada inválida, confirmação,
Turnstile, limite, autenticação recente, conflito idempotente e indisponibilidade. Nenhuma mensagem revela se existe
conta de terceiro, dados de outro tenant ou detalhes de permissão.

## Acessibilidade

- ordem de foco: título, orientação, campos, confirmação, Turnstile quando presente, ação;
- primeiro erro recebe foco; mudança de estado usa região `aria-live=polite`;
- operação completa por teclado, zoom 200%, reflow a 320 CSS px e alvos de toque adequados;
- busy não remove rótulo nem causa deslocamento; movimento respeita `prefers-reduced-motion`;
- cores não são a única indicação de estado.

## i18n e privacidade

Todo texto vem de bundles Rinos. Datas/horas usam locale corrente, persistência continua UTC. UI e telemetria nunca
registram token, IP, IDs internos, payload completo ou dados de outro tenant.

## Validação visual futura

Desktop e mobile devem cobrir: vazio, validação, Turnstile obrigatório, busy, aceite, replay, conflito, rate limit,
indisponibilidade, quatro estágios e atenção. O RFW showroom é a referência visual; eventual mudança segue o ciclo
separado descrito na análise de lacuna.
