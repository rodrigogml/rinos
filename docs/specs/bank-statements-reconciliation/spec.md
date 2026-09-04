# Feature Specification: Extratos e Conciliação Bancária

**Feature**: `bank-statements-reconciliation`
**Created**: 2026-07-23
**Status**: Clarified — pronta para planejamento

## Escopo

Esta feature permite importar ou receber extratos externos de contas financeiras monetárias, preservar cada linha como evidência independente e ajudar o usuário a relacioná-la aos fatos internos do Rinos. O sistema apresenta candidatos pontuados e explicáveis, mas nenhuma pontuação substitui a decisão final do usuário.

Uma linha externa pode corresponder a um ou vários lançamentos financeiros; várias linhas também podem corresponder a um único lançamento. Pagamentos, recebimentos, liquidações, transferências, obrigações, direitos e ocorrências recorrentes permanecem como contextos funcionais do lançamento subjacente e não possuem saldo conciliável independente. Quando ainda não existir o fato interno, o usuário poderá confirmar a criação de lançamentos, transferências, pagamentos, recebimentos ou efeitos previstos por uma recorrência, conforme os contratos das respectivas features.

> [!IMPORTANT]
> Importar uma linha de extrato não altera o saldo interno, não cria lançamento e não comprova uma classificação. O efeito financeiro somente existe quando já estava registrado no Rinos ou quando o usuário confirma explicitamente sua criação.

> [!IMPORTANT]
> Correspondência sugerida, alta pontuação e histórico de decisões nunca conciliam automaticamente. A confirmação final é sempre humana, inclusive quando várias linhas forem confirmadas em lote.

> [!NOTE]
> Linha de extrato externo, lançamento financeiro interno, liquidação e ocorrência recorrente mantêm identidades e ciclos de vida próprios. A conciliação registra vínculos e rateios entre eles sem fundir ou substituir os registros de origem.

## Clarifications

### Session 2026-07-23

- Q: Como identificar reimportações sem descartar movimentos legitimamente idênticos quando a origem não fornece chave estável? -> A: Cada linha recebe ID interno único; identificador externo é usado quando disponível; impressão canônica versionada e não exclusiva agrupa conteúdos equivalentes; multiplicidade preserva todas as ocorrências, e ambiguidades exigem decisão humana.
- Q: Deve existir conclusão ou encerramento próprio do extrato importado? -> A: Não; o usuário confirma a conciliação pelo fluxo normal ao verificar que o saldo está batendo, sem estado, ação de conclusão, encerramento ou reabertura adicional.
- Q: A primeira versão deve conciliar também extratos de contas de cartão de crédito? -> A: Não; extratos e conciliação com operadoras de cartão ficam adiados para um SDD de MVP futuro, precedido por pesquisa dos documentos e formatos realmente exportados pelos bancos e operadoras.

### Session 2026-07-24

- Q: Pagamentos, recebimentos, liquidações e transferências possuem resíduos conciliáveis separados de seus lançamentos? -> A: Não. A unidade monetária consumida é sempre o lançamento financeiro. Os demais objetos são contextos funcionais e caminhos de seleção do mesmo efeito, preservados para navegação e auditoria sem permitir dupla conciliação.
- Q: Uma linha ainda pendente na origem pode ser conciliada ou criar fato financeiro? -> A: Não. Ela pode receber candidatos e associação provisória sem consumir resíduos. Somente a linha efetivada pode ser alocada e confirmada; substituição, desaparecimento ou rejeição da pendente preserva evidência histórica sem alterar fatos internos.
- Q: Uma ingestão aceita deve preservar o arquivo ou payload exatamente como recebido? -> A: Sim. Arquivos mantêm os bytes exatos e conexões futuras mantêm o envelope recebido sem segredos, junto de hash, tamanho, tipo, codificação, parser e mapeamento. O payload é evidência protegida do tenant, não anexo genérico; prévia abandonada ou rejeitada conserva somente diagnóstico mínimo.
- Q: O que ocorre quando a origem revisa uma linha já conciliada? -> A: A decisão permanece vinculada à revisão exata confirmada e nada é alterado automaticamente. Mudança apenas informativa pode ser aceita pelo usuário; mudança material exige revisão antes de novas alocações. Substituição mantém versões sob uma identidade, enquanto reversão com identidade própria é outra linha e exige fato compensatório próprio.
- Q: Como tratar falhas parciais em arquivos e entregas conectadas? -> A: Arquivos são atômicos por conta e moeda; linha inválida bloqueia o sublote, salvo exclusão explícita e motivada, enquanto linhas técnicas reconhecidas podem ser ignoradas com motivo. Conexões processam cada linha identificada de modo independente e retomável, sem apresentar sucesso integral quando houver falhas ou pendências.

## Interface Coverage

| Superfície humana conhecida | Tipo | Atores | Cobertura | Comportamento funcional | Exclusões ou adiamentos |
|-----------------------------|------|--------|-----------|-------------------------|-------------------------|
| Web responsiva | Web | Usuários autorizados do tenant | FULL | Importar, revisar, pontuar, ratear, confirmar, desfazer e auditar conciliações por conta e extrato | Credenciais bancárias e consentimentos pertencem a `financial-connectivity` |
| Aplicativo móvel nativo | Mobile | Usuários do tenant | N/A | Não existe superfície móvel nativa definida | Toda capacidade nativa permanece fora do produto atual |
| Aplicação desktop nativa | Desktop | Usuários do tenant | N/A | Não existe superfície desktop nativa definida | Toda capacidade desktop nativa permanece fora do produto atual |
| CLI/TUI | CLI/TUI | Operadores | N/A | Não há jornada humana de conciliação por terminal | Administração operacional por terminal não integra a feature |

## User Scenarios & Testing

### User Story 1 - Importar um extrato preservando sua origem (Priority: P1)

Um usuário autorizado importa um arquivo OFX ou CSV para uma conta financeira, confere conta, moeda, período, saldos e linhas antes de aceitar a ingestão.

**Why this priority**: Sem uma cópia externa identificável e confiável, nenhuma conciliação posterior pode ser repetida, auditada ou protegida contra duplicidade.

**Independent Test**: Importar um extrato válido, repetir o mesmo arquivo e comprovar que conta, lote, linhas, identificadores e repetição são reconhecidos sem criar fatos financeiros.

**Acceptance Scenarios**:

1. **Given** arquivo válido e conta compatível, **When** usuário confirma a importação, **Then** lote e linhas externas são registrados com origem, valores originais e identidade própria sem alterar saldo interno.
2. **Given** arquivo já importado, **When** usuário tenta importá-lo novamente, **Then** o sistema identifica a repetição, apresenta o lote existente e não duplica linhas.
3. **Given** CSV com colunas ainda não mapeadas, **When** usuário configura e valida a correspondência das colunas, **Then** uma prévia demonstra datas, direções, valores e descrições antes da importação.
4. **Given** conta, moeda ou identificador do extrato incompatível, **When** usuário tenta aceitar a importação, **Then** nenhuma linha é persistida e as divergências são apresentadas.
5. **Given** extrato contendo saldos inicial e final, **When** a prévia é calculada, **Then** o sistema demonstra se saldo inicial mais movimentos reconciliam com o saldo final informado.
6. **Given** dois Pix legítimos com o mesmo conteúdo canônico no mesmo dia, **When** o extrato é importado, **Then** duas linhas com IDs internos distintos e a mesma impressão são preservadas como multiplicidade dois.
7. **Given** lote aceito, **When** sua auditoria técnica é consultada por usuário autorizado, **Then** o payload original imutável, seu hash e as versões de parser e mapeamento permitem reproduzir os dados recebidos.
8. **Given** arquivo com linha de dados inválida, **When** usuário tenta confirmar o sublote, **Then** nenhuma linha daquele sublote é aceita até correção do mapeamento ou exclusão explícita e motivada da linha.
9. **Given** arquivo separável por duas contas, **When** uma conta possui erro e a outra está válida, **Then** cada conta forma sublote independente e somente o sublote válido pode ser confirmado.

