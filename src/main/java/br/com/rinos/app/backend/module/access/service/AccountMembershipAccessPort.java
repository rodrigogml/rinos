package br.com.rinos.app.backend.module.access.service;

/** Porta publicada pelo módulo de membership para validação estrutural sem acoplamento ACL. */
public interface AccountMembershipAccessPort {

  AccountMembershipAccessSnapshot inspect(long membershipId);
}
