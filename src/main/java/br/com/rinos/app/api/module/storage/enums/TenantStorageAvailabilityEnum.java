package br.com.rinos.app.api.module.storage.enums;

/**
 * Disponibilidade segura do armazenamento de um tenant, sem revelar sua localização física.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
public enum TenantStorageAvailabilityEnum {
  READY,
  WAITING,
  MIGRATING,
  ATTENTION,
  INACTIVE
}
