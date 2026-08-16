package br.com.rinos.app.backend.module.identity.component;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import br.com.rinos.app.backend.module.identity.service.AdministrativeFactorContinuityContext;
import br.com.rinos.app.backend.module.identity.service.AdministrativeFactorContinuityPort;

/** Falha de modo fechado quando o módulo de autorização não está disponível. */
@Component
@ConditionalOnMissingBean(AdministrativeFactorContinuityPort.class)
public class UnavailableAdministrativeFactorContinuityAdapter
    implements AdministrativeFactorContinuityPort {

  @Override
  public AdministrativeFactorContinuityContext lockContexts(long userId) {
    throw new IllegalStateException("administrative factor continuity is unavailable");
  }

  @Override
  public void validateAndRevise(
      AdministrativeFactorContinuityContext context, Instant effectiveAt) {
    throw new IllegalStateException("administrative factor continuity is unavailable");
  }
}
