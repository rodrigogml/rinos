# RFW Compatibility Analysis: Cadastro de Usuário

**RFW commit analyzed**: `f7c404e761d3d95d1eac570b92bd2dc9c4b6e3a9`
**Date**: 2026-07-26
**Status**: todas as nove lacunas resolvidas

## Resultado

O RFW Platform atual já fornece a base correta para a interface de acesso:

- `RFWAccessComponent` e `RFWAccessComponentFactory`;
- cadastro local, ativação e reenvio por `RFWRegistrationProvider`;
- Google Identity Services por `RFWGoogleSignInComponent` e `RFWGoogleIdentityProvider`;
- Turnstile por `RFWTurnstileComponent` e `RFWTurnstileVerificationService`;
- documentos legais por `RFWLegalDocumentVO`;
- estados assíncronos, feedback, slots, renderers, i18n, tema e publicação segura da autenticação;
- componentes futuros de configurações de segurança.

O Rinos deve hospedar esse componente e implementar seus providers. Não há justificativa para criar uma tela de cadastro paralela.

As cinco lacunas funcionais e a melhoria de segurança originalmente identificadas foram implementadas no RFW pelo
commit `072f7de9affa7d24fdcb1671aeed6e4ce27d9c31`, com código, testes, traduções, documentação e laboratórios no
showroom. As três lacunas adicionais encontradas pela Interface Design foram resolvidas no commit
`fb59049ef916f0854b53159542b71591db24cb8f`. O endurecimento da origem atrás de proxy e a classificação pública das
falhas do Turnstile foram concluídos no commit `ce2ff13e4b4d762e839f7263ce28060bf84b5b38`. A exigência dos claims
temporais do Google, a tolerância limitada e os timeouts explícitos de discovery OIDC e JWKS foram concluídos no
commit `f7c404e761d3d95d1eac570b92bd2dc9c4b6e3a9`, que constitui a baseline aprovada para a implementação.

| Lacuna original | Situação no commit analisado |
|-----------------|------------------------------|
| `GAP-RFW-REG-001` | Resolvida por continuação tipada de cadastro externo, e-mail verificado somente leitura e provider próprio |
| `GAP-RFW-REG-002` | Resolvida por `issuer()` tipado na identidade externa validada |
| `GAP-RFW-REG-003` | Resolvida por política condicional de verificação humana por operação e origem |
| `GAP-RFW-REG-004` | Resolvida por solicitação e confirmação tipadas do cancelamento |
| `GAP-RFW-REG-005` | Resolvida pela aplicação de `fieldErrors` aos controles do renderer padrão |
| `GAP-RFW-REG-006` | Resolvida por `action` estável e `idempotency_key` na integração Turnstile |
| `GAP-RFW-REG-007` | Resolvida por continuação tipada de aceite durante a ativação e provider próprio |
| `GAP-RFW-REG-008` | Resolvida por ação direta de recuperação com e-mail preenchido |
| `GAP-RFW-REG-009` | Resolvida pela preservação restrita a e-mail e IDs de documentos legais |
| `GAP-RFW-REG-010` | Resolvida por `RFWRemoteAddressProvider` e fallback compatível para a conexão direta |
| `GAP-RFW-REG-011` | Resolvida pela classificação pública de prova, contexto, disponibilidade e configuração |
| `GAP-RFW-REG-012` | Resolvida pela validação obrigatória de `exp` e `iat`, clock skew limitado e timeout comum de discovery OIDC e JWKS |

## GAP-RFW-REG-001: Continuação de cadastro por identidade externa

**Severity**: BLOCKER
**Requirements**: `FR-REG-043` a `FR-REG-052`

### Estado anterior

O botão Google entrega a credencial a `RFWExternalIdentityProvider.authenticate()`, que chama `RFWExternalIdentityResolver`. Os resultados disponíveis permitem autenticar, rejeitar, concluir, limitar ou encaminhar para ativação/segundo fator.

Não existe um estado próprio para:

1. identidade Google validada sem usuário ativo;
2. apresentar o e-mail verificado e bloqueado;
3. coletar somente documentos legais;
4. concluir o cadastro sem senha nem confirmação adicional.

Usar a etapa `ACTIVATION` com renderer local exigiria esconder uma continuação de negócio dentro de um desafio de ativação e buscar dados por fora do contrato do componente.

### Resolução implementada

- Acrescentar um resultado tipado de continuação, como `EXTERNAL_REGISTRATION_REQUIRED`.
- Acrescentar uma etapa substituível de cadastro externo.
- Transportar somente referência opaca, provider, e-mail verificado para apresentação e documentos legais; nunca o ID token.
- Publicar provider/DTO para concluir a continuação com os IDs dos documentos aceitos.
- Manter Rinos responsável por criar/reutilizar pendência, persistir consentimentos, vincular `issuer + sub` e ativar.

### Compatibilidade

Mudança aditiva. Providers atuais continuam funcionando. A nova etapa só aparece quando o resolver retornar o novo estado.

## GAP-RFW-REG-002: Emissor explícito da identidade externa

**Severity**: BLOCKER DE INTEGRIDADE
**Requirements**: `FR-REG-043`, `FR-REG-044`, `FR-REG-049`

