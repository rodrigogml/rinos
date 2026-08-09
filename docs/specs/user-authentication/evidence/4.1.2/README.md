# Evidência 4.1.2 — Enrollment TOTP protegido

## Escopo validado

- cada início cria referência e segredo individuais, revoga pendências anteriores do usuário e persiste somente o
  segredo cifrado por AEAD;
- a cifra autentica o domínio formado por usuário e referência, impedindo transportar o envelope para outro fator;
- URI `otpauth://` e segredo manual são devolvidos apenas na resposta inicial e aparecem redigidos em representações
  textuais; a geração do QR é local no renderer da RFW;
- pendências têm validade persistida, contador de tentativas e revogação por expiração, cancelamento, substituição ou
  esgotamento do limite configurável;
- a confirmação exige usuário ativo, bloqueia as linhas no banco global e só torna o fator utilizável após uma prova
  TOTP válida;
- a validação usa dígitos, período e janela da configuração oficial da RFW e identifica o passo exato aceito;
- confirmação e uso persistem `lastAcceptedStep`; passo igual ou anterior é rejeitado mesmo em outra instância;
- listagem pública contém somente referência, rótulo e datas seguras;
- o adapter da interface deriva o alvo do principal autenticado e não acessa entity ou repository;
- o schema consolidado e o update incremental `20260809_003` produzem estruturas equivalentes no MySQL 9.

## Autoridade das configurações

| Origem exclusiva | Responsabilidade |
|------------------|------------------|
| `rfw.authentication.second-factor.*` | dígitos TOTP, período, janela, tamanho do OTP por e-mail e quantidade de recovery codes |
| `rinos.authentication.mfa.*` | validade do desafio/enrollment e máximo de tentativas |
| `rinos.authentication.keyring.*` | chave ativa e versões de leitura para proteger o segredo persistido |

Não existe fallback cruzado entre essas origens. O provider concreto só é publicado quando o keyring está
explicitamente habilitado e válido.

## Concorrência e replay

A verificação adquire primeiro o lock pessimista do usuário e depois os fatores ativos em ordem estável. Duas
transações que apresentam o mesmo código no mesmo time-step são serializadas: a primeira persiste o passo e a
segunda o encontra já consumido. O teste de integração executa essa corrida com duas threads contra MySQL 9 e exige
exatamente um resultado aceito.

## Execução reproduzível

```powershell
mvn -q "-Dtest=TotpProtocolServiceTest,TotpFactorServiceTest,RFWSecondFactorManagementProviderAdapterTest,AuthenticationFactorEntityTest,AuthenticationFactorServiceTest,RinosConfigurationBindingTest,ConfigurationFileParityTest,GlobalDatabaseUpdateCatalogTest" "-Dit.test=TotpFactorRepositoryIT,AuthenticationDatabaseSchemaIT,GlobalDatabaseMigrationIT" verify
```

```powershell
mvn -q verify
```

## Rastreabilidade

| Tema | Prova principal |
|------|-----------------|
| Vetor RFC, janela exata e vínculo AEAD | `TotpProtocolServiceTest` |
| Substituição, validade, tentativas, confirmação e replay | `TotpFactorServiceTest` |
| Identidade autenticada, redação e outcomes RFW | `RFWSecondFactorManagementProviderAdapterTest` |
| Replay concorrente cross-instance | `TotpFactorRepositoryIT` |
| Paridade do schema e evolução incremental | `AuthenticationDatabaseSchemaIT`, `GlobalDatabaseMigrationIT` |
