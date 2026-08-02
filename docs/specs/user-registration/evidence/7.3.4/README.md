# Evidência 7.3.4 — matriz visual responsiva

## Objetivo

Comprovar a renderização e a inspeção visual dos cinco contratos de interação de
`user-registration` em desktop e telefone, nos temas claro e escuro.

## Ambiente

- data da execução: 2026-08-02;
- aplicação: `RegistrationUiTestApplication`, com as views, o CSS, a segurança e os componentes
  públicos reais do RFW;
- desktop: `1440 × 1000`;
- telefone: `390 × 844`;
- temas: tokens claros e escuros públicos do RFW;
- navegador: Chromium controlado pelo navegador integrado do Codex.

> [!NOTE]
> O botão flutuante do Vaadin Copilot no canto inferior direito pertence exclusivamente ao modo de
> desenvolvimento do harness. Ele não integra a superfície do produto nem estará presente no build
> de produção.

## Matriz inspecionada

| Interação | Desktop claro | Desktop escuro | Telefone claro | Telefone escuro |
|-----------|----------------|-----------------|-----------------|------------------|
| `INT-WEB-REG-001` — cadastro local | [imagem](./int-web-reg-001-desktop-light.png) | [imagem](./int-web-reg-001-desktop-dark.png) | [imagem](./int-web-reg-001-phone-light.png) | [imagem](./int-web-reg-001-phone-dark.png) |
| `INT-WEB-REG-002` — ativação | [imagem](./int-web-reg-002-desktop-light.png) | [imagem](./int-web-reg-002-desktop-dark.png) | [imagem](./int-web-reg-002-phone-light.png) | [imagem](./int-web-reg-002-phone-dark.png) |
| `INT-WEB-REG-003` — conclusão Google | [imagem](./int-web-reg-003-desktop-light.png) | [imagem](./int-web-reg-003-desktop-dark.png) | [imagem](./int-web-reg-003-phone-light.png) | [imagem](./int-web-reg-003-phone-dark.png) |
| `INT-WEB-REG-004` — solicitação de cancelamento | [imagem](./int-web-reg-004-desktop-light.png) | [imagem](./int-web-reg-004-desktop-dark.png) | [imagem](./int-web-reg-004-phone-light.png) | [imagem](./int-web-reg-004-phone-dark.png) |
| `INT-WEB-REG-005` — confirmação de cancelamento | [imagem](./int-web-reg-005-desktop-light.png) | [imagem](./int-web-reg-005-desktop-dark.png) | [imagem](./int-web-reg-005-phone-light.png) | [imagem](./int-web-reg-005-phone-dark.png) |

## Critérios e resultados

| Critério | Resultado |
|----------|-----------|
| Conteúdo, labels e ações previstos no `interface-spec.md` | Aprovado nas 20 apresentações |
| Corte, sobreposição ou perda de informação | Não identificado |
| Hierarquia de título, instrução, campos e ações | Preservada |
| Estados de foco inicialmente definidos pelo contrato | Visíveis em claro e escuro |
| Reflow do card e quebra de textos em `390 × 844` | Aprovados |
| Rolagem horizontal nas dez apresentações de telefone | Ausente por medição de `scrollWidth` e `clientWidth` |
| Contraste visual aparente e distinção entre fundo, card, campos e ações | Aprovados; o gate mensurável permanece na evidência 7.3.1 |

## Conclusão

As 20 apresentações foram renderizadas e inspecionadas. Não foram encontrados bloqueios nem falhas
menores que exijam tarefa corretiva. Esta evidência conclui a tarefa 7.3.4 e não substitui as
avaliações humanas por teclado, leitor de tela e participantes previstas nas tarefas 7.3.2, 7.3.3,
7.3.5 e 7.3.6.
