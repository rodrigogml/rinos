# Feature Specification: Painel e Configurações do Usuário

**Feature**: `user-dashboard`
**Created**: 2026-07-19
**Status**: Clarified — pronta para planejamento

## Escopo

Esta feature oferece ao usuário ativo um espaço global e privado para consultar os dados de sua própria identidade, alterar seu e-mail mediante confirmação, consultar e atualizar aceites aplicáveis e acessar as configurações de segurança definidas pela feature `user-authentication`.

O Painel de Usuário não representa uma conta pessoal, empresa ou tenant. Nesta etapa ele não permite criar contas, assinar planos, aceitar convites, selecionar contexto de conta, consultar dados de terceiros nem acessar módulos de negócio. Essas capacidades serão adicionadas pelas respectivas features futuras.

## Clarifications

### Session 2026-07-19

- Q: O Painel de Usuário inicial deve oferecer encerramento da identidade em autosserviço? -> A: Não; desativação e cancelamento voluntário ficam fora do primeiro escopo e serão definidos após o ciclo de vida das contas.

## User Scenarios & Testing

### User Story 1 - Acessar o próprio painel (Priority: P1)

Um usuário ativo autenticado acessa seu Painel de Usuário e reconhece sua identidade, o estado de suas configurações pessoais e os caminhos disponíveis, mesmo quando ainda não participa de nenhuma conta.

**Why this priority**: é o destino mínimo após a ativação ou autenticação e garante valor independente da existência de contas, empresas ou módulos.

**Independent Test**: autenticar um usuário ativo sem associação a contas e verificar que ele acessa somente seus dados e configurações globais, sem qualquer contexto de tenant.

**Acceptance Scenarios**:

1. **Given** usuário ativo autenticado, **When** abre o painel, **Then** visualiza somente informações pertencentes à própria identidade
2. **Given** usuário ativo sem associação a contas, **When** abre o painel, **Then** consegue utilizá-lo sem criação automática de conta, tenant ou plano
3. **Given** usuário não autenticado, **When** tenta abrir o painel, **Then** é direcionado ao fluxo de autenticação sem exposição de dados pessoais
4. **Given** usuário bloqueado, desativado ou cancelado, **When** tenta acessar o painel por sessão anterior, **Then** o acesso é negado e a sessão não permanece utilizável

---

### User Story 2 - Alterar o e-mail principal (Priority: P1)

Um usuário ativo solicita a troca de seu e-mail principal, confirma o novo endereço dentro do prazo e recebe confirmação de que a alteração foi concluída. Até a confirmação, o e-mail atual continua sendo o identificador válido.

**Why this priority**: o e-mail é o dado principal da identidade e sustenta autenticação, recuperação e notificações de segurança.

**Independent Test**: partir de usuário ativo, solicitar alteração para um endereço disponível, confirmar a prova enviada ao novo endereço e verificar que o novo e-mail substitui o anterior de forma atômica.

**Acceptance Scenarios**:

1. **Given** usuário reautenticado e novo e-mail disponível, **When** solicita a troca, **Then** o sistema mantém o e-mail atual e envia confirmação exclusivamente ao novo endereço
2. **Given** solicitação válida e não expirada, **When** o usuário comprova o novo e-mail, **Then** a troca é concluída uma única vez e ambos os endereços recebem aviso apropriado
3. **Given** solicitação não confirmada por 24 horas, **When** o prazo termina, **Then** a troca é descartada, o e-mail atual permanece e o usuário é avisado de que deverá reiniciar o processo
4. **Given** prova inválida, expirada, consumida ou pertencente a outra solicitação, **When** alguém tenta confirmar, **Then** nenhum e-mail é alterado
5. **Given** novo e-mail indisponível, **When** a troca é solicitada ou confirmada, **Then** o sistema rejeita a alteração sem revelar dados de outra identidade

---

### User Story 3 - Consultar e ajustar aceites (Priority: P2)

Um usuário consulta os documentos legais que aceitou, suas versões e datas, e pode atualizar escolhas opcionais sem confundi-las com os aceites obrigatórios necessários para utilizar o sistema.

