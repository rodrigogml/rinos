# Evidência 4.4.5 — Limpeza diária dos artefatos de autenticação

## Escopo

Artefatos temporários não podem permanecer indefinidamente no banco global. A limpeza deve ser acionada pelo catálogo
diário, com cada domínio isolado para que uma falha parcial não interrompa os demais.

## Evidência implementada

`IdentityCleanupCatalogScheduler` delega separadamente:

- fluxos e provas de autenticação ao `AuthenticationArtifactCleanupService`;
- sessões expiradas ou fora da retenção;
- janelas antifraude expiradas e seu histórico retido;
- recovery codes e provas de recuperação pelos respectivos serviços;
- pendências de cadastro, origens e tombstones de cancelamento.

Cada chamada é protegida por captura de falha e possui fronteira transacional própria no serviço especializado. O
agendamento usa a liderança de manutenção já definida para execução entre instâncias.

## Validação

O catálogo foi exercitado pelas integrações dos repositórios de autenticação e pelo `mvn verify` do projeto; a tarefa
não altera contratos de dados nem cria uma segunda rotina de agendamento.
