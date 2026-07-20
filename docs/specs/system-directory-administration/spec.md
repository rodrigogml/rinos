# Feature Specification: Administração do Diretório Global

**Feature**: `system-directory-administration`
**Created**: 2026-07-20
**Status**: Clarified

## Escopo

Esta feature oferece a administradores do sistema devidamente autorizados uma visão global e segura das identidades de usuários, contas e relações administrativas necessárias à operação da plataforma. Ela permite localizar registros, compreender estados, aplicar intervenções globais proporcionais, invalidar acessos comprometidos, suspender contas por motivo operacional e recuperar a administração mínima de uma conta que ficou sem participante apto.

Todas as ações operam no contexto global e são autorizadas por grupos e chaves do sistema. O papel “Administrador do Sistema” identifica o ator, mas não concede acesso por si próprio. Consultar ou administrar o diretório não estabelece contexto de tenant, não permite visualizar dados internos da conta e não torna o administrador participante dela.

Não inclui representação de usuário, login como terceiro, acesso temporário ao conteúdo de tenant, redefinição manual de senha, leitura de fatores de autenticação, edição do e-mail do usuário, exclusão física de dados, tratamento completo de solicitações de privacidade, operações de backup ou migração, manutenção do catálogo de planos, configuração geral da plataforma nem administração de grupos e chaves já definida em `access-control`.

## Clarifications

### Session 2026-07-20

- Q: Quem deve autorizar a recuperação administrativa de uma conta sem participante apto? → A: Um único administrador do sistema com chave exclusiva pode autorizá-la, após autenticação recente por TOTP ou passkey, justificativa e evidências; a recuperação ainda depende do aceite do responsável-alvo, auditoria e notificações. Não há segundo aprovador obrigatório nesta etapa.
- Q: Quem pode ser indicado como responsável na recuperação administrativa excepcional? → A: Qualquer usuário Rinos já ativo, com e-mail confirmado e 2FA compatível, ainda que não participe da conta; nesse caso, associação e concessões mínimas somente são criadas depois de seu aceite explícito e da revalidação final.
- Q: Qual deve ser a validade da solicitação de recuperação administrativa excepcional? → A: O prazo máximo deve ser configurável, com padrão inicial de 7 dias contado da criação. Não existe espera mínima: o administrador pode analisar, autorizar, negar ou cancelar antes do vencimento; depois dele, qualquer etapa ainda pendente perde validade e exige nova solicitação.
- Q: Quais intervenções globais sobre a identidade estarão disponíveis inicialmente? → A: Bloquear ou desbloquear a identidade, invalidar suas sessões e exigir recuperação conduzida pelo próprio usuário. A administração global não poderá desativar, cancelar ou excluir a identidade, trocar seu e-mail, definir senha nem alterar seus fatores de autenticação.
- Q: O que fazer quando uma intervenção deixaria a plataforma sem administrador global apto? → A: A operação deve ser recusada atomicamente. Deve permanecer ao menos um usuário com identidade ativa, concessões administrativas globais necessárias e 2FA forte compatível; outro administrador apto precisa ser preparado antes da intervenção.

## User Scenarios & Testing

### User Story 1 - Localizar usuários e contas no diretório global (Priority: P1)

Um administrador do sistema com chaves específicas pesquisa identidades e contas por critérios globais, reconhece o registro correto e acessa somente metadados necessários à administração da plataforma.

**Why this priority**: qualquer diagnóstico ou intervenção precisa começar por uma identificação inequívoca sem abrir dados internos de tenants.

**Independent Test**: pesquisar usuários e contas existentes e inexistentes com e sem chaves, verificando resultado, paginação, proteção de dados, auditoria e ausência de contexto de tenant.

**Acceptance Scenarios**:

1. **Given** administrador autenticado com chave de consulta, **When** pesquisa uma identidade por critério permitido, **Then** recebe somente os metadados globais necessários para reconhecer o usuário
2. **Given** administrador com chave de consulta de contas, **When** pesquisa uma conta, **Then** visualiza identidade global, estado, plano, tenant e saúde resumida sem conteúdo funcional da conta
3. **Given** ator sem chave global necessária, **When** tenta pesquisar o diretório, **Then** a operação é negada independentemente de seu papel
4. **Given** resultado relacionado a várias contas, **When** é apresentado, **Then** cada registro permanece distinguível sem consultar ou combinar dados internos dos tenants
5. **Given** pesquisa ou abertura de detalhe, **When** ocorre, **Then** a finalidade, o ator, os critérios seguros e o resultado são auditados proporcionalmente ao risco

---

