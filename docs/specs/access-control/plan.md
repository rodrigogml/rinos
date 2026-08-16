# Implementation Plan: Controle de Acesso por Grupos e Chaves

**Feature**: `access-control` | **Date**: 2026-08-15 | **Spec**: [spec.md](./spec.md)

## Summary

Implementar um núcleo único de autorização contextual, persistido no plano de controle global, que combina regras
diretas e de grupos, aplica precedência absoluta de bloqueios, integra direitos de plano e garantia de autenticação sem
misturar seus motivos e protege a continuidade administrativa por validação transacional. A interface Vaadin consumirá
somente a facade pública e reutilizará os padrões da RFW Platform.

## Technical Context

**Language/Version**: Java 25
**Primary Dependencies**: Spring Boot 4.0.7, Spring Security, Vaadin 25.0.2, RFW 2.0.0
**Storage**: MySQL 9, schema global `rinos_global`
**Testing**: JUnit 5, Mockito, Spring Boot Test e harness MySQL temporário já adotado pelo projeto
**Target Platform**: JAR executável em Linux atrás de proxy reverso; uma ou mais instâncias
**Project Type**: aplicação web modular server-side
**Performance Goals**: alvo de engenharia p95 até 100 ms para decisão com snapshot válido e até 250 ms para recarga,
sem SLA comercial nesta fase
**Constraints**: negação segura em falhas, contexto explícito, nenhuma dependência de Redis/mensageria, códigos
técnicos invisíveis ao usuário, sem alteração da RFW neste ciclo
**Scale/Scope**: catálogo inicial global e de tenant, grupos sem aninhamento, regras diretas e de grupo, interface
administrativa, explicação e bootstrap; sem curingas ou políticas arbitrárias

## Interaction Surface Architecture

**Surface Catalog**: [Interaction Surface Architecture](../../architecture/interaction-surfaces.md)
**Interface Design Applicability**: REQUIRED — a feature possui gestão e diagnóstico humanos.

| Surface ID | Feature Coverage | Technology Decision | Module/Repository | Notes |
|------------|------------------|---------------------|-------------------|-------|
| `SURF-WEB-RINOS` | FULL | Vaadin server-side com APIs públicas da RFW | `src/main/java/br/com/rinos/app/ui/module/access` | Contextos global e tenant têm entradas separadas; interface não exibe códigos |

## Constitution Check

| Princípio | Status | Notas |
|-----------|--------|-------|
| I. Isolamento Multi-Tenant Inviolável | PASS | Toda regra de tenant exige `idTenant`; consultas filtram o contexto antes de materializar dados |
| II. Autorização Explícita e Contextual | PASS | Negação padrão, permissão explícita e bloqueio prevalente são o algoritmo central |
| III. Integridade e Rastreabilidade | PASS | Mudança, histórico, auditoria, revisão e continuidade ficam na mesma transação |
| IV. Arquitetura Modular Baseada no RFW | PASS | UI compõe componentes públicos existentes; nenhuma alteração no submódulo |
| V. Qualidade Antes de Escopo | PASS | Backlog inclui matriz, isolamento, concorrência, acessibilidade e falha segura |

## Arquitetura de Componentes

O núcleo separa facade pública, decisão, resolução de regras, continuidade administrativa, auditoria e cache.

- `AccessKeyContributor`: descriptors imutáveis publicados por cada módulo.
- `AccessKeyRegistryService`: valida e registra o catálogo idempotente na inicialização.
- `AuthorizationFacade`: API pública para decidir, exigir e explicar autorização; a explicação recebe consulente e
  alvo separados e normaliza internamente a chave de visibilidade do mesmo contexto antes de resolver o alvo.
- `AuthorizationDecisionService`: orquestra gates e resultado por chave sem conhecer UI.
- `AccessRuleResolver`: carrega regras diretas e de grupos do contexto e aplica vigência.
- `AdministrativeContinuityService`: impede perda do último administrador apto.
- `AccessAdministrationService`: transações de grupos, associações e regras.
- `AccessAdministrationFacade`: fotografia pública limitada às seções individualmente autorizadas, pesquisa limitada
  de sujeitos e comandos com a revisão contextual observada, sem expor entity ou repository à UI.
- `RevisionedAccessSnapshotCache`: snapshot local limitado por sujeito e contexto, revisão e fronteira temporal.
- `WorkspaceAuthorizationContextAdapter`: lê da `UI`/área de trabalho exata apenas a referência de contexto escolhida,
  deriva o ator do `SecurityContext` e monta a requisição explícita sem confiar na rota ou na sessão compartilhada.
- `AccessAuditService`: mutações e negações sensíveis com correlação e minimização de dados.
- `SpringAuthorizationAdapter`: traduz operações Spring sem congelar authorities de tenant na sessão.
- `SystemOperationAuthorizer`: valida origem sistêmica tipada contra operação, contexto e chaves exatos e produz a
  fonte permissiva sistêmica auditável, sem consultar regras humanas.

## Algoritmo de Decisão

1. Validar o `AuthorizationRequest`.
2. Validar identidade e sessão; identidade bloqueada encerra a decisão.
3. Validar contexto global ou tenant, associação e conta operacionais.
4. Resolver descriptors; chave desconhecida, inativa ou de escopo incompatível nega.
5. Obter a revisão do contexto; falha de leitura nega.
6. Carregar ou reutilizar o snapshot do sujeito no contexto, correspondente à revisão e ainda anterior à próxima
   fronteira temporal conhecida.
7. Reaplicar no instante UTC corrente a vigência das fontes diretas e dos grupos ativos e separar permissões e
   bloqueios para cada chave.