---

### User Story 2 - Receber linhas de uma conexão bancária (Priority: P1)

Uma integração autorizada entrega linhas externas para uma conta sem transferir à conciliação a responsabilidade por consentimentos, credenciais ou comunicação com a instituição.

**Why this priority**: Arquivos e conexões devem alimentar o mesmo modelo de extrato, permitindo que idempotência e conciliação funcionem de modo uniforme.

**Independent Test**: Entregar duas vezes o mesmo conjunto de linhas por uma origem conectada e comprovar que somente uma versão vigente de cada linha fica disponível.

**Acceptance Scenarios**:

1. **Given** origem conectada autorizada e conta identificada, **When** novas linhas são entregues, **Then** elas são registradas com origem e identidade externa sem alterar saldo.
2. **Given** mesma linha recebida novamente sem mudança, **When** a entrega é reprocessada, **Then** o resultado é idempotente.
3. **Given** mesma identidade externa com conteúdo divergente, **When** nova entrega ocorre, **Then** a divergência é preservada como revisão ou conflito, sem sobrescrever silenciosamente o conteúdo anterior.
4. **Given** falha em parte da entrega, **When** a origem repete a operação, **Then** linhas já aceitas não são duplicadas e as restantes podem ser retomadas.
5. **Given** linha pendente posteriormente substituída por linha efetivada, **When** a entrega é processada, **Then** ambas permanecem relacionadas e o candidato provisório é reapresentado sem duplicar linha nem fato interno.
6. **Given** entrega com linhas aceitas, rejeitadas e pendentes, **When** o resultado é registrado, **Then** cada item possui situação própria e a entrega não é apresentada como sucesso integral.

---

### User Story 3 - Avaliar candidatos explicáveis (Priority: P1)

Um usuário autorizado seleciona uma linha externa e consulta candidatos internos ordenados por uma pontuação de compatibilidade acompanhada dos fatores favoráveis, contrários e ausentes.

**Why this priority**: O sistema deve reduzir o trabalho de busca sem esconder incertezas nem transformar uma aproximação em decisão financeira.

**Independent Test**: Comparar uma linha com lançamento exato, liquidação próxima e ocorrência recorrente aproximada, verificando ordenação, pontuação e explicação distintas.

**Acceptance Scenarios**:

1. **Given** linha e lançamento com conta, moeda, direção, valor, data e referência compatíveis, **When** candidatos são calculados, **Then** o lançamento aparece com pontuação e explicação de cada sinal.
2. **Given** mais de um candidato plausível, **When** resultados são apresentados, **Then** todos permanecem disponíveis e nenhum é confirmado automaticamente.
3. **Given** sinais incompatíveis ou insuficientes, **When** candidatos são calculados, **Then** o sistema reduz a pontuação, explica a incerteza e permite busca manual.
4. **Given** histórico de decisões semelhantes no tenant, **When** nova linha é avaliada, **Then** o histórico pode influenciar a sugestão de forma identificável sem ocultar os demais sinais.
5. **Given** pontuação máxima, **When** usuário não confirma a proposta, **Then** nenhum vínculo ou efeito financeiro é criado.
6. **Given** linha ainda pendente na origem, **When** usuário escolhe candidato, **Then** somente associação provisória é registrada e nenhum resíduo é consumido.

---

### User Story 4 - Conciliar registros existentes com rateios flexíveis (Priority: P1)

Um usuário autorizado relaciona linhas externas a lançamentos financeiros já existentes, diretamente ou por contextos como transferências, pagamentos e recebimentos, e distribui valores quando a correspondência não for um para um.

**Why this priority**: Depósitos agrupados, tarifas descontadas, pagamentos em lote e liquidações parciais exigem cardinalidades flexíveis sem duplicar movimentações.

**Independent Test**: Conciliar duas linhas externas com três liquidações internas por rateios parciais e comprovar que cada saldo conciliável é consumido exatamente uma vez.

**Acceptance Scenarios**:

1. **Given** uma linha e um lançamento de mesmo valor, **When** usuário confirma, **Then** ambos são vinculados sem criar nova movimentação.
2. **Given** uma linha que resume várias liquidações, **When** usuário distribui integralmente seu valor, **Then** cada alocação é preservada e a linha fica integralmente conciliada.
3. **Given** várias linhas correspondentes a um lançamento, **When** usuário aloca os valores, **Then** o lançamento pode ficar parcial ou integralmente conciliado sem novo efeito no saldo.
4. **Given** valores ainda não integralmente distribuídos, **When** usuário salva o trabalho, **Then** resíduos externos e internos permanecem explícitos e disponíveis para conciliação posterior.
5. **Given** tentativa de alocar valor superior ao resíduo disponível, **When** confirmação é solicitada, **Then** a operação é rejeitada sem efeito parcial.
6. **Given** liquidação e respectivo lançamento apresentados como caminhos de seleção, **When** um deles é conciliado, **Then** ambos exibem o mesmo resultado e o lançamento não possui segundo resíduo conciliável.

---

### User Story 5 - Criar fatos internos a partir do extrato (Priority: P1)

Quando uma linha externa ainda não possuir fato interno correspondente, um usuário autorizado decide criar e classificar o efeito adequado, preservando o vínculo com a evidência bancária.

**Why this priority**: Tarifas, juros, créditos inesperados e débitos automáticos frequentemente aparecem primeiro no banco e precisam entrar no controle interno sem digitação duplicada.

**Independent Test**: Usar uma linha de tarifa para criar lançamento categorizado e uma linha de débito automático para criar obrigação liquidada prevista por recorrência, comprovando ausência de duplicidade.

**Acceptance Scenarios**:

1. **Given** linha ainda não representada internamente e dados completos, **When** usuário confirma lançamento direto, **Then** um único lançamento confirmado é criado e conciliado com a linha.
2. **Given** linha real sem classificação completa, **When** usuário confirma seu reconhecimento mínimo, **Then** um pré-lançamento pode representar o saldo e permanece pendente de conclusão.
3. **Given** ocorrência recorrente configurada para classificação direta, **When** usuário confirma a correspondência, **Then** somente o lançamento categorizado e o vínculo esparso da ocorrência são criados.
4. **Given** ocorrência configurada para obrigação ou direito, **When** usuário confirma a correspondência, **Then** o módulo responsável cria e liquida os documentos aplicáveis com uma única movimentação financeira.
5. **Given** uma linha representando transferência entre contas do tenant, **When** usuário confirma sua natureza, **Then** a transferência e seus movimentos são criados ou vinculados conforme as contas envolvidas, sem tratar o valor como receita ou despesa.

---

### User Story 6 - Aplicar decisões em lote com revisão humana (Priority: P2)

Um usuário autorizado revisa várias propostas, ajusta exceções e confirma em lote somente as correspondências que deseja aceitar.

**Why this priority**: Extratos extensos precisam de produtividade, mas a automação não pode retirar do usuário o controle sobre cada efeito produzido.

**Independent Test**: Preparar cinquenta propostas, rejeitar duas, alterar três e confirmar as demais em lote, verificando efeitos individuais e relatório de falhas.

**Acceptance Scenarios**:

