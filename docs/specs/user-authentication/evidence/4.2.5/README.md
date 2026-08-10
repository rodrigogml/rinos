# Evidência da tarefa 4.2.5

## Resultado

O boundary de persistência WebAuthn rejeita e audita alterações incompatíveis com a credential registrada sem
revogar automaticamente a passkey observada, outras credenciais, sessões ou métodos de autenticação do usuário.

## Controles implementados

- credential ou owner inativo, owner divergente e estado de backup impossível são recusados antes de qualquer
  alteração;
- tipo, chave pública, inicialização de verificação, elegibilidade de backup, transports, attestation e nome são
  tratados como material imutável nessa borda;
- regressão ou repetição de contador positivo é tratada como risco de replay ou clonagem; zero continuado permanece
  aceito para autenticadores que não implementam contador confiável;
- o evento `PASSKEY_RISK_DETECTED` guarda somente usuário, correlação aleatória, instante e motivo catalogado;
- a auditoria usa uma transação independente, permanecendo registrada quando a assertion rejeitada reverte sua
  transação principal;
- nenhuma dessas detecções chama exclusão ou revogação automática.

## Validação reproduzível

```powershell
mvn -q -DskipITs -DskipTests=false "-Dtest=SpringWebAuthnCredentialRepositoryAdapterTest,PasskeyRiskAuditServiceTest" test
mvn -q -DskipITs=false "-Dit.test=SpringWebAuthnRepositoryAdapterIT" verify
mvn -q verify
```

O teste de integração MySQL repete um contador positivo dentro de uma transação destinada a falhar e comprova que a
credential mantém contador e estado, enquanto o evento de risco persiste após o rollback. Os testes unitários cobrem
material público divergente, replay de contador, tentativa sobre credential revogada e sanitização da auditoria.
