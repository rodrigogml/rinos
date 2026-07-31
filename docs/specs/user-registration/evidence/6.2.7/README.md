# Evidência da tarefa 6.2.7

## Escopo

Em 31 de julho de 2026 foram rastreados os providers usados por
`INT-WEB-REG-002` desde os contratos do RFW até as facades e serviços reais do
Rinos. Também foram inventariados os construtores de referências públicas e
repetidos os testes que protegem as URLs contra exposição de identificadores
internos.

Nenhum código novo foi necessário: a implementação e a cobertura já existiam,
mas a tarefa ainda não havia sido atualizada no backlog.

## Providers reais

Os adapters são componentes Spring concretos e dependem somente da camada pública
`br.com.rinos.app.api`. Nenhum adapter da UI importa `backend`, entity, repository
ou service interno.

| Adapter Rinos | Contrato RFW | Facade pública usada | Operações |
|---|---|---|---|
| `RFWRegistrationProviderAdapter` | `RFWRegistrationProvider` | `RegistrationStartFacade`, `RegistrationResendFacade`, `RegistrationActivationFacade` | iniciar, ativar e reenviar |
| `RFWActivationConsentProviderAdapter` | `RFWActivationConsentProvider` | `RegistrationActivationFacade` | registrar novos aceites e concluir a ativação |
| `RFWRegistrationCancellationProviderAdapter` | `RFWRegistrationCancellationProvider` | `RegistrationCancellationFacade` | solicitar e confirmar cancelamento |

As facades possuem implementações reais no backend e delegam aos casos de uso
transacionais. Não há provider provisório, resposta de sucesso artificial nem
acesso da UI diretamente à persistência.

`RFWPlatformIntegrationTest` confirma que a presença desses adapters faz o
`RFWAccessCapabilityService` anunciar exatamente as capabilities de cadastro,
continuação de aceites e cancelamento. O mesmo teste comprova que, sem providers,
nenhuma capability de negócio é anunciada.

## Referências públicas

O único serviço que constrói links de interação enviados ao usuário é
`PublicApplicationUriService`. Os demais usos de `URI` no código principal
validam endpoints de integrações externas e não geram navegação do produto.

O link de ativação possui a forma canônica:

```text
https://app.rinos.com.br/login?step=activation&proof=<prova-opaca>
```

O link de confirmação do cancelamento, consumido pela jornada própria futura,
possui a forma:

```text
https://app.rinos.com.br/cancel-registration?token=<prova-opaca>
```

Ambos são montados exclusivamente sobre `rinos.application.public-base-url` e
recebem somente a prova de uso único. Nenhum método aceita e-mail, `userId`,
`registrationId` ou `verificationId`, portanto esses valores não podem ser
incorporados ao path ou à query por essa fronteira.

Na entrada da ativação, `LoginView` aceita apenas um valor para `step` e um para
`proof`. Intenções desconhecidas, parâmetros repetidos e provas excessivamente
longas convergem para o login. Depois de entregar a prova ao estado efêmero do
`RFWAccessComponent`, a view substitui a localização visível do navegador por
`/login`.

As URLs de documentos legais usam uma referência própria do documento, conforme
o contrato de `INT-WEB-REG-002`; elas não expõem identificador de usuário,
cadastro ou comprovação.

## Validações executadas

Suíte focada:

```powershell
mvn "-Dtest=RFWPlatformIntegrationTest,RFWRegistrationProviderAdapterTest,RFWActivationConsentProviderAdapterTest,RFWRegistrationCancellationProviderAdapterTest,PublicApplicationUriServiceTest,LoginViewTest,RegistrationActivationFacadeImplTest,RegistrationResendFacadeImplTest,RegistrationCancellationFacadeImplTest" test
```

Resultado:

```text
Tests run: 58, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Gate completo:

```powershell
mvn verify
```

Resultado com Java 25 e MySQL 9.7.2:

```text
Testes unitários:     291; 0 falhas; 0 erros; 0 ignorados
Testes de integração:  47; 0 falhas; 0 erros; 2 E2E de navegador opt-in
BUILD SUCCESS
```

Os dois E2E opt-in pertencem à inspeção de navegador e form factors da tarefa
6.2.8. Eles não substituem nem impedem a validação dos contratos e URLs desta
tarefa.

## Conclusão

Os providers reais do ciclo local estão integrados por facades públicas, as
capabilities são descobertas pelo RFW somente quando esses beans existem e as
referências externas usam provas opacas sem IDs internos da identidade. A tarefa
6.2.7 está concluída.