1. **Given** várias linhas com candidatos, **When** usuário seleciona propostas, **Then** visualiza antecipadamente vínculos, rateios e novos fatos de cada item.
2. **Given** seleção com proposta ambígua ou incompleta, **When** confirmação em lote é solicitada, **Then** o item exige tratamento individual sem bloquear propostas independentes válidas.
3. **Given** falha em um item do lote, **When** os demais são confirmados, **Then** o sistema informa resultados por linha e não apresenta a falha como sucesso.
4. **Given** propostas geradas pelo histórico, **When** usuário confirma o lote, **Then** cada confirmação conserva a autoria humana e a explicação que foi apresentada.

---

### User Story 7 - Corrigir ou desfazer uma conciliação (Priority: P1)

Um usuário autorizado desfaz vínculo incorreto ou corrige o efeito criado pela conciliação, respeitando a data de fechamento financeiro e os contratos dos módulos envolvidos.

**Why this priority**: Erros de correspondência são inevitáveis; corrigi-los não pode apagar a evidência externa nem reescrever fatos protegidos.

**Independent Test**: Desfazer conciliação com lançamento preexistente, depois desfazer conciliação que criou lançamento em data aberta e comprovar comportamentos distintos.

**Acceptance Scenarios**:

1. **Given** conciliação com fato preexistente e data aberta, **When** usuário desfaz o vínculo, **Then** o fato interno permanece e somente as alocações de conciliação são revertidas.
2. **Given** conciliação que criou fato interno ainda reversível, **When** usuário solicita desfazimento, **Then** a correção é coordenada com o módulo responsável sem deixar saldo ou vínculo parcial.
3. **Given** data financeira protegida pelo fechamento, **When** usuário tenta desfazer efeito protegido, **Then** o sistema preserva o fato original e orienta correção compensatória em data aberta.
4. **Given** linha externa corrigida ou revertida pela origem, **When** a revisão é processada, **Then** conciliações existentes são sinalizadas para revisão sem alteração financeira automática.
5. **Given** revisão apenas informativa de linha conciliada, **When** usuário confirma sua equivalência, **Then** a decisão anterior permanece válida e ambas as revisões continuam auditáveis.
6. **Given** revisão material de valor, direção, moeda, conta ou data relevante, **When** ela é recebida, **Then** a linha exige revisão humana e não aceita nova alocação até a divergência ser resolvida.
7. **Given** reversão fornecida como movimento externo com identidade própria, **When** é ingerida, **Then** ela permanece linha distinta e pode ser conciliada com fato compensatório distinto.

---

### User Story 8 - Acompanhar cobertura e auditoria do extrato (Priority: P2)

Um usuário autorizado consulta o que foi importado, conciliado, parcialmente resolvido, pendente, divergente ou corrigido em cada conta e origem.

**Why this priority**: A conciliação precisa explicar tanto as diferenças restantes quanto as decisões passadas, sem depender de memória operacional.

**Independent Test**: Consultar um extrato contendo linhas conciliadas, parciais, pendentes, revisadas e conflitantes e reproduzir a origem de cada estado.

**Acceptance Scenarios**:

1. **Given** lote importado, **When** seu resumo é consultado, **Then** totais, resíduos e quantidade de linhas por estado são apresentados sem misturar saldo externo e interno.
2. **Given** conciliação confirmada, **When** auditoria é aberta, **Then** usuário, instante, candidatos apresentados, decisão, alocações e efeitos permanecem rastreáveis.
3. **Given** linha reimportada ou revisada, **When** histórico é consultado, **Then** origem, conteúdo anterior, conteúdo vigente e impacto sobre conciliações ficam distinguíveis.
4. **Given** usuário sem acesso a determinado documento interno, **When** consulta o extrato, **Then** o sistema não revela seus dados por explicações, totais ou auditoria.

### Edge Cases

- Dois movimentos legítimos possuem mesma data, valor e descrição.
- O mesmo arquivo é renomeado, reordenado ou exportado novamente com pequenas diferenças.
- A instituição muda o identificador de uma transação entre pendente e efetivada.
- Uma linha é substituída, excluída ou revertida pela origem.
- OFX contém identificador repetido, ausente ou divergente dentro da mesma conta.
- CSV usa separadores, codificação, sinais, formatos de data ou casas decimais diferentes.
- Arquivo contém mais de uma conta, moeda não compatível ou período sobreposto.
- Extrato omite saldo inicial, saldo final ou identificador da conta.
- Saldo inicial mais movimentos não corresponde ao saldo final informado.
- Uma linha possui data da transação e data de efetivação diferentes.
- Fuso horário muda a data aparente de uma movimentação.
- Uma linha combina principal, tarifa, juros, tributos ou outras parcelas.
- Depósito líquido corresponde a várias cobranças menos uma tarifa.
- Pagamento em lote corresponde a obrigações de diferentes documentos compatíveis.
- Linha externa e fato interno estão em moedas diferentes.
- Um candidato já está integralmente conciliado com outra linha.
- Duas pessoas tentam confirmar a mesma linha simultaneamente.
- Confirmação em lote falha apenas para parte das linhas.
- Correção do extrato chega depois do fechamento financeiro.
- Lançamento, liquidação, recorrência ou transferência vinculada é corrigida no módulo de origem.
- Conta é inativada ou encerrada com linhas pendentes.
- Importação contém período anterior ao saldo de abertura da conta.
- Usuário perde autorização entre a prévia e a confirmação.

## Requirements

### Fronteiras e Responsabilidades

- **FR-BSR-BOUND-001**: Todo lote, extrato, linha, revisão, candidato, alocação e decisão DEVE pertencer exclusivamente a um tenant e a uma conta financeira identificada.
- **FR-BSR-BOUND-002**: Linha externa DEVE permanecer distinta de lançamento, transferência, obrigação, direito, liquidação, ocorrência recorrente e documento contábil.
- **FR-BSR-BOUND-003**: Importação ou recebimento de linha externa NÃO DEVE alterar saldo, criar fato interno, classificar movimentação nem liquidar documento.
- **FR-BSR-BOUND-004**: Conciliação DEVE registrar correspondência e alocações sem substituir a identidade ou o histórico dos registros relacionados.
- **FR-BSR-BOUND-005**: Esta feature DEVE controlar ingestão, análise, decisão e vínculo; consentimentos, credenciais, comunicação e sincronização com instituições pertencem a `financial-connectivity`.
- **FR-BSR-BOUND-006**: Efeitos financeiros confirmados DEVEM ser produzidos pelos contratos de `financial-transactions`, `financial-transfers`, `accounts-payable`, `accounts-receivable` ou `financial-recurrences`, conforme sua natureza.
- **FR-BSR-BOUND-007**: Pontuação e sugestão NÃO DEVEM constituir aprovação, confirmação, lançamento ou evidência de que os registros representam o mesmo fato.
- **FR-BSR-BOUND-008**: Toda confirmação DEVE decorrer de ação explícita de usuário autorizado, inclusive em operação em lote.
- **FR-BSR-BOUND-009**: Ingestão DEVE converter OFX, CSV e entregas conectadas em uma representação canônica de linha externa; conciliação DEVE operar sobre essa representação sem depender do formato ou canal de origem.
- **FR-BSR-BOUND-010**: A primeira versão DEVE limitar importação e conciliação a contas financeiras monetárias e NÃO DEVE importar nem conciliar extratos de contas especializadas de cartão de crédito.
- **FR-BSR-BOUND-011**: Lançamento financeiro DEVE ser a única unidade interna com valor e resíduo conciliáveis; documentos, liquidações, pagamentos, recebimentos, transferências e ocorrências recorrentes DEVEM permanecer contextos funcionais sem saldo conciliável próprio.

### Origens, Extratos e Lotes

