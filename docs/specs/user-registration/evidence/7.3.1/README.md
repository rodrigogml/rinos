# Gate automatizado WCAG 2.2 AA

Data da execução: 2026-08-02

## Escopo automatizado

A suíte `RegistrationAccessibilityE2EIT` executa axe-core 4.11.2 sobre o renderer real do RFW e a composição real do
Rinos. As integrações externas usam o mesmo harness determinístico dos E2E funcionais; nenhum HTML alternativo ou
mock de componente substitui a interface entregue.

Foram auditados 13 estados observáveis dos cinco contratos `INT-WEB-REG-001` a `INT-WEB-REG-005` em cada uma das
quatro apresentações:

- desktop 1440 × 1000, claro e escuro;
- telefone 390 × 844, claro e escuro;
- movimento reduzido para inspecionar cores estáveis, sem capturar valores intermediários de transições visuais.

Os estados cobrem cadastro inicial, validação e envio; ativação e reenvio; continuação Google inicial e rejeitada;
solicitação de cancelamento inicial, inválida e enviada; confirmação e resultado terminal. A análise usa as tags
`wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`, `wcag22a` e `wcag22aa`.

## Resultado

```text
Relatórios JSON: 52
Testes E2E: 4
Falhas: 0
Erros: 0
Violações critical: 0
Violações serious: 0
Violações de qualquer impacto: 0
BUILD SUCCESS
```

Os relatórios brutos são produzidos em `target/accessibility-evidence/*.json` e não são versionados por serem
artefatos regeneráveis. O teste falha quando uma regra retorna impacto `critical` ou `serious` e mantém todos os
demais resultados nos JSONs para triagem.

O gate padrão posterior `mvn verify` também terminou com `BUILD SUCCESS`: 320 testes unitários e 71 testes de
integração aprovados; os 13 casos E2E opt-in, incluindo este gate, permaneceram ignorados nesse perfil por contrato.

## Achado corrigido

A primeira execução encontrou `color-contrast`, impacto `serious`, no botão de link do RFW: `#0284c7` sobre branco
resultava em 4,09:1. A correção foi feita na fonte compartilhada, sem CSS paralelo no Rinos:

- RFW fixado em `65ede7c4ce8839b7b9f1fa5d06540f1540edd0c3`;
- destaque claro alterado para `--rfw-palette-info-700`;
- teste de contrato calcula e protege o piso de 4,5:1;
- documentação e laboratório de botões atualizados nos seis idiomas do showroom;
- RFW aprovado com 319 testes e showroom aprovado com 21 testes.

## Resultado inconclusivo do motor

Cada relatório contém um item `incomplete`, e não uma violação: o axe não consegue determinar automaticamente a cor
de fundo do texto do botão primário porque o fundo é um gradiente. Os extremos definidos pelos tokens foram conferidos
separadamente:

| Tema | Extremo claro | Extremo escuro | Piso aplicável |
|------|---------------|----------------|----------------|
| Claro, texto branco | 4,865:1 | 6,993:1 | 4,5:1 |
| Escuro, texto `neutral-950` | 12,451:1 | 9,286:1 | 4,5:1 |

O item permanece registrado para transparência, mas não constitui violação crítica ou séria. As avaliações humanas
por teclado e leitor de tela continuam pertencendo às tarefas 7.3.2 e 7.3.3.

## Comando reproduzível

```powershell
mvn test-compile `
  '-Drinos.ui.e2e.enabled=true' `
  '-Dit.test=RegistrationAccessibilityE2EIT' `
  failsafe:integration-test failsafe:verify
```
