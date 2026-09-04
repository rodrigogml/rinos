# Contrato público de administração de acesso

**Status**: implementado nas fases 5.2 e 5.3
**Data**: 2026-08-16

## Fronteira

`AccessAdministrationFacade` é a única fronteira pública entre a central Vaadin e o backend administrativo. A UI não
recebe entities, repositories, códigos para digitação nem nomes técnicos para apresentação. O adapter Spring deriva o
ator autenticado, usa o contexto da `UI` exata e reautoriza toda leitura, pesquisa ou mutação.

## Leitura

`inspect(context, capabilities)` retorna `AccessAdministrationSnapshot` com:

- contexto e revisão monotônica da fotografia;
- capacidades de consulta e manutenção resolvidas individualmente;
- categorias e chaves do escopo, com referências internas usadas apenas na comunicação entre componentes;
- grupos, sujeitos, associações temporais e regras correntes limitados às seções autorizadas;
- efeito, vigência, estado e versão necessários para a matriz.

Uma única decisão composta resolve as três capacidades de leitura e outra resolve as duas capacidades de manutenção.
O adapter interpreta os resultados por chave e os gates comuns, evitando cinco resoluções completas. Se nenhuma seção
de leitura estiver permitida, a facade não é chamada. A implementação também não consulta repositories de seções sem
visibilidade.

`searchSubjects(context, query, limit)` aceita no máximo 200 resultados. No diretório global a limitação é aplicada no
banco antes da materialização. Termos de pesquisa não são gravados em auditoria ou telemetria.

## Mutações

Os DTOs públicos são:

- `AccessGroupSaveRequest`;
- `AccessGroupSubjectChangeRequest`;
- `AccessRuleSaveRequest`;
- `AccessRecordDeactivateRequest`.

Cada comando recebe contexto, `expectedRevision`, ator derivado, justificativa e correlação. A transação bloqueia a
guarda contextual e compara a revisão antes da mutação. Divergência produz `AccessAdministrationConflictException`
com motivo seguro `ACL_CONTEXT_REVISION_CONFLICT`; nenhuma alteração parcial é aplicada. Continuidade administrativa,
baseline protegida, histórico, auditoria, incremento de revisão e invalidação continuam sob os serviços de domínio.

## Prévia protegida

Cada comando possui uma operação de prévia correspondente em `AccessAdministrationFacade`. A prévia usa exatamente o
mesmo DTO, contexto e `expectedRevision` da mutação. `AccessAdministrationPreviewExecutor` executa o comando real em
uma transação `REQUIRES_NEW` que sempre termina com rollback, inclusive quando o comando seria aceito. Assim, validação
de escopo, baseline, continuidade, vigência e concorrência não é reimplementada na interface.

`AccessAdministrationPreview` informa somente contexto, revisão, tipo humano da mudança, menor quantidade de
administradores aptos nas fronteiras temporais conhecidas antes/depois, alcance da baseline, possibilidade de confirmar,
motivo seguro e instante. A simulação não publica auditoria, histórico, revisão ou invalidação porque toda a transação é
descartada. A confirmação exige reautenticação forte por TOTP ou passkey e executa novamente a autorização e o comando
com a revisão mostrada. Mudança concorrente fecha a prévia, recarrega o contexto e não reaproveita o resultado anterior.

## Explicação administrativa

A central seleciona um sujeito do contexto e uma ou mais chaves registradas, sem campo de código livre. O adapter
deriva consulente, identidade alvo, associação tenant e garantia da sessão; `AuthorizationFacade.explain` revalida a
chave administrativa de explicação antes de resolver o alvo. A apresentação separa gates estruturais, plano,
autenticação e resultado por chave, exibindo origens e vigências sem referências internas. Copiar produz somente o
resumo seguro localizado.

## Apresentação

`internalReference`, `nameI18nKey` e `descriptionI18nKey` são dados técnicos. Renderers usam somente o texto resolvido
no bundle. Se uma tradução estiver ausente, a central mostra uma mensagem genérica e nunca a chave i18n ou o código.
O bundle pt-BR contém nome e descrição para todas as 90 chaves v1, e o teste `AccessCatalogI18nTest` impede regressão.
