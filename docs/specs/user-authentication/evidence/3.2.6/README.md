# Evidência 3.2.6 — Login por senha e proteção contra abuso

## Escopo validado

O gate cobre os caminhos controláveis que poderiam revelar identidade ou perder atualizações entre
instâncias:

- identidade existente, ausente e identificador malformado executam uma consulta de usuário, uma
  consulta de credencial e exatamente uma comparação Argon2id;
- usuário bloqueado ou credencial comprometida compara o hash real, mas produz a mesma rejeição e
  não atualiza o hash;
- identificador e origem são registrados na ordem de lock invariável e sofrem rollback conjunto;
- duas instâncias concorrentes preservam os dois incrementos nas duas dimensões;
- a terceira falha exige Turnstile e nova falha renova os 15 minutos completos;
- política persistente, origem inválida ou Siteverify indisponível falham fechados;
- proxy não confiável não pode fornecer uma cadeia encaminhada e cadeias contraditórias são
  rejeitadas;
- token Turnstile reutilizado, hostname divergente, action divergente e timeout são rejeitados pelo
  protocolo server-side do RFW.

> [!IMPORTANT]
> A proteção reduz diferenças grosseiras criadas pelo código; ela não promete execução em tempo
> constante na JVM, na rede ou no MySQL. Medições de latência de produção pertencem ao gate da
> release e não devem transformar testes automatizados em limites frágeis de relógio.

## Execução reproduzível

Ambiente executado em 9 de agosto de 2026:

- Java 25;
- MySQL 9.7.2 local;
- schema temporário exclusivo criado e removido pelo harness de testes.

Comando:

```powershell
mvn -q "-Dtest=PasswordCredentialAuthenticationServiceTest,HumanVerificationPolicyFacadeImplTest,TrustedProxyServiceTest,RFWPasswordAuthenticationProviderAdapterTest" "-Dit.test=AuthenticationSessionRepositoryIT,TurnstileIntegrationIT" verify
```

Resultado: 35 testes executados, sem falhas, erros ou ignorados. O gate MySQL concluiu as disputas
concorrentes dentro do timeout de 10 segundos e removeu o schema temporário ao final.

## Rastreabilidade

| Tema | Prova principal |
|------|-----------------|
| Caminho observável e comprometimento | `PasswordCredentialAuthenticationServiceTest` |
| Política e indisponibilidade | `HumanVerificationPolicyFacadeImplTest` |
| Resultado público e origem indisponível | `RFWPasswordAuthenticationProviderAdapterTest` |
| Fronteira do proxy | `TrustedProxyServiceTest` |
| Limiares, renovação, rollback e concorrência | `AuthenticationSessionRepositoryIT` |
| Siteverify, replay, contexto e timeout | `TurnstileIntegrationIT` |
