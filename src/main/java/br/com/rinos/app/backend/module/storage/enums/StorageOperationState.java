package br.com.rinos.app.backend.module.storage.enums;

/** Estados duráveis de uma operação na fila estrutural. */
public enum StorageOperationState {
  QUEUED,
  CLAIMED,
  RUNNING,
  RETRY_WAIT,
  COMPLETED,
  FAILED_FINAL,
  CANCELLED
}
