# Evidência 5.4 — gestão de métodos e sessões

O painel em `/user/security` usa as seções RFW para senha, passkeys, fatores adicionais, recovery codes, identidades externas e sessões. Os adapters Rinos conectam cada provider às fachadas reais; o componente exige reautenticação para operações sensíveis, atualiza a seção após mutação e preserva estados independentes de loading/error/stale. Revogações e exclusões destrutivas permanecem confirmadas pelo renderer, sem expor IDs internos na interface.

Os testes de contrato do RFW e os adapters Rinos cobrem os invariants, ausência de provider, falhas e outcomes públicos. A suíte E2E opt-in do login verifica a entrada no painel; a inspeção detalhada das seções é executada pelo showroom e pelo teste visual do componente RFW.
