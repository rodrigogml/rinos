# Evidência da tarefa 7.2.7

## Escopo

Em 31 de julho de 2026 foram triados os achados inventariados na tarefa 7.2.5,
corrigidos os casos bloqueantes e repetidos os testes e scanners aplicáveis. O
objetivo deste gate é impedir o release com vulnerabilidade conhecida aplicável,
exposição diagnóstica de segredo ou falha estática capaz de comprometer o fluxo em
execução.

## Dependências e vulnerabilidades conhecidas

O inventário inicial consultou 248 dependências de runtime e encontrou 58
associações do OSV em 15 pacotes. Eliminadas as duplicidades entre avisos e
dependências, eram 52 avisos únicos: 5 críticos, 17 altos, 21 moderados e 9 baixos.

A pilha foi atualizada para Spring Boot 4.0.7 e passou a resolver, entre outros:

| Componente | Versão validada |
|---|---:|
| Spring Framework | 7.0.8 |
| Spring Security | 7.0.6 |
| Tomcat Embed | 11.0.22 |
| Logback | 1.5.34 |
| Jackson 2 | 2.21.5 |
| Jackson 3 | 3.1.5 |

Os BOMs do Jackson são importados explicitamente antes do BOM do Vaadin. Essa
ordem é necessária porque apenas alterar propriedades do Spring Boot não impede
que outro BOM importado posteriormente selecione uma versão anterior.

Após uma resolução limpa da árvore de runtime, a consulta em lote à API oficial
do OSV produziu:

```text
Rinos:        236 pacotes resolvidos; 0 pacotes afetados; 0 associações
RFW Platform: 225 pacotes resolvidos; 0 pacotes afetados; 0 associações
RFW Showroom: 226 pacotes resolvidos; 0 pacotes afetados; 0 associações
```

O OWASP Dependency-Check permanece inadequado como fonte única neste ambiente
enquanto a NVD responder HTTP 429 e não houver cache local confiável. A consulta
direta ao OSV foi mantida como verificação determinística desta tarefa; a limitação
operacional do scanner OWASP já está registrada na evidência 7.2.5.

## Segredo do Turnstile no RFW Platform

`TurnstileConfig` é um `record` e, sem intervenção, sua representação diagnóstica
incluía todos os componentes, inclusive `secretKey`. A revisão
`a7be938c795e1cf3443c7dc9611797ce56a7a729` do RFW Platform:

- substitui a representação do segredo por `<redacted>`;
- testa que o valor real nunca aparece no `toString()`;
- documenta a restrição em todas as traduções do showroom;
- adota a mesma atualização de dependências de segurança.

O RFW foi validado e publicado isoladamente antes da atualização do ponteiro no
Rinos:

```text
RFW Platform: 133 testes; 0 falhas; 0 erros; 0 ignorados
RFW Showroom:  17 testes; 0 falhas; 0 erros; 0 ignorados
```

## Análise estática

O possível acesso nulo em
`ExternalIntegrationPropertiesValidatorConfig.validateMail` foi eliminado ao
capturar a porta uma única vez antes da validação. Isso torna explícito para o
analisador e para o leitor que o teste de nulidade domina as comparações numéricas.

Comando repetido:

```powershell
mvn com.github.spotbugs:spotbugs-maven-plugin:4.10.3.0:check `
  "-Dspotbugs.effort=Max" "-Dspotbugs.threshold=Low"
```

Resultado comparativo:

| Medida | Antes | Depois |
|---|---:|---:|
| Total | 74 | 73 |
| Prioridade média | 63 | 62 |
| Prioridade baixa | 11 | 11 |
| `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` | 1 | 0 |

Os 73 avisos remanescentes foram revisados por família:

| Família | Quantidade | Classificação contextual |
|---|---:|---|
| `CT_CONSTRUCTOR_THROW` | 40 | Validações de invariantes e dependências obrigatórias em construtores de entidades e beans; não há publicação do objeto parcialmente construído nem finalizador próprio. |
| `EI_EXPOSE_REP2` | 12 | Referências de associações JPA ou dependências injetadas pelo Spring; cópia defensiva quebraria a identidade e o ciclo de vida esperados. |
| `EI_EXPOSE_REP` | 10 | Navegação de associações JPA e um value object interno; não expõe coleção, segredo ou API remota mutável. |
| `THROWS_METHOD_THROWS_RUNTIMEEXCEPTION` | 4 | Propagação intencional de falha da tarefa de manutenção para permitir observabilidade e controle transacional. |
| `MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR` | 3 | Configuração declarativa de componentes Vaadin durante sua construção. |
| `SE_NO_SERIALVERSIONID` | 3 | Classes serializáveis por herança do Spring/Vaadin; não há contrato de serialização persistente ou entre versões. |
| `SE_BAD_FIELD` | 1 | Dependência Spring mantida em view Vaadin; a aplicação não usa passivação ou replicação de sessão Java. |

Nenhuma supressão genérica foi adicionada. Esses avisos permanecem visíveis e
devem ser reavaliados se o modelo de sessão, a persistência de objetos Java ou os
limites públicos dessas classes mudarem.

## Gate de regressão

O build completo foi executado com Java 25 e MySQL 9.7.2:

```text
Testes unitários:    291; 0 falhas; 0 erros; 0 ignorados
Testes de integração: 47; 0 falhas; 0 erros; 2 E2E de navegador opt-in
BUILD SUCCESS
```

Os dois testes ignorados dependem da ativação explícita do navegador e não cobrem
as correções desta tarefa.

## Conclusão

Não restou achado bloqueante conhecido no escopo analisado. A tarefa está
concluída com dependências sem associações no OSV, segredo diagnóstico protegido,
alerta de nulidade eliminado, testes verdes e avisos contextuais preservados para
futuras revisões.
