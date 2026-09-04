package br.com.rinos.app.backend.module.storage.service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.storage.enums.StorageOperationStepState;

/**
 * Valida o resultado imutável de cada etapa de uma operação estrutural.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
@Service
public class StorageOperationStepStateTransitionService {

  private static final Map<StorageOperationStepState, Set<StorageOperationStepState>> ALLOWED_TRANSITIONS =
      transitions();

  /**
   * Valida a troca de estado de uma etapa persistida.
   *
   * @param currentState estado atual, obrigatório
   * @param nextState resultado pretendido, obrigatório
   * @return o estado pretendido quando a transição for permitida
   * @throws IllegalArgumentException quando a etapa for repetida, regredir ou saltar estados
   */
  public StorageOperationStepState transition(StorageOperationStepState currentState,
      StorageOperationStepState nextState) {
    Objects.requireNonNull(currentState, "currentState must not be null");
    Objects.requireNonNull(nextState, "nextState must not be null");
    if (!ALLOWED_TRANSITIONS.get(currentState).contains(nextState)) {
      throw new IllegalArgumentException("invalid storage operation step transition: "
          + currentState + " -> " + nextState);
    }
    return nextState;
  }

  private static Map<StorageOperationStepState, Set<StorageOperationStepState>> transitions() {
    Map<StorageOperationStepState, Set<StorageOperationStepState>> transitions =
        new EnumMap<>(StorageOperationStepState.class);
    transitions.put(StorageOperationStepState.PENDING, EnumSet.of(StorageOperationStepState.RUNNING));
    transitions.put(StorageOperationStepState.RUNNING,
        EnumSet.of(StorageOperationStepState.COMPLETED, StorageOperationStepState.FAILED));
    transitions.put(StorageOperationStepState.COMPLETED, EnumSet.noneOf(StorageOperationStepState.class));
    transitions.put(StorageOperationStepState.FAILED, EnumSet.noneOf(StorageOperationStepState.class));
    return Map.copyOf(transitions);
  }
}