### User Story 2 - Investigar e proteger uma identidade global (Priority: P1)

Um administrador autorizado consulta o estado seguro de uma identidade, suas sessões e vínculos globais resumidos e aplica bloqueio ou outra intervenção permitida quando há risco, sem definir uma nova senha nem obter seus segredos.

**Why this priority**: comprometimento de identidade afeta todas as contas do usuário e exige resposta global imediata e rastreável.

**Independent Test**: partir de usuário ativo com múltiplas sessões e contas, aplicar bloqueio autorizado e comprovar invalidação dos acessos, preservação das contas e ausência de exposição de credenciais.

**Acceptance Scenarios**:

1. **Given** usuário ativo e administrador com chave de intervenção, **When** confirma o bloqueio com justificativa, **Then** todas as sessões e provas temporárias do usuário são invalidadas sem alterar o estado de suas contas
2. **Given** usuário bloqueado, **When** tenta autenticar ou iniciar nova operação, **Then** o acesso é negado conforme `user-authentication`
3. **Given** causa resolvida e desbloqueio autorizado, **When** a identidade retorna ao estado permitido, **Then** sessões anteriores não são reativadas e o usuário precisa autenticar novamente
4. **Given** administrador consulta métodos de autenticação, **When** o resumo é exibido, **Then** apresenta somente tipos, estados e informações seguras, nunca senhas, segredos, códigos ou provas
5. **Given** intervenção concluída ou negada, **When** a auditoria é consultada, **Then** apresenta alvo, ator, motivo, estado anterior, estado resultante e notificações solicitadas
6. **Given** administrador global autorizado, **When** tenta desativar, cancelar ou excluir a identidade, trocar seu e-mail, definir senha ou alterar 2FA, **Then** a operação não existe nesta feature e deve ser recusada sem modificar o usuário
7. **Given** existe apenas um administrador global apto, **When** bloqueio, invalidação de aptidão ou revogação concorrente deixaria a plataforma sem administrador apto, **Then** a operação é recusada atomicamente e o estado anterior é preservado

---

### User Story 3 - Intervir no estado global de uma conta (Priority: P1)

Um administrador autorizado suspende uma conta diante de risco, abuso ou problema operacional da plataforma e permite sua regularização ou retorno controlado, sem assumir acesso aos dados do tenant.

**Why this priority**: incidentes de plataforma podem exigir contenção de uma conta mesmo quando seus administradores não estão disponíveis.

**Independent Test**: suspender uma conta ativa, verificar bloqueio de novas operações comuns, manutenção dos dados e demais contas, acesso apenas às ações de regularização e posterior retorno autorizado.

**Acceptance Scenarios**:

1. **Given** conta ativa e risco justificado, **When** administrador com chave específica confirma a suspensão, **Then** novas operações comuns são bloqueadas sem apagar dados ou alterar outras contas
2. **Given** conta suspensa, **When** participante autorizado acessa seu contexto restrito, **Then** somente ações de consulta ou regularização explicitamente permitidas ficam disponíveis
3. **Given** causa resolvida e conta estruturalmente compatível, **When** reativação autorizada é confirmada, **Then** a conta retorna ao estado operacional sem restaurar sessões ou concessões revogadas
4. **Given** administrador do sistema sem associação ao tenant, **When** suspende ou reativa a conta, **Then** não recebe contexto, grupo, chave ou acesso aos dados internos dela
5. **Given** conta em criação, falha, migração ou cancelamento, **When** uma transição incompatível é solicitada, **Then** ela é rejeitada e orienta a operação administrativa adequada

---

### User Story 4 - Recuperar a administração mínima de uma conta (Priority: P1)

Quando uma conta não possui participante ativo com as chaves administrativas mínimas e 2FA compatível, um administrador do sistema autorizado inicia recuperação excepcional, identifica um responsável apto e restaura somente a capacidade mínima necessária para a conta voltar a se autoadministrar.

**Why this priority**: as regras comuns impedem remover o último administrador, mas falhas, bloqueios ou inconsistências ainda podem deixar uma conta sem responsável apto.

**Independent Test**: preparar conta sem participante administrativamente apto, executar recuperação com alvo válido e confirmar concessões mínimas, aceite, notificações, auditoria e ausência de acesso do operador global ao tenant.

**Acceptance Scenarios**:

