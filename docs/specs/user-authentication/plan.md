# Implementation Plan: Autenticação e Recuperação do Usuário

**Feature**: `user-authentication` | **Date**: 2026-08-08 | **Spec**: [spec.md](./spec.md)

## Summary

Implementar autenticação global do usuário ativo por senha, passkey ou Google; segundo fator por TOTP, e-mail,
passkey verificada ou código de recuperação; sessões normais e persistentes; reautenticação; gestão dos métodos; e
recuperação de acesso. O desenho amplia o contexto global de identidade iniciado em `user-registration`, conserva
segredos e provas fora da UI serializável e usa o MySQL como autoridade comum de revogação, concorrência e uso único.

A interface continuará composta pelos componentes públicos do RFW. O `RFWAccessComponent` será a única orquestração
de login público e o `RFWSecuritySettingsComponent` será a base da gestão autenticada. As lacunas reutilizáveis
identificadas em [rfw-gap-analysis.md](./rfw-gap-analysis.md) devem ser resolvidas, testadas e documentadas no showroom
antes de os adapters do Rinos anunciarem as capacidades correspondentes.

O registro de sessão autenticada será persistido no schema global, mas a árvore de UI Vaadin continuará local à
instância e dependerá de afinidade de sessão no proxy quando houver mais de uma instância. Não será usado Spring
Session JDBC: revogação e estado da autenticação são globais, enquanto replicação transparente da sessão Vaadin não
faz parte desta feature.

## Technical Context

**Language/Version**: Java 25<br>
**Primary Dependencies**: Spring Boot 4.0.7, Spring Security 7.0.6 gerenciado pelo Boot, Vaadin 25.0.2, RFW 2.0.0 e
`spring-security-webauthn`<br>
**Storage**: MySQL 9, schema global `rinos_global`<br>
**Testing**: JUnit 5, Spring Boot Test, MySQL 9 descartável, servidores SMTP/OIDC locais e Playwright em navegador real<br>
**Target Platform**: JAR executável em servidor Linux, porta interna 7070, atrás de proxy reverso HTTPS<br>
**Project Type**: aplicação web Spring Boot/Vaadin modular por funcionalidade<br>
**Performance Goals**: atender `SC-AUTH-001` e `SC-AUTH-002`; atualização de atividade será limitada para não escrever
no banco a cada evento Vaadin; validações externas terão timeout explícito<br>
**Constraints**: identidade global sem tenant; negação por padrão; configuração exclusiva por origem; nenhum segredo
versionado; uma única superfície humana; sem orçamento para infraestrutura comercial de sessão distribuída<br>
**Scale/Scope**: uma ou mais instâncias com afinidade de sessão; revogação e provas consistentes entre instâncias;
sem API REST pública

## Interaction Surface Architecture

**Surface Catalog**: [Interaction Surface Architecture](../../architecture/interaction-surfaces.md)<br>
**Interface Design Applicability**: **REQUIRED** — a feature altera login, desafios, recuperação e configurações de
segurança em uma interface humana responsiva.

| Surface ID | Feature Coverage | Technology Decision | Module/Repository | Notes |
|------------|------------------|---------------------|-------------------|-------|
| `SURF-WEB-RINOS` | FULL | Vaadin server-side com componentes RFW | Rinos em `br.com.rinos.app`; RFW no submódulo | Login público em `/login`; gestão autenticada integrada ao futuro `/user` |

O documento de Interface Design deverá detalhar ao menos login por senha, passkey e Google; escolha e confirmação de
segundo fator; aceite legal pós-autenticação; recuperação; reautenticação; e as seções do componente de segurança.

## Constitution Check

*GATE: aprovado antes do Phase 0 e revalidado depois do Phase 1.*

| Princípio | Status | Notas |
|-----------|--------|-------|
| I. Isolamento Multi-Tenant Inviolável | PASS | Autenticação, métodos e sessões pertencem ao global; não há `tenantId`. A futura elevação administrativa recebe contexto explícito, mas não lê tenant nesta feature. |
| II. Autorização Explícita e Contextual | PASS | Autenticar comprova identidade e nível de garantia; não cria papel, grupo, chave ou permissão. Acesso administrativo continua dependente da feature de autorização. |
| III. Integridade e Rastreabilidade dos Dados | PASS | Consumo de provas, rotação, revogação e concorrência usam transações, constraints, locks e eventos append-only sanitizados. |
| IV. Arquitetura Modular Baseada no RFW | PASS condicionado | A composição usa exclusivamente os componentes e protocolos públicos do RFW. As lacunas listadas devem ser encerradas no RFW antes da respectiva capability ser ativada. |
| V. Qualidade Antes de Escopo | PASS | O plano inclui gates de segurança, integração, acessibilidade, concorrência e jornadas reais; capacidades sem provider completo permanecem ausentes. |

