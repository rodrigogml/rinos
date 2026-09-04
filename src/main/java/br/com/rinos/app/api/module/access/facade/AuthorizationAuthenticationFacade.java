package br.com.rinos.app.api.module.access.facade;

import java.time.Instant;
import java.util.Optional;

import br.com.rinos.app.api.module.access.vo.AuthenticationAssurance;

/** Revalida a sessão global e devolve somente sua garantia corrente. */
public interface AuthorizationAuthenticationFacade {

  Optional<AuthenticationAssurance> resolve(
      long expectedIdentityId, String sessionReference, Instant occurredAt);
}
