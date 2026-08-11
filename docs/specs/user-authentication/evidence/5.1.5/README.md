# Evidência 5.1.5 — telemetria sanitizada do acesso

O RFW expõe `RFWAccessTelemetryListener` por configuração. O Rinos liga o listener ao `AuthenticationUiTelemetryService`, que registra somente `type`, `step` e `status` em nível debug. O evento nunca transporta identificadores, valores de campos, credenciais, provas, tokens ou endereços.

Tipos emitidos:

- `VIEW`, ao renderizar uma etapa;
- `SUBMISSION`, ao iniciar uma operação assíncrona;
- `RESULT`, ao receber o resultado público da operação.

Falhas do listener são absorvidas pelo RFW e não alteram o comportamento de autenticação.

Validação isolada: `mvn -q -DskipITs -Dtest=RFWAccessComponentTest,RFWAccessComponentConfigTest test` no submódulo RFW.
