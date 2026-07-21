# Feature Specification: Papéis e Relacionamentos de Pessoas

**Feature**: `party-relationships-roles`
**Created**: 2026-07-21
**Status**: Clarified — pronta para planejamento

## Escopo

Esta feature complementa o diretório de pessoas do tenant com duas capacidades distintas: classificar uma pessoa em papéis de negócio e registrar relacionamentos direcionais entre duas pessoas.

Papéis de negócio descrevem como uma pessoa é utilizada no tenant, como cliente, fornecedor ou colaborador. Relacionamentos descrevem como duas pessoas se relacionam, como uma pessoa física que é contato ou representante de uma pessoa jurídica.

> [!IMPORTANT]
> Papel de negócio e relacionamento cadastral NÃO concedem acesso ao sistema. A autorização continua sendo determinada exclusivamente pela participação, pelos grupos e pelas chaves de acesso.

> [!NOTE]
> Usuário, participante e pessoa permanecem conceitos independentes. Esta feature não associa uma identidade de acesso a uma pessoa; essa ponte somente deverá ser especificada quando um caso de uso concreto, como autosserviço funcional ou reembolso de colaborador, demonstrar sua necessidade.

> [!NOTE]
> Papéis circunstanciais de uma operação, como pagador, beneficiário, portador de cartão, solicitante ou destinatário, não exigem necessariamente uma classificação permanente. Cada módulo consumidor deve decidir quando apenas referencia uma pessoa na operação e quando exige um papel cadastral vigente.

## Clarifications

### Session 2026-07-21

- Q: Quem pode definir tipos de papel com significado funcional? → A: Somente o sistema e seus módulos podem fornecer tipos canônicos de papel. Classificações livres do tenant não são papéis e poderão ser tratadas futuramente por uma capacidade própria de etiquetas.
- Q: Quem pode definir tipos de relacionamento? → A: O sistema e seus módulos fornecem tipos canônicos com significado funcional, enquanto o tenant pode criar tipos personalizados exclusivamente descritivos, incapazes de substituir requisitos ou comportamentos canônicos.
- Q: Qual cardinalidade deve valer para associações vigentes entre participante e pessoa no mesmo tenant? → A: Foi inicialmente escolhida a cardinalidade um para um, decisão posteriormente superada pela retirada integral dessa associação do escopo atual.
- Q: Qual tipo de pessoa pode ser associado a um participante? → A: Foi inicialmente escolhida somente pessoa física, decisão posteriormente superada pela retirada integral dessa associação do escopo atual.
- Q: A associação entre participante e pessoa deve permanecer nesta feature sem um caso de uso que a exija? → A: Não. Usuário, participante e pessoa permanecem independentes; uma associação futura deverá possuir especificação própria orientada por uma necessidade funcional concreta.

## User Scenarios & Testing

### User Story 1 - Classificar uma pessoa por seus papéis de negócio (Priority: P1)

Um usuário autorizado atribui a uma pessoa um ou vários papéis de negócio para que os módulos e consultas identifiquem as capacidades em que ela atua no tenant.

**Why this priority**: Cliente, fornecedor e colaborador são classificações transversais necessárias para organizar o uso das pessoas sem duplicar seus dados cadastrais.

**Independent Test**: Atribuir simultaneamente os papéis de cliente e fornecedor a uma pessoa, localizá-la por ambos e comprovar que nenhum acesso ao sistema foi criado ou alterado.

**Acceptance Scenarios**:

1. **Given** uma pessoa ativa e um usuário autorizado, **When** um papel compatível é atribuído, **Then** a pessoa passa a ser localizada por esse papel sem ganhar participação ou permissão.
2. **Given** uma pessoa que já possui um papel, **When** outro papel compatível é atribuído, **Then** ambos permanecem vigentes de forma independente.
3. **Given** uma tentativa de repetir o mesmo papel vigente para a mesma pessoa e contexto, **When** a operação é confirmada, **Then** o sistema impede a duplicidade e preserva a atribuição existente.

---

### User Story 2 - Registrar o relacionamento entre duas pessoas (Priority: P1)

