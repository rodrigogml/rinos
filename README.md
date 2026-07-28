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
- `rfw.platform:1.0.0` e suas dependências disponíveis no repositório Maven local ou configurado.

Após clonar o projeto, inicialize os submódulos:

```shell
git submodule update --init --recursive
```

Quando os artefatos do RFW ainda não estiverem disponíveis no repositório Maven usado pela máquina, instale primeiro
o Kernel e depois a Platform a partir dos submódulos. O parent `rfw.distro:1.0.0` também deve estar disponível nesse
repositório:

```shell
mvn -f modules/RFW.Platform/modules/RFW.Kernel/pom.xml install
mvn -f modules/RFW.Platform/pom.xml install
```

### Build e testes

```shell
mvn test
mvn verify
```

Testes unitários usam o sufixo `*Test`; testes de integração usam `*IT` e são executados pelo Failsafe durante
`mvn verify`.

### Execução

Crie o `application.properties` não versionado na raiz a partir do futuro `application.properties.model` e execute:

```shell
mvn spring-boot:run
```

O pacote executável é produzido por:

```shell
mvn clean package
java -jar target/rinos-1.0.0.jar
```

> [!NOTE]
> Enquanto a configuração e o schema global ainda não forem implementados, a inicialização operacional completa
> dependerá das próximas tarefas da Fase 1. O teste de contexto desta fundação desativa integrações externas.

## Preparação para produção

> [!IMPORTANT]
> Este checklist evolui junto com a implementação. Uma versão somente pode ser liberada quando os comandos,
> propriedades e gates mencionados aqui estiverem disponíveis e validados para aquela versão.

1. Preparar um servidor Linux com a versão suportada do Java 25, MySQL 9 e um proxy reverso com HTTPS.
2. Criar o `application.properties` na raiz a partir do `application.properties.model`, mantendo todas as definições
   explícitas. Não usar variáveis de ambiente, propriedades JVM ou argumentos de linha de comando para sobrescrevê-las.
3. Configurar credenciais, SMTP, Cloudflare Turnstile, Google, Pwned Passwords, proxies confiáveis e demais integrações
   exigidas pelas features incluídas na versão. O `application.properties` real não pode ser versionado.
   Cada instância deve possuir `rinos.instance.id` explícito e exclusivo; heartbeat, expiração, estabilização e timeout
   transacional dos lotes da coordenação de manutenção usam os valores documentados no modelo de properties. O timeout
   do lote deve permanecer inferior à estabilização.
4. Preparar os bancos e as permissões operacionais. Migrações são executadas automaticamente no deploy; falha global
   mantém a aplicação indisponível e falha de tenant mantém somente o tenant afetado bloqueado para intervenção externa.
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
7. Iniciar o JAR executável atrás do proxy reverso e aguardar a validação automática do schema global e dos tenants.
8. Verificar logs, saúde das integrações, resolução segura do IP de origem, aquisição do lease de manutenção por uma
   única sessão e ausência de configuração importada de fonte não autorizada.
9. Executar os smoke tests e todos os gates de release das features incluídas antes de liberar o acesso. Para cadastro,
   usar 100 dispatches no SMTP local controlado, com perfil de teste que permita ao menos 100 novas pendências por
   origem e verificação humana controlada, e somente um smoke test no SMTP real; esse resultado não declara throughput
   ou capacidade da instalação. Os limites antifraude padrão e o Turnstile real devem ser validados separadamente.

Consulte também:

- [Constituição do projeto](docs/constitution.md)
- [Uso obrigatório da RFW Platform](docs/architecture/rfw-platform-usage.md)
- [Configuração da plataforma](docs/specs/platform-configuration/spec.md)
- [Operações da plataforma](docs/specs/platform-operations/spec.md)
- [Provisionamento e migração de tenants](docs/specs/tenant-storage-provisioning/spec.md)
- [Governança de dados dos tenants](docs/specs/tenant-data-governance/spec.md)
- [Cadastro de usuários](docs/specs/user-registration/spec.md)
