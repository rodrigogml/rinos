# Evidências da tarefa 6.4.7

Data da validação: 2026-08-02

## Matriz de cobertura

| Nível | Contratos comprovados |
|-------|------------------------|
| Componente RFW no Rinos | Composição a partir da ativação, consequência anterior à ação, estado ocupado, rejeição recuperável, token Turnstile consumido, action contextual e transição neutra. |
| Adapter do Rinos | Conversão dos DTOs públicos, locale e correlação efêmeros, challenge neutra, erros por campo e ausência de entity na fronteira da UI. |
| E2E Chromium | Foco, teclado, anúncio de erro, touch, pt-BR, reflow em desktop e telefone e transição nominal para a confirmação. |
| Inspeção visual | Hierarquia, legibilidade, consequência, proximidade das ações, erro no campo e ausência de overflow horizontal. |

O E2E substitui somente a decisão de backend por um provider determinístico em `src/test`. O componente,
`RinosAccessComponentFactory`, renderer, bundles, CSS, eventos Vaadin e navegação de estados são os mesmos da
aplicação. O adapter real é validado em conjunto com o componente no gate focal, sem publicar rota ou contrato
adicional em produção.

## Evidência visual registrada

As capturas foram produzidas pelas mesmas jornadas E2E e inspecionadas em resolução original:

- [erro anunciado em desktop 1440 × 1000](../6.4.6/cancellation-request-feedback-desktop.png);
- [reflow e alvo de toque em telefone 390 × 844](../6.4.6/cancellation-request-ready-phone.png).

A inspeção confirmou card centralizado, título e consequência legíveis, label persistente, erro adjacente ao campo,
ação principal inequívoca e ação de retorno distinguível. Em telefone, o conteúdo ocupa a largura disponível sem
corte, sobreposição ou rolagem horizontal.

## Validação automatizada

Componente e adapter real:

```powershell
mvn '-Dtest=RFWPlatformIntegrationTest,RFWRegistrationCancellationProviderAdapterTest' test
```

```text
25 testes; 0 falhas; 0 erros; 0 ignorados
BUILD SUCCESS
```

E2E completo da feature até este ponto:

```powershell
mvn test-compile '-Drinos.ui.e2e.enabled=true' '-Dit.test=RegistrationViewE2EIT' `
  failsafe:integration-test failsafe:verify
```

```text
10 testes; 0 falhas; 0 erros; 0 ignorados
BUILD SUCCESS
```

Gate completo:

```text
Rinos: 316 testes unitários e 56 testes de integração; 0 falhas; 10 E2E opt-in ignorados
RFW: 313 testes; 0 falhas; 0 erros; 0 ignorados
Showroom: 21 testes; 0 falhas; 0 erros; 0 ignorados
```

Os dez E2E ignorados no gate padrão foram executados pelo comando explícito. A tarefa 7.3.3 permanece responsável
pela avaliação humana transversal com leitor de tela real; esta tarefa não a presume a partir da árvore semântica.