Não há violação constitucional autorizada. A condição do princípio IV é um gate de sequência, não uma exceção.

## Architecture and Responsibility Boundaries

| Contexto | Responsabilidade |
|----------|------------------|
| UI pública | `LoginView` hospeda `RFWAccessComponent`, recebe somente resultados públicos e não mantém senha, OTP, token ou segredo em campo serializável. |
| UI autenticada | `RFWSecuritySettingsComponent` é composto pelo `user-dashboard`; a view não acessa entities ou repositories. |
| API Rinos | Facades e DTOs/VOs Java representam login, desafios, sessão, métodos, recuperação e reautenticação sem expor credenciais persistidas. |
| Backend `identity` | Usuário global, credenciais, fatores, passkeys, vínculos Google, provas, sessões, lembranças e auditoria. |
| RFW | Máquina de estados, componentes, protocolos WebAuthn/Google/TOTP/OTP, publicação do `SecurityContext`, contratos de providers e integração Spring Security. |
| Spring Security | Cadeia HTTP, CSRF, proteção contra fixation, endpoints WebAuthn e contexto autenticado por requisição. |
| MySQL global | Autoridade de unicidade, revogação, validade, contadores, consumo único e coordenação cross-instance. |
| Provedores externos | Google comprova identidade federada; Cloudflare executa o desafio anti-automação; SMTP entrega OTP e notificações. |

### Fronteira entre autenticação e autorização

O principal autenticado contém apenas a identidade global e uma referência opaca da sessão. A fotografia de garantia
da sessão informa os métodos usados e o instante da última autenticação forte, mas não contém authorities de tenant.
Papéis de ator podem determinar que uma operação futura exija MFA, porém somente grupos e chaves concederão acesso.
Permissões globais também não são carregadas no login: operações globais e de tenant usam o mesmo contrato de decisão
em tempo de execução. Tenant selecionado pertence à `UI`/área de trabalho do Rinos e não integra principal,
`HttpSession`, `VaadinSession` ou `RFWSessionState` compartilhado.

### Fronteira entre sessão Vaadin e sessão de autenticação

- O `HttpSession`/`VaadinSession` permanece em memória na instância e usa cookie `Secure`, `HttpOnly` e `SameSite`
  apropriado, com renovação após autenticação.
- `identity_authSession` é o registro global da continuidade autenticada e pode ser revogado por qualquer instância.
- Um guard de autenticação valida estado do usuário, sessão global, expiração absoluta e inatividade antes de permitir
  uma requisição autenticada ou evento Vaadin protegido.
- O proxy usa afinidade quando houver múltiplas instâncias. Perda da instância pode exigir novo login; alta
  disponibilidade com replicação da árvore Vaadin é tema operacional futuro e não enfraquece a revogação global.
- A atualização de `lastActivityAt` é feita no máximo uma vez por intervalo configurado e nunca estende a duração
  absoluta.

### Fronteira de transação

Facades públicas não abrem transações nem acessam repositories. Cada operação persistente completa delega a um método
público de service transacional: autenticação do primeiro fator, emissão/consumo de desafio, criação/revogação de
sessão, alteração de método, vínculo externo, rotação de recuperação e redefinição de senha.

Operações externas que produzem mensagem executam após o commit. Falha SMTP deixa a prova válida somente quando o
contrato funcional permitir reenvio; a UI não afirma entrega. Assinaturas Google e WebAuthn são validadas antes de o
resultado virar VO de domínio. O lifecycle persiste uma sessão `PREPARED`, o RFW renova a sessão HTTP e salva o
contexto local e, somente depois, o backend consome o fluxo e publica a sessão global como `ACTIVE`. Falha intermediária
limpa o estado local e compensa a preparação ou publicação.

## Authentication Flow

