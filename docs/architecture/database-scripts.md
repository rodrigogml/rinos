# Organização dos scripts de banco de dados

## Objetivo

O Rinos mantém catálogos de scripts independentes para o banco global e para os bancos dos tenants. Essa separação
impede que uma atualização global seja aplicada a um tenant, ou que scripts de tenant sejam executados no banco
global, e permite que o mesmo conjunto de scripts inicialize cada novo tenant.

## Estrutura obrigatória

```text
src/main/resources/db/
├── global/
│   ├── init/
│   │   ├── 01-ddl.sql
│   │   ├── 02-seed.sql
│   │   ├── 03-procedures.sql
│   │   └── 99-database-version.sql
│   └── update/
│       └── YYYYMMDD_NNN_update.sql
└── tenant/
    ├── init/
    │   ├── 01-ddl.sql
    │   ├── 02-seed.sql
    │   ├── 03-procedures.sql
    │   └── 99-database-version.sql
    └── update/
        └── YYYYMMDD_NNN_update.sql
```

Os diretórios `global` e `tenant` representam catálogos distintos. Uma execução do atualizador recebe apenas os
scripts de um catálogo e um `DataSource` que aponta para o banco correspondente. Os dois catálogos nunca devem ser
combinados na mesma execução.

## Nomenclatura dos schemas

O prefixo `rinos_` identifica todos os schemas pertencentes à aplicação:

- o schema global possui o nome fixo `rinos_global`;
- cada schema de tenant usa `rinos_<identificadorFisico>`.

O prefixo mais específico `rinos_test_` é reservado à infraestrutura automatizada de testes e não identifica tenants.
Cada execução cria somente schemas `rinos_test_<uuid>` com credencial MySQL própria, sem acesso ao global ou aos
tenants, e os remove ao terminar. A URL administrativa de testes deve apontar ao servidor sem selecionar previamente
um database. Essa convenção permite validar init, updates, mappings e concorrência numa instância MySQL 9 existente sem
alterar conteúdo operacional.

O identificador físico do tenant é interno, imutável, gerado pelo sistema e seguro para composição do nome do schema.
Ele não deve ser o nome de exibição da conta nem qualquer texto informado diretamente pelo usuário. A correspondência
entre a identidade funcional do tenant e seu schema físico pertence ao cadastro global de armazenamento.

## Responsabilidades

| Catálogo | Inicialização | Atualização |
|----------|---------------|-------------|
| Global | A infraestrutura cria o banco e executa `db/global/init` uma vez | O bootstrap da aplicação executa `db/global/update` antes de disponibilizar o sistema |
| Tenant | O provisionador executa `db/tenant/init` ao criar o armazenamento do tenant | A fila estrutural executa `db/tenant/update` separadamente para cada tenant |

## Ciclo operacional de inicialização e atualização

Os quatro fluxos usam catálogos e momentos distintos:

| Fluxo | Disparo | Executor | Indisponibilidade | Liberação |
|-------|---------|----------|--------------------|-----------|
| Init global | Instalação inicial | Infraestrutura, antes do primeiro startup | Aplicação ainda não pode iniciar | Somente depois que o init terminar com `databaseVersion` válida |
| Update global | Deploy ou reinício com nova versão | Bootstrap automático pelo RFW | Toda a aplicação, sem modo comum, administrativo ou somente leitura | Somente depois que a versão global exata for validada |
| Init de tenant | Provisionamento de uma conta | Fila estrutural da aplicação | Somente a conta e o tenant em preparação | Somente depois que todo o init e a versão do tenant forem validados |
| Update de tenant | Deploy, depois da compatibilidade global | Fila estrutural, separadamente por tenant | Somente o tenant em execução; os demais continuam conforme seu próprio estado | Individualmente, depois que o tenant alcançar a versão exata esperada |

O deploy segue esta ordem:

1. a infraestrutura garante que o global já foi criado pelo init e inicia o novo JAR;
2. o bootstrap descobre exclusivamente `db/global/update`, bloqueia a disponibilidade e valida ou atualiza o global;
3. uma falha global encerra o startup; nenhum processamento de tenant ou interface fica disponível;
4. com o global compatível e antes de a aplicação anunciar disponibilidade, o startup identifica tenants pendentes,
   os marca como `MIGRATING` de forma transacional e persiste suas operações; a fila estrutural então processa
   `db/tenant/update`;
5. cada tenant permanece indisponível enquanto aguarda ou executa sua migration e é liberado individualmente;
6. a falha de um tenant o mantém em quarentena, mas não deve interromper os demais tenants elegíveis.

> [!IMPORTANT]
> Migração não possui modo online ou somente leitura. Código e schema são tratados como um par compatível; nenhum
> escopo pode receber operações comuns enquanto sua versão for diferente da versão exata esperada pelo código.

O atualizador do RFW não cria bancos e não executa os scripts de `init`. Para atualizações adicionais ao banco
primário, o Rinos usa uma requisição explícita do RFW com o `DataSource`, as localizações do catálogo e o timeout do
lock. O modo automático do RFW permanece reservado ao banco global primário.