- **FR-BSR-SOURCE-001**: Sistema DEVE aceitar, na primeira versão, arquivos OFX e CSV e linhas entregues por origem conectada.
- **FR-BSR-SOURCE-002**: Cada ingestão DEVE registrar tipo da origem, conta, instituição quando conhecida, período, momento de recebimento, ator ou origem sistêmica e referência idempotente.
- **FR-BSR-SOURCE-003**: Arquivo DEVE possuir identidade de conteúdo capaz de reconhecer repetição integral mesmo quando nome ou momento de envio mudar.
- **FR-BSR-SOURCE-004**: Valores originais relevantes da origem e valores normalizados usados pelo Rinos DEVEM permanecer distinguíveis e auditáveis.
- **FR-BSR-SOURCE-005**: Importação DEVE apresentar prévia com conta, moeda, período, quantidade de linhas, totais, saldos informados, alertas e erros antes da confirmação.
- **FR-BSR-SOURCE-006**: Falha de validação estrutural, conta incompatível, moeda incompatível ou linha de dados inválida DEVE impedir a confirmação atômica do sublote correspondente, salvo exclusão explícita permitida, sem persistir suas demais linhas como importação concluída.
- **FR-BSR-SOURCE-007**: Lote rejeitado ou com falha DEVE permanecer diagnosticável sem apresentar linhas como disponíveis para conciliação.
- **FR-BSR-SOURCE-008**: Repetição de lote já aceito DEVE apresentar a ingestão existente e não duplicar suas linhas.
- **FR-BSR-SOURCE-009**: Sobreposição de períodos ou cobertura parcial DEVE ser informada sem presumir duplicidade de todas as linhas.
- **FR-BSR-SOURCE-010**: Quando saldos inicial e final existirem, sistema DEVE verificar saldo inicial mais efeitos das linhas contra saldo final e explicitar qualquer diferença.
- **FR-BSR-SOURCE-011**: Ausência de saldos, período ou identificador de conta DEVE ser indicada sem inventar valores ausentes.
- **FR-BSR-SOURCE-012**: Extrato com múltiplas contas ou moedas DEVE ser separado em sublotes independentes por combinação compatível ou rejeitado com explicação antes da confirmação.
- **FR-BSR-SOURCE-013**: Ingestão aceita por arquivo DEVE preservar de forma imutável os bytes exatos recebidos, seu hash, tamanho, tipo declarado e detectado, codificação e nome original quando informado.
- **FR-BSR-SOURCE-014**: Ingestão aceita por origem conectada DEVE preservar o envelope bruto recebido ou representação canônica equivalente definida pelo contrato, removendo ou segregando credenciais, tokens e segredos.
- **FR-BSR-SOURCE-015**: Toda ingestão aceita DEVE registrar versão do parser, modelo e versão do mapeamento, opções de normalização e demais metadados necessários para explicar como o payload produziu lotes e linhas.
- **FR-BSR-SOURCE-016**: Payload bruto DEVE pertencer ao armazenamento protegido do tenant, seguir `tenant-data-governance` e NÃO DEVE depender de `attachments-evidence` nem ser apresentado como anexo comum.
- **FR-BSR-SOURCE-017**: Prévia abandonada, lote rejeitado ou validação sem confirmação NÃO DEVE reter o payload completo além do processamento necessário; somente diagnóstico e auditoria mínimos e não excessivos PODERÃO permanecer.
- **FR-BSR-SOURCE-018**: Preservação do payload NÃO DEVE permitir reprocessamento silencioso que modifique lotes ou linhas existentes; nova interpretação DEVE constituir ingestão ou revisão explicitamente identificada.
- **FR-BSR-SOURCE-019**: Arquivo OFX ou CSV DEVE ser confirmado atomicamente por sublote de conta e moeda; sucesso de um sublote NÃO DEVE ocultar falha de outro.
- **FR-BSR-SOURCE-020**: Cabeçalho, rodapé, linha vazia ou registro técnico reconhecido pelo parser PODERÁ ser ignorado automaticamente, desde que sua posição e motivo permaneçam na prévia e no diagnóstico.
- **FR-BSR-SOURCE-021**: Linha que represente dado financeiro somente PODERÁ ser excluída do sublote por decisão explícita de usuário autorizado, com motivo e identificação preservados no diagnóstico.
- **FR-BSR-SOURCE-022**: Linha de dados excluída NÃO DEVE gerar linha externa conciliável, mas DEVE permanecer reproduzível a partir do payload imutável e do diagnóstico do lote.
- **FR-BSR-SOURCE-023**: Usuário DEVE poder corrigir mapeamento ou opções de interpretação e gerar nova prévia antes da confirmação, sem modificar payload original.
- **FR-BSR-SOURCE-024**: Entrega de origem conectada DEVE processar cada linha externamente identificada como unidade idempotente, permitindo aceitar linhas válidas e manter falhas individualizadas.
- **FR-BSR-SOURCE-025**: Resultado de entrega conectada DEVE distinguir linhas aceitas, repetidas, rejeitadas, conflitantes e pendentes e NÃO DEVE declarar sucesso integral enquanto houver falha ou pendência.
- **FR-BSR-SOURCE-026**: Retomada de entrega conectada DEVE reutilizar as identidades já aceitas e processar novamente somente itens necessários, sem duplicar linhas nem revisões.

### OFX, CSV e Origem Conectada

- **FR-BSR-FMT-001**: Importação OFX DEVE preservar identificador da instituição, identificador da conta, moeda, período, saldos, identificadores das transações, datas, tipo, valor, nome, memorando e referências quando fornecidos.
- **FR-BSR-FMT-002**: Revisões, substituições, exclusões e reversões declaradas pelo OFX DEVEM ser reconhecidas como eventos sobre linhas identificadas, sem exclusão destrutiva.
- **FR-BSR-FMT-003**: CSV DEVE exigir mapeamento validado para, no mínimo, data, valor ou débito/crédito e descrição.
- **FR-BSR-FMT-004**: Mapeamento CSV PODERÁ definir data de efetivação, data da transação, identificador externo, direção, moeda, contraparte, referência e campos adicionais.
- **FR-BSR-FMT-005**: Usuário DEVE poder salvar modelos de mapeamento CSV no tenant e identificá-los por instituição ou formato, sem alterar lotes já importados.
- **FR-BSR-FMT-006**: Prévia CSV DEVE evidenciar formato de data, separadores, sinal monetário, precisão, linhas ignoradas e erros de conversão.
- **FR-BSR-FMT-007**: Origem conectada DEVE entregar conta, identidade da origem e identidade de cada linha; ausência ou conflito DEVE produzir rejeição ou pendência rastreável.
- **FR-BSR-FMT-008**: Esta feature NÃO DEVE armazenar ou renovar credenciais, consentimentos ou tokens de conexão bancária.

### Identidade, Repetição e Revisões