1. O RFW coleta o método inicial e uma origem já validada pelo Rinos.
2. O backend aplica limite por identificador informado e IP, exige Turnstile quando a política determinar e produz a
   mesma resposta pública para usuário inexistente, senha incorreta ou estado não autorizado.
3. O primeiro fator válido cria uma `AuthenticationFlow` opaca, nunca uma sessão autenticada parcial.
4. A política calcula fatores permitidos e nível necessário. Passkey com user verification pode encerrar o fluxo;
   senha ou Google podem exigir TOTP, e-mail, passkey ou recuperação conforme contexto e configuração.
5. Antes de publicar a sessão, o backend revalida usuário ativo e versões legais obrigatórias. Aceite pendente abre
   continuação própria sem conservar credenciais na UI.
   A disponibilidade de senha, Google, passkey, TOTP, e-mail e recovery code é recomposta no banco; método
   comprovado revogado rejeita o fluxo e alternativa indisponível deixa de ser oferecida.
6. O lifecycle cria `AuthSession.PREPARED`; o RFW renova o identificador HTTP e salva o `SecurityContext` local.
7. A publicação global revalida as invariants e, numa transação, consome o fluxo, ativa a sessão e registra os
   eventos. Qualquer falha limpa o contexto local e chama a compensação idempotente.
8. Cada uso posterior passa pelo guard global. Revogação, bloqueio ou expiração remove o contexto local e encerra a
   UI protegida.

## Method-specific Decisions

### Senha

- Reutilizar `LocalCredential` e o `PasswordEncoder` Argon2id já calibrável.
- Comparar um hash sintético também quando a identidade não existir, reduzindo diferenças grosseiras de tempo.
- Atualizar o hash após login válido quando `upgradeEncoding` indicar parâmetros antigos, na mesma transação do evento
  de autenticação e sem invalidar a sessão recém-criada.
- Consultar Pwned Passwords na criação, alteração e redefinição, não em cada login. Uma credencial explicitamente
  marcada como comprometida não autentica até redefinição.

### Google

- Reutilizar a validação do RFW e localizar somente por `issuer + sub`.
- Coincidência de e-mail sem vínculo nunca autentica nem vincula automaticamente.
- Vínculo autenticado exige reautenticação recente e confirmação explícita; tokens Google não são persistidos.
- O método inicial `GOOGLE` fica registrado na sessão. Código enviado ao mesmo e-mail não eleva essa sessão para
  garantia administrativa.

### Passkeys

- Usar os endpoints e tipos oficiais de `spring-security-webauthn`, com repositories persistentes adaptados ao modelo
  global do Rinos.
- O `userHandle` WebAuthn é aleatório, estável e distinto do ID sequencial e do e-mail.
- Persistir chave pública, credential ID, contador, backup eligibility/state, transports, nome e datas; nunca chave
  privada ou dado biométrico.
- Exigir user verification para satisfazer 2FA. Anomalia de contador gera evento e decisão de risco, sem revogar
  automaticamente outras credenciais.
- O endpoint WebAuthn não pode publicar autenticação diretamente antes do gate de usuário, MFA e documentos legais;
  isso é uma lacuna obrigatória do RFW.

### TOTP, e-mail e recuperação

- TOTP usa o serviço RFW com seis dígitos, período de 30 segundos e janela `±1`. O último passo aceito é persistido
  atomicamente para impedir replay.
- O segredo TOTP é recuperável apenas pelo backend e será cifrado com AEAD e chave versionada de origem exclusiva no
  `application.properties`; todas as instâncias da mesma instalação recebem o mesmo keyring operacional.
- OTP de e-mail é curto, expira, possui limite de emissão e tentativa e é persistido apenas como MAC versionado. Uma
  nova emissão invalida a anterior; o valor não aparece em logs ou auditoria.
- Cada conjunto possui 10 códigos de recuperação. Somente hashes independentes são persistidos; consumir, regenerar,
  desativar 2FA ou recuperar fatores invalida atomicamente os códigos aplicáveis.
- O enrollment TOTP precisa apresentar QR/URI uma única vez e confirmar um código antes de ativar o fator.

## Session and Remember-me Strategy

Sessões normais têm duração absoluta de 12 horas e inatividade de 30 minutos. Sessões solicitadas com “lembrar-me”
têm duração absoluta de 30 dias e inatividade de sete dias. Os valores funcionais são fixados pela spec; propriedades
podem somente materializar a política aprovada e falham na inicialização quando incompatíveis.