> [!IMPORTANT]
> `rfw.database.update.enabled` deve permanecer em `true` em uma instalação que provisiona ou atualiza tenants. Ao
> desativá-la, o RFW não registra os serviços que descobrem e validam scripts; por consequência, o Rinos também não
> inicia os trabalhadores de provisionamento e migração de tenants. Essa opção é apropriada apenas para diagnósticos
> controlados nos quais nenhuma operação estrutural de tenant deva ocorrer.

> [!IMPORTANT]
> O `DataSource` deve selecionar previamente o banco correto. Scripts não devem escolher dinamicamente outro banco
> com `USE`, nem depender do nome físico de um tenant.

O `lock-timeout` do updater é somente o prazo máximo para uma instância adquirir o lock nomeado de migration quando
outra instância estiver verificando ou atualizando o mesmo schema. Depois da aquisição, o lock permanece associado à
conexão até a execução terminar e é então liberado. Esse timeout não limita a duração da migration, não corresponde ao
lease de manutenção da plataforma e não constitui bloqueio geral das operações do MySQL.

## Versionamento independente

O global e o modelo de tenant possuem marcos `databaseVersion` independentes. Todos os tenants devem convergir para a
mesma versão estrutural esperada pelo código, mas cada banco mantém seu próprio histórico de aplicação.

O `init` de cada tenant também cria `core_tenantBootstrap` com a chave técnica imutável
`tenant.schema.baseline`. Seu valor registra a versão do catálogo que originou o schema e não é uma configuração de
negócio ou de instalação. Na validação de prontidão, o Rinos compara esse baseline, a view `databaseVersion`, o
catálogo distribuído e as evidências de migrations posteriores. Script desconhecido, hash divergente, lacuna posterior
ao baseline, versão ausente ou versão diferente da esperada bloqueiam somente o tenant afetado.

> [!IMPORTANT]
> O hash de cada update é calculado sobre o conteúdo UTF-8 distribuído e confrontado com a evidência histórica global.
> O baseline representa um init completo, portanto não simula que os updates anteriores foram executados um a um.
> Migrations posteriores ao baseline devem possuir evidência contínua e íntegra para que o tenant possa voltar a
> `READY`.

Os nomes de updates são ordenáveis e imutáveis depois de distribuídos. Um ajuste estrutural deve:

1. atualizar o catálogo `init` correspondente para que novos bancos já nasçam no estado final;
2. criar um novo arquivo no catálogo `update` correspondente para bancos existentes;
3. atualizar o marco de versão daquele catálogo;
4. preservar scripts de update já publicados.

Uma falha global mantém a aplicação indisponível. Uma falha de tenant mantém somente esse tenant em quarentena e não
interrompe o processamento dos demais. Correções são progressivas e externas à interface; não há rollback estrutural
automático nem repetição interna de migration falha.

## Falhas e intervenção externa

Falha de configuração, conexão, lock, catálogo, consistência de versão, validação de script ou execução é bloqueante no
escopo afetado. Uma migration pode ter aplicado efeitos parciais antes de falhar, especialmente em comandos DDL do
MySQL; por isso, reiniciar ou repetir sem diagnóstico não é uma recuperação segura.

Ao ocorrer uma falha, a infraestrutura deve:

1. manter indisponível a aplicação inteira, se o global falhou, ou somente o tenant em quarentena, se a falha foi local;
2. preservar os logs da tentativa com escopo, versão atual, versão-alvo, script, comando e causa disponíveis, sem copiar
   credenciais, URL de conexão, dados funcionais ou conteúdo desnecessário do tenant;
3. identificar se a causa é ambiental — configuração, credencial, conectividade, permissão, capacidade ou lock — ou se
   o banco exige correção estrutural ou de dados;
4. examinar o estado efetivamente alcançado pelo banco antes de decidir a correção, sem presumir rollback;
5. corrigir infraestrutura quando a causa for ambiental ou preparar um novo update incremental quando houver evolução
   estrutural ou de dados; updates já publicados ou aplicados permanecem imutáveis;
6. iniciar um novo ciclo controlado de deploy ou processamento estrutural para que a aplicação releia a versão e os
   efeitos existentes; isso não constitui repetição automática dentro da execução que falhou;
7. liberar o escopo somente depois que o mecanismo normal confirmar a versão exata esperada e todas as validações
   estruturais terminarem com sucesso.

> [!CAUTION]
> A interface não oferece comando para executar, repetir, ignorar, corrigir, reverter ou marcar migration como
> concluída. Também não altera manualmente `databaseVersion`. Reconhecer uma ocorrência ou registrar que ela está em
> tratamento não modifica a condição técnica nem libera o escopo.

Backup e restauração pertencem exclusivamente à infraestrutura. Se a solução escolhida envolver restauração externa, a
aplicação ou o tenant restaurado permanece indisponível até que identidade, versão e compatibilidade sejam novamente
validadas pelos fluxos normais. A aplicação não cria, seleciona nem restaura cópias e não promete que restauração seja a
resposta adequada para toda falha de migration.

## Referências

- [Uso da RFW Platform](rfw-platform-usage.md)
- [Provisionamento e migração de tenants](../specs/tenant-storage-provisioning/spec.md)
- [Operações da plataforma](../specs/platform-operations/spec.md)
- [Governança de dados e restauração externa](../specs/tenant-data-governance/spec.md)
- documentação `Infrastructure > Database Updater` no showroom do RFW
