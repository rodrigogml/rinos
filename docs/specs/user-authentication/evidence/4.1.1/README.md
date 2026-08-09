# Evidência 4.1.1 — Keyring AEAD/MAC versionado

## Escopo validado

- o keyring tem origem exclusiva em `application.properties`, com uma versão ativa para escrita e versões anteriores
  para leitura durante rotação;
- cada chave usa Base64 canônico, possui ao menos 256 bits e não pode repetir material de outra versão;
- configuração habilitada sem chave ativa utilizável, Base64 válido ou tamanho mínimo interrompe o startup;
- AES-256-GCM usa subchave derivada; HMAC-SHA-256 preserva o formato anterior para que a centralização do keyring não
  invalide digests persistidos sob a mesma versão de chave;
- MAC inclui domínio, escreve pela chave ativa e compara em tempo constante com a versão persistida;
- AEAD usa AES-256-GCM, nonce aleatório de 96 bits, tag de 128 bits e autentica domínio e versão;
- ciphertext, nonce e versão cabem nos campos já existentes de `identity_totpFactor`; não houve mudança de schema;
- adulteração de ciphertext, troca de domínio e versão ausente falham fechadas;
- VOs e configuração usam cópias defensivas e redigem chave, MAC, nonce e ciphertext em diagnósticos;
- o keyring desabilitado não executa silenciosamente MAC ou cifra: seus serviços falham fechados quando chamados.
- operações que atualizam as janelas de identificador e origem adquirem locks na ordem binária do índice, validada
  contra concorrência real no MySQL mesmo quando a ordem difere dos papéis semânticos.

## Operação de rotação

1. adicionar a nova entrada `rinos.authentication.keyring.keys.<versão>` em todas as instâncias;
2. manter a versão ativa anterior e reiniciar/validar todas as instâncias com o mesmo conjunto;
3. alterar `active-version` para a nova versão em todas as instâncias e reiniciar;
4. novos valores passam a usar a nova versão; valores antigos continuam legíveis pela versão gravada;
5. remover uma chave antiga somente depois que um ciclo futuro de recifra confirmar que nenhum registro a referencia.

> [!CAUTION]
> Remover prematuramente uma versão torna os fatores TOTP correspondentes indisponíveis. O código não tenta outra
> chave nem ignora a tag, pois isso esconderia erro operacional e enfraqueceria a autenticação do envelope.

## Execução reproduzível

```powershell
mvn -q "-Dtest=AuthenticationKeyringServiceTest,AuthenticationKeyringMacServiceTest,RinosConfigurationBindingTest,ConfigurationFileParityTest" test
```

```powershell
mvn -q verify
```

## Rastreabilidade

| Tema | Prova principal |
|------|-----------------|
| Binding e falha de startup | `RinosConfigurationBindingTest` |
| Paridade do arquivo local/modelo | `ConfigurationFileParityTest` |
| Rotação MAC e comparação por versão | `AuthenticationKeyringServiceTest` |
| Nonce, roundtrip e leitura de chave anterior | `AuthenticationKeyringServiceTest` |
| Adulteração, domínio e versão ausente | `AuthenticationKeyringServiceTest` |
| Compatibilidade da fachada antifraude | `AuthenticationKeyringMacServiceTest`, `AuthenticationSessionRepositoryIT` |
