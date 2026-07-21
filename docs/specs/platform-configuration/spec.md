# Feature Specification: Configuração da Plataforma

**Feature**: `platform-configuration`
**Created**: 2026-07-20
**Status**: Especificada e clarificada

## Escopo

Esta feature permite que administradores do sistema devidamente autorizados consultem e mantenham políticas e valores funcionais globais reconhecidos pelo Rinos. Cada configuração possui identidade estável, finalidade, tipo, validação, valor seguro padrão, sensibilidade, forma de ativação e comportamento diante de ausência ou inconsistência. Alterações de origem banco global são versionadas, confirmadas, auditadas e aplicadas de maneira consistente em todas as instâncias.

Cada definição possui exatamente uma origem: arquivo `application.properties`, banco global ou banco do tenant. Não existe precedência nem busca entre essas origens. As definições pertencem às features que consomem os valores; esta capacidade fornece o catálogo e o resolvedor tipado compartilhado, mas sua interface administrativa mantém somente valores de origem banco global. Configurações de tenant são administradas pela respectiva feature dentro de contexto explícito do tenant.

Não inclui credenciais, senhas, chaves privadas, segredos de integrações, dados de conexão, configuração do servidor, proxy, portas, caminhos, processo de deploy, backup, restauração, parâmetros internos do banco ou valores de infraestrutura que precisem permanecer fora da aplicação. Também não inclui direitos de planos, preferências pessoais, configurações particulares de conta ou conteúdo funcional dos módulos.

## Clarifications

### Session 2026-07-20

- Q: Uma configuração pode ser definida simultaneamente em arquivo, banco, variável de ambiente ou linha de comando e resolvida por precedência? -> A: Não. Cada definição possui origem exclusiva — `application.properties`, banco global ou banco do tenant — e configurações do Rinos não aceitam sobrescrita por variável de ambiente, propriedade JVM ou argumento de linha de comando.
- Q: Como uma nova versão de configuração de origem banco global entra em vigor? -> A: Cada definição declara se sua ativação é dinâmica, dependente de reinício ou condicionada a evento específico.
- Q: Configurações globais persistidas podem ter ativação agendada para um instante futuro? -> A: Não. A ativação ocorre imediatamente ou após a condição técnica declarada de reinício ou evento específico.
- Q: Quais regras comuns desta feature são obrigatórias para configurações armazenadas no banco do tenant? -> A: Definição tipada, validação, tenant explícito e auditoria são obrigatórios; a feature consumidora define autorização, lifecycle e necessidade de versionamento.
- Q: A publicação de configuração global exige aprovação de um segundo administrador? -> A: Não. O próprio administrador autorizado pode concluir a publicação após autenticação forte recente, confirmação, justificativa, validações e auditoria.

## User Scenarios & Testing

### User Story 1 - Consultar a configuração efetiva da plataforma (Priority: P1)

Um administrador autorizado pesquisa uma política global e compreende seu valor efetivo, finalidade, origem, versão, estado, impacto e eventual necessidade de reinício, sem visualizar segredos ou detalhes internos de infraestrutura.

**Why this priority**: antes de alterar qualquer política, o operador precisa saber o que realmente está em vigor e por quê.

**Independent Test**: cadastrar definições em estados e fontes diferentes, consultar com e sem chave e comprovar valor efetivo, fallback, proteção de dados, filtros e auditoria.

**Acceptance Scenarios**:

1. **Given** configuração publicada e válida, **When** administrador autorizado a consulta, **Then** visualiza valor efetivo, origem, vigência, versão e explicação funcional
2. **Given** valor persistido ausente, **When** a definição possui padrão seguro, **Then** a consulta mostra o padrão como valor efetivo e identifica sua origem
3. **Given** ator sem chave global de consulta, **When** tenta acessar o catálogo, **Then** a operação é negada independentemente de seu papel
4. **Given** definição sensível ou vinculada a segredo externo, **When** é consultada, **Then** somente presença, estado e orientação permitidos são exibidos, nunca o segredo
5. **Given** valor inválido ou fonte indisponível, **When** a resolução ocorre, **Then** o estado não é apresentado como saudável nem efetivo sem aplicar o fallback declarado
6. **Given** definição de origem arquivo, **When** variável de ambiente, propriedade JVM ou argumento de linha de comando usa o mesmo código, **Then** o valor carregado do `application.properties` não é sobrescrito
7. **Given** definição de origem banco do tenant, **When** é consultada sem tenant explícito, **Then** a leitura é recusada e não utiliza valor global nem de arquivo como fallback

