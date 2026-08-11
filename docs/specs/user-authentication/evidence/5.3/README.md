# Evidência 5.3 — configurações de segurança do usuário

`/user/security` é uma rota autenticada do Rinos que compõe `RFWSecuritySettingsComponentFactory.create()` diretamente. O painel de usuário oferece o enlace para essa rota. Seções, providers, loading/empty/error/stale, refresh após mutações, foco, localização, responsividade e confirmação destrutiva permanecem centralizados no RFW; o Rinos não replica componentes nem transporta dados sensíveis em telemetria.

Validação: `UserSecurityViewTest`, `UserDashboardEntryViewTest`, `RFWSecuritySettingsComponentTest` e `RFWPlatformIntegrationTest`.
