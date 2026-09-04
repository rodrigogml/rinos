-- Arquivo de atualização do banco de dados, não pode ser alterado por agentes de IA.

ALTER TABLE identity_authenticationFlow
  DROP CHECK ck_identity_authentication_flow_purpose,
  ADD CONSTRAINT ck_identity_authentication_flow_purpose
    CHECK (purpose IN ('SIGN_IN', 'REAUTHENTICATION', 'FACTOR_RECOVERY', 'LEGAL_CONSENT',
        'REGISTRATION_ACTIVATION'));

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260901001' AS version;
