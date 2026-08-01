# Implementation Plan: Cadastro e Ciclo Inicial do Usuário

**Feature**: `user-registration` | **Date**: 2026-07-25 | **Spec**: [spec.md](./spec.md)

## Summary

Implementar a primeira identidade global do Rinos, independente de tenant, com cadastro local ou Google, consentimentos versionados, comprovação de e-mail, proteção contra automação, retomada, cancelamento e expiração. A abordagem usa facades transacionais, constraints de banco como autoridade final de unicidade, credenciais e tokens não recuperáveis, integrações externas isoladas por portas e a infraestrutura reutilizável do RFW para e-mail, verificação e atualização de banco.

Este plano não inclui autenticação geral de sessões, recuperação de acesso, conteúdo do Painel de Usuário, associação
a contas ou permissões. O cadastro publica somente a autenticação resultante de sua ativação pelo serviço de sessão
do RFW e redireciona para a rota global autenticada reservada `/user`. A classe de entrada permanece sem conteúdo,
dados ou operações até ser composta pela feature `user-dashboard`.

## Technical Context

**Language/Version**: Java 25
**Primary Dependencies**: Spring Boot 4.0.7, Spring Security, Spring Data JPA, Spring Validation, Spring Mail, Vaadin 25.0.2 e RFW 2.0.0, conforme o submódulo atual
**Storage**: MySQL 9, schema global do sistema
**Testing**: JUnit 5, Spring Boot Test, testes de integração com MySQL compatível e testes de UI Vaadin/Playwright na etapa de implementação
**Target Platform**: JAR executável em servidor Linux atrás de proxy reverso confiável
**Project Type**: aplicação web Spring Boot/Vaadin modular por funcionalidade
**Performance Goals**: em amostra nominal controlada de 100 cadastros, ao menos 95 instruções de comprovação aceitas pelo SMTP local em até dois minutos após o commit; um smoke test no SMTP real; operações de validação externa com timeout explícito; nenhum compromisso de throughput antes de existir infraestrutura de referência
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
| Backend `platform` | Lease global, heartbeat, fencing e elegibilidade compartilhada para tarefas de manutenção, conforme `platform-operations`. |
| Backend `registration` | Orquestração do cadastro, idempotência, comprovações, limites por origem, expiração e integração com portas externas. |
| RFW Platform | `RFWAccessComponent`, providers, estados, Google, Turnstile, e-mail, challenges, i18n, tema, sessão e atualização de banco. |
| Provedores externos | Cloudflare valida presença humana; Google comprova identidade externa; HIBP informa comprometimento de senha; SMTP entrega a comprovação. |
| `user-authentication` | Login, sessão, recuperação, 2FA, passkeys e vínculo Google de usuário já ativo. A recuperação mínima de senha é dependência de release de `user-registration`. |
| `user-dashboard` | Conteúdo e operações do painel acessível depois da ativação. |

Os documentos apresentados pela rota são consultados por uma facade pública do catálogo
global. Arquivos em `docs/legal/drafts/` são somente material de elaboração e não integram o
classpath nem qualquer fallback de runtime. Sem Termos de Uso e Política de Privacidade
vigentes, a composição mantém a rota acessível, desliga a capacidade de cadastro e informa a
indisponibilidade. A leitura de uma versão já publicada usa `/legal-document/{reference}` e o
renderer Markdown sanitizado do RFW depois da verificação do hash persistido.

A consulta segura do estado do cadastro não é uma operação genérica de pesquisa por e-mail,
prova ou identificador interno. Cada caso de uso público devolve seu próprio resultado tipado:
início, ativação, reenvio e cancelamento informam somente o estado público necessário, erros por
campo, espera aplicável e eventual continuação opaca. Essa abordagem mantém a UI desacoplada das
entities, evita uma superfície adicional de enumeração e permite que adapters do RFW traduzam os
resultados sem consultar repositories ou services.

## RFW Compatibility Gate

O commit `fb59049ef916f0854b53159542b71591db24cb8f` encerrou originalmente as lacunas registradas em
[rfw-gap-analysis.md](./rfw-gap-analysis.md): cadastro Google com aceites,
issuer tipado, Turnstile condicional, cancelamento, erros por campo, continuação de aceite durante a ativação,
encaminhamento direto à recuperação e preservação seletiva de e-mail e aceites.

