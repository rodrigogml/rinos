# Requirements Checklist: Cadastro e Ciclo Inicial do Usuário

**Purpose**: Validar clareza, completude, consistência, mensurabilidade e rastreabilidade dos requisitos antes da criação do backlog.
**Created**: 2026-07-26
**Feature**: [Feature Specification](../spec.md)
**Depth**: Standard
**Primary risk focus**: segurança do ciclo público e consistência entre requisitos, contratos e interface

## Escopo e completude

- [x] CHK001 - O limite da feature separa cadastro, autenticação, Painel de Usuário e associações a contas sem atribuir responsabilidades sobrepostas? [Completude, Spec §Escopo; Plan §Architecture and Responsibility Boundaries] {auto}
- [x] CHK002 - Os três percursos funcionais — criar, retomar e cancelar cadastro — possuem objetivo, prioridade, teste independente e cenários de aceite? [Completude, Spec §User Scenarios & Testing] {auto}
- [x] CHK003 - Os requisitos cobrem cadastro local e Google, comprovação, reenvio, ativação, expiração, cancelamento, concorrência e falhas externas? [Cobertura, Spec §FR-REG-001–052; Spec §Edge Cases] {auto}
- [x] CHK004 - Os dados pessoais permitidos e proibidos no cadastro inicial estão delimitados por finalidade? [Completude, Spec §FR-USR-003 e FR-USR-010; Interface §INT-WEB-REG-001] {auto}
- [x] CHK005 - O estado inicial do usuário ativo, sem conta ou permissões implícitas, está definido de forma consistente? [Consistência, Spec §FR-USR-008 e FR-REG-020; Interface §Navigation and Parity] {auto}

## Clareza e mensurabilidade

- [x] CHK006 - A política de senha possui limites, classes exigidas, tratamento de comprometimento, colagem, gerenciadores e ausência de expiração arbitrária explicitamente definidos? [Clareza, Spec §FR-REG-004; Research §Decision 2] {auto}
- [x] CHK007 - Validades, retenções, janelas e limites funcionais possuem números e unidades verificáveis? [Mensurabilidade, Spec §FR-REG-013, FR-REG-015, FR-REG-023–024 e FR-REG-037–041] {auto}
- [x] CHK008 - A população, o tamanho mínimo da amostra e o método de medição dos percentuais de usabilidade e ativação na primeira tentativa estão especificados? [Mensurabilidade, Spec §SC-UR-001 e SC-UR-003; Plan §Validation Strategy] {auto}
- [x] CHK009 - O ponto inicial/final, a amostra nominal de 100 operações, o mínimo de 95 sucessos, o isolamento dos controles antifraude, a fonte controlada e o smoke test real que comprovam o SLO SMTP estão definidos sem prometer throughput? [Mensurabilidade, Spec §SC-UR-002; Contracts §SMTP por RFW; Quickstart §Scenario 20] {auto}
- [x] CHK010 - Os critérios de unicidade, replay, isolamento inicial, Turnstile e Google usam resultados objetivos de 100% nos cenários críticos? [Mensurabilidade, Spec §SC-UR-004–006 e SC-UR-008–013] {auto}
- [x] CHK011 - O padrão de acessibilidade adotado, seu nível de conformidade e a definição de “bloqueio crítico” estão explicitados para tornar o gate reproduzível? [Mensurabilidade, Spec §SC-UR-007; Constitution §Critérios de Qualidade e Entrega; Interface §Shared Accessibility and Input] {auto}

## Consistência entre artefatos

