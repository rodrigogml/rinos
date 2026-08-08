# Rinos

Aplicação web multiusuário e multi-tenant para gestão pessoal e empresarial, construída com Java 25, Spring, Vaadin,
MySQL 9 e [RFW Platform](modules/RFW.Platform/README.md).

O projeto está em desenvolvimento orientado por especificações, mantidas em [docs/specs](docs/specs). As regras
obrigatórias para agentes e desenvolvedores estão em [AGENTS.md](AGENTS.md).

## Desenvolvimento local

### Pré-requisitos

- Java 25;
- Maven 3.9 ou posterior;
- submódulos Git inicializados;
- `br.eng.rodrigogml.rfw:rfw:2.0.0` e suas dependências de hospedeira disponíveis no classpath.
- MySQL 9 externo exclusivo para testes ou Docker Desktop com contêineres Linux, para executar o gate completo de
  integração.

Após clonar o projeto, inicialize os submódulos:

```shell
git submodule update --init --recursive
```

Quando o artefato do RFW ainda não estiver disponível no repositório Maven usado pela máquina, instale a versão fixada
pelo submódulo. Desde a versão 2.0, o antigo Kernel está consolidado no artefato único `rfw`; o parent
`rfw.distro:1.0.0` também deve estar disponível nesse repositório:

```shell
mvn -f modules/RFW.Platform/pom.xml clean install
```

> [!IMPORTANT]
> O RFW 2.0 declara como `provided` as integrações que pertencem à aplicação hospedeira. O `pom.xml` do Rinos deve
> declarar explicitamente as bibliotecas das capacidades usadas, como Spring Security, contexto Micrometer e o
> renderizador Markdown. Consulte [Uso da RFW Platform](docs/architecture/rfw-platform-usage.md) antes de alterar esse
> conjunto.

### Build e testes

```shell
mvn test
mvn verify
```

Testes unitários usam o sufixo `*Test`; testes de integração usam `*IT` e são executados pelo Failsafe durante
`mvn verify`.

O mesmo comando instrumenta testes unitários e integrados com JaCoCo e gera o relatório navegável em
`target/site/jacoco/index.html`, além dos formatos XML e CSV. A cobertura quantitativa serve para localizar caminhos
não exercidos; os gates de release continuam definidos pelos comportamentos e critérios de sucesso documentados.

Os testes de migration global, coordenação concorrente e persistência usam um schema descartável
`rinos_test_<uuid>`. O provedor preferencial é uma instância MySQL 9 externa explicitamente habilitada no
`application.properties`; sem essa configuração, o projeto usa Testcontainers com a imagem `mysql:9.0`. Quando nenhum
provedor está disponível, esses cenários são reportados explicitamente como ignorados para que o build local continue
utilizável.

O gate completo deve apresentar os seis cenários de migration, os quatro cenários físicos do schema de autenticação,
os oito cenários do lease, os 22 cenários de persistência da identidade e o cenário de timeout HIBP local executados,
sem skips:

```shell
mvn -Dit.test=GlobalDatabaseMigrationIT,AuthenticationDatabaseSchemaIT,MaintenanceLeaseRepositoryIT,IdentityRepositoryIT,PwnedPasswordsServiceIT verify
```

#### Uso seguro de uma instância MySQL 9 existente

> [!CAUTION]
> Nunca habilite os testes com o usuário operacional do Rinos, uma URL que selecione `rinos_global` ou qualquer schema
> de tenant. O harness rejeita URLs com database e nomes fora de `rinos_test_<uuid>`, mas a separação de privilégios no
> servidor é a barreira independente contra erro de configuração.

Um administrador do MySQL deve criar uma vez um usuário exclusivo. Ajuste host e senha para o ambiente; os caracteres
`_` do prefixo são escapados porque o `GRANT` interpreta `_` e `%` como curingas:

```sql
CREATE USER 'rinos_test'@'localhost' IDENTIFIED BY 'substitua-por-uma-senha-local';
GRANT ALL PRIVILEGES ON `rinos\_test\_%`.* TO 'rinos_test'@'localhost';
```

No `application.properties` não versionado, habilite o provedor e informe somente a URL do servidor:

```properties
rinos.test-database.external.enabled=true
rinos.test-database.external.server-url=jdbc:mysql://localhost:3306/?useUnicode=true&characterEncoding=UTF-8
rinos.test-database.external.username=rinos_test
rinos.test-database.external.password=senha-local
```

Cada classe usa um schema aleatório, recriado antes de cada cenário e removido no encerramento. Builds concorrentes não
compartilham schema. Uma interrupção abrupta pode deixar apenas um schema órfão com o prefixo reservado, nunca dados no
global ou em tenants. A configuração é lida diretamente do arquivo raiz; variáveis de ambiente, propriedades JVM e
argumentos Maven não habilitam nem sobrescrevem o provedor.

