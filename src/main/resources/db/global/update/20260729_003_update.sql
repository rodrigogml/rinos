-- Arquivo de atualização do banco de dados, não pode ser alterado por agentes de IA.

CREATE TABLE identity_legalDocumentVersion (
  id BIGINT AUTO_INCREMENT NOT NULL,
  documentType VARCHAR(32) NOT NULL,
  versionName VARCHAR(64) NOT NULL,
  required BOOLEAN NOT NULL,
  content LONGTEXT NOT NULL,
  contentHash BINARY(32) NOT NULL,
  effectiveAt TIMESTAMP(6) NOT NULL,
  retiredAt TIMESTAMP(6) NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_identity_legal_document_version PRIMARY KEY (id),
  CONSTRAINT uk_identity_legal_document_version UNIQUE (documentType, versionName),
  INDEX idx_identity_legal_document_effective (documentType, effectiveAt, retiredAt)
) ENGINE = InnoDB;

CREATE TABLE identity_legalConsent (
  id BIGINT AUTO_INCREMENT NOT NULL,
  idUser BIGINT NOT NULL,
  idRegistration BIGINT NULL,
  idLegalDocumentVersion BIGINT NOT NULL,
  decision VARCHAR(16) NOT NULL,
  decidedAt TIMESTAMP(6) NOT NULL,
  createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT pk_identity_legal_consent PRIMARY KEY (id),
  CONSTRAINT fk_identity_legal_consent_user FOREIGN KEY (idUser)
    REFERENCES identity_user (id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,
  CONSTRAINT fk_identity_legal_consent_registration FOREIGN KEY (idRegistration)
    REFERENCES identity_registration (id)
    ON DELETE SET NULL
    ON UPDATE RESTRICT,
  CONSTRAINT fk_identity_legal_consent_document_version FOREIGN KEY (idLegalDocumentVersion)
    REFERENCES identity_legalDocumentVersion (id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,
  CONSTRAINT uk_identity_legal_consent_user_version UNIQUE (idUser, idLegalDocumentVersion)
) ENGINE = InnoDB;

CREATE OR REPLACE
SQL SECURITY INVOKER
VIEW databaseVersion AS
SELECT '20260729003' AS version;
