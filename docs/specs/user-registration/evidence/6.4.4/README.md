# Evidências da tarefa 6.4.4

Data da validação: 2026-08-01

## Escopo validado

A solicitação de cancelamento mantém o identificador somente no estado transitório da intenção atual quando uma
rejeição permite correção ou nova tentativa. Uma reconstrução do renderer restaura esse valor, mas sair da etapa por
uma nova intenção pública elimina o estado preservado. O identificador não é persistido, incluído em telemetria ou
transferido para estado serializável da sessão.

Uma solicitação aceita transporta o identificador apenas como contexto transitório necessário à confirmação; isso não
constitui restauração de formulário rejeitado nem armazenamento durável.

## Descarte da prova humana

O renderer público consome o token por `RFWTurnstileComponent.consumeToken()` antes da validação assíncrona. A operação
remove o token no servidor e reinicia o widget no navegador. Depois de uma rejeição da fachada, o renderer cria outro
widget vazio. Uma segunda submissão sem resolver o novo desafio é rejeitada pela comprovação humana e não chama a
fachada de cancelamento novamente.

O teste usa a factory e o componente RFW reais, o adapter real do Rinos e providers controlados somente nas fronteiras
externas. Ele comprova, na mesma jornada:

1. envio do token exclusivamente à operação `REGISTRATION_CANCELLATION`;
2. preservação do identificador editado após rejeição recuperável;
3. ausência do token tanto no widget consumido quanto no widget renovado;
4. impossibilidade de a tentativa seguinte reutilizar o token;
5. remoção do identificador ao voltar para o login.

> [!IMPORTANT]
> Não houve alteração na RFW Platform. A revisão
> `7d47fe735d181acde035c6aa22c8e1dd6c0c7c17` já publica o contrato necessário e permaneceu limpa. A tarefa acrescenta
> cobertura e rastreabilidade na aplicação hospedeira.

## Validação automatizada focada

```powershell
mvn '-Dtest=RFWPlatformIntegrationTest' test
```

```text
Testes unitários: 16; 0 falhas; 0 erros; 0 ignorados
BUILD SUCCESS
```

## Gate completo do Rinos

```powershell
mvn verify
```

```text
Testes unitários:      314; 0 falhas; 0 erros; 0 ignorados
Testes de integração:   54; 0 falhas; 0 erros; 8 E2E opt-in ignorados
BUILD SUCCESS
```

O gate incluiu os cenários MySQL 9, migração global, origem exclusiva das propriedades e bootstrap HTTP Vaadin. A
tarefa não altera schema, contratos persistentes nem configuração operacional.
