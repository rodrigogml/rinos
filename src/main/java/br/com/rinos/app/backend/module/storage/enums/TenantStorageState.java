package br.com.rinos.app.backend.module.storage.enums;

/** Estados persistidos da disponibilidade estrutural de um armazenamento de tenant. */
public enum TenantStorageState {
  REQUESTED,
  PROVISIONING,
  INITIALIZING,
  MIGRATING,
  READY,
  FAILED,
  QUARANTINED,
  DEACTIVATING,
  INACTIVE
}
