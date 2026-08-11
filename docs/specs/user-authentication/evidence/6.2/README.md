# Evidência 6.2 — segurança, concorrência e falhas

As suítes existentes cobrem enumeração neutra, limites e Turnstile, CSRF/origem, fixation e replay; os testes MySQL verificam consumo único concorrente de OTP, recovery code, provas e sessões. Os testes de WebAuthn validam contador, origem, RP ID e JWKS. Falhas de SMTP, Google, Turnstile, HIBP e persistência são convertidas em outcomes indisponíveis/rejeitados sem publicar efeitos parciais. Auditorias e telemetria não incluem segredos, tokens, provas ou IP de origem.

Comandos focados reproduzíveis estão nos arquivos de evidência das fases 3 e 4; o build isolado do RFW foi aprovado no commit publicado `db3f246`.