---

### User Story 2 - Publicar uma alteração global segura (Priority: P1)

Um administrador com chave específica propõe um novo valor, revisa sua validação e impacto, confirma a publicação com autenticação reforçada e produz uma nova versão sem sobrescrever o histórico anterior.

**Why this priority**: configurações globais podem afetar todos os usuários e tenants; uma alteração ambígua ou parcial pode ampliar acesso ou indisponibilizar a plataforma.

**Independent Test**: alterar configurações de tipos e riscos diferentes, testar entradas inválidas e concorrentes e comprovar validação, versionamento, atomicidade, confirmação e auditoria.

**Acceptance Scenarios**:

1. **Given** valor válido e administrador autorizado, **When** confirma a publicação, **Then** uma nova versão imutável é criada e o valor anterior permanece no histórico
2. **Given** valor fora do domínio permitido, **When** tenta publicar, **Then** a alteração é recusada antes de afetar qualquer consumidor
3. **Given** dois administradores partem da mesma versão, **When** publicam valores diferentes, **Then** somente a alteração baseada na versão vigente pode concluir sem nova revisão
4. **Given** configuração de segurança, **When** a alteração reduzir proteção além do limite permitido, **Then** é recusada mesmo que o ator possua chave administrativa
5. **Given** alteração confirmada, **When** a auditoria é consultada, **Then** apresenta definição, versão anterior, nova versão, ator, justificativa, impacto e resultado
6. **Given** administrador possui a chave exigida e conclui autenticação forte recente, confirmação e justificativa, **When** publica alteração válida, **Then** pode concluí-la sem aprovação de outro administrador

---

### User Story 3 - Controlar ativação e vigência (Priority: P1)

Um administrador autorizado publica uma versão válida segundo a forma de ativação definida e acompanha estados pendentes, ativos, substituídos ou cancelados, sem permitir sobreposição ambígua ou ativação parcial entre instâncias.

**Why this priority**: publicar o valor e torná-lo efetivo são decisões diferentes quando uma mudança exige reinício ou comprovação de outro evento técnico.

**Independent Test**: ativar versões imediatas, dependentes de reinício e condicionadas a evento, cancelar versão ainda pendente, simular concorrência e verificar um único valor efetivo por definição e ambiente.

**Acceptance Scenarios**:

1. **Given** versão publicada para ativação imediata, **When** a confirmação termina, **Then** todas as novas avaliações convergem para um único valor efetivo
2. **Given** mudança que exige reinício, **When** é publicada, **Then** permanece claramente pendente e não é apresentada como efetiva antes de a instância comprovar a reinicialização
3. **Given** mudança condicionada a evento específico, **When** é publicada, **Then** permanece pendente até que o evento declarado seja comprovado e não produz efeito antecipadamente
4. **Given** versão ainda pendente, **When** administrador autorizado a cancela antes da condição técnica, **Then** ela nunca se torna efetiva e o valor anterior permanece vigente
5. **Given** tentativa de informar data ou hora futura para ativação, **When** a publicação é validada, **Then** o agendamento é recusado porque não pertence ao modelo da feature

---

### User Story 4 - Reverter o efeito de uma alteração problemática (Priority: P1)

Um administrador identifica que uma versão ativa causou efeito indesejado e publica uma nova versão baseada em valor anterior válido, preservando a versão problemática e toda a cadeia de decisões.

**Why this priority**: corrigir rapidamente não pode significar apagar evidências, reescrever história ou restaurar valor incompatível com a versão atual da aplicação.

**Independent Test**: ativar valor problemático, selecionar versão histórica compatível como base e confirmar que uma nova versão restaura o efeito pretendido sem editar o passado.

**Acceptance Scenarios**:

1. **Given** versão ativa problemática e versão anterior compatível, **When** administrador confirma o retorno funcional, **Then** uma nova versão é publicada com o valor escolhido
2. **Given** versão histórica incompatível com o contrato atual, **When** tenta reutilizá-la, **Then** a publicação é recusada e o valor vigente não muda
3. **Given** versão problemática já produziu efeitos funcionais, **When** novo valor entra em vigor, **Then** efeitos concluídos não são apagados ou revertidos implicitamente
4. **Given** retorno publicado, **When** o histórico é consultado, **Then** distingue versão original, versão problemática, decisão corretiva e valor atualmente efetivo

---