Um usuário autorizado registra que uma pessoa mantém determinada relação com outra, identificando origem, destino, tipo, vigência e informações descritivas necessárias.

**Why this priority**: Contatos e representantes precisam ser reutilizáveis e pesquisáveis sem serem reduzidos a campos fixos da pessoa jurídica nem confundidos com classificações comerciais.

**Independent Test**: Relacionar uma pessoa física como contato de uma pessoa jurídica, consultar a relação nos dois cadastros com a direção correta e confirmar o isolamento entre tenants.

**Acceptance Scenarios**:

1. **Given** duas pessoas ativas do mesmo tenant, **When** o usuário registra uma relação compatível entre elas, **Then** o sistema preserva quem é a origem, quem é o destino e como a relação deve ser apresentada em cada lado.
2. **Given** uma relação de contato vigente, **When** a pessoa jurídica é consultada, **Then** o contato aparece com sua função e vigência sem duplicar os dados-base da pessoa física.
3. **Given** pessoas pertencentes a tenants diferentes, **When** alguém tenta relacioná-las, **Then** a operação é rejeitada sem confirmar dados do outro tenant.

---

### User Story 3 - Localizar pessoas por papel e relacionamento (Priority: P2)

Um usuário autorizado filtra e seleciona pessoas por papel, relacionamento, estado e vigência para atender uma finalidade de negócio sem percorrer todo o diretório.

**Why this priority**: Os módulos consumidores precisam encontrar candidatos adequados e distinguir classificação permanente de participação circunstancial na operação.

**Independent Test**: Pesquisar clientes vigentes, fornecedores desativados e contatos de uma pessoa específica, verificando autorização, direção e vigência dos resultados.

**Acceptance Scenarios**:

1. **Given** pessoas com múltiplos papéis, **When** o usuário filtra por um papel vigente, **Then** recebe somente os registros autorizados e compatíveis.
2. **Given** uma pessoa com vários relacionamentos, **When** o usuário consulta seus contatos ou representantes, **Then** o resultado respeita tipo, direção e vigência.
3. **Given** uma relação encerrada, **When** uma seleção comum para nova operação é aberta, **Then** a relação não aparece por padrão, mas permanece em consulta histórica autorizada.

---

### User Story 4 - Encerrar e corrigir vínculos preservando histórico (Priority: P2)

Um usuário autorizado encerra um papel ou relacionamento que deixou de ser válido e corrige registros lançados indevidamente sem reescrever fatos históricos.

**Why this priority**: Relações profissionais e comerciais mudam ao longo do tempo, mas operações anteriores precisam continuar explicáveis.

**Independent Test**: Encerrar e posteriormente restabelecer um papel ou relacionamento, preservando períodos distintos, autoria e referências históricas.

**Acceptance Scenarios**:

1. **Given** um papel vigente já utilizado, **When** ele é encerrado, **Then** deixa de qualificar a pessoa para novas seleções que o exijam e continua visível no histórico.
2. **Given** um relacionamento com data de início futura, **When** a data ainda não foi alcançada, **Then** ele é identificável como programado e não é tratado como vigente.
3. **Given** um vínculo criado por engano, **When** o usuário autorizado o cancela com motivo, **Then** o sistema registra a correção sem apagar silenciosamente sua existência.

### Edge Cases

- Uma pessoa atua simultaneamente como cliente, fornecedor e colaborador.
- Duas pessoas mantêm mais de um tipo de relacionamento entre si.
- A mesma pessoa deixa de exercer um papel e volta a exercê-lo posteriormente.
- Datas de vigência de atribuições equivalentes se sobrepõem.
- Uma pessoa é desativada enquanto possui papéis ou relacionamentos vigentes.
- Tentativa de relacionar uma pessoa consigo mesma.
- Tentativa de criar relacionamento circular para um tipo que imponha hierarquia.
- Alteração cadastral da pessoa depois que seu nome foi preservado em snapshot por outro módulo.
- Operações concorrentes atribuem o mesmo papel ou relacionamento.
- Usuário autorizado a consultar pessoas, mas não seus papéis ou relacionamentos.

## Requirements

### Conceitos e Fronteiras

