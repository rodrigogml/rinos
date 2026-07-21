# Feature Specification: Dados de Pagamento de Pessoas

**Feature**: `party-payment-details`
**Created**: 2026-07-21
**Status**: Clarified — pronta para planejamento

## Escopo

Esta feature permite manter instruções de pagamento associadas às pessoas do tenant para que módulos financeiros, comerciais, trabalhistas e administrativos possam identificar com segurança onde pagar ou receber valores. A primeira versão contempla contas bancárias nacionais e internacionais e chaves Pix, com finalidades, preferência, vigência, verificação, proteção e auditoria próprias.

Um dado de pagamento descreve um destino ou meio informado para movimentar valores em favor de uma pessoa. Ele não representa uma conta financeira do tenant, não possui saldo, extrato ou lançamentos e não autoriza qualquer movimentação por si próprio.

> [!IMPORTANT]
> Dados de pagamento são informações financeiras sensíveis. A consulta deve apresentar valores mascarados por padrão, e o acesso ao conteúdo completo deve ser excepcional, autorizado e auditado.

> [!NOTE]
> Esta feature não armazena senhas bancárias, credenciais de internet banking, tokens de integração, códigos de segurança nem dados completos de cartões. Integrações bancárias e meios de pagamento adicionais exigirão especificações próprias.

## Clarifications

### Session 2026-07-21

- Q: O dado de pagamento pode possuir titular diferente da pessoa beneficiária? → A: Sim, desde que cada titular seja uma pessoa cadastrada no mesmo tenant. A divergência exige justificativa, sinalização e auditoria, e cada módulo consumidor pode proibir pagamentos a terceiros em seu próprio contexto.
- Q: Como deve funcionar a verificação dos dados de pagamento na primeira versão? → A: Todo dado começa como declarado e pode receber conferência manual documentada; futura verificação externa será identificada separadamente. A verificação é opcional na feature, e cada módulo consumidor define o nível exigido conforme o risco.
- Q: Em qual contexto deve existir um dado de pagamento preferencial? → A: Deve existir no máximo um preferencial por pessoa, finalidade e moeda; o mesmo dado pode ser preferencial em vários contextos, sem autorizar nem executar pagamentos automaticamente.

## User Scenarios & Testing

### User Story 1 - Cadastrar conta bancária de uma pessoa (Priority: P1)

Um usuário autorizado registra uma conta bancária indicada para uma pessoa, informando os dados compatíveis com o país e o sistema bancário aplicável, para que ela possa ser selecionada em pagamentos futuros.

**Why this priority**: Pagamentos a fornecedores, colaboradores e outras contrapartes precisam de uma instrução bancária reutilizável e independente do cadastro de contas financeiras do tenant.

**Independent Test**: Cadastrar uma conta bancária brasileira, consultá-la de forma mascarada e selecioná-la para uma finalidade autorizada sem criar saldo, extrato ou conta financeira.

**Acceptance Scenarios**:

1. **Given** uma pessoa ativa e um usuário autorizado, **When** informa uma conta bancária válida para o país selecionado, **Then** o sistema registra a instrução exclusivamente no tenant atual.
2. **Given** uma conta cujo país não utiliza agência ou dígito verificador, **When** o usuário a cadastra, **Then** o sistema exige somente os identificadores aplicáveis àquele esquema bancário.
3. **Given** uma conta com combinação inválida de instituição, país ou identificadores, **When** o usuário tenta confirmar, **Then** o sistema indica os campos incompatíveis sem armazenar uma instrução aparentemente válida.
4. **Given** uma conta cujo titular difere da pessoa beneficiária, **When** o usuário informa titular cadastrado e justificativa, **Then** o sistema sinaliza a divergência, audita a decisão e permite que o módulo consumidor aplique sua própria restrição.

---

### User Story 2 - Cadastrar chave Pix de uma pessoa (Priority: P1)

