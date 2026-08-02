# Ciclo local destrutivo no MySQL 9

Data da execução: 2026-08-02

## Ambiente e comando

- MySQL Community Server 9.7.2 local;
- schema temporário exclusivo, criado e removido pelo harness de integração;
- scripts oficiais de inicialização global aplicados antes dos testes;
- comando: `mvn "-Dit.test=IdentityRepositoryIT" verify`.

## Provas executadas

| Etapa | Prova no banco real |
|-------|---------------------|
| Cadastro local | usuário, processo pendente, credencial, aceites e prova são persistidos sob as restrições reais do schema |
| Reenvio | a prova anterior deixa de ser utilizável e uma nova prova aberta é persistida para o mesmo processo |
| Ativação | o processo e o usuário chegam aos estados finais e a prova consumida permanece marcada como usada |
| Cancelamento | a raiz temporária e seus dados dependentes são removidos de forma atômica |
| Expiração | a raiz vencida é removida e somente o tombstone sem dados pessoais é preservado |

O conjunto `IdentityRepositoryIT` executou 25 testes, sem falhas, erros ou casos ignorados.

## Correção encontrada pela prova

A primeira execução revelou uma diferença entre a cascata física do MySQL e o estado gerenciado pelo Hibernate: ao
remover diretamente o usuário, o `RegistrationEntity` ainda presente no contexto de persistência referenciava a raiz
já marcada para remoção. O serviço de expiração agora materializa a ordem transacional:

1. transiciona e persiste todos os processos elegíveis como expirados;
2. minimiza os eventos relacionados;
3. marca o processo de cadastro para remoção no contexto JPA;
4. remove o usuário e deixa as FKs do banco eliminarem os demais dependentes;
5. persiste o tombstone sem e-mail, IP ou identificador da raiz removida.

Essa ordem conserva a transição para auditoria durante a transação, respeita a integridade do Hibernate e mantém o
resultado final de retenção previsto no modelo.
