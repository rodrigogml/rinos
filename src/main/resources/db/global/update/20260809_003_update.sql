-- Acrescenta a autoridade persistente de validade e tentativas ao enrollment TOTP.

ALTER TABLE identity_totpFactor
  ADD COLUMN enrollmentExpiresAt TIMESTAMP(6) NULL AFTER status,
  ADD COLUMN attemptCount INT NOT NULL DEFAULT 0 AFTER enrollmentExpiresAt;

UPDATE identity_totpFactor
SET enrollmentExpiresAt = DATE_ADD(createdAt, INTERVAL 5 MINUTE)
WHERE enrollmentExpiresAt IS NULL;

ALTER TABLE identity_totpFactor
  MODIFY COLUMN enrollmentExpiresAt TIMESTAMP(6) NOT NULL,
  ADD CONSTRAINT ck_identity_totp_factor_attempts CHECK (attemptCount >= 0);

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260809003' AS version;
