# Evidência da tarefa 7.5.7

**Data da decisão:** 2026-08-02
**Decisão vigente:** **NO-GO para produção**

## Autoridade do gate

As subtarefas que impedem release são marcadas diretamente com `[release-blocker]` no
`tasks.md`. O profile Maven `release` lê esse arquivo durante a fase `validate` e falha quando
qualquer marcador estiver aberto (`[ ]`, `[~]` ou `[!]`). Não existe uma segunda lista de
permissão no POM.

O procedimento operacional documentado exige:

```shell
mvn -Prelease clean package
```

Um `mvn clean package` comum continua disponível para desenvolvimento, mas seu JAR não constitui
um artefato aprovado para produção.

## Bloqueadores abertos

Após a implementação da recuperação mínima, da matriz automatizada e da calibração no servidor-alvo,
permanecem 10 gates abertos:

| Grupo | IDs | Condição para GO |
|---|---|---|
| Documentos jurídicos | 6.0.3, 6.0.4, 6.0.5 | Inventário e fornecedores reais, revisão/autorização jurídica e publicação versionada |
| Acesso inicial | 7.2.6 | Acesso real ao Painel validado sem tenant ou concessão automática |
| Acessibilidade e usabilidade humana | 7.3.2, 7.3.3, 7.3.5, 7.3.6, 7.3.7 | Teclado, leitor de tela, dez participantes, metas 9/10 e triagem dos achados concluídos |
| Integração real | 7.4.2 | Smoke SMTP real concluído depois dos gates locais já comprovados |

O ambiente de release (`7.5.5` e `7.5.6`) já foi validado, incluindo a calibração efetiva no
Turing com `iterations=75`; nenhum gate desse grupo permanece aberto.

Marcar a tarefa 7.5.7 como concluída significa que a decisão NO-GO foi registrada e passou a ser
aplicada pelo build. Não significa que os 10 bloqueadores remanescentes foram satisfeitos.

## Validação executada

O lifecycle comum permanece utilizável:

```shell
mvn -q -DskipTests validate
```

O gate de release deve continuar falhando enquanto os bloqueadores permanecerem abertos:

```shell
mvn -Prelease -DskipTests validate
```

O bloqueio ocorre antes de compilar ou empacotar, evitando promover pelo procedimento oficial um
JAR enquanto a decisão vigente for NO-GO. A infraestrutura continua responsável pelo deploy, pelo
servidor e pelo proxy; nenhuma função de implantação foi adicionada ao runtime do Rinos.

## Reavaliação

Cada gate somente pode trocar de status quando sua evidência própria comprovar conclusão. O mesmo
comando `mvn -Prelease validate` deve ser repetido depois de cada alteração. A decisão somente se
torna GO quando não restar marcador `[release-blocker]` aberto e todos os demais requisitos do
checklist de produção estiverem atendidos.
