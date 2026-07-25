# Instruções do Projeto Rinos

## Uso obrigatório da RFW Platform

Toda especificação, planejamento, implementação ou revisão de interface humana do Rinos DEVE usar a RFW Platform como primeira fonte de componentes, padrões visuais, protocolos de interação e infraestrutura compartilhável.

Antes de propor ou implementar uma interface:

1. Leia [docs/architecture/rfw-platform-usage.md](docs/architecture/rfw-platform-usage.md).
2. Leia `modules/RFW.Platform/README.md` e as instruções `AGENTS.md` aplicáveis dentro do submódulo.
3. Consulte prioritariamente a documentação e os laboratórios do showroom em:
   - `modules/RFW.Platform/modules/rfw.showroom/`;
   - `modules/RFW.Platform/modules/rfw.showroom/src/main/resources/showroom/content/`.
4. Pesquise componentes, factories, configs, providers, slots, renderers, tokens e exemplos já existentes no RFW antes de desenhar uma solução própria.

O Rinos não deve duplicar componente ou comportamento reutilizável já oferecido pelo RFW. Quando a capacidade necessária for genérica e útil para outras aplicações hospedeiras, proponha sua evolução no RFW e aguarde autorização explícita antes de alterar o submódulo.

Customizações próprias do Rinos devem consumir primeiro as APIs públicas do RFW: configuração, providers, slots, renderers, classes e tokens documentados. CSS estrutural paralelo ou cópia de componentes exige justificativa arquitetural.

## Alterações no submódulo

Alterações no RFW Platform constituem um ciclo separado:

- apresentar previamente o problema, a API proposta, compatibilidade e impacto para outras aplicações;
- obter autorização explícita;
- atualizar código, testes, documentação e showroom do RFW;
- validar o RFW isoladamente antes de atualizar o ponteiro no Rinos.

