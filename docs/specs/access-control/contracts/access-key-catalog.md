# Catálogo Canônico de Chaves de Acesso

**Feature**: `access-control`
**Version**: 1
**Date**: 2026-08-14
**Status**: contrato inicial para implementação

## Finalidade

Este contrato é a fonte canônica dos códigos internos usados pelos módulos do Rinos. Ele evita chaves inventadas na
interface, colisões semânticas e nomes diferentes para a mesma capacidade. O catálogo descreve autorização; direitos
de plano continuam definidos em `plans-entitlements`.

> [!IMPORTANT]
> Códigos técnicos existem para estabilidade entre código, banco, auditoria e testes, mas nunca aparecem na interface.
> Usuários pesquisam e selecionam chaves por nome e descrição localizados.

## Convenção

O código segue `<escopo>.<recurso>[.<subrecurso>...].<acao>`:

- `global` identifica operações do sistema sem tenant;
- `tenant` identifica operações em exatamente um tenant;
- há ao menos um segmento de recurso e exatamente uma ação final; subrecursos opcionais refinam a capacidade;
- ownership não é inferido do código: o descriptor registra separadamente um único módulo proprietário;
- segmentos usam inglês, minúsculas e `.`;
- ações usam verbo estável no infinitivo técnico, como `view`, `create`, `update`, `block` ou `reconcile`;
- uma mudança incompatível de significado cria novo código;
- sinônimos não criam chaves diferentes;
- uma operação composta declara todas as chaves dos efeitos produzidos.

Cada descriptor registra: código, nome e descrição i18n, módulo proprietário, escopo, categoria, estado, requisitos de
origem e indicação de administração mínima. O registro modular é idempotente e deve falhar quando o mesmo código vier
com descriptor incompatível.

### Materialização obrigatória dos descriptors v1

Para evitar repetir colunas invariáveis em todas as tabelas, cada linha abaixo materializa normativamente o descriptor
da seguinte forma:

| Campo do descriptor | Valor v1 |
|---------------------|----------|
| `code` | valor da coluna **Código interno** |
| `nameI18nKey` | `access.key.<code>.name` |
| `descriptionI18nKey` | `access.key.<code>.description` |
| `scope` | `GLOBAL` nas chaves globais e `TENANT` nas demais seções |
| `categoryCode` | valor da coluna **Categoria** |
| `ownerModule` | um único módulo da coluna **Módulo proprietário** |
| `status` | `ACTIVE` |
| `sourceRequirements` | IDs ou seletores `FR-...-*` da coluna **Requisitos consumidores**, resolvidos contra a versão documental de 2026-08-14 |

A coluna **Nome i18n** mostra o texto pt-BR esperado para validar o bundle; não substitui `nameI18nKey`. Cada
`descriptionI18nKey` deve explicar finalidade e limite da operação sem incluir o código técnico na tradução. Um
seletor de requisitos terminado em `-*` significa todos os IDs existentes com aquele prefixo nesta baseline. Uma
faixa `FR-X-001..009` inclui cada ID existente entre os limites, sem inventar IDs para eventuais lacunas da numeração.
Seletores e faixas devem ser expandidos para linhas individuais de `access_keyRequirement` pelo contributor. Texto
livre na coluna complementa, mas não substitui, ao menos um ID, faixa ou seletor rastreável.

Os códigos e as chaves i18n são internos. Nem o código, nem `nameI18nKey`, nem `descriptionI18nKey` podem ser
renderizados ou aceitos como entrada digitável na interface; somente os textos traduzidos são apresentados.

## Categorias

| Categoria | Pai | Escopo | Nome de apresentação | Efeito autorizativo |
|-----------|-----|--------|---------------------|---------------------|
| `global.platform` | — | global | Plataforma | nenhum |
| `global.platform.directory` | `global.platform` | global | Diretório do sistema | nenhum |
| `global.platform.access` | `global.platform` | global | Acessos globais | nenhum |
| `global.platform.operations` | `global.platform` | global | Operações da plataforma | nenhum |
| `global.platform.commercial` | `global.platform` | global | Planos e direitos | nenhum |
| `tenant.foundation` | — | tenant | Administração da conta | nenhum |
| `tenant.foundation.access` | `tenant.foundation` | tenant | Acessos da conta | nenhum |
| `tenant.parties` | — | tenant | Pessoas e relacionamentos | nenhum |
| `tenant.financial` | — | tenant | Financeiro | nenhum |
| `tenant.financial.structure` | `tenant.financial` | tenant | Estrutura financeira | nenhum |
| `tenant.financial.operations` | `tenant.financial` | tenant | Operações financeiras | nenhum |
| `tenant.financial.control` | `tenant.financial` | tenant | Controle e auditoria | nenhum |

