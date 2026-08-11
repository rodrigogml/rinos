# Evidência 5.1.3 — estados, reentrada, indisponibilidade e limpeza

## Escopo

O fluxo de login é renderizado pelo `RFWAccessComponent`; o Rinos não mantém um formulário paralelo. A cobertura do RFW verifica as transições entre login, segundo fator, consentimento, ativação, resultado e rejeição, além de submissões assíncronas ocupadas.

## Evidências executadas

- `RFWAccessComponentTest`: submissão de senha/passkey, seleção e reenvio de segundo fator, rejeição, rate limit, provider indisponível, consentimento, replay e limpeza de e-mail/aceites preservados.
- A flag `busy` desabilita a etapa durante cada operação; uma nova submissão não é iniciada pelo renderer enquanto a operação está pendente.
- O componente preserva somente e-mail e IDs de documentos quando a rejeição é recuperável; senhas, provas, tokens e credenciais não integram o estado preservado.
- A telemetria foi adicionada como contrato sanitizado, sem alterar o fluxo quando o callback falha.

## Comando

```text
cd modules/RFW.Platform
mvn -q -DskipITs -Dtest=RFWAccessComponentTest,RFWAccessComponentConfigTest test
```

Resultado: sucesso (28 testes em `RFWAccessComponentTest` e testes de configuração).
