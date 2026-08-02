# Roundtrip transacional sem exposição de entidade

Data da execução: 2026-08-02

## Caminho comprovado

`RegistrationRoundtripIT` executa uma única submissão por este caminho:

1. `RFWAccessComponent.submitRegistration(...)` na UI pública da plataforma;
2. descoberta do `RFWRegistrationProvider` real pela capacidade do RFW;
3. `RFWRegistrationProviderAdapter`, que converte exclusivamente DTOs/VOs públicos;
4. `RegistrationStartFacade` e serviços de domínio reais;
5. transação Spring real de `RegistrationCreationService`;
6. repositories JPA e constraints dos scripts oficiais no MySQL 9;
7. resultado público devolvido ao componente, que avança para ativação.

Somente duas integrações externas são simuladas: a resposta segura da consulta HIBP e o transporte SMTP. A mensagem
SMTP final continua sendo montada pelo coordenador real e só é entregue ao simulador após o commit.

## Efeitos verificados

- usuário e cadastro local em `PENDING_VERIFICATION`;
- hash com identificador `{argon2id}` e sem senha em claro;
- duas decisões legais vinculadas às versões realmente consultadas pela UI;
- prova de e-mail aberta;
- evento `REGISTRATION_STARTED`;
- reserva persistida da janela por origem;
- mensagem pós-commit destinada ao e-mail cadastrado e com URL canônica de ativação;
- UI no estado `ACTIVATION_REQUIRED`.

O teste também percorre assinaturas públicas de `RegistrationStartFacade` e
`RFWRegistrationProviderAdapter`; qualquer tipo cujo nome contenha o pacote `.entity.` faz a prova falhar. Essa
asserção complementa o gate arquitetural global de `PublicContractSecurityTest`.

## Execução

Comando: `mvn "-Dit.test=RegistrationRoundtripIT" verify`

- MySQL Community Server 9.7.2;
- schema temporário exclusivo e removido pelo harness;
- 319 testes unitários aprovados;
- 1 roundtrip integrado aprovado;
- nenhuma falha, erro ou teste ignorado no escopo selecionado.
