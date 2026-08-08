# Requirements Checklist: Autenticação e Recuperação do Usuário

**Purpose**: validar clareza, completude, consistência, mensurabilidade, segurança e cobertura da especificação antes da decomposição em tarefas.
**Created**: 2026-08-08
**Feature**: [Feature Specification](../spec.md)

## Escopo e rastreabilidade

- [x] CHK001 - O escopo diferencia autenticação global de autorização por conta e mantém papéis, grupos e tenants fora desta feature? [Completude, Spec §Escopo; Plan §Architecture and Responsibility Boundaries] {auto}
- [x] CHK002 - Cada uma das seis histórias possui prioridade, valor independente e cenários Given/When/Then ligados aos grupos de requisitos? [Rastreabilidade, Spec §User Scenarios & Testing] {auto}
- [x] CHK003 - Os métodos de senha, passkey, Google, TOTP, e-mail e recuperação possuem requisitos funcionais identificáveis e entidades correspondentes? [Completude, Spec §Requirements e §Key Entities; Data Model §Cross-Entity Invariants] {auto}
- [x] CHK004 - Os critérios de sucesso cobrem resultados funcionais, concorrência, acessibilidade e tempos das jornadas principais com limiares objetivos? [Mensurabilidade, Spec §Measurable Outcomes] {auto}
- [x] CHK005 - Requisitos, autoridade de design e cenários de validação estão mapeados sem grupo funcional órfão? [Rastreabilidade, Plan §Requirement Traceability; Quickstart §Scenarios 1–15] {auto}

## Autenticação, métodos e garantia

- [x] CHK006 - Os estados de usuário autorizados a iniciar ou manter sessão e sua invalidação estão definidos sem depender do método usado? [Clareza, Spec §Autenticação e Estado do Usuário FR-AUTH-001..014] {auto}
- [x] CHK007 - A política de senha quantifica comprimento, composição, Unicode, colagem, comprometimento, armazenamento e troca sem termos vagos? [Clareza, Spec §Senha Local FR-AUTH-PWD-001..010] {auto}
- [x] CHK008 - A origem da verificação de senha comprometida e o comportamento quando a dependência falha estão definidos? [Dependência, Plan §Senha; Contract External Services §Pwned Passwords] {auto}
- [x] CHK009 - O contrato de passkey cobre descoberta sem e-mail, user verification, desafio, origem/RP, múltiplas credenciais, gestão e anomalias? [Cobertura, Spec §Passkeys e Chaves de Segurança; Plan §Passkeys] {auto}
- [x] CHK010 - O vínculo Google é inequívoco quanto a `issuer + sub`, ausência de associação por e-mail, confirmação explícita e indisponibilidade independente? [Consistência, Spec §Identidade Google; Contract Authentication Providers §External Identity] {auto}
- [x] CHK011 - A exigência administrativa de MFA é separada de papéis/permissões e define quais combinações de fatores satisfazem a garantia? [Segurança, Spec §Autenticação de Dois Fatores FR-AUTH-MFA-002..012] {auto}
- [x] CHK012 - Enrollment, apresentação única, replay, tentativas e remoção de TOTP/e-mail/recovery codes têm requisitos inequívocos? [Cobertura, Spec §Autenticação de Dois Fatores; Data Model §TotpFactor, §EmailFactor e §RecoveryCode] {auto}
- [x] CHK013 - A reautenticação define duração, métodos contextualmente aceitos, ausência de nova autoridade e revalidação da operação original? [Clareza, Spec FR-AUTH-012 e FR-AUTH-SES-008; Research §Decision 10; Interface §INT-WEB-AUTH-010] {auto}
- [x] CHK014 - A invariante de ao menos um método utilizável está consistente entre senha, Google, passkey, fatores e usuário administrativo? [Consistência, Spec FR-AUTH-009..010, FR-AUTH-GGL-008 e FR-AUTH-MFA-017; Data Model §Cross-Entity Invariants] {auto}

## Abuso, recuperação e privacidade