**Why this priority**: oferece transparência e controle sobre consentimentos sem bloquear o acesso básico ao painel.

**Independent Test**: partir de usuário com aceites obrigatórios e opcionais registrados, consultar o histórico e alterar uma escolha opcional, preservando os registros históricos.

**Acceptance Scenarios**:

1. **Given** usuário com aceites registrados, **When** abre a área correspondente, **Then** visualiza documento, versão, finalidade, caráter obrigatório ou opcional e instante do aceite vigente
2. **Given** aceite opcional vigente, **When** o usuário o revoga, **Then** a preferência deixa de produzir efeitos futuros e o histórico permanece rastreável
3. **Given** nova versão obrigatória aplicável, **When** o usuário acessa uma funcionalidade que exige o aceite, **Then** o sistema apresenta a nova versão antes de permitir a continuidade dessa funcionalidade
4. **Given** aceite obrigatório vigente, **When** o usuário tenta apenas desmarcá-lo, **Then** o sistema explica que a revogação depende do encerramento da relação correspondente e não apaga o histórico

---

### User Story 4 - Acessar configurações de segurança (Priority: P2)

Um usuário acessa pelo painel a gestão de senha, passkeys, identidade Google, 2FA, códigos de recuperação e sessões, com indicação clara do estado de proteção de sua identidade.

**Why this priority**: centraliza a descoberta das capacidades de segurança sem duplicar seus contratos funcionais.

**Independent Test**: autenticar um usuário e verificar que o painel apresenta o estado resumido e direciona para cada operação permitida pela feature `user-authentication`.

**Acceptance Scenarios**:

1. **Given** usuário com ao menos um método de autenticação, **When** abre segurança, **Then** visualiza os métodos configurados sem exposição de segredos
2. **Given** usuário com 2FA obrigatório ainda incompleto, **When** abre o painel, **Then** recebe orientação destacada para concluir a proteção antes de exercer acesso administrativo
3. **Given** usuário seleciona uma operação sensível, **When** sua autenticação não é recente, **Then** o fluxo exige reautenticação conforme a política vigente
4. **Given** serviço externo de um método está indisponível, **When** o painel é aberto, **Then** as demais configurações continuam acessíveis e a indisponibilidade é informada sem remover vínculos

### Edge Cases

- O usuário abre o painel simultaneamente em dispositivos diferentes.
- O estado do usuário muda enquanto o painel está aberto.
- Duas solicitações de alteração de e-mail são iniciadas em sequência.
- O novo e-mail torna-se indisponível entre a solicitação e a confirmação.
- A confirmação da troca é repetida após sucesso, cancelamento ou expiração.
- O usuário perde acesso ao novo e-mail antes de confirmar a troca.
- A entrega do aviso de expiração ou de segurança falha temporariamente.
- Uma nova versão de documento obrigatório entra em vigor durante uma sessão ativa.
- O histórico de aceites contém versões que já não estão publicamente vigentes.
- Uma restauração de backup contém solicitação de troca de e-mail já expirada ou consumida.

## Requirements

### Acesso e Isolamento do Painel

- **FR-UD-001**: Somente usuário ativo com sessão autenticada válida DEVE acessar o Painel de Usuário.
- **FR-UD-002**: O painel DEVE operar no contexto global da identidade e NÃO DEVE criar, inferir ou reutilizar contexto de tenant.
- **FR-UD-003**: O painel DEVE apresentar somente dados, estados, avisos e operações pertencentes ao usuário autenticado.
- **FR-UD-004**: Identificadores fornecidos pelo cliente NÃO DEVEM permitir consultar ou alterar outro usuário.
- **FR-UD-005**: Um usuário sem associação a contas DEVE utilizar integralmente as capacidades desta feature.
- **FR-UD-006**: A ativação ou o primeiro acesso ao painel NÃO DEVE criar automaticamente conta pessoal, empresa, tenant, plano, assinatura, papel, grupo, chave ou permissão.
- **FR-UD-007**: Criação de contas, assinatura de planos, convites, seleção de conta e módulos de negócio NÃO DEVEM ser apresentados como operações disponíveis enquanto suas respectivas features não estiverem liberadas.
- **FR-UD-008**: Tentativas sem autenticação DEVEM preservar o destino solicitado apenas quando isso não expuser dados e DEVEM exigir autenticação antes de retornar ao painel.
- **FR-UD-009**: Bloqueio, desativação ou cancelamento do usuário DEVE impedir imediatamente o acesso ao painel.
- **FR-UD-010**: O painel DEVE oferecer encerramento seguro da sessão atual.
- **FR-UD-011**: Estados de carregamento, ausência de dados, indisponibilidade parcial e falha DEVEM ser informados de modo compreensível e não podem exibir dados residuais de outra sessão.
- **FR-UD-012**: Toda informação e operação do painel DEVE ser utilizável apenas por teclado e compatível com tecnologias assistivas.

