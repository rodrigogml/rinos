# Evidências da fase 5.3

**Data**: 2026-08-16
**Escopo**: INT-WEB-ACL-004 e INT-WEB-ACL-005

## Entregas

- painel de explicação por sujeito e conjunto de chaves, com gates, plano, autenticação, permissões, bloqueios,
  vigências e condição decisiva em mensagens localizadas;
- códigos de chave, referências de origem e motivos internos não são renderizados nem copiados;
- prévia executa o mesmo comando em transação descartável e calcula administradores aptos antes/depois em todas as
  fronteiras temporais conhecidas;
- confirmação usa os contratos públicos RFW para TOTP/passkey e reexecuta autorização, continuidade e revisão;
- conflito entre prévia e confirmação fecha editores e exige fotografia nova;
- foco inicial, contenção e retorno são fornecidos pelo `Dialog` Vaadin, com título focável e feedback `aria-live`.

## Arquivos centrais

- `AccessCenterComponent`, `AccessReauthenticationFlow` e views global/tenant;
- `AccessAdministrationPreview`, `AccessAdministrationPreviewExecutor` e facade administrativa;
- `AdministrativeContinuitySnapshot` e avaliador de continuidade detalhado;
- `ReauthenticationOperationEnum`, com operações ACL limitadas a TOTP/passkey;
- `AccessAdministrationPreviewExecutorTest`, `ReauthenticationPolicyServiceTest` e
  `AccessCenterComponentTest`.

## Validação

- `mvn test`: 736 testes, sem falhas ou erros;
- persistência/MySQL: 133 testes de integração concluídos, sem falhas ou erros (18 skips condicionais);
- teste de rollback obrigatório e contagens antes/depois: aprovado;
- teste de bloqueio quando o último administrador seria perdido: aprovado;
- teste do catálogo de reautenticação forte sem senha: aprovado;
- inspeção de UI sem renderização de código técnico: aprovado.
