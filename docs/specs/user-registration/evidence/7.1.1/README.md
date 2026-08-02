# Matriz executável dos 20 cenários do quickstart

Data do inventário inicial: 2026-08-02

## Regra de fechamento

Um cenário só é considerado automatizado quando todos os seus efeitos observáveis estão cobertos no nível adequado.
Testes unitários complementares não substituem um roundtrip exigido pelo próprio cenário. Gates dependentes do
ambiente de produção podem ter harness automatizado aqui e execução final na tarefa operacional especializada.

## Matriz atual

| Cenário | Automação principal | Situação |
|--------:|--------------------|----------|
| 1 | E2E local/ativação; ciclo MySQL em `IdentityRepositoryIT`; roundtrip integrado em `RegistrationRoundtripIT` | Automatizado |
| 2 | E2E de erros/foco; `PasswordPolicyServiceTest`; validação pré-escrita da facade | Automatizado; releitura integrada em 7.1.5 |
| 3 | `PwnedPasswordsServiceIT`; `PasswordPolicyServiceTest`; falha fechada da facade | Automatizado |
| 4 | `IdentityRepositoryIT.save_shouldChooseOneUser...`; convergência da facade | Automatizado |
| 5 | `RegistrationStartFacadeImplTest.start_shouldReturnExistingEmail...`; adapter RFW | Automação funcional pronta; ação de recuperação depende de `user-authentication` |
| 6 | `VerificationServiceTest`; `RegistrationActivationServiceTest`; concorrência MySQL da prova | Automatizado |
| 7 | `RegistrationResendServiceTest`; facade e adapter de reenvio | Automatizado |
| 8 | `TurnstileIntegrationIT`; `OriginLimitServiceTest`; limites e retenção MySQL | Automatizado |
| 9 | `TrustedProxyServiceTest`; facade e adapter de origem | Automatizado |
| 10 | serviços Google; resolução e conclusão MySQL; E2E Google simulado | Automatizado; prova consolidada em 7.1.3 |
| 11 | reutilização da pendência no MySQL; preservação pré-aceite e remoção transacional da credencial | Automatizado; prova consolidada em 7.1.3 |
| 12 | usuário ativo sem vínculo em serviço, facade, adapter e MySQL | Automatizado; prova consolidada em 7.1.3 |
| 13 | `RegistrationActivationServiceTest`; continuação de aceite e adapter RFW | Automatizado |
| 14 | solicitação/limite/Turnstile; cancelamento concorrente MySQL; E2E desktop/telefone | Automatizado |
| 15 | coordenação, fencing, timeout e takeover MySQL; limpezas e catálogo unitários | Automatizado |
| 16 | `RegistrationRoundtripIT` percorre UI RFW → adapter → facade → serviços → MySQL e inspeciona os tipos públicos | Automatizado |
| 17 | E2E teclado/touch e semântica estrutural | Leitor de tela e fluxo real seguem 7.3 e 7.1.5 |
| 18 | dispatch pós-commit, falha, reenvio explícito e ausência de segredo | Automatizado; execução integrada em 7.4 |
| 19 | ferramenta de calibração, ≥50 amostras, percentis, pisos e redação de segredos | Harness automatizado; execução no servidor em 7.5.6 |
| 20 | métricas e despacho individual | Falta harness de 100 cadastros; 7.4.1–7.4.2 |

## Próximas provas que fecham esta tarefa

1. exercer os estados de interface sobre resultados reais (7.1.5);
2. implementar e executar o harness SMTP nominal de 100 operações (7.4.1).

> [!IMPORTANT]
> A tarefa 7.1.1 permanece em andamento até essas provas existirem. A revisão jurídica, o leitor de tela humano, o
> SMTP real e a calibração no servidor continuam gates externos próprios e não são artificialmente aprovados aqui.