Quando “lembrar-me” é solicitado, o navegador recebe um cookie de autenticação opaco com seletor aleatório e
verificador secreto. O banco armazena somente o SHA-256 de ambos. O verificador é rotacionado depois de uso válido; apresentação
de seletor conhecido com verificador inválido revoga a família por suspeita de roubo. A ausência de “lembrar-me” usa
somente o cookie de sessão local do Spring; a opção habilitada adiciona a credencial opaca com expiração limitada
pelo vencimento absoluto de 30 dias.

`identity_authSession` não armazena o cookie bruto nem o identificador `HttpSession`. A referência exibida na gestão é
outro valor opaco, sem utilidade para autenticar. Revogar uma sessão invalida também seus validadores persistentes.

## Legal Consent Gate

O gate consulta o mesmo `LegalDocumentFacade` e as evidências imutáveis já usadas no cadastro. Uma versão obrigatória
nova cria uma continuação temporária vinculada ao fluxo autenticado e aos documentos vigentes. O usuário ainda não
recebe sessão plenamente autenticada; após aceite válido, a mesma transação registra os consentimentos, consome a
continuação e libera a criação de sessão. Recusa não altera evidências anteriores e não concede acesso.

## Configuration Ownership

Todas as definições abaixo têm origem exclusiva `PROPERTY_FILE`, no arquivo raiz `application.properties`, com modelo
versionado em `application.properties.model`. Nenhuma será duplicada em tabela de configuração.

| Grupo | Conteúdo |
|-------|----------|
| Sessão | durações normal/persistente, inatividade, intervalo mínimo de atualização da atividade, cookie e reautenticação de 15 minutos |
| Abuso | três falhas em 15 minutos por e-mail informado ou IP, permanência de Turnstile por 15 minutos sem nova falha, limites progressivos e retenção curta |
| Notificações | cooldown de 24 horas para falhas repetidas e reconhecimento de navegador em sessões retidas nos 30 dias anteriores |
| MFA | validade e tentativas de desafios/OTP, parâmetros TOTP e quantidade fixa de 10 códigos de recuperação |
| Criptografia local | keyring versionado para AEAD de TOTP e MAC de OTP; chave ativa e chaves de leitura anteriores |
| Passkey | Propriedades exclusivas `rfw.authentication.passkey.*`; RP ID `app.rinos.com.br` em produção, `localhost` no desenvolvimento local, origins pertencentes ao RP e `user-verification=required` |
| Integrações | Google, Turnstile e SMTP já definidos pelos respectivos contratos RFW/Rinos |
| Retenção | 30 dias para sessões encerradas e janelas antifraude, 365 dias para eventos e retenções técnicas dos artefatos temporários |

Estados, contagens e preferências do usuário pertencem ao banco; não são configurações de instalação.

## Validation Strategy

| Camada | Cobertura mínima |
|--------|------------------|
| Unitária | política de fatores, equivalência de respostas, expiração, assurance, replay, rotação, último método e mapeamentos RFW |
| Persistência | constraints, locks, consumo atômico, revogação cross-instance, contador TOTP, sessões concorrentes e vínculo `issuer + sub` |
| Integração | MySQL 9, SMTP local, Google/JWKS local, Turnstile local, WebAuthn do Spring Security e relógio controlado |
| Segurança | fixation, enumeração, brute force, replay, CSRF, cookie, origem forjada, bypass do gate legal, remoção do último método e segredos em logs/estado Vaadin |
| UI | teclado, leitor de tela, reflow, foco, erros, cancelamento de desafios, QR com alternativa textual e recuperação |
| End-to-end | todos os métodos iniciais, combinações de MFA, gestão/revogação remota, login persistente, recuperação e usuário bloqueado durante sessão |

## Requirement Traceability

