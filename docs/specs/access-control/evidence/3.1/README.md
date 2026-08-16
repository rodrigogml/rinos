# Evidência 3.1 — Resolução e precedência

- `AccessRuleResolutionService` carrega somente a origem direta do sujeito e seus grupos no contexto solicitado.
- A revisão é lida antes e depois sob `READ_COMMITTED`; mudança concorrente torna a resolução indisponível em vez de
  reutilizar fotografia inconsistente.
- Cada chave exige ao menos um `PERMITIR` corrente e nenhum `BLOQUEAR` corrente.
- Regras, grupos ou associações inativas, futuras e expiradas são explicadas como fontes ignoradas.
- A próxima fronteira temporal é calculada para limitar qualquer cache futuro sem congelar a vigência.
- Testes cobrem bloqueio direto contra permissão de grupo, bloqueio de grupo contra permissão direta, vários grupos
  permissivos com um bloqueador, grupo de outro tenant e regra futura.
