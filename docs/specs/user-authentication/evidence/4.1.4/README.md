# Evidência 4.1.4 — Códigos de recuperação

## Escopo validado

- o protocolo `RFWRecoveryCodeService` gera exatamente 10 códigos legíveis e distintos;
- o Rinos falha no startup se `rfw.authentication.second-factor.recovery-code-count` divergir de 10;
- cada código recebe uma chamada independente ao encoder Argon2id efetivo da aplicação;
- o banco global recebe somente os hashes e ordinais não secretos; valores legíveis não entram em entity, log ou
  auditoria;
- a apresentação completa existe somente no retorno da geração e possui representação textual redigida;
- não existe operação de leitura capaz de recuperar ou reapresentar os códigos;
- novo conjunto invalida atomicamente os códigos disponíveis e o conjunto ativo anterior;
- consumo bloqueia usuário, conjunto e códigos, aceita cada código uma única vez e fecha o conjunto no último uso;
- duas instâncias concorrentes tentando consumir o mesmo código produzem exatamente um uso aceito;
- geração/substituição exige usuário ativo e registra alteração de método sem material secreto.

## Reutilização do modelo e da RFW

Não foi criada migração. `identity_recoveryCodeSet` e `identity_recoveryCode`, entregues na fase de fundação, já
contêm unicidade do conjunto ativo, estado individual e constraints necessárias. O conjunto é global e pertence ao
usuário; não recebe FK para um fluxo temporário. A associação do consumo ao fluxo será responsabilidade do
orquestrador da tarefa 4.1.5.

A RFW já fornece geração criptograficamente aleatória, normalização, hashing pelo encoder da aplicação e renderer de
apresentação única. O Rinos apenas aplica sua quantidade fixa, persiste os hashes e publica uma fachada sanitizada;
nenhuma alteração no submódulo foi necessária.

## Execução reproduzível

```powershell
mvn -q "-Dtest=RecoveryCodeServiceTest,RecoveryCodeManagementFacadeImplTest,AuthenticationProtocolPropertiesValidatorConfigTest" "-Dit.test=AuthenticationFactorRepositoryIT" verify
```

```powershell
mvn -q verify
```

## Rastreabilidade

| Tema | Prova principal |
|------|-----------------|
| Geração, hashes independentes, substituição e redação | `RecoveryCodeServiceTest` |
| Contrato público sem reapresentação ou vazamento | `RecoveryCodeManagementFacadeImplTest` |
| Quantidade fixa validada no startup | `AuthenticationProtocolPropertiesValidatorConfigTest` |
| Consumo único cross-instance e hashes reais no MySQL 9 | `AuthenticationFactorRepositoryIT` |
