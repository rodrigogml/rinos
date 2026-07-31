# Evidências da tarefa 6.2.1

Data da validação: 2026-07-30

## Escopo exercitado

- entrada manual por `/login?step=activation`;
- deep link por `/login?step=activation&proof=...`;
- conversão da entrada pública em `RFWAccessEntryRequestVO` para a etapa `ACTIVATION`;
- ausência de e-mail e IDs internos na URL;
- fallback seguro ao login para intenção desconhecida, parâmetros repetidos ou prova excessivamente grande;
- geração da URL absoluta a partir de `rinos.application.public-base-url`;
- apresentação da mesma prova opaca de 256 bits no link e como código copiável no e-mail;
- persistência restrita ao hash já definido, sem código curto ou segundo segredo;
- escaping do conteúdo interpolado no template e redação da prova no `toString()` do pedido de envio.

Esta tarefa compõe a entrada da tela e o conteúdo do e-mail. O consumo da prova, reenvio, limitação, cancelamento,
navegação pós-ativação e inspeção visual completa permanecem nas subtarefas seguintes de `6.2`.

## Validação automatizada focada

```text
mvn -q \
  -Dtest=LoginViewTest,RinosAccessComponentFactoryTest,PublicApplicationUriServiceTest,\
VerificationEmailDispatchServiceTest,RegistrationResendServiceTest \
  test

Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
```

Os testes verificam as duas formas de entrada, parâmetros inválidos ou repetidos, URL de produção e desenvolvimento,
renderização do link e do código, escaping HTML, não exposição em logs e propagação da prova em reenvios.

## Gate completo

```text
mvn -q verify

Unitários: 250 executados, 0 falhas, 0 erros, 0 ignorados
Integração: 47 encontrados, 0 falhas, 0 erros, 37 ignorados
BUILD SUCCESS
```

Dos 37 testes de integração ignorados, 35 dependem do MySQL descartável ainda indisponível neste ambiente e dois são
os testes de navegador opt-in. A integração HTTP/Vaadin disponível no gate foi executada com sucesso.

> [!NOTE]
> A inspeção visual e o E2E completo do deep link pertencem à tarefa `6.2.8`; não são antecipados por esta composição.
