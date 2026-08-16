# Evidência 4.3 — Bootstrap global e origem sistêmica

- `AccessBootstrapPropertiesConfig` define `rinos.access.bootstrap.administrator-email`, com padrão
  `admin@rinos.com.br`; o modelo versionado de propriedades documenta a chave sem alterar o arquivo local do ambiente.
- `GlobalAccessBootstrapService` bloqueia primeiro a revisão global e depois o singleton `access_bootstrap`. Somente uma
  identidade ativa, previamente ativada e com TOTP ou passkey administrativa pode prosseguir.
- A transação cria ou reutiliza o grupo global protegido da baseline ativa, materializa uma regra `PERMITIR` sem
  vigência para cada chave mínima, associa a identidade, grava a classificação autorizativamente neutra
  `SYSTEM_ADMINISTRATOR`, revalida a continuidade, audita e conclui permanentemente o marcador.
- O bootstrap é tentado depois da sincronização do catálogo e novamente, após commit, quando TOTP ou passkey forte é
  confirmado. Trocar a propriedade depois de `COMPLETED` não consulta nem concede acesso ao novo endereço.
- `SystemOperationContributor` e `SystemOperationDescriptor` registram origem, operação, escopo e conjunto exato de
  chaves. `SystemOperationAuthorizer` produz fontes `SYSTEM_SOURCE`, nunca consulta regras humanas e audita a finalidade.
  Origem, operação, escopo ou conjunto divergente falham de modo fechado.
- `AccessRulePersistenceIT`, no MySQL 9.7.2, cobre conclusão integral, repetição, troca posterior da propriedade e duas
  instâncias concorrentes, comprovando uma conclusão e um único grupo/vínculo/evento. Os gates de migration validam a
  atualização `20260816_001` e a versão global `20260816001`.

Ref: FR-ACL-BOOT-*; FR-ACL-AUTHZ-011; SC-ACL-017
