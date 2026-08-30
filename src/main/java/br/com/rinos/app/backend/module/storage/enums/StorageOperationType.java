package br.com.rinos.app.backend.module.storage.enums;

/** Tipos fechados de operação física executada sobre um armazenamento de tenant. */
public enum StorageOperationType {
  PROVISION,
  MIGRATE,
  RECONCILE,
  DEACTIVATE
}
