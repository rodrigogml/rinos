# Evidências da tarefa 6.4.1

Data da validação: 2026-08-01

## Escopo implementado

A ação **Cancelar cadastro pendente** do renderer padrão da ativação agora abre uma nova intenção tipada
`REGISTRATION_CANCELLATION_REQUEST`. A transição:

- aparece somente quando a capability `REGISTRATION_CANCELLATION` está disponível;
- usa o valor atual do identificador, inclusive quando a pessoa o corrigiu depois de abrir a ativação;
- não transporta código, prova, challenge ou resultado da ativação;
- renderiza o formulário padrão do RFW já preenchido, sem componente ou renderer paralelo no Rinos;
- ainda não solicita cancelamento, executa Turnstile ou chama SMTP até a submissão explícita da etapa seguinte.

## Evolução da RFW Platform

O comportamento compartilhável foi implementado e publicado separadamente na RFW Platform pelo commit
`7d8ee5c` (`Preserva identificador ao solicitar cancelamento`). A alteração não cria nova API pública: o renderer
padrão passa a usar a entrada tipada pública já existente. Renderers personalizados não são afetados.

O showroom documenta a motivação, a minimização da transição e como observá-la no laboratório, em todos os idiomas
mantidos pela plataforma.

Validação focada do renderer:

```powershell
mvn "-Dtest=RFWDefaultAccessStepRendererTest" test
```

```text
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Gate completo do RFW:

```powershell
mvn verify
```

```text
Tests run: 311, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Gate do showroom:

```powershell
mvn verify
```

```text
Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Integração do Rinos

`RFWPlatformIntegrationTest.activation_shouldOpenCancellationRequestWithCurrentIdentifier_whenCapabilityIsAvailable`
usa a factory real do Rinos, a auto-configuração, o renderer padrão e o adapter real de cancelamento. O teste altera o
identificador na ativação, aciona o botão e comprova a nova etapa, o preenchimento e a ausência da prova opaca.

Como o Rinos consome `br.eng.rodrigogml.rfw:rfw:2.0.0` pelo repositório Maven local, o artefato do commit validado foi
instalado depois dos gates do RFW e antes de testar a hospedeira:

```powershell
mvn -DskipTests install
mvn "-Dtest=RFWPlatformIntegrationTest" test
```

```text
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

O gate completo do Rinos, com Java 25 e MySQL 9.7.2, também foi aprovado:

```powershell
mvn verify
```

```text
Testes unitários:      306; 0 falhas; 0 erros; 0 ignorados
Testes de integração:   53; 0 falhas; 0 erros; 8 E2E opt-in ignorados
BUILD SUCCESS
```

> [!NOTE]
> A explicação detalhada das consequências, a resposta pública neutra, o Turnstile, os estados adversos e a inspeção
> visual pertencem às tarefas 6.4.2–6.4.7. Esta tarefa conclui exclusivamente a composição e a transição segura.
