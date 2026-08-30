package br.com.rinos.app.backend.module.storage.enums;

/** Classifica divergências estruturais detectadas sem acionar qualquer correção automática. */
public enum TenantStorageDivergenceType {
  REGISTRY_SCHEMA_MISSING,
  OPERATION_WITHOUT_PROGRESS
}
