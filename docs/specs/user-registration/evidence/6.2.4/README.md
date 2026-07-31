# Evidências da tarefa 6.2.4

Data da validação: 2026-07-30

## Escopo exercitado

- minimização do e-mail antes de cruzar o contrato público da continuação de ativação;
- preservação somente do primeiro caractere do local para endereços com mais de dois caracteres,
  como `person@example.com` em `p***@example.com`;
- ocultação integral de locais com até dois caracteres, como `ab@example.com` em
  `***@example.com`;
- preservação do e-mail completo apenas no domínio e na persistência, sem alterar o endereço
  cadastrado;
- apresentação do e-mail mascarado em `EmailField` somente leitura pelo renderer padrão do RFW;
- seleção exata dos documentos contidos no `RFWActivationConsentChallengeVO`, mesmo quando o
  catálogo vigente também contém outros documentos obrigatórios e opcionais;
- ausência do e-mail completo e da referência opaca de ativação no conteúdo visível;
- comportamento fechado do renderer do RFW quando alguma referência exigida não está no catálogo
  fornecido pela hospedeira.

O teste de componente usa a factory pública real do RFW e a composição jurídica do Rinos. Nenhuma
alteração ou renderer paralelo foi criado no submódulo.

## Validação automatizada focada

```text
mvn -q \
  -Dtest=EmailPrivacyServiceTest,RegistrationActivationServiceTest,\
RFWRegistrationProviderAdapterTest,RFWActivationConsentProviderAdapterTest,\
RFWPlatformIntegrationTest,RinosAccessComponentFactoryTest \
  test

Tests run: 34, Failures: 0, Errors: 0, Skipped: 0
```

Além das fronteiras do mascaramento, a seleção dos documentos é verificada na árvore real de
componentes Vaadin por seus atributos e links públicos.

## Gate completo

```text
mvn -q verify

Unitários: 260 executados, 0 falhas, 0 erros, 0 ignorados
Integração: 47 encontrados, 0 falhas, 0 erros, 37 ignorados
BUILD SUCCESS
```

Dos 37 testes de integração ignorados, 35 dependem do MySQL descartável ainda indisponível neste
ambiente e dois são os testes de navegador opt-in. A integração HTTP/Vaadin disponível no gate foi
executada com sucesso.

> [!NOTE]
> Os estados públicos de prova e SMTP pertencem à tarefa `6.2.5`. A inspeção visual e o E2E de
> retomada completos permanecem na tarefa `6.2.8`.