Um usuário autorizado registra uma ou várias chaves Pix indicadas para a pessoa, distinguindo o tipo da chave e sua finalidade, para utilizá-las em operações compatíveis.

**Why this priority**: Pix é um meio recorrente de pagamento e recebimento no Brasil e não deve ser improvisado como número de conta ou informação de contato comum.

**Independent Test**: Cadastrar chaves Pix de tipos diferentes, validar e mascarar cada formato e impedir duplicidade silenciosa dentro do tenant.

**Acceptance Scenarios**:

1. **Given** uma pessoa ativa, **When** o usuário autorizado registra chave Pix válida e informa seu tipo, **Then** a chave é normalizada, protegida e disponibilizada somente para finalidades compatíveis.
2. **Given** uma chave em formato incompatível com o tipo escolhido, **When** a confirmação é solicitada, **Then** o sistema rejeita o cadastro e explica a correção necessária.
3. **Given** uma chave igual já registrada no tenant, **When** o usuário tenta registrá-la novamente, **Then** o sistema alerta sobre a possível duplicidade sem revelar dados de pessoa que ele não possa consultar.

---

### User Story 3 - Definir finalidade e preferência (Priority: P1)

Um usuário autorizado indica para quais finalidades cada dado de pagamento pode ser usado e qual deve ser sugerido quando houver múltiplas opções elegíveis.

**Why this priority**: Uma pessoa pode receber salários, reembolsos, pagamentos comerciais e devoluções em destinos distintos; uma preferência única e genérica pode direcionar valores incorretamente.

**Independent Test**: Cadastrar múltiplos dados para a mesma pessoa, atribuir finalidades diferentes e verificar que cada operação sugere somente a opção elegível e preferencial para seu contexto.

**Acceptance Scenarios**:

1. **Given** vários dados ativos da mesma pessoa, **When** um deles é definido como preferencial para uma finalidade, **Then** o sistema remove qualquer ambiguidade entre preferenciais equivalentes.
2. **Given** uma operação que exige determinada finalidade e moeda, **When** o usuário seleciona o destinatário, **Then** somente dados elegíveis são sugeridos por padrão.
3. **Given** nenhum dado preferencial elegível, **When** a operação solicita uma instrução, **Then** o sistema exige seleção explícita e não escolhe silenciosamente um destino incompatível.

---

### User Story 4 - Consultar e utilizar dados com proteção (Priority: P2)

Um usuário autorizado pesquisa dados de pagamento de uma pessoa, visualiza apenas as informações necessárias e, quando possuir autorização adicional, revela ou copia o conteúdo completo para uma operação legítima.

**Why this priority**: O dado precisa ser utilizável sem ampliar desnecessariamente a exposição de informações financeiras sensíveis.

**Independent Test**: Consultar a mesma pessoa com permissões de visualização mascarada e completa, verificando diferenças de conteúdo e auditoria do acesso completo.

**Acceptance Scenarios**:

1. **Given** um usuário com acesso apenas à consulta comum, **When** abre a lista de dados de pagamento, **Then** vê instituição, tipo, finalidade, estado e identificadores mascarados.
2. **Given** um usuário com acesso ao conteúdo completo, **When** solicita revelar ou copiar um dado, **Then** o sistema confirma a finalidade, apresenta o valor e audita o acesso.
3. **Given** um usuário sem autorização para os dados de pagamento, **When** consulta a pessoa, **Then** não recebe valores, contagens ou mensagens que confirmem sua existência.

---

### User Story 5 - Corrigir, desativar e preservar histórico (Priority: P2)

Um usuário autorizado substitui uma instrução alterada, desativa um destino que não deve mais receber pagamentos e preserva exatamente o que foi utilizado nas operações anteriores.

**Why this priority**: Alterar silenciosamente agência, conta ou chave Pix poderia fazer uma operação histórica aparentar ter usado um destino diferente e direcionar novos pagamentos de forma indevida.