- [x] CHK015 - O gatilho do Turnstile, janela, renovação, dimensões e indisponibilidade obrigatória estão quantificados? [Mensurabilidade, Spec §Proteção contra Automação e Descoberta FR-AUTH-ABUSE-003..008; Contract External Services §Cloudflare Turnstile] {auto}
- [x] CHK016 - Os sinais obrigatórios do MVP estão limitados a contadores independentes por identificador informado e IP, sem tornar futura análise comportamental pré-condição dos limites? [Clareza, Spec §Clarifications Session 2026-08-08 e FR-AUTH-ABUSE-001] {auto}
- [x] CHK017 - Respostas e limites de login/recuperação impedem descoberta de identidade tanto pelo conteúdo quanto pelo comportamento observável? [Segurança, Spec FR-AUTH-004..005, FR-AUTH-ABUSE-007..010 e FR-AUTH-REC-001..005] {auto}
- [x] CHK018 - Provas de recuperação possuem finalidade, validade, uso único, substituição, invalidação e retenção definidos? [Cobertura, Spec §Recuperação de Acesso; Data Model §AuthenticationProof e §Retention and Cleanup] {auto}
- [x] CHK019 - A recuperação de MFA no MVP limita-se a outro método forte confirmado ou recovery code válido e proíbe bypass/processo alternativo não especificado? [Segurança, Spec §Clarifications Session 2026-08-08 e FR-AUTH-REC-009; Research §Decision 9] {auto}
- [x] CHK020 - A criação de senha para identidade passwordless exige decisão explícita e não transforma recuperação de e-mail em método novo silencioso? [Segurança, Spec FR-AUTH-REC-008; Research §Decision 9; Interface §INT-WEB-AUTH-004 e §INT-WEB-AUTH-008] {auto}
- [x] CHK021 - Segredos, provas, identificadores técnicos e dados de origem têm regras de exposição, auditoria e retenção distintas e citáveis? [Proteção de dados, Spec FR-AUTH-008, FR-AUTH-PWD-010 e FR-AUTH-SES-010; Data Model §IdentityEvent Evolution e §Retention and Cleanup; Interface §Sensitive State] {auto}
- [x] CHK022 - Notificações exigidas possuem eventos, canal e regra de não vazar identidades ou segredos documentados? [Cobertura, Spec FR-AUTH-ABUSE-009, FR-AUTH-MFA-018, FR-AUTH-REC-011 e FR-AUTH-SES-011; Contract External Services §SMTP] {auto}

## Sessão, concorrência e persistência

- [x] CHK023 - Sessões normais e persistentes quantificam duração absoluta, inatividade, rotação, revogação e reconhecimento pelo usuário? [Clareza, Spec §Sessões; Plan §Session and Remember-me Strategy] {auto}
- [x] CHK024 - A distinção entre `HttpSession`/Vaadin e `AuthSession` global explica afinidade, perda de instância e revogação cross-instance sem prometer serialização da UI? [Arquitetura, Plan §Fronteira entre sessão Vaadin e sessão de autenticação; Research §Decision 1] {auto}
- [x] CHK025 - Fluxos intermediários não criam `SecurityContext` parcial e têm referência opaca, expiração, finalidade e consumo único? [Segurança, Plan §Authentication Flow; Data Model §AuthenticationFlow e §AuthenticationProof] {auto}
- [x] CHK026 - Consumo concorrente, repetição de confirmação e revogação idempotente têm resultado máximo e transação definidos? [Concorrência, Spec FR-AUTH-INFRA-LOCK e FR-AUTH-INFRA-IDEMP; Plan §Fronteira de transação; Quickstart §Scenario 15] {auto}
- [x] CHK027 - Bloqueio ou alteração de credencial concorrente com login/sessão possui regra fail-closed e ponto de revalidação? [Edge case, Spec FR-AUTH-002, FR-AUTH-011 e FR-AUTH-SES-006; Quickstart §Scenarios 11 e 15] {auto}
- [x] CHK028 - Rotação de chaves, limpeza diária e restauração de dados definem como não reativar artefatos vencidos ou revogados? [Operação, Spec §Decisões de Infraestrutura Auditáveis; Data Model §Retention and Cleanup; Quickstart §Scenario 15] {auto}
- [x] CHK029 - Configurações possuem origem exclusiva no `application.properties`, defaults/modelo e grupos de propriedade sem duplicação em banco? [Consistência, Plan §Configuration Ownership] {auto}

## Interface e contratos

- [x] CHK030 - A única superfície web declara cobertura FULL e inventaria toda tela, painel ou diálogo novo/modificado com ID estável? [Completude, Interface §Interface Coverage e §Interaction Inventory] {auto}
- [x] CHK031 - Cada interação especifica ator, entrada, dados, ações, validação, feedback, responsividade, acessibilidade, localização, integração e telemetria? [Completude, Interface §Interaction Details] {auto}
- [x] CHK032 - Os onze estados canônicos são definidos ou justificados para todas as dez interações? [Cobertura, Interface §Interaction Details e §Validation Summary] {auto}
- [x] CHK033 - Foco, teclado, leitor de tela, reflow, contraste, reduced motion, toque, colagem e gerenciadores possuem requisitos transversais? [Acessibilidade, Interface §Shared Accessibility and Input; Spec SC-AUTH-015] {auto}
- [x] CHK034 - Componentes e evoluções reutilizáveis apontam primeiro ao RFW e registram compatibilidade/documentação obrigatória? [Constitution Alignment, Plan §Interaction Surface Architecture; Interface §Components and Design System; RFW Gap Analysis §Required RFW Delivery Gate] {auto}
- [x] CHK035 - Cada interação mapeia histórias, requisitos, critérios de sucesso e contratos, e os wireframes obrigatórios preservam o texto como autoridade? [Rastreabilidade, Interface §Traceability, §Wireframes e §Validation Summary] {auto}

## Notes

- Itens `{auto}` foram resolvidos contra os artefatos citados; ambiguidades descobertas foram integradas à especificação antes da aprovação do gate.
- Não há item `{humano}` aberto: as decisões de produto necessárias a este gate já constam dos artefatos.
