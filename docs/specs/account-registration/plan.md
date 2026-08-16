# Plano Técnico — Cadastro de Contas

**Status**: quality gate documental concluído; persistência básica implementada
**Escopo inicial implementável**: aceite durável da criação e persistência global básica

## Objetivo do primeiro slice

Entregar a autoridade global de conta/tenant e aceitar uma criação idempotente sem anunciar conta ativa. O slice
termina com conta `CREATING`, protocolo consultável, auditoria e outbox pendente. Ativação somente entra em outro slice
depois que storage, membership, ACL e plano publicarem adapters concretos.

## Componentes

```text
UI/API adapter
  -> AccountCreationFacade
      -> IdentityStatePort / garantia / antiabuso
      -> AccountCreationService
          -> account + tenant + intent + audit + outbox (uma transação global)
  -> AccountCreationStatusFacade

AccountProvisioningOutboxDispatcher
  -> TenantProvisioningRequestPort
  -> confirmações posteriores da saga
```

## Fronteiras

- `api.module.account`: facades, comandos, resultados e enums seguros.
- `backend.module.account`: entidades, repositories, serviços e adapters de infraestrutura.
- `backend.module.identity`: continua autoridade do estado global e garantia.
- `backend.module.access`: decide manutenção posterior; não persiste conta.
- `tenant-storage-provisioning`, `account-membership` e `plans-entitlements`: dependências por portas, sem imports de
  entities/repositories.

## Fluxo de aceite

1. Adapter deriva a identidade autenticada, garantia e origem confiável.
2. Valida identidade ativa, autenticação recente, limite rígido e Turnstile quando exigido.
3. Normaliza nome; valida ISO 4217 suportada e `ZoneId` IANA.
4. Calcula hash canônico do payload.
5. Sob transação, procura `(creator, idempotencyKey)`.
6. Mesmo hash retorna protocolo existente; hash diferente retorna conflito.
7. Cria tenant, conta `CREATING`, intenção, auditoria e evento outbox.
8. Depois do commit, dispatcher pode antecipar o processamento; a linha durável continua sendo a autoridade.

## Transações e falhas

- Nenhum registro é criado antes da prova obrigatória e das validações.
- Conta, tenant, intenção, auditoria e outbox confirmam ou revertem juntos.
- Falha de publicação não remove a outbox; backoff e retomada são persistidos.
- Payload conflitante nunca revela outra conta além da associada ao próprio criador.
- Indisponibilidade interna resulta em status seguro e nenhum sucesso artificial.

## Segurança e privacidade

- Tokens, prova de reautenticação, IP puro e detalhes internos de storage não entram em entidades, DTOs de status ou logs.
- Auditoria registra ator, correlação, resultado, conta/tenant e campos alterados minimizados.
- UUIDs públicos evitam enumeração por IDs sequenciais.
- Consultas sempre restringem por ator autorizado ou contexto tenant explicitamente validado.

## Observabilidade

Métricas separadas: aceitação, idempotent replay, conflito, rejeição antiabuso, outbox pendente, tentativas, idade do
evento mais antigo e tempo até cada etapa. Correlação usa protocolo público; payload e prova permanecem redigidos.

## Entrega incremental

1. Contratos e configuração.
2. Schema global e persistência básica.
3. Serviço idempotente de aceite e consulta de status.
4. Outbox e porta de provisionamento.
5. Integração membership + bootstrap ACL.
6. Integração plano padrão e ativação.
7. Manutenção/lifecycle autorizados.
8. Interface e validação E2E.

## Constitution check

- Isolamento: tenant é identidade explícita desde o aceite.
- Autorização: negação por padrão; papel fundador não concede chaves.
- Integridade: outbox, idempotência, histórico e migrações RFW.
- RFW: recursos públicos reutilizados; submódulo intocado.
- Qualidade: schema/código somente após checklists documentais.
