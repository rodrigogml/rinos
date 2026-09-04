package br.com.rinos.app.backend.module.identity.service;

import java.util.List;

/** Contextos de autorização bloqueados antes de remover um fator administrativo. */
public record AdministrativeFactorContinuityContext(long userId, List<Long> tenantIds) {
  public AdministrativeFactorContinuityContext {
    tenantIds = List.copyOf(tenantIds);
  }
}
