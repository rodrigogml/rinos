# Evidência 7.1 — preparação operacional

O modelo de configuração está em `application.properties.model`; valores reais não são versionados. O README e `docs/operations/user-registration-runbook.md` documentam JAR Linux na porta 7070, Apache como proxy HTTPS, headers confiáveis, afinidade Vaadin, cookies, origins, RP ID, keyring, calibração Argon2id, Turnstile, retenção e reação a falhas de migração/autenticação.

O procedimento usa somente referências relativas na aplicação e exige validações antes do deploy. Backups, restauração e intervenção em falha estrutural permanecem responsabilidade da infraestrutura, conforme a constituição.
