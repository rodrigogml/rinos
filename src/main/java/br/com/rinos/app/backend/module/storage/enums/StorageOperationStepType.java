package br.com.rinos.app.backend.module.storage.enums;

/** Etapas verificáveis que uma operação estrutural pode registrar. */
public enum StorageOperationStepType {
  RESERVE,
  CREATE_SCHEMA,
  INITIALIZE,
  VERIFY_VERSION,
  MIGRATE,
  VALIDATE_READINESS,
  RECONCILE,
  DEACTIVATE
}
