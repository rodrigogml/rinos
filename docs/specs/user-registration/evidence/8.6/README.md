# Evidência da tarefa 8.6 — enrollment TOTP do fundador

Data: 2026-09-01.

## Entrega

A ativação local ou Google agora emite uma continuação autenticável, curta e opaca,
somente depois de concluir o lifecycle de identidade. A continuação declara a origem
real da credencial (`PASSWORD` para cadastro local e `GOOGLE` para cadastro externo)
e dura apenas a janela de reautenticação configurada. Ela não usa a prova de e-mail
como fator de autenticação.

Quando a identidade ativa corresponde ao e-mail de fundador configurado e não possui
um fator TOTP ativo, o guard global do Vaadin bloqueia toda jornada autenticada e a
redireciona para as configurações de segurança. Nessa condição, a composição pública
da RFW Platform deixa habilitada somente a seção de segundo fator; senha, passkeys,
identidades externas, sessões e códigos de recuperação não ficam disponíveis.

A decisão é reavaliada contra o estado durável do usuário e dos fatores a cada
navegação. O e-mail configurado não é devolvido pelo contrato da política, não entra
em logs nem é exibido pela interface.

> [!IMPORTANT]
> A implementação não provisiona tenant, conta, grupo, papel, chave ou regra. A
> confirmação bem-sucedida do TOTP apenas satisfaz a pré-condição já documentada
> para o bootstrap global explícito e auditável.

## Componentes rastreáveis

- `RegistrationAuthenticationContinuationService` cria a continuação persistida de
  ativação e `RFWRegistrationProviderAdapter` e
  `RFWExternalRegistrationProviderAdapter` a entregam ao lifecycle de sessão RFW;
- `FounderTotpEnrollmentPolicyService` compara internamente a identidade ativa à
  configuração normalizada e verifica a existência de TOTP ativo;
- `FounderTotpEnrollmentNavigationGuard` aplica o bloqueio em cada UI Vaadin;
- `UserSecurityView` usa somente a API pública de configuração da RFW Platform para
  limitar a tela ao enrollment TOTP;
- `20260901_001_update.sql` adiciona a finalidade persistida
  `REGISTRATION_ACTIVATION` e atualiza o marco da versão global para `20260901001`.

## Validação

- `RegistrationAuthenticationContinuationServiceTest` cobre as continuações local e
  Google, a finalidade, o método efetivamente comprovado e a expiração curta;
- `FounderTotpEnrollmentPolicyServiceTest` cobre fundador sem TOTP, confirmação
  posterior e identidade não fundadora;
- `FounderTotpEnrollmentNavigationGuardTest` comprova o redirecionamento da jornada
  autenticada e a exceção exclusiva da tela de segurança;
- `TotpFactorServiceTest.confirm_shouldExpireOrExhaustWithoutActivating` comprova que
  prova expirada ou tentativas esgotadas revogam o fator e não o tornam ativo;
- `mvn '-Dit.test=IdentityRepositoryIT,RegistrationRoundtripIT' verify` passou com
  813 testes unitários e 36 integrações (uma ignorada), contra MySQL 9.7 descartável;
- `mvn '-Dit.test=GlobalDatabaseMigrationIT' verify` passou com 813 testes unitários e
  nove integrações de init, atualização incremental e idempotência da versão global
  `20260901001`.
