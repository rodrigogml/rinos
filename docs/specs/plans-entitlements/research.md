# Research — Planos, contratos e direitos

## Decisões

### Dois escopos, uma fundação

`PERSONAL` e `TENANT` compartilham catálogo, versionamento, avaliação e auditoria, mas nunca titular, plano padrão,
atribuição, consumo ou billing. Duplicar toda a engine criaria semânticas divergentes; usar um proprietário polimórfico sem
escopo explícito enfraqueceria isolamento e integridade.

### Contrato não é atribuição

`ServiceContract` é a fronteira durável do titular e do billing futuro. `PlanAssignment` é histórico temporal dentro do
contrato. Troca de plano encerra uma atribuição e cria outra, sem trocar a identidade do contrato.

### Catálogo tipado

Plano, versão e definição carregam `ContractScope`. O código `FREE` é único dentro do escopo, formando as identidades
`PERSONAL/FREE` e `TENANT/FREE`. Há exatamente um padrão publicado por escopo.

### Direito de acesso declara o titular

Contexto global é ambíguo: pode representar operação pessoal ou administração da plataforma. Por isso um gate usa
`EntitlementRequirement(subjectScope, entitlementCode)`. A ausência do requisito significa que a operação não é
condicionada a plano; não se infere `PERSONAL` de chave global.

### Limite inicial do tenant

`membership.associated-users.limit` é `MAXIMUM_QUANTITY`, mede identidades distintas que já ocuparam associação e vale
`10` em `TENANT/FREE`. Estados posteriores não liberam vaga. Convite pendente reserva vaga; expiração ou revogação antes
do aceite libera a reserva; aceite converte a reserva em ocupação.

Contar linhas de membership seria incorreto porque uma identidade pode possuir histórico repetido. A autoridade será o
registro idempotente de ocupação por `(tenantId, userId)` mais reservas válidas ainda não vinculadas a uma identidade.

### Concorrência

Pré-checagem serve somente à experiência. A decisão autoritativa bloqueia o agregado de capacidade do contrato, elimina
reservas expiradas aplicáveis, contabiliza ocupações e reservas distintas e persiste o novo efeito na mesma transação.
Processos em várias instâncias dependem do lock do banco, não de cache ou sessão.

### Cache

Composição publicada é imutável e pode ser armazenada em cache por `planVersionId`. Contrato e atribuição são lidos em
tempo de execução no primeiro slice. Cache futuro deve usar revisão do contrato e invalidação por outbox; falha sempre
fecha o acesso.

### Bootstrap e backfill

A ativação de identidade exige `PERSONAL/FREE`; a ativação de tenant exige `TENANT/FREE`. Antes de habilitar as
restrições, um backfill idempotente cria contratos para titulares existentes sem alterar contratos válidos. O fundador
ocupa uma das dez vagas.

### Dados de uso

Catálogo, contratos, atribuições e reservas de controle ficam no global. Dados detalhados de tenant permanecem no tenant.
O armazenamento detalhado de uso pessoal será definido pelo primeiro módulo pessoal; este slice publica a fronteira e
não inventa um data plane pessoal.

### RFW e interface

A interface será planejada sobre as APIs públicas da RFW após leitura e análise de lacunas exigidas pelo projeto. Este
slice não altera o submódulo e não implementa telas.