O Rinos fixa para a implementação desta feature a revisão aprovada
`bb6328a99a38116d45d4fee417568e8ba911e322`, publicada como `br.eng.rodrigogml.rfw:rfw:2.0.0`, que incorpora esse
gate, consolida o antigo Kernel no artefato único e preserva a configuração pública do endpoint de
verificação do Turnstile, a origem validada pela hospedeira, resultados públicos distintos da verificação humana e a
atualização explícita de catálogos em `DataSource` distintos, validação obrigatória de `exp` e `iat` no Google e
timeouts explícitos para discovery OIDC e JWKS. A revisão também censura o segredo do Turnstile na representação
diagnóstica e adota as correções de segurança da pilha Spring e Jackson. O ciclo foi implementado, testado e documentado no
showroom do RFW antes da atualização do ponteiro no Rinos. A migração para 2.0 também tornou explícitas no POM da
hospedeira as dependências das capacidades consumidas e substituiu a exceção genérica legada de e-mail pela hierarquia
tipada de infraestrutura e integração. O gate de compatibilidade está encerrado e a
[Interface Design](./interface-spec.md) referencia os contratos finais.

O ponteiro corrente do submódulo está em `7d8ee5c`, revisão descendente da baseline aprovada. Os deltas posteriores
acrescentam o componente público `RFWPicker` e fazem a transição padrão da ativação para a solicitação de cancelamento
preservar somente o identificador atual, sem transportar a prova; não alteram as APIs públicas consumidas pela feature.

A revisão também sincroniza o showroom obrigatório com as coordenadas, dependências fornecidas pela hospedeira e o
procedimento de migração da RFW 2.0. Em relação à baseline anterior, ela remove o namespace legado
`br.eng.rodrigogml.rfw.platform.*`, adota propriedades `rfw.*`, move recursos públicos para `/rfw/` e substitui a
auto-configuração agregadora por configurações independentes de cada capacidade. O Rinos foi migrado junto com o
ponteiro para impedir uma combinação incompatível entre código, configuração e artefato.

Para `INT-WEB-REG-002`, essa baseline também define no renderer padrão o foco inicial da ativação, mantém
`autocomplete="one-time-code"` sem restringir a prova opaca a números, apresenta a expiração pelo locale de formato
e fuso da sessão, e acrescenta ao alerta o `retryAfter` arredondado para cima com singular ou plural localizado. O
Rinos fornece o `Instant` real emitido pelo domínio; o RFW permanece responsável pela apresentação.

Para `INT-WEB-REG-003`, a baseline direciona o foco inicial ao primeiro aceite obrigatório e usa a ação principal
quando não houver documento obrigatório. Uma tentativa de conclusão incompleta marca os aceites ausentes e devolve o
foco ao primeiro deles; rejeições remotas continuam submetidas à precedência geral do primeiro campo inválido.

Na implementação de `INT-WEB-REG-001`, os estados `initial` e `ready` pertencem ao renderer
`REGISTRATION`; `processing` usa o bloqueio e `aria-busy` do `RFWAccessComponent`; sucesso,
rejeição, limitação e indisponibilidade são outcomes tipados do provider; e a desconexão usa a
reconexão padrão do Vaadin sem presumir confirmação da operação. Mudança de versão legal não cria
estado obsoleto nessa tela: a pendência conserva a fotografia aceita e a ativação aplica o gate das
versões obrigatórias vigentes.

## Transaction and Failure Strategy

As implementações backend das facades delimitam o caso de uso público, mas não abrem a
transação diretamente. Cada comando persistente completo delega a um único método público de
service com `@Transactional`: criação, reenvio, ativação, conclusão de novos aceites, emissão da
prova de cancelamento ou confirmação do cancelamento. Essa fronteira interna permite que a facade
trate colisões e indisponibilidades depois da decisão transacional e garante que o callback SMTP
registrado durante a escrita execute somente depois do commit. UI, adapters e facades públicas não
acessam repositories nem controlam transações.

1. A submissão local valida contrato, origem, limite, Turnstile e senha antes de iniciar escrita.
2. Uma transação cria ou reutiliza a identidade pendente, substitui a credencial local quando permitido, registra exatamente as versões publicadas apresentadas e aceitas, invalida comprovações anteriores e cria nova comprovação com token armazenado somente como hash. Uma versão retirada depois de apresentada ainda pode originar a pendência; referências desconhecidas, futuras, duplicadas por finalidade ou sem os dois documentos-base são rejeitadas.
3. O commit ocorre antes do envio SMTP direto pelo RFW. A chamada usa timeout explícito e somente confirma envio depois da aceitação pelo SMTP. Falha ou interrupção não reverte nem duplica o cadastro, não dispara retentativa automática e orienta retomada e reenvio; nenhuma outbox, mensagem renderizada, URL secreta ou token recuperável é persistido no primeiro incremento.
4. A ativação bloqueia e relê cadastro, usuário, comprovação e documentos vigentes na mesma transação. Se faltarem
   versões legais atuais, a prova original permanece aberta e funciona como referência opaca da continuação; ela só
   é consumida ao registrar os aceites e ativar. Repetição antes ou depois da conclusão retorna o mesmo estágio
   lógico sem recriar efeitos. Se o fluxo local vencer uma corrida contra uma continuação Google ainda pendente, as
   provas e vínculos externos não ativados são removidos dentro da ativação local.