### User Story 5 - Explicar e auditar mudanças de configuração (Priority: P2)

Um administrador autorizado compara versões, investiga quem alterou uma política, entende por que o valor efetivo mudou e identifica consumidores ou ocorrências afetadas sem acessar dados internos dos tenants.

**Why this priority**: problemas de configuração frequentemente aparecem longe da alteração original e exigem correlação confiável para diagnóstico e governança.

**Independent Test**: criar histórico com publicação, ativação, cancelamento, fallback e correção, verificando comparação, filtros, correlação, exportação segura futura e acessibilidade.

**Acceptance Scenarios**:

1. **Given** várias versões da mesma definição, **When** o administrador compara duas, **Then** identifica valores permitidos, autor, justificativa, vigência, impacto e estado de cada uma
2. **Given** consumidor aplicou fallback, **When** o evento é investigado, **Then** a origem inconsistente e o valor seguro utilizado são explicáveis
3. **Given** configuração afeta múltiplas features, **When** seu detalhe é consultado, **Then** apresenta consumidores declarados sem abrir conteúdo funcional de tenants
4. **Given** histórico sensível, **When** é consultado, **Then** valores secretos continuam ausentes e a própria consulta é auditada
5. **Given** interface administrativa, **When** é utilizada apenas por teclado, **Then** estados, origem, risco e diferenças permanecem compreensíveis sem depender somente de cor

### Edge Cases

- Duas instâncias detectam simultaneamente a condição técnica de ativação da mesma versão pendente.
- Dois administradores editam a partir da mesma versão efetiva.
- Uma definição é removida do código enquanto ainda possui versões persistidas.
- Uma versão da aplicação muda o tipo ou domínio aceito por uma definição existente.
- O valor persistido é válido pelo contrato antigo, mas inválido pelo contrato atual.
- O padrão seguro está ausente ou também é inválido.
- O `application.properties` possui valor inválido para uma definição obrigatória.
- Variável de ambiente, propriedade JVM ou argumento de linha de comando tenta sobrescrever uma configuração do Rinos.
- O mesmo código de configuração é registrado em mais de uma origem.
- Uma configuração de tenant é consultada sem contexto explícito do tenant.
- Uma configuração depende de outra e forma ciclo de resolução.
- O evento técnico esperado ocorre durante indisponibilidade ou reinício da aplicação.
- Uma versão pendente exige reinício, mas uma nova versão a substitui antes dele.
- O administrador tenta usar configuração comum para armazenar segredo.
- Uma alteração de limite deixaria operações ou recursos existentes acima do novo valor.
- Uma alteração reduz proteção contra automação para valor inseguro.
- Uma restauração externa recupera versão de configuração anteriormente substituída.
- Uma definição é renomeada e o código antigo continua referenciando seu identificador.
- Uma consulta ampla tenta enumerar valores sensíveis ou extrair todo o histórico.
- A instalação possui somente um administrador apto a publicar configurações globais.

## Requirements

### Catálogo e Contrato das Definições

- **FR-PC-DEF-001**: Cada definição DEVE possuir código global estável, nome, finalidade, categoria, tipo, origem exclusiva, escopo, domínio permitido, padrão seguro, obrigatoriedade, sensibilidade, forma de ativação e comportamento de fallback.
- **FR-PC-DEF-002**: Código de definição DEVE ser único, imutável e independente do nome exibido.
- **FR-PC-DEF-003**: Definição DEVE pertencer à feature responsável por interpretar o valor e declarar seus consumidores conhecidos.
- **FR-PC-DEF-004**: Administrador NÃO DEVE criar código arbitrário pela interface; somente definições reconhecidas pela versão da aplicação poderão receber valor.
- **FR-PC-DEF-005**: Tipos mínimos DEVEM abranger booleano, número inteiro, número decimal, texto limitado, duração, quantidade, enumeração e conjunto estruturado quando houver contrato explícito.
- **FR-PC-DEF-006**: Cada tipo DEVE declarar formato, limites, unidade, nulidade e validações suficientes para impedir interpretação ambígua.
- **FR-PC-DEF-007**: Mudança incompatível de código, tipo, unidade ou significado DEVE constituir nova definição e não poderá reescrever versões anteriores.
- **FR-PC-DEF-008**: Definição retirada DEVE permanecer reconhecível no histórico, mas não poderá aceitar nova versão nem ser usada por consumidor atual.
- **FR-PC-DEF-009**: Dependência entre definições DEVE ser explícita, acíclica e validada como conjunto antes da publicação.
- **FR-PC-DEF-010**: Valor derivado ou composto DEVE indicar suas entradas e não poderá ocultar fonte que altere o resultado efetivo.
- **FR-PC-DEF-011**: Definição DEVE declarar se a ausência bloqueia a capacidade, aplica padrão seguro ou mantém último valor válido.
- **FR-PC-DEF-012**: Configuração de segurança DEVE declarar limite mínimo de proteção que nem mesmo administrador autorizado poderá violar.

