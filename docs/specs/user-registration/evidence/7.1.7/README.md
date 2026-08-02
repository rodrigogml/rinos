# Build limpo encadeado do RFW e do Rinos

Data da execução: 2026-08-02

## Ambiente

- Windows 11 amd64;
- Oracle JDK 25.0.1;
- Apache Maven 3.9.13;
- encoding da plataforma UTF-8;
- MySQL Community Server 9.7.2 pelo provedor externo descartável de testes.

## Revisão fixada

O ponteiro do submódulo referencia exatamente
`7dbf0471c4ee003bee471841c8b4e832bfa6954b`. O worktree do RFW estava limpo durante a validação.

> [!IMPORTANT]
> A revisão do RFW permanece localmente à frente de `origin/main`. Esta prova valida o conteúdo exato fixado, mas não
> substitui a publicação remota exigida antes de um clone ou release externo.

## Sequência executada

1. `mvn clean install` em `modules/RFW.Platform`:
   - 301 fontes principais e 50 fontes de teste compiladas;
   - 318 testes aprovados;
   - artefato `br.eng.rodrigogml.rfw:rfw:2.0.0` instalado no repositório Maven local.
2. `mvn clean verify` em `modules/RFW.Platform/modules/rfw.showroom`:
   - 29 fontes principais e 5 fontes de teste compiladas;
   - 21 testes aprovados;
   - documentação e laboratório do acesso empacotados.
3. `mvn clean verify` na raiz do Rinos, depois da instalação do RFW:
   - 184 fontes principais e 81 fontes de teste compiladas;
   - 326 testes unitários aprovados;
   - 70 testes integrados aprovados e 12 E2E opt-in ignorados;
   - relatório JaCoCo gerado para 165 classes;
   - JAR executável `rinos-1.0.0.jar` produzido.

Todos os comandos terminaram com `BUILD SUCCESS`.

## Impressões dos artefatos locais

| Artefato | SHA-256 |
|----------|---------|
| `rfw-2.0.0.jar` | `DBCEFECFF08BCF33AD55F0896CF12F854319FDBADB67B208DCACE8A01F4A5394` |
| `rinos-1.0.0.jar` | `DB0EDE6F82126718104B981A4AE11A7134726C494E098AE46FA52AC2F1C0983D` |

Os hashes identificam somente os artefatos locais desta execução; não constituem assinatura de distribuição.