1. **Given** conta com ao menos um participante ativo e apto, **When** recuperação excepcional é solicitada, **Then** a operação é rejeitada e o fluxo comum de administração deve ser usado
2. **Given** conta sem participante apto, **When** administrador autorizado abre recuperação com motivo e evidências, **Then** surge uma solicitação rastreável sem concessão imediata
3. **Given** usuário-alvo ativo, identificado e com 2FA compatível, **When** aceita a responsabilidade dentro do prazo, **Then** recebe somente grupo e chaves mínimas de administração daquela conta
4. **Given** alvo rejeita, expira, perde aptidão ou não confirma a responsabilidade, **When** a solicitação termina, **Then** nenhuma concessão é criada e uma nova recuperação será necessária
5. **Given** recuperação concluída, **When** operador global encerra o processo, **Then** ele não se torna participante e não recebe acesso ao tenant
6. **Given** recuperação repetida ou concorrente, **When** é processada, **Then** no máximo uma solicitação vigente e uma concessão mínima válida produzem efeito
7. **Given** administrador global com chave exclusiva, autenticação recente e fator forte, **When** autoriza recuperação com justificativa e evidências, **Then** a solicitação pode seguir ao aceite do alvo sem exigir segundo aprovador
8. **Given** usuário ativo da plataforma que ainda não participa da conta, com e-mail confirmado e 2FA compatível, **When** aceita expressamente a recuperação e passa pela revalidação final, **Then** sua associação e as concessões administrativas mínimas são criadas de forma atômica
9. **Given** solicitação de recuperação ainda dentro do prazo máximo configurado, **When** o administrador conclui sua análise, **Then** pode autorizá-la, negá-la ou cancelá-la imediatamente, sem aguardar qualquer período mínimo
10. **Given** solicitação criada há mais do que o prazo máximo configurado, **When** administrador ou usuário-alvo tenta prosseguir, **Then** a ação é recusada, a solicitação é encerrada como expirada e nenhuma associação ou concessão é criada

---

### User Story 5 - Acompanhar intervenções e pendências globais (Priority: P2)

Um administrador autorizado consulta ações recentes, solicitações pendentes, bloqueios, suspensões e recuperações, identifica o que exige atenção e acompanha seu resultado sem utilizar relatórios como atalho para dados de tenant.

**Why this priority**: intervenções sensíveis precisam de supervisão, vencimento e reconciliação, especialmente quando mais de uma instância ou administrador estiver operando.

**Independent Test**: criar intervenções em estados diferentes e comprovar filtros, alertas, expiração, auditoria, isolamento e acessibilidade da área administrativa.

**Acceptance Scenarios**:

1. **Given** intervenções pendentes, concluídas, expiradas e negadas, **When** o administrador consulta a fila, **Then** identifica tipo, alvo, responsável, prazo, última atividade e ação possível
2. **Given** solicitação sem progresso além do limite, **When** a monitoração a encontra, **Then** produz alerta rastreável sem concluir a ação automaticamente
3. **Given** concessão global do operador é revogada durante uma solicitação ainda pendente, **When** ele tenta continuar, **Then** a operação é negada e outro administrador apto deverá assumi-la
4. **Given** auditoria global consultada, **When** um evento referencia conta ou usuário, **Then** apresenta apenas o contexto necessário e não conteúdo funcional de tenant

### Edge Cases

- Dois usuários possuem e-mails visualmente semelhantes ou o mesmo nome futuro.
- O e-mail de um usuário muda entre a pesquisa e a intervenção.
- O usuário-alvo é o próprio administrador que executa a ação.
- O último administrador global apto tenta bloquear ou desativar a si mesmo.
- Duas instâncias bloqueiam e desbloqueiam a mesma identidade simultaneamente.
- O usuário é bloqueado durante uma operação já aceita ou durante recuperação de conta.
- Uma conta é suspensa enquanto uma migração, backup ou restauração está em andamento.
- A conta é suspensa e regularizada por administradores diferentes.
- A conta já está cancelada quando uma recuperação administrativa é solicitada.
- O único participante apto perde 2FA ou é bloqueado sem que sua associação seja removida.
- O alvo da recuperação ainda não participa da conta.
- O convite ou aceite da recuperação expira durante sua confirmação.
- Uma restauração reintroduz bloqueio, solicitação ou concessão já substituídos.
- A entrega de notificação falha após a intervenção ter sido confirmada.
- Uma consulta ampla tenta produzir volume excessivo ou enumerar todo o diretório.

## Requirements

### Contexto Global e Autorização

