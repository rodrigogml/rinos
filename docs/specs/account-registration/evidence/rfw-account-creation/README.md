# Evidência — Operação RFW de Criação de Conta

Em 2026-08-15, após autorização explícita:

- a revisão RFW `ba1bfda` adicionou `ACCOUNT_CREATION` e a action estável `account-creation`;
- todas as actions existentes foram preservadas pelo novo accessor público `action()`;
- testes direcionados e a suíte completa do core RFW passaram;
- a documentação do showroom foi atualizada em português, inglês, espanhol, francês, italiano e chinês;
- a suíte isolada do showroom passou;
- `RFWAccountHumanVerificationAdapterTest` comprovou action/idempotência e falha segura no Rinos.
