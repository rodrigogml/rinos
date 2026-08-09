# Evidência 3.3.6 — Robustez da sessão persistente

## Escopo validado

- a publicação interativa e a restauração persistente passam pela estratégia contra session fixation do RFW;
- uma sessão HTTP preexistente recebe novo identificador antes de armazenar o contexto autenticado;
- a perda da sessão local/Vaadin é recuperada a partir de um único cookie persistente válido;
- a restauração rotaciona o validator, cria um novo contexto HTTP e conserva a mesma sessão global;
- o validator anterior não pode ser reutilizado e seu replay revoga também a credencial recém-rotacionada;
- o limite inativo expira exatamente na fronteira configurada;
- atividade periódica renova o limite inativo, mas nunca ultrapassa o limite absoluto;
- a expiração absoluta ocorre exatamente na fronteira, mesmo após renovações periódicas;
- cookies definitivos inválidos, expirados, revogados, bloqueados ou com replay são removidos pelo filtro.

> [!IMPORTANT]
> “Perda da instância Vaadin” significa ausência do estado local associado à sessão HTTP. O registro global no MySQL
> permanece a autoridade; a reconstrução só acontece quando o navegador apresenta o cookie persistente válido. Não
> existe promessa de recuperação de sessão normal sem “lembrar-me”.

## Execução reproduzível

```powershell
mvn -q "-Dtest=PersistentSessionResilienceTest,PersistentLoginFacadeImplTest,RFWPersistentLoginProviderAdapterTest" "-Dit.test=AuthenticationSessionRepositoryIT" verify
```

Ambiente: Java 25, MySQL 9.7.2 e schema temporário exclusivo removido pelo harness.

## Rastreabilidade

| Tema | Prova principal |
|------|-----------------|
| Session fixation na restauração | `PersistentSessionResilienceTest` |
| Perda do estado local e reconstrução | `PersistentSessionResilienceTest` |
| Cookie endurecido e publicação da rotação | `PersistentLoginFacadeImplTest` |
| Tradução segura entre Rinos e RFW | `RFWPersistentLoginProviderAdapterTest` |
| Expiração inativa e absoluta | `AuthenticationSessionRepositoryIT` |
| Rotação, replay e revogação do sucessor | `AuthenticationSessionRepositoryIT` |
| Limpeza do cookie em estados terminais | `RFWPersistentLoginAuthenticationFilterTest`, no RFW Platform |
