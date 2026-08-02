# Evidência 6.5.7 — componente, E2E destrutivo e inspeção visual

Data da validação: 2026-08-02

## Cobertura executada

| Nível | Evidência |
|-------|-----------|
| Componente e adapter | Prova efêmera, provider real, resultado terminal, origem tipada e próximas jornadas condicionais. |
| E2E Chromium | Deep link sanitizado, foco, `one-time-code`, confirmação, resultado, teclado/touch, pt-BR e ausência de overflow. |
| Persistência MySQL | Cancelamento concorrente com um vencedor, remoção efetiva e liberação do e-mail para novo cadastro. |
| Inspeção visual | Confirmação e resultado em desktop 1440 × 1000 e telefone 390 × 844. |

O E2E de apresentação usa apenas um provider determinístico para a decisão de backend; rota, factory do Rinos,
componente e renderer do RFW, bundles, CSS e eventos Vaadin são os mesmos da aplicação. O efeito destrutivo real foi
executado separadamente pelo teste transacional contra um schema temporário MySQL 9.7.2, preservando a responsabilidade
de cada gate.

## Evidências visuais

- [confirmação pronta em desktop](./cancellation-confirmation-ready-desktop.png);
- [resultado terminal em desktop](./cancellation-confirmation-result-desktop.png);
- [confirmação pronta em telefone](./cancellation-confirmation-ready-phone.png);
- [resultado terminal em telefone](./cancellation-confirmation-result-phone.png).

A inspeção em resolução original confirmou hierarquia clara, consequência irreversível anterior à ação, labels
persistentes, botão principal inequívoco, retorno visualmente secundário, texto sem cortes e ausência de sobreposição
ou rolagem horizontal. O resultado diferencia corretamente a nova jornada de cadastro e o retorno ao login.

## Comandos e resultados

E2E completo da interface:

```powershell
mvn '-Drinos.ui.e2e.enabled=true' '-Dit.test=RegistrationViewE2EIT' verify
```

```text
12 testes E2E; 0 falhas; 0 erros; 0 ignorados
BUILD SUCCESS
```

Efeito destrutivo real:

```powershell
mvn '-Dit.test=IdentityRepositoryIT#cancelRegistration_shouldChooseOneWinner_andReleaseEmailForNewRegistration' verify
```

```text
MySQL 9.7.2; schema temporário descartável
1 teste de integração; 0 falhas; 0 erros; 0 ignorados
BUILD SUCCESS
```
