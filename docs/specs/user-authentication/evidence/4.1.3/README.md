# Evidência 4.1.3 — OTP por e-mail

## Escopo validado

- o código numérico imprevisível é gerado pelo protocolo `RFWOneTimeCodeService` e nunca é persistido;
- `identity_authenticationProof` guarda apenas HMAC-SHA-256, versão do keyring, validade, tentativas e estado de uso;
- o MAC vincula fluxo, fator e e-mail normalizado atual, de modo que troca do endereço invalida o código anterior;
- somente um `EMAIL_OTP` permanece aberto no fluxo; reenvio invalida atomicamente a prova anterior;
- cooldown por fluxo e janela móvel por usuário contam emissões bem-sucedidas, falhas e provas terminais;
- padrões operacionais: validade de 5 minutos limitada pelo fluxo, 5 tentativas, cooldown de 1 minuto e 3 emissões
  em 15 minutos;
- o template `authentication-email-code` recebe somente código e expiração e é materializado depois do commit;
- a fachada só devolve `EMITTED` após aceitação SMTP; falha de template/transporte ou rollback não afirma entrega;
- falha posterior ao commit invalida em nova transação somente o digest que originou aquele envio, preservando
  reenvio concorrente mais novo;
- destinatário completo, código, MAC, corpo SMTP e falha detalhada não entram em resposta, log ou métrica;
- consumo e tentativas são serializados por usuário, fluxo e prova; o mesmo código possui um único vencedor entre
  instâncias.

## Reutilização do modelo

Não foi criada tabela nem migração. A entidade `AuthenticationProof`, preparada na fase de fundação, já oferece FK
do fluxo, unicidade da prova aberta, digest versionado, contador, expiração e consumo único. `EmailFactor` continua
representando a escolha do e-mail confirmado como fator; o endereço é sempre relido da identidade.

## Execução reproduzível

```powershell
mvn -q "-Dtest=EmailOtpServiceTest,EmailOtpDispatchServiceTest,EmailOtpFacadeImplTest,AuthenticationProofServiceTest,RinosConfigurationBindingTest,ConfigurationFileParityTest" "-Dit.test=EmailOtpRepositoryIT" verify
```

```powershell
mvn -q verify
```

## Rastreabilidade

| Tema | Prova principal |
|------|-----------------|
| MAC versionado, limite de tentativas e compensação exata | `AuthenticationProofServiceTest` |
| Emissão, vínculo, cooldown, janela e redação | `EmailOtpServiceTest` |
| Commit, rollback, template, SMTP e ausência de vazamento | `EmailOtpDispatchServiceTest` |
| Outcome público somente após entrega | `EmailOtpFacadeImplTest` |
| Consumo único cross-instance no MySQL 9 | `EmailOtpRepositoryIT` |