### Escopo e Fronteiras

- **FR-PC-SCOPE-001**: Cada definição DEVE possuir exatamente uma origem entre `PROPERTY_FILE`, `GLOBAL_DATABASE` e `TENANT_DATABASE`.
- **FR-PC-SCOPE-002**: O mesmo código NÃO DEVE ser registrado em mais de uma origem ou escopo, e uma divergência DEVE impedir a inicialização segura da capacidade afetada.
- **FR-PC-SCOPE-003**: Configuração `PROPERTY_FILE` DEVE possuir escopo da instalação, ser lida exclusivamente do `application.properties` explícito na raiz do projeto e somente mudar após reinicialização do serviço.
- **FR-PC-SCOPE-004**: Configuração `GLOBAL_DATABASE` DEVE ser lida exclusivamente do banco global e NÃO DEVE possuir equivalente no arquivo de propriedades.
- **FR-PC-SCOPE-005**: Configuração `TENANT_DATABASE` DEVE ser lida exclusivamente do banco do tenant explicitamente informado e NÃO DEVE possuir equivalente global ou no arquivo de propriedades.
- **FR-PC-SCOPE-006**: A interface administrativa desta feature PODERÁ consultar metadados e estado seguros das definições da instalação, mas somente DEVE manter valores `GLOBAL_DATABASE`; valores `PROPERTY_FILE` não são editáveis e valores `TENANT_DATABASE` não são consultados fora de tenant explícito.
- **FR-PC-SCOPE-007**: Configuração `TENANT_DATABASE` DEVE utilizar definição tipada, validação, tenant explícito e auditoria compartilhadas, mas ser administrada pela feature consumidora, que define autorização, lifecycle e necessidade de versionamento.
- **FR-PC-SCOPE-008**: Preferência pessoal DEVE permanecer em `user-dashboard`, e plano, direito, franquia e limite comercial DEVEM permanecer em `plans-entitlements`.
- **FR-PC-SCOPE-009**: Credencial, senha, chave privada, segredo TOTP, token, segredo de integração e material criptográfico NÃO DEVEM ser armazenados nem editados nesta feature.
- **FR-PC-SCOPE-010**: Banco, servidor, proxy, porta, caminho, backup, restauração, deploy, processo e credencial de infraestrutura somente PODERÃO ser definidos em `application.properties` quando forem configurações reconhecidas da instalação e NÃO DEVEM ser configurados pela interface.
- **FR-PC-SCOPE-011**: Configurações pertencentes ao Rinos NÃO DEVEM aceitar valor, sobrescrita, precedência, redirecionamento de arquivo, importação de fonte ou ativação de perfil proveniente de variável de ambiente, propriedade de sistema da JVM ou argumento de linha de comando, ainda que a fonte seja reconhecida automaticamente pelo framework.
- **FR-PC-SCOPE-012**: O `application.properties` NÃO DEVE conter interpolação de variável de ambiente e NÃO DEVE ser editável pela aplicação.
- **FR-PC-SCOPE-013**: A existência de configuração global NÃO DEVE conceder acesso, grupo, chave, papel, direito de plano ou contexto de tenant.
- **FR-PC-SCOPE-014**: Integração futura com provedor de segredos DEVE possuir contrato próprio e somente poderá expor estado seguro, nunca o valor secreto.

### Resolução do Valor Efetivo

