# Evidência da tarefa 8.7 — cenários de validação do fundador

Data: 2026-09-01.

## Matriz de cenários

| Cenário | Proteção comprovada |
|---|---|
| Cadastro local | A continuação usa o método `PASSWORD`, emitido somente após ativação completa. |
| Cadastro Google | A continuação usa o método `GOOGLE`, emitido somente após conclusão do vínculo externo. |
| Fundador sem TOTP ativo | A política retorna obrigação de enrollment e o guard redireciona qualquer rota autenticada para a tela restrita. |
| Fundador após confirmação | O fator persistido ativo encerra a obrigação na próxima avaliação. |
| Falha, expiração ou abandono | Sem confirmação não há fator ativo; a política continua restringindo a jornada. Prova expirada ou esgotada é revogada. |
| Outro usuário | A igualdade com a configuração é interna e o resultado é falso para outra identidade. |
| Sigilo do e-mail | A facade publica somente `boolean`; a continuação tem `toString()` redigido e a UI não recebe o valor configurado. |
| Escopo de conta | Nenhum dos fluxos toca entidades ou serviços de tenant, conta, associação, grupo, chave ou privilégio. |

## Regressões cobertas

`RegistrationActivationServiceTest` e `ExternalRegistrationCompletionServiceTest`
confirmam que os dois caminhos produzem a continuação de sessão depois das garantias
da ativação. `RFWRegistrationProviderAdapterTest` e
`RFWExternalRegistrationProviderAdapterTest` confirmam que ela é entregue ao
lifecycle RFW com principal sem autoridades implícitas. A política e o guard possuem
testes dedicados, e a expiração ou exaustão do enrollment permanece coberta por
`TotpFactorServiceTest`.

As validações executadas neste ciclo foram
`mvn '-Dit.test=IdentityRepositoryIT,RegistrationRoundtripIT' verify` (813 unitários
e 36 integrações selecionadas) e
`mvn '-Dit.test=GlobalDatabaseMigrationIT' verify` (813 unitários e nove integrações
de migração), ambas concluídas contra MySQL 9.7 descartável.
