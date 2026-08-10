# Evidência 4.1.6 — Matriz de segurança dos fatores adicionais

## Escopo validado

Esta tarefa consolida as garantias dos protocolos entregues em 4.1.1–4.1.5. Os testes permanecem próximos da classe
ou fronteira que observam; este documento evita criar uma suíte paralela que duplicaria fixtures e poderia divergir
dos contratos reais.

| Garantia | Teste principal | Evidência |
|----------|-----------------|-----------|
| RFC 6238 SHA-1 | `TotpProtocolServiceTest` | seis instantes oficiais, adaptando os oito dígitos do RFC aos seis dígitos configurados e conferindo o passo exato |
| Janela e replay TOTP | `TotpFactorServiceTest`, `TotpFactorRepositoryIT` | expiração do enrollment, limite de confirmação e um único vencedor MySQL no mesmo passo |
| Rotação de segredo TOTP | `AuthenticationKeyringServiceTest`, `TotpProtocolServiceTest` | segredo cifrado com versão anterior continua legível enquanto a chave está no keyring; contexto divergente falha fechado |
| Rotação do MAC de OTP | `AuthenticationKeyringServiceTest`, `AuthenticationProofServiceTest` | prova conserva `keyVersion`, aceita chave anterior mantida e rejeita versão removida ou domínio divergente |
| Expiração e limites do OTP | `EmailOtpServiceTest`, `AuthenticationProofServiceTest` | validade limitada pelo fluxo, cooldown, janela de emissão e máximo de tentativas |
| Concorrência do OTP | `EmailOtpRepositoryIT` | duas transações concorrentes produzem um `USED` e um `STALE` |
| Apresentação única TOTP | `TotpProtocolServiceTest`, `TotpFactorServiceTest` | segredo/URI existem apenas no retorno inicial e não aparecem na representação textual nem na consulta posterior |
| Apresentação única recovery | `RecoveryCodeServiceTest`, `RecoveryCodeManagementFacadeImplTest` | exatamente dez valores transitórios, sem consulta de reapresentação e com `toString()` redigido |
| Concorrência recovery | `AuthenticationFactorRepositoryIT` | duas transações concorrentes consomem o mesmo código uma única vez e deixam nove disponíveis |
| Seleção e limite compartilhado | `SecondFactorServiceTest`, `AuthenticationFlowServiceTest` | fator stale não é consumido, falhas não são contadas duas vezes e o fluxo é invalidado no máximo configurado |
| Borda RFW sem envio automático | `RFWSecondFactorProviderAdapterTest` | somente e-mail declara emissão; TOTP/recovery não disparam envio e provas/referências são redigidas |

## Vetores RFC 6238

O Rinos usa período de 30 segundos, HMAC-SHA-1 e seis dígitos. O RFC 6238 publica valores de oito dígitos; para a
configuração efetiva, o teste usa os seis dígitos finais e exige que o serviço devolva o passo temporal exato. Isso
também comprova que a tolerância configurada não mascara qual passo foi efetivamente aceito.

| Epoch (s) | RFC com 8 dígitos | Código efetivo com 6 dígitos | Passo esperado |
|-----------|-------------------|------------------------------|----------------|
| 59 | `94287082` | `287082` | 1 |
| 1111111109 | `07081804` | `081804` | 37037036 |
| 1111111111 | `14050471` | `050471` | 37037037 |
| 1234567890 | `89005924` | `005924` | 41152263 |
| 2000000000 | `69279037` | `279037` | 66666666 |
| 20000000000 | `65353130` | `353130` | 666666666 |

## Execução reproduzível

```powershell
mvn -q "-Dtest=TotpProtocolServiceTest,TotpFactorServiceTest,AuthenticationKeyringServiceTest,AuthenticationProofServiceTest,EmailOtpServiceTest,RecoveryCodeServiceTest,RecoveryCodeManagementFacadeImplTest,SecondFactorServiceTest,AuthenticationFlowServiceTest,RFWSecondFactorProviderAdapterTest" "-Dit.test=TotpFactorRepositoryIT,EmailOtpRepositoryIT,AuthenticationFactorRepositoryIT" verify
```

```powershell
mvn -q verify
```

Os testes `*IT` usam exclusivamente o schema temporário criado pelo suporte local de testes MySQL; não executam
contra o schema global de desenvolvimento nem conservam dados entre execuções.
