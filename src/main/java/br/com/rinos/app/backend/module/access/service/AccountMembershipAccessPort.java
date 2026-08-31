package br.com.rinos.app.backend.module.access.service;

/** Porta publicada pelo módulo de membership para validação estrutural sem acoplamento ACL. */
public interface AccountMembershipAccessPort {

  AccountMembershipAccessSnapshot inspect(long membershipId);

  /**
   * Localiza apenas a associação ativa ou histórica do fundador para o bootstrap inicial do ACL.
   *
   * @param accountId conta global cujo fundador será associado ao grupo protegido
   * @param founderUserId identidade global declarada como fundadora da conta
   * @return fotografia minimizada; indisponibilidade deve impedir o bootstrap
   */
  FoundingMembershipAccessSnapshot inspectFounder(long accountId, long founderUserId);
}
