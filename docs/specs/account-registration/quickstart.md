# Quickstart — Persistência Básica de Conta

## 1. Aceitar uma intenção

Pré-condições: identidade ativa, garantia recente, origem permitida e prova antiabuso quando exigida.

```text
idempotencyKey = 14f0... (UUID novo para esta intenção)
displayName     = Oficina Aurora
baseCurrency   = BRL
timeZoneId     = America/Sao_Paulo
confirmed      = true
```

Resultado esperado: `ACCEPTED`, protocolo opaco, conta `CREATING`, tenant `RESERVED`, auditoria e outbox pendente.

## 2. Repetir com o mesmo payload

Envie a mesma chave e os mesmos valores. Resultado: `REPLAYED`, mesmos protocolo e conta, sem novas linhas funcionais,
auditoria ou outbox duplicada.

## 3. Reutilizar a chave com payload diferente

Altere moeda, fuso ou nome mantendo a chave. Resultado: `CONFLICT` e `ACCOUNT_IDEMPOTENCY_CONFLICT`; a conta original
não muda.

## 4. Consultar status

O criador consulta pelo protocolo e recebe somente estágio público e dados seguros. Outro usuário recebe negação sem
confirmação da existência.

## 5. Falha antes do aceite

Token Turnstile ausente/inválido, identidade inativa, garantia antiga ou entrada inválida não criam tenant, conta,
intenção, auditoria de sucesso nem outbox.

## 6. Consumidor indisponível

Se `tenant-storage-provisioning` ainda não estiver conectado, a conta permanece `CREATING` e a outbox `PENDING`. O
sistema não marca a conta ativa e pode retomar o evento quando o adapter existir.

## 7. Verificação MySQL

O gate de integração deve comprovar:

- unicidade de UUID e `(creator,idempotencyKey)`;
- FK e relação 1:1 conta/tenant;
- rollback conjunto dos cinco registros do aceite;
- conflito otimista de atualização;
- claim concorrente da outbox;
- checks de estado e `databaseVersion`.