5. O fluxo Google valida integralmente a resposta no RFW antes de escrever. O Rinos reduz o resultado a
   `providerId`, `issuer`, `subject`, e-mail verificado e correlation ID; cria ou reutiliza uma pendência com vínculo
   externo `PENDING`; substitui qualquer candidata externa anterior desse usuário; e emite uma continuação opaca cujo
   token é persistido somente como hash. Ao concluir os aceites de uma pendência local reutilizada, revalida a única
   candidata, invalida credencial local e comprovações dentro da mesma transação, antes de ativar vínculo, cadastro e
   usuário. O adapter RFW só publica o principal autenticado depois que o commit retorna e não aceita replay da prova.
6. Expiração de cadastros, retenção de janelas de origem e remoção de tombstones terminais usam o mesmo catálogo diário
   coordenado, com tarefas idempotentes, lotes próprios e métricas separadas. O heartbeat adquire ou renova
   `global-maintenance`; antes de cada tarefa e lote, a sessão comprova lease global vigente, `epoch` atual e
   estabilização. Cada lote executa em uma transação com timeout padrão de cinco minutos, validado como inferior aos 10
   minutos de estabilização; dentro da transação, relê o estado de negócio antes da exclusão. Assim, um lote antigo
   termina ou é abortado antes de a nova coordenadora iniciar, sem sobreposição de escritas. Falha parcial é contida por
   tarefa e não impede as tarefas independentes seguintes.

## Configuration Ownership

Todas as definições abaixo têm origem exclusiva `PROPERTY_FILE`, são lidas do `application.properties` explícito na raiz e documentadas no `application.properties.model`. Nenhuma será duplicada em banco.

| Grupo | Conteúdo |
|-------|----------|
| Cadastro | validade de 24 horas, retenção pendente de 15 dias, limite de três reenvios em 15 minutos e agenda diária de limpeza |
| Coordenação de manutenção | `instanceId` obrigatório por instância, heartbeat padrão de 30 minutos, abandono após quatro horas, estabilização de 10 minutos e timeout transacional de lote padrão de cinco minutos, obrigatoriamente inferior à estabilização; todos com origem exclusiva `PROPERTY_FILE` |
| Origem e proxy | proxies confiáveis; limiar/janela para exigir Turnstile, com limiar padrão zero; limite absoluto configurável, com padrão de 20 novas pendências de cadastro local por origem em 24 horas, mesmo após Turnstile válido; e retenção do IP por até 30 dias depois do fim da janela |
| Turnstile | site key pública, secret key, hostname/action esperados, endpoint e timeouts |
| Google OIDC | client ID, client secret quando aplicável, redirect URI, issuer/discovery permitido e timeouts |
| Senha comprometida | endpoint Pwned Passwords, user-agent, timeouts e política fail-closed |
| Hash de senha | Argon2id com memória, iterações, paralelismo, salt e tamanho do hash; piso de 19.456 KiB, duas iterações, paralelismo um, salt de 16 bytes e hash de 32 bytes |
| E-mail | SMTP e propriedades de template/remetente consumidas pelo RFW |

> [!IMPORTANT]
> Os valores funcionais já fixados pela spec não se tornam editáveis apenas por constarem como parâmetros operacionais. A implementação deve evitar duas fontes para a mesma definição e respeitar a política de configuração de `platform-configuration`.

## Validation Strategy

| Camada | Cobertura mínima |
|--------|------------------|
| Unitária | normalização; política de senha; transições; validade; limitação por origem; decisão Turnstile; cálculo de retenção; tradução de resultados externos |
| Persistência | unicidade concorrente de e-mail e `issuer + sub`; cascade/restrict; índices; bloqueio transacional; deleção segura de pendências; normalização binária e retenção limitada da origem |
| Integração | MySQL 9; SMTP RFW; Siteverify; Google OIDC; Pwned Passwords; timeouts e respostas inválidas, usando servidores simulados locais; amostra nominal de 100 dispatches SMTP com limite antifraude elevado para ao menos 100 e verificação humana controlada |
| Segurança | replay de tokens; token de outro cadastro; race de ativação; cabeçalhos de proxy forjados; enumeração além da mensagem explicitamente permitida; ausência de segredos em logs; piso e calibração reproduzível do Argon2id no servidor-alvo |
| UI | WCAG 2.2 AA; zero violações automatizadas críticas ou sérias; jornadas principais sem bloqueios por teclado e leitor de tela; mobile; desafio renovado sem perda de campos permitidos; mensagens e rotas de todos os estados |
| Usabilidade | mínimo de 10 participantes alheios ao desenvolvimento, sem orientação; pelo menos quatro jornadas em telefone e quatro em desktop; gates de envio e ativação satisfeitos por no mínimo 9 participantes |
| End-to-end | cadastro local, cadastro Google, retomada, cancelamento, expiração, duplicidade simultânea e isolamento de acesso após ativação |