### Estado anterior

`RFWGoogleIdentityProvider` valida o issuer configurado, mas `RFWVerifiedExternalIdentityVO` expõe somente `providerId`, `subject`, `email`, `emailVerified` e o mapa genérico de claims.

O Rinos precisa persistir e restringir a combinação imutável `issuer + sub`. Obrigar o host a extrair `iss` do mapa de claims enfraquece o contrato tipado.

### Resolução implementada

- Acrescentar `issuer` explícito a `RFWVerifiedExternalIdentityVO`.
- Preenchê-lo somente depois da validação criptográfica.
- Atualizar testes, provider Google, documentação e showroom.

### Compatibilidade

É necessária estratégia compatível para o record público. Recomenda-se nova factory/versão de contrato ou alteração coordenada antes de haver aplicações consumidoras estáveis. O showroom e todos os usos internos devem ser migrados no mesmo commit.

## GAP-RFW-REG-003: Turnstile condicional por operação e origem

**Severity**: BLOCKER
**Requirements**: `FR-REG-028`, `FR-REG-037` a `FR-REG-042`

### Estado anterior

Quando existe `RFWHumanVerificationProvider`, `RFWAccessComponent`:

- sempre renderiza o widget nas etapas protegidas;
- sempre chama `verify()` antes do provider funcional;
- decide apenas pela presença global do provider e da site key.

Não existe contrato para o Rinos informar que o cadastro ainda está abaixo do limiar por origem. Com limiar zero o comportamento atual atende; qualquer valor maior não pode ser representado.

### Resolução implementada

- Introduzir contexto tipado da operação, como `SIGN_IN`, `REGISTRATION` e `RECOVERY`.
- Permitir um provider/policy decidir se a prova é exigida para operação e origem já resolvida.
- Preservar como fallback o comportamento atual “provider presente = obrigatório”, garantindo compatibilidade.
- Renderizar e validar o widget somente quando a decisão efetiva exigir.
- Manter no Rinos a origem normalizada, os contadores, as janelas, os bloqueios e a retenção limitada.

### Compatibilidade

Mudança aditiva com fallback equivalente ao comportamento atual.

## GAP-RFW-REG-004: Cancelamento de cadastro pendente

**Severity**: BLOCKER
**Requirements**: `FR-REG-025` a `FR-REG-027`

### Estado anterior

`RFWRegistrationProvider` publica `register`, `activate` e `resendActivation`. `RFWAccessStepEnum` não possui solicitação/confirmação de cancelamento.

Uma view isolada no Rinos duplicaria layout, feedback, tratamento assíncrono, desafios e navegação do componente de acesso.

### Resolução implementada

- Criar capability/provider opcional de cancelamento de cadastro, separado do provider existente para preservar compatibilidade.
- Oferecer solicitação de prova e confirmação de cancelamento por referência opaca.
- Acrescentar etapa e renderer padrão, incluindo confirmação explícita da ação destrutiva.
- Não presumir deleção: o provider hospedeiro define retenção e auditoria.

### Compatibilidade

Mudança aditiva; a capability não aparece sem provider.

## GAP-RFW-REG-005: Aplicação de erros por campo

**Severity**: HIGH
**Requirements**: `FR-REG-003`, `FR-REG-004`, `FR-REG-030`, `SC-UR-001`

### Estado anterior

`RFWAccessErrorVO` já possui `fieldErrors`, mas `RFWAccessComponent.renderFeedback()` apresenta somente `messageKey`. O renderer padrão não associa os erros retornados a e-mail, senha, confirmação ou documentos legais.

### Resolução implementada

- Definir nomes estáveis dos campos públicos por etapa.
- Fazer o renderer padrão aplicar `fieldErrors` aos componentes correspondentes.
- Manter alerta geral e direcionar foco ao primeiro campo inválido.
- Limpar o erro quando o usuário corrigir ou iniciar nova tentativa.
- Documentar como renderers customizados consomem os mesmos erros.

### Compatibilidade

Ativa um campo já existente no contrato; mudança comportamental compatível e esperada.

## GAP-RFW-REG-006: Contexto/action do Turnstile

**Severity**: SECURITY IMPROVEMENT
**Requirements**: reforça `FR-REG-034` e separa os fluxos protegidos

### Estado anterior

O serviço valida sucesso e hostname, mas o widget não declara uma `action` por operação e o backend não a compara. Também não envia `idempotency_key` ao Siteverify.

### Resolução implementada

- Configurar action estável por operação.
- Enviar action no widget e validar a action retornada no servidor.
- Gerar `idempotency_key` por validação para retry técnico seguro.
- Manter token e resposta fora da persistência.

### Compatibilidade

Pode ser aditiva, com validação de action habilitada quando configurada. Para o Rinos, deve estar ativa.

## GAP-RFW-REG-007: Novo aceite legal durante a ativação

**Severity**: BLOCKER
**Requirements**: `FR-REG-016`, `FR-REG-017`

### Estado anterior

