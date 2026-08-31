package br.com.rinos.app.backend.module.access.service;

/**
 * Representa a referência estrutural mínima da associação fundadora requerida pelo bootstrap ACL.
 *
 * <p>O snapshot não transporta nome, e-mail, papel, permissões ou estado do plano. Ele permite
 * somente comprovar que um membership pertencente ao fundador continua ativo no tenant esperado.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-31
 */
public record FoundingMembershipAccessSnapshot(
    boolean sourceAvailable,
    boolean exists,
    Long membershipId,
    Long tenantId,
    boolean membershipActive) {

  /**
   * Valida a consistência da resposta fail-safe entre os estados disponível, ausente e encontrado.
   *
   * @throws IllegalArgumentException quando os campos não representarem um único estado seguro
   */
  public FoundingMembershipAccessSnapshot {
    if (!sourceAvailable && (exists || membershipId != null || tenantId != null || membershipActive)
        || exists && (membershipId == null || membershipId <= 0 || tenantId == null || tenantId <= 0)
        || !exists && (membershipId != null || tenantId != null || membershipActive)) {
      throw new IllegalArgumentException("founding membership access snapshot is inconsistent");
    }
  }

  /** @return resposta que impede o bootstrap quando membership não puder ser consultado */
  public static FoundingMembershipAccessSnapshot unavailable() {
    return new FoundingMembershipAccessSnapshot(false, false, null, null, false);
  }

  /** @return resposta disponível que não encontrou a associação fundadora */
  public static FoundingMembershipAccessSnapshot absent() {
    return new FoundingMembershipAccessSnapshot(true, false, null, null, false);
  }

  /**
   * Cria uma resposta encontrada e minimizada.
   *
   * @param membershipId identificador interno da associação
   * @param tenantId contexto lógico da associação
   * @param membershipActive indica se a associação ainda pode participar de grupos
   * @return snapshot consistente
   */
  public static FoundingMembershipAccessSnapshot found(
      long membershipId,
      long tenantId,
      boolean membershipActive) {
    return new FoundingMembershipAccessSnapshot(true, true, membershipId, tenantId, membershipActive);
  }
}