No cancelamento, `RFWRegistrationCancellationProvider` sempre abre a confirmação para uma solicitação
sintaticamente válida. A referência da challenge é aleatória e não identifica a prova persistida; somente a pendência
elegível recebe a prova por e-mail. A confirmação usa e-mail mais token para bloquear o cadastro, consumir
`REGISTRATION_CANCEL`, invalidar as provas concorrentes e apagar a raiz `User` por cascade antes de gravar o tombstone
sem PII. Essa assimetria interna não atravessa a resposta da solicitação e impede enumeração.

## Requirement Traceability

| Requirement group | Design authority | Principal validation |
|-------------------|------------------|----------------------|
| `FR-USR-001` a `FR-USR-015` | identidade global, credencial, vínculo externo e eventos em [data-model.md](./data-model.md) | persistência, segurança e acesso pós-ativação |
| `FR-REG-001` a `FR-REG-011` | facade de início, normalização, constraints e política de senha | cenários 1 a 5 do [quickstart.md](./quickstart.md) |
| `FR-REG-012` a `FR-REG-020` | `Verification`, transação de ativação e contrato SMTP | cenários 1, 6 e 13 |
| `FR-REG-021` a `FR-REG-027` | retomada pela identidade pendente, cancelamento e job de limpeza | cenários 6, 7, 14 e 15 |
| `FR-REG-028` a `FR-REG-042` | política por origem, adapter Turnstile e proxy confiável | cenários 8 e 9 |
| `FR-REG-043` a `FR-REG-052` | Google Identity Services pelo RFW, continuação de cadastro externo e `ExternalIdentity` | cenários 10 a 12 |
| `SC-UR-001` a `SC-UR-015` | matriz de testes, métricas operacionais e [Interface Design](./interface-spec.md) | suíte end-to-end, eleição concorrente, calibração Argon2id e checklist de qualidade |

## Project Structure

### Documentation (this feature)

```text
docs/specs/user-registration/
├── spec.md
├── plan.md
├── research.md
├── rfw-gap-analysis.md
├── data-model.md
├── operations.md
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

A fundação executável da aplicação hospedeira foi criada pela tarefa 1.1:

```text
pom.xml
src/
├── main/java/br/com/rinos/app/
│   ├── RinosApplication.java
│   ├── api/
│   ├── backend/
│   ├── config/
│   ├── shared/
│   └── ui/
└── test/java/br/com/rinos/app/
    └── RinosApplicationIT.java
modules/RFW.Platform/         # submódulo e dependência-base
src/main/resources/db/
├── global/
│   ├── init/
│   └── update/
└── tenant/
    ├── init/
    └── update/
```

**Structure Decision**: o projeto Maven hospedeiro permanece na raiz e consome o RFW sem mover nem copiar o
submódulo. O package base é `br.com.rinos.app`; os módulos funcionais serão materializados nas tarefas
correspondentes e deverão preservar as fronteiras `ui`, `api`, `backend` e `shared` definidas pelos agentes do projeto.
Os catálogos global e de tenant seguem a
[organização de scripts de banco de dados](../../architecture/database-scripts.md) e nunca são combinados.

## Convenções de Borda

| Camada | Case style | Validação | Fonte da verdade |
|--------|------------|-----------|------------------|
| Tabelas e colunas MySQL | tabelas com prefixo de módulo e nomes `camelCase`; constraints `snake_case` | scripts init/update e testes de schema | [data-model.md](./data-model.md) e migrations futuras |
| Entity JPA | `camelCase`, igual à coluna física | annotations explícitas e estratégia física padrão | entidades backend futuras |
| Facade, DTO e VO Java | classes `PascalCase`, campos `camelCase` | Bean Validation + regras em services | contratos Java futuros |
| UI Vaadin | rotas em lowercase e kebab-case; chaves i18n por módulo | router, binder e facade | [interface-spec.md](./interface-spec.md) |
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
8. No ambiente equivalente ao de produção, calibrar Argon2id, registrar a evidência no checklist operacional e somente então concluir o gate de liberação.

Essa sequência é arquitetural; a decomposição executável pertence a `tasks.md`.
A nomenclatura, as tags, os alertas iniciais e os limites de responsabilidade da etapa 6 estão em
[operations.md](./operations.md).

## Complexity Tracking

Não há violação da Constitution que exija justificativa. O plano reutiliza o add-in de acesso do RFW e encaminha lacunas genéricas para a plataforma, mantendo políticas e persistência no Rinos.
