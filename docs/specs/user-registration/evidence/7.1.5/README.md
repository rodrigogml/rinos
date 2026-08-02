# Estados da interface derivados do MySQL

Data da execução: 2026-08-02

## Escopo comprovado

`RegistrationRoundtripIT` monta o `RFWAccessComponent` com adapters, facades, serviços transacionais, repositories e
MySQL 9 reais. Somente fronteiras que não pertencem ao estado persistido são controladas: resposta HIBP, transporte
SMTP, identidade Google já validada e publicação da sessão autenticada.

| Interface | Estados e transições derivados de dados reais |
|-----------|-------------------------------------------------|
| INT-WEB-REG-001 | `initial`/`ready` com documentos vigentes; erro de senha; HIBP indisponível; e-mail existente; limite por origem; sucesso que cria a pendência e abre ativação |
| INT-WEB-REG-002 | prova inválida; reenvio que invalida a prova anterior; ativação; `partial-stale` causado pela troca real das versões legais e conclusão com novos aceites |
| INT-WEB-REG-003 | continuação Google emitida no MySQL; aceite ausente; documentos substituídos durante a sessão; rejeição dos IDs antigos; atualização visual do catálogo; ativação sem credencial local |
| INT-WEB-REG-004 | solicitação neutra de cancelamento que emite prova somente para a pendência real |
| INT-WEB-REG-005 | prova inválida sem efeito; prova válida que remove a raiz temporária e preserva apenas o tombstone auditável |

O teste confirma também os estados finais de usuário, cadastro, identidade Google, credencial local, consentimentos,
provas, eventos e janelas por origem. Assim, a UI não recebe sucesso ou rejeição fabricados por mocks de provider.

## Estados não orientados a dados

`loading`, `empty` e `access-denied` são declarados como não aplicáveis nas interfaces correspondentes.
`processing` e `offline` pertencem ao ciclo transitório do componente e da conexão Vaadin; continuam cobertos pelos
testes do RFW e pelos E2E de apresentação, pois não existe um registro de banco que deva produzi-los.

## Correção revelada pela prova

A execução encontrou que o componente mantinha uma fotografia fixa dos documentos legais. O RFW passou a oferecer
`legalDocumentsProvider(...)`, compatível com `legalDocuments(List)`, e o showroom documenta e demonstra o contrato.
O Rinos reconsulta sua facade em cada renderização legal, portanto uma versão publicada durante a sessão substitui a
anterior antes de outro aceite.

## Execuções

- `mvn install` no RFW: 318 testes aprovados;
- `mvn test` no showroom: 21 testes aprovados;
- `mvn "-Dtest=RinosAccessComponentFactoryTest" "-Dit.test=RegistrationRoundtripIT" verify` no Rinos:
  4 testes unitários e 6 testes integrados aprovados contra MySQL Community Server 9.7.2;
- `mvn verify` no Rinos: 326 testes unitários e 70 testes integrados aprovados; 12 E2E opt-in ignorados conforme
  configuração padrão;
- schemas temporários exclusivos removidos pelo harness;
- nenhuma falha, erro ou teste ignorado nos escopos selecionados.
