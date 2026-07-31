# Evidência da tarefa 7.2.5

## Escopo

Em 30 de julho de 2026 foram executadas as verificações estáticas e de dependências
disponíveis para estabelecer o inventário de achados antes do release. Esta tarefa não
classifica a aplicabilidade de cada aviso nem altera dependências ou código; essa triagem e
as correções bloqueantes pertencem à tarefa 7.2.7.

## Dependências declaradas

Comando:

```powershell
mvn org.apache.maven.plugins:maven-dependency-plugin:3.9.0:analyze
```

Resultado:

```text
BUILD SUCCESS
```

O analisador apontou dependências transitivas utilizadas diretamente e starters declarados
como aparentemente não utilizados. Esses avisos são esperados em parte por causa da
composição e da autoconfiguração do Spring Boot e do Vaadin. Eles não justificam declarar
automaticamente todos os módulos internos dos frameworks como dependências diretas.

## Vulnerabilidades conhecidas

### OWASP Dependency-Check

Comando:

```powershell
mvn org.owasp:dependency-check-maven:12.2.2:check "-DfailBuildOnCVSS=7" "-Dformat=JSON"
```

O scanner não conseguiu obter a base NVD: o serviço respondeu HTTP 429 e não havia cache
local válido, resultando em `NoDataException`. Portanto, essa tentativa é inconclusiva e não
deve ser interpretada como ausência de vulnerabilidades.

A [documentação oficial do Dependency-Check](https://jeremylong.github.io/DependencyCheck/dependency-check-maven/index.html)
alerta que a primeira atualização da NVD pode ser demorada e recomenda infraestrutura de
espelhamento ou chave de API para uso confiável em automação.

### OSV

O scanner de fontes também não conseguiu resolver integralmente o projeto porque o Maven
Central respondeu HTTP 429 e o artefato privado da RFW Platform não está publicado em um
repositório público. Como fallback, o Maven resolveu localmente a árvore de runtime e os
coordenados foram consultados na API oficial do OSV.

Resultado:

```text
Dependências de runtime consultadas: 248
Pacotes com ao menos uma associação: 15
Associações com avisos: 58
```

Distribuição das associações:

| Dependência | Versão | Avisos |
|---|---:|---:|
| `ch.qos.logback:logback-core` | 1.5.22 | 3 |
| `com.fasterxml.jackson.core:jackson-core` | 2.20.1 | 2 |
| `com.fasterxml.jackson.core:jackson-databind` | 2.20.1 | 5 |
| `org.apache.tomcat.embed:tomcat-embed-core` | 11.0.15 | 12 |
| `org.springframework.boot:spring-boot` | 4.0.1 | 2 |
| `org.springframework.security:spring-security-config` | 7.0.2 | 2 |
| `org.springframework.security:spring-security-core` | 7.0.2 | 1 |
| `org.springframework.security:spring-security-oauth2-jose` | 7.0.2 | 1 |
| `org.springframework.security:spring-security-web` | 7.0.2 | 2 |
| `org.springframework:spring-core` | 7.0.2 | 1 |
| `org.springframework:spring-expression` | 7.0.2 | 3 |
| `org.springframework:spring-webmvc` | 7.0.2 | 11 |
| `org.springframework:spring-websocket` | 7.0.2 | 1 |
| `tools.jackson.core:jackson-core` | 3.0.3 | 4 |
| `tools.jackson.core:jackson-databind` | 3.0.3 | 8 |

Uma associação indica somente que pacote e versão coincidiram com um aviso. A tarefa 7.2.7
deve eliminar duplicidades, verificar configuração e caminhos alcançáveis, classificar
severidade e confirmar a versão corrigida antes de atualizar a pilha.

Referência: [uso oficial do OSV Scanner](https://google.github.io/osv-scanner/usage/).

## SpotBugs

Comando:

```powershell
mvn com.github.spotbugs:spotbugs-maven-plugin:4.10.3.0:check "-Dspotbugs.effort=Max" "-Dspotbugs.threshold=Low"
```

Resultado:

```text
Achados: 74
Prioridade média: 63
Prioridade baixa: 11
```

Distribuição por tipo:

| Tipo | Quantidade |
|---|---:|
| `CT_CONSTRUCTOR_THROW` | 40 |
| `EI_EXPOSE_REP2` | 12 |
| `EI_EXPOSE_REP` | 10 |
| `THROWS_METHOD_THROWS_RUNTIMEEXCEPTION` | 4 |
| `MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR` | 3 |
| `SE_NO_SERIALVERSIONID` | 3 |
| `SE_BAD_FIELD` | 1 |
| `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` | 1 |

Grande parte dos avisos exige avaliação no contexto do ciclo de vida do Spring, JPA e Vaadin.
O possível acesso nulo em `ExternalIntegrationPropertiesValidatorConfig.validateMail` deve
ser verificado primeiro na tarefa 7.2.7.

Referência: [plugin Maven oficial do SpotBugs](https://spotbugs.github.io/spotbugs-maven-plugin/plugin-info.html).

## Conclusão

A verificação cumpriu o objetivo de inventariar as ferramentas disponíveis e revelou
achados que impedem declarar o gate de segurança concluído. A tarefa 7.2.7 permanece aberta
para triagem, correção dos bloqueantes e repetição dos scanners.