8. Permitir a chave somente quando houver permissão e não houver bloqueio.
9. Avaliar direito de plano e garantia de autenticação como gates independentes.
10. Autorizar somente quando todas as chaves e gates permitirem.
11. Produzir decisão segura e registrar negação sensível conforme política.

## Transações, Auditoria e Cache

- Toda mutação usa controle otimista; mudança administrativa também bloqueia uma linha de guarda do contexto.
- A central envia a revisão da fotografia aberta. A facade bloqueia a guarda e rejeita `ACL_CONTEXT_REVISION_CONFLICT`
  antes de chamar a mutação quando qualquer regra, grupo ou associação mudou no intervalo.
- Mudanças que alcançam vários contextos bloqueiam primeiro o global e depois tenants por identificador crescente,
  antes das entidades afetadas, evitando inversão de locks entre ACL, membership e fatores fortes.
- A transação calcula o estado efetivo pós-mudança no instante atual e nas fronteiras futuras conhecidas de vigência,
  incluindo grupos, regras diretas, bloqueios, associações e 2FA compatível.
- Sem administrador mínimo apto, nada é persistido.
- Alteração aceita grava estado corrente, histórico, auditoria e incremento da revisão.
- Troca `PERMITIR`/`BLOQUEAR` atualiza a única regra corrente.
- Bootstrap global usa guarda e marcador único no contexto global.
- Eventos registram correlação, origem, contexto, alvo, chave, efeito, antes/depois, instante e resultado, sem segredos.
- Toda decisão confirma a revisão monotônica antes de usar snapshot; revisão antiga força recarga.
- A mesma operação composta pode reutilizar uma leitura de revisão e fotografia consistente; nova requisição, evento,
  tentativa ou início de job executa nova confirmação.
- O cache não armazena decisões finais nem ACL completa do tenant. Entradas usam identidade global ou associação de
  tenant como sujeito, possuem limite de peso/inatividade e expiram, no máximo, na próxima fronteira temporal conhecida.
- Plano, estado, garantia de autenticação e relógio são gates reaplicados e não são congelados no snapshot ACL.
- Após o commit, a instância mutante invalida o contexto localmente. Notificação remota futura pode antecipar a
  invalidação, mas revisão persistida continua sendo a autoridade contra perda, atraso ou reordenação do evento.
- Indisponibilidade da revisão, catálogo ou regras produz negação e sinal operacional.

## Registro Modular de Chaves

O catálogo inicial está em [contracts/access-key-catalog.md](./contracts/access-key-catalog.md). Cada módulo publica um
contributor. Na readiness global, o registry agrega descriptors, rejeita colisões, valida escopo, categoria, i18n,
ownership e referências, sincroniza inclusões compatíveis e exige mudança explícita para inativação.

## Project Structure

```text
docs/specs/access-control/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── interface-spec.md
├── tasks.md
├── contracts/
└── checklists/

src/main/java/br/com/rinos/app/
├── api/module/access/{facade,vo,dto}/
├── backend/module/access/{component,config,entity,repository,service}/
└── ui/module/access/{component,view}/

src/main/resources/
├── db/global/init/
├── db/global/update/
└── i18n/
```

**Structure Decision**: ACL é um módulo funcional próprio. Contratos públicos ficam em `api`; persistência e decisão
em `backend`; views e composição RFW em `ui`. Não haverá package genérico `common` ou `util`.

## Convenções de Borda

- UI → API: somente facade, DTO e VO; nunca entity ou repository.
- UI/workspace → autorização: identidade vem do `SecurityContext`; a `UI` exata fornece somente contexto e associação
  pretendidos, que são revalidados. `HttpSession`, `VaadinSession`, `RFWSessionState` e principal não guardam tenant
  ativo, regras, chaves ou decisão efetiva.
- RFW execution context → domínio: pode transportar uma projeção pequena e imutável já delimitada pela entrada, mas
  nunca substitui `AuthorizationRequest`, banco, cache ou validação do serviço.
- Módulo consumidor → autorização: operação tipada e descriptors do módulo, não string digitada na chamada.
- Autorização → planos: porta de leitura com gate tipado e motivo seguro.
- Autorização → autenticação: porta de leitura da garantia atual, sem acesso a segredo.
- Tenant → global: identificadores globais já validados; nenhuma consulta ACL abre schema do tenant.
- Trabalho assíncrono → autorização: reenvia ator, contexto e operação antes do primeiro efeito.
- Sistema autônomo → autorização: principal sistêmico tipado, finalidade limitada e auditoria obrigatória.

## Phases

### Phase 0 - Contratos e catálogo

Consolidar API pública, contributors, catálogo e motivos seguros.

### Phase 1 - Persistência global

Criar scripts globais init/update, entities, repositories, constraints, histórico, revisão e bootstrap.

### Phase 2 - Núcleo de decisão

Implementar gates, precedência, operações compostas, explicação, cache revisionado e adapters.

### Phase 3 - Administração e continuidade

Implementar grupos, associações, regras, baseline protegida e validação transacional.

### Phase 4 - Interface web

Implementar `interface-spec.md` usando RFW e sem exibir códigos.

### Phase 5 - Integração dos módulos

Migrar verificações em ondas: fundação do tenant, pessoas e financeiro.

### Phase 6 - Quality gate e operação

Validar segurança, isolamento, concorrência, performance, acessibilidade, documentação e deploy.

## Complexity Tracking

Nenhuma violação constitucional identificada. A persistência centralizada no global pertence ao plano de controle e
não cria referência do global para dados físicos de tenant.
