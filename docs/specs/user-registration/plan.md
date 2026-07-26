# Implementation Plan: Cadastro e Ciclo Inicial do Usuário

**Feature**: `user-registration` | **Date**: 2026-07-25 | **Spec**: [spec.md](./spec.md)

## Summary

Implementar a primeira identidade global do Rinos, independente de tenant, com cadastro local ou Google, consentimentos versionados, comprovação de e-mail, proteção contra automação, retomada, cancelamento e expiração. A abordagem usa facades transacionais, constraints de banco como autoridade final de unicidade, credenciais e tokens não recuperáveis, integrações externas isoladas por portas e a infraestrutura reutilizável do RFW para e-mail, verificação e atualização de banco.

Este plano não inclui autenticação de sessão, recuperação de acesso, conteúdo do Painel de Usuário, associação a contas ou permissões. O redirecionamento final aponta para a rota reservada do painel, cuja implementação completa pertence a `user-dashboard`.

## Technical Context

**Language/Version**: Java 25
**Primary Dependencies**: Spring Boot 4.0.1, Spring Security, Spring Data JPA, Spring Validation, Spring Mail, Vaadin 25.0.2 e RFW Platform 1.0.0, conforme o submódulo atual
**Storage**: MySQL 9, schema global do sistema
**Testing**: JUnit 5, Spring Boot Test, testes de integração com MySQL compatível e testes de UI Vaadin/Playwright na etapa de implementação
**Target Platform**: JAR executável em servidor Linux atrás de proxy reverso confiável
**Project Type**: aplicação web Spring Boot/Vaadin modular por funcionalidade
**Performance Goals**: envio inicial processado sem bloqueios desnecessários; 95% das instruções de comprovação aceitas pelo SMTP em até dois minutos após o commit; operações de validação externa com timeout explícito
**Constraints**: equipe inicial de uma pessoa; sem orçamento reservado; configuração exclusiva por origem; nenhuma variável de ambiente, propriedade JVM ou argumento de linha de comando pode sobrescrever configuração Rinos; nenhum segredo versionado; identidade global sem tenant
**Scale/Scope**: cadastro público exposto à internet, preparado para concorrência e múltiplas instâncias, sem API pública no primeiro incremento

## Interaction Surface Architecture

**Surface Catalog**: [Interaction Surface Architecture](../../architecture/interaction-surfaces.md)
**Interface Design Applicability**: **REQUIRED** — a feature cria jornadas públicas em web responsiva, com estados de validação, falhas externas, acessibilidade e retomada.

| Surface ID | Feature Coverage | Technology Decision | Module/Repository | Notes |
|------------|------------------|---------------------|-------------------|-------|
| `SURF-WEB-RINOS` | FULL | Java 25, Vaadin 25.0.2 e Spring Boot 4.0.1 | Repositório Rinos; módulo hospedeiro ainda não criado | Desktop, tablet e telefone; UI usa facades Java e i18n |

## Constitution Check

*GATE: aprovado antes do Phase 0 e revalidado após o Phase 1.*

| Princípio | Status | Notas |
|-----------|--------|-------|
| I. Isolamento Multi-Tenant Inviolável | PASS | A identidade e o cadastro são exclusivamente globais; não há leitura ou escrita de tenant. |
| II. Autorização Explícita e Contextual | PASS | A ativação concede somente acesso sistêmico mínimo ao próprio painel, sem papel ou permissão de conta. Rotas posteriores permanecem negadas por padrão. |
| III. Integridade e Rastreabilidade dos Dados | PASS | Unicidade, idempotência, histórico de estados, contratos externos e falhas estão definidos; migrations usarão o updater RFW. |
| IV. Arquitetura Modular Baseada no RFW | PASS | O Rinos hospedará `RFWAccessComponent` e implementará seus providers; Google, Turnstile, e-mail, verificação, i18n, tema e updater serão reutilizados. Lacunas genéricas seguem [rfw-gap-analysis.md](./rfw-gap-analysis.md). |
| V. Qualidade Antes de Escopo | PASS | O plano exige testes por camada, acessibilidade, documentação, falha fechada nos controles críticos e redução de escopo em vez de atalhos. |

## Architecture and Responsibility Boundaries

