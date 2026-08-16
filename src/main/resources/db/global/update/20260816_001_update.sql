ALTER TABLE identity_user
  ADD COLUMN globalActorRole VARCHAR(32) NOT NULL DEFAULT 'USER' AFTER status,
  ADD CONSTRAINT ck_identity_user_global_actor_role CHECK (
    globalActorRole IN ('USER', 'SYSTEM_ADMINISTRATOR')
  );

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260816001' AS version;