- [x] CHK012 - A identidade global e a ausência de tenant neste domínio são consistentes entre spec, modelo e plano? [Consistência, Spec §FR-USR-001 e FR-USR-009; Data Model §Entity: User; Plan §Architecture and Responsibility Boundaries] {auto}
- [x] CHK013 - A normalização e unicidade do e-mail são coerentes entre requisito, constraints e tratamento de concorrência? [Consistência, Spec §FR-USR-004–005 e FR-REG-009; Data Model §User Constraints and indexes; Plan §Transaction and Failure Strategy] {auto}
- [x] CHK014 - A imutabilidade do e-mail depois do aceite do cadastro e a necessidade de reiniciar com endereço correto estão alinhadas nos fluxos? [Consistência, Spec §FR-REG-033; Spec §Clarifications 2026-07-17; Interface §INT-WEB-REG-002] {auto}
- [x] CHK015 - A alteração de documentos legais durante um processo pendente possui requisito, continuação tipada, estado de interface e contrato RFW correspondentes? [Consistência, Spec §FR-REG-016–017; Interface §INT-WEB-REG-002; RFW Compatibility §GAP-RFW-REG-007] {auto}
- [x] CHK016 - O fluxo Google usa `issuer + sub`, não vincula automaticamente por e-mail e invalida credenciais pendentes antes da ativação? [Consistência, Spec §FR-REG-043–051; Data Model §Entity: ExternalIdentity; Interface §INT-WEB-REG-003] {auto}
- [x] CHK017 - A expressão “demais dados preenchidos” exclui explicitamente senha, confirmação e token, e o cenário de interação deixou de mencionar uma decisão futura já encerrada? [Consistência, Spec §FR-REG-035; Interface §INT-WEB-REG-001 e §Shared Security and Privacy; Quickstart §Scenario 17] {auto}
- [x] CHK018 - A dependência de disponibilizar recuperação de senha no lançamento desta feature está explicitada, já que a ação do renderer depende de capability cuja implementação pertence a `user-authentication`? [Consistência, Spec §Escopo e FR-REG-008; Plan §Architecture and Responsibility Boundaries; Interface §INT-WEB-REG-001] {auto}

## Segurança, privacidade e abuso

- [x] CHK019 - Segredos, credenciais e provas possuem regras explícitas de efemeridade, armazenamento não recuperável e exclusão de logs, auditoria e DTOs públicos? [Segurança, Spec §FR-USR-012 e FR-REG-029; Data Model §Security rules e §Audit rules; Interface §Shared Security and Privacy] {auto}
- [x] CHK020 - A consulta de senha comprometida define k-anonimato, padding, política fail-closed e comportamento alternativo pelo Google? [Segurança, Research §Decision 2; Contracts §Pwned Passwords Range API] {auto}
- [x] CHK021 - A comprovação local define imprevisibilidade, vínculo, uso único, expiração, invalidação por reenvio e atomicidade de consumo? [Segurança, Spec §FR-REG-013–019; Data Model §Entity: Verification; Plan §Transaction and Failure Strategy] {auto}
- [x] CHK022 - Turnstile possui regra condicional, padrão zero, validação server-side, action/hostname, idempotência e comportamento fail-closed? [Segurança, Spec §FR-REG-028 e FR-REG-034–042; Contracts §Cloudflare Turnstile Siteverify] {auto}
- [x] CHK023 - A origem atrás de proxy confiável, a rejeição de headers forjados, o armazenamento binário direto, o acesso restrito e a exclusão coordenada em até 30 dias após a janela estão documentados? [Segurança e privacidade, Spec §FR-REG-039; Research §Decision 6 e §Decision 10; Data Model §OriginWindow] {humano}
- [x] CHK024 - Google possui validação de assinatura, issuer, audience, tempo, nonce, replay e e-mail verificado antes de qualquer escrita? [Segurança, Spec §FR-REG-043–052; Contracts §Google OpenID Connect] {auto}
- [x] CHK025 - Cancelamento e expiração invalidam provas, removem dados identificáveis e possuem retenção mínima documentada? [Privacidade, Spec §FR-REG-023–027; Research §Decision 8; Data Model §Retention and Cleanup] {auto}
- [x] CHK026 - A política para IP compartilhado define se o bloqueio temporário máximo, mesmo após Turnstile válido, é um risco de produto aceitável ou se exige mitigação adicional? [Risco aceito, Spec §FR-REG-040–041; Quickstart §Scenario 8] {humano}

## Falhas, concorrência e operação

- [x] CHK027 - Cada dependência externa possui ownership, campos aceitos, validação, timeout/indisponibilidade e resultado seguro documentados? [Completude, Contracts §Cloudflare, §Google, §Pwned Passwords e §SMTP; Constitution §Restrições de Arquitetura e Segurança] {auto}
- [x] CHK028 - A estratégia transacional impede identidade ativa parcial, duplicidade e efeitos repetidos nos fluxos local, Google e ativação? [Cobertura, Spec §FR-REG-009–010 e FR-REG-018–019; Plan §Transaction and Failure Strategy] {auto}
- [x] CHK029 - O envio SMTP ocorre depois do commit e sua falha preserva a pendência sem duplicar cadastro ou comprovação? [Consistência, Spec §FR-REG-032; Plan §Transaction and Failure Strategy; Contracts §SMTP por RFW] {auto}
- [x] CHK030 - O catálogo diário inclui expiração de cadastros e retenção de origens, com frequência mínima, idempotência, lotes próprios, timeout e proteção contra exclusão concorrente de usuário ativo? [Operação, Spec §FR-REG-023–024 e FR-REG-039; Plan §Transaction and Failure Strategy; Data Model §Retention and Cleanup] {auto}
- [x] CHK031 - A configuração possui origem exclusiva e separa parâmetros operacionais de configurações voláteis em banco? [Consistência, Plan §Configuration Ownership; Constitution §Restrições de Arquitetura e Segurança] {auto}

