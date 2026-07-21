# Feature Specification: Cadastro Base de Pessoas

**Feature**: `party-registration`
**Created**: 2026-07-21
**Status**: Clarified — pronta para planejamento

## Escopo

Esta feature estabelece o diretório de pessoas de cada tenant. Uma pessoa representa uma pessoa física ou jurídica que poderá atuar como contraparte ou referência nos diferentes módulos do sistema, sem que o cadastro-base lhe conceda acesso, papel de negócio ou vínculo automático com um usuário.

O escopo inclui identidade cadastral, nomes, identificadores, endereços, telefones, e-mails, pesquisa, manutenção, desativação, rastreabilidade e proteção dos dados. Papéis especializados, relacionamentos entre pessoas e dados de pagamento pertencem a especificações posteriores.

> [!IMPORTANT]
> A conta/tenant, a identidade global de usuário, a participação do usuário no tenant e a pessoa cadastrada são conceitos distintos. Nenhum vínculo entre eles deve ser presumido.

## Clarifications

### Session 2026-07-21

- Q: O cadastro deve permitir pessoa sem CPF, CNPJ ou identificador equivalente? → A: Sim. A ausência de documento não torna a pessoa genericamente incompleta; cada funcionalidade consumidora deve exigir os identificadores necessários à sua própria operação.
- Q: Como deve funcionar a unicidade de CPF, CNPJ e outros identificadores? → A: A política de unicidade é definida pelo tipo e pela autoridade emissora. CPF e CNPJ são estritamente únicos dentro do tenant; outros identificadores seguem a regra declarada para seu tipo.
- Q: Qual política deve ser aplicada à exclusão, anonimização e retenção? → A: Pessoa sem papel, relacionamento ou referência mantida por outro módulo pode ser excluída definitivamente por usuário autorizado. Pessoa referenciada somente pode ser desativada; anonimização depende do processo de privacidade e das obrigações de retenção aplicáveis.

## User Scenarios & Testing

### User Story 1 - Cadastrar uma pessoa no tenant (Priority: P1)

Um usuário autorizado cadastra uma pessoa física ou jurídica com os dados disponíveis, para que ela possa ser localizada e posteriormente utilizada pelos módulos de negócio.

**Why this priority**: Sem um registro-base não há contraparte consistente para operações financeiras, comerciais, fiscais ou administrativas.

**Independent Test**: Cadastrar uma pessoa com os dados mínimos aceitos, consultá-la no mesmo tenant e comprovar que ela não é visível em outro tenant.

**Acceptance Scenarios**:

1. **Given** um usuário autorizado no tenant ativo, **When** informa dados válidos de uma pessoa física, **Then** o sistema cria um registro pertencente exclusivamente àquele tenant.
2. **Given** um usuário autorizado no tenant ativo, **When** informa dados válidos de uma pessoa jurídica, **Then** o sistema cria o registro sem atribuir automaticamente papel de cliente, fornecedor ou outro papel de negócio.
3. **Given** um cadastro em andamento com dados inválidos, **When** o usuário solicita a confirmação, **Then** o sistema identifica os campos problemáticos sem salvar um registro aparentemente válido.

---

### User Story 2 - Localizar e selecionar uma pessoa (Priority: P1)

Um usuário autorizado pesquisa o diretório do tenant por nome, nome alternativo ou identificador para localizar uma contraparte sem precisar conhecer seu identificador interno.

**Why this priority**: O valor do diretório depende de permitir seleção segura, rápida e inequívoca pelas demais funcionalidades.

**Independent Test**: Criar pessoas com nomes semelhantes, pesquisá-las por critérios distintos e selecionar apenas registros pertencentes ao tenant ativo.

**Acceptance Scenarios**:

1. **Given** pessoas cadastradas no tenant, **When** o usuário pesquisa por parte do nome, **Then** recebe resultados autorizados com informações suficientes para diferenciá-las.
2. **Given** uma pessoa com identificador cadastrado, **When** o usuário autorizado pesquisa pelo identificador, **Then** o registro correspondente é encontrado sem revelar dados de outros tenants.
3. **Given** um identificador pertencente somente a outro tenant, **When** o usuário o pesquisa no tenant atual, **Then** o sistema não confirma a existência externa.

---

### User Story 3 - Manter dados cadastrais e contatos (Priority: P2)

Um usuário autorizado atualiza nomes, identificadores, endereços, telefones e e-mails, mantendo múltiplos meios de contato quando necessário e identificando quais estão vigentes e quais devem ser preferidos.

**Why this priority**: Contrapartes mudam seus dados ao longo do tempo e diferentes operações podem exigir contatos ou endereços distintos.

