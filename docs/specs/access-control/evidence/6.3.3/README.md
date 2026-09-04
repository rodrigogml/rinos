# Evidências da fase 6.3.3 — revisão final de implementação

**Data**: 2026-09-02

## Escopo revisado

- contratos, catálogo, schema global, resolução de regras, continuidade administrativa, cache, adapters Spring e
  interface ACL;
- coerência entre `spec.md`, plano, contratos, modelo de dados, interface, checklists, evidências e `tasks.md`;
- separação global/tenant, precedência de bloqueio, gates de plano/autenticação, auditoria, cache revisionado e falha
  fechada;
- composição UI baseada em RFW Platform, sem alteração do submódulo.

## Validação automatizada

```text
mvn clean verify
```

Resultado: build aprovado em 6 min 36 s, com 813 testes unitários sem falhas ou erros e 157 testes de integração
sem falhas ou erros. Dezoito integrações foram ignoradas pelas condições externas já declaradas nos próprios testes;
nenhuma foi ignorada para ocultar falha ACL. A execução usa MySQL 9.7 descartável para as integrações de persistência,
concorrência, migração e configuração.

Também foi validado especificamente o contexto que revelou a regressão de inicialização:

```text
mvn "-Dit.test=RinosConfigurationSourceIT" verify
mvn "-Dit.test=TenantStoragePersistenceIT" verify
```

Os dois comandos concluíram sem falhas. A inicialização com propriedades exclusivas, o JAR atrás de proxy reverso e a
fila concorrente de provisionamento foram exercitados sem afrouxar a proteção de produção.

## Resultado da revisão de segurança

- Não há autorização positiva implícita: ausência de regra permissiva, bloqueio vigente, escopo incompatível, estado
  inválido, plano insuficiente, garantia insuficiente ou indisponibilidade interna resultam em negação.
- Regras diretas e de grupo são avaliadas apenas no contexto solicitado; o cache leva sujeito, contexto, revisão e
  fronteira temporal, sem se tornar autoridade para uma decisão final.
- Mutação administrativa e continuidade são reavaliadas transacionalmente; persistência MySQL cobre colisões,
  histórico, isolamento e concorrência.
- As rotas e serviços ACL permanecem reautorizados; ocultação de ações na interface não é a única barreira.
- O `git diff --check` não encontrou erro de whitespace. Os avisos LF/CRLF do workspace são normalização de checkout,
  não conteúdo inválido no diff.

## Coerência documental

Os checklists `requirements.md`, `security.md` e `cross-artifact.md` registravam corretamente o fechamento do ciclo
documental original, mas continham texto temporal de que não existiam schema ou código. Foram preservados como
registro histórico e receberam uma seção datada que aponta para o estado implementado e para o backlog atual. A
constituição não foi alterada: seus princípios continuam atendidos e o seu relatório histórico não deve ser reescrito
apenas para refletir uma etapa posterior.

## Pendências deliberadamente abertas

1. **6.3.4 — acessibilidade manual:** os componentes Vaadin/RFW trazem semântica e teclado nativos, e há cobertura
   automatizada de diálogo e `aria-live`, mas ainda não há evidência manual reproduzível de fluxo integral por teclado,
   foco, reflow e leitor de tela. A entrega não deve ser apresentada como validada manualmente enquanto essa tarefa não
   estiver concluída.
2. **6.3.5 — métodos de enrollment RFW:** a RFW oferece ações de enrollment TOTP e por e-mail no componente de
   configurações de segurança; o Rinos aceita somente TOTP no adapter. A tentativa por e-mail falha fechada, portanto
   não concede acesso, mas a opção deve deixar de ser apresentada. A solução correta é uma evolução genérica da API
   RFW para declarar métodos permitidos, sujeita à autorização prévia, testes e showroom. Nenhum workaround ou
   alteração no submódulo foi feita nesta revisão.
3. **6.1.2, 6.1.3 e 6.2:** consumidores de pessoas, pagamentos e financeiro continuam inexistentes em Java; suas
   integrações ACL permanecem bloqueadas, como já registrado no backlog.
4. **6.3.1 e 6.3.2:** a matriz completa depende desses consumidores, e o benchmark ainda não possui metas nem
   protocolo aprovados.

## Conclusão

A revisão 6.3.3 está concluída como atividade de análise e quality gate. Ela não encerra a entrega integral do
controle de acesso: as tarefas bloqueadas e pendentes acima são condições explícitas para as partes correspondentes
do produto.