### Identidade Exibida

- **FR-UD-ID-001**: O painel DEVE exibir o e-mail principal confirmado e o estado geral da identidade sem expor identificadores internos.
- **FR-UD-ID-002**: O e-mail principal DEVE ser apresentado de forma inequívoca como identificador global do usuário, e não como propriedade de uma conta ou empresa.
- **FR-UD-ID-003**: Dados ainda não pertencentes ao cadastro inicial, incluindo nome, telefone, documento e endereço, NÃO DEVEM ser exigidos para acessar ou utilizar o painel nesta etapa.
- **FR-UD-ID-004**: Avisos de segurança ou pendências DEVEM indicar a ação necessária sem revelar segredos, provas temporárias ou dados de terceiros.

### Alteração do E-mail Principal

- **FR-UD-EMAIL-001**: Somente usuário ativo com reautenticação recente DEVE iniciar alteração do e-mail principal.
- **FR-UD-EMAIL-002**: O novo e-mail DEVE ser normalizado e validado pelas mesmas regras de identidade e unicidade aplicadas ao cadastro.
- **FR-UD-EMAIL-003**: O sistema DEVE rejeitar novo e-mail já utilizado ou reservado por outra identidade sem revelar qualquer dado adicional dessa identidade.
- **FR-UD-EMAIL-004**: Uma solicitação aceita NÃO DEVE alterar o e-mail principal antes da confirmação do novo endereço.
- **FR-UD-EMAIL-005**: A solicitação DEVE enviar uma prova exclusivamente ao novo e-mail e um aviso de segurança ao e-mail principal atual.
- **FR-UD-EMAIL-006**: A prova DEVE ser imprevisível, vinculada ao usuário, ao novo e-mail e à finalidade de troca, utilizável uma única vez e válida por 24 horas.
- **FR-UD-EMAIL-007**: O sistema DEVE manter no máximo uma solicitação de troca de e-mail ativa por usuário.
- **FR-UD-EMAIL-008**: Iniciar nova solicitação DEVE invalidar qualquer solicitação anterior ainda aberta e exigir nova reautenticação.
- **FR-UD-EMAIL-009**: O usuário DEVE poder cancelar uma solicitação aberta sem alterar o e-mail principal.
- **FR-UD-EMAIL-010**: A confirmação DEVE revalidar a disponibilidade do novo e-mail e concluir a troca de forma atômica.
- **FR-UD-EMAIL-011**: Prova inválida, expirada, cancelada, consumida ou pertencente a outra solicitação NÃO DEVE alterar a identidade.
- **FR-UD-EMAIL-012**: Solicitação não confirmada dentro de 24 horas DEVE ser descartada automaticamente e não poderá ser reativada.
- **FR-UD-EMAIL-013**: Após a expiração, o sistema DEVE avisar no e-mail principal que a troca não ocorreu e que um novo processo será necessário.
- **FR-UD-EMAIL-014**: Após troca concluída, o sistema DEVE avisar o endereço anterior e o novo endereço sem incluir provas reutilizáveis.
- **FR-UD-EMAIL-015**: A troca concluída DEVE atualizar o identificador de login, o destino de recuperação e o destino dos futuros códigos de e-mail de forma consistente.
- **FR-UD-EMAIL-016**: A troca do e-mail Rinos NÃO DEVE criar, remover nem alterar automaticamente vínculos com identidades externas.
- **FR-UD-EMAIL-017**: A troca concluída DEVE invalidar todas as provas temporárias destinadas ao e-mail anterior e todas as demais sessões, preservando somente a sessão atual após nova confirmação de segurança.
- **FR-UD-EMAIL-018**: Confirmações concorrentes ou repetidas DEVEM produzir no máximo uma troca e retornar estado consistente.
- **FR-UD-EMAIL-019**: Solicitação, cancelamento, expiração, falha e conclusão da troca DEVEM ser auditados sem registrar a prova ou seu conteúdo secreto.

