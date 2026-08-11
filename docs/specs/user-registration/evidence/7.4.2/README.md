# Evidência parcial da tarefa 7.4.2

## Gate SMTP local

A tarefa 7.4.1 comprovou 100 aceitações em 11,02 segundos, contadas individualmente a partir
do commit de cada cadastro. O resultado supera o mínimo de 95 aceitações em dois minutos.

## Restauração do limite padrão

O perfil normal permanece com `rinos.origin.absolute-limit=20`. A validação isolada confirmou o
binding desse valor e, no MySQL 9.7.2, vinte reservas aceitas, bloqueio da vigésima primeira e
contador persistido em vinte.

Comando:

```powershell
mvn "-Dtest=RinosConfigurationBindingTest#bind_shouldApplyDefaultsAndExplicitList_whenOnlyRequiredValueIsProvided" "-Dit.test=IdentityRepositoryIT#reserveNewRegistration_shouldBlockTwentyFirstCreation_forSharedOrigin" verify
```

Resultado: dois testes executados, sem falhas ou erros, e `BUILD SUCCESS`.

## Siteverify externo

`TurnstileExternalIT` chama por HTTPS o endpoint real da Cloudflare com o token e as credenciais
públicas que o provedor oferece especificamente para testes automatizados. O teste comprova o
transporte e a leitura da resposta pelo adapter RFW sem armazenar uma credencial de produção.

Execução opt-in:

```powershell
mvn "-Drinos.turnstile.gate.enabled=true" "-Dit.test=TurnstileExternalIT" verify
```

Resultado: um teste executado em 1,308 segundo, sem falha ou erro, e `BUILD SUCCESS`.
As chaves e o token empregados são os valores publicados na
[documentação de testes da Cloudflare](https://developers.cloudflare.com/turnstile/troubleshooting/testing/).

> [!IMPORTANT]
> Este smoke não substitui a configuração do widget e das chaves exclusivas de produção.

## SMTP real

Em 11/08/2026 foi configurado o ambiente candidato com o Forward Email usando
`smtp.forwardemail.net:465` em SSL/TLS implícito e o remetente
`no-reply@rinos.com.br`. Foi enviado um único e-mail de smoke para
`teste@rinos.com.br`.

Resultado observado:

- autenticação e aceitação SMTP confirmadas (`SMTP_ACCEPTED`);
- mensagem recebida na caixa pessoal associada ao alias de teste;
- nenhuma senha ou segredo incluído nesta evidência.

Esta evidência comprova o smoke de SMTP real e a entrega observada pelo destinatário, mas não
declara throughput. A tarefa permanece aberta até a restauração/validação do limite padrão
de 20, do Turnstile real e dos demais critérios do gate.
