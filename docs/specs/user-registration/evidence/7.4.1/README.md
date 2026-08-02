# Evidência 7.4.1 — gate de 100 aceitações SMTP

## Objetivo

Executar 100 cadastros nominais contra um servidor SMTP local controlado, sem que o limite padrão de
20 pendências ou a verificação humana externa contaminem a medição, e medir cada aceite a partir do
respectivo commit.

## Implementação do gate

- o teste opt-in `RegistrationRoundtripIT.smtpGate_shouldMeasureOneHundredAcceptancesFromTheirCommits`
  usa MySQL temporário e a fachada, os serviços, as transações e os repositories reais;
- o `SmtpEmailDispatcher` público do RFW renderiza os templates reais e envia por Spring Mail para
  um GreenMail 2.1.11 local em porta dinâmica;
- o perfil exclusivo do teste configura limiar de verificação humana em 100 e limite absoluto em
  120; os defaults da aplicação e os demais testes não são alterados;
- cada iteração abre uma transação externa de teste e registra um callback antes de o serviço
  registrar o callback SMTP. O instante é capturado no primeiro `afterCommit`, e o término é medido
  depois do retorno do callback que recebeu o aceite;
- a duração é, portanto, um limite superior conservador entre o commit efetivo e o aceite, e não
  inclui o tempo anterior de validação ou hash da senha;
- o relatório detalhado é gravado em `target/smtp-gate/7.4.1.json`, sem e-mail, senha, token, prova,
  URL ou corpo de mensagem.

O GreenMail foi selecionado porque oferece um servidor SMTP embutido para testes, suporta porta
dinâmica e sua série 2.1 é compatível com Jakarta Mail 2.1. Consulte a
[documentação oficial do GreenMail](https://greenmail-mail-test.github.io/greenmail/).

## Execução de 2026-08-02

Comando:

```powershell
mvn "-Drinos.smtp.gate.enabled=true" `
  "-Dit.test=RegistrationRoundtripIT#smtpGate_shouldMeasureOneHundredAcceptancesFromTheirCommits" `
  verify
```

| Medida | Resultado |
|--------|-----------|
| Cadastros persistidos | 100 |
| Commits observados | 100 |
| Mensagens aceitas pelo SMTP local | 100 |
| Contador de origem após a amostra | 100 |
| Métricas SMTP com resultado `accepted` | 100 |
| Mediana commit → retorno após aceite | 5 ms |
| Percentil 95 commit → retorno após aceite | 7 ms |
| Máximo commit → retorno após aceite | 129 ms |
| Falhas, rejeições ou bloqueios | 0 |

O primeiro envio concentrou a inicialização preguiçosa do transporte e produziu o máximo de 129 ms;
as demais amostras permaneceram na faixa de poucos milissegundos. As 100 medições ficaram abaixo de
dois minutos, mas a tarefa 7.4.2 permanece aberta porque ainda exige, separadamente, validar o limite
padrão de 20, o Turnstile real e um smoke test no SMTP real sem declaração de throughput.

## Regressão

Após o gate especializado, `mvn verify` sem a propriedade opt-in concluiu com sucesso:

- 72 testes de integração executados;
- 14 testes opt-in ignorados, incluindo o novo gate SMTP;
- nenhuma falha ou erro;
- build completo aprovado.

## Conclusão

A tarefa 7.4.1 está concluída. O gate comprova que uma configuração controlada aceita a amostra de
100 novas pendências pela mesma origem, que todos os commits geram exatamente um aceite SMTP local e
que cada duração pode ser auditada por correlação técnica sem persistir dados sensíveis.
