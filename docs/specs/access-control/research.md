# Research: Controle de Acesso por Grupos e Chaves

Documento produzido no Phase 0 do planejamento. As decisões abaixo resolvem as fronteiras técnicas antes do desenho
do modelo e não autorizam implementação neste ciclo.

## Decision 1: Núcleo de autorização e Spring Security

**Decision**: criar um serviço de domínio único para decisões (`AuthorizationFacade` sobre um
`AuthorizationDecisionService`) e publicar um adapter para Spring Security. Serviços de aplicação chamam a facade ou
usam uma anotação/interceptor que converte a operação em `AuthorizationRequest`; a configuração HTTP/Vaadin continua
responsável apenas por autenticação, rota pública e entrada segura.

**Rationale**: a decisão precisa ser igual em UI, facade, serviço e trabalho assíncrono. Autorizar apenas na rota ou em
`SecurityFilterChain` não protege chamadas internas nem composições entre módulos. O adapter Spring integra o contrato
sem acoplar entidades ou repositories à camada de apresentação.

**Alternatives considered**: authorities estáticas no `Authentication` foram rejeitadas porque ficam obsoletas durante
a sessão, não representam tenant, vigência e bloqueios e não explicam direitos de plano. Verificações manuais dispersas
foram rejeitadas por divergirem entre módulos.

## Decision 2: Fronteira com Vaadin e RFW Platform

**Decision**: a UI Vaadin consulta DTOs/VOs da facade e usa a RFW como primeira fonte de componentes. Listas usam
`Grid`/`TreeGrid` compostas pelos padrões e `UIFactory`; pesquisa usa o contrato de filtering da RFW; seleção contextual
usa `RFWPicker`; confirmação usa botões padronizados e `Dialog`; feedback transitório usa toast e impedimentos
persistentes usam banner. O tenant selecionado pertence à `UI`/área de trabalho exata do Rinos, nunca ao
`HttpSession`, `VaadinSession`, `SecurityContext` ou `RFWSessionState` compartilhado. Um adapter de entrada pode projetar
essa referência mínima em uma fotografia imutável de execução da RFW, mas o serviço recebe contexto explícito e o
revalida; a fotografia não armazena regras nem decisões. Nenhuma alteração na RFW é necessária para especificar a
primeira versão.

**Rationale**: o showroom já fornece filtragem, seleção, botões, banners, toast, estados assíncronos, contexto de
execução e padrões de acessibilidade suficientes para compor a gestão. O próprio contrato RFW define o contexto como
fotografia, não como sessão, cache ou fonte de autorização. Como o estado padrão da RFW é da `VaadinSession`, o estado
por aba precisa permanecer sob responsabilidade da feature `tenant-context-isolation`. A matriz e a explicação são
regras de negócio do Rinos; criar um componente genérico no submódulo antes de validar o uso real ampliaria a API
pública prematuramente.

**Alternatives considered**: duplicar componentes ou CSS estrutural foi rejeitado pelas regras do projeto. Evoluir o
RFW agora foi adiado; se a implementação comprovar um padrão reutilizável, uma proposta separada deve descrever API,
compatibilidade, impacto, testes e atualização obrigatória do showroom.

**Revisão da implementação em 2026-08-16**: a fase 5.3 confirmou que os contratos públicos
`RFWReauthenticationChallengeProvider` e `RFWPasskeyComponent`, combinados com `Dialog`, botões, banner e toast da RFW,
cobrem a confirmação forte por TOTP/passkey. A orquestração e a prévia pertencem ao domínio do Rinos e permanecem em
seu módulo. Não foi encontrada lacuna que justifique alterar o submódulo; nenhuma API, CSS ou componente RFW foi
copiado ou modificado.

## Decision 3: Local da persistência multi-tenant

**Decision**: catálogo, categorias, grupos globais e de tenant, associações, regras, baselines protegidas, revisões de
cache, bootstrap e auditoria administrativa de acesso ficam no schema global `rinos_global`. Registros de tenant levam
`idTenant` obrigatório e são sempre consultados por ele. Nenhuma tabela global referencia tabela física de schema de
tenant.

