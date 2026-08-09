CREATE TABLE testFailedUpdateMarker (
  id BIGINT NOT NULL,
  CONSTRAINT pk_test_failed_update_marker PRIMARY KEY (id)
) ENGINE = InnoDB;

INVALID SQL COMMAND;

CREATE TABLE testUnexpectedUpdateContinuation (
  id BIGINT NOT NULL,
  CONSTRAINT pk_test_unexpected_update_continuation PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260809004' AS version;