- **FR-PRR-BOUND-001**: Papel de negócio DEVE representar uma classificação da pessoa dentro do tenant e NÃO DEVE representar papel de ator, grupo de acesso, chave de acesso ou autorização.
- **FR-PRR-BOUND-002**: Relacionamento DEVE representar uma ligação tipificada entre duas pessoas do mesmo tenant, preservando sua direção e vigência.
- **FR-PRR-BOUND-004**: Atribuir, alterar, encerrar ou cancelar papel ou relacionamento NÃO DEVE conceder, ampliar, reduzir nem revogar acesso ao sistema.
- **FR-PRR-BOUND-005**: Participação no tenant e papel de colaborador da pessoa DEVEM permanecer independentes e NÃO DEVEM possuir correspondência nesta feature.
- **FR-PRR-BOUND-006**: Uma pessoa DEVE poder existir sem papel ou relacionamento.
- **FR-PRR-BOUND-007**: O próprio tenant NÃO DEVE ser presumido como uma pessoa física ou jurídica enquanto essa identidade não for definida em feature específica.
- **FR-PRR-BOUND-008**: Dados especializados de CRM, vendas, compras, recursos humanos, fiscal, contábil ou financeiro NÃO DEVEM ser armazenados como atributos genéricos desta feature.

### Tipos e Atribuições de Papéis

- **FR-PRR-ROLE-001**: Todo tipo de papel DEVE possuir identidade estável, nome apresentável, descrição, estado e regras declaradas de compatibilidade com tipos de pessoa.
- **FR-PRR-ROLE-002**: Tipos de papel com significado funcional DEVEM ser canônicos e fornecidos exclusivamente pelo sistema ou por seus módulos; tenants NÃO DEVEM criar, alterar nem substituir esses tipos.
- **FR-PRR-ROLE-003**: A primeira versão DEVE permitir, no mínimo, classificar pessoas como cliente, fornecedor e colaborador, inclusive com múltiplos papéis simultâneos.
- **FR-PRR-ROLE-004**: Contato e representante NÃO DEVEM ser tratados apenas como papéis isolados quando seu significado depender da outra pessoa com a qual se relacionam.
- **FR-PRR-ROLE-005**: Uma atribuição de papel DEVE identificar tenant, pessoa, tipo, estado, início e término de vigência quando informados, além de sua origem cadastral.
- **FR-PRR-ROLE-006**: Para a mesma pessoa, tipo de papel e contexto, NÃO DEVE existir mais de uma atribuição vigente equivalente.
- **FR-PRR-ROLE-007**: A pessoa DEVE poder receber novo período do mesmo papel após o encerramento anterior, sem reescrever o histórico.
- **FR-PRR-ROLE-008**: Atribuição futura DEVE ser distinguida de atribuição vigente, e atribuição encerrada ou cancelada NÃO DEVE qualificar novas operações.
- **FR-PRR-ROLE-009**: Encerramento e cancelamento DEVEM exigir motivo, autorização e auditoria; cancelamento DEVE indicar correção de lançamento indevido, não simples término normal.
- **FR-PRR-ROLE-010**: Um módulo especializado PODERÁ manter perfil próprio de cliente, fornecedor ou colaborador, mas DEVERÁ vincular esse perfil à pessoa e não duplicar sua identidade cadastral.
- **FR-PRR-ROLE-011**: O módulo consumidor DEVE declarar quando exige papel vigente e NÃO DEVE inferir essa exigência apenas porque a pessoa ocupa uma função circunstancial na operação.
- **FR-PRR-ROLE-012**: Classificações livres criadas pelo tenant NÃO DEVEM ser representadas como papéis, nem produzir comportamento funcional; eventual capacidade de etiquetas ou classificações personalizadas pertence a especificação futura.

### Tipos e Registros de Relacionamentos