- **FR-BSR-ID-001**: Linha externa DEVE possuir identidade interna estável e imutável no tenant, independentemente de vínculos ou classificações posteriores.
- **FR-BSR-ID-002**: Identificador externo estável DEVE ser avaliado no escopo conjunto de origem, instituição e conta, sem presumir unicidade global.
- **FR-BSR-ID-003**: Repetição com mesma identidade externa e mesmo conteúdo DEVE ser idempotente.
- **FR-BSR-ID-004**: Mesma identidade externa com conteúdo materialmente diferente DEVE criar revisão ou conflito, preservar versões e impedir sobrescrita silenciosa.
- **FR-BSR-ID-005**: Conteúdo igual com identidades externas diferentes NÃO DEVE ser descartado automaticamente, pois movimentos legítimos podem possuir os mesmos dados.
- **FR-BSR-ID-006**: Toda linha DEVE possuir impressão canônica determinística e versionada, formada por campos normalizados e estáveis disponíveis, distinta do ID interno e do identificador externo.
- **FR-BSR-ID-007**: Linha pendente substituída por linha efetivada DEVE manter relação entre as identidades ou revisões quando a origem fornecer sinais suficientes.
- **FR-BSR-ID-008**: Correção ou reversão externa NÃO DEVE desfazer conciliação nem criar compensação financeira automaticamente.
- **FR-BSR-ID-009**: Importação concorrente ou repetida em múltiplas instâncias NÃO DEVE produzir duas linhas vigentes para a mesma identidade externa estável.
- **FR-BSR-ID-010**: Cancelamento de lote sem conciliações PODERÁ retirar suas linhas da operação comum, preservando auditoria; lote com vínculos ou revisões NÃO DEVE ser excluído destrutivamente.
- **FR-BSR-ID-011**: Impressão canônica NÃO DEVE possuir restrição de unicidade, substituir o ID interno nem provar que duas linhas representam a mesma ocorrência.
- **FR-BSR-ID-012**: Cada ocorrência de conteúdo canônico idêntico dentro de uma ingestão DEVE receber ID interno próprio e compor a multiplicidade observada daquela impressão.
- **FR-BSR-ID-013**: Reimportação sem identificador externo DEVE comparar impressão, multiplicidade, conta, cobertura e demais evidências disponíveis para reutilizar somente ocorrências suficientemente correspondentes.
- **FR-BSR-ID-014**: Nova ingestão com multiplicidade maior que a já reconhecida DEVE preservar as ocorrências adicionais como linhas próprias, sem descartar a diferença como duplicidade.
- **FR-BSR-ID-015**: Quando a origem, cobertura ou ordem não permitir distinguir com segurança quais ocorrências idênticas já existiam, sistema DEVE preservar as linhas candidatas, sinalizar a ambiguidade e exigir decisão humana.
- **FR-BSR-ID-016**: Ordem da linha no arquivo ou lote NÃO DEVE, isoladamente, provar identidade entre ingestões diferentes.
- **FR-BSR-ID-017**: Alteração da composição da impressão DEVE criar nova versão de cálculo sem modificar impressões, identidades ou decisões produzidas por versões anteriores.
- **FR-BSR-ID-018**: Impressão do lote e impressão da linha DEVEM ter finalidades distintas: a primeira reconhece repetição integral do conteúdo recebido; a segunda agrupa linhas canonicamente equivalentes.
- **FR-BSR-ID-019**: Decisão e alocações de conciliação DEVEM referenciar a revisão exata da linha apresentada e confirmada pelo usuário.
- **FR-BSR-ID-020**: Nova revisão externa NÃO DEVE migrar, recalcular, desfazer nem reaplicar decisão, alocação ou fato interno automaticamente.
- **FR-BSR-ID-021**: Alteração somente informativa PODERÁ manter a decisão anterior aplicável após confirmação explícita do usuário, preservando as duas revisões e a justificativa.
- **FR-BSR-ID-022**: Alteração material de valor, direção, moeda, conta ou data relevante DEVE colocar a linha em revisão necessária e impedir novas alocações até decisão humana.
- **FR-BSR-ID-023**: Substituição sob a mesma identidade externa DEVE permanecer como revisão da linha; reversão entregue com identidade própria e efeito monetário oposto DEVE constituir linha externa distinta.

### Conteúdo e Estado da Linha Externa

- **FR-BSR-LINE-001**: Linha DEVE preservar conta, moeda, direção, valor, datas disponíveis, descrição original, referências, contraparte informada, tipo externo e estado informado pela origem.
- **FR-BSR-LINE-002**: Data da transação, data de efetivação e momento de recepção DEVEM permanecer distintas quando fornecidas.
- **FR-BSR-LINE-003**: Valor DEVE conservar sinal e precisão da origem; normalização NÃO DEVE inverter direção silenciosamente.
- **FR-BSR-LINE-004**: Linha DEVE distinguir ao menos pendente na origem, efetivada, corrigida, revertida e excluída pela origem quando essas informações existirem.
- **FR-BSR-LINE-005**: Situação de conciliação DEVE distinguir não analisada, com sugestões, parcialmente conciliada, conciliada, divergente e em revisão.
- **FR-BSR-LINE-006**: Estado externo e situação de conciliação DEVEM ser independentes.
- **FR-BSR-LINE-007**: Linha pendente na origem PODERÁ receber sugestões e associação provisória, mas NÃO DEVE receber alocação, consumir resíduo, ser conciliada nem criar fato interno.
- **FR-BSR-LINE-008**: Conteúdo original protegido NÃO DEVE ser editado pelo usuário; anotações e classificações internas DEVEM permanecer separadas.
- **FR-BSR-LINE-009**: Associação provisória DEVE identificar linha pendente, candidato, ator ou origem e instante, sem constituir decisão de conciliação.
- **FR-BSR-LINE-010**: Quando linha efetivada correspondente for identificada, o sistema DEVE preservar a relação com a pendente e reapresentar o candidato provisório para nova decisão humana, sem convertê-lo automaticamente em alocação.
- **FR-BSR-LINE-011**: Linha pendente desaparecida, rejeitada, substituída ou expirada na origem DEVE permanecer como evidência histórica e perder a associação provisória vigente sem cancelar, revisar ou criar fato interno.

### Candidatos e Pontuação

- **FR-BSR-MATCH-001**: Sistema DEVE buscar candidatos entre lançamentos, movimentos de transferências, liquidações, pagamentos, recebimentos e ocorrências recorrentes acessíveis no tenant.
- **FR-BSR-MATCH-002**: Conta, moeda e direção incompatíveis DEVEM excluir candidato, salvo contrato explícito de transferência ou conversão já registrado.
- **FR-BSR-MATCH-003**: Pontuação DEVE considerar, conforme disponibilidade, valor, datas, referências, identificadores, contraparte, descrição, documento, recorrência e histórico confirmado.
- **FR-BSR-MATCH-004**: Cada candidato DEVE apresentar pontuação em escala comum, fatores favoráveis, fatores contrários e dados relevantes ausentes.
- **FR-BSR-MATCH-005**: Pontuação DEVE representar ordenação de compatibilidade e NÃO DEVE ser apresentada como probabilidade garantida.
- **FR-BSR-MATCH-006**: Critérios obrigatórios, pesos e faixas de confiança DEVEM possuir versão identificável para que uma sugestão passada possa ser explicada.
- **FR-BSR-MATCH-007**: Alterar regras ou histórico NÃO DEVE modificar conciliações confirmadas nem reescrever a explicação registrada na decisão.
- **FR-BSR-MATCH-008**: Usuário DEVE poder buscar manualmente registros fora da janela ou dos candidatos sugeridos, sujeito às compatibilidades obrigatórias.
- **FR-BSR-MATCH-009**: Registro já integralmente alocado NÃO DEVE ser sugerido como se ainda possuísse valor disponível.
- **FR-BSR-MATCH-010**: Ambiguidade entre candidatos DEVE permanecer explícita e impedir qualquer presunção automática.
- **FR-BSR-MATCH-011**: Histórico de confirmações do tenant PODERÁ sugerir contraparte, categoria, dimensões, descrição e destino, mas cada influência DEVE ser identificável.
- **FR-BSR-MATCH-012**: Decisão rejeitada pelo usuário PODERÁ reduzir sugestões equivalentes futuras sem ocultar candidatos baseados em sinais objetivos.
- **FR-BSR-MATCH-013**: Candidatos alcançados diretamente pelo lançamento ou por diferentes contextos funcionais DEVEM ser consolidados como uma única possibilidade monetária, preservando as explicações e os caminhos de navegação relevantes.

### Alocações e Cardinalidade

