# RFW Compatibility Analysis: Cadastro de Usuário

**RFW commit analyzed**: `db8756b8b7bae92878144f3341a0ee43973b293a`  
**Date**: 2026-07-25  
**Status**: alterações propostas, aguardando autorização

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

Foram encontradas cinco lacunas funcionais e uma melhoria de segurança necessárias para atender integralmente à spec do Rinos sem contornar o RFW.

## GAP-RFW-REG-001: Continuação de cadastro por identidade externa

**Severity**: BLOCKER  
**Requirements**: `FR-REG-043` a `FR-REG-052`

### Estado atual

O botão Google entrega a credencial a `RFWExternalIdentityProvider.authenticate()`, que chama `RFWExternalIdentityResolver`. Os resultados disponíveis permitem autenticar, rejeitar, concluir, limitar ou encaminhar para ativação/segundo fator.

Não existe um estado próprio para:

1. identidade Google validada sem usuário ativo;
2. apresentar o e-mail verificado e bloqueado;
3. coletar somente documentos legais;
4. concluir o cadastro sem senha nem confirmação adicional.

Usar a etapa `ACTIVATION` com renderer local exigiria esconder uma continuação de negócio dentro de um desafio de ativação e buscar dados por fora do contrato do componente.

### Evolução proposta

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

### Estado atual

`RFWGoogleIdentityProvider` valida o issuer configurado, mas `RFWVerifiedExternalIdentityVO` expõe somente `providerId`, `subject`, `email`, `emailVerified` e o mapa genérico de claims.

O Rinos precisa persistir e restringir a combinação imutável `issuer + sub`. Obrigar o host a extrair `iss` do mapa de claims enfraquece o contrato tipado.

### Evolução proposta

- Acrescentar `issuer` explícito a `RFWVerifiedExternalIdentityVO`.
- Preenchê-lo somente depois da validação criptográfica.
- Atualizar testes, provider Google, documentação e showroom.

### Compatibilidade

É necessária estratégia compatível para o record público. Recomenda-se nova factory/versão de contrato ou alteração coordenada antes de haver aplicações consumidoras estáveis. O showroom e todos os usos internos devem ser migrados no mesmo commit.

## GAP-RFW-REG-003: Turnstile condicional por operação e origem

**Severity**: BLOCKER  
**Requirements**: `FR-REG-028`, `FR-REG-037` a `FR-REG-042`

### Estado atual

Quando existe `RFWHumanVerificationProvider`, `RFWAccessComponent`:

- sempre renderiza o widget nas etapas protegidas;
- sempre chama `verify()` antes do provider funcional;
- decide apenas pela presença global do provider e da site key.

Não existe contrato para o Rinos informar que o cadastro ainda está abaixo do limiar por origem. Com limiar zero o comportamento atual atende; qualquer valor maior não pode ser representado.

### Evolução proposta

- Introduzir contexto tipado da operação, como `SIGN_IN`, `REGISTRATION` e `RECOVERY`.
- Permitir um provider/policy decidir se a prova é exigida para operação e origem já resolvida.
- Preservar como fallback o comportamento atual “provider presente = obrigatório”, garantindo compatibilidade.
- Renderizar e validar o widget somente quando a decisão efetiva exigir.
- Manter contadores, HMAC da origem, janelas e bloqueios no Rinos.

### Compatibilidade

Mudança aditiva com fallback equivalente ao comportamento atual.

## GAP-RFW-REG-004: Cancelamento de cadastro pendente

**Severity**: BLOCKER  
**Requirements**: `FR-REG-025` a `FR-REG-027`

### Estado atual

`RFWRegistrationProvider` publica `register`, `activate` e `resendActivation`. `RFWAccessStepEnum` não possui solicitação/confirmação de cancelamento.

Uma view isolada no Rinos duplicaria layout, feedback, tratamento assíncrono, desafios e navegação do componente de acesso.

### Evolução proposta

- Criar capability/provider opcional de cancelamento de cadastro, separado do provider existente para preservar compatibilidade.
- Oferecer solicitação de prova e confirmação de cancelamento por referência opaca.
- Acrescentar etapa e renderer padrão, incluindo confirmação explícita da ação destrutiva.
- Não presumir deleção: o provider hospedeiro define retenção e auditoria.

### Compatibilidade

Mudança aditiva; a capability não aparece sem provider.

## GAP-RFW-REG-005: Aplicação de erros por campo

**Severity**: HIGH  
**Requirements**: `FR-REG-003`, `FR-REG-004`, `FR-REG-030`, `SC-UR-001`

### Estado atual

`RFWAccessErrorVO` já possui `fieldErrors`, mas `RFWAccessComponent.renderFeedback()` apresenta somente `messageKey`. O renderer padrão não associa os erros retornados a e-mail, senha, confirmação ou documentos legais.

### Evolução proposta

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

### Estado atual

O serviço valida sucesso e hostname, mas o widget não declara uma `action` por operação e o backend não a compara. Também não envia `idempotency_key` ao Siteverify.

### Evolução proposta

- Configurar action estável por operação.
- Enviar action no widget e validar a action retornada no servidor.
- Gerar `idempotency_key` por validação para retry técnico seguro.
- Manter token e resposta fora da persistência.

### Compatibilidade

Pode ser aditiva, com validação de action habilitada quando configurada. Para o Rinos, deve estar ativa.

## Alterações que não são necessárias

- Não criar outro componente de cadastro no Rinos.
- Não criar outro cliente Turnstile.
- Não criar outro botão ou adapter Google.
- Não duplicar tema, seletor de idioma, feedback assíncrono ou estrutura do card.
- Não alterar o RFW para persistir usuários, credenciais ou consentimentos; isso pertence ao Rinos.
- Não mover políticas de senha, retenção, limitação por IP ou auditoria para o RFW.

## Ordem recomendada no RFW

1. Identidade externa tipada e continuação de cadastro Google.
2. Política condicional e action do Turnstile.
3. Cancelamento de cadastro pendente.
4. Aplicação de erros por campo.
5. Documentação, traduções, testes e laboratórios do showroom para todas as mudanças.

## Gate

A Interface Design detalhada deve usar os contratos finais dessas extensões. Implementar wireframes e estados antes da decisão criaria uma especificação baseada em APIs que ainda não existem.

Nenhuma alteração no RFW foi realizada durante esta análise.

