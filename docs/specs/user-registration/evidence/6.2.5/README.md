# Evidências da tarefa 6.2.5

Data da validação: 2026-07-30

## Escopo exercitado

- prova desconhecida ou estruturalmente inválida produz rejeição pública própria;
- prova expirada permanece distinta da prova inválida e não inicia qualquer transição de lifecycle;
- replay de prova usada é reconhecido pelo cadastro já ativado, não autentica novamente e não repete
  os efeitos da ativação;
- prova válida associada a cadastro cancelado, marcado como expirado ou além da validade absoluta
  produz o novo resultado público `REGISTRATION_CLOSED`;
- cadastro encerrado orienta nova inscrição sem revelar e-mail, ID interno ou estado adicional da
  identidade;
- falha SMTP no cadastro inicial preserva a pendência e não afirma que a mensagem foi enviada;
- falha SMTP no reenvio permanece rejeição recuperável, enquanto uma tentativa posterior explícita
  ainda pode emitir nova comprovação;
- todas as mensagens públicas de ativação e SMTP são resolvidas em `pt-BR` pelo serviço i18n
  efetivamente usado pelo RFW.

O renderer padrão do RFW já mantém a etapa corrente e apresenta o `RFWAccessErrorVO` fornecido pelo
Rinos. Não foi necessário modificar ou substituir qualquer componente da plataforma.

## Validação automatizada focada

```text
mvn -q \
  -Dtest=RegistrationActivationServiceTest,RFWRegistrationProviderAdapterTest,\
RegistrationActivationFacadeImplTest,RegistrationResendFacadeImplTest,\
RFWPlatformIntegrationTest \
  test

Tests run: 39, Failures: 0, Errors: 0, Skipped: 0
```

Os testes percorrem a classificação transacional, o contrato público, a tradução para outcomes do
RFW, a ausência de autenticação no replay e a disponibilidade real das mensagens localizadas.

## Gate completo

```text
mvn -q verify

Unitários: 269 executados, 0 falhas, 0 erros, 0 ignorados
Integração: 47 encontrados, 0 falhas, 0 erros, 37 ignorados
BUILD SUCCESS
```

Dos 37 testes de integração ignorados, 35 dependem do MySQL descartável ainda indisponível neste
ambiente e dois são os testes de navegador opt-in. A integração HTTP/Vaadin disponível no gate foi
executada com sucesso.

> [!NOTE]
> A validação detalhada de foco, anúncios e localização temporal pertence à tarefa `6.2.6`. O E2E
> completo e a inspeção visual dos estados permanecem na tarefa `6.2.8`.
