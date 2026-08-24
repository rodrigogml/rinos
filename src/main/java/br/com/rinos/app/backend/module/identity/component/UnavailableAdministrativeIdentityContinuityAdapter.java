package br.com.rinos.app.backend.module.identity.component;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import br.com.rinos.app.backend.module.identity.service.AdministrativeIdentityContinuityContext;
import br.com.rinos.app.backend.module.identity.service.AdministrativeIdentityContinuityPort;

/** Falha de modo fechado quando o módulo de autorização não está disponível para transições de identidade. */
@Component
@ConditionalOnMissingBean(AdministrativeIdentityContinuityPort.class)
public class UnavailableAdministrativeIdentityContinuityAdapter
    implements AdministrativeIdentityContinuityPort {

  @Override
  public AdministrativeIdentityContinuityContext lockIdentityContexts(long userId) {
    throw new IllegalStateException("administrative identity continuity is unavailable");
  }

  @Override
  public void validateAndRevise(
      AdministrativeIdentityContinuityContext context, Instant effectiveAt) {
    throw new IllegalStateException("administrative identity continuity is unavailable");
  }
}
