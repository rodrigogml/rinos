package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;

import br.com.rinos.app.backend.module.identity.enums.OriginReservationStatusEnum;

/**
 * Resultado reduzido da reserva atômica.
 *
 * @param status reserva confirmada ou bloqueio
 * @param blockedUntil fim da janela somente quando bloqueado
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record OriginReservationResultVO(
    OriginReservationStatusEnum status,
    Instant blockedUntil) {

  /**
   * Garante nulidade coerente sem expor contadores internos.
   */
  public OriginReservationResultVO {
    if ((status == OriginReservationStatusEnum.BLOCKED) != (blockedUntil != null)) {
      throw new IllegalArgumentException("blocked status and blockedUntil must agree");
    }
  }
}