#### Fallback com Docker no Windows

O Testcontainers detecta automaticamente o Docker Desktop pelo named pipe do Windows; o projeto não exige
`DOCKER_HOST` nem contêiner permanente. Use o backend WSL 2 e contêineres Linux.

Em uma máquina Windows 11 ainda sem WSL, execute uma vez em PowerShell **como administrador** e reinicie:

```powershell
wsl --install --no-distribution
```

Depois da reinicialização, atualize o WSL e instale o Docker Desktop:

```powershell
wsl --update
winget install --id Docker.DockerDesktop --exact
```

Inicie o Docker Desktop, aceite os termos aplicáveis ao ambiente, mantenha o engine baseado em WSL 2 e aguarde o
estado operacional. Não selecione o engine de contêineres Windows, que não é suportado por estes testes.

Valide o ambiente antes do Maven:

```powershell
wsl --version
docker version
docker info
docker run --rm hello-world
```

`docker version` deve exibir as seções Client e Server. Se exibir apenas o Client, inicie ou aguarde o Docker Desktop.
Remova variáveis `DOCKER_HOST`, `DOCKER_TLS_VERIFY` ou `DOCKER_CERT_PATH` definidas manualmente, salvo quando houver um
runtime remoto deliberadamente configurado. Em seguida, execute o gate completo acima e confirme que os testes
MySQL não foram ignorados.

Referências operacionais:

