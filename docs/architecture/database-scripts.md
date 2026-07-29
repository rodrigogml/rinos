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

O identificador físico do tenant é interno, imutável, gerado pelo sistema e seguro para composição do nome do schema.
Ele não deve ser o nome de exibição da conta nem qualquer texto informado diretamente pelo usuário. A correspondência
entre a identidade funcional do tenant e seu schema físico pertence ao cadastro global de armazenamento.

## Responsabilidades

| Catálogo | Inicialização | Atualização |
|----------|---------------|-------------|
| Global | A infraestrutura cria o banco e executa `db/global/init` uma vez | O bootstrap da aplicação executa `db/global/update` antes de disponibilizar o sistema |
| Tenant | O provisionador executa `db/tenant/init` ao criar o armazenamento do tenant | A fila estrutural executa `db/tenant/update` separadamente para cada tenant |

O atualizador do RFW não cria bancos e não executa os scripts de `init`. Para atualizações adicionais ao banco
primário, o Rinos usa uma requisição explícita do RFW com o `DataSource`, as localizações do catálogo e o timeout do
lock. O modo automático do RFW permanece reservado ao banco global primário.

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

Os nomes de updates são ordenáveis e imutáveis depois de distribuídos. Um ajuste estrutural deve:

1. atualizar o catálogo `init` correspondente para que novos bancos já nasçam no estado final;
2. criar um novo arquivo no catálogo `update` correspondente para bancos existentes;
3. atualizar o marco de versão daquele catálogo;
4. preservar scripts de update já publicados.

Uma falha global mantém a aplicação indisponível. Uma falha de tenant mantém somente esse tenant em quarentena e não
interrompe o processamento dos demais. Correções são progressivas e externas à interface; não há rollback estrutural
automático nem repetição interna de migration falha.

## Referências

- [Uso da RFW Platform](rfw-platform-usage.md)
- [Provisionamento e migração de tenants](../specs/tenant-storage-provisioning/spec.md)
- [Operações da plataforma](../specs/platform-operations/spec.md)
- documentação `Infrastructure > Database Updater` no showroom do RFW