**Independent Test**: Utilizar um dado em uma operação, substituir seu identificador crítico e confirmar que a operação anterior mantém o destino original enquanto novas seleções usam somente a versão vigente.

**Acceptance Scenarios**:

1. **Given** um dado já utilizado, **When** um identificador crítico precisa mudar, **Then** o sistema encerra a versão anterior e cria nova versão rastreável.
2. **Given** um dado desativado, **When** uma nova operação procura destinos, **Then** ele não aparece por padrão e continua identificável em referências históricas autorizadas.
3. **Given** um cadastro lançado por engano e ainda não utilizado, **When** o usuário autorizado solicita sua exclusão, **Then** o sistema permite a remoção conforme a política de auditoria e privacidade.

### Edge Cases

- Conta conjunta utilizada por mais de uma pessoa do tenant.
- Dado de pagamento cujo titular legal é diferente da pessoa beneficiária.
- Pessoa sem CPF ou CNPJ que possui conta bancária válida.
- Conta de instituição estrangeira sem agência, dígito ou formato brasileiro.
- IBAN, BIC/SWIFT ou código de roteamento válido para determinado país.
- Chave Pix de e-mail ou telefone diferente dos contatos cadastrados na pessoa.
- Chave Pix CPF/CNPJ diferente dos identificadores cadastrados na pessoa.
- Mesma conta ou chave informada para pessoas diferentes.
- Dado preferencial desativado enquanto existem outras opções elegíveis.
- Pessoa desativada enquanto possui dados de pagamento ativos.
- Alteração concorrente da preferência ou dos identificadores críticos.
- Importação que diverge de um dado cadastrado manualmente.
- Usuário autorizado a executar pagamento, mas não a administrar ou revelar os dados completos.
- Solicitação de anonimização ou eliminação sujeita a histórico financeiro obrigatório.

## Requirements

### Conceitos e Fronteiras

- **FR-PPD-BOUND-001**: Todo dado de pagamento DEVE pertencer exclusivamente a uma pessoa e ao tenant dessa pessoa.
- **FR-PPD-BOUND-002**: Dado de pagamento DEVE representar uma instrução reutilizável para pagar ou receber valores e NÃO DEVE possuir saldo, extrato, lançamentos ou conciliação.
- **FR-PPD-BOUND-003**: Cadastrar um dado de pagamento NÃO DEVE criar conta financeira, cartão, autorização bancária, papel de negócio, participação ou permissão.
- **FR-PPD-BOUND-004**: Uma pessoa DEVE poder possuir nenhum, um ou vários dados de pagamento de tipos distintos.
- **FR-PPD-BOUND-005**: A primeira versão DEVE suportar contas bancárias nacionais, contas bancárias internacionais e chaves Pix.
- **FR-PPD-BOUND-006**: Senhas, tokens, credenciais bancárias, códigos de segurança e dados completos de cartões NÃO DEVEM ser recebidos nem armazenados por esta feature.
- **FR-PPD-BOUND-007**: Boleto, cartão, carteira digital, débito autorizado, cobrança e integração bancária NÃO DEVEM ser tratados como variações genéricas sem especificação própria.
- **FR-PPD-BOUND-008**: O dado DEVE possuir identidade imutável independente de seus valores apresentáveis e versões históricas.

### Beneficiário e Titularidade

