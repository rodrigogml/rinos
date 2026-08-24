package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;

/**
 * Protege a continuidade administrativa durante transições que retiram uma identidade do estado ativo.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-23
 */
public interface AdministrativeIdentityContinuityPort {

  /**
   * Bloqueia o contexto global e os tenants afetados antes de a identidade ser bloqueada, desativada ou cancelada.
   *
   * @param userId identidade que sofrerá a transição
   * @return contextos bloqueados na ordem canônica
   */
  AdministrativeIdentityContinuityContext lockIdentityContexts(long userId);

  /**
   * Reavalia a continuidade após o flush da transição e revisa somente os contextos que continuarem aptos.
   *
   * @param context contextos anteriormente bloqueados
   * @param effectiveAt instante UTC da transição
   * @throws IllegalArgumentException quando a transição elimina a última administração apta
   * @throws IllegalStateException quando a continuidade não pode ser avaliada com segurança
   */
  void validateAndRevise(AdministrativeIdentityContinuityContext context, Instant effectiveAt);
}
