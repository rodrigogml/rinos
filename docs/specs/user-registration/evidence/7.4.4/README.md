# Evidência da tarefa 7.4.4

## Fronteira validada

O Rinos emite sinais estáveis e sanitizados; armazenamento, consulta, regra temporal e entrega da
notificação pertencem ao monitoramento da infraestrutura. A aplicação não envia alertas por
e-mail ou webhook e não expõe endpoint administrativo nesta feature.

| Condição | Sinal da aplicação | Regra operacional |
|----------|--------------------|-------------------|
| Falha SMTP | contador e timer com `template_failure` ou `transport_failure`, mais `WARN` com correlation ID e tipo fechado | alertar acima de 5% em 15 minutos, com ao menos 20 tentativas |
| Falha de job | `WARN` com nome fechado da tarefa e tipo da falha; tarefas independentes continuam | alertar em qualquer ocorrência |
| Integração indisponível | contador de operação com resultado `unavailable` ou `unexpected_failure` | alertar quando contínuo por cinco minutos |
| Erro de migration | startup interrompido, exit code diferente de zero e categoria segura do RFW | alertar imediatamente e bloquear a liberação |

## Provas executáveis

- `VerificationEmailDispatchServiceTest` comprova métricas e logs de falha sem mensagem sensível.
- `IdentityCleanupCatalogSchedulerTest` comprova o `WARN` sanitizado e a continuidade das demais tarefas.
- `RegistrationObservabilityServiceTest` comprova a série `unavailable` com tags fechadas.
- `GlobalDatabaseMigrationIT` comprova interrupção por falha parcial sem avançar falsamente a versão.
- `RinosConfigurationSourceIT` comprova que o JAR encerra sem iniciar e produz diagnóstico seguro
  quando a migration global não pode ser preparada.

Comando focal:

```powershell
mvn "-Dtest=RegistrationObservabilityServiceTest,VerificationEmailDispatchServiceTest,IdentityCleanupCatalogSchedulerTest" "-Dit.test=GlobalDatabaseMigrationIT#startup_shouldStopWithoutAdvancingVersion_whenUpdateFailsPartially,RinosConfigurationSourceIT#application_shouldFailWithSafeDiagnostic_whenGlobalDataSourceIsMissing" verify
```

Resultado: 16 testes unitários e dois testes de integração executados, sem falhas, erros ou
testes ignorados, e `BUILD SUCCESS`.

> [!IMPORTANT]
> O gate de produção ainda deve testar o encaminhamento no monitoramento escolhido pela
> infraestrutura. Esse teste não altera a fronteira: o Rinos apenas produz os sinais.