- **FR-SDA-CTX-001**: Toda operação desta feature DEVE executar em contexto global de sistema e NÃO DEVE estabelecer ou reutilizar tenant ativo da interface.
- **FR-SDA-CTX-002**: O papel Administrador do Sistema NÃO DEVE conceder acesso; cada consulta e intervenção DEVE exigir chave global específica.
- **FR-SDA-CTX-003**: Chaves de consulta, bloqueio de usuário, intervenção em conta e recuperação administrativa DEVEM ser separadas para permitir menor privilégio.
- **FR-SDA-CTX-004**: Operações sensíveis DEVEM exigir autenticação ocorrida há no máximo 15 minutos e segundo fator por TOTP ou passkey verificada.
- **FR-SDA-CTX-005**: Código enviado ao mesmo e-mail usado no login NÃO DEVE satisfazer o segundo fator de uma intervenção global.
- **FR-SDA-CTX-006**: Concessão global NÃO DEVE criar associação, grupo, chave ou contexto em qualquer conta.
- **FR-SDA-CTX-007**: Identificadores fornecidos pela interface DEVEM ser revalidados contra o registro global e não poderão direcionar a operação a outro tipo de alvo.
- **FR-SDA-CTX-008**: Mudança de concessão do administrador durante processo pendente DEVE ser observada antes de cada nova etapa.
- **FR-SDA-CTX-009**: Estado bloqueado, desativado ou cancelado do próprio administrador DEVE invalidar sua sessão e impedir continuidade.

### Pesquisa e Diretório

- **FR-SDA-DIR-001**: Usuários e contas DEVEM ser pesquisáveis apenas por critérios globais explicitamente permitidos e adequados à finalidade administrativa.
- **FR-SDA-DIR-002**: Resultado de usuário DEVE limitar-se a identificador seguro, e-mail confirmado, estado, criação, última atividade de segurança conhecida e indicadores resumidos autorizados.
- **FR-SDA-DIR-003**: Resultado de conta DEVE limitar-se a identificador seguro, nome, estado, plano, criação, tenant, versão e saúde operacional resumida autorizada.
- **FR-SDA-DIR-004**: Relação resumida entre usuário e contas DEVE apresentar somente identidade da conta, estado da associação e papel de ator, sem grupos, chaves ou conteúdo do tenant quando não houver chave específica adicional.
- **FR-SDA-DIR-005**: Consulta de grupos e concessões globais ou de conta DEVE obedecer integralmente a `access-control` e suas chaves de explicação.
- **FR-SDA-DIR-006**: Resultado NÃO DEVE exibir senha, hash, segredo TOTP, código, token, prova, chave privada, localização física do tenant nem dado funcional de módulo.
- **FR-SDA-DIR-007**: Pesquisa DEVE possuir paginação e limites capazes de impedir extração irrestrita do diretório por uma única consulta.
- **FR-SDA-DIR-008**: Critérios inválidos, excessivamente amplos ou não autorizados DEVEM ser rejeitados sem retornar conjunto parcial indevido.
- **FR-SDA-DIR-009**: Resultado inexistente DEVE ser distinguível para administrador autorizado, mas não poderá alterar respostas dos fluxos públicos de autenticação ou cadastro.
- **FR-SDA-DIR-010**: Pesquisa, abertura de detalhe e exportação administrativa futura DEVEM ser auditadas conforme sensibilidade, sem registrar o termo completo quando ele contiver dado pessoal desnecessário.
- **FR-SDA-DIR-011**: A interface DEVE diferenciar claramente usuário, conta, tenant, associação e armazenamento para impedir intervenção no alvo errado.
- **FR-SDA-DIR-012**: Estado carregando, vazio, indisponível ou parcial NÃO DEVE apresentar dados residuais de pesquisa anterior.

### Intervenções na Identidade