- **FR-BSR-ALLOC-001**: Conciliação DEVE admitir relações um para um, um para muitos, muitos para um e muitos para muitos.
- **FR-BSR-ALLOC-002**: Cada alocação DEVE identificar linha externa, lançamento financeiro interno, valor aplicado, moeda e decisão que a criou, podendo preservar contextos funcionais relacionados sem lhes atribuir resíduo próprio.
- **FR-BSR-ALLOC-003**: Soma das alocações de uma linha NÃO DEVE exceder seu valor conciliável vigente.
- **FR-BSR-ALLOC-004**: Soma das alocações recebidas por lançamento financeiro NÃO DEVE exceder seu valor conciliável vigente.
- **FR-BSR-ALLOC-005**: Alocação parcial DEVE manter resíduos externo e interno disponíveis e visíveis.
- **FR-BSR-ALLOC-006**: Conciliação integral DEVE exigir que o resíduo aplicável seja zero na precisão da moeda.
- **FR-BSR-ALLOC-007**: Diferença NÃO DEVE ser descartada como arredondamento, tarifa, juros, desconto ou ajuste sem destinação escolhida pelo usuário.
- **FR-BSR-ALLOC-008**: Uma linha PODERÁ ser dividida entre registros existentes e novos efeitos, desde que todas as parcelas permaneçam explícitas e reconciliem com o total confirmado.
- **FR-BSR-ALLOC-009**: Relação entre moedas distintas somente DEVE ser permitida quando o fato interno conservar conversão explícita e valores comparáveis; a conciliação NÃO DEVE inventar taxa.
- **FR-BSR-ALLOC-010**: Operações concorrentes DEVEM consumir resíduos de forma serializada e rejeitar alocação baseada em saldo conciliável desatualizado.
- **FR-BSR-ALLOC-011**: Selecionar pagamento, recebimento, liquidação, transferência ou outro contexto DEVE consumir exatamente o mesmo resíduo do lançamento subjacente que seria consumido por sua seleção direta.
- **FR-BSR-ALLOC-012**: Um lançamento integralmente conciliado NÃO DEVE voltar a possuir valor disponível por meio de outro contexto, documento ou vínculo de origem.
- **FR-BSR-ALLOC-013**: Para transferência, cada linha externa DEVE ser alocada somente ao lançamento correspondente à conta daquele extrato; o vínculo com a operação coordenada NÃO DEVE fundir os resíduos das duas pontas.
- **FR-BSR-ALLOC-014**: Somente revisão externa efetivada e vigente DEVE possuir valor conciliável e aceitar alocação; linha pendente NÃO DEVE participar de rateio nem cardinalidade monetária.
- **FR-BSR-ALLOC-015**: Revisão materialmente divergente NÃO DEVE disponibilizar novo resíduo conciliável enquanto a relação com decisões anteriores não tiver sido explicitamente resolvida.

### Confirmação e Efeitos Financeiros

- **FR-BSR-CONF-001**: Toda confirmação DEVE revalidar tenant, conta, moeda, linha vigente, resíduos, autorização, fechamento e contratos dos módulos relacionados.
- **FR-BSR-CONF-002**: Vincular linha a fato interno existente NÃO DEVE criar nova movimentação nem reaplicar o efeito no saldo.
- **FR-BSR-CONF-003**: Criação de fato novo DEVE ocorrer atomicamente com sua alocação à linha ou não produzir efeito parcial.
- **FR-BSR-CONF-004**: Linha com dados completos PODERÁ originar lançamento confirmado com categoria, dimensões, contraparte e demais dados exigidos.
- **FR-BSR-CONF-005**: Linha cujo efeito é real, mas cuja classificação está incompleta, PODERÁ originar pré-lançamento com os dados mínimos de `financial-transactions`.
- **FR-BSR-CONF-006**: Pré-lançamento criado pela conciliação DEVE permanecer pendente e bloquear avanço do fechamento aplicável até sua conclusão, mudança de data ou cancelamento autorizado.
- **FR-BSR-CONF-007**: Confirmação de transferência DEVE usar `financial-transfers`, preservar o vínculo da operação e alocar cada linha somente ao lançamento da respectiva conta.
- **FR-BSR-CONF-008**: Confirmação de pagamento ou recebimento DEVE usar `accounts-payable` ou `accounts-receivable`, incluindo alocações e componentes escolhidos, e conciliar somente o lançamento financeiro resultante.
- **FR-BSR-CONF-009**: Confirmação de ocorrência recorrente DEVE respeitar a estratégia configurada na série e registrar sua realização esparsa.
- **FR-BSR-CONF-010**: Usuário DEVE poder destinar diferenças a novos lançamentos categorizados, outros documentos compatíveis ou resíduos pendentes sem presunção automática.
- **FR-BSR-CONF-011**: Repetição da mesma decisão DEVE retornar o resultado vigente sem duplicar vínculo, lançamento, transferência, liquidação ou realização.
- **FR-BSR-CONF-012**: Confirmação em lote DEVE registrar decisão humana por item e apresentar resultado individual, ainda que a interface permita uma única ação agregada.
- **FR-BSR-CONF-013**: Falha de um item independente em lote NÃO DEVE ser apresentada como sucesso nem corromper itens já confirmados; dependências conjuntas DEVEM concluir integralmente ou falhar juntas.
- **FR-BSR-CONF-014**: Antes da confirmação, sistema DEVE apresentar valores externos, alocações, resíduos e diferença resultante para que o usuário verifique se o saldo está batendo.
- **FR-BSR-CONF-015**: Saldo matematicamente coincidente NÃO DEVE confirmar a conciliação automaticamente; a decisão final DEVE permanecer como ação explícita do usuário.
- **FR-BSR-CONF-016**: Confirmação sobre linha pendente DEVE ser rejeitada sem criar lançamento, pré-lançamento, transferência, pagamento, recebimento, liquidação, realização recorrente ou alocação.

### Regras Reutilizáveis e Histórico

- **FR-BSR-RULE-001**: Usuário autorizado DEVE poder cadastrar regras de sugestão por conta ou conjunto compatível de contas.
- **FR-BSR-RULE-002**: Regra PODERÁ considerar direção, faixa de valor, descrição, referência, contraparte, categoria, dimensões, recorrência e tipo externo.
- **FR-BSR-RULE-003**: Regra DEVE produzir somente sugestão, preenchimento proposto ou candidato e NÃO DEVE confirmar conciliação.
- **FR-BSR-RULE-004**: Regras DEVEM possuir prioridade, vigência, estado e histórico de alterações.
- **FR-BSR-RULE-005**: Quando várias regras forem aplicáveis, sistema DEVE explicar a ordem e os resultados combinados ou conflitantes.
- **FR-BSR-RULE-006**: Usuário DEVE poder criar regra a partir de uma decisão confirmada sem que a regra seja criada silenciosamente.
- **FR-BSR-RULE-007**: Sugestão baseada em frequência histórica DEVE indicar que deriva de decisões anteriores e não de referência fornecida pela instituição.

### Desfazimento, Revisão e Fechamento