- [Instalação do WSL](https://learn.microsoft.com/windows/wsl/install)
- [Instalação do Docker Desktop no Windows](https://docs.docker.com/desktop/setup/install/windows-install/)
- [Ambientes suportados pelo Testcontainers](https://java.testcontainers.org/supported_docker_environment/)

### Execução

Crie o `application.properties` não versionado na raiz a partir do
[`application.properties.model`](application.properties.model), preencha os valores do ambiente e execute:

```shell
mvn spring-boot:run
```

O arquivo raiz é a única origem aceita pelo Rinos. Argumentos de linha de comando, propriedades JVM, variáveis de
ambiente, `SPRING_APPLICATION_JSON`, profiles e arquivos adicionais não complementam nem sobrescrevem seus valores.
Interpolação por placeholders e chaves de importação também são recusadas. Arquivo ausente, propriedade obrigatória
vazia ou combinação incompatível impede a inicialização com diagnóstico operacional.

O pacote executável para desenvolvimento é produzido por:

```shell
mvn clean package
java -jar target/rinos-1.0.0.jar
```

Esse comando não constitui aprovação para produção. O JAR destinado a release DEVE ser
construído com o profile que valida os gates declarados no backlog:

```shell
mvn -Prelease clean package
```

O profile interrompe o build na fase `validate` quando encontra qualquer subtarefa aberta,
em andamento ou bloqueada marcada com `[release-blocker]` em
`docs/specs/user-registration/tasks.md`. O marcador fica no backlog que define o gate; não existe
uma segunda lista permissiva no POM. Remover o profile, omitir deliberadamente `-Prelease` ou apagar
um marcador sem encerrar sua evidência não transforma o artefato em release aprovado.

O lifecycle Maven executa `vaadin:build-frontend` e incorpora ao JAR o bundle otimizado de
produção. Não remova esse plugin nem compense sua ausência empacotando o servidor de
desenvolvimento do Vaadin: o artefato usado pelo serviço Linux deve iniciar sem ferramentas de
frontend ou dependências de desenvolvimento.

Os valores de desenvolvimento podem manter Turnstile, Google e migrações desabilitados. Quando uma integração for
habilitada, todas as suas propriedades obrigatórias devem estar presentes no mesmo arquivo.

## Preparação para produção

> [!IMPORTANT]
> Este checklist evolui junto com a implementação. Uma versão somente pode ser liberada quando os comandos,
> propriedades e gates mencionados aqui estiverem disponíveis e validados para aquela versão.
> A construção destinada a produção deve usar `mvn -Prelease clean package`; falha do gate é uma decisão
> automática de NO-GO e o JAR de um build comum não deve ser promovido como substituto.

1. Preparar um servidor Linux com a versão suportada do Java 25, MySQL 9 e um proxy reverso com HTTPS.
2. Criar o `application.properties` na raiz a partir do `application.properties.model`, mantendo todas as definições
   explícitas. Não usar variáveis de ambiente, propriedades JVM ou argumentos de linha de comando para sobrescrevê-las.
3. Configurar credenciais, SMTP, Cloudflare Turnstile, Google, Pwned Passwords, proxies confiáveis e demais integrações
   exigidas pelas features incluídas na versão. O `application.properties` real não pode ser versionado.
   Cada instância deve possuir `rinos.maintenance.instance-id` explícito e exclusivo; heartbeat, expiração,
   estabilização e timeout transacional dos lotes da coordenação de manutenção usam os valores documentados no modelo
   de properties. O timeout do lote deve permanecer inferior à estabilização.
4. Preparar os bancos e as permissões operacionais. Migrações são executadas automaticamente no deploy; falha global
   mantém a aplicação indisponível e falha de tenant mantém somente o tenant afetado bloqueado para intervenção externa.
   Seguir o [ciclo operacional e procedimento de falha das migrations](docs/architecture/database-scripts.md), sem
   repetição ou correção pela interface.
5. Executar o backup externo coordenado do global e dos tenants antes do deploy. Backup e restauração não são
   funcionalidades da interface nem operações controladas pelo Rinos.
6. Calibrar o Argon2id no mesmo tipo de servidor da produção usando a ferramenta entregue pela feature
   `user-registration`:
   - não usar menos de 19.456 KiB de memória, duas iterações e paralelismo um;
   - usar salt de 16 bytes e hash de 32 bytes;
   - depois do aquecimento da JVM, medir no mínimo 50 operações;
   - ajustar memória ou iterações para obter mediana entre 500 ms e 1 segundo e percentil 95 de até 1,5 segundo;
   - registrar hardware, JVM, parâmetros, data e resultados;
   - não liberar a versão se o piso de segurança ou o limite de latência não forem atendidos.

   A ferramenta lê os parâmetros exclusivamente do `application.properties` na pasta corrente:

   ```shell
   mvn -q -DskipTests compile dependency:copy-dependencies -DincludeScope=runtime
   java -cp 'target/classes:target/dependency/*' br.com.rinos.app.backend.module.identity.service.PasswordHashCalibrationTool
   ```
7. Iniciar o JAR executável atrás do proxy reverso e aguardar a validação automática do schema global e dos tenants.
   Definir `server.port=7070` para a porta interna padronizada e
   `rinos.application.public-base-url=https://app.rinos.com.br` como a origem pública canônica. Links externos são
   montados exclusivamente a partir dessa propriedade; cabeçalhos `Host` e `Forwarded` recebidos não podem alterar a
   origem de links enviados por e-mail. Manter `server.forward-headers-strategy=none` e listar em
   `rinos.proxy.trusted-proxies` somente os endereços ou CIDRs efetivamente usados pelo proxy; o Apache deve bloquear
   acesso externo direto à porta interna.
8. Verificar logs, saúde das integrações, resolução segura do IP de origem, aquisição do lease de manutenção por uma
   única sessão e ausência de configuração importada de fonte não autorizada.
   As transições do lease também incrementam o contador `rinos.maintenance.lease.events`, com a tag de cardinalidade
   fixa `event` nos valores `acquisition`, `takeover`, `renewal`, `loss` e `rejection`. Sem integração de métricas
   instalada, o Rinos mantém um registro em memória e não publica endpoint administrativo; uma instalação pode fornecer
   outro `MeterRegistry` para exportação sem alterar a coordenação.
   Com `spring.datasource.url` explícita, o primeiro heartbeat disputa `global-maintenance`; o catálogo de limpeza
   inicia depois de um intervalo de heartbeat e repete conforme `rinos.cleanup.interval`. Confirmar que somente a sessão
   estabilizada executa os lotes de expiração, retenção de IP e tombstones, e que uma falha parcial não interrompe as
   tarefas independentes seguintes.
   Validar também as métricas, tags, alertas iniciais e limites de responsabilidade definidos no
   [guia operacional do cadastro](docs/specs/user-registration/operations.md). O `correlationId` deve permanecer
   somente nos logs; dados pessoais e segredos não podem aparecer em tags ou mensagens.
9. Executar os smoke tests e todos os gates de release das features incluídas antes de liberar o acesso. Para cadastro,
   usar 100 dispatches no SMTP local controlado, com perfil de teste que permita ao menos 100 novas pendências por
   origem e verificação humana controlada, e somente um smoke test no SMTP real; esse resultado não declara throughput
   ou capacidade da instalação. Os limites antifraude padrão e o Turnstile real devem ser validados separadamente.

Consulte também:

- [Índice da documentação](docs/index.md)
- [Constituição do projeto](docs/constitution.md)
- [Uso obrigatório da RFW Platform](docs/architecture/rfw-platform-usage.md)
- [Organização dos scripts de banco de dados](docs/architecture/database-scripts.md)
- [Configuração da plataforma](docs/specs/platform-configuration/spec.md)
- [Operações da plataforma](docs/specs/platform-operations/spec.md)
- [Provisionamento e migração de tenants](docs/specs/tenant-storage-provisioning/spec.md)
- [Governança de dados dos tenants](docs/specs/tenant-data-governance/spec.md)
- [Cadastro de usuários](docs/specs/user-registration/spec.md)
