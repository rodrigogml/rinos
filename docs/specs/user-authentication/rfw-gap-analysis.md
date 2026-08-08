# RFW Gap Analysis: Autenticação e Segurança do Usuário

**RFW baseline originally inspected**: `dd7554c8c5ffca3527ffc7ca859eec781703fa46`<br>
**RFW delivery baseline current**: `800cb67f2e7c3547f5118d1d5d6af55d5e772226` (`br.eng.rodrigogml.rfw:rfw:2.0.0`)<br>
**Date**: 2026-08-08<br>
**Status**: gaps AUTH-RFW-001 a AUTH-RFW-004 entregues; os demais devem ser implementados no RFW antes dos adapters
dependentes no Rinos

## Existing Capabilities Reused

- `RFWAccessComponent` e `RFWAuthenticationSessionService`;
- providers de senha, segundo fator, recuperação, Google, Turnstile e lembrar-me;
- `RFWSecuritySettingsComponent` e providers de passkeys, fatores, vínculos, sessões e reautenticação;
- `RFWTotpService`, `RFWOneTimeCodeService`, `RFWRecoveryCodeService` e `RFWOpaqueTokenService`;
- `RFWPasskeyComponent` e integração inicial com endpoints WebAuthn do Spring Security;
- publicação do `SecurityContext`, proteção contra fixation, i18n, tema, estados assíncronos e extensões públicas.

Essas capacidades não devem ser copiadas pelo Rinos.

## Gap AUTH-RFW-001: conclusão WebAuthn contorna a orquestração do acesso

**Evidence**: `RFWPasskeyComponent.authenticate()` chama `/login/webauthn` e redireciona quando o endpoint responde
`authenticated`. O `RFWAccessComponent` não recebe um outcome para aplicar MFA adicional, estado do usuário, criação
da sessão global ou aceite legal.

**Required evolution**: permitir que a assertion validada seja entregue a um provider/orquestrador da hospedeira e
somente então publicar a autenticação. O contrato deve preservar os endpoints oficiais Spring, não transportar a
assertion pela sessão Vaadin e permitir login descobrível sem e-mail.

**Compatibility**: manter o comportamento direto como opção compatível para hospedeiras antigas; o modo orquestrado
deve ser ativado por provider/capability explícita.

## Gap AUTH-RFW-002: enrollment TOTP não devolve dados de provisionamento

**Evidence**: `RFWSecondFactorManagementProvider.beginEnrollment` retorna apenas `String challengeId`, e o renderer
abre diretamente um campo de confirmação. A URI/segredo/QR produzida por `RFWTotpService` não pode ser apresentada.

**Required evolution**: outcome tipado de enrollment contendo referência, método, expiração e dados de apresentação
única para TOTP (`otpauthUri` e alternativa textual). O componente deve renderizar QR acessível, alternativa copiável
e apagar o segredo após fechar/confirmar.

**Compatibility**: adicionar contrato/overload sem quebrar providers existentes; adapters antigos continuam no fluxo
de challenge simples.

## Gap AUTH-RFW-003: fator por e-mail não possui emissão, seleção e reenvio

**Evidence**: `RFWSecondFactorProvider` somente verifica. Trocar o método permitido no renderer não chama o backend
para emitir OTP, e não há operação de reenvio com nova validade.

**Required evolution**: provider para iniciar/selecionar/reemitir método do desafio e outcome com destino mascarado,
expiração, limitação e indisponibilidade. A UI deve renovar a prova sem conservar código anterior.

**Compatibility**: TOTP e recovery code podem continuar verificáveis sem emissão; e-mail só aparece quando o provider
de emissão estiver presente.

## Gap AUTH-RFW-004: reautenticação aceita somente uma prova textual

**Delivery status**: concluído na baseline `800cb67f2e7c3547f5118d1d5d6af55d5e772226`.

**Evidence**: `RFWReauthenticationProvider` retorna `boolean` para `operationId + proof`; o componente sempre abre
`PasswordField`. Usuário passwordless não pode usar passkey/TOTP, e não existe continuação tipada.

**Required evolution**: contrato de reautenticação que possa retornar “já recente”, desafio com métodos permitidos,
sucesso, rejeição ou limitação. O renderer deve reutilizar a UI de desafio e WebAuthn.

**Compatibility**: adaptar provider booleano como estratégia legada de senha; novo provider tem precedência explícita
e não pode coexistir de forma ambígua.

