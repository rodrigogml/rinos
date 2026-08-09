-- Arquivo de atualização do banco de dados, não pode ser alterado por agentes de IA.

CREATE TABLE identity_reauthenticationContext (
  id BIGINT AUTO_INCREMENT NOT NULL,
  idAuthenticationFlow BIGINT NOT NULL,
  idAuthSession BIGINT NOT NULL,
  operation VARCHAR(48) NOT NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_identity_reauthentication_context PRIMARY KEY (id),
  CONSTRAINT fk_identity_reauthentication_context_flow FOREIGN KEY (idAuthenticationFlow)
    REFERENCES identity_authenticationFlow (id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,
  CONSTRAINT fk_identity_reauthentication_context_session FOREIGN KEY (idAuthSession)
    REFERENCES identity_authSession (id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,
  CONSTRAINT uk_identity_reauthentication_context_flow UNIQUE (idAuthenticationFlow),
  CONSTRAINT ck_identity_reauthentication_context_operation
    CHECK (operation IN (
      'RENAME_PASSKEY', 'REGISTER_PASSKEY', 'CREATE_PASSWORD', 'CHANGE_PASSWORD',
      'REVOKE_PASSKEY', 'ENROLL_FACTOR', 'REMOVE_FACTOR', 'REGENERATE_RECOVERY_CODES',
      'LINK_EXTERNAL_IDENTITY', 'UNLINK_EXTERNAL_IDENTITY', 'REVOKE_SESSION',
      'REVOKE_ALL_SESSIONS'
    )),
  INDEX idx_identity_reauthentication_context_session (idAuthSession)
) ENGINE = InnoDB;

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260809002' AS version;
