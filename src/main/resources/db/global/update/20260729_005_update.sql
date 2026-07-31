-- Arquivo de atualização do banco de dados, não pode ser alterado por agentes de IA.

ALTER TABLE security_originWindow
  ADD COLUMN activeMarker BOOLEAN NULL AFTER policy;

UPDATE security_originWindow originWindow
JOIN (
  SELECT MAX(id) AS id
  FROM security_originWindow
  WHERE windowEndsAt > CURRENT_TIMESTAMP(6)
  GROUP BY originAddress, operation, policy
) activeWindow
  ON activeWindow.id = originWindow.id
SET originWindow.activeMarker = TRUE;

ALTER TABLE security_originWindow
  ADD CONSTRAINT uk_security_origin_window_active
    UNIQUE (originAddress, operation, policy, activeMarker);

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260729005' AS version;