- **FR-PPD-HOLDER-001**: Todo dado DEVE identificar a pessoa em favor da qual ele foi cadastrado e preservar os dados declarados do titular necessários para conferir a instrução.
- **FR-PPD-HOLDER-002**: O dado PODERÁ possuir um ou vários titulares diferentes da pessoa beneficiária, desde que cada titular seja uma pessoa cadastrada no mesmo tenant.
- **FR-PPD-HOLDER-003**: Nome e identificador declarados do titular NÃO DEVEM ser inferidos ou atualizados silenciosamente a partir do cadastro-base após o dado ser utilizado.
- **FR-PPD-HOLDER-004**: Divergência entre titular declarado e pessoa beneficiária DEVE ser perceptível antes da seleção e nas confirmações de operação.
- **FR-PPD-HOLDER-005**: Ausência de CPF, CNPJ ou documento na pessoa NÃO DEVE impedir genericamente o cadastro; cada tipo de instrução e operação consumidora exige os identificadores aplicáveis.
- **FR-PPD-HOLDER-006**: Todo dado DEVE possuir ao menos um titular declarado; titular identificado NÃO DEVE ser mantido somente como texto livre quando representar pessoa diferente da beneficiária.
- **FR-PPD-HOLDER-007**: Quando a pessoa beneficiária não estiver entre os titulares, o cadastro e a seleção DEVEM exigir justificativa, apresentar alerta inequívoco e produzir auditoria específica.
- **FR-PPD-HOLDER-008**: Módulo consumidor DEVE poder impedir dado de terceiro ou exigir controles adicionais, sem que o simples cadastro autorize seu uso em qualquer operação.
- **FR-PPD-HOLDER-009**: Conta conjunta DEVE poder identificar múltiplos titulares cadastrados, sem duplicar a identidade ou os dados-base dessas pessoas.
- **FR-PPD-HOLDER-010**: O mesmo destino financeiro PODERÁ ser indicado para mais de uma pessoa beneficiária, mas a possível duplicidade DEVE ser apresentada para decisão explícita e cada indicação DEVE preservar finalidade, justificativa e auditoria próprias.
- **FR-PPD-HOLDER-011**: A referência como titular ou beneficiário DEVE impedir exclusão cadastral comum da pessoa enquanto o dado precisar ser preservado; desativação e processos de privacidade seguem `party-registration` e `tenant-data-governance`.

### Contas Bancárias

- **FR-PPD-BANK-001**: Conta bancária DEVE identificar país, instituição, esquema de identificação aplicável, tipo de conta e seus identificadores obrigatórios.
- **FR-PPD-BANK-002**: Instituições conhecidas DEVEM poder ser selecionadas por código e nome; instituição ausente do catálogo DEVE poder ser informada de maneira controlada com nome e país.
- **FR-PPD-BANK-003**: Agência, dígito de agência, número da conta e dígito da conta DEVEM ser exigidos somente quando aplicáveis ao país, instituição e esquema selecionados.
- **FR-PPD-BANK-004**: A primeira versão DEVE distinguir ao menos conta corrente, poupança, salário, pagamento, investimento e outros tipos declarados pelo esquema aplicável.
- **FR-PPD-BANK-005**: Contas internacionais DEVEM admitir identificadores como IBAN, BIC/SWIFT e códigos de roteamento sem exigir estrutura brasileira.
- **FR-PPD-BANK-006**: Valores DEVEM ser normalizados e validados segundo seu esquema, preservando apresentação apropriada ao usuário.
- **FR-PPD-BANK-007**: O sistema DEVE permitir informar moedas aceitas quando essa informação for conhecida, sem presumir que toda conta seja restrita a uma única moeda.
- **FR-PPD-BANK-008**: Similaridade com conta existente no tenant DEVE gerar alerta antes da confirmação e NÃO DEVE provocar fusão ou sobrescrita automática.
- **FR-PPD-BANK-009**: Conta bancária cadastrada para uma pessoa NÃO DEVE ser tratada como conta financeira pertencente ao tenant.

### Chaves Pix