**Independent Test**: Alterar os dados de uma pessoa, adicionar múltiplos contatos e endereços e verificar o estado atualizado sem modificar registros históricos de outros módulos.

**Acceptance Scenarios**:

1. **Given** uma pessoa ativa, **When** o usuário autorizado altera seus dados atuais, **Then** o sistema preserva autoria, instante e conteúdo relevante da alteração.
2. **Given** múltiplos endereços ou meios de contato, **When** um deles é marcado como preferencial para uma finalidade, **Then** o sistema impede ambiguidade entre preferenciais vigentes da mesma finalidade.
3. **Given** uma pessoa referenciada por outro módulo, **When** seus dados atuais são alterados, **Then** documentos históricos que tenham preservado seus próprios dados não são reescritos.

---

### User Story 4 - Desativar e reativar uma pessoa (Priority: P2)

Um usuário autorizado retira do uso corrente um cadastro que não deve mais ser selecionado, preservando referências e histórico, e pode reativá-lo quando a situação for corrigida.

**Why this priority**: Cadastros referenciados não podem ser simplesmente apagados, mas também não devem continuar disponíveis para novas operações comuns.

**Independent Test**: Desativar uma pessoa já referenciada, confirmar sua ausência nas seleções comuns e sua presença em consultas históricas, e depois reativá-la.

**Acceptance Scenarios**:

1. **Given** uma pessoa ativa, **When** um usuário autorizado a desativa, **Then** ela deixa de aparecer como opção padrão para novas operações e permanece consultável no histórico autorizado.
2. **Given** uma pessoa desativada, **When** uma referência histórica é consultada, **Then** o sistema continua identificando a pessoa sem reativá-la.
3. **Given** uma pessoa desativada, **When** um usuário autorizado a reativa, **Then** ela volta a poder ser selecionada em novas operações compatíveis.

### Edge Cases

- Pessoa ainda sem CPF, CNPJ ou identificador equivalente.
- Pessoa estrangeira sem identificadores brasileiros e com endereço internacional.
- Duas pessoas com nomes iguais ou muito semelhantes no mesmo tenant.
- Tentativa de cadastrar identificador já utilizado no tenant.
- Mudança informada entre pessoa física e pessoa jurídica após o cadastro.
- Identificador válido em formato, mas atribuído à pessoa errada.
- Contato ou endereço repetido na mesma pessoa.
- Pessoa desativada ainda referenciada por documentos ou operações futuras.
- Importação concorrente do mesmo cadastro por funcionalidades diferentes.
- Solicitação de correção, anonimização ou eliminação de dados pessoais sujeitos a obrigações de retenção.

## Requirements

### Identidade e Escopo

- **FR-PTY-ID-001**: Toda pessoa DEVE pertencer exclusivamente a um tenant e somente poderá ser criada, consultada ou alterada em contexto de tenant válido.
- **FR-PTY-ID-002**: A identidade da pessoa DEVE ser imutável e independente de nome, documento, e-mail, telefone ou qualquer outro dado alterável.
- **FR-PTY-ID-003**: O cadastro DEVE distinguir pessoa física de pessoa jurídica.
- **FR-PTY-ID-004**: Uma pessoa NÃO DEVE receber automaticamente papel de cliente, fornecedor, colaborador, contato, portador ou qualquer outro papel de negócio.
- **FR-PTY-ID-005**: Uma pessoa NÃO DEVE conceder acesso ao sistema nem criar associação com usuário ou participante do tenant.
- **FR-PTY-ID-006**: Um usuário ou participante NÃO DEVE ser transformado automaticamente em pessoa do diretório.
- **FR-PTY-ID-007**: Pessoa, usuário, participante e o próprio tenant DEVEM permanecer independentes; eventual associação futura entre esses conceitos DEVE ser explícita e possuir especificação própria orientada por uma necessidade funcional concreta.
- **FR-PTY-ID-008**: A mesma pessoa real PODERÁ possuir registros independentes em tenants distintos, sem compartilhamento ou deduplicação global.
- **FR-PTY-ID-009**: O cadastro DEVE permitir pessoa sem CPF, CNPJ ou identificador equivalente e NÃO DEVE classificá-la genericamente como incompleta por essa ausência; cada funcionalidade consumidora DEVE exigir os identificadores indispensáveis à sua própria operação.

### Nomes e Identificadores