- **FR-PC-RES-001**: Toda leitura DEVE iniciar pela definição tipada e consultar somente a origem e o escopo nela declarados, sem procurar o código em fontes alternativas.
- **FR-PC-RES-002**: Um resolvedor compartilhado PODERÁ encaminhar a leitura às três origens, mas NÃO DEVE aplicar precedência entre elas.
- **FR-PC-RES-003**: Para origem `PROPERTY_FILE`, o valor efetivo DEVE ser o valor explícito do `application.properties` ou, quando ausente e permitido, o padrão declarado no código.
- **FR-PC-RES-004**: Configuração `PROPERTY_FILE` obrigatória sem valor válido e sem padrão permitido DEVE impedir a inicialização segura da capacidade ou da aplicação, conforme criticidade declarada.
- **FR-PC-RES-005**: Valores `PROPERTY_FILE` DEVEM compor um retrato imutável durante a execução; alteração do arquivo somente PODERÁ produzir efeito após reinicialização.
- **FR-PC-RES-006**: Para origem `GLOBAL_DATABASE`, o valor efetivo DEVE ser a versão persistida vigente ou, quando ausente e permitido, o padrão declarado pela definição.
- **FR-PC-RES-007**: Para origem `TENANT_DATABASE`, a leitura DEVE exigir tenant explícito, consultar somente seu banco e usar apenas o padrão declarado pela própria definição quando permitido.
- **FR-PC-RES-008**: Configuração `TENANT_DATABASE` NÃO DEVE recorrer implicitamente ao banco global, ao arquivo de propriedades ou a outro tenant.
- **FR-PC-RES-009**: Valor padrão criado por migração DEVE ser tratado como valor persistido normal; fallback em código DEVE ser proteção declarada e observável, nunca uma segunda origem configurável.
- **FR-PC-RES-010**: Valor obtido da origem exclusiva DEVE passar pelo contrato vigente antes de ser considerado efetivo.
- **FR-PC-RES-011**: Valor ausente ou inválido DEVE seguir o fallback específico da definição e produzir ocorrência proporcional ao risco; se o fallback também for inválido, a capacidade afetada DEVE falhar com segurança.
- **FR-PC-RES-012**: Último valor válido somente PODERÁ ser mantido quando a definição declarar essa estratégia e sua vigência continuar compatível.
- **FR-PC-RES-013**: Toda leitura DEVE resolver exatamente um valor efetivo por definição, escopo e instante, e consumidores equivalentes DEVEM receber o mesmo resultado.
- **FR-PC-RES-014**: Resultado armazenado temporariamente NÃO DEVE ampliar acesso nem sobreviver além da mudança que o invalida.
- **FR-PC-RES-015**: Resultado DEVE identificar definição, escopo, origem exclusiva, valor permitido, versão quando persistida, vigência e estado sem expor dados protegidos.

### Versionamento e Publicação

- **FR-PC-VER-001**: Cada alteração persistida DEVE criar versão imutável com definição, valor protegido, base utilizada, autor, justificativa, criação e estado.
- **FR-PC-VER-002**: Versão publicada NÃO DEVE ser editada ou removida por operação comum.
- **FR-PC-VER-003**: Publicação DEVE validar contrato, dependências, limite de segurança, versão-base e impacto conhecido antes de concluir.
- **FR-PC-VER-004**: Falha de qualquer validação DEVE impedir integralmente a publicação sem alterar valor efetivo.
- **FR-PC-VER-005**: Publicações concorrentes baseadas na mesma versão NÃO DEVEM sobrescrever silenciosamente uma à outra.
- **FR-PC-VER-006**: Versão DEVE distinguir rascunho, publicada aguardando condição técnica, efetiva, substituída e cancelada quando aplicável.
- **FR-PC-VER-007**: No máximo uma versão PODERÁ ser efetiva por definição, ambiente e instante.
- **FR-PC-VER-008**: Cancelamento somente DEVE ocorrer antes de a versão se tornar efetiva e não poderá apagar a versão ou sua auditoria.
- **FR-PC-VER-009**: Retorno a valor histórico DEVE criar nova versão compatível, nunca reativar ou editar a versão antiga.
- **FR-PC-VER-010**: Alteração de configuração NÃO DEVE reverter implicitamente operações já concluídas sob valor anterior.
- **FR-PC-VER-011**: Configuração que reduza limite DEVE declarar como consumidores tratam estado existente acima do novo valor antes da publicação.
- **FR-PC-VER-012**: Comparação DEVE apresentar diferenças em forma compatível com o tipo, sem expor conteúdo sensível desnecessário.

### Ativação e Propagação