- **FR-SDA-USER-001**: Ações administrativas sobre identidade DEVEM apresentar estado atual, efeito global esperado, contas relacionadas em resumo e exigir motivo antes da confirmação.
- **FR-SDA-USER-002**: Bloqueio administrativo DEVE impedir novas autenticações e operações do usuário em todos os contextos.
- **FR-SDA-USER-003**: Bloqueio DEVE invalidar imediatamente todas as sessões, provas temporárias e contextos ainda utilizáveis do usuário.
- **FR-SDA-USER-004**: Bloqueio do usuário NÃO DEVE suspender, cancelar, transferir nem alterar automaticamente qualquer conta da qual participe.
- **FR-SDA-USER-005**: Desbloqueio NÃO DEVE reativar sessões, provas, convites, associações, grupos ou chaves expirados, encerrados ou revogados.
- **FR-SDA-USER-006**: Usuário desbloqueado DEVE autenticar novamente pelos métodos ainda válidos.
- **FR-SDA-USER-007**: Administrador NÃO DEVE definir, visualizar ou transmitir nova senha em nome do usuário.
- **FR-SDA-USER-008**: Quando necessário, administrador autorizado PODERÁ invalidar sessões e iniciar exigência de recuperação pelo próprio usuário, sem receber a prova de recuperação.
- **FR-SDA-USER-009**: Administrador NÃO DEVE editar diretamente o e-mail principal, criar passkey, obter código de recuperação ou desativar 2FA em nome do usuário.
- **FR-SDA-USER-010**: Intervenção DEVE ser negada atomicamente quando puder remover o último administrador global apto; outro administrador apto DEVE ser preparado antes da operação.
- **FR-SDA-USER-011**: Estado anterior, estado resultante, ator, motivo, origem, instante e resultado de cada intervenção DEVEM ser auditados.
- **FR-SDA-USER-012**: Usuário afetado DEVE ser notificado por canal seguro quando isso não ampliar risco ou comprometer investigação registrada.
- **FR-SDA-USER-013**: As intervenções globais iniciais sobre identidade DEVEM limitar-se a bloquear, desbloquear, invalidar sessões e exigir recuperação conduzida pelo próprio usuário.
- **FR-SDA-USER-014**: Esta feature NÃO DEVE desativar, cancelar, excluir ou executar solicitação de privacidade sobre a identidade.
- **FR-SDA-USER-015**: Esta feature NÃO DEVE trocar o e-mail, definir ou redefinir senha, ativar ou desativar 2FA, nem cadastrar, remover ou substituir fator de autenticação do usuário.
- **FR-SDA-USER-016**: Exigir recuperação DEVE apenas marcar a condição de segurança, invalidar as provas abrangidas e encaminhar o usuário ao fluxo de `user-authentication`; o administrador NÃO DEVE receber código, link, token ou outro segredo desse processo.
- **FR-SDA-USER-017**: Administrador global apto DEVE significar usuário com identidade ativa, concessões globais administrativas necessárias em vigor e TOTP ou passkey compatível disponível para o exercício administrativo.
- **FR-SDA-USER-018**: A proteção do último administrador apto DEVE abranger bloqueio, revogação ou expiração de concessões, retirada de grupo, perda de fator forte e qualquer outra transição administrada pelo sistema que elimine sua aptidão.
- **FR-SDA-USER-019**: Verificações concorrentes DEVEM preservar o invariante de ao menos um administrador global apto; duas operações individualmente válidas não poderão ser concluídas se o resultado combinado violar esse invariante.
- **FR-SDA-USER-020**: Tentativa recusada pela proteção do último administrador DEVE preservar integralmente o estado anterior, ser auditada e orientar a preparação prévia de outro administrador apto.

### Intervenções na Conta

- **FR-SDA-ACC-001**: Intervenção em conta DEVE validar identidade global, estado cadastral, estado do armazenamento e operação estrutural em andamento antes de propor transição.
- **FR-SDA-ACC-002**: Suspensão global DEVE bloquear novas operações comuns da conta e preservar dados, auditoria, identidade, plano e associações.
- **FR-SDA-ACC-003**: Suspensão global NÃO DEVE alterar outras contas dos mesmos participantes nem a identidade global deles.
- **FR-SDA-ACC-004**: Conta suspensa DEVE permitir somente ações explicitamente autorizadas de consulta ou regularização no contexto restrito.
- **FR-SDA-ACC-005**: Reativação DEVE exigir causa da suspensão resolvida, conta estruturalmente compatível e ausência de impedimento de maior prioridade.
- **FR-SDA-ACC-006**: Reativação NÃO DEVE restaurar sessões, contextos, convites, associações ou concessões expirados, encerrados ou revogados.
- **FR-SDA-ACC-007**: Suspender ou reativar NÃO DEVE tornar o administrador global participante nem lhe conceder acesso ao tenant.
- **FR-SDA-ACC-008**: Cancelamento voluntário continua pertencendo a `account-registration`; intervenção global NÃO DEVE cancelar fisicamente uma conta por atalho.
- **FR-SDA-ACC-009**: Conta cancelada NÃO DEVE ser reativada por esta feature.
- **FR-SDA-ACC-010**: Transição incompatível com criação, migração, restauração, transferência ou desativação em andamento DEVE ser recusada ou coordenada pela feature operacional responsável.
- **FR-SDA-ACC-011**: Suspensão, reativação, falha e tentativa negada DEVEM registrar ator, motivo, estado anterior, estado resultante, impactos conhecidos e resultado.
- **FR-SDA-ACC-012**: Participantes administrativamente aptos DEVEM ser notificados da intervenção por canal seguro, salvo impedimento investigativo registrado.