| Contexto | Responsabilidade |
|----------|------------------|
| UI de cadastro | A rota Rinos hospeda `RFWAccessComponent`; configuração, slots e renderers aprovados apresentam a jornada sem substituir sua máquina de estados. |
| API/facade Java | Implementar os providers do RFW e publicar casos de uso completos de início, retorno Google, reenvio, confirmação, cancelamento e consulta de estado seguro. |
| Backend `identity` | Normalização de e-mail, lifecycle global do usuário, consentimentos, credenciais, identidades externas e auditoria. |
| Backend `registration` | Orquestração do cadastro, idempotência, comprovações, limites por origem, expiração e integração com portas externas. |
| RFW Platform | `RFWAccessComponent`, providers, estados, Google, Turnstile, e-mail, challenges, i18n, tema, sessão e atualização de banco. |
| Provedores externos | Cloudflare valida presença humana; Google comprova identidade externa; HIBP informa comprometimento de senha; SMTP entrega a comprovação. |
| `user-authentication` | Login, sessão, recuperação, 2FA, passkeys e vínculo Google de usuário já ativo. A recuperação mínima de senha é dependência de release de `user-registration`. |
| `user-dashboard` | Conteúdo e operações do painel acessível depois da ativação. |

## RFW Compatibility Gate

O commit `fb59049ef916f0854b53159542b71591db24cb8f` fornece o add-in de acesso que deve ser hospedado pelo Rinos e
resolve todas as lacunas registradas em [rfw-gap-analysis.md](./rfw-gap-analysis.md): cadastro Google com aceites,
issuer tipado, Turnstile condicional, cancelamento, erros por campo, continuação de aceite durante a ativação,
encaminhamento direto à recuperação e preservação seletiva de e-mail e aceites.

O ciclo foi implementado, testado e documentado no showroom do RFW antes da atualização do ponteiro no Rinos. O gate
de compatibilidade está encerrado e a [Interface Design](./interface-spec.md) referencia os contratos finais.

## Transaction and Failure Strategy

1. A submissão local valida contrato, origem, limite, Turnstile e senha antes de iniciar escrita.
2. Uma transação cria ou reutiliza a identidade pendente, substitui a credencial local quando permitido, registra aceites, invalida comprovações anteriores e cria nova comprovação com token armazenado somente como hash.
3. O commit ocorre antes do envio SMTP. A falha de envio não reverte nem duplica o cadastro; a resposta orienta reenvio.
4. A ativação bloqueia e relê cadastro, usuário, comprovação e documentos vigentes na mesma transação. Repetição retorna o estado já alcançado sem recriar efeitos.
5. O fluxo Google valida integralmente a resposta antes de escrever. Ao reutilizar pendência, invalida credencial local e comprovações antes de gravar o vínculo e ativar.
6. Expiração e limpeza usam job diário idempotente, com lotes limitados e métricas; execução concorrente não pode excluir usuário ativo.

## Configuration Ownership

Todas as definições abaixo têm origem exclusiva `PROPERTY_FILE`, são lidas do `application.properties` explícito na raiz e documentadas no `application.properties.model`. Nenhuma será duplicada em banco.

| Grupo | Conteúdo |
|-------|----------|
| Cadastro | validade de 24 horas, retenção pendente de 15 dias, limite de três reenvios em 15 minutos e agenda diária de limpeza |
| Origem e proxy | proxies confiáveis, chave HMAC do IP, limiar/janela para exigir Turnstile e limite/janela absoluta para bloquear cadastro mesmo após Turnstile válido |
| Turnstile | site key pública, secret key, hostname/action esperados, endpoint e timeouts |
| Google OIDC | client ID, client secret quando aplicável, redirect URI, issuer/discovery permitido e timeouts |
| Senha comprometida | endpoint Pwned Passwords, user-agent, timeouts e política fail-closed |
| E-mail | SMTP e propriedades de template/remetente consumidas pelo RFW |

> [!IMPORTANT]
> Os valores funcionais já fixados pela spec não se tornam editáveis apenas por constarem como parâmetros operacionais. A implementação deve evitar duas fontes para a mesma definição e respeitar a política de configuração de `platform-configuration`.

## Validation Strategy

| Camada | Cobertura mínima |
|--------|------------------|
| Unitária | normalização; política de senha; transições; validade; limitação por origem; decisão Turnstile; cálculo de retenção; tradução de resultados externos |
| Persistência | unicidade concorrente de e-mail e `issuer + sub`; cascade/restrict; índices; bloqueio transacional; deleção segura de pendências |
| Integração | MySQL 9; SMTP RFW; Siteverify; Google OIDC; Pwned Passwords; timeouts e respostas inválidas, usando servidores simulados locais |
| Segurança | replay de tokens; token de outro cadastro; race de ativação; cabeçalhos de proxy forjados; enumeração além da mensagem explicitamente permitida; ausência de segredos em logs |
| UI | WCAG 2.2 AA; zero violações automatizadas críticas ou sérias; jornadas principais sem bloqueios por teclado e leitor de tela; mobile; desafio renovado sem perda de campos permitidos; mensagens e rotas de todos os estados |
| Usabilidade | mínimo de 10 participantes alheios ao desenvolvimento, sem orientação; pelo menos quatro jornadas em telefone e quatro em desktop; gates de envio e ativação satisfeitos por no mínimo 9 participantes |
| End-to-end | cadastro local, cadastro Google, retomada, cancelamento, expiração, duplicidade simultânea e isolamento de acesso após ativação |

