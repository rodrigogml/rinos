# Evidência 1.3.7 — Migração para RFW 2.0

## Escopo validado

- ponteiro do submódulo atualizado de `a7be938c795e1cf3443c7dc9611797ce56a7a729` para
  `8057720bb2e2161e6bc6b9e107ad9dc34f738e52`;
- coordenada Maven alterada de `br.eng.rodrigogml.rfw:rfw.platform:1.0.0` para
  `br.eng.rodrigogml.rfw:rfw:2.0.0`;
- instalação separada do Kernel removida, pois suas capacidades foram consolidadas no artefato `rfw`;
- dependências `provided` necessárias às capacidades usadas pelo Rinos declaradas na aplicação hospedeira;
- falhas de template e transporte de e-mail migradas de `RFWFailException` para
  `RFWInfrastructureException` e `RFWIntegrationException`, respectivamente;
- teste de integração atualizado para compor o novo add-in de logging quando constrói manualmente as
  auto-configurações.

## Compatibilidade observada

As APIs de acesso, providers de cadastro, Google, Turnstile, conteúdo, banco e Vaadin usadas pelo Rinos permaneceram
compatíveis. A compilação limpa não identificou outras alterações obrigatórias no código da aplicação.

> [!NOTE]
> Na revisão analisada, a página de ativação do showroom ainda exemplifica a coordenada 1.0 anterior. O POM raiz do
> RFW, o nome do JAR produzido e a instalação Maven confirmam `br.eng.rodrigogml.rfw:rfw:2.0.0`; por decisão expressa,
> o Rinos segue o POM como contrato de distribuição.

## Validações reproduzíveis

```shell
mvn -f modules/RFW.Platform/pom.xml clean install
mvn -f modules/RFW.Platform/modules/rfw.showroom/pom.xml clean verify
mvn clean test
```

Resultados obtidos em 2026-07-31:

- RFW 2.0: 289 testes, sem falhas, erros ou ignorados;
- showroom do RFW: 17 testes, sem falhas, erros ou ignorados;
- Rinos: 291 testes unitários, sem falhas, erros ou ignorados;
- Rinos: 47 testes de integração, sem falhas ou erros; dois cenários E2E permaneceram ignorados pelo gate explícito
  de execução visual, enquanto migration MySQL e integração Vaadin foram executadas.
