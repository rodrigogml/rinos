-- Correção da representação hexadecimal do hash da Política de Privacidade 1.0.0.

UPDATE identity_legalDocumentVersion
SET contentHash = UNHEX('33D468F0C3E0016C2376BA3299585EA11F02919B9400903C8D11114F48F076A9')
WHERE documentType = 'PRIVACY_POLICY' AND versionName = '1.0.0';

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260812002' AS version;
