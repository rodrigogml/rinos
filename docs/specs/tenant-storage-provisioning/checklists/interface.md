# Interface Checklist: Provisionamento do Armazenamento de Tenant

**Propósito**: validar o contrato de interface humana, não sua implementação.
**Criado**: 2026-08-29
**Feature**: [interface-spec.md](../interface-spec.md)

## Cobertura e estados

- [x] CHK001 - A superfície web única declara cobertura parcial, escopo incluído e exclusões intencionais? [Completude, Interface §Interface Coverage] {auto}
- [x] CHK002 - Cada tela/painel novo possui `INT-*`, ator, entrada, contrato, telemetria e rastreabilidade? [Rastreabilidade, Interface §Inventory, Details e Traceability] {auto}
- [x] CHK003 - Todos os estados canônicos foram definidos ou justificados para cada interação? [Cobertura, Interface §INT-WEB-TSP-001..003] {auto}

## Segurança e experiência

- [x] CHK004 - A interface separa publicamente os quatro estados de preparação de detalhes técnicos e de conta ativa? [Clareza, Interface §INT-WEB-TSP-001; Spec FR-TSP-PROV-013..014] {auto}
- [x] CHK005 - Inventário/detalhe deixam explícito que não há comando de migration, backup, restauração, retry técnico ou promoção manual? [Consistência, Interface §INT-WEB-TSP-002..003; Spec SEC-008] {auto}
- [x] CHK006 - Responsividade, teclado, foco, anúncios, contraste e localização foram definidos em todas as interações? [Acessibilidade, Interface §INT-WEB-TSP-001..003; Constituição V] {auto}

## Componentes e rastreabilidade visual

- [x] CHK007 - Componentes e extensões usam APIs públicas RFW e registram que não há lacuna aprovada? [Constituição, Interface §Avaliação RFW; docs/architecture/rfw-platform-usage.md] {auto}
- [x] CHK008 - As duas telas novas com mudança estrutural possuem wireframes de baixa fidelidade coerentes com o texto? [Completude, Interface §Wireframes; wireframes/] {auto}

## Notas

- Não há item `{humano}` aberto: os limites de exposição e ações foram definidos pela especificação.
