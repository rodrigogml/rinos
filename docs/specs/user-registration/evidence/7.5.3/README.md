# Evidência da tarefa 7.5.3

## RFW Platform

- ponteiro do submódulo: `65ede7c4ce8839b7b9f1fa5d06540f1540edd0c3`;
- `main` remoto depois da publicação: o mesmo commit;
- worktree e branch do submódulo: limpos e sincronizados;
- versão declarada pelo RFW: `2.0.0`;
- versão consumida pelo Rinos: `2.0.0`.

O remoto estava temporariamente sete commits atrás do ponteiro já consumido pelo Rinos. A relação
foi comprovada como fast-forward, o dry-run confirmou o destino e os commits previamente validados
foram publicados sem alterar o código do submódulo neste gate.

## Properties

`application.properties.model` e o arquivo local possuem 96 chaves cada, sem chave ausente ou
exclusiva. A comparação não exibiu valores. `ConfigurationFileParityTest` executou quatro cenários
sem falhas, erros ou testes ignorados.

## Migrations

`GlobalDatabaseMigrationIT` executou cinco cenários no MySQL 9.7.2:

- banco vazio sem init bloqueado;
- banco atual não reaplicado;
- versão posterior aos artefatos bloqueada;
- catálogo global isolado do catálogo de tenant;
- falha parcial interrompida sem avançar falsamente a versão.

Comando:

```powershell
mvn "-Dtest=ConfigurationFileParityTest" "-Dit.test=GlobalDatabaseMigrationIT" verify
```

Resultado conjunto: nove testes, sem falhas, erros ou testes ignorados, e `BUILD SUCCESS`.

## Documentação

A validação percorreu o README, o índice, as diretrizes de banco e RFW e todos os artefatos
Markdown de `user-registration`: 236 links locais foram resolvidos e nenhum destino estava ausente.
