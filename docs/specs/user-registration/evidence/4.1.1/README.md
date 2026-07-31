# Evidência da tarefa 4.1.1

## Escopo validado

A fronteira pública do ciclo de cadastro foi consolidada em `br.com.rinos.app.api`, com DTOs de
entrada, VOs de resultado e enums fechados. O inventário validado contém 19 DTOs e VOs utilizados
pelas facades e pelos adapters de interface.

Os contratos:

- não possuem campos ou componentes que referenciem packages de entities ou repositories;
- copiam defensivamente senha, listas, conjuntos e mapas mutáveis;
- transferem e zeram a cópia interna da senha do pedido de cadastro;
- mantêm referências opacas somente nos estados que precisam continuar uma operação;
- não serializam e-mail, IP, prova, referência opaca, issuer, subject ou principal em `toString()`;
- validam as invariantes estruturais dos resultados que exigem continuação ou autenticação.

> [!IMPORTANT]
> Referências opacas de uso único continuam presentes nos contratos funcionais que precisam
> transportar uma continuação. Elas não são IDs persistentes e foram redigidas das representações
> diagnósticas para impedir vazamento acidental em logs.

## Teste de contrato

Comando:

```powershell
mvn -q -Dtest=PublicContractSecurityTest test
```

Resultado:

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

Os quatro cenários cobrem ausência de tipos de persistência, cópias defensivas, redação de dados
sensíveis e invariantes dos VOs de resultado.

## Validação completa

Comando:

```powershell
mvn -q verify
```

Resultado consolidado:

```text
Unit tests: 273; failures: 0; errors: 0; skipped: 0
Integration tests: 47; failures: 0; errors: 0; skipped: 37
```

Os 37 skips correspondem aos testes que exigem MySQL externo explicitamente habilitado ou Docker
disponível. Os testes unitários, de contrato e as integrações independentes desses provedores
foram executados com sucesso.

## Verificação de diff

Comando:

```powershell
git diff --check -- src/main/java/br/com/rinos/app/api src/test/java/br/com/rinos/app/api docs/specs/user-registration/tasks.md
```

Resultado: nenhuma inconsistência de whitespace encontrada.