**Rationale**: autorização é parte do plano de controle e precisa funcionar antes de abrir a conexão do tenant,
durante provisionamento, bloqueio ou migração e na seleção de contexto. O global já conhece identidade, registro do
tenant e associação do usuário; guardar ACL no tenant exigiria trocar datasource antes de saber se a troca é
autorizada e impediria administração segura quando o schema estivesse indisponível.

**Alternatives considered**: duplicar ACL em cada schema de tenant foi rejeitado por complicar bootstrap, recuperação,
consulta global e consistência entre instâncias. Copiar regras entre global e tenant foi rejeitado por criar duas
fontes de verdade.

## Decision 4: Sujeitos diretos e grupos

**Decision**: regra direta global aponta para `identity_user`; regra direta de tenant aponta para a associação global
do usuário ao tenant, nunca diretamente para o usuário. Associação de grupo segue o mesmo sujeito. Grupos não se
aninham e regras de grupo e diretas compartilham o mesmo objeto semântico `AccessRuleEffect`.

**Rationale**: a associação representa o ciclo de vida contextual. Suspender ou encerrar a participação elimina todos
os efeitos daquele tenant sem tocar os demais. Um único modelo de regra simplifica precedência e explicação.

**Alternatives considered**: regra tenant por usuário foi rejeitada porque sobreviveria ambiguamente a convites,
reativações e novas associações. Tabelas separadas de permissão e bloqueio foram rejeitadas por permitirem duas regras
correntes contraditórias na mesma origem.

## Decision 5: Algoritmo determinístico

**Decision**: para cada chave obrigatória, carregar regras correntes vigentes da origem direta e dos grupos ativos. A
chave é permitida quando existe `PERMITIR` e não existe `BLOQUEAR`; qualquer bloqueio vence. A operação usa semântica
`TODAS`. Antes das chaves são avaliados identidade, contexto, associação e conta; depois são avaliados direito de plano
e garantia de autenticação conforme o contrato da operação. A decisão preserva motivos separados.

**Rationale**: a ordem é estável, independe da quantidade ou sequência de grupos e permite explicar cada gate sem
converter falta de plano ou identidade bloqueada em bloqueio de chave.

**Alternatives considered**: prioridade numérica de grupo, “última regra vence”, curingas e expressões booleanas foram
rejeitados por dificultarem auditoria, continuidade administrativa e previsão pelo usuário.

## Decision 6: Vigência e relógio

**Decision**: início é inclusivo, término é exclusivo e ambos usam `Instant` UTC. A regra não precisa ser alterada por
agendador para expirar: toda decisão aplica a vigência corrente. Rotina de manutenção pode apenas compactar índices ou
marcar estado derivado, sem ser fonte da segurança.

**Rationale**: autorização não pode depender da pontualidade de um scheduler ou da instância eleita para manutenção.

**Alternatives considered**: desativação por tarefa periódica foi rejeitada como condição autorizativa porque cria uma
janela após o vencimento.

## Decision 7: Concorrência e continuidade administrativa

**Decision**: mutações de grupo, regra, associação, fator forte ou estado que afetem administração travam uma linha de
guarda do contexto (`SELECT ... FOR UPDATE`) e aplicam a alteração proposta em memória. A continuidade é recalculada
no instante corrente e em cada fronteira futura conhecida de vigência das regras, associações e fatores envolvidos;
em cada intervalo resultante deve restar pelo menos um administrador apto. O commit inclui mudança, histórico,
auditoria e incremento da revisão do contexto.

Quando uma única mutação afeta vários contextos — por exemplo, remoção de fator forte de uma identidade com
participação em vários tenants — a ordem canônica é a guarda global, seguida das guardas de tenant em `tenantId`
crescente, e somente depois os locks da identidade, fator ou membership. Todos os contextos são validados antes de
qualquer incremento de revisão; uma negação reverte a transação inteira.

**Rationale**: validar antes sem serialização permite que duas transações removam administradores diferentes e ambas
observem continuidade inexistente após os commits. Validar somente o instante atual permitiria agendar uma expiração
que eliminasse toda a administração sem nova mutação.

**Alternatives considered**: validação eventual, lock apenas em Java e compensação posterior foram rejeitados porque
podem deixar o sistema sem administrador e não funcionam entre instâncias.

## Decision 8: Cache e múltiplas instâncias

