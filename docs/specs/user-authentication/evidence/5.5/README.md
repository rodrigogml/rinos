# Evidência 5.5 — reautenticação para operações sensíveis

O provider RFW de reautenticação é registrado pelo Rinos e consumido pelo componente de configurações de segurança. O catálogo de métodos e a validade de 15 minutos são resolvidos no backend; o renderer apresenta a operação em linguagem humana, sem `operationId`, e retoma a operação somente uma vez após a prova válida. Cancelamento, timeout, método passwordless, mesmo canal, falha e operação stale não produzem mutação.

A cobertura de estados e acessibilidade é compartilhada com os testes de `RFWSecuritySettingsComponent`; a integração real é exercitada pelo adapter Rinos e pelo contexto Spring.