- **FR-PTY-NAME-001**: Toda pessoa DEVE possuir um nome principal adequado ao seu tipo e suficiente para identificação nas consultas comuns.
- **FR-PTY-NAME-002**: Pessoa física DEVE poder registrar nome civil e nome preferencial ou social sem substituir indevidamente um pelo outro.
- **FR-PTY-NAME-003**: Pessoa jurídica DEVE poder registrar razão social e nome fantasia.
- **FR-PTY-NAME-004**: Nomes iguais ou semelhantes DEVEM ser permitidos e NÃO DEVEM ser usados como identidade ou unicidade.
- **FR-PTY-DOC-001**: O sistema DEVE suportar CPF e CNPJ como identificadores brasileiros, validando tipo e formato quando informados.
- **FR-PTY-DOC-002**: O modelo funcional DEVE admitir identificadores de outros países e autoridades sem tratá-los como CPF ou CNPJ.
- **FR-PTY-DOC-003**: Cada identificador DEVE registrar ao menos seu tipo, valor normalizado, país ou autoridade aplicável e vigência cadastral.
- **FR-PTY-DOC-004**: A apresentação DEVE preservar o formato apropriado ao identificador sem depender dele para armazenar a identidade interna da pessoa.
- **FR-PTY-DOC-005**: Cada tipo de identificador DEVE declarar sua política de unicidade e a autoridade ou o país que delimita seu significado; CPF e CNPJ normalizados DEVEM ser estritamente únicos dentro do tenant, enquanto outros tipos somente poderão admitir repetição quando sua própria política a permitir explicitamente.
- **FR-PTY-DOC-006**: O sistema NÃO DEVE consultar, confirmar ou comparar identificadores entre tenants para fins de cadastro comum.
- **FR-PTY-DOC-007**: Alteração do tipo entre pessoa física e jurídica DEVE ser impedida quando produzir incompatibilidade com identificadores ou referências existentes, exigindo correção explícita e auditada.

### Endereços e Meios de Contato

- **FR-PTY-CONTACT-001**: Uma pessoa DEVE poder possuir nenhum, um ou vários endereços, telefones e e-mails.
- **FR-PTY-CONTACT-002**: Cada endereço ou meio de contato DEVE poder receber rótulo, finalidade, indicação de preferência e estado vigente ou inativo.
- **FR-PTY-CONTACT-003**: Para a mesma pessoa e finalidade, o sistema DEVE admitir no máximo um endereço, telefone e e-mail preferencial vigente de cada categoria.
- **FR-PTY-CONTACT-004**: O sistema DEVE suportar endereços brasileiros e internacionais sem exigir município, estado, CEP ou formatação exclusivamente brasileira quando não aplicável.
- **FR-PTY-CONTACT-005**: Telefones e e-mails DEVEM ser validados de acordo com o tipo, preservando formatos internacionais válidos.
- **FR-PTY-CONTACT-006**: Contatos ou endereços repetidos na mesma pessoa DEVEM gerar alerta antes da confirmação, sem fusão silenciosa.
- **FR-PTY-CONTACT-007**: Inativar um contato ou endereço NÃO DEVE removê-lo de referências históricas já registradas por outras funcionalidades.

### Pesquisa e Duplicidade

- **FR-PTY-SEARCH-001**: Usuário autorizado DEVE poder pesquisar pessoas ativas por nome principal, nomes alternativos e identificadores permitidos.
- **FR-PTY-SEARCH-002**: Consultas administrativas autorizadas DEVEM poder incluir pessoas desativadas e identificar claramente seu estado.
- **FR-PTY-SEARCH-003**: Resultados DEVEM apresentar informações suficientes para diferenciar homônimos sem expor dados pessoais desnecessários.
- **FR-PTY-SEARCH-004**: Pesquisa DEVE permanecer limitada ao tenant ativo e não poderá confirmar correspondências existentes em outro tenant.
- **FR-PTY-DUP-001**: Antes da criação, o sistema DEVE alertar sobre possíveis duplicidades no mesmo tenant com base nos dados disponíveis.
- **FR-PTY-DUP-002**: Similaridade de nomes isoladamente NÃO DEVE impedir o cadastro.
- **FR-PTY-DUP-003**: O sistema NÃO DEVE fundir, sobrescrever ou vincular automaticamente pessoas consideradas semelhantes.
- **FR-PTY-DUP-004**: Fusão e desmembramento de pessoas NÃO fazem parte desta primeira versão e deverão ser especificados antes de serem disponibilizados.
- **FR-PTY-DUP-005**: Quando um identificador estritamente único já existir no tenant, o sistema DEVE impedir o novo cadastro e, se o usuário puder consultar a pessoa existente, oferecer acesso a esse cadastro; sem essa autorização, a rejeição NÃO DEVE expor seus dados.

### Ciclo de Vida e Referências

