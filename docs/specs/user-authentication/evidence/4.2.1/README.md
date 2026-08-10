# Evidência 4.2.1 — Repositories persistentes do Spring WebAuthn

## Escopo validado

- `PublicKeyCredentialUserEntityRepository` usa o usuário global ativo e um `userHandle` binário aleatório, estável
  e distinto do e-mail e do ID sequencial;
- o adapter não cria identidade, não troca handle já associado e rejeita colisão com outra identidade;
- `UserCredentialRepository` converte integralmente `CredentialRecord` para as colunas explícitas de
  `identity_passkeyCredential`, sem blob serializado do framework;
- lookup por credential ID e por user handle publica somente credential `ACTIVE` de usuário `ACTIVE`;
- saves posteriores não podem substituir owner, credential ID, chave pública, user-verification inicial,
  backup-eligibility, transports, attestation ou label;
- somente `signatureCount`, `backupState` e `lastUsedAt` podem avançar durante uma assertion validada;
- exclusões técnicas falham fechado e não apagam usuário, owner ou credential; revogação lógica permanece no caso de
  uso de gestão;
- o roundtrip real no MySQL 9 preserva os campos e atualiza uma única linha de owner/credential.

## Modelo reutilizado

Não foi criada migration. `identity_passkeyUser` e `identity_passkeyCredential`, entregues na fundação da feature,
já possuem handles/IDs binários únicos, chave COSE, contador, flags, transports, attestation, nome, datas, estado e
versão otimista. Foram acrescentadas apenas queries JPA bloqueáveis e por estado necessárias aos contratos Spring.

O protocolo continua pertencendo ao `spring-security-webauthn` configurado pelo RFW. Os adapters recebem apenas
`PublicKeyCredentialUserEntity` e `CredentialRecord` depois da validação do framework; nenhuma assertion, chave
privada, PIN ou biometria é persistida pelo domínio. Nenhuma alteração no submódulo RFW foi necessária.

## Execução reproduzível

```powershell
mvn -q "-Dtest=SpringWebAuthnUserRepositoryAdapterTest,SpringWebAuthnCredentialRepositoryAdapterTest" "-Dit.test=SpringWebAuthnRepositoryAdapterIT" verify
```

```powershell
mvn -q verify
```

## Rastreabilidade

| Tema | Prova principal |
|------|-----------------|
| Normalização, handle estável e ausência de criação/exclusão da identidade | `SpringWebAuthnUserRepositoryAdapterTest` |
| Conversão completa e barreira sobre material imutável | `SpringWebAuthnCredentialRepositoryAdapterTest` |
| Roundtrip binário e atualização controlada no MySQL 9 | `SpringWebAuthnRepositoryAdapterIT` |
