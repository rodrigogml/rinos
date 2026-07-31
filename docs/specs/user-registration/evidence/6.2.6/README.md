# Evidências da tarefa 6.2.6

Data da validação: 2026-07-31

## Escopo exercitado

- foco inicial na prova quando a ativação é aberta para entrada manual;
- foco inicial na ação **Ativar conta** quando o deep link já forneceu a prova;
- precedência do primeiro campo inválido sobre o foco inicial da etapa;
- foco no primeiro aceite obrigatório da continuação `ACTIVATION_CONSENT`;
- `autocomplete="one-time-code"` no campo de prova, sem `inputmode` numérico incompatível
  com a prova opaca alfanumérica do Rinos;
- mensagem de entrada, destino mascarado e expiração em regiões `status` educadas e
  atômicas, além do feedback de falha na região `alert` assertiva já existente;
- propagação do `Instant` real da comprovação emitida, sem expor prova ou ID interno;
- apresentação da expiração pelo locale de formato `pt-BR` e pelo fuso
  `America/Sao_Paulo`, mantendo o valor de domínio em UTC;
- apresentação de `retryAfter` arredondado para cima, com unidade e singular ou plural
  localizados;
- preservação da estrutura responsiva do `RFWAccessComponent`, com largura fluida,
  campos e ação principal em largura total, ações secundárias flexíveis e breakpoint de
  telefone fornecidos pela folha pública do RFW.

O E2E completo da retomada, as capturas visuais e a inspeção nos diferentes form factors
continuam pertencendo à tarefa `6.2.8`.

## Reavaliação do RFW 2.0

O cadastro continua usando `RFWOpaqueTokenService`: a mesma prova de 256 bits pode ser
transportada pelo link e copiada para o formulário. `RFWOneTimeCodeService` permanece
adequado somente a códigos numéricos curtos e não substitui a prova definida para esta
feature.

Não foi necessário criar formatter, campo ou renderer paralelo no Rinos. A preparação
temporal usa o `RFWVaadinLocalizationService`, e a interação usa exclusivamente o renderer
padrão. A evolução genérica foi implementada, testada e documentada no showroom do RFW no
commit `4a91e16a28072de54c39757f3a3d14604784045a` antes da atualização do ponteiro.

## Validação automatizada focada

```text
RFW Platform:
mvn -q -Dtest=RFWDefaultAccessStepRendererTest,RFWAccessComponentTest test

Tests run: 17, Failures: 0, Errors: 0, Skipped: 0

Rinos:
mvn -q -Dtest=RegistrationStartFacadeImplTest,RegistrationResendFacadeImplTest,
RFWRegistrationProviderAdapterTest,RinosAccessComponentFactoryTest,
RFWPlatformIntegrationTest test

Tests run: 43, Failures: 0, Errors: 0, Skipped: 0
```

Os testes cobrem o foco declarado pelo renderer, a precedência do erro, a semântica do
campo, os atributos das regiões vivas, o arredondamento temporal, a expiração transportada
pelas facades e pelo adapter e a apresentação real em `pt-BR` no fuso configurado.

## Gates completos

```text
RFW Platform: mvn -q verify
Tests run: 148, Failures: 0, Errors: 0, Skipped: 0

RFW Showroom: mvn -q verify
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0

Rinos: mvn -q verify
Unitários: 291, Failures: 0, Errors: 0, Skipped: 0
Integração: 47, Failures: 0, Errors: 0, Skipped: 2
BUILD SUCCESS
```

Os dois testes de integração ignorados são os cenários de navegador opt-in reservados à
tarefa `6.2.8`. As integrações MySQL locais foram executadas no gate desta tarefa.
