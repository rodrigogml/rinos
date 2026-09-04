package br.com.rinos.app.api.module.storage.vo;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.api.module.storage.enums.TenantStorageDeactivationStatusEnum;

/**
 * Resultado sanitizado de uma solicitação global de desativação lógica.
 *
 * <p>O resultado não revela schema, host, credencial, comandos ou identificador físico.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
public record TenantStorageDeactivationResultVO(
    TenantStorageDeactivationStatusEnum status,
    String safeReasonCode,
    Instant occurredAt) {

  /**
   * Valida o resultado antes de ele atravessar a fronteira pública do módulo.
   *
   * @throws NullPointerException quando status ou instante não forem informados
   * @throws IllegalArgumentException quando o código seguro estiver em branco
   */
  public TenantStorageDeactivationResultVO {
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    if (safeReasonCode == null || safeReasonCode.isBlank()) {
      throw new IllegalArgumentException("safeReasonCode must not be blank");
    }
    safeReasonCode = safeReasonCode.strip();
  }
}