### Recuperação da Administração da Conta

- **FR-SDA-REC-001**: Recuperação excepcional somente DEVE iniciar quando não existir participante ativo com chaves administrativas mínimas e 2FA compatível.
- **FR-SDA-REC-002**: Existência de participante apto DEVE bloquear a recuperação e direcionar ao fluxo comum de `account-membership` e `access-control`.
- **FR-SDA-REC-003**: Solicitação DEVE identificar conta, motivo, evidências seguras, solicitante, usuário-alvo, concessão mínima pretendida, criação e validade.
- **FR-SDA-REC-004**: Usuário-alvo DEVE possuir identidade ativa, e-mail confirmado e segundo fator compatível antes de receber capacidade administrativa.
- **FR-SDA-REC-005**: Usuário-alvo que ainda não participe da conta DEVE confirmar expressamente a entrada e a responsabilidade administrativa antes da criação da associação.
- **FR-SDA-REC-006**: Participante existente DEVE confirmar expressamente a nova responsabilidade antes de receber concessões adicionais.
- **FR-SDA-REC-007**: Aceite DEVE mostrar conta, responsabilidade, efeitos e origem administrativa sem expor evidências internas ou dados de terceiros.
- **FR-SDA-REC-008**: Recuperação DEVE conceder somente o grupo e as chaves mínimas necessárias para a conta voltar a administrar participantes e acessos.
- **FR-SDA-REC-009**: Papel de administrador da conta DEVE identificar o alvo após aceite, mas seu acesso efetivo continuará decorrendo das concessões explícitas.
- **FR-SDA-REC-010**: Operador global NÃO DEVE ser usado como alvo implícito, tornar-se participante nem receber qualquer concessão no tenant por executar a recuperação.
- **FR-SDA-REC-011**: A conta DEVE possuir no máximo uma solicitação de recuperação administrativa vigente.
- **FR-SDA-REC-012**: Nova solicitação autorizada DEVE substituir a anterior de forma explícita, invalidando seu aceite ainda não utilizado.
- **FR-SDA-REC-013**: Aceite expirado, rejeitado, cancelado, consumido ou destinado a outro usuário NÃO DEVE criar associação nem concessão.
- **FR-SDA-REC-014**: Confirmações concorrentes ou repetidas DEVEM produzir no máximo uma associação e um conjunto mínimo de concessões.
- **FR-SDA-REC-015**: Antes de concluir, o sistema DEVE revalidar conta, ausência de administrador apto, usuário-alvo, 2FA, solicitação e concessões pretendidas.
- **FR-SDA-REC-016**: Caso outro participante se torne apto durante a solicitação, a recuperação excepcional DEVE ser cancelada sem conceder acesso.
- **FR-SDA-REC-017**: A conclusão DEVE notificar o usuário-alvo e participantes alcançáveis, registrar toda a cadeia e encerrar a condição excepcional.
- **FR-SDA-REC-018**: Recuperação NÃO DEVE permitir acesso a dados da conta antes do aceite e da criação válida da associação e das concessões mínimas.
- **FR-SDA-REC-019**: Um único administrador do sistema com chave global exclusiva de autorização DEVE ser suficiente para autorizar a solicitação; a versão inicial NÃO DEVE exigir segundo aprovador global.
- **FR-SDA-REC-020**: Antes de autorizar, o sistema DEVE validar autenticação ocorrida há no máximo 15 minutos por TOTP ou passkey, chave específica, justificativa, evidências, elegibilidade da conta e identidade do usuário-alvo.
- **FR-SDA-REC-021**: A autorização do administrador global NÃO DEVE concluir a recuperação nem produzir associação ou concessão; somente o aceite do usuário-alvo e a revalidação final poderão fazê-lo.
- **FR-SDA-REC-022**: Solicitação, autorização, aceite, recusa, expiração e resultado da recuperação DEVEM formar uma cadeia integralmente auditável e gerar as notificações aplicáveis.
- **FR-SDA-REC-023**: O usuário-alvo PODERÁ ser qualquer usuário Rinos já ativo, com e-mail confirmado e 2FA compatível; participação prévia na conta, condição de criador original ou outro vínculo anterior NÃO DEVEM ser requisitos obrigatórios.
- **FR-SDA-REC-024**: Quando o usuário-alvo ainda não participar da conta, sua associação e as concessões mínimas DEVEM ser criadas somente após aceite explícito e revalidação final, em uma única conclusão consistente que não deixe associação sem as concessões mínimas nem concessões sem associação.
- **FR-SDA-REC-025**: A recuperação excepcional NÃO DEVE cadastrar uma nova identidade nem convidar endereço ainda não registrado; o destinatário deverá concluir seu cadastro e atender aos requisitos de segurança antes de poder ser indicado.
- **FR-SDA-REC-026**: A solicitação DEVE possuir prazo máximo de validade configurável, com valor padrão inicial de 7 dias contado de sua criação.
- **FR-SDA-REC-027**: O prazo máximo NÃO DEVE impor espera mínima; administrador autorizado PODERÁ analisar, autorizar, negar ou cancelar a solicitação a qualquer momento antes do vencimento.
- **FR-SDA-REC-028**: O prazo da solicitação DEVE abranger todas as etapas ainda pendentes, inclusive o aceite do usuário-alvo, e NÃO DEVE ser reiniciado pela autorização administrativa, pelo envio ou pela nova tentativa de uma notificação.
- **FR-SDA-REC-029**: Depois do vencimento, nenhuma autorização ou aceite DEVE produzir efeito; a solicitação será encerrada como expirada e eventual nova tentativa exigirá outra solicitação integralmente validada.