- **FR-PPD-PIX-001**: Chave Pix DEVE identificar explicitamente seu tipo entre CPF, CNPJ, e-mail, telefone e chave aleatória.
- **FR-PPD-PIX-002**: O valor DEVE ser validado e normalizado conforme o tipo, sem alterar a chave aleatória além da normalização permitida.
- **FR-PPD-PIX-003**: E-mail ou telefone usado como chave Pix NÃO DEVE criar nem alterar meio de contato do cadastro-base.
- **FR-PPD-PIX-004**: CPF ou CNPJ usado como chave Pix NÃO DEVE criar nem alterar identificador do cadastro-base.
- **FR-PPD-PIX-005**: Pessoa DEVE poder possuir várias chaves Pix, inclusive de tipos iguais quando os valores forem distintos.
- **FR-PPD-PIX-006**: Chave Pix repetida no tenant DEVE gerar alerta e tratamento explícito antes da confirmação, sem revelar pessoa não autorizada.
- **FR-PPD-PIX-007**: O cadastro local NÃO DEVE afirmar que uma chave existe, pertence ao titular ou está registrada no arranjo Pix sem evidência de verificação correspondente.
- **FR-PPD-PIX-008**: A chave PODERÁ referenciar a conta bancária conhecida para fins organizacionais, mas essa relação NÃO DEVE ser obrigatória nem presumida sem confirmação.

### Finalidades e Preferências

- **FR-PPD-PURPOSE-001**: Todo dado DEVE poder receber rótulo livre para identificação e uma ou várias finalidades canônicas de uso.
- **FR-PPD-PURPOSE-002**: O sistema e seus módulos DEVEM poder fornecer finalidades canônicas como uso geral, pagamento a fornecedor, salário, reembolso e devolução.
- **FR-PPD-PURPOSE-003**: Finalidade DEVE limitar elegibilidade e preferência, mas NÃO DEVE conceder autorização para consultar ou executar pagamentos.
- **FR-PPD-PURPOSE-004**: Para cada combinação de pessoa, finalidade e moeda, DEVE existir no máximo um dado de pagamento preferencial vigente.
- **FR-PPD-PURPOSE-005**: Uma operação consumidora DEVE declarar finalidade, moeda e demais restrições necessárias antes de solicitar opções elegíveis.
- **FR-PPD-PURPOSE-006**: Ausência de opção preferencial elegível NÃO DEVE autorizar seleção silenciosa de dado incompatível.
- **FR-PPD-PURPOSE-007**: Alterar finalidade ou preferência NÃO DEVE modificar operações que já preservaram a instrução utilizada.
- **FR-PPD-PURPOSE-008**: O mesmo dado de pagamento PODERÁ ser preferencial em várias combinações de finalidade e moeda quando for elegível em todas elas.
- **FR-PPD-PURPOSE-009**: Preferência NÃO DEVE autorizar, aprovar nem executar pagamento e DEVE permanecer sujeita às restrições e confirmações do módulo consumidor.
- **FR-PPD-PURPOSE-010**: Quando o dado preferencial estiver desativado, substituído, incompatível ou abaixo do nível de verificação exigido, o sistema DEVE deixar o contexto sem preferência válida e exigir seleção explícita, sem eleger outro destino automaticamente.
- **FR-PPD-PURPOSE-011**: Alterações concorrentes de preferência DEVEM preservar no máximo um preferencial vigente em cada combinação de pessoa, finalidade e moeda.

### Verificação, Estado e Ciclo de Vida

