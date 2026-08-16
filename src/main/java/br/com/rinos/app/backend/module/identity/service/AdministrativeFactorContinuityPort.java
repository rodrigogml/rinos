package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.UUID;

/** Protege a continuidade administrativa quando um fator forte é removido. */
public interface AdministrativeFactorContinuityPort {

  AdministrativeFactorContinuityContext lockContexts(long userId);

  void validateAndRevise(AdministrativeFactorContinuityContext context, Instant effectiveAt);

  /** Agenda nova tentativa de bootstrap depois que um fator forte for confirmado. */
  default void afterStrongFactorEstablished(UUID correlationId, Instant occurredAt) {
  }
}
