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
**Performance Goals**: envio inicial processado sem bloqueios desnecessários; 95% dos e-mails aceitos pelo transporte entregues em até dois minutos; operações de validação externa com timeout explícito  
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
| IV. Arquitetura Modular Baseada no RFW | PASS | O Rinos reutiliza e-mail, contratos de verificação e updater do RFW; o cliente Turnstile genérico será acrescentado ao RFW, sem levar regra de cadastro ao framework. |
| V. Qualidade Antes de Escopo | PASS | O plano exige testes por camada, acessibilidade, documentação, falha fechada nos controles críticos e redução de escopo em vez de atalhos. |

## Architecture and Responsibility Boundaries

| Contexto | Responsabilidade |
|----------|------------------|
| UI de cadastro | Coletar dados, apresentar consentimentos, preservar entradas não secretas em renovação do desafio e traduzir resultados da facade em feedback acessível. |
| API/facade Java | Publicar casos de uso completos de início, retorno Google, reenvio, confirmação, cancelamento e consulta de estado seguro. |
| Backend `identity` | Normalização de e-mail, lifecycle global do usuário, consentimentos, credenciais, identidades externas e auditoria. |
| Backend `registration` | Orquestração do cadastro, idempotência, comprovações, limites por origem, expiração e integração com portas externas. |
| RFW Platform | Entrega de e-mail, contratos técnicos de challenge, atualização de banco e cliente reutilizável do Turnstile. |
| Provedores externos | Cloudflare valida presença humana; Google comprova identidade externa; HIBP informa comprometimento de senha; SMTP entrega a comprovação. |
| `user-authentication` | Login, sessão, recuperação, 2FA, passkeys e vínculo Google de usuário já ativo. |
| `user-dashboard` | Conteúdo e operações do painel acessível depois da ativação. |

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
| Origem e proxy | proxies confiáveis, chave HMAC do IP, limiar/janela para exigir Turnstile e limite/janela para bloquear cadastro |
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
| UI | teclado; foco; leitores de tela; mobile; desafio renovado sem perda de campos permitidos; mensagens e rotas de todos os estados |
| End-to-end | cadastro local, cadastro Google, retomada, cancelamento, expiração, duplicidade simultânea e isolamento de acesso após ativação |

## Requirement Traceability

| Requirement group | Design authority | Principal validation |
|-------------------|------------------|----------------------|
| `FR-USR-001` a `FR-USR-015` | identidade global, credencial, vínculo externo e eventos em [data-model.md](./data-model.md) | persistência, segurança e acesso pós-ativação |
| `FR-REG-001` a `FR-REG-011` | facade de início, normalização, constraints e política de senha | cenários 1 a 5 do [quickstart.md](./quickstart.md) |
| `FR-REG-012` a `FR-REG-020` | `Verification`, transação de ativação e contrato SMTP | cenários 1, 6 e 13 |
| `FR-REG-021` a `FR-REG-027` | retomada pela identidade pendente, cancelamento e job de limpeza | cenários 6, 7, 14 e 15 |
| `FR-REG-028` a `FR-REG-042` | política por origem, adapter Turnstile e proxy confiável | cenários 8 e 9 |
| `FR-REG-043` a `FR-REG-052` | OIDC Google, `ExternalAuthAttempt` e `ExternalIdentity` | cenários 10 a 12 |
| `SC-UR-001` a `SC-UR-013` | matriz de testes, métricas operacionais e futura Interface Design | suíte end-to-end e checklist de qualidade |

## Project Structure

### Documentation (this feature)

```text
docs/specs/user-registration/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── external-services.md
└── interface-spec.md
```

`interface-spec.md` será criado pela etapa 6 e não existe neste momento.

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

1. Criar o esqueleto Maven hospedeiro, configuração exclusiva e banco global compatível com RFW.
2. Evoluir o RFW com o cliente Turnstile genérico, testes e documentação próprios.
3. Implementar modelo global, migrations init/update e repositories.
4. Implementar portas externas, adapters e políticas de senha/origem.
5. Implementar facades transacionais do cadastro e limpeza agendada.
6. Implementar as views Vaadin conforme `interface-spec.md`.
7. Executar testes de segurança, integração, UI e end-to-end; validar build do RFW e do Rinos.

Essa sequência é arquitetural; a decomposição executável pertence a `tasks.md`.

## Complexity Tracking

Não há violação da Constitution que exija justificativa. A separação entre adaptação técnica do Turnstile no RFW e política funcional no Rinos evita duplicação sem transformar o framework em módulo de negócio.