### Aceites e Preferências de Comunicação

- **FR-UD-CONSENT-001**: O usuário DEVE consultar os documentos aceitos com título, finalidade, versão, caráter obrigatório ou opcional e instante do aceite vigente.
- **FR-UD-CONSENT-002**: O sistema DEVE preservar o histórico de versões e mudanças de decisão necessário para auditoria, mesmo quando uma escolha deixar de vigorar.
- **FR-UD-CONSENT-003**: Aceites opcionais DEVEM permanecer separados dos obrigatórios e poder ser concedidos ou revogados individualmente.
- **FR-UD-CONSENT-004**: Revogar aceite opcional DEVE interromper efeitos futuros vinculados a ele sem apagar registros históricos legítimos.
- **FR-UD-CONSENT-005**: Aceites obrigatórios NÃO DEVEM ser apresentados como simples preferência revogável enquanto forem condição para manter a relação correspondente.
- **FR-UD-CONSENT-006**: Quando uma nova versão obrigatória exigir novo aceite, o sistema DEVE apresentar o conteúdo e registrar decisão explícita antes de liberar as funcionalidades condicionadas.
- **FR-UD-CONSENT-007**: A ausência de aceite de nova versão obrigatória NÃO DEVE conceder acesso a contas, tenants ou módulos condicionados, mas DEVE preservar acesso às ações necessárias para revisar a decisão e proteger ou encerrar a identidade.
- **FR-UD-CONSENT-008**: Documentos aceitos DEVEM permanecer consultáveis em formato legível correspondente à versão efetivamente aceita.
- **FR-UD-CONSENT-009**: Alterações de aceites DEVEM registrar usuário, versão, decisão, instante e origem validada.
- **FR-UD-CONSENT-010**: Falha ao aplicar uma preferência opcional NÃO DEVE registrar aceite ou revogação parcial como concluído.

### Configurações de Segurança

- **FR-UD-SEC-001**: O painel DEVE apresentar acesso à gestão de senha, passkeys, identidade Google, 2FA, códigos de recuperação e sessões conforme disponibilidade para o usuário.
- **FR-UD-SEC-002**: O painel DEVE exibir somente estado resumido e informações seguras de reconhecimento, sem exibir senhas, segredos TOTP, provas, códigos já emitidos ou chaves privadas.
- **FR-UD-SEC-003**: Operações de segurança DEVEM obedecer integralmente aos requisitos de reautenticação, recuperação, auditoria e invalidação definidos em `user-authentication`.
- **FR-UD-SEC-004**: O painel NÃO DEVE permitir remover o último método de autenticação utilizável.
- **FR-UD-SEC-005**: Usuário sujeito a 2FA obrigatório e ainda sem fator compatível DEVE receber aviso destacado e acesso direto à configuração necessária.
- **FR-UD-SEC-006**: O aviso de 2FA obrigatório NÃO DEVE conceder, remover ou inferir papel, grupo, chave ou permissão.
- **FR-UD-SEC-007**: Indisponibilidade de um provedor externo NÃO DEVE ocultar ou impedir a gestão dos demais métodos independentes.
- **FR-UD-SEC-008**: Alterações concluídas por fluxos de segurança DEVEM refletir no painel sem permitir reutilização de estado anterior da sessão ou do navegador.

### Ciclo de Vida da Identidade