- **FR-PTY-LIFE-001**: Pessoa DEVE possuir estados ativo e desativado.
- **FR-PTY-LIFE-002**: Pessoa ativa DEVE estar disponível para novas operações, sujeita às regras da funcionalidade consumidora.
- **FR-PTY-LIFE-003**: Pessoa desativada NÃO DEVE aparecer nas seleções comuns para novas operações, mas DEVE permanecer consultável quando já referenciada.
- **FR-PTY-LIFE-004**: Desativação e reativação DEVEM exigir autorização específica, motivo e auditoria.
- **FR-PTY-LIFE-005**: A existência de referência histórica DEVE impedir eliminação que comprometa integridade ou rastreabilidade.
- **FR-PTY-LIFE-006**: Módulos consumidores DEVEM decidir se uma operação futura já existente pode continuar vinculada a pessoa posteriormente desativada.
- **FR-PTY-LIFE-007**: Alterações cadastrais NÃO DEVEM modificar snapshots ou evidências históricas mantidas legitimamente por módulos consumidores.
- **FR-PTY-LIFE-008**: Pessoa sem papel, relacionamento ou referência mantida por outro módulo PODERÁ ser excluída definitivamente por usuário autorizado, mediante confirmação e auditoria; pessoa referenciada NÃO DEVE ser excluída pela operação cadastral comum e somente poderá ser desativada.
- **FR-PTY-LIFE-009**: Snapshot de dados mantido por um módulo consumidor NÃO constitui por si só uma referência à pessoa; cada módulo DEVE definir quando preserva também o relacionamento com a pessoa e, enquanto esse relacionamento existir, a exclusão cadastral comum DEVE permanecer bloqueada.
- **FR-PTY-LIFE-010**: Anonimização ou eliminação de pessoa referenciada somente PODERÁ ocorrer por processo específico de privacidade, após avaliação das obrigações legais, contratuais e operacionais de retenção definidas em `tenant-data-governance`.
- **FR-PTY-LIFE-011**: A auditoria de exclusão definitiva DEVE preservar somente as evidências mínimas necessárias para demonstrar a operação, sem conservar desnecessariamente os dados pessoais eliminados.

### Autorização, Privacidade e Auditoria

- **FR-PTY-SEC-001**: Acesso ao diretório DEVE ser negado por padrão e liberado por chaves específicas do tenant.
- **FR-PTY-SEC-002**: Visualizar, criar, alterar, desativar, reativar e consultar identificadores completos DEVEM poder ser autorizados separadamente.
- **FR-PTY-SEC-003**: Interfaces e operações internas DEVEM aplicar o mesmo contrato de autorização.
- **FR-PTY-SEC-004**: Listagens e pesquisas DEVEM minimizar ou mascarar identificadores e contatos quando o usuário não necessitar do valor completo.
- **FR-PTY-SEC-005**: Criação, alteração, desativação, reativação e tentativa negada sensível DEVEM registrar ator, tenant, instante, origem, resultado e alterações relevantes.
- **FR-PTY-SEC-006**: Auditoria e logs NÃO DEVEM expor identificadores, endereços ou contatos completos sem necessidade legítima.
- **FR-PTY-SEC-007**: Coleta, consulta, correção, exportação, anonimização, eliminação e retenção DEVEM respeitar finalidade, necessidade e as políticas definidas em `tenant-data-governance`.
- **FR-PTY-SEC-008**: O sistema DEVE permitir explicar a origem cadastral dos dados relevantes, distinguindo ao menos entrada manual e origem fornecida por outra funcionalidade.

### Integração com Outras Features

- **FR-PTY-INT-001**: Módulos consumidores DEVEM referenciar a identidade imutável da pessoa, e não seu nome ou documento como chave de relacionamento.
- **FR-PTY-INT-002**: Outra funcionalidade PODERÁ propor ou solicitar criação e atualização de pessoa, mas deverá respeitar validação, autorização, duplicidade e auditoria desta feature.
- **FR-PTY-INT-003**: Importações NÃO DEVEM sobrescrever silenciosamente dados atuais divergentes; conflitos DEVEM ser apresentados ou registrados para tratamento explícito.
- **FR-PTY-INT-004**: Papéis e relacionamentos pertencem a `party-relationships-roles` e NÃO DEVEM ser armazenados como atributos fixos do cadastro-base.
- **FR-PTY-INT-005**: Contas bancárias, chaves Pix e outros dados de pagamento pertencem a `party-payment-details`.
- **FR-PTY-INT-006**: Regras para snapshots cadastrais em títulos, documentos fiscais, contratos ou outras operações pertencem às respectivas features consumidoras.

### Experiência e Acessibilidade