## Gap AUTH-RFW-005: lifecycle de “lembrar-me” é incompleto

**Evidence**: `RFWRememberMeProvider` recebe apenas sucesso. Não resolve cookie em nova requisição, não rotaciona,
revoga, limpa nem participa do logout/expiração.

**Required evolution**: protocolo genérico de lifecycle de sessão persistente integrado ao Spring Security, com
criação pós-autenticação, restauração, rotação, revogação e limpeza. A hospedeira continua proprietária da persistência
e da política.

**Compatibility**: preservar callback atual como contrato simples; capability persistente completa só é anunciada
quando o novo provider estiver presente.

## Gap AUTH-RFW-006: sessão global não participa da publicação e do logout

**Evidence**: `RFWAuthenticationSessionService` publica/limpa somente o contexto Spring/RFW e chama o callback de
lembrar-me. Não existe hook transacional para criar ou revogar o registro de sessão da hospedeira.

**Required evolution**: lifecycle provider que prepare uma referência de sessão antes da publicação, acrescente-a ao
principal mínimo de modo não secreto e seja notificado no logout. Falha deve impedir sessão parcial e não deixar
cookie utilizável.

**Compatibility**: provider opcional; aplicações sem registro global conservam comportamento atual.

## Gap AUTH-RFW-007: gate legal pós-login não possui etapa própria

**Evidence**: existe `ACTIVATION_CONSENT_REQUIRED`, semanticamente vinculado ao cadastro/ativação. Login de usuário
ativo não possui continuação distinta antes da publicação da sessão.

**Required evolution**: outcome e etapa de consentimento pós-autenticação que recebam referência opaca e catálogo
vigente, preservem o método/remember-me sem guardar credenciais e retomem a conclusão somente após provider válido.

**Compatibility**: a etapa nova é aditiva. O contrato de ativação permanece inalterado.

## Gap AUTH-RFW-008: gestão não comunica resultados e invariants de forma suficiente

**Evidence**: providers autenticados retornam predominantemente `Void`; o componente fecha diálogos antes de saber
se confirmação/revogação falhou e não possui resultados para “último método”, conflito concorrente ou provider
indisponível. `RFWAuthenticationMethodVO` também não informa criação/estado suficiente para a spec.

**Required evolution**: outcomes tipados de gestão, fechamento somente após sucesso, erros associados à ação, refresh
após conflito e VOs seguros com datas/estado necessários. A UI continua sem decidir invariants.

**Compatibility**: métodos legados podem ser adaptados a `COMPLETED`; novos resultados são preferidos quando
disponíveis.

## Gap AUTH-RFW-009: componente de passkey precisa de acessibilidade e estado de falha tipado

**Evidence**: o web component interno cria um botão HTML e emite apenas `data-error`/nome genérico; não há mensagem
localizada, foco, diferenciação de cancelamento, indisponibilidade ou credencial rejeitada.

**Required evolution**: eventos públicos tipados, texto i18n, estado busy, anúncio acessível, foco previsível e
preservação do método alternativo. Nenhum detalhe criptográfico deve ser exposto.

## Gap AUTH-RFW-010: configurações de segurança não gerenciam senha local

**Evidence**: `RFWSecuritySettingsSectionEnum` cobre passkeys, fatores, identidades externas, sessões e códigos de
recuperação, mas não possui seção/provider para criar, alterar ou reconhecer a credencial local.

**Required evolution**: seção e provider de senha com estados “configurada/ausente/comprometida”, alteração mediante
reautenticação, criação explícita para usuário externo/passwordless e validações por campo. O componente não aplica a
política nem recebe o hash; a hospedeira continua responsável por HIBP, Argon2id, sessões e auditoria.

**Compatibility**: seção aparece somente com provider real; aplicações sem gestão de senha permanecem inalteradas.

## Required RFW Delivery Gate

Para cada gap implementado:

1. apresentar/confirmar contrato público e compatibilidade no ciclo do RFW;
2. implementar testes unitários, de contexto e de integração aplicáveis;
3. atualizar documentação-base em português e traduções equivalentes do showroom;
4. atualizar laboratório de acesso/configurações de segurança e bundles i18n quando demonstrável;
5. validar o RFW isoladamente e publicar o commit em `main`;
6. atualizar o ponteiro do submódulo no Rinos e registrar a nova baseline neste documento;
7. somente então registrar o provider real e anunciar a capability no Rinos.

O Rinos não deve criar renderers locais permanentes para contornar essas lacunas genéricas.