## Chaves globais

| Código interno | Nome i18n | Módulo proprietário | Categoria | Requisitos consumidores | Mínima global |
|----------------|-----------|----------------------|-----------|-------------------------|---------------|
| `global.directory.user.view` | Consultar usuários | system-directory-administration | `global.platform.directory` | FR-SDA-CTX-001..009, FR-SDA-DIR-001..012 | sim |
| `global.directory.account.view` | Consultar contas | system-directory-administration | `global.platform.directory` | FR-SDA-CTX-001..009, FR-SDA-DIR-001..012 | sim |
| `global.directory.identity.block` | Bloquear identidade | system-directory-administration | `global.platform.directory` | FR-SDA-USER-001..021, FR-SDA-AUD-001..009 | sim |
| `global.directory.account.intervene` | Intervir em conta | system-directory-administration | `global.platform.directory` | FR-SDA-ACC-001..012, FR-SDA-AUD-001..009 | sim |
| `global.directory.account.recover` | Recuperar administração da conta | system-directory-administration | `global.platform.directory` | FR-SDA-REC-001..029, FR-SDA-AUD-001..009 | sim |
| `global.access.catalog.view` | Consultar catálogo global | access-control | `global.platform.access` | FR-ACL-KEY-* | sim |
| `global.access.catalog.manage` | Manter catálogo global | access-control | `global.platform.access` | FR-ACL-ADM-001/006 | sim |
| `global.access.group.view` | Consultar grupos globais | access-control | `global.platform.access` | FR-ACL-GRP-* | sim |
| `global.access.group.manage` | Manter grupos globais | access-control | `global.platform.access` | FR-ACL-GRP-* | sim |
| `global.access.rule.view` | Consultar regras globais | access-control | `global.platform.access` | FR-ACL-RULE-* | sim |
| `global.access.rule.manage` | Manter regras globais | access-control | `global.platform.access` | FR-ACL-RULE-* | sim |
| `global.access.explain` | Explicar acesso global | access-control | `global.platform.access` | FR-ACL-EXP-* | sim |
| `global.platform.configuration.view` | Consultar configurações globais | platform-configuration | `global.platform.operations` | FR-PC-DEF-* | sim |
| `global.platform.configuration.manage` | Alterar configurações globais | platform-configuration | `global.platform.operations` | FR-PC-* | sim |
| `global.platform.operation.view` | Consultar operações da plataforma | platform-operations | `global.platform.operations` | FR-PO-CTX-* | sim |
| `global.platform.operation.manage` | Executar operações da plataforma | platform-operations | `global.platform.operations` | FR-PO-* | sim |
| `global.platform.provisioning.manage` | Gerir provisionamento de tenants | tenant-storage-provisioning | `global.platform.operations` | FR-TSP-ID-* | sim |
| `global.platform.audit.view` | Consultar auditoria global | tenant-data-governance | `global.platform.operations` | FR-TDG-AUD-* | sim |
| `global.platform.support.operate` | Atuar como operador de suporte | tenant-support-access | `global.platform.operations` | FR-TSA-ID-* | sim |
| `global.plan.catalog.view` | Consultar catálogo de planos | plans-entitlements | `global.platform.commercial` | FR-PE-* | sim |
| `global.plan.catalog.manage` | Manter planos e direitos | plans-entitlements | `global.platform.commercial` | FR-PE-* | sim |
| `global.plan.assignment.manage` | Manter contrato e atribuição | plans-entitlements | `global.platform.commercial` | FR-PE-ASG-001..020, FR-PE-ADM-001 | sim |

O conjunto `sim` constitui a baseline explícita do grupo global protegido versão 1. Incluir nova chave administrativa
no catálogo não altera essa baseline; sua evolução exige nova versão e migração deliberada.

## Chaves de fundação do tenant