- **FR-PRR-REL-001**: Todo tipo de relacionamento DEVE possuir identidade estável, rótulos para leitura a partir da origem e do destino, descrição, estado e regras declaradas de direção, compatibilidade, cardinalidade, autorrelacionamento e ciclos.
- **FR-PRR-REL-002**: Tipos de relacionamento canônicos DEVEM ser fornecidos exclusivamente pelo sistema ou por seus módulos e PODERÃO possuir significado funcional reconhecido pelas funcionalidades consumidoras.
- **FR-PRR-REL-003**: A primeira versão DEVE suportar, no mínimo, os relacionamentos direcionais de contato de e representante de, com apresentação compreensível nos dois lados.
- **FR-PRR-REL-004**: Todo relacionamento DEVE identificar tenant, pessoa de origem, pessoa de destino, tipo, estado, início e término de vigência quando informados, além de sua origem cadastral.
- **FR-PRR-REL-005**: Origem e destino DEVEM pertencer ao mesmo tenant e NÃO DEVEM ser substituídos silenciosamente pela relação inversa.
- **FR-PRR-REL-006**: Autorrelacionamento DEVE ser impedido, salvo quando o tipo declarar explicitamente que possui significado válido para essa situação.
- **FR-PRR-REL-007**: O sistema DEVE validar compatibilidade de tipos de pessoa, cardinalidade e ciclos conforme as regras do tipo antes de ativar um relacionamento.
- **FR-PRR-REL-008**: Para a mesma origem, destino, tipo e contexto, NÃO DEVE existir mais de um relacionamento vigente equivalente.
- **FR-PRR-REL-009**: Um relacionamento DEVE admitir informações descritivas próprias, como função, setor, observação e finalidade, sem copiar os meios de contato mantidos na pessoa.
- **FR-PRR-REL-010**: Relacionamento futuro, vigente, encerrado e cancelado DEVEM ser distinguíveis e consultáveis conforme autorização.
- **FR-PRR-REL-011**: Encerramento e cancelamento DEVEM exigir motivo, autorização e auditoria, preservando períodos e referências anteriores.
- **FR-PRR-REL-012**: Usuário autorizado DEVE poder criar, alterar e desativar tipos de relacionamento personalizados pertencentes exclusivamente ao tenant.
- **FR-PRR-REL-013**: Tipo personalizado DEVE ser exclusivamente descritivo e NÃO DEVE satisfazer exigência por tipo canônico, acionar comportamento funcional de módulo, conceder acesso ou se apresentar como equivalente canônico.
- **FR-PRR-REL-014**: Tipo personalizado DEVE permitir definir nome, descrição e rótulos direcionais para origem e destino, além de regras estruturais de compatibilidade, cardinalidade, autorrelacionamento e ciclos que não produzam comportamento externo ao relacionamento.
- **FR-PRR-REL-015**: Identidade, direção e significado de tipo personalizado já utilizado NÃO DEVEM ser reaproveitados para outra finalidade; quando a semântica mudar materialmente, o tipo existente DEVE ser desativado e outro criado.
- **FR-PRR-REL-016**: Desativar tipo de relacionamento personalizado DEVE impedir novos relacionamentos desse tipo e preservar os relacionamentos históricos e sua apresentação compreensível.

### Pesquisa e Uso por Outros Módulos

- **FR-PRR-SEARCH-001**: Usuário autorizado DEVE poder pesquisar e filtrar pessoas por um ou vários papéis, estado e vigência.
- **FR-PRR-SEARCH-002**: Usuário autorizado DEVE poder consultar relacionamentos a partir de qualquer lado, com direção, tipo, estado e vigência claramente apresentados.
- **FR-PRR-SEARCH-003**: Seleções comuns DEVEM excluir por padrão papéis e relacionamentos futuros, encerrados, cancelados ou ligados a pessoa desativada.
- **FR-PRR-SEARCH-004**: Consultas históricas autorizadas DEVEM permitir incluir vínculos não vigentes e explicar seus períodos e estados.
- **FR-PRR-SEARCH-005**: Filtros e seleções DEVEM permanecer limitados ao tenant ativo e NÃO DEVEM confirmar papéis, relações ou associações existentes em outro tenant.
- **FR-PRR-SEARCH-006**: Módulo consumidor DEVE poder referenciar a pessoa e, quando necessário ao seu contrato, a atribuição de papel ou o relacionamento específico utilizado.
- **FR-PRR-SEARCH-007**: Snapshot preservado por operação consumidora NÃO DEVE ser atualizado quando pessoa, papel ou relacionamento mudar posteriormente.
- **FR-PRR-SEARCH-008**: Quando um módulo exigir determinado papel vigente, o sistema DEVE validar essa condição no momento definido pela própria operação e fornecer motivo compreensível quando a pessoa não for elegível.

