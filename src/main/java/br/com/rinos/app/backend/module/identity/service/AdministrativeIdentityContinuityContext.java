package br.com.rinos.app.backend.module.identity.service;

import java.util.List;

/**
 * Contextos de autorização bloqueados antes de uma identidade ativa perder sua elegibilidade administrativa.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-23
 */
public record AdministrativeIdentityContinuityContext(long userId, List<Long> tenantIds) {

  /**
   * Cria uma fotografia imutável dos tenants afetados.
   *
   * @param userId identidade global afetada
   * @param tenantIds tenants em que ela possui associação corrente
   */
  public AdministrativeIdentityContinuityContext {
    tenantIds = List.copyOf(tenantIds);
  }
}