- **FR-BSR-REV-001**: Usuário autorizado DEVE poder desfazer conciliação enquanto os efeitos financeiros envolvidos puderem ser coordenados conforme seus módulos de origem.
- **FR-BSR-REV-002**: Desfazer vínculo com fato preexistente DEVE preservar o fato e restaurar somente resíduos e estados de conciliação.
- **FR-BSR-REV-003**: Desfazer conciliação que criou fato interno DEVE coordenar revisão ou cancelamento com o módulo responsável e não deixar saldo, documento ou alocação parcial.
- **FR-BSR-REV-004**: Data protegida pelo fechamento financeiro NÃO DEVE aceitar exclusão ou reescrita do fato interno por meio da conciliação.
- **FR-BSR-REV-005**: Correção após fechamento DEVE preservar decisão original e usar fatos compensatórios em data aberta quando houver efeito monetário a corrigir.
- **FR-BSR-REV-006**: Linha externa corrigida, revertida ou excluída pela origem DEVE sinalizar conciliações afetadas para revisão humana.
- **FR-BSR-REV-007**: Mudança no documento interno relacionado DEVE recalcular resíduos e sinalizar incompatibilidade sem alterar a linha externa.
- **FR-BSR-REV-008**: Estado liquidado, pago ou conciliado NÃO DEVE constituir imutabilidade adicional além das datas e contratos aplicáveis.
- **FR-BSR-REV-009**: Extrato ou lote NÃO DEVE possuir estado, ação ou ciclo de conclusão, encerramento ou reabertura; linhas, alocações, resíduos e diferenças permanecem consultáveis pelos respectivos estados operacionais.
- **FR-BSR-REV-010**: Reabertura da data de fechamento NÃO DEVE desfazer conciliações automaticamente, mas poderá voltar a permitir correções autorizadas.
- **FR-BSR-REV-011**: Aceitação de revisão apenas informativa DEVE preservar a decisão original, registrar o usuário e manter rastreabilidade entre a revisão confirmada e a vigente.
- **FR-BSR-REV-012**: Resolução de revisão material DEVE permitir desfazer e refazer alocações conforme fechamento, autorização e contratos dos módulos envolvidos, sem reescrever evidência externa.
- **FR-BSR-REV-013**: Se a revisão material exigir corrigir fato interno protegido pelo fechamento, a conciliação DEVE preservar o original e orientar compensação em data aberta, sem declarar a divergência resolvida antes dos vínculos aplicáveis.
- **FR-BSR-REV-014**: Reversão externa com identidade própria DEVE manter resíduo conciliável independente e somente poderá ser resolvida por vínculo ou criação de fato compensatório distinto.
- **FR-BSR-REV-015**: Nenhuma revisão, substituição, exclusão ou reversão externa DEVE criar correção, estorno, compensação ou novo efeito interno sem decisão explícita de usuário autorizado.

### Autorização, Auditoria e Privacidade

- **FR-BSR-SEC-001**: Importar, receber, consultar, sugerir, criar regra, confirmar, confirmar em lote, desfazer e auditar DEVEM admitir chaves de acesso distintas.
- **FR-BSR-SEC-002**: Acesso à linha externa NÃO DEVE conceder automaticamente acesso ao documento interno candidato ou conciliado.
- **FR-BSR-SEC-003**: Confirmação DEVE exigir simultaneamente acesso à conciliação e às operações dos módulos que produzirão ou alterarão efeitos.
- **FR-BSR-SEC-004**: Usuário sem acesso a valores, identificadores, contrapartes ou documentos NÃO DEVE inferi-los por pontuações, totais, filtros, erros ou auditoria.
- **FR-BSR-SEC-005**: Conteúdo de extrato, referências bancárias e identificadores completos DEVEM ser protegidos conforme sua sensibilidade e revelados somente quando necessários.
- **FR-BSR-SEC-006**: Consultar ou baixar payload bruto DEVE exigir permissão específica, tenant explícito, finalidade legítima e auditoria, sem expor segredo eventualmente segregado pela origem.
- **FR-BSR-AUD-001**: Importação, repetição, conflito, revisão externa, sugestão apresentada, decisão, alocação, regra, criação de fato e desfazimento DEVEM produzir trilha auditável.
- **FR-BSR-AUD-002**: Auditoria de decisão DEVE preservar ator, tenant, instante, linha vigente, candidatos considerados, explicação apresentada, escolha, resíduos e efeitos.
- **FR-BSR-AUD-003**: Operação sistêmica DEVE registrar origem identificável sem atribuí-la falsamente a usuário.
- **FR-BSR-AUD-004**: Auditoria NÃO DEVE armazenar segredo de conexão, credencial nem duplicar conteúdo sensível desnecessário.
- **FR-BSR-AUD-005**: Auditoria da ingestão DEVE vincular payload, hash, parser, mapeamento, opções de normalização, lote e linhas produzidas sem copiar integralmente o conteúdo bruto para logs ou eventos.

### Experiência e Acessibilidade

- **FR-BSR-UX-001**: Interface DEVE distinguir visual e textualmente linha externa, candidato, fato interno, alocação, resíduo e decisão confirmada.
- **FR-BSR-UX-002**: Pontuação NÃO DEVE depender somente de cor e DEVE ser acompanhada de explicação compreensível.
- **FR-BSR-UX-003**: Usuário DEVE poder filtrar por conta, período, origem, lote, estado externo, situação de conciliação, direção, valor e contraparte.
- **FR-BSR-UX-004**: Jornada DEVE permitir avançar entre linhas pendentes sem perder filtros, seleção ou trabalho parcial.
- **FR-BSR-UX-005**: Confirmação DEVE apresentar antecipadamente vínculos, rateios, resíduos, novos fatos e documentos que serão afetados.
- **FR-BSR-UX-006**: Ação destrutiva ou desfazimento DEVE apresentar impacto, exigir confirmação e informar quando fato compensatório será necessário.
- **FR-BSR-UX-007**: Erros de lote DEVEM identificar itens com falha sem ocultar sucessos nem exigir reinício integral.
- **FR-BSR-UX-008**: Jornadas principais DEVEM ser operáveis por teclado e tecnologias assistivas em todos os form factors web suportados.

### Confiabilidade e Operação

- **FR-BSR-INFRA-IDEMP**: Ingestão e confirmação DEVEM possuir identidades estáveis no escopo do tenant, origem, conta e operação, sem prazo que permita duplicar efeitos históricos.
- **FR-BSR-INFRA-LOCK**: Importação, alocação e confirmação concorrentes DEVEM preservar uma única sequência válida em todas as instâncias da aplicação.
- **FR-BSR-INFRA-ATOMIC**: Lote confirmado, decisão conjunta e efeito financeiro acoplado DEVEM concluir integralmente dentro de sua fronteira ou permanecer retomáveis sem sucesso enganoso.
- **FR-BSR-INFRA-SCHED**: Importações por arquivo e cálculo de candidatos DEVEM ocorrer sob demanda; esta feature NÃO DEVE consultar instituições periodicamente, embora possa receber entregas iniciadas por `financial-connectivity`.
- **FR-BSR-INFRA-KEY**: Esta feature NÃO DEVE possuir segredo de conexão bancária nem política própria de rotação de credenciais externas.
- **FR-BSR-INFRA-BACKUP**: Lotes, linhas, revisões, alocações, decisões, regras e auditorias DEVEM participar dos backups e restaurações do tenant conforme `tenant-data-governance`.
- **FR-BSR-OPS-001**: Processamento de lote extenso DEVE permitir acompanhamento de progresso, conclusão, falha e retomada sem duplicidade.
- **FR-BSR-OPS-002**: Falha ao pontuar uma linha NÃO DEVE impedir a análise das demais e DEVE deixar a linha disponível para busca manual.
- **FR-BSR-OPS-003**: Logs e métricas DEVEM identificar tenant, origem, lote, etapa e resultado sem expor conteúdo financeiro protegido.
- **FR-BSR-OPS-004**: Consulta DEVE usar intervalo e paginação, sem carregar indefinidamente todo o histórico da conta.

## Key Entities

