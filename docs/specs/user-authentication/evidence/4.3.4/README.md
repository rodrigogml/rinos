# Evidências da tarefa 4.3.4

## Escopo comprovado

- vínculo Google somente depois da validação técnica, confirmação explícita e reautenticação recente;
- chave de domínio reduzida a `provider + issuer + subject`, sem e-mail, token ou claims completos;
- unicidade definitiva de `(issuer, subject)` no MySQL e conflito público neutro em corrida;
- referência UUID opaca separada do ID interno, issuer e subject;
- desvínculo por estado `REVOKED`, com auditoria e bloqueio do último método utilizável;
- reativação permitida somente para o mesmo proprietário histórico;
- listagem limitada ao principal e à sessão corrente.

## Evolução isolada do RFW

A revisão `4971e679d56a78c8bc4b73fb436dee84f147ca8f` acrescenta a operação
`link-external-identity`, uma confirmação explícita após a validação Google e a reautenticação antes de chamar
`RFWExternalIdentityManagementProvider.linkOutcome(...)`. Cancelamento não possui efeito de domínio. O ciclo atualizou
teste de componente, seis bundles da biblioteca e as seis variantes do documento de configurações de segurança no
showroom.

Validação executada no repositório RFW:

```text
mvn -q -Dtest=RFWSecuritySettingsComponentTest test
Exit code: 0

mvn -q verify
Exit code: 0
```

## Persistência e roundtrip MySQL

O update global `20260810_001_update.sql` acrescenta `reference BINARY(16)`, `revokedAt` e a constraint
`uk_identity_external_identity_reference`. O init consolidado produz o mesmo schema e a view
`databaseVersion=20260810001`. O teste de migration também comprova atualização desde o marco anterior, não
reaplicação quando atual e interrupção sem avanço de versão diante de falha parcial.

```text
mvn -q -DskipUnitTests '-Dit.test=GlobalDatabaseMigrationIT,AuthenticationDatabaseSchemaIT' verify
Exit code: 0
```

## Testes de domínio e adapter

`ExternalIdentityManagementServiceTest`, `ExternalIdentityManagementFacadeImplTest` e
`RFWExternalIdentityManagementProviderAdapterTest` cobrem confirmação obrigatória, minimização dos dados, conflito
entre proprietários, tradução da corrida de unicidade, referência opaca, último método, revogação seletiva e ausência
de principal autenticado. A regressão unitária completa também foi executada:

```text
mvn -q -DskipITs test
Exit code: 0
```

O gate final foi executado sobre o conjunto consolidado, incluindo testes unitários, integração MySQL, contexto
Spring, empacotamento e jornadas Vaadin:

```text
mvn -q verify
Exit code: 0
```
