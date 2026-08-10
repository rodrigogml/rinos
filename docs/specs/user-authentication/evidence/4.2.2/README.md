# Evidência 4.2.2 — Configuração WebAuthn explícita

## Resultado

O Rinos passou a usar somente `rfw.authentication.passkey.*`, o mesmo grupo efetivamente consumido pelo RFW e pelo
Spring Security. O grupo redundante `rinos.authentication.webauthn.*` e seu binder foram removidos.

O RFW aplica `user-verification` às opções WebAuthn de cadastro e autenticação. A plataforma mantém `preferred` como
padrão compatível para outras aplicações; o Rinos declara e valida obrigatoriamente `required`.

## Invariantes de inicialização

- RP ID, nome do RP e ao menos uma origin são obrigatórios quando passkeys estiverem habilitadas;
- origins remotas exigem HTTPS; apenas `localhost` admite HTTP;
- origin não aceita caminho além de `/`, credenciais, query ou fragmento;
- o host da origin deve ser igual ou subordinado ao RP ID;
- produção (`app.rinos.com.br`) e desenvolvimento (`localhost`) usam configurações distintas;
- configuração que não exigir verificação local impede a inicialização.

## Validação reproduzível

No RFW:

```powershell
mvn test
```

Resultado observado: 457 testes, sem falhas, erros ou testes ignorados.

No Rinos:

```powershell
mvn verify
```

Resultado observado: 559 testes unitários e 100 testes de integração, sem falhas ou erros; 15 integrações externas
opcionais foram ignoradas. O teste focado cobre o perfil local válido, verificação local insuficiente, origin fora do
RP e HTTP remoto.

## Rastreabilidade

| Requisito | Evidência |
|---|---|
| Origem exclusiva por definição | `application.properties.model` e remoção de `AuthenticationWebAuthnPropertiesConfig` |
| RP ID e origins coerentes | `AuthenticationProtocolPropertiesValidatorConfigTest` |
| Verificação local obrigatória | configuração `required`, validação do Rinos e opções produzidas pelo RFW |
| Aplicação em cadastro e login | `RFWWebAuthnRelyingPartyOperationsConfigTest` no submódulo |