- **FR-PPD-VERIFY-001**: Todo dado DEVE apresentar estado de vigência separado de sua situação de verificação.
- **FR-PPD-VERIFY-002**: Todo dado novo DEVE iniciar como declarado e PODERÁ receber conferência manual documentada; futura verificação externa DEVE ser identificada separadamente e somente após integração específica.
- **FR-PPD-VERIFY-003**: A situação de verificação DEVE indicar o que foi efetivamente comprovado e NÃO DEVE sugerir validação bancária inexistente.
- **FR-PPD-VERIFY-004**: Módulo consumidor DEVE declarar se aceita dado apenas declarado ou exige determinado nível de verificação.
- **FR-PPD-VERIFY-005**: Conferência manual DEVE exigir autorização específica e registrar responsável, instante, método, alcance, resultado e referência à evidência utilizada.
- **FR-PPD-VERIFY-006**: Dado conferido manualmente NÃO DEVE ser apresentado como confirmado pela instituição financeira, pelo arranjo Pix ou por serviço externo.
- **FR-PPD-VERIFY-007**: Verificação externa futura DEVE identificar fonte, instante, alcance, resultado e versão exata dos dados submetidos, sem transformar simples validação de formato em prova de titularidade.
- **FR-PPD-VERIFY-008**: Conferência ou verificação DEVE pertencer à versão exata do dado; substituição de qualquer identificador crítico DEVE iniciar a nova versão como declarada.
- **FR-PPD-VERIFY-009**: A feature NÃO DEVE tornar conferência obrigatória para todo cadastro; cada módulo consumidor DEVE aplicar exigência proporcional ao risco de sua operação.
- **FR-PPD-VERIFY-010**: Evidências DEVEM ser acessíveis somente a usuários autorizados e respeitar minimização, retenção e descarte definidos em `tenant-data-governance`.
- **FR-PPD-VERIFY-011**: Situações de declaração, conferência manual, verificação externa, falha e expiração, quando aplicáveis, DEVEM ser distinguíveis sem ocultar o histórico de avaliações.
- **FR-PPD-LIFE-001**: Dado de pagamento DEVE possuir estados ativo e desativado; versões substituídas DEVEM permanecer identificáveis como encerradas.
- **FR-PPD-LIFE-002**: Alteração de instituição, esquema, titular, agência, conta, IBAN, código de roteamento, tipo ou valor de chave Pix DEVE criar nova versão em vez de reescrever o identificador anteriormente utilizado.
- **FR-PPD-LIFE-003**: Alterações de rótulo, finalidade e preferência PODERÃO atualizar a versão vigente com auditoria, desde que não mudem a instrução financeira preservada.
- **FR-PPD-LIFE-004**: Dado desativado ou substituído NÃO DEVE aparecer nas seleções comuns para novas operações.
- **FR-PPD-LIFE-005**: Reativação NÃO DEVE ocorrer automaticamente quando pessoa, instituição ou outro dado relacionado for reativado.
- **FR-PPD-LIFE-006**: Dado nunca utilizado nem referenciado PODERÁ ser excluído definitivamente por usuário autorizado; dado referenciado somente PODERÁ ser desativado ou substituído.
- **FR-PPD-LIFE-007**: Pessoa desativada NÃO DEVE ter seus dados sugeridos para novas operações comuns, sem apagar referências históricas.
- **FR-PPD-LIFE-008**: Eliminação ou anonimização DEVE respeitar retenção financeira, auditoria e `tenant-data-governance`.

### Pesquisa e Uso por Outros Módulos

- **FR-PPD-USE-001**: Usuário autorizado DEVE poder listar dados por pessoa, tipo, instituição, finalidade, estado e situação de verificação.
- **FR-PPD-USE-002**: Resultados comuns DEVEM apresentar identificadores mascarados e informações suficientes para distinguir opções sem exposição completa.
- **FR-PPD-USE-003**: Seleções DEVEM permanecer limitadas ao tenant ativo e NÃO DEVEM confirmar dados cadastrados em outro tenant.
- **FR-PPD-USE-004**: Módulo consumidor DEVE preservar snapshot suficiente da instrução efetivamente utilizada para que alterações posteriores não reescrevam o histórico.
- **FR-PPD-USE-005**: O vínculo com a identidade imutável e a versão do dado PODERÁ ser mantido para rastreabilidade, sem substituir o snapshot necessário à operação.
- **FR-PPD-USE-006**: Importação ou integração NÃO DEVE sobrescrever silenciosamente informação manual divergente; conflitos DEVEM ser apresentados ou registrados para tratamento explícito.
- **FR-PPD-USE-007**: A utilização de um dado em uma operação NÃO comprova automaticamente sua titularidade, validade bancária ou segurança para operações futuras.

### Autorização, Proteção e Auditoria

