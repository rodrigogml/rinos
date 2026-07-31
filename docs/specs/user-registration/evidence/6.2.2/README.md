# Evidências da tarefa 6.2.2

Data da validação: 2026-07-30

## Escopo exercitado

- permanência na etapa `ACTIVATION` após o cadastro inicial aceito;
- permanência na etapa `ACTIVATION` após o reenvio aceito;
- propagação do tempo de espera quando o reenvio é limitado;
- ativação manual ou por prova opaca delegada ao facade público real do Rinos;
- descoberta das capabilities de cadastro e cancelamento pelos serviços reais do RFW;
- remoção imediata da prova opaca da URL visível e do histórico do navegador;
- retorno canônico e seguro para `/login`, sem repetir a prova por atualização ou navegação de retorno.

O detalhamento temporal localizado do limite pertence à tarefa `6.2.6`. Os demais estados negativos da prova e de
despacho SMTP serão ampliados na tarefa `6.2.5`.

## Validação automatizada focada

```text
mvn -q \
  -Dtest=RFWRegistrationProviderAdapterTest,RFWRegistrationCancellationProviderAdapterTest,\
RFWPlatformIntegrationTest,LoginViewTest \
  test

Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
```

Os testes cobrem a tradução dos resultados reais do domínio para o protocolo do RFW, o consumo do deep link sem
reexposição da prova e a descoberta dos adapters reais pela auto-configuração da plataforma.

## Gate completo

```text
mvn -q verify

Unitários: 253 executados, 0 falhas, 0 erros, 0 ignorados
Integração: 47 encontrados, 0 falhas, 0 erros, 37 ignorados
BUILD SUCCESS
```

Dos 37 testes de integração ignorados, 35 dependem do MySQL descartável ainda indisponível neste ambiente e dois são
os testes de navegador opt-in. A integração HTTP/Vaadin disponível no gate foi executada com sucesso.

> [!NOTE]
> O E2E completo da retomada e a inspeção visual pertencem à tarefa `6.2.8`.
