-- Arquivo de atualização do banco de dados, não pode ser alterado por agentes de IA.

ALTER TABLE identity_externalIdentity
  ADD COLUMN reference BINARY(16) NULL AFTER idUser,
  ADD COLUMN revokedAt TIMESTAMP(6) NULL AFTER lastUsedAt;

UPDATE identity_externalIdentity
SET reference = UUID_TO_BIN(UUID())
WHERE reference IS NULL;

ALTER TABLE identity_externalIdentity
  MODIFY COLUMN reference BINARY(16) NOT NULL,
  ADD CONSTRAINT uk_identity_external_identity_reference UNIQUE (reference);

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260810001' AS version;
