# Esboço do HumAIne

**Status**: em descoberta

Este documento registra somente as ideias aprovadas durante a exploração inicial do HumAIne. Sugestões e hipóteses não aprovadas não devem ser tratadas como decisões do módulo.

## Ideias aprovadas

- O novo módulo se chamará **HumAIne**.
- O HumAIne oferecerá um sistema de agentes de IA para usuários do Rinos.
- O acesso ao módulo dependerá de um plano de acesso ao HumAIne estar ativo para o usuário.
- O usuário poderá criar agentes distintos, com características, funções e acessos diferentes e exclusivos.
- Cada agente terá um nome para identificá-lo.
- Cada agente terá um UID normalizado, sem espaços, caracteres acentuados ou caracteres especiais.
- Cada agente terá instruções de operação.
- A definição do agente será usada pelo sistema para executar comandos em provedores de IA.
- O HumAIne será vinculado ao usuário, e não ao tenant.
- O usuário poderá configurar diferentes provedores de IA na aplicação.
- A camada de AI terá um contrato único para a aplicação.
- A camada de AI deverá trabalhar com OpenAI, Claude, Gemini, DeepSeek e outros provedores.
- A inclusão de um novo provider deverá ser feita por meio de uma nova implementação do contrato da camada de AI.

## Itens ainda não definidos

- Fluxo de criação e utilização dos agentes.
- Significado operacional de características, funções e acessos.
- Capacidades cobertas pelo contrato único da camada de AI.
- Regras para configuração e credenciais dos provedores.
- Tratamento das diferenças entre as APIs dos provedores.