### Auditoria, Notificações e Privacidade

- **FR-SDA-AUD-001**: Consulta ou intervenção DEVE produzir evento global com ator, chave usada, alvo seguro, motivo, origem, correlação, instante e resultado.
- **FR-SDA-AUD-002**: Alterações em associações e concessões decorrentes de recuperação DEVEM produzir também eventos no contexto da conta conforme `tenant-data-governance`.
- **FR-SDA-AUD-003**: Auditoria global NÃO DEVE copiar conteúdo funcional do tenant nem valores secretos para explicar uma intervenção.
- **FR-SDA-AUD-004**: Motivos e evidências DEVEM ser limitados ao necessário e protegidos conforme sua sensibilidade.
- **FR-SDA-AUD-005**: Consulta ao histórico desta feature DEVE exigir chave global própria e também ser auditada.
- **FR-SDA-AUD-006**: Notificação DEVE informar ação, instante, efeito e canal de contestação ou suporte quando aplicável, sem expor detalhes investigativos indevidos.
- **FR-SDA-AUD-007**: Falha de entrega de notificação NÃO DEVE desfazer intervenção de segurança já confirmada, mas DEVE permanecer visível para nova tentativa.
- **FR-SDA-AUD-008**: Evento obrigatório que não puder ser persistido DEVE impedir conclusão da intervenção ou produzir evidência alternativa durável e reconciliável.
- **FR-SDA-AUD-009**: Dados desta feature DEVEM seguir classificação, retenção, backup, restauração e destinação de `tenant-data-governance`.

### Limites com Features Relacionadas

- **FR-SDA-BOUND-001**: Cadastro, ativação e estados comuns da identidade pertencem a `user-registration`; esta feature executa somente intervenções administrativas autorizadas.
- **FR-SDA-BOUND-002**: Sessões, fatores, provas e recuperação pessoal pertencem a `user-authentication`; esta feature pode invalidá-los, mas nunca lê ou recria seus segredos.
- **FR-SDA-BOUND-003**: Estados cadastrais e cancelamento voluntário da conta pertencem a `account-registration`.
- **FR-SDA-BOUND-004**: Associação comum, convites, entrada, saída e proteção do último administrador pertencem a `account-membership`; esta feature trata somente a exceção em que já não há administrador apto.
- **FR-SDA-BOUND-005**: Grupos, chaves, atribuições e explicação de acesso pertencem a `access-control`; a recuperação usa apenas os contratos mínimos definidos por ela.
- **FR-SDA-BOUND-006**: Acesso ao conteúdo de tenant, representação e suporte assistido pertencem à futura `tenant-support-access` e NÃO são concedidos por esta feature.
- **FR-SDA-BOUND-007**: Provisionamento, migração, restauração e saúde técnica detalhada pertencem a `platform-operations` e às features de armazenamento.
- **FR-SDA-BOUND-008**: Configurações globais, políticas e valores operacionais pertencem à futura `platform-configuration`.

### Decisões de Infraestrutura Auditáveis

- **FR-SDA-INFRA-LOCK**: Bloqueio, desbloqueio, suspensão, reativação e conclusão de recuperação DEVEM permanecer consistentes entre todas as instâncias concorrentes.
- **FR-SDA-INFRA-IDEMP**: Cada intervenção e solicitação DEVE possuir identidade estável, e sua repetição NÃO DEVE duplicar transições, associações, concessões ou notificações concluídas.
- **FR-SDA-INFRA-SCHED**: Solicitações vencidas e notificações pendentes DEVEM ser processadas automaticamente sem depender de sessão ativa, com política configurável e observável.
- **FR-SDA-INFRA-BACKUP**: Estados, solicitações, concessões e auditorias DEVEM participar dos conjuntos coordenados de recuperação; restauração NÃO DEVE reativar aceite expirado nem desfazer revogação posterior conhecida.
- **FR-SDA-INFRA-CLOCK**: Prazos, autenticação recente e eventos DEVEM usar referência temporal confiável e falhar com segurança diante de divergência relevante.