### Ciclo de Vida e Integridade

- **FR-PRR-LIFE-001**: Papéis, relacionamentos e associações já utilizados ou auditáveis NÃO DEVEM ser apagados silenciosamente; correções DEVEM usar cancelamento e operações normais DEVEM usar encerramento.
- **FR-PRR-LIFE-002**: Pessoa que possua papel ou relacionamento preservado por esta feature DEVE ser considerada referenciada para a política de exclusão de `party-registration`.
- **FR-PRR-LIFE-003**: Desativar uma pessoa DEVE impedir novas atribuições e relacionamentos comuns, sem encerrar automaticamente fatos históricos ou alterar autorizações.
- **FR-PRR-LIFE-004**: Reativar uma pessoa NÃO DEVE reativar automaticamente papel ou relacionamento encerrado ou cancelado.
- **FR-PRR-LIFE-005**: Alterações concorrentes DEVEM impedir períodos vigentes incompatíveis e produzir resultado determinístico.
- **FR-PRR-LIFE-006**: O sistema NÃO DEVE promover fusão automática de papéis, relacionamentos ou associações quando detectar pessoas semelhantes.

### Autorização, Privacidade e Auditoria

- **FR-PRR-SEC-001**: Acesso a papéis, relacionamentos e associações DEVE ser negado por padrão e liberado por chaves específicas do tenant.
- **FR-PRR-SEC-002**: Consultar, atribuir, alterar, encerrar e cancelar papéis DEVEM poder ser autorizados separadamente das operações equivalentes sobre relacionamentos.
- **FR-PRR-SEC-003**: A autorização para consultar uma pessoa NÃO DEVE implicar autorização para consultar todos os seus papéis ou relacionamentos.
- **FR-PRR-SEC-004**: Interfaces e operações internas DEVEM aplicar o mesmo contrato de autorização e isolamento.
- **FR-PRR-SEC-005**: Toda criação, alteração, encerramento, cancelamento e tentativa sensível negada DEVE registrar ator, tenant, instante, origem, resultado, motivo e mudanças relevantes.
- **FR-PRR-SEC-006**: Auditoria e logs NÃO DEVEM expor desnecessariamente documentos, contatos ou demais dados pessoais das pessoas relacionadas.
- **FR-PRR-SEC-007**: Usuário que não possa consultar a pessoa relacionada NÃO DEVE obter seus dados por meio de filtros, contagens, mensagens de erro ou auditorias desta feature.
- **FR-PRR-SEC-008**: Relacionamentos potencialmente sensíveis DEVEM respeitar finalidade, necessidade, retenção e demais políticas de `tenant-data-governance`.

### Experiência e Acessibilidade

- **FR-PRR-UX-001**: A interface DEVE explicar se o usuário está atribuindo um papel ou relacionando duas pessoas.
- **FR-PRR-UX-002**: A seleção de tipo DEVE apresentar descrição e compatibilidades suficientes para evitar o uso de uma classificação no lugar de um relacionamento.
- **FR-PRR-UX-003**: Direção do relacionamento DEVE ser compreensível antes da confirmação e nas consultas posteriores.
- **FR-PRR-UX-004**: Estado e período de vigência DEVEM ser perceptíveis sem depender exclusivamente de cor.
- **FR-PRR-UX-005**: Jornadas principais DEVEM ser realizáveis por teclado e tecnologias assistivas, com erros associados aos campos e objetos correspondentes.
- **FR-PRR-UX-006**: Operações de encerramento, cancelamento e substituição DEVEM apresentar seu efeito antes da confirmação e não poderão apagar histórico sem aviso.

### Decisões de Infraestrutura Auditáveis