- **FR-UD-LIFE-001**: O Painel de Usuário inicial NÃO DEVE oferecer desativação ou cancelamento voluntário da identidade em autosserviço; essa capacidade será especificada após a definição do ciclo de vida das contas e das responsabilidades associadas.
- **FR-UD-LIFE-002**: Nenhum fluxo de ciclo de vida DEVE apagar ou anonimizar imediatamente dados cuja preservação seja necessária por segurança, auditoria, prevenção a fraude ou obrigação legal.
- **FR-UD-LIFE-003**: Estado cancelado ou desativado NÃO DEVE ser utilizado como autorização implícita para excluir dados pertencentes a futuras contas ou empresas.

### Decisões de Infraestrutura Auditáveis

- **FR-UD-INFRA-SCHED**: O sistema DEVE identificar ao menos a cada hora solicitações de troca de e-mail que completaram 24 horas, descartá-las de forma idempotente e solicitar o envio do aviso de expiração uma única vez.
- **FR-UD-INFRA-LOCK**: Confirmação, cancelamento, expiração e substituição de solicitação de troca DEVEM permanecer consistentes quando processados concorrentemente por múltiplas instâncias.
- **FR-UD-INFRA-IDEMP**: Repetições da mesma confirmação, alteração de aceite ou cancelamento DEVEM produzir no máximo um efeito e não recriar provas ou decisões anteriores.
- **FR-UD-INFRA-BACKUP**: Dados persistentes da identidade, solicitações e aceites DEVEM participar dos backups do sistema; restaurações NÃO DEVEM reativar solicitações expiradas, canceladas ou consumidas.
- **FR-UD-INFRA-KEY**: Provas persistentes de confirmação DEVEM permanecer protegidas durante rotação de chaves, sem tornar provas revogadas ou expiradas novamente válidas.

### Key Entities

- **User Identity**: identidade global do usuário, com e-mail principal confirmado, estado e referências aos seus métodos e decisões pessoais.
- **Email Change Request**: intenção temporária de substituir o e-mail principal, com novo endereço, validade, estado, origem e rastreabilidade, sem armazenar prova recuperável.
- **Legal Document Version**: versão imutável de um documento obrigatório ou opcional apresentada ao usuário.
- **User Consent Decision**: decisão do usuário sobre uma versão e finalidade, com caráter, estado, instante e origem, preservando o histórico aplicável.
- **Security Summary**: visão segura e não secreta do estado dos métodos de autenticação e sessões disponibilizada ao próprio usuário.

## Success Criteria

### Measurable Outcomes

- **SC-UD-001**: Pelo menos 95% dos usuários autenticados acessam e reconhecem seu e-mail e suas configurações disponíveis em até 10 segundos.
- **SC-UD-002**: Em 100% dos testes com usuário sem contas, o painel permanece acessível sem criar conta, tenant, plano, associação ou permissão.
- **SC-UD-003**: Em 100% dos testes de isolamento, um usuário não consulta nem altera dados da identidade de outro usuário.
- **SC-UD-004**: Em 100% dos testes, usuários bloqueados, desativados ou cancelados não acessam o painel por sessões anteriores.
- **SC-UD-005**: Pelo menos 90% dos usuários em teste concluem uma troca válida de e-mail sem ajuda em até três minutos, excluído o tempo de entrega da mensagem.
- **SC-UD-006**: Em 100% dos testes, o e-mail principal permanece inalterado antes da confirmação válida do novo endereço.
- **SC-UD-007**: Em 100% dos testes, solicitações de troca não confirmadas são descartadas após 24 horas e não podem ser reativadas.
- **SC-UD-008**: Em 100% dos testes concorrentes ou repetidos, uma solicitação de e-mail produz no máximo uma alteração.
- **SC-UD-009**: Em 100% dos testes, a troca concluída atualiza login, recuperação e códigos por e-mail sem alterar vínculos externos.
- **SC-UD-010**: Em 100% dos testes, uma escolha opcional pode ser alterada sem modificar ou apagar aceites obrigatórios e seu histórico.
- **SC-UD-011**: Em 100% dos testes, a versão do documento exibida no histórico corresponde à versão efetivamente aceita.
- **SC-UD-012**: Nenhuma tela do painel exibe senha, segredo TOTP, prova temporária, código de recuperação já emitido ou chave privada.
- **SC-UD-013**: Todas as jornadas do painel podem ser concluídas apenas por teclado e sem bloqueios críticos para tecnologias assistivas.
