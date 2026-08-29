# Security Checklist: Provisionamento do Armazenamento de Tenant

**Propósito**: validar a qualidade dos requisitos de isolamento, segredo, autorização e falha segura.
**Criado**: 2026-08-29
**Feature**: [spec.md](../spec.md)

## Isolamento e entrada

- [x] CHK001 - O nome físico é exclusivamente gerado, validado e mantido fora da UI, contratos e entradas externas? [Completude, FR-TSP-ID-003..006 e SEC-006; Research §Decisão 1] {auto}
- [x] CHK002 - A prontidão física está separada de autorização, identidade, associação e entitlement? [Consistência, Constitution I-II; Research §Decisão 7] {auto}
- [x] CHK003 - Tenant ausente, divergente, falho ou incompatível possui negação fechada antes de operação comum? [Cobertura, FR-TSP-STATE-002..010 e SEC-005] {auto}

## Credenciais, banco e dados

- [x] CHK004 - A origem única de credencial está definida e proíbe root, segredo versionado, URL paralela e acesso a schemas alheios? [Segurança, Research §Decisão 2; Plan §Segurança] {auto}
- [x] CHK005 - O requisito de privilégio mínimo para `CREATE DATABASE` e DDL do padrão `rinos\_%` está explícito sem `GRANT OPTION` ou privilégio administrativo? [Clareza, Research §Decisão 2] {auto}
- [x] CHK006 - Logs, auditoria, eventos e mensagens excluem schema, host, URL, SQL, credencial, stack trace e dados de tenant? [Proteção de dados, FR-TSP-ID-006, SEC-007, OPS-006; Contracts] {auto}

## Autorização e operações sensíveis

- [x] CHK007 - Ações administrativas distinguem consulta, reconciliação e desativação e exigem contexto global, chave e garantia forte quando aplicável? [Cobertura, FR-TSP-SEC-001..008; Interface §INT-WEB-TSP-003] {auto}
- [x] CHK008 - A interface proíbe caminhos humanos para executar/repetir/ignorar migration, backup e restauração? [Consistência, FR-TSP-MIG-018, INFRA-BACKUP; Interface §Regras compartilhadas] {auto}

## Resiliência e auditoria

- [x] CHK009 - Leases, locks, etapas e auditoria exigidos para retomada cross-instance estão descritos sem confiar em memória local? [Cobertura, FR-TSP-INFRA-LOCK/IDEMP/RECOVERY; Plan §Transações] {auto}
- [x] CHK010 - Falhas de migration não recebem retry automático, rollback ou promoção manual de estado? [Consistência, FR-TSP-MIG-011..020; Research §Decisão 5] {auto}

## Notas

- Nenhum requisito demanda criptografia do IP, de segredo ou de dados funcionais no registro de storage; esses dados não
  pertencem ao modelo desta feature.
- A comprovação prática de grants e a revisão de permissões ocorrem somente em ambiente controlado da implementação.
