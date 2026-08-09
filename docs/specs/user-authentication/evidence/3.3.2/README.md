# Evidência 3.3.2 — Cookie persistente opaco

## Escopo validado

O ciclo implementado comprova que:

- o RFW solicita a emissão somente após publicar a sessão global e apenas para “lembrar-me”;
- a sessão `ACTIVE` recebe conjuntamente um selector e um validator novos;
- somente o SHA-256 de cada parte permanece em `identity_authSession`;
- o valor bruto atravessa apenas a fachada HTTP que grava o `Set-Cookie`;
- uma restauração válida rotaciona o validator na mesma transação que valida a sessão;
- reutilizar o cookie anterior revoga a sessão e produz `REPLAY_DETECTED`;
- cookie duplicado, vazio, malformado ou desconhecido não autentica;
- falha transitória devolve `UNAVAILABLE`, preserva o cookie e não cria contexto parcial;
- `Set-Cookie` usa `Path=/`, `HttpOnly`, `SameSite=Strict`, a política `Secure` configurada e validade que nunca
  ultrapassa o vencimento absoluto da sessão.

> [!IMPORTANT]
> A referência pública da sessão não autentica. Ela identifica gestão e liga o principal local ao guard global;
> não substitui selector nem validator.

## Execução reproduzível

Ambiente executado em 9 de agosto de 2026:

- Java 25;
- MySQL 9.7.2 local;
- schema temporário exclusivo criado e removido pelo harness.

Comandos:

```powershell
mvn -q "-Dtest=PersistentLoginFacadeImplTest,RFWPersistentLoginProviderAdapterTest,SensitiveValueObjectSecurityTest" test
mvn -q "-Dit.test=AuthenticationSessionRepositoryIT" verify
```

Resultado: testes unitários e gate MySQL concluídos sem falhas. O segundo comando também executou a suíte
unitária vigente antes da integração.

## Rastreabilidade

| Tema | Prova principal |
|------|-----------------|
| Atributos, expiração e duplicidade do cookie | `PersistentLoginFacadeImplTest` |
| Mapeamento RFW e indisponibilidade fechada | `RFWPersistentLoginProviderAdapterTest` |
| Redação de valores sensíveis | `SensitiveValueObjectSecurityTest` e `PublicContractSecurityTest` |
| Persistência, ordem do lifecycle, rotação e replay | `AuthenticationSessionRepositoryIT` |
