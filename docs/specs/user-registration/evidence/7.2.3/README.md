# Evidência da tarefa 7.2.3

## Origem e proxy confiável

`TrustedProxyServiceTest` comprova:

- uso do peer imediato quando não há proxy confiável;
- rejeição de `Forwarded` recebido de origem fora da allowlist;
- escolha do primeiro endereço não confiável da direita para a esquerda, ignorando prefixo
  forjado;
- concordância obrigatória entre `Forwarded` e `X-Forwarded-For`;
- rejeição de cadeias contraditórias, ausentes ou de CIDR inválido.

`OriginAddressServiceTest` cobre normalização binária e representação canônica de IPv4 e IPv6,
além da rejeição de hostnames, zonas e formatos ambíguos.

## Turnstile

`TurnstileIntegrationIT` usa um servidor Siteverify local e valida:

- sucesso somente com hostname e action esperados;
- envio de uma `idempotency_key` distinta por tentativa;
- rejeição do replay informado pelo provedor;
- distinção entre `HOSTNAME_MISMATCH` e `ACTION_MISMATCH`;
- falha fechada quando o Siteverify excede o timeout.

## Limite absoluto e retenção

Os testes de persistência no MySQL 9.7.2 comprovam:

- vinte reservas aceitas e bloqueio da vigésima primeira na mesma origem;
- contador mantido em vinte depois da tentativa bloqueada;
- outra origem ainda apta a criar sua própria janela;
- disputa inicial concorrente com uma única reserva vencedora e uma única janela ativa;
- fechamento automático da janela vencida e abertura de uma nova;
- seleção para limpeza somente depois do prazo de retenção.

Os testes unitários da limpeza também confirmam suspensão sem lease estável e processamento
independente por lotes.

## Validação focal

Comando:

```powershell
mvn "-Dtest=TrustedProxyServiceTest,OriginLimitServiceTest,OriginWindowCleanupServiceTest,OriginAddressServiceTest" "-Dit.test=TurnstileIntegrationIT,IdentityRepositoryIT#findByWindowEndsAtBefore_shouldSelectOnlyOriginWindowsOutsideRetention+reserveNewRegistration_shouldBlockTwentyFirstCreation_forSharedOrigin+reserveNewRegistration_shouldChooseOneWinner_whenFirstWindowIsConcurrent+reserveNewRegistration_shouldOpenNewWindow_whenPreviousWindowExpired" verify
```

Resultado:

```text
Unit tests: 17; failures: 0; errors: 0; skipped: 0
Integration tests: 8; failures: 0; errors: 0; skipped: 0
Database version: 9.7.2
BUILD SUCCESS
```

Após o gate, a consulta com o usuário restrito de testes confirmou zero schemas
`rinos_test_%` remanescentes.