- **FR-PPD-SEC-001**: Acesso aos dados de pagamento DEVE ser negado por padrão e liberado por chaves específicas do tenant.
- **FR-PPD-SEC-002**: Consultar conteúdo mascarado, revelar conteúdo completo, criar, alterar, definir preferência, verificar, desativar e excluir DEVEM poder ser autorizados separadamente.
- **FR-PPD-SEC-003**: Interfaces e operações internas DEVEM aplicar o mesmo contrato de autorização e isolamento.
- **FR-PPD-SEC-004**: Agência, conta, IBAN, códigos de roteamento, chave Pix e identificador do titular DEVEM ser mascarados por padrão em telas, mensagens, exportações e resultados de pesquisa.
- **FR-PPD-SEC-005**: Revelação, cópia ou exportação de conteúdo completo DEVE exigir finalidade legítima, autorização específica e auditoria.
- **FR-PPD-SEC-006**: Dados sensíveis persistidos e transmitidos DEVEM receber proteção compatível com seu risco e permanecer protegidos também em backups.
- **FR-PPD-SEC-007**: Logs, erros, métricas e trilhas técnicas NÃO DEVEM conter valores completos de contas, chaves Pix ou identificadores financeiros.
- **FR-PPD-SEC-008**: Criação, alteração, verificação, revelação, cópia, mudança de preferência, desativação, exclusão e tentativa sensível negada DEVEM registrar ator, tenant, instante, origem, resultado, motivo e efeito.
- **FR-PPD-SEC-009**: Usuário sem autorização sobre o dado NÃO DEVE inferir sua existência por contagens, validação de duplicidade, mensagens de erro ou auditorias.
- **FR-PPD-SEC-010**: Exportações DEVEM minimizar dados, identificar sua sensibilidade e respeitar finalidade e retenção.

### Experiência e Acessibilidade

- **FR-PPD-UX-001**: A interface DEVE distinguir claramente conta bancária, chave Pix e conta financeira do tenant.
- **FR-PPD-UX-002**: Formulários DEVEM adaptar campos e validações ao país, esquema e tipo selecionados sem exigir estrutura brasileira quando inaplicável.
- **FR-PPD-UX-003**: Antes de confirmar, o usuário DEVE revisar titular, beneficiário, instituição, identificadores mascarados, finalidades e possíveis duplicidades.
- **FR-PPD-UX-004**: Ação de revelar ou copiar conteúdo completo DEVE ser explícita e não poderá ocorrer automaticamente ao abrir a tela.
- **FR-PPD-UX-005**: Estado, preferência e situação de verificação DEVEM ser perceptíveis sem depender exclusivamente de cor.
- **FR-PPD-UX-006**: Jornadas principais DEVEM ser realizáveis por teclado e tecnologias assistivas, com erros associados aos campos correspondentes.

### Decisões de Infraestrutura Auditáveis

> Decisões de infraestrutura: não há agendamento nem refresh de token externo próprios desta primeira versão. Eventual verificação externa deverá definir sua política quando a integração correspondente for especificada.

- **FR-PPD-INFRA-KEY**: A proteção criptográfica de dados persistidos DEVE admitir identificação da versão da chave, rotação controlada e leitura segura durante a transição, sem exigir exposição dos valores em texto puro.
- **FR-PPD-INFRA-IDEMP**: Repetição técnica da mesma solicitação confirmada DEVE produzir no máximo um cadastro, versão ou mudança de estado.
- **FR-PPD-INFRA-LOCK**: Operações concorrentes DEVEM preservar versão vigente, preferência, duplicidade e histórico determinísticos em todas as instâncias da aplicação.
- **FR-PPD-INFRA-BACKUP**: Dados de pagamento, versões e auditorias DEVEM participar dos backups e restaurações do tenant segundo `tenant-data-governance`, mantendo a mesma proteção dos dados ativos.

### Key Entities