- **FR-PTY-UX-001**: O formulário DEVE adaptar campos e validações ao tipo da pessoa sem apagar dados informados sem confirmação.
- **FR-PTY-UX-002**: Campos não aplicáveis ao país, tipo da pessoa ou identificador NÃO DEVEM ser exigidos.
- **FR-PTY-UX-003**: Usuário DEVE poder revisar dados e alertas de possível duplicidade antes de confirmar o cadastro.
- **FR-PTY-UX-004**: Todas as jornadas principais DEVEM ser realizáveis por teclado e tecnologias assistivas, com erros associados aos campos correspondentes.
- **FR-PTY-UX-005**: O estado ativo ou desativado e a identidade do tenant atual DEVEM ser perceptíveis sem depender exclusivamente de cor.

### Decisões de Infraestrutura Auditáveis

> Decisões de infraestrutura: não há agendamento, refresh de token externo ou rotação de chave próprios desta feature.

- **FR-PTY-INFRA-IDEMP**: Repetição técnica da mesma solicitação confirmada de criação, alteração ou mudança de estado DEVE produzir no máximo um efeito cadastral.
- **FR-PTY-INFRA-LOCK**: Criações e alterações concorrentes DEVEM preservar unicidade, estado e histórico determinísticos em todas as instâncias da aplicação.
- **FR-PTY-INFRA-BACKUP**: Pessoas, identificadores, contatos, endereços e respectivas auditorias DEVEM participar dos backups e restaurações do tenant segundo `tenant-data-governance`.

### Key Entities

- **Pessoa**: registro-base de uma pessoa física ou jurídica dentro de um tenant, com identidade imutável, nomes, estado e origem cadastral.
- **Identificador da Pessoa**: documento ou código atribuído por uma autoridade ou país, como CPF, CNPJ ou equivalente estrangeiro, com tipo e vigência.
- **Endereço da Pessoa**: localização postal associada à pessoa, com finalidade, preferência e vigência.
- **Telefone da Pessoa**: meio de contato telefônico associado à pessoa, com finalidade, preferência e vigência.
- **E-mail da Pessoa**: endereço eletrônico associado à pessoa, com finalidade, preferência e vigência.
- **Evento de Auditoria Cadastral**: registro rastreável de alterações e decisões sensíveis sobre os dados da pessoa.

## Success Criteria

### Measurable Outcomes

- **SC-PTY-001**: Em 100% dos testes cruzados, pessoas, identificadores, contatos e endereços de um tenant não são consultados, alterados nem confirmados em outro tenant.
- **SC-PTY-002**: Em 100% dos testes, cadastrar uma pessoa não cria usuário, participação, papel de negócio ou permissão.
- **SC-PTY-003**: Usuários autorizados concluem um cadastro com os dados mínimos aceitos em até 2 minutos durante testes de usabilidade.
- **SC-PTY-004**: Pelo menos 95% das pesquisas comuns por nome ou identificador retornam resultados utilizáveis em até 2 segundos nas condições operacionais definidas para a primeira versão.
- **SC-PTY-005**: Em 100% dos testes com homônimos, o sistema permite os cadastros e fornece elementos autorizados para diferenciá-los.
- **SC-PTY-006**: Em 100% dos testes de alteração, documentos históricos com snapshot próprio permanecem inalterados.
- **SC-PTY-007**: Em 100% dos testes, pessoa desativada deixa de aparecer em seleções comuns e continua identificável nas referências históricas autorizadas.
- **SC-PTY-008**: Em 100% das alterações e mudanças de estado testadas, existe auditoria com ator, tenant, instante e efeito, sem exposição desnecessária de dados pessoais.
- **SC-PTY-009**: Todas as jornadas principais podem ser concluídas apenas por teclado e sem bloqueios críticos para tecnologias assistivas.
- **SC-PTY-010**: Em 100% dos testes concorrentes e de repetição técnica, cada criação ou alteração confirmada produz no máximo um efeito e preserva as regras de unicidade vigentes.
- **SC-PTY-011**: Em 100% dos testes, CPF ou CNPJ já utilizado no tenant impede um segundo cadastro e não revela dados da pessoa existente a usuário sem autorização de consulta.

## Fora do Escopo

- Papéis de cliente, fornecedor, colaborador, portador ou contato.
- Relacionamentos entre pessoas ou associação com usuário, participante e tenant.
- Contas bancárias, chaves Pix ou outros dados de pagamento.
- Fusão e desmembramento de cadastros duplicados.
- CRM, folha de pagamento, compras, vendas, fiscal ou financeiro específicos.
- Consultas automáticas a Receita Federal, SEFAZ ou provedores externos.