| Código interno | Nome i18n | Módulo proprietário | Categoria | Requisitos consumidores | Mínima tenant |
|----------------|-----------|----------------------|-----------|-------------------------|---------------|
| `tenant.account.view` | Consultar conta | account-registration | `tenant.foundation` | FR-ACC-* | sim |
| `tenant.account.update` | Alterar conta | account-registration | `tenant.foundation` | FR-ACC-MAINT-001..008 | sim |
| `tenant.account.lifecycle.manage` | Gerir ciclo de vida da conta | account-registration | `tenant.foundation` | FR-ACC-STATE-* | não |
| `tenant.membership.view` | Consultar participantes | account-membership | `tenant.foundation` | FR-MEM-008, FR-MEM-LIFE-001 | sim |
| `tenant.membership.invite` | Convidar participante | account-membership | `tenant.foundation` | FR-MEM-INV-001..014, FR-MEM-ACCEPT-* | sim |
| `tenant.membership.manage` | Alterar participante | account-membership | `tenant.foundation` | FR-MEM-LIFE-002..011 | sim |
| `tenant.plan.view` | Consultar plano da conta | plans-entitlements | `tenant.foundation` | FR-PE-ADM-005, FR-PE-EVAL-001..016 | sim |
| `tenant.access.catalog.view` | Consultar catálogo da conta | access-control | `tenant.foundation.access` | FR-ACL-KEY-* | sim |
| `tenant.access.group.view` | Consultar grupos da conta | access-control | `tenant.foundation.access` | FR-ACL-GRP-* | sim |
| `tenant.access.group.manage` | Manter grupos da conta | access-control | `tenant.foundation.access` | FR-ACL-GRP-* | sim |
| `tenant.access.rule.view` | Consultar regras da conta | access-control | `tenant.foundation.access` | FR-ACL-RULE-* | sim |
| `tenant.access.rule.manage` | Manter regras da conta | access-control | `tenant.foundation.access` | FR-ACL-RULE-* | sim |
| `tenant.access.explain` | Explicar acesso da conta | access-control | `tenant.foundation.access` | FR-ACL-EXP-* | sim |
| `tenant.audit.view` | Consultar auditoria da conta | tenant-data-governance | `tenant.foundation` | FR-TDG-AUD-* | sim |

O conjunto marcado `sim` constitui a baseline explícita do grupo fundador protegido versão 1. A associação ao grupo
é criada durante o provisionamento; o papel de fundador não substitui essa associação.

## Pessoas, papéis e dados de pagamento

| Código interno | Nome i18n | Módulo proprietário | Categoria | Requisitos consumidores |
|----------------|-----------|----------------------|-----------|-------------------------|
| `tenant.party.view` | Consultar pessoas | party-registration | `tenant.parties` | FR-PTY-ID-001, FR-PTY-SEARCH-001..002, FR-PTY-SEC-002 |
| `tenant.party.create` | Cadastrar pessoa | party-registration | `tenant.parties` | FR-PTY-ID-001, FR-PTY-DUP-001..005, FR-PTY-SEC-002 |
| `tenant.party.update` | Alterar pessoa | party-registration | `tenant.parties` | FR-PTY-ID-001, FR-PTY-DOC-001..007, FR-PTY-SEC-002 |
| `tenant.party.deactivate` | Desativar pessoa | party-registration | `tenant.parties` | FR-PTY-LIFE-001..013, FR-PTY-SEC-002 |
| `tenant.party.reactivate` | Reativar pessoa | party-registration | `tenant.parties` | FR-PTY-LIFE-001..013, FR-PTY-SEC-002 |
| `tenant.party.identifier.reveal` | Revelar identificadores completos | party-registration | `tenant.parties` | FR-PTY-SEC-002, FR-PTY-SEC-004..007 |
| `tenant.party.relationship.view` | Consultar papéis e relacionamentos | party-relationships-roles | `tenant.parties` | FR-PRR-SEARCH-002..006, FR-PRR-SEC-002..003 |
| `tenant.party.relationship.assign` | Atribuir papel ou relacionamento | party-relationships-roles | `tenant.parties` | FR-PRR-ROLE-005..009, FR-PRR-SEC-002 |
| `tenant.party.relationship.update` | Alterar papel ou relacionamento | party-relationships-roles | `tenant.parties` | FR-PRR-BOUND-004, FR-PRR-SEC-002 |
| `tenant.party.relationship.end` | Encerrar papel ou relacionamento | party-relationships-roles | `tenant.parties` | FR-PRR-ROLE-009, FR-PRR-REL-011, FR-PRR-SEC-002 |
| `tenant.party.relationship.cancel` | Cancelar papel ou relacionamento | party-relationships-roles | `tenant.parties` | FR-PRR-ROLE-009, FR-PRR-REL-011, FR-PRR-SEC-002 |
| `tenant.party.payment.view` | Consultar dado de pagamento mascarado | party-payment-details | `tenant.parties` | FR-PPD-BOUND-001..008, FR-PPD-SEC-001..004 |
| `tenant.party.payment.reveal` | Revelar dado de pagamento completo | party-payment-details | `tenant.parties` | FR-PPD-SEC-002, FR-PPD-SEC-004..010 |
| `tenant.party.payment.create` | Cadastrar dado de pagamento | party-payment-details | `tenant.parties` | FR-PPD-BOUND-001..008, FR-PPD-SEC-002 |
| `tenant.party.payment.update` | Alterar dado de pagamento | party-payment-details | `tenant.parties` | FR-PPD-SEC-002, FR-PPD-SEC-008 |
| `tenant.party.payment.verify` | Verificar dado de pagamento | party-payment-details | `tenant.parties` | FR-PPD-SEC-002, FR-PPD-SEC-008 |
| `tenant.party.payment.prefer` | Definir dado preferencial | party-payment-details | `tenant.parties` | FR-PPD-SEC-002, FR-PPD-SEC-008 |
| `tenant.party.payment.deactivate` | Desativar dado de pagamento | party-payment-details | `tenant.parties` | FR-PPD-SEC-002, FR-PPD-SEC-008 |
| `tenant.party.payment.delete` | Excluir dado de pagamento | party-payment-details | `tenant.parties` | FR-PPD-SEC-002, FR-PPD-SEC-008 |