| Requirement group | Design authority | Principal validation |
|-------------------|------------------|----------------------|
| `FR-AUTH-001` a `FR-AUTH-014` | fluxo de autenticação, gate legal e `AuthSession` | quickstart 1, 2, 7 e 12 |
| `FR-AUTH-PWD-*` | `LocalCredential`, política/upgrade Argon2id | quickstart 1, 8 e 11 |
| `FR-AUTH-ABUSE-*` | `OriginWindow`, tentativas e Turnstile | quickstart 2 e 3 |
| `FR-AUTH-PK-*` | repositories WebAuthn e `PasskeyCredential` | quickstart 4 e 10 |
| `FR-AUTH-GGL-*` | `ExternalIdentity` e provider Google RFW | quickstart 5 e 10 |
| `FR-AUTH-MFA-*` | `AuthenticationFlow`, `TotpFactor`, `EmailOtp` e `RecoveryCode` | quickstart 6, 9 e 10 |
| `FR-AUTH-REC-*` | recuperação existente ampliada e provas de fatores | quickstart 8 e 9 |
| `FR-AUTH-SES-*` | `AuthSession`, guard e cookie opaco | quickstart 7 e 11 |
| `FR-AUTH-INFRA-*` | locks, keyring, manutenção e idempotência | quickstart 10 a 12 |

## Project Structure

### Documentation

```text
docs/specs/user-authentication/
├── spec.md
├── password-recovery-release-slice.md
├── plan.md
├── research.md
├── rfw-gap-analysis.md
├── data-model.md
├── quickstart.md
├── interface-spec.md
├── tasks.md
├── checklists/
│   └── requirements.md
└── contracts/
    ├── authentication-providers.md
    └── external-services.md
```

Interface Design, checklist e backlog foram produzidos; a análise cross-artifact precede a execução.

### Source Code

```text
src/main/java/br/com/rinos/app/
├── api/
│   ├── dto/
│   ├── facade/
│   └── vo/
├── backend/module/identity/
│   ├── entity/
│   ├── enums/
│   ├── repository/
│   ├── service/
│   └── vo/
├── config/
└── ui/
    ├── config/
    └── module/
        ├── identity/
        └── user/
```

**Structure Decision**: ampliar o módulo global `identity` existente em vez de criar outro agregado de usuário. Classes
continuam agrupadas por finalidade e usam subpacotes somente quando o volume justificar; nenhuma entity atravessa a
camada `api`.

## Boundary Conventions

| Borda | Convenção | Validação | Fonte da verdade |
|-------|----------|-----------|------------------|
| MySQL | tabelas/colunas `camelCase`, constraints `snake_case`, prefixos `identity_`/`security_` | init/update e testes de schema | [data-model.md](./data-model.md) |
| Java | classes `PascalCase`, campos `camelCase`, instantes `Instant`, referências externas opacas | Bean Validation e services | facades/DTOs/VOs |
| RFW | enums e outcomes públicos convertidos explicitamente por adapters | testes de contrato | [authentication-providers.md](./contracts/authentication-providers.md) |
| WebAuthn/Google | formato do protocolo não atravessa o domínio sem validação | adapter técnico | [external-services.md](./contracts/external-services.md) |
| UI | rotas lowercase/kebab-case e textos por i18n | router, componentes e testes E2E | [interface-spec.md](./interface-spec.md) |

## Implementation Sequencing

1. Encerrar e publicar as lacunas genéricas do RFW, cada uma com teste e documentação multilíngue no showroom.
2. Criar migrations e persistência global para sessões, fluxos, fatores, passkeys e códigos.
3. Implementar guard de sessão, cookies opacos, revogação e invalidação por estado do usuário.
4. Implementar senha e política antifraude, reutilizando origem, Turnstile e credencial existentes.
5. Implementar desafios MFA, TOTP, e-mail e recuperação, incluindo keyring e manutenção.
6. Integrar Google, passkeys e gate legal à conclusão única de autenticação.
7. Implementar gestão autenticada e reautenticação sobre o componente RFW.
8. Executar testes por camada e gates operacionais previstos no backlog e nos artefatos aprovados.

Essa sequência é arquitetural e não substitui `tasks.md`.

## Complexity Tracking

| Decisão | Por que a complexidade é necessária | Alternativa rejeitada |
|---------|-------------------------------------|-----------------------|
| Registro global separado da sessão Vaadin | Revogação imediata entre instâncias sem serializar UI | Spring Session JDBC é incompatível com o estado Vaadin; sessão apenas em memória não atende revogação global |
| Keyring para TOTP/OTP | TOTP precisa ser recuperável e OTP curto resiste mal a ataque offline sem segredo do servidor | Texto puro ou hash não chaveado |
| `AuthenticationFlow` persistente | Impede sessão parcial e replay entre instâncias | Estado apenas no componente Vaadin |
