# Especificação de Interface — Participação em Contas

## Superfícies

- participantes e convites dentro do tenant explicitamente identificado;
- formulário de convite e acompanhamento de entrega;
- proposta pública segura após autenticação/cadastro;
- alteração de papel, suspensão, reativação, remoção e saída.

## RFW Platform

Usar `UIFactory` e definições públicas de botões, `RFWBannerComponent` para impedimentos persistentes,
`RFWToastService` para confirmação breve, componentes públicos de diálogo/confirmação e protocolo RFW de
reautenticação. Grid, filtros, campos, tokens, ícones e estados vêm primeiro das APIs/showroom do RFW. Não criar CSS
estrutural paralelo nem alterar o submódulo sem análise e autorização.

## Interação

- pesquisa por nome/e-mail mascarado e filtros de estado/papel, sempre no tenant atual;
- papel aparece como identificação e nunca como resumo de permissão;
- ações ocultas na UI continuam verificadas no serviço;
- impacto sensível informa perda de contexto e continuidade, sem expor regras/grupos internos;
- convite público mostra conta, convidante minimizado, papel e validade antes da decisão;
- estados: pendente, ativo, suspenso, saiu, removido, expirado, revogado e indisponível pelo plano.

## Acessibilidade e privacidade

Teclado completo, foco no primeiro erro/diálogo, `aria-live` para mudanças, reflow 320 CSS px, zoom 200%, contraste e
alvos adequados. E-mail é mascarado quando completo não for necessário; prova, IDs internos e detalhes de autorização
nunca aparecem. Listas de outro tenant não entram no DOM nem em payload de cliente.
