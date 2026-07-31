# Operação e observabilidade do cadastro de usuário

Este documento define como observar o ciclo local de cadastro sem transformar dados pessoais ou
segredos em logs, métricas ou auditoria permanente. Os nomes abaixo são contratos operacionais
estáveis; alterar nomes ou tags exige avaliar dashboards, alertas e exporters consumidores.

## Métricas

| Nome lógico Micrometer | Tipo | Tags permitidas | Finalidade |
|------------------------|------|-----------------|------------|
| `rinos.registration.operations` | Counter | `operation`, `result` | Quantidade de conclusões por operação e resultado público |
| `rinos.registration.operation.duration` | Timer | `operation`, `result` | Latência integral percebida pela facade |
| `rinos.registration.lifecycle.events` | Counter | `event` | Efeitos confirmados no ciclo local |
| `rinos.registration.verification.smtp.attempts` | Counter | `result` | Tentativas de despacho posteriores ao commit |
| `rinos.registration.verification.smtp.duration` | Timer | `result` | Tempo até aceitação ou falha do servidor SMTP |

Os valores de `operation` são `start`, `resend`, `activate`, `activation_consent`,
`cancellation_request` e `cancellation_confirm`. `result` é sempre o nome normalizado de um status
público fechado; não aceita texto livre.

Os valores de `event` são:

- `pending_created`: cadastro pendente persistido, mesmo se o SMTP falhar depois do commit;
- `activated`: ativação local efetivamente concluída;
- `cancelled`: cancelamento efetivamente concluído;
- `expired`: cadastro removido pela retenção, contado depois do commit de cada lote;
- `blocked`: operação recusada por limite de origem.

> [!NOTE]
> `pending_created` mede entradas no estado, não a quantidade instantânea de pendências. A
> diferença entre contadores pode apoiar análise de tendência, mas não substitui uma consulta
> transacional quando a operação precisar do estoque atual.

O `correlationId` aparece somente no log estruturado da conclusão. Ele não é tag porque cada valor
é único e causaria cardinalidade ilimitada. E-mail, IP, senha, token, prova, identificador
persistente, conteúdo renderizado e URL secreta nunca são tags ou parâmetros de log.

## Métricas e logs não participam da decisão funcional

Uma indisponibilidade do registro de métricas não altera o resultado público nem desfaz uma
transação funcional. A auditoria persistente permanece separada: aceita apenas enums, estados e
motivos com formato de código fechado. Nas remoções terminais autorizadas, os eventos temporários
são minimizados e substituídos pelo tombstone previsto na política de retenção.

Sem uma integração externa, o Rinos usa um `MeterRegistry` em memória e não publica endpoint
administrativo. A infraestrutura pode fornecer outro registro/exporter. Os nomes apresentados pelo
backend de monitoramento podem seguir a conversão própria desse exporter, mas devem preservar o
nome lógico e as tags.

## Alertas iniciais recomendados

Os limites são configuração da plataforma de monitoramento e devem ser recalibrados com tráfego
real. Como ponto inicial:

1. Alertar quando `unavailable` ou `unexpected_failure` ocorrer continuamente por cinco minutos.
2. Alertar quando falhas SMTP (`template_failure` + `transport_failure`) superarem 5% em 15 minutos,
   desde que tenham ocorrido pelo menos 20 tentativas.
3. Alertar quando bloqueios ultrapassarem três vezes a linha de base do mesmo horário nos últimos
   sete dias, com no mínimo 20 ocorrências na janela.
4. Alertar por log quando qualquer tarefa do catálogo de limpeza falhar; ausência de expirações,
   isoladamente, não é falha porque pode não haver pendências vencidas.
5. Alertar sobre perda de lease ou ausência de renovação por mais de dois intervalos de heartbeat.

Dashboards devem exibir taxa e percentis de duração por operação, resultados SMTP, eventos do
ciclo e eventos do lease de manutenção. Não devem incluir filtros por identidade, endereço de
origem ou correlation ID como dimensão de métrica.

## Limites de responsabilidade

- A aplicação mede até o servidor SMTP aceitar ou rejeitar a mensagem. Entrega na caixa postal,
  bounce, atraso e spam dependem de eventos do provedor e não são inferidos pelo Rinos.
- A aplicação produz métricas e logs sanitizados; armazenamento, exporter, retenção, dashboards e
  alertas são responsabilidades da infraestrutura.
- A aplicação executa retenção e expiração somente sob lease válido. Backup, restauração e
  recuperação de infraestrutura permanecem externos.
- Métricas são evidência operacional agregada, não fonte de verdade funcional nem trilha de
  auditoria por usuário.
