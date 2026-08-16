# Pesquisa Técnica — Participação em Contas

**Data**: 2026-08-15
**Baseline**: Java 25, Spring Boot 4, MySQL 9, Vaadin e RFW 2.0

## Decisões

1. Membership e convites pertencem ao banco global: eles ligam identidade global e conta antes de qualquer acesso a
   dados do tenant e precisam ser consultáveis pelo gate estrutural.
2. Uma associação representa um ciclo de participação. Reentrada após remoção cria nova linha e preserva o ciclo
   anterior. Um marcador anulável com unique `(conta, usuário, marcadorAtual)` garante no máximo um ciclo vigente.
3. Convite é entidade separada. A validação persiste somente digest MAC e versão da chave. Para entrega assíncrona, uma
   segunda representação recuperável existe exclusivamente como envelope AEAD versionado e apagável no outbox; a
   prova nunca integra payload JSON, logs ou auditoria. Um convite pendente por conta/e-mail é garantido por unique.
4. Aceite bloqueia o convite e o par conta/identidade, revalida e-mail confirmado, conta, plano e convite, e consome a
   prova na mesma transação que cria a associação.
5. Papel contextual é dado descritivo, nunca fonte de autorização. Toda administração chama o contrato canônico de
   autorização do tenant.
6. Continuidade administrativa é validada pelo resultado efetivo fornecido por `access-control`, incluindo bloqueios,
   vigências e 2FA. A mutação e sua validação devem compartilhar uma fronteira transacional fail-safe.
7. Mudanças publicam revisão por associação/tenant e evento durável; novas operações e jobs revalidam a associação.
8. Emissão e reenvio usam janela fixa nas dimensões conta, convidante, destinatário e origem. As quatro reservas ficam
   na transação do convite e são integralmente revertidas quando uma dimensão bloqueia.
9. O dispatcher reivindica lotes com lock e lease persistente. Outra instância recupera lease vencido; sucesso e
   invalidações apagam o envelope, enquanto falha agenda backoff limitado e nunca ultrapassa a expiração do convite.

## Integrações

- `account-registration`: bootstrap idempotente da associação fundadora.
- `access-control`: leitura estrutural minimizada e continuidade administrativa efetiva.
- `plans-entitlements`: limite de participantes no aceite, nunca confundido com permissão.
- `identity`: estado, e-mail principal confirmado, garantia recente e proteção da prova.
- `tenant-context-isolation`: lista/troca de contexto baseada somente em associações ativas e conta operacional.

Adapters ausentes retornam indisponibilidade e não permitem emissão, aceite ou mutação.

O outbox diferencia eventos de lifecycle, sempre sem segredo, da emissão destinada ao e-mail. Esta última contém um
envelope AEAD rotacionável, autenticado pelo domínio `membership/invitation-delivery/v1` e apagado em todo estado
terminal. Persistir o token puro no JSON do outbox continua proibido.

## Pesquisa RFW

Foram revistos o guia Rinos/RFW, README/AGENTS do submódulo e showroom. A interface usará `UIFactory`, definições de
botão, `RFWBannerComponent`, `RFWToastService`, diálogo/confirmador e protocolo público de reautenticação. Tabelas,
filtros e formulários serão composição de domínio com componentes/tokens públicos. Não foi identificada nova lacuna
RFW para o slice persistente; qualquer descoberta na implementação visual seguirá análise e autorização separadas.

## Alternativas descartadas

- guardar membership no schema de cada tenant: impediria validar o contexto antes de selecionar a fonte do tenant;
- reutilizar a mesma linha após remoção: apagaria a distinção entre ciclos e convites;
- token reversível ou em claro: ampliaria o impacto de leitura indevida do banco;
- inferir permissões do papel: contradiz autorização explícita;
- cachear tenants ativos na sessão: gera conflito entre abas e revogação tardia.
