-- Arquivo de atualização do banco de dados, não pode ser alterado por agentes de IA.

ALTER TABLE identity_authenticationFlowMethod
  ADD COLUMN state VARCHAR(24) NULL AFTER method,
  ADD COLUMN verifiedAt TIMESTAMP(6) NULL AFTER state,
  ADD COLUMN userVerification BOOLEAN NULL AFTER verifiedAt;

UPDATE identity_authenticationFlowMethod
SET state = 'PERMITTED'
WHERE state IS NULL;

ALTER TABLE identity_authenticationFlowMethod
  MODIFY COLUMN state VARCHAR(24) NOT NULL,
  ADD CONSTRAINT ck_identity_authentication_flow_method_state
    CHECK (state IN ('PERMITTED', 'VERIFIED')),
  ADD CONSTRAINT ck_identity_authentication_flow_method_verification
    CHECK (
      (state = 'PERMITTED' AND verifiedAt IS NULL AND userVerification IS NULL)
      OR (state = 'VERIFIED' AND verifiedAt IS NOT NULL)
    );

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260808002' AS version;