O renderer padrão de `ACTIVATION` apresenta identificador, prova, reenvio e cancelamento. O
`RFWActivationRequestDTO` transporta somente identificador e prova. Se um documento obrigatório mudar entre o início
do cadastro local e sua ativação, o provider pode rejeitar a ativação, mas o componente não possui continuação tipada
para apresentar somente as novas versões, recolher seus aceites e concluir a mesma ativação.

Reabrir `REGISTRATION` exigiria novamente senha e dados iniciais, misturaria início e conclusão do processo e não
preservaria corretamente a prova já validada. Usar `EXTERNAL_REGISTRATION` seria semanticamente incorreto e acoplaria
o cadastro local a um contrato criado para identidade externa.

### Resolução implementada

- Resultado tipado `ACTIVATION_CONSENT_REQUIRED`.
- `RFWActivationConsentChallengeVO` com referência opaca, versões legais pendentes e expiração.
- Etapa `ACTIVATION_CONSENT`, com e-mail apenas informativo e não editável quando disponibilizado de
  forma mascarada ou segura.
- `RFWActivationConsentRequestDTO` com referência opaca e IDs das versões aceitas.
- `RFWActivationConsentProvider`, cujo implementador revalida referência, expiração, uso único e versões vigentes antes de
  registrar os aceites e ativar atomicamente.
- Fluxo anterior preservado quando nenhuma nova versão precisa de aceite.

### Compatibilidade

Mudança aditiva. Providers atuais continuam válidos; a nova etapa somente aparece quando a aplicação hospedeira
retornar o novo resultado. O contrato deve ser documentado e demonstrado no showroom antes de o Rinos utilizá-lo.

## GAP-RFW-REG-008: Encaminhamento direto do cadastro para recuperação

**Severity**: HIGH
**Requirements**: `FR-REG-008`

### Estado anterior

O cadastro padrão permite voltar ao login. A recuperação de senha aparece no login quando existe
`RFWPasswordRecoveryProvider`, mas a etapa `REGISTRATION` não oferece uma ação direta para abrir
`RECOVERY_REQUEST`. Assim, a mensagem de e-mail existente consegue orientar textualmente, porém não cumpre a
experiência aprovada de oferecer o direcionamento imediato com o identificador já preenchido.

### Resolução implementada

- Quando a capability `PASSWORD_RECOVERY` está disponível, a etapa `REGISTRATION` apresenta uma ação secundária
  de recuperação de senha.
- `RECOVERY_REQUEST` recebe como identificador o e-mail informado no cadastro.
- A ação não depende de informação privada adicional retornada pelo provider.
- Sem a capability, permanece somente a orientação para voltar ao login, sem rota
  ou recuperação parcial no Rinos.

### Compatibilidade

Mudança comportamental aditiva no renderer padrão, condicionada à capability já existente. Não exige novo provider,
mas exige documentação, traduções, testes e demonstração no showroom.

## GAP-RFW-REG-009: Preservação seletiva do formulário após rejeição

**Severity**: BLOCKER
**Requirements**: `FR-REG-030`, `FR-REG-035`, `SC-UR-001`

### Estado anterior

O renderer padrão limpa senha e confirmação antes de submeter o cadastro, o que é correto. Quando o Turnstile ou o
provider retorna rejeição, `RFWAccessComponent` renderiza novamente a etapa para aplicar `fieldErrors`; como o e-mail
e os aceites legais não fazem parte de um estado seguro reidratável, eles também voltam vazios. A pessoa precisa
repetir dados que não são segredos, inclusive quando apenas o desafio anti-automação expirou.

### Resolução implementada

- O estado da etapa preserva somente e-mail e IDs dos documentos marcados.
- Senha, confirmação, token Turnstile, código, prova e credencial externa nunca são preservados.
- E-mail e aceites são reidratados após rejeição recuperável quando a pessoa permanece na mesma tentativa.
- O estado preservado é limpo ao sair da etapa, concluir o cadastro ou iniciar uma nova intenção pública.
- O contrato para renderers customizados e a efemeridade dos campos sensíveis estão documentados no showroom.

### Compatibilidade

Mudança comportamental compatível. Não altera DTOs públicos, mas exige testes de ciclo de vida, serialização segura,
teclado/foco e laboratório que demonstre rejeição com preservação seletiva.

## Alterações que não são necessárias

- Não criar outro componente de cadastro no Rinos.
- Não criar outro cliente Turnstile.
- Não criar outro botão ou adapter Google.
- Não duplicar tema, seletor de idioma, feedback assíncrono ou estrutura do card.
- Não alterar o RFW para persistir usuários, credenciais ou consentimentos; isso pertence ao Rinos.
- Não mover políticas de senha, retenção, limitação por IP ou auditoria para o RFW.

## Ordem executada no RFW

1. Continuação tipada de aceite durante a ativação.
2. Preservação seletiva do formulário.
3. Encaminhamento de recuperação no renderer padrão de cadastro.
4. Código, testes, traduções, documentação e laboratório do showroom no mesmo commit.

## Gate

Gate encerrado no commit `fb59049ef916f0854b53159542b71591db24cb8f`. A Interface Design pode ser aprovada e
seguir para checklist e backlog usando os contratos finais do submódulo.