## Interface e rastreabilidade

- [x] CHK032 - Todas as interações humanas possuem inventário, entrada, conteúdo, ações, validação, responsividade, acessibilidade, contratos, telemetria e estados canônicos? [Completude, Interface §Interaction Inventory e §Interaction Details] {auto}
- [x] CHK033 - Os estados de erro, offline, acesso negado e conteúdo obsoleto possuem apresentação e saída definida quando aplicáveis? [Cobertura, Interface §INT-WEB-REG-001–005 States] {auto}
- [x] CHK034 - Os requisitos proíbem uma interface paralela e determinam reutilização dos componentes, providers, renderers e tokens públicos do RFW? [Constitution Alignment, Plan §RFW Compatibility Gate; Interface §Components and Design System; Constitution §IV] {auto}
- [x] CHK035 - Cada grupo de requisitos funcionais aponta para autoridade de design e cenário principal de validação? [Rastreabilidade, Plan §Requirement Traceability; Quickstart §Scenarios 1–20] {auto}
- [x] CHK036 - As cinco interações mapeiam user stories, requisitos, success criteria e contratos correspondentes? [Rastreabilidade, Interface §Traceability] {auto}
- [x] CHK037 - Não existem placeholders de clarificação pendentes na spec, no plano ou na Interface Design aprovada? [Completude, Spec §Clarifications; Plan §Complexity Tracking; Interface §Validation Summary] {auto}
- [x] CHK038 - O Argon2id possui piso de segurança, procedimento de calibração reproduzível, critérios mensuráveis, registro operacional e bloqueio de release quando o servidor-alvo não os atende? [Segurança e operação, Spec §SC-UR-014; Research §Decision 3; README §Preparação para produção] {humano}
- [x] CHK039 - A limpeza em múltiplas instâncias define liderança automática, aquisição atômica, heartbeat, expiração, estabilização superior ao timeout do lote, fencing, ausência de sobreposição, relógio confiável, falha segura e revalidação transacional do cadastro? [Concorrência e operação, Spec §FR-REG-024 e SC-UR-015; Research §Decision 10; Platform Operations §Coordenação de Manutenção] {humano}

## Notes

- Gate `requirements`: **PASS**, com 39 de 39 itens encerrados.
- Itens `{auto}` satisfeitos estão marcados com `[x]` e citam a evidência documental.
- As ambiguidades automáticas identificadas foram resolvidas na spec e refletidas nos artefatos dependentes.
- `CHK026` foi encerrado por decisão explícita do dono do produto: o limite temporário por IP permanece absoluto.
- `CHK007` foi revalidado com o padrão de 20 novas pendências de cadastro local por origem em uma janela de 24 horas e com a definição dos eventos contabilizados.
- `CHK029` foi revalidado com dispatch direto pós-commit, timeout explícito, ausência de outbox e recuperação por reenvio solicitado pela pessoa.
- `CHK038` foi encerrado com piso Argon2id, amostra mínima de 50 operações, mediana entre 500 ms e um segundo, percentil 95 de até 1,5 segundo e registro obrigatório no checklist de produção.
- `CHK039` foi encerrado com lease operacional separado de configuração, heartbeat de 30 minutos, expiração de quatro horas, estabilização de 10 minutos, timeout transacional de cinco minutos, `sessionId`, `epoch` e ausência de sobreposição entre líderes.
- `CHK023` foi revalidado com armazenamento direto do IP normalizado em `security_originWindow`, sem HMAC, e exclusão automática em lotes próprios do job coordenado até 30 dias depois do fim da janela.
- `CHK009` foi revalidado com 100 operações nominais em SMTP local, mínimo de 95 aceitações em dois minutos, perfil de teste isolado dos controles antifraude e um único smoke test no SMTP real, sem compromisso de capacidade.
- As marcações `[Ambiguity]` e `[Conflict]` identificadas nesta rodada foram resolvidas pela sessão de clarificação de 2026-07-26.
- O `[Gap]` de dependência entre features foi encerrado tornando a recuperação mínima de senha obrigatória para o release.
