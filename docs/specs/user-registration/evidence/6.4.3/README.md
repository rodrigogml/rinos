# Evidências da tarefa 6.4.3

Data da validação: 2026-08-01

## Escopo implementado

A solicitação de cancelamento usa a operação específica `REGISTRATION_CANCELLATION`. O renderer do RFW apresenta o
widget com a action `registration-cancellation`, consome o token efêmero e solicita sua validação no servidor antes
de chamar `RFWRegistrationCancellationProviderAdapter`, que delega para a fachada real do Rinos.

A política do Rinos sempre exige Turnstile para cancelamento quando a integração está habilitada. Essa decisão não
reutiliza o limiar de criação de cadastros e o fluxo não consome o limite absoluto de novas pendências por origem.

## Limite neutro de emissão

As propriedades exclusivas `rinos.registration.cancellation-request-limit` e
`rinos.registration.cancellation-request-window` têm padrões de três provas em 15 minutos. O serviço bloqueia a
pendência antes de contar os eventos `REGISTRATION_CANCELLATION_REQUESTED`, tornando a decisão serializada entre
instâncias. Somente uma prova persistida cria esse evento; ausência, inelegibilidade e limitação não incrementam a
janela.

A quarta solicitação não cria prova nem agenda SMTP. A fachada continua retornando a mesma challenge pública aleatória
usada nos demais resultados neutros, enquanto a observabilidade interna registra `RATE_LIMITED`. O instante de
liberação não atravessa o contrato público e, portanto, não pode revelar a existência da pendência.

> [!IMPORTANT]
> Não houve alteração na RFW Platform. A implementação consome apenas os contratos públicos já documentados da versão
> 2.0.0; por isso não foi necessário um ciclo de mudança no submódulo ou no showroom.

## Validação automatizada focada

O gate unitário cobre configuração, política sempre obrigatória, janela cheia, ausência de despacho, neutralidade da
fachada, telemetria interna e integração da tela com o adapter real. O teste Siteverify usa servidor local simulado e
comprova hostname, action, origem, token e chave idempotente da operação de cancelamento.

```powershell
mvn '-Dtest=RegistrationCancellationServiceTest,RegistrationCancellationFacadeImplTest,RinosConfigurationBindingTest,HumanVerificationPolicyFacadeImplTest,RFWPlatformIntegrationTest' '-Dit.test=TurnstileIntegrationIT' test failsafe:integration-test failsafe:verify
```

```text
Testes unitários:     41; 0 falhas; 0 erros; 0 ignorados
Testes de integração:  5; 0 falhas; 0 erros; 0 ignorados
BUILD SUCCESS
```

## Gate completo do Rinos

```powershell
mvn verify
```

```text
Testes unitários:      313; 0 falhas; 0 erros; 0 ignorados
Testes de integração:   54; 0 falhas; 0 erros; 8 E2E opt-in ignorados
BUILD SUCCESS
```

O gate executou também as integrações MySQL 9, migração global, origem exclusiva das propriedades e bootstrap HTTP
Vaadin. Não houve mudança de schema; a janela nova usa os eventos de cancelamento já existentes.
