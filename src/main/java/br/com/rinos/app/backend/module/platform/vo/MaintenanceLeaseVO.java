package br.com.rinos.app.backend.module.platform.vo;

import java.time.Instant;
import java.util.Objects;

/**
 * Expõe internamente o token completo necessário para comprovar a propriedade de um lease.
 *
 * @param leaseKey chave lógica exclusiva
 * @param owner instância e sessão proprietárias
 * @param epoch fencing token monotônico
 * @param acquiredAt instante da aquisição vigente
 * @param heartbeatAt instante do último heartbeat
 * @param leaseUntil limite de validade
 * @param version versão otimista persistida
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record MaintenanceLeaseVO(
    String leaseKey,
    MaintenanceSessionVO owner,
    long epoch,
    Instant acquiredAt,
    Instant heartbeatAt,
    Instant leaseUntil,
    long version) {

  /**
   * Valida a integridade mínima do token retornado pela persistência.
   *
   * @throws IllegalArgumentException quando chave, epoch ou versão são inválidos
   * @throws NullPointerException quando um campo obrigatório é nulo
   */
  public MaintenanceLeaseVO {
    Objects.requireNonNull(leaseKey, "leaseKey must not be null");
    Objects.requireNonNull(owner, "owner must not be null");
    Objects.requireNonNull(acquiredAt, "acquiredAt must not be null");
    Objects.requireNonNull(heartbeatAt, "heartbeatAt must not be null");
    Objects.requireNonNull(leaseUntil, "leaseUntil must not be null");
    if (leaseKey.isBlank()) {
      throw new IllegalArgumentException("leaseKey must not be blank");
    }
    if (epoch < 1) {
      throw new IllegalArgumentException("epoch must be positive");
    }
    if (version < 0) {
      throw new IllegalArgumentException("version must not be negative");
    }
  }
}
