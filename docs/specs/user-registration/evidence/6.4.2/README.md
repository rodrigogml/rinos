# Evidências da tarefa 6.4.2

Data da validação: 2026-08-01

## Escopo implementado

A solicitação de cancelamento agora explica, antes da ação principal, que pedir instruções ainda não cancela o
cadastro. O texto antecipa as consequências da confirmação definitiva: exclusão da pendência, invalidação de links e
códigos de ativação, liberação do e-mail para um novo cadastro e ausência de desfazer.

Depois da solicitação, a confirmação apresenta uma resposta condicional. Ela orienta a informar a prova sem confirmar
se o cadastro pendente existe, se está elegível ou se houve envio. O fluxo continua sem destino mascarado e somente a
confirmação com prova válida pode efetuar o cancelamento.

## Evolução da RFW Platform

O comportamento compartilhado foi documentado, testado e publicado na RFW Platform pelo commit `7d47fe7`
(`Explica consequências do cancelamento pendente`). Não houve alteração de API, provider, banco ou regra de negócio:
foram preservadas as chaves i18n e a máquina de estados existentes.

Os bundles português, inglês, espanhol, francês, italiano e chinês simplificado possuem textos próprios. A seção de
ciclo de cadastro do showroom foi atualizada nos mesmos idiomas para orientar aplicações hospedeiras a preservar a
diferença entre consequência anterior à ação e resposta pública neutra.

Validação focada do renderer e dos bundles:

```powershell
mvn '-Dtest=RFWDefaultAccessStepRendererTest,RFWAccessMessageBundleTest' test
```

```text
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Gate completo do RFW:

```powershell
mvn test
```

```text
Tests run: 313, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Gate do showroom:

```powershell
mvn test
```

```text
Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Integração e inspeção no Rinos

`RFWPlatformIntegrationTest.cancellationRequest_shouldExplainConsequencesAndKeepConfirmationResponseNeutral` usa a
factory real do Rinos, o renderer padrão e o adapter real. O teste comprova o texto anterior à ação, a transição para
`REGISTRATION_CANCELLATION_CONFIRMATION`, a ausência de destino mascarado e a mensagem condicional apresentada depois
da solicitação neutra.

```powershell
mvn '-Dtest=RFWPlatformIntegrationTest' test
```

```text
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

O laboratório existente do showroom também foi executado com o artefato atualizado. A inspeção confirmou:

- texto completo antes de **Solicitar cancelamento**;
- resposta condicional na confirmação;
- largura do documento sem overflow horizontal em 1280 px e 390 px;
- texto legível e quebrado dentro do card nos dois tamanhos.

O gate completo do Rinos, usando o artefato RFW instalado a partir da revisão publicada, também foi aprovado:

```powershell
mvn verify
```

```text
Testes unitários:      307; 0 falhas; 0 erros; 0 ignorados
Testes de integração:   53; 0 falhas; 0 erros; 8 E2E opt-in ignorados
BUILD SUCCESS
```

> [!NOTE]
> Esta inspeção é proporcional à alteração textual. Estados adversos, acessibilidade completa, demais form factors e
> evidência visual abrangente permanecem nas tarefas 6.4.5–6.4.7.
