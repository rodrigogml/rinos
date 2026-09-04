package br.com.rinos.app.backend.module.account.vo;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa a posse transitória de um evento de criação reclamada pela manutenção global.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-31
 */
public record AccountProvisioningOutboxClaimVO(
    UUID eventId,
    String leaseOwner,
    Instant leaseUntil) {

  /**
   * Valida os elementos que vinculam a confirmação posterior ao mesmo lease.
   *
   * @throws NullPointerException quando o evento ou a expiração não forem informados
   * @throws IllegalArgumentException quando o identificador da instância for vazio
   */
  public AccountProvisioningOutboxClaimVO {
    Objects.requireNonNull(eventId, "eventId must not be null");
    Objects.requireNonNull(leaseUntil, "leaseUntil must not be null");
    if (leaseOwner == null || leaseOwner.isBlank()) {
      throw new IllegalArgumentException("leaseOwner must not be blank");
    }
    leaseOwner = leaseOwner.strip();
  }
}