- **Origem do Extrato**: canal que forneceu os dados, como arquivo OFX, arquivo CSV ou conexão externa, sem conter a credencial da instituição.
- **Lote de Importação**: unidade auditável de ingestão que identifica origem, conta, período, saldos, conteúdo recebido, resultado e repetição.
- **Sublote de Arquivo**: unidade atômica de confirmação formada por uma combinação de conta e moeda dentro do payload importado.
- **Payload Original da Ingestão**: bytes exatos do arquivo ou envelope bruto permitido da origem conectada, preservados de forma imutável e protegida com metadados capazes de explicar o processamento.
- **Extrato Externo**: agrupamento informado pela origem para determinada conta e período, quando o formato oferecer esse conceito.
- **Linha de Extrato Externo**: observação financeira externa imutável em sua identidade, com conteúdo original, valores normalizados, estado externo e histórico de revisões.
- **Identidade Interna da Linha**: identificador único e imutável atribuído pelo Rinos a cada ocorrência importada, inclusive quando várias possuem conteúdo idêntico.
- **Identificador Externo da Linha**: chave fornecida pela origem e avaliada no escopo da instituição e da conta quando estiver disponível e for estável.
- **Impressão Canônica da Linha**: resultado determinístico e versionado dos campos normalizados usados para encontrar conteúdos equivalentes, sem garantia de unicidade.
- **Multiplicidade da Impressão**: quantidade de ocorrências distintas que compartilham a mesma impressão em determinado conteúdo recebido ou conjunto comparado.
- **Revisão da Linha**: novo conteúdo, correção, reversão ou exclusão informada pela origem para uma identidade externa já conhecida.
- **Candidato de Correspondência**: possibilidade calculada entre linha e registro interno, acompanhada de pontuação, sinais e versão dos critérios.
- **Associação Provisória**: vínculo não monetário e não conciliatório entre linha pendente e candidato, preservado apenas para revisão quando a origem efetivar, substituir ou retirar a linha.
- **Regra de Sugestão**: configuração versionada que propõe candidato, classificação ou preenchimento sem confirmar a decisão.
- **Decisão de Conciliação**: confirmação humana auditável vinculada à revisão exata da linha apresentada, que aceita vínculos, rateios e eventuais novos efeitos financeiros.
- **Alocação de Conciliação**: parcela monetária que relaciona uma linha externa a um lançamento financeiro interno, permitindo cardinalidades flexíveis e preservando contextos funcionais sem lhes atribuir saldo próprio.
- **Resíduo Conciliável**: valor ainda não alocado de uma linha externa ou de um lançamento financeiro.
- **Conflito de Ingestão**: divergência de identidade ou conteúdo que impede tratar uma repetição como equivalente sem análise.

## Success Criteria

- **SC-BSR-001**: Em 100% das importações, nenhuma linha externa altera saldo ou cria fato interno antes de confirmação humana.
- **SC-BSR-002**: Em 100% das repetições idênticas de arquivo ou entrega conectada, nenhuma linha externa é duplicada.
- **SC-BSR-003**: Em 100% dos conflitos de mesma identidade com conteúdo diferente, ambas as versões permanecem rastreáveis e nenhuma é sobrescrita silenciosamente.
- **SC-BSR-004**: Em 100% das sugestões, pontuação, fatores favoráveis, contrários e ausentes permanecem consultáveis antes da decisão.
- **SC-BSR-005**: Em 100% dos candidatos, nenhuma pontuação produz confirmação, vínculo ou efeito sem ação do usuário.
- **SC-BSR-006**: Em 100% das conciliações integrais, alocações reconciliam exatamente linhas e registros internos na precisão da moeda.
- **SC-BSR-007**: Em 100% das conciliações parciais, resíduos externo e interno permanecem explícitos e disponíveis sem duplicidade.
- **SC-BSR-008**: Em 100% das confirmações com fatos preexistentes, nenhum novo efeito é aplicado ao saldo.
- **SC-BSR-009**: Em 100% das confirmações que criam fatos, efeito e vínculo concluem juntos ou nenhum deles permanece como sucesso.
- **SC-BSR-010**: Em 100% dos reprocessamentos de decisão, nenhum lançamento, transferência, pagamento, recebimento, liquidação ou vínculo é duplicado.
- **SC-BSR-011**: Em 100% das correções externas, conciliações afetadas são sinalizadas e nenhum fato financeiro é alterado automaticamente.
- **SC-BSR-012**: Em 100% das tentativas de correção protegidas pelo fechamento, fatos bloqueados permanecem inalterados e a alternativa compensatória é indicada.
- **SC-BSR-013**: Em 100% dos testes cruzados, dados, candidatos, regras e totais de um tenant não são acessados nem inferidos por outro.
- **SC-BSR-014**: Usuário autorizado importa e valida um OFX comum de uma única conta em até 2 minutos nos testes de usabilidade.
- **SC-BSR-015**: Pelo menos 95% das consultas de candidatos para uma linha retornam resultado utilizável em até 2 segundos nas condições operacionais da primeira versão.
- **SC-BSR-016**: Pelo menos 95% das consultas paginadas de lotes e linhas retornam resultado utilizável em até 2 segundos nas condições operacionais da primeira versão.
- **SC-BSR-017**: Em 100% das confirmações em lote, cada item possui resultado individual e nenhuma falha é apresentada como sucesso.
- **SC-BSR-018**: Todas as jornadas principais podem ser concluídas somente por teclado e sem bloqueios críticos para tecnologias assistivas.
- **SC-BSR-019**: Em 100% dos testes com duas ou mais movimentações legitimamente idênticas, todas recebem identidade interna própria e nenhuma é descartada pela impressão compartilhada.
- **SC-BSR-020**: Em 100% das reimportações sem identificador externo nas quais a multiplicidade não puder ser determinada com segurança, nenhuma ocorrência candidata é descartada sem decisão humana.
- **SC-BSR-021**: Em 100% dos testes, selecionar um lançamento diretamente ou por pagamento, recebimento, liquidação, transferência ou outro contexto consome o mesmo resíduo uma única vez e não permite dupla conciliação.
- **SC-BSR-022**: Em 100% das linhas pendentes, sugestões e associações provisórias não consomem resíduos nem criam fatos; somente a linha efetivada pode ser conciliada após nova decisão humana.
- **SC-BSR-023**: Em 100% das ingestões aceitas, payload, hash, parser e mapeamento permanecem rastreáveis sob autorização específica; prévias abandonadas e lotes rejeitados não conservam o conteúdo bruto completo.
- **SC-BSR-024**: Em 100% das revisões externas de linhas conciliadas, a decisão original permanece vinculada à versão confirmada, mudanças materiais bloqueiam novas alocações e nenhum fato financeiro é alterado sem decisão humana.
- **SC-BSR-025**: Em 100% dos arquivos, cada sublote é aceito integralmente ou rejeitado sem linhas parciais; em 100% das entregas conectadas, resultados por linha são retomáveis e nenhuma falha ou pendência é apresentada como sucesso.

## Fora do Escopo

- Consentimento, autenticação, credenciais, tokens, comunicação e sincronização com instituições financeiras.
- Execução de pagamento, transferência, débito, cobrança ou qualquer ordem em instituição externa.
- Emissão de boleto, Pix de cobrança, link de pagamento ou arquivo de remessa.
- Conciliação contábil, fiscal, de estoque ou de documentos não financeiros.
- Criação ou alteração automática de fatos apenas por pontuação, regra ou histórico.
- Treinamento compartilhado entre tenants ou envio de dados financeiros para aprendizado externo.
- Alteração do saldo de abertura para forçar igualdade com o extrato.
- Snapshots de saldo interno como substitutos dos lançamentos financeiros.
- Correção destrutiva de fatos protegidos pela data de fechamento financeiro.
- Importação de posições, cotações e eventos de investimentos.
- Extratos, faturas externas e conciliação com operadoras de cartão de crédito, adiados para `credit-card-statements-reconciliation`.

## Integração com Controle de Acesso

Importação, consulta e confirmação de conciliação usam chaves distintas `tenant.financial.statement.import` e `tenant.financial.reconciliation.*` do [catálogo canônico](../access-control/contracts/access-key-catalog.md); conciliar não implica criar lançamento.
