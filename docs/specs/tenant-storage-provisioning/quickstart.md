# Cenários de Validação — Provisionamento do Armazenamento de Tenant

## Cenário 1: provisionamento novo e ativação posterior

1. Criar uma conta por `AccountCreationFacade` com uma intenção válida.
2. Confirmar conta `CREATING`, protocolo e outbox persistidos no global.
3. O dispatcher chama `TenantProvisioningRequestPort` com o protocolo e a fila cria uma única operação `PROVISION`.
4. O worker eleito reserva `physicalIdentifier`, cria o schema derivado, executa somente `db/tenant/init` e confirma
   `databaseVersion` esperada.
5. Consultar `TenantStorageReadinessPort`.
6. **Esperado**: um único schema `rinos_<interno>` existe; o consumidor recebe apenas `ready=true`; a conta ainda
   não está ativa até membership, baseline ACL e plano concluírem.

## Cenário 2: repetição e concorrência

1. Duas instâncias chamam `TenantProvisioningRequestPort` com o mesmo protocolo.
2. Ambas tentam reclamar a mesma operação.
3. **Esperado**: a constraint idempotente retorna a mesma referência; somente um claim/lock executa DDL e nenhum
   segundo identificador físico é reservado.

## Cenário 3: falha transitória no init

1. Induzir falha transitória controlada depois de `CREATE_SCHEMA` e antes de `VALIDATE_VERSION` em MySQL temporário.
2. Reiniciar o worker ou deixar o lease expirar.
3. **Esperado**: a operação e as etapas persistem; a retomada observa o schema existente, não o recria e recomeça do
   último ponto comprovado. Até três tentativas são feitas; esgotadas, o estado público é `ATTENTION` e o tenant não
   fica pronto.

## Cenário 4: migration de tenant falha isoladamente

1. Criar dois schemas de tenant na versão anterior em MySQL temporário.
2. Injetar update inválido somente no primeiro tenant.
3. Iniciar a aplicação depois da atualização global bem-sucedida.
4. **Esperado**: o primeiro tenant entra em quarentena sem retry interno; o segundo continua sua própria operação e
   fica pronto. Nenhum dos dois aceita operações comuns enquanto sua versão não for a esperada.

## Cenário 5: roundtrip backend e status humano

1. A tela Vaadin de criação envia pedido real à `AccountCreationFacade` e recebe o protocolo público.
2. A tela consulta o status pelo contrato público, sem mock ou entity.
3. Enquanto a fila executa, o status muda entre `WAITING` e `PREPARING`; depois da validação muda para `READY`.
4. **Esperado**: payload, VO e tela usam os mesmos enums; nenhuma resposta contém nome do schema, versão técnica,
   tentativa, script, host ou credencial.

## Cenário 6: conta sob migration

1. Enfileirar migration para um tenant pronto.
2. Tentar selecionar esse tenant em uma operação funcional antes, durante e depois da migration.
3. **Esperado**: antes/depois de compatível o gate permite seguir para os demais checks; durante a migration ele
   nega com estado seguro. Nenhuma autorização, plano ou cache concede exceção.
