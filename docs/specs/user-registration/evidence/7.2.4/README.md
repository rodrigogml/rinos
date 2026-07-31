# Evidências da tarefa 7.2.4

Data da revisão: 2026-07-30

## Escopo revisado

A revisão percorreu os pontos que podem materializar ou propagar segredos e dados pessoais:

- chamadas de log e saídas diretas da aplicação;
- nomes e tags de métricas;
- eventos permanentes de auditoria;
- mensagens de exceção e representações `toString()`;
- contratos públicos e objetos internos que transportam credenciais, provas, e-mail ou endereço de
  origem;
- propriedades versionadas e arquivo local ignorado pelo Git;
- tabelas globais de identidade, comprovação, auditoria e contenção por origem;
- políticas de remoção de cadastros terminais, tombstones e janelas de origem.

## Resultado no Rinos

Foram corrigidas duas representações diagnósticas automáticas de `record`:

- `GoogleIdentityDomainResultVO` agora redige o token de continuação e o e-mail verificado;
- `NormalizedEmailVO` agora redige tanto o e-mail preservado quanto sua chave normalizada.

Os testes de regressão verificam simultaneamente a presença do marcador `REDACTED` e a ausência dos
valores originais. Os contratos públicos e os demais transportes internos sensíveis já possuíam
representações redigidas.

Logs de cadastro e SMTP contêm somente operação, resultado fechado, correlação técnica, duração e
tipo da falha. Métricas usam somente tags enumeradas e de baixa cardinalidade; e-mail, IP, token e
correlation ID não são dimensões de métrica. As exceções revisadas descrevem o contrato violado sem
incorporar o valor recebido.

O `application.properties` local permanece ignorado pelo Git. Ele e o modelo versionado não contêm
valor para senhas ou segredos.

## Persistência e retenção

O schema global persiste somente os dados necessários a cada finalidade:

- credencial local como hash Argon2id;
- provas de uso único como SHA-256, sem token bruto;
- e-mail e identidade externa somente nas tabelas funcionais de identidade;
- auditoria com relações internas, correlação binária, estados e motivos de vocabulário fechado;
- IP bruto exclusivamente em `security_originWindow`, sujeito à limpeza temporal configurada.

Eventos relacionados a cadastros pendentes são minimizados antes da remoção terminal. O tombstone
remanescente não contém usuário, cadastro, e-mail, IP ou prova e também possui retenção própria.

## Validação automatizada focada

```text
mvn -q \
  "-Dtest=SensitiveValueObjectSecurityTest,PublicContractSecurityTest,\
RegistrationObservabilityServiceTest,IdentityAuditServiceTest,\
VerificationEmailDispatchServiceTest,MaintenanceObservabilityServiceTest" \
  test

Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
```

## Gate completo

```text
mvn -q verify

Unitários: 280 executados, 0 falhas, 0 erros, 0 ignorados
Integração: 47 encontrados, 0 falhas, 0 erros, 37 ignorados
BUILD SUCCESS
```

Os testes de integração ignorados são os que dependem do provedor MySQL descartável ainda
indisponível ou de execução de navegador opt-in.

## Achado na fronteira com o RFW

> [!WARNING]
> A versão atualmente apontada do RFW Platform declara `TurnstileConfig` como `record` com o campo
> `secretKey` e sem representação diagnóstica própria. O `toString()` automático pode, portanto,
> revelar o segredo se o objeto for registrado. O Rinos não registra essa configuração, mas o risco
> pertence à biblioteca compartilhada e deve ser revalidado e corrigido no ciclo separado do RFW
> Platform 2.0 antes da liberação.

O achado foi encaminhado à tarefa `7.2.7`; nenhuma alteração foi feita no submódulo durante esta
revisão.

> [!NOTE]
> Esta tarefa é uma revisão estrutural e não depende de uma instância MySQL. Os testes integrados
> contra MySQL 9 permanecem no gate `7.1.2`.
