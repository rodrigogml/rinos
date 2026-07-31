# Evidências da tarefa 6.2.3

Data da validação: 2026-07-30

## Escopo exercitado

- tradução do resultado `CONSENT_REQUIRED` do Rinos para `ACTIVATION_CONSENT_REQUIRED` do RFW;
- composição da etapa `ACTIVATION_CONSENT` pelo renderer padrão da plataforma, sem tela local paralela;
- descoberta do `RFWActivationConsentProviderAdapter` e da capability `ACTIVATION_CONSENT` pela auto-configuração real;
- envio ao facade público somente da referência opaca, dos IDs explicitamente aceitos e de uma nova correlação técnica;
- conclusão da ativação depois dos aceites;
- permanência em `ACTIVATION_CONSENT` com challenge atualizado quando outra versão obrigatória muda antes da conclusão;
- preservação da mesma prova ainda aberta, sem segunda credencial ou identificador interno em URL.

A implementação transacional do domínio e o adapter público já haviam sido entregues pelas tarefas de fundação. Esta
tarefa consolidou sua composição na superfície RFW e acrescentou a cobertura de integração que impede a capability de
desaparecer silenciosamente.

## Validação automatizada focada

```text
mvn -q \
  -Dtest=RFWRegistrationProviderAdapterTest,RFWActivationConsentProviderAdapterTest,\
RinosAccessComponentFactoryTest,RFWPlatformIntegrationTest,RegistrationActivationFacadeImplTest,\
RegistrationActivationServiceTest \
  test

Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
```

Os testes percorrem o domínio transacional, a facade, os adapters, a configuração dos documentos e a descoberta dos
providers pela auto-configuração efetiva do RFW.

## Gate completo

```text
mvn -q verify

Unitários: 254 executados, 0 falhas, 0 erros, 0 ignorados
Integração: 47 encontrados, 0 falhas, 0 erros, 37 ignorados
BUILD SUCCESS
```

Dos 37 testes de integração ignorados, 35 dependem do MySQL descartável ainda indisponível neste ambiente e dois são
os testes de navegador opt-in. A integração HTTP/Vaadin disponível no gate foi executada com sucesso.

> [!NOTE]
> A verificação detalhada do e-mail somente leitura e da seleção exata de documentos pertence à tarefa `6.2.4`. O E2E
> e a inspeção visual completa pertencem à tarefa `6.2.8`.