- **FR-PC-ACT-001**: Cada definição `GLOBAL_DATABASE` DEVE declarar exatamente uma forma de ativação entre dinâmica, dependente de reinício ou condicionada a evento específico.
- **FR-PC-ACT-002**: Publicação DEVE permitir somente formas de ativação compatíveis com a definição.
- **FR-PC-ACT-003**: Ativação imediata DEVE produzir um único valor efetivo para novas avaliações depois da confirmação.
- **FR-PC-ACT-004**: A feature NÃO DEVE permitir data, hora, prazo ou agendamento futuro de ativação; a versão DEVE produzir efeito imediatamente ou aguardar exclusivamente a condição técnica declarada.
- **FR-PC-ACT-005**: Versão dependente de reinício NÃO DEVE ser apresentada como efetiva enquanto a instância não comprovar que iniciou sob ela.
- **FR-PC-ACT-006**: Enquanto houver instâncias em valores diferentes durante transição permitida, o estado DEVE aparecer degradado e nenhuma decisão de segurança poderá usar o valor menos protetivo.
- **FR-PC-ACT-007**: Configuração que exija consistência imediata entre instâncias DEVE falhar com segurança se a convergência não puder ser comprovada.
- **FR-PC-ACT-008**: Nova versão publicada antes da ativação de outra DEVE resolver explicitamente substituição ou conflito sem duas vigências concorrentes.
- **FR-PC-ACT-009**: Ativação, cancelamento, substituição, falha e fallback DEVEM produzir eventos correlacionáveis.
- **FR-PC-ACT-010**: A constatação de reinício ou evento técnico DEVE ser idempotente e NÃO DEVE ativar uma versão cancelada, substituída ou incompatível.
- **FR-PC-ACT-011**: Consumidor assíncrono ainda não iniciado DEVE resolver novamente o valor efetivo quando começar, salvo contrato explícito que fixe a versão na aceitação.
- **FR-PC-ACT-012**: Operação já aceita sob versão válida DEVE concluir segundo o contrato da feature consumidora e não poderá trocar parcialmente de valor durante sua execução.
- **FR-PC-ACT-013**: Configuração condicionada a evento específico DEVE identificar o evento esperado e a evidência necessária para comprovar sua ocorrência antes de ser apresentada como efetiva.

### Administração e Segurança

- **FR-PC-ADM-001**: Consulta de catálogo, consulta de histórico, edição de rascunho, publicação, cancelamento e auditoria DEVEM possuir chaves globais separáveis.
- **FR-PC-ADM-002**: O papel Administrador do Sistema NÃO DEVE conceder nenhuma dessas capacidades sem chave explícita.
- **FR-PC-ADM-003**: Publicação, cancelamento e retorno funcional DEVEM exigir autenticação ocorrida há no máximo 15 minutos, TOTP ou passkey compatível e confirmação explícita.
- **FR-PC-ADM-004**: Código enviado ao mesmo e-mail usado no login NÃO DEVE satisfazer o fator forte exigido para alteração global.
- **FR-PC-ADM-005**: Confirmação DEVE apresentar definição, origem atual, valor proposto protegido, impacto, forma de ativação e justificativa.
- **FR-PC-ADM-006**: Consulta DEVE possuir busca, categoria, estado e paginação suficientes para evitar enumeração irrestrita.
- **FR-PC-ADM-007**: Interface NÃO DEVE permitir colar ou persistir segredo em campo de configuração comum e DEVE orientar o canal externo adequado.
- **FR-PC-ADM-008**: Valores sensíveis permitidos, quando não secretos, DEVEM ser reduzidos na interface, em logs e em auditorias conforme sua classificação.
- **FR-PC-ADM-009**: Alteração que possa indisponibilizar capacidade DEVE destacar o risco e exigir justificativa proporcional.
- **FR-PC-ADM-010**: Administrador que perde sua chave durante rascunho ou publicação NÃO DEVE concluir a etapa seguinte.
- **FR-PC-ADM-011**: Todas as jornadas DEVEM ser navegáveis por teclado e diferenciar estado, origem, risco e vigência sem depender somente de cor.
- **FR-PC-ADM-012**: Publicação válida NÃO DEVE exigir aprovação de segundo administrador; o mesmo administrador que iniciou a alteração PODERÁ concluí-la quando mantiver a chave necessária e satisfizer autenticação forte recente, confirmação, justificativa, validações e auditoria.

### Auditoria e Observabilidade

