# Interaction Surface Architecture

**Created**: 2026-07-25  
**Last Updated**: 2026-08-02<br>
**Status**: Approved
**Sources**: briefing 2026-07-17, Constitution 1.1.0, plano `user-registration`

## Surface Catalog

| Surface ID | Type | Users | Platforms and Form Factors | Product Coverage | Technology, Language and Runtime | Delivery Strategy | Design System | Module/Repository | Decision Status |
|------------|------|-------|----------------------------|------------------|----------------------------------|-------------------|---------------|-------------------|-----------------|
| `SURF-WEB-RINOS` | WEB | Pessoas não autenticadas, usuários, participantes de contas e administradores autorizados | Navegadores modernos em desktop, tablet e telefone | MVP e evolução; única superfície humana inicial | Java 25, Vaadin 25.0.2, Spring Boot 4.0.7, JAR no servidor | Web responsiva server-side, sem aplicativo nativo | Componentes, factories, tema, tokens, slots e renderers do RFW; extensões reutilizáveis retornam à plataforma | Repositório Rinos; aplicação hospedeira em `br.com.rinos.app`, RFW em `modules/RFW.Platform` | Approved |

## Cross-Surface Decisions

### Capability and Parity Policy

Há uma única superfície no MVP. Toda capacidade humana aprovada deve declarar cobertura `FULL`, `PARTIAL`, `DEFERRED` ou `N/A` nessa superfície. Aplicações nativas Android/iOS e CLI/TUI não são assumidas nem recebem paridade automática.

### Shared Domain and Contracts

A UI Vaadin consome somente facades, DTOs e VOs Java publicados pela camada `api`. Entities e repositories permanecem internos ao backend. Integrações externas passam por adapters que convertem contratos de provedor em valores internos validados.

Não há API pública REST aprovada no MVP. Se outro consumidor surgir, seu contrato e sua política de compatibilidade devem ser planejados antes da exposição.

### Shared Code Strategy

Com uma superfície server-side, domínio e apresentação permanecem no mesmo artefato executável, mas em packages separados. O RFW é compartilhado como dependência pelo submódulo `modules/RFW.Platform`. Componentes e capacidades técnicas reutilizáveis DEVEM evoluir nele; regras de negócio do Rinos permanecem na aplicação hospedeira. O procedimento obrigatório está em [Uso da RFW Platform no Rinos](./rfw-platform-usage.md).

### Accessibility and Input Baseline

Todas as jornadas devem operar por teclado, manter ordem de foco previsível, expor nomes/descrições acessíveis, anunciar erros e mudanças de estado e suportar zoom e reflow em viewport móvel. Mouse ou gesto de toque não podem ser a única forma de concluir uma ação. O padrão de conformidade e a matriz de navegadores serão consolidados na Interface Design/checklist.

### Localization and Content

Textos visíveis, validações e mensagens usam i18n por contexto funcional. Português do Brasil é o primeiro locale; a arquitetura não deve fixar mensagens em código nem impedir novos locales. Datas, horas e números usam formatadores do locale, enquanto persistência temporal usa UTC.

### Identity and Authorization Boundary

Rotas públicas declaram explicitamente as operações anônimas permitidas. Rotas autenticadas negam acesso por padrão. O Painel de Usuário usa a identidade autenticada como alvo implícito e nunca aceita um identificador arbitrário de outro usuário para consulta ou alteração.

## Decision History

| Date | Surface ID | Decision | Rationale | Source |
|------|------------|----------|-----------|--------|
| 2026-07-25 | `SURF-WEB-RINOS` | Adotar uma aplicação web responsiva Vaadin server-side como única superfície humana do MVP | Stack e operação já aprovadas; reduz duplicação para a equipe inicial | briefing e `user-registration/plan.md` |
| 2026-07-25 | `SURF-WEB-RINOS` | Tornar obrigatório pesquisar e reutilizar o RFW e seu showroom antes de criar interfaces | Centraliza componentes e devolve melhorias reutilizáveis às aplicações hospedeiras | Constitution 1.1.0 e `rfw-platform-usage.md` |