## Financeiro

| Código interno | Nome i18n | Módulo proprietário | Categoria | Requisitos consumidores |
|----------------|-----------|----------------------|-----------|-------------------------|
| `tenant.financial.account.view` | Consultar contas financeiras | financial-accounts | `tenant.financial.structure` | FR-FAC-BOUND-* |
| `tenant.financial.account.create` | Criar conta financeira | financial-accounts | `tenant.financial.structure` | FR-FAC-BOUND-001..013, FR-FAC-SEC-002 |
| `tenant.financial.account.update` | Alterar conta financeira | financial-accounts | `tenant.financial.structure` | FR-FAC-SEC-002..007 |
| `tenant.financial.account.deactivate` | Desativar conta financeira | financial-accounts | `tenant.financial.structure` | FR-FAC-SEC-002..007 |
| `tenant.financial.category.view` | Consultar categorias | financial-categories | `tenant.financial.structure` | FR-FCAT-BOUND-* |
| `tenant.financial.category.manage` | Manter categorias | financial-categories | `tenant.financial.structure` | FR-FCAT-SEC-002..005 |
| `tenant.financial.dimension.view` | Consultar dimensões | financial-dimensions | `tenant.financial.structure` | FR-FDIM-BOUND-* |
| `tenant.financial.dimension.manage` | Manter dimensões | financial-dimensions | `tenant.financial.structure` | FR-FDIM-SEC-002..005 |
| `tenant.financial.transaction.view` | Consultar lançamentos | financial-transactions | `tenant.financial.operations` | FR-FTR-BOUND-* |
| `tenant.financial.transaction.create` | Criar lançamento ou rascunho | financial-transactions | `tenant.financial.operations` | FR-FTR-BOUND-001..008, FR-FTR-SEC-002 |
| `tenant.financial.transaction.confirm` | Confirmar lançamento | financial-transactions | `tenant.financial.operations` | FR-FTR-SEC-002..005 |
| `tenant.financial.transaction.correct` | Corrigir lançamento | financial-transactions | `tenant.financial.operations` | FR-FTR-SEC-002..005 |
| `tenant.financial.transaction.cancel` | Cancelar lançamento | financial-transactions | `tenant.financial.operations` | FR-FTR-SEC-002..005 |
| `tenant.financial.transfer.view` | Consultar transferências | financial-transfers | `tenant.financial.operations` | FR-FTF-BOUND-* |
| `tenant.financial.transfer.create` | Criar transferência | financial-transfers | `tenant.financial.operations` | FR-FTF-BOUND-001..008, FR-FTF-SEC-002 |
| `tenant.financial.transfer.confirm` | Confirmar transferência | financial-transfers | `tenant.financial.operations` | FR-FTF-SEC-002..005 |
| `tenant.financial.transfer.correct` | Corrigir transferência | financial-transfers | `tenant.financial.operations` | FR-FTF-SEC-002..005 |
| `tenant.financial.transfer.cancel` | Cancelar transferência | financial-transfers | `tenant.financial.operations` | FR-FTF-SEC-002..005 |
| `tenant.financial.payable.view` | Consultar contas a pagar | accounts-payable | `tenant.financial.operations` | FR-AP-BOUND-* |
| `tenant.financial.payable.manage` | Manter contas a pagar | accounts-payable | `tenant.financial.operations` | FR-AP-SEC-002..006 |
| `tenant.financial.payable.settle` | Liquidar conta a pagar | accounts-payable | `tenant.financial.operations` | FR-AP-BOUND-004, FR-AP-SEC-002..006 |
| `tenant.financial.receivable.view` | Consultar contas a receber | accounts-receivable | `tenant.financial.operations` | FR-AR-BOUND-* |
| `tenant.financial.receivable.manage` | Manter contas a receber | accounts-receivable | `tenant.financial.operations` | FR-AR-SEC-001..003 |
| `tenant.financial.receivable.settle` | Liquidar conta a receber | accounts-receivable | `tenant.financial.operations` | FR-AR-BOUND-004, FR-AR-SEC-001..003 |
| `tenant.financial.recurrence.view` | Consultar recorrências | financial-recurrences | `tenant.financial.operations` | FR-FREC-BOUND-* |
| `tenant.financial.recurrence.manage` | Manter recorrências | financial-recurrences | `tenant.financial.operations` | FR-FREC-SEC-001..005 |
| `tenant.financial.card.view` | Consultar cartões de crédito | credit-cards | `tenant.financial.structure` | FR-CC-BOUND-* |
| `tenant.financial.card.manage` | Manter cartões de crédito | credit-cards | `tenant.financial.structure` | FR-CC-SEC-001..007 |
| `tenant.financial.closing.view` | Consultar fechamento | financial-closing-control | `tenant.financial.control` | FR-FCC-BOUND-* |
| `tenant.financial.closing.close` | Avançar data de fechamento | financial-closing-control | `tenant.financial.control` | FR-FCC-SEC-001..004 |
| `tenant.financial.closing.reopen` | Retroceder data de fechamento | financial-closing-control | `tenant.financial.control` | FR-FCC-SEC-001..005 |
| `tenant.financial.statement.import` | Importar extrato bancário | bank-statements-reconciliation | `tenant.financial.control` | FR-BSR-BOUND-001..010, FR-BSR-SEC-001 |
| `tenant.financial.reconciliation.view` | Consultar conciliação | bank-statements-reconciliation | `tenant.financial.control` | FR-BSR-SEC-001..002, FR-BSR-SEC-004..006 |
| `tenant.financial.reconciliation.manage` | Confirmar ou desfazer conciliação | bank-statements-reconciliation | `tenant.financial.control` | FR-BSR-SEC-001..005 |
| `tenant.financial.audit.view` | Consultar auditoria financeira | tenant-data-governance | `tenant.financial.control` | FR-TDG-AUD-* |