- **FR-PC-AUD-001**: Consulta sensível e toda alteração DEVE registrar ator ou processo, chave usada, definição, versão, ação, instante, origem, correlação e resultado.
- **FR-PC-AUD-002**: Publicação DEVE registrar versão-base, justificativa, validações, impacto declarado e forma de ativação.
- **FR-PC-AUD-003**: Evento NÃO DEVE armazenar segredo nem valor completo quando hash, categoria ou representação reduzida forem suficientes.
- **FR-PC-AUD-004**: Falha de auditoria obrigatória DEVE impedir conclusão de alteração administrativa.
- **FR-PC-AUD-005**: Mudança efetiva DEVE ser correlacionável a ocorrências operacionais posteriores sem copiar conteúdo de tenant.
- **FR-PC-AUD-006**: Histórico DEVE permitir comparar versões persistidas e explicar a origem exclusiva, o padrão e o fallback aplicados.
- **FR-PC-AUD-007**: Leitura de histórico e comparação DEVEM exigir chave própria e também ser auditadas conforme sensibilidade.
- **FR-PC-AUD-008**: Definição ausente, valor inválido, divergência de instância e fallback de segurança DEVEM produzir ocorrência operacional proporcional.
- **FR-PC-AUD-009**: Métricas agregadas NÃO DEVEM incluir valores configurados que revelem informação sensível.
- **FR-PC-AUD-010**: Retenção e destinação de versões e auditorias DEVEM seguir `tenant-data-governance`, preservando o histórico necessário à explicação.
- **FR-PC-AUD-011**: Toda alteração `TENANT_DATABASE` DEVE registrar ao menos tenant, ator ou processo, definição, ação, instante, representação protegida da mudança, correlação e resultado, independentemente de a feature consumidora adotar versionamento.

### Limites com Features Relacionadas

- **FR-PC-BOUND-001**: `access-control` define grupos, chaves e concessões globais usadas para administrar configurações.
- **FR-PC-BOUND-002**: `system-directory-administration` identifica os administradores e seus estados; esta feature não altera identidades ou acessos.
- **FR-PC-BOUND-003**: `platform-operations` apresenta ocorrências decorrentes de configuração, mas não edita valores nem substitui a validação desta feature.
- **FR-PC-BOUND-004**: `plans-entitlements` permanece autoridade exclusiva sobre planos, direitos, franquias e valores comerciais.
- **FR-PC-BOUND-005**: Cada feature consumidora permanece responsável por definir significado, fallback, limite seguro, autorização, lifecycle, necessidade de versionamento e efeitos de mudança de sua configuração de tenant, sem dispensar tipagem, validação, contexto explícito e auditoria comuns.
- **FR-PC-BOUND-006**: Segredos e configurações de infraestrutura permanecem sob procedimentos externos e não recebem interface administrativa genérica.
- **FR-PC-BOUND-007**: Alteração estrutural de banco continua pertencendo às migrações automáticas no deploy e NÃO DEVE ser simulada por configuração.
- **FR-PC-BOUND-008**: Preferências e valores particulares de conta pertencem às respectivas features e ao armazenamento do usuário ou tenant.

### Decisões de Infraestrutura Auditáveis

- **FR-PC-INFRA-SCHED**: A plataforma NÃO DEVE manter scheduler para vigência futura desta feature; a reconciliação de versões aguardando reinício ou evento técnico DEVE ocorrer automaticamente sem depender de sessão ativa.
- **FR-PC-INFRA-LOCK**: Publicação e ativação da mesma definição DEVEM permanecer serializadas entre todas as instâncias e impedir duas versões efetivas.
- **FR-PC-INFRA-IDEMP**: Intenção de publicação, ativação e cancelamento DEVE possuir identidade estável suficiente para repetição sem duplicar versão ou efeito.
- **FR-PC-INFRA-RECOVERY**: Reinício ou troca de instância NÃO DEVE apagar versão pendente, reativar versão substituída nem inventar confirmação de ativação.
- **FR-PC-INFRA-BACKUP**: Definições, versões persistidas e auditorias DEVEM participar da proteção externa de dados; após restauração, contrato e vigência DEVEM ser revalidados antes de considerar valor efetivo.
- **FR-PC-INFRA-CLOCK**: Instantes de criação, publicação, cancelamento e ativação DEVEM usar referência temporal confiável para ordenação e auditoria, sem governar agendamento de vigência.

### Key Entities

- **Configuration Definition**: contrato global estável que descreve identidade, finalidade, tipo, origem exclusiva, escopo, validações, padrão, obrigatoriedade, sensibilidade, fallback e ativação de uma política.
- **Configuration Version**: valor global imutável proposto para uma definição, com versão-base, autor, justificativa, estado, criação e instante de ativação quando efetivada.
- **Effective Configuration**: resultado resolvido para definição, escopo e instante, com valor permitido, origem exclusiva, versão quando persistida e estado.
- **Configuration Dependency**: relação declarada entre definições que precisam ser validadas e ativadas de forma coerente.
- **Configuration Activation**: transição identificada que torna uma versão efetiva imediatamente ou após reinício ou evento técnico comprovado, sem agendamento temporal.
- **Configuration Audit Event**: evidência protegida de consulta, publicação, ativação, cancelamento, fallback e resultado.

