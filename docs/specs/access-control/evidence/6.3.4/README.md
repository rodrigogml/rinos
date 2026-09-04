# Evidências da fase 6.3.4 — validação manual de acessibilidade

- **Status:** bloqueada, sem validação manual declarada.
- **Registro inicial:** 2026-09-03; atualizado em 2026-09-04.

## Escopo que permanece pendente

A tarefa exige uma execução interativa, com dados reais de uma sessão de teste, da central ACL global e de tenant e
dos diálogos de explicação e prévia de mudança. A validação deve cobrir teclado, ordem e retorno de foco,
mensagens `aria-live`, reflow e ao menos um leitor de tela.

As verificações automatizadas registradas em
[../6.3.3/README.md](../6.3.3/README.md) continuam úteis, mas não substituem esse ensaio manual.

## Sondas realizadas

| Sonda | Resultado factual |
| --- | --- |
| Porta local configurada (`localhost:7070`) | Não havia processo escutando na porta. |
| Rota publicada `/system/access` | Redirecionou para `/login`, como esperado sem autenticação. |
| Estado visual do login publicado | Em 2026-09-04, a tela foi reinspecionada após o deploy e não apresenta mais os textos de recepção suprimidos pelo Rinos. A inspeção não substitui os cenários autenticados desta tarefa. |
| Serviço `rinos` no Turing | Em 2026-09-04, o JAR atual foi conferido por SHA-256 antes da instalação; a unidade foi reiniciada, ficou ativa e respondeu HTTP 200 em `127.0.0.1:7070/login`. |
| Leitor de tela | Nenhum leitor foi conectado ao navegador durante esta tentativa. |

As sondas de 03/09 não enviaram formulários, não criaram usuários e não modificaram permissões. O deploy
controlado de 04/09 está registrado separadamente na tabela acima.

> [!IMPORTANT]
> Esta evidência não aprova a acessibilidade manual. Ela documenta precisamente por que a tarefa não pôde ser
> encerrada. A versão atual já está publicada, mas ainda faltam sessão administrativa descartável e leitor de
> tela para produzir a evidência dos fluxos autenticados.

## Pré-condições para retomada

1. Criar uma identidade de teste com TOTP configurado e acesso administrativo global; criar também uma conta de teste
   com associação administrativa, chaves, grupos e regras suficientes para abrir as duas rotas.
2. Usar dados sintéticos e remover ou desativar a identidade de teste depois da validação, conforme o ciclo de dados
   de teste aprovado.
3. Registrar navegador, versão, sistema operacional, largura de viewport e leitor de tela. A execução deve incluir,
   no mínimo, um navegador suportado e um leitor de tela efetivamente ativo.

## Protocolo reproduzível

### 1. Centro global

1. Autenticar a identidade de teste e abrir `/system/access`.
2. Usar somente `Tab`, `Shift+Tab`, `Enter`, `Space` e `Esc` para percorrer pesquisa, árvore de categorias, abas,
   grids e ações disponíveis.
3. Confirmar visualmente e com o leitor de tela que foco, nome e estado de cada controle seguem ordem compreensível
   e que nenhum código técnico de chave é anunciado.
4. Abrir a seleção de chaves para explicação, exigir uma seleção vazia e registrar a mensagem anunciada; selecionar
   chaves e abrir a explicação efetiva.
5. Abrir uma alteração que gere prévia. Confirmar que o foco vai ao título da prévia, fica contido no diálogo e volta
   ao acionador quando o diálogo é cancelado. Confirmar que `Esc` apenas cancela e não confirma a mudança.

### 2. Centro de tenant

1. Abrir `/account/access?tenant=<teste>&membership=<teste>` com a associação administrativa sintética.
2. Repetir o fluxo de teclado, explicação e prévia, incluindo uma regra permissiva e uma bloqueadora.
3. Confirmar que a explicação não anuncia participante, grupo ou regra pertencente a outro tenant e diferencia
   ausência de permissão, bloqueio e indisponibilidade de plano.

### 3. Reflow e mensagens dinâmicas

1. Repetir os dois fluxos em viewport de 320 CSS px e com zoom de 400%.
2. Confirmar que campos, árvore, abas, grids e rodapés de diálogo permanecem alcançáveis sem perda de conteúdo nem
   rolagem horizontal da página para leitura de texto.
3. Com o leitor de tela ativo, provocar validação de campo obrigatório, resultado de explicação e falha de prévia;
   registrar o texto anunciado, a vez em que foi anunciado e se interrompeu leitura indevidamente.

## Resultado a registrar ao concluir

| Ambiente | Navegador/leitor | Cenários executados | Resultado | Defeitos |
| --- | --- | --- | --- | --- |
| A preencher | A preencher | A preencher | A preencher | A preencher |

O checkbox da tarefa 6.3.4 somente pode mudar para concluído quando esta tabela tiver evidência de cada bloco acima
ou uma justificativa de escopo explicitamente aprovada.