**Decision**: usar cache local, limitado por peso e inatividade, somente para snapshots imutáveis por sujeito e
contexto. A chave lógica é `(GLOBAL, identityId)` para decisão humana global e
`(TENANT, tenantId, membershipId)` para decisão humana de tenant; ela nunca representa todas as regras do tenant nem é
armazenada na sessão. O valor conserva as fontes diretas e de grupos necessárias à resolução, a revisão com que foi
montado e a próxima fronteira temporal conhecida. A vigência é reaplicada no instante UTC de toda decisão e o snapshot
não pode sobreviver à sua próxima fronteira temporal.

Cada operação independente consulta a revisão monotônica do contexto no global antes de usar o snapshot; chamadas
internas pertencentes à mesma operação composta podem compartilhar uma única leitura e fotografia consistente, mas
não entre requisições, eventos ou tentativas distintas. Snapshot com revisão diferente é descartado e recarregado. A
mutação incrementa a revisão na mesma transação. Falha ao obter revisão, catálogo ou regras produz negação. A primeira
implementação não depende de Redis nem de mensageria.

**Rationale**: uma leitura pequena por operação garante que todas as instâncias observem revogação confirmada antes de
novo efeito, enquanto snapshots do conjunto de sujeitos recentemente ativos reduzem joins repetitivos e limitam a
memória. Cachear decisão final ou ACL completa do tenant duplicaria dados e poderia congelar plano, garantia, estado ou
tempo. A fronteira temporal cobre início e expiração sem depender de mutação ou scheduler.

Após o commit, a instância mutante invalida localmente o contexto. Uma notificação idempotente
`AuthorizationContextChanged(scope, tenantId?, newRevision)` poderá antecipar a remoção nas demais instâncias; evento
perdido, atrasado ou fora de ordem não muda a correção, porque a revisão do MySQL permanece a autoridade. Invalidação
somente por usuário não é suficiente para alteração de grupo; a unidade segura inicial é o contexto global ou o tenant.

**Alternatives considered**: carregar chaves no login, no principal ou na seleção do tenant foi rejeitado por ficar
obsoleto, duplicar dados entre áreas e compartilhar incorretamente o tenant entre abas. TTL isolado foi rejeitado
porque amplia acesso até expirar. Snapshot com todas as regras do tenant foi rejeitado por memória e exposição
desnecessárias. Sem cache é funcional e permanece fallback seguro e estratégia inicial de diagnóstico. Cache
distribuído adicionaria infraestrutura não disponível no projeto e, mesmo no futuro, não substituiria a revisão
autoritativa.

## Decision 9: Registro modular do catálogo

**Decision**: cada módulo publica um `AccessKeyContributor` com descriptors imutáveis. Na inicialização global, um
registrador idempotente valida código, semântica, escopo, categoria, textos obrigatórios e módulo proprietário contra o
catálogo persistido. Inclusão compatível é permitida; colisão ou alteração incompatível impede readiness. Chaves
retiradas são desativadas por atualização explícita e nunca reutilizadas.

**Rationale**: o catálogo precisa acompanhar código e requisitos, mas permanecer consultável, localizável e auditável
no banco. Falhar cedo evita executar operação cuja chave não possui contrato conhecido.

**Alternatives considered**: seed SQL como única fonte foi rejeitado porque módulos poderiam declarar operações sem
verificação de runtime. Descoberta por strings em anotações foi rejeitada por perder descrições, ownership e
rastreabilidade.

## Decision 10: Operações autônomas e trabalhos assíncronos

**Decision**: trabalho originado por usuário conserva referência ao ator, contexto, operação e chaves requeridas, mas
reautoriza imediatamente antes de iniciar. Operação autônoma usa principal e origem sistêmicos tipados, registrados
com a combinação exata de operação, escopos e chaves. A origem ativa funciona como fonte permissiva sistêmica e é
auditada; não recebe regra humana nem associação a grupo.

**Rationale**: filas não podem congelar permissões antigas e o sistema não deve criar “usuários técnicos” com acesso
amplo para executar manutenção.

**Alternatives considered**: snapshot de autorização na aceitação foi rejeitado por ignorar revogação. Usuário de
serviço em grupo humano foi rejeitado por dificultar distinguir ação autônoma de ação delegada.