## Regras transversais de consumo

- Consulta mascarada não autoriza revelação, cópia ou exportação completa.
- Transferência confirmada exige cumulativamente acesso às contas envolvidas, à transferência e a cada lançamento
  econômico adicional produzido.
- Liquidação exige cumulativamente consulta do título, a chave `tenant.financial.payable.settle` ou
  `tenant.financial.receivable.settle` aplicável e acesso à conta financeira afetada; as duas chaves não são
  intercambiáveis.
- Importação não concede conciliação; conciliação não concede criação de lançamento sem a chave correspondente.
- A interface pode ocultar ações inviáveis, mas o serviço sempre reavalia o mesmo conjunto de chaves.
- Trabalhos assíncronos registram as chaves exigidas na aceitação e reavaliam o catálogo corrente antes de iniciar.
- Chave indisponível pelo plano continua visível somente a quem pode administrar o contexto, marcada como
  indisponível pelo plano; ela não produz permissão nem bloqueio.

## Controle de evolução

1. O módulo proprietário propõe descriptor e referências aos requisitos.
2. A revisão verifica duplicidade semântica e escopo.
3. O catálogo versionado é atualizado antes do módulo consumir a chave.
4. Grupos protegidos só recebem a nova chave por nova versão explícita de baseline.
5. Chave retirada fica inativa; seu código não é reutilizado.