- **Dado de Pagamento**: identidade estável da instrução associada à pessoa beneficiária, com titulares cadastrados, tipo, estado, finalidade, preferência e situação de verificação.
- **Versão do Dado de Pagamento**: conjunto imutável dos identificadores financeiros vigentes em determinado período.
- **Conta Bancária da Pessoa**: instrução bancária nacional ou internacional com instituição, país, esquema, tipo, titular e identificadores aplicáveis.
- **Chave Pix da Pessoa**: instrução Pix tipificada e protegida, declarada em favor da pessoa.
- **Finalidade de Pagamento**: contexto canônico no qual um dado pode ser sugerido ou utilizado.
- **Evidência de Verificação**: registro do método, alcance, responsável, instante e resultado de uma verificação, quando aplicável.
- **Evento de Auditoria Financeira Sensível**: evidência de acesso ou alteração relevante sem exposição desnecessária do conteúdo protegido.

## Success Criteria

### Measurable Outcomes

- **SC-PPD-001**: Em 100% dos testes cruzados, dados de pagamento de um tenant não são consultados, alterados nem confirmados em outro tenant.
- **SC-PPD-002**: Em 100% dos testes, cadastrar dado de pagamento não cria conta financeira, saldo, lançamento, participação ou permissão.
- **SC-PPD-003**: Em 100% das listagens e pesquisas comuns testadas, os identificadores financeiros permanecem mascarados.
- **SC-PPD-004**: Em 100% das revelações, cópias e exportações completas testadas, existe autorização específica e auditoria com ator, tenant, instante e finalidade.
- **SC-PPD-005**: Usuários autorizados cadastram uma conta bancária brasileira ou chave Pix válida em até 2 minutos durante testes de usabilidade.
- **SC-PPD-006**: Em 100% dos testes internacionais, campos brasileiros inaplicáveis não são exigidos e os identificadores próprios do esquema são preservados.
- **SC-PPD-007**: Em 100% das alterações de identificadores críticos, a versão anterior permanece vinculável ao histórico e deixa de ser sugerida para novas operações.
- **SC-PPD-008**: Em 100% dos testes de finalidade e preferência, o sistema não seleciona silenciosamente uma opção incompatível.
- **SC-PPD-009**: Pelo menos 95% das consultas comuns retornam opções mascaradas e utilizáveis em até 2 segundos nas condições operacionais definidas para a primeira versão.
- **SC-PPD-010**: Em 100% dos testes concorrentes e de repetição técnica, cada solicitação confirmada produz no máximo um efeito e preserva versão e preferência determinísticas.
- **SC-PPD-011**: Logs, mensagens de erro e métricas examinados nos testes não contêm valores completos de contas, chaves Pix ou identificadores financeiros.
- **SC-PPD-012**: Todas as jornadas principais podem ser concluídas apenas por teclado e sem bloqueios críticos para tecnologias assistivas.
- **SC-PPD-013**: Em 100% dos testes com titular terceiro, todas as pessoas pertencem ao mesmo tenant, a divergência é sinalizada e existe justificativa e auditoria específica.
- **SC-PPD-014**: Em 100% dos testes, conferência manual e verificação externa são apresentadas distintamente, permanecem vinculadas à versão avaliada e não sobrevivem à alteração de identificador crítico.
- **SC-PPD-015**: Em 100% dos testes, existe no máximo um preferencial vigente por pessoa, finalidade e moeda, e a indisponibilidade desse dado não promove substituição automática.

## Fora do Escopo

- Saldos, extratos, lançamentos, conciliação e contas financeiras do tenant.
- Senhas bancárias, credenciais, tokens, consentimentos de Open Finance e iniciação de pagamentos.
- Validação automática no arranjo Pix ou consulta bancária externa sem integração específica.
- Cartões, números completos de cartão e códigos de segurança.
- Boletos, cobranças, carteiras digitais e outros meios ainda não especificados.
- Execução, aprovação ou liquidação de pagamentos.
- Cadastro-base, papéis e relacionamentos da pessoa.
- Associação entre pessoa e usuário ou participante.