### Key Entities

- **Directory User Summary**: visão global segura da identidade, estado, segurança e relações necessárias ao administrador, sem segredos.
- **Directory Account Summary**: visão global segura da conta, plano, tenant, estado e saúde resumida, sem conteúdo funcional.
- **Administrative Intervention**: intenção e resultado de bloquear, desbloquear, suspender, reativar ou invalidar acesso, com alvo, motivo, ator e estados.
- **Account Administration Recovery**: solicitação excepcional e temporária para restaurar a administração mínima de uma conta sem participante apto.
- **Recovery Acceptance**: confirmação de responsabilidade pelo usuário-alvo, vinculada à conta, solicitação, validade e finalidade.
- **Administrative Audit Event**: evidência global protegida de consulta, decisão, etapa, notificação e resultado desta feature.

## Success Criteria

### Measurable Outcomes

- **SC-SDA-001**: Pelo menos 95% das pesquisas administrativas válidas localizam o usuário ou a conta correta em até 10 segundos.
- **SC-SDA-002**: Em 100% dos testes, ator sem a chave específica não consulta nem intervém no diretório, mesmo identificado como administrador do sistema.
- **SC-SDA-003**: Nenhuma visão, auditoria ou notificação desta feature exibe senha, segredo, código, token, prova, chave privada ou conteúdo funcional de tenant.
- **SC-SDA-004**: Em 100% dos bloqueios testados, todas as sessões e novas operações do usuário são invalidadas sem alterar o estado de suas contas.
- **SC-SDA-005**: Em 100% dos desbloqueios, nenhuma sessão, prova, associação ou concessão anteriormente expirada ou revogada volta a ser válida.
- **SC-SDA-006**: Em 100% das suspensões, somente a conta escolhida perde operações comuns e seus dados permanecem preservados.
- **SC-SDA-007**: Em 100% das intervenções em conta, o operador global não recebe associação, contexto nem acesso ao tenant.
- **SC-SDA-008**: Em 100% das recuperações concluídas, a conta estava sem participante apto, o alvo aceitou, possuía 2FA compatível e recebeu somente as concessões mínimas.
- **SC-SDA-009**: Em 100% das recuperações expiradas, rejeitadas, substituídas ou concorrentes, nenhuma concessão indevida ou duplicada é criada.
- **SC-SDA-010**: Em 100% das intervenções críticas testadas, existe auditoria completa ou a operação não é declarada concluída.
- **SC-SDA-011**: Pelo menos 95% das invalidações de sessão, suspensões e revogações tornam-se efetivas para novas operações em até 5 segundos.
- **SC-SDA-012**: Em 100% das restaurações testadas, intervenções encerradas, aceites expirados e concessões revogadas não voltam a produzir acesso.
- **SC-SDA-013**: Todas as jornadas administrativas podem ser concluídas apenas por teclado e mantêm alvo, risco, estado e confirmação compreensíveis sem depender somente de cor.
- **SC-SDA-014**: Em 100% das recuperações concluídas, existe autorização de um administrador global com chave exclusiva, fator forte, justificativa e evidências, seguida do aceite do alvo e de auditoria completa, sem exigência de segundo aprovador.
- **SC-SDA-015**: Em 100% das recuperações destinadas a usuário que ainda não participava da conta, nenhuma associação ou concessão é criada antes do aceite e ambas passam a existir de forma consistente somente após a revalidação final.
- **SC-SDA-016**: Em 100% dos testes, a solicitação pode ser decidida antes de 7 dias e nenhuma etapa pendente produz efeito depois do prazo máximo configurado, sem reinício indevido do prazo.
- **SC-SDA-017**: Em 100% dos testes de administração de identidade, somente bloqueio, desbloqueio, invalidação de sessões e exigência de autorrecuperação estão disponíveis; nenhuma credencial, fator, e-mail, cancelamento ou exclusão pode ser executado pelo administrador global.
- **SC-SDA-018**: Em 100% dos testes isolados e concorrentes, nenhuma intervenção deixa a plataforma com zero administradores globais aptos e toda recusa preserva o estado anterior com auditoria.
