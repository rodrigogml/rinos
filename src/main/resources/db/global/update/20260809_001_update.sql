-- Arquivo de atualização do banco de dados, não pode ser alterado por agentes de IA.

ALTER TABLE identity_authSession
  ADD COLUMN idAuthenticationFlow BIGINT NULL AFTER idUser,
  ADD COLUMN activatedAt TIMESTAMP(6) NULL AFTER authenticatedAt;

UPDATE identity_authSession
SET activatedAt = authenticatedAt
WHERE status = 'ACTIVE';

ALTER TABLE identity_authSession
  DROP CHECK ck_identity_auth_session_status,
  ADD CONSTRAINT fk_identity_auth_session_flow FOREIGN KEY (idAuthenticationFlow)
    REFERENCES identity_authenticationFlow (id)
    ON DELETE SET NULL
    ON UPDATE RESTRICT,
  ADD CONSTRAINT uk_identity_auth_session_flow UNIQUE (idAuthenticationFlow),
  ADD CONSTRAINT ck_identity_auth_session_status
    CHECK (status IN ('PREPARED', 'ACTIVE', 'REVOKED', 'EXPIRED')),
  ADD CONSTRAINT ck_identity_auth_session_activation
    CHECK (
      (status = 'PREPARED' AND activatedAt IS NULL)
      OR status IN ('REVOKED', 'EXPIRED')
      OR (status = 'ACTIVE' AND activatedAt IS NOT NULL)
    );

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260809001' AS version;
