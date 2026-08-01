# Evidência da tarefa 6.3.5

Data da validação: 2026-08-01

## Escopo

Os estados adversos da resolução e da conclusão do cadastro Google foram validados desde a decisão de domínio até o
contrato entregue ao RFW. O comportamento de produção já possuía as decisões necessárias; esta tarefa completou a
cobertura automatizada das traduções públicas e consolidou sua evidência.

Nenhum cenário rejeitado publica principal ou autenticação. As respostas não expõem e-mail, `issuer`, `subject`,
token, referência opaca, identidade relacionada nem diagnóstico interno.

## Matriz de estados

| Situação | Decisão pública | Resultado entregue ao RFW | Efeito permitido |
|----------|-----------------|----------------------------|------------------|
| E-mail de usuário ativo já existente | `EXISTING_USER_REAUTHENTICATION_REQUIRED` | `registration.google.existing-user-reauthentication-required` | Orientar autenticação futura; não vincular Google |
| `issuer + subject` conflitante ou pendência incompatível | `EXTERNAL_IDENTITY_CONFLICT` ou `CONFLICT` | `registration.google.identity-conflict` no início; `registration.google.completion.conflict` na conclusão | Reiniciar o Google sem criar sessão |
| Referência de continuação expirada | `EXPIRED_REFERENCE` | `registration.google.completion.expired-reference` | Reiniciar o Google sem reutilizar a referência |
| Resolução ou conclusão indisponível | `UNAVAILABLE` | `registration.google.unavailable` | Tentar novamente ou usar cadastro local |
| Documento aceito deixou de ser vigente | `VALIDATION_REJECTED` | campo `acceptedLegalDocumentIds` com `registration.error.legal-documents` | Apresentar as versões atuais e exigir nova decisão |

## Garantias por camada

### Identidade existente e conflito

`GoogleIdentityResolutionServiceTest` comprova que um usuário ativo não recebe vínculo automático e que conflitos da
chave externa estável ou da pendência não emitem continuação. `RFWExternalIdentityResolverAdapterTest` comprova que
essas decisões se tornam rejeições públicas distintas e sem autenticação.

### Expiração e indisponibilidade

`ExternalRegistrationCompletionServiceTest` diferencia referência expirada de referência inválida ou já consumida e
não executa remoção de credencial nem ativação. `RFWExternalRegistrationProviderAdapterTest` comprova as mensagens
específicas de expiração, conflito e indisponibilidade, sempre sem principal autenticado. A indisponibilidade durante a
resolução continua coberta pela facade e pelo resolver, permitindo retornar ao cadastro local.

### Documento jurídico alterado

`ExternalRegistrationCompletionServiceTest` comprova que a validação das versões vigentes ocorre antes do consumo da
prova, do registro de efeitos de ativação, da remoção da credencial local e da ativação do vínculo Google.
`ExternalRegistrationFacadeImplTest` comprova que a mudança é reduzida a um erro recuperável no campo de documentos,
sem transportar a exceção interna. O adapter mantém esse erro por campo para o renderer público do RFW.

## Validação automatizada

Linha de base antes de completar a cobertura:

```powershell
mvn "-Dtest=GoogleIdentityResolutionServiceTest,GoogleIdentityResolutionFacadeImplTest,RFWExternalIdentityResolverAdapterTest,ExternalRegistrationCompletionServiceTest,ExternalRegistrationFacadeImplTest,RFWExternalRegistrationProviderAdapterTest" test
```

```text
Tests run: 26, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Mesmo conjunto depois da cobertura:

```text
Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Gate completo:

```powershell
mvn verify
```

Resultado com Java 25 e MySQL 9.7.2:

```text
Testes unitários:      299; 0 falhas; 0 erros; 0 ignorados
Testes de integração:   49; 0 falhas; 0 erros; 4 E2E de navegador opt-in
BUILD SUCCESS
```

Os quatro E2E ignorados são jornadas de navegador opt-in já existentes. Navegação por teclado, leitor de tela,
responsividade, localização e inspeção visual pertencem às tarefas 6.3.6 e 6.3.7.

## Conclusão

A tarefa 6.3.5 está concluída. Identidade existente, conflito, expiração, indisponibilidade e mudança jurídica são
rejeitados de forma específica, recuperável quando aplicável e sem autenticação ou ativação parcial.
