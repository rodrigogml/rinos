# Recorte de release: recuperação mínima de senha

## Finalidade

Este recorte implementa a dependência explícita de release do `user-registration`: um usuário ativo que já possui
credencial local pode solicitar uma prova por e-mail e definir uma nova senha. Ele não declara concluída a feature
ampla `user-authentication` nem substitui o futuro plano técnico dessa feature.

## Contrato entregue

- A solicitação pública sempre responde de forma neutra, exista ou não a identidade informada.
- Somente usuário `ACTIVE` com credencial local `ACTIVE` recebe a mensagem de recuperação.
- O RFW exige Turnstile na solicitação e entrega os estados `RECOVERY_REQUEST` e `PASSWORD_RESET`.
- A prova possui 256 bits de entropia, mas somente seu SHA-256 é persistido em `identity_passwordRecovery`.
- A prova vale uma hora, é vinculada ao usuário, só pode ser consumida uma vez e uma nova emissão invalida as
  anteriores.
- Emissão é limitada por origem e usuário; tentativas são limitadas por origem. Os limites pertencem exclusivamente
  ao `application.properties` sob `rinos.password-recovery.*`.
- A nova senha usa a mesma política, verificação k-anônima no HIBP e Argon2id do cadastro.
- A troca da credencial, o consumo da prova e a invalidação das demais provas abertas ocorrem na mesma transação.
- Emissão e conclusão produzem auditoria sem e-mail, IP, token, hash ou senha.
- Provas encerradas são removidas pelo catálogo global de manutenção após a retenção configurada.

## Interface e URL externa

O Rinos registra `RFWPasswordRecoveryProviderAdapter`; a presença desse provider ativa automaticamente a ação de
recuperação no componente de acesso do RFW. Quando a ação parte do erro de e-mail já cadastrado, o renderer público do
RFW transfere esse e-mail para o campo de recuperação, sem colocá-lo na URL.

O e-mail aponta para:

```text
/login?step=password-reset&proof=<prova-opaca>
```

`LoginView` aceita somente uma intenção conhecida e uma única prova limitada a 512 caracteres. Depois de entregá-la
ao estado efêmero do RFW, substitui a localização visível do navegador por `/login`.

## Limites deste recorte

Permanecem no backlog completo de `user-authentication`: sessões persistentes e sua revogação concreta, recuperação
de 2FA, recovery codes, orientação por e-mail para identidades exclusivamente externas e demais métodos de acesso.
Enquanto não existem sessões persistentes no Rinos, não há sessão secundária a revogar; a atualização da credencial
é o marco que a futura implementação de sessões deverá observar.

> [!IMPORTANT]
> Este documento permite liberar `user-registration`, não liberar toda a autenticação. Qualquer ampliação deve passar
> pelo planejamento próprio de `user-authentication`.