## Success Criteria

### Measurable Outcomes

- **SC-PC-001**: Em 100% das consultas autorizadas, a interface identifica um único valor efetivo, sua origem, versão, vigência e estado sem revelar segredo.
- **SC-PC-002**: Em 100% dos testes, ator sem a chave específica não consulta histórico nem altera configuração, mesmo identificado como administrador do sistema.
- **SC-PC-003**: Em 100% das publicações válidas, uma nova versão imutável é criada e nenhuma versão histórica é sobrescrita.
- **SC-PC-004**: Em 100% das entradas inválidas, dependências cíclicas ou reduções abaixo do limite seguro, o valor efetivo permanece inalterado.
- **SC-PC-005**: Em 100% das publicações concorrentes baseadas na mesma versão, no máximo uma conclui sem revisão da versão-base.
- **SC-PC-006**: Em 100% dos instantes testados, existe no máximo uma versão efetiva por definição e ambiente.
- **SC-PC-007**: Pelo menos 95% das ativações dinâmicas válidas convergem em todas as instâncias disponíveis em até 10 segundos.
- **SC-PC-008**: Em 100% das ativações que exigem reinício, nenhuma instância apresenta a versão como efetiva antes de comprovar que iniciou sob ela.
- **SC-PC-009**: Em 100% dos fallbacks testados, a estratégia declarada é aplicada e a origem da inconsistência permanece explicável.
- **SC-PC-010**: Em 100% dos retornos a valor histórico, uma nova versão é criada e a versão problemática permanece íntegra no histórico.
- **SC-PC-011**: Em 100% das alterações, efeitos funcionais concluídos sob versão anterior não são revertidos implicitamente.
- **SC-PC-012**: Nenhuma configuração, auditoria, log ou comparação contém credencial, chave privada, token ou segredo de integração.
- **SC-PC-013**: Em 100% das alterações sensíveis, existe autenticação forte recente, confirmação, justificativa e auditoria completa ou a alteração não é concluída.
- **SC-PC-014**: Em 100% das restaurações externas testadas, versão incompatível, cancelada ou substituída não se torna efetiva sem revalidação do contrato e da vigência.
- **SC-PC-015**: Pelo menos 90% dos administradores em teste localizam uma definição e explicam o valor efetivo em até 60 segundos sem ajuda.
- **SC-PC-016**: Todas as jornadas administrativas podem ser concluídas apenas por teclado e mantêm definição, estado, origem, risco, vigência e diferenças compreensíveis sem depender somente de cor.
- **SC-PC-017**: Em 100% dos testes, configuração global não concede acesso, não altera plano e não consulta nem modifica valor particular de usuário ou tenant.
- **SC-PC-018**: Em 100% das definições, existe exatamente uma origem e um escopo; código duplicado entre origens impede a inicialização segura da capacidade afetada.
- **SC-PC-019**: Em 100% dos testes de configuração do Rinos, variável de ambiente, propriedade JVM e argumento de linha de comando não sobrescrevem valores, redirecionam o arquivo, importam outra fonte nem ativam perfil que altere o `application.properties` explícito.
- **SC-PC-020**: Em 100% dos testes, leitura de configuração `TENANT_DATABASE` sem tenant explícito é recusada e nunca utiliza valor global, de arquivo ou de outro tenant.
- **SC-PC-021**: Em 100% das ativações condicionadas a evento, a versão permanece pendente até a comprovação do evento declarado e nunca produz efeito antecipadamente.
- **SC-PC-022**: Em 100% das tentativas de informar data, hora ou prazo futuro de ativação, a publicação é recusada sem alterar o valor efetivo nem criar agendamento.
- **SC-PC-023**: Em 100% das alterações `TENANT_DATABASE`, o tenant é explícito, o valor é validado pela definição tipada e existe auditoria correlacionável, mesmo quando a feature consumidora não adota versionamento.
- **SC-PC-024**: Em 100% das publicações globais válidas, um único administrador devidamente autorizado consegue concluir o fluxo sem aprovação de segunda pessoa; a ausência de qualquer controle obrigatório impede a publicação.
