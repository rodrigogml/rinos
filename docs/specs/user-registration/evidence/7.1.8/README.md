# Consolidação da validação integrada

Data da consolidação: 2026-08-02

## Resultado

A documentação corrente, o backlog e as evidências da fase 7.1 foram confrontados com os resultados executados. A
validação integrada possui provas para persistência destrutiva no MySQL 9, cadastro Google simulado, roundtrip entre
interface, facade, domínio e banco, estados derivados de dados persistidos, cobertura quantitativa, build limpo
encadeado e isolamento dos schemas de teste.

| Tarefa | Resultado consolidado |
|--------|-----------------------|
| 7.1.1 | Em andamento somente pelo harness SMTP nominal de 100 cadastros, encaminhado para 7.4.1–7.4.2 |
| 7.1.2 | Ciclo local destrutivo aprovado no MySQL 9 |
| 7.1.3 | Variações do cadastro Google aprovadas com serviços externos simulados e persistência real |
| 7.1.4 | Roundtrip completo aprovado sem exposição de entidades de persistência |
| 7.1.5 | Estados de interface aprovados a partir de dados persistidos; catálogo legal dinâmico corrigido no RFW |
| 7.1.6 | Cobertura medida e lacunas rastreadas pelos respectivos critérios de sucesso |
| 7.1.7 | RFW, showroom e Rinos aprovados em builds limpos encadeados |
| 7.1.9 | Schemas MySQL descartáveis e provedor externo de teste validados |

## Referências correntes

- o ponteiro documentado do RFW foi atualizado para
  `7dbf0471c4ee003bee471841c8b4e832bfa6954b`;
- as evidências históricas mantêm os hashes e resultados das revisões realmente usadas em cada execução;
- o catálogo legal corrente é obtido por provider e reavaliado pelo RFW a cada renderização do fluxo de acesso;
- a política jurídica em rascunho não integra esta consolidação e continua sob edição independente.

## Gates remanescentes

A consolidação não antecipa aprovação de gates ainda não executados:

1. interface, acessibilidade e usabilidade permanecem na fase 7.3;
2. volume SMTP, observabilidade e operação permanecem na fase 7.4;
3. dependências externas, ambiente de release e decisão de produção permanecem na fase 7.5;
4. recuperação de senha e Painel do Usuário dependem dos SDDs próprios;
5. revisão jurídica e provas humanas continuam externas à automação de engenharia.

> [!IMPORTANT]
> A tarefa 7.1.1 não deve ser marcada como concluída antes da execução do harness nominal de 100 cadastros. O build
> aprovado em 7.1.7 valida o estado técnico atual, mas não substitui os gates operacionais ou humanos posteriores.

## Validação documental

A revisão verificou a coerência entre `tasks.md`, `plan.md` e as evidências 7.1.1–7.1.9. Não houve alteração de código
executável; portanto, não se aplica nova execução de testes ou build para esta tarefa documental.
