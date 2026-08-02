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

## Pendência externa

A tarefa permanece aberta. A instalação local não possui host, usuário, senha e destinatário de
um SMTP real. Falta executar um único cadastro de smoke nesse serviço e confirmar apenas o aceite
SMTP, sem declarar throughput nem entrega final na caixa postal.