> Decisões de infraestrutura: não há agendamento, refresh de token externo ou rotação de chave próprios desta feature. A vigência é avaliada a partir dos períodos registrados, sem depender de uma alteração agendada de estado.

- **FR-PRR-INFRA-IDEMP**: Repetição técnica da mesma solicitação confirmada DEVE produzir no máximo uma atribuição, relacionamento ou mudança de estado.
- **FR-PRR-INFRA-LOCK**: Operações concorrentes DEVEM preservar cardinalidade, vigência, isolamento e histórico determinísticos em todas as instâncias da aplicação.
- **FR-PRR-INFRA-BACKUP**: Papéis, relacionamentos, associações e respectivas auditorias DEVEM participar dos backups e restaurações do tenant segundo `tenant-data-governance`.

### Key Entities

- **Tipo de Papel**: definição identificável de uma classificação de negócio, com apresentação, estado e regras de compatibilidade.
- **Atribuição de Papel**: vínculo temporal entre pessoa e tipo de papel no tenant, sem efeito de autorização.
- **Tipo de Relacionamento**: definição da semântica entre origem e destino, incluindo rótulos, direção, compatibilidade, cardinalidade e ciclos permitidos.
- **Relacionamento entre Pessoas**: vínculo temporal e direcionado entre duas pessoas do mesmo tenant.
- **Evento de Auditoria de Vínculo**: evidência de criação, alteração, encerramento, cancelamento ou tentativa sensível envolvendo os vínculos desta feature.

## Success Criteria

### Measurable Outcomes

- **SC-PRR-001**: Em 100% dos testes, atribuir ou remover papel ou relacionamento não altera grupos, chaves, participação nem acesso do usuário.
- **SC-PRR-002**: Em 100% dos testes cruzados, papéis, relacionamentos e associações de um tenant não são consultados, alterados nem confirmados em outro tenant.
- **SC-PRR-003**: Em 100% dos testes, uma pessoa pode manter simultaneamente os papéis de cliente, fornecedor e colaborador sem duplicação do cadastro-base.
- **SC-PRR-004**: Em 100% dos testes de relacionamento, origem, destino e apresentação inversa permanecem inequívocos antes e depois de alterações cadastrais nas pessoas.
- **SC-PRR-005**: Em 100% dos testes, vínculos futuros, vigentes, encerrados e cancelados são corretamente diferenciados nas seleções comuns e consultas históricas.
- **SC-PRR-006**: Em 100% dos testes concorrentes e de repetição técnica, cada operação confirmada produz no máximo um efeito e respeita cardinalidade e sobreposição vigentes.
- **SC-PRR-007**: Em 100% das operações sensíveis testadas, existe auditoria com ator, tenant, instante, motivo e efeito, sem exposição desnecessária de dados pessoais.
- **SC-PRR-008**: Usuários autorizados localizam pessoas por papel ou relacionamento em até 2 segundos em pelo menos 95% das consultas nas condições operacionais definidas para a primeira versão.
- **SC-PRR-009**: Todas as jornadas principais podem ser concluídas apenas por teclado e sem bloqueios críticos para tecnologias assistivas.
- **SC-PRR-010**: Em 100% dos testes, desativar pessoa preserva papéis e relacionamentos históricos e não produz alteração automática de autorização.
- **SC-PRR-011**: Em 100% dos testes, relacionamento personalizado permanece limitado ao tenant, não satisfaz requisito por tipo canônico e não aciona comportamento funcional de módulo.

## Fora do Escopo

- Grupos, chaves de acesso e papéis de ator do participante.
- Associação entre usuário ou participante e pessoa do diretório.
- Cadastro-base, documentos, endereços, telefones e e-mails da pessoa.
- Dados bancários, chaves Pix e outros dados de pagamento.
- Campos especializados de cliente, fornecedor, colaborador, contato ou representante.
- CRM, folha de pagamento, compras, vendas, fiscal, contábil ou financeiro específicos.
- Classificação da conta/tenant como pessoa física ou jurídica.
- Autosserviço cadastral ou funcional baseado na identidade da pessoa autenticada.
- Fusão ou desmembramento de pessoas e seus vínculos.