## Requirement Traceability

| Requirement group | Design authority | Principal validation |
|-------------------|------------------|----------------------|
| `FR-USR-001` a `FR-USR-015` | identidade global, credencial, vínculo externo e eventos em [data-model.md](./data-model.md) | persistência, segurança e acesso pós-ativação |
| `FR-REG-001` a `FR-REG-011` | facade de início, normalização, constraints e política de senha | cenários 1 a 5 do [quickstart.md](./quickstart.md) |
| `FR-REG-012` a `FR-REG-020` | `Verification`, transação de ativação e contrato SMTP | cenários 1, 6 e 13 |
| `FR-REG-021` a `FR-REG-027` | retomada pela identidade pendente, cancelamento e job de limpeza | cenários 6, 7, 14 e 15 |
| `FR-REG-028` a `FR-REG-042` | política por origem, adapter Turnstile e proxy confiável | cenários 8 e 9 |
| `FR-REG-043` a `FR-REG-052` | Google Identity Services pelo RFW, continuação de cadastro externo e `ExternalIdentity` | cenários 10 a 12 |
| `SC-UR-001` a `SC-UR-013` | matriz de testes, métricas operacionais e [Interface Design](./interface-spec.md) | suíte end-to-end e checklist de qualidade |

## Project Structure

### Documentation (this feature)

```text
docs/specs/user-registration/
├── spec.md
├── plan.md
├── research.md
├── rfw-gap-analysis.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── external-services.md
├── checklists/
│   └── requirements.md
├── interface-spec.md
└── tasks.md
```

`interface-spec.md` foi criado e validado pela etapa 6; o quality gate e a decomposição executável estão registrados
em `checklists/requirements.md` e `tasks.md`.

### Source Code (repository root)

A estrutura de código da aplicação hospedeira Rinos ainda não existe. Os únicos paths técnicos reais disponíveis hoje são:

```text
modules/RFW.Platform/        # submódulo e dependência-base
docs/specs/user-registration/ # documentação desta feature
docs/architecture/           # decisões transversais de arquitetura
```

**Structure Decision**: a primeira etapa de implementação deverá criar o projeto Maven hospedeiro na raiz, sem mover nem copiar o RFW. O package base será `br.eng.rodrigogml.rinos`; os módulos funcionais deverão seguir as fronteiras `ui`, `api`, `backend` e `shared` definidas pelos agentes do projeto. A árvore detalhada só será materializada em `tasks.md` depois da Interface Design e do quality gate, evitando registrar como existente uma estrutura que ainda não foi criada.

## Convenções de Borda

| Camada | Case style | Validação | Fonte da verdade |
|--------|------------|-----------|------------------|
| Tabelas e colunas MySQL | tabelas com prefixo de módulo e nomes `camelCase`; constraints `snake_case` | scripts init/update e testes de schema | [data-model.md](./data-model.md) e migrations futuras |
| Entity JPA | `camelCase`, igual à coluna física | annotations explícitas e estratégia física padrão | entidades backend futuras |
| Facade, DTO e VO Java | classes `PascalCase`, campos `camelCase` | Bean Validation + regras em services | contratos Java futuros |
| UI Vaadin | rotas em lowercase e kebab-case; chaves i18n por módulo | router, binder e facade | futura `interface-spec.md` |
| Provedores externos | formato próprio do provedor | adaptador valida e converte para VO interno | [external-services.md](./contracts/external-services.md) |

**Mapper layer (DB <-> DTO/VO)**: backend do respectivo módulo; entities e repositories nunca atravessam a facade.

**Validação de schema**: contratos estruturais na entrada da facade e constraints no banco; respostas externas são validadas integralmente nos adapters antes de virarem VOs internos.

## Implementation Sequencing

1. ~~Resolver, autorizar, implementar e publicar as lacunas do RFW.~~ Concluído em
   `fb59049ef916f0854b53159542b71591db24cb8f`.
2. Criar o esqueleto Maven hospedeiro, configuração exclusiva e banco global compatível com RFW.
3. Implementar modelo global, migrations init/update e repositories.
4. Implementar providers RFW, facades e políticas de senha/origem.
5. Compor a rota Vaadin com `RFWAccessComponent` conforme `interface-spec.md`.
6. Implementar limpeza agendada e observabilidade.
7. Executar testes de segurança, integração, UI e end-to-end; validar build do RFW e do Rinos.

Essa sequência é arquitetural; a decomposição executável pertence a `tasks.md`.

## Complexity Tracking

Não há violação da Constitution que exija justificativa. O plano reutiliza o add-in de acesso do RFW e encaminha lacunas genéricas para a plataforma, mantendo políticas e persistência no Rinos.
