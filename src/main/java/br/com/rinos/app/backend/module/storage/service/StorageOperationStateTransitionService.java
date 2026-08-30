package br.com.rinos.app.backend.module.storage.service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.storage.enums.StorageOperationState;

/**
 * Valida o ciclo durável de uma operação da fila estrutural.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
@Service
public class StorageOperationStateTransitionService {

  private static final Map<StorageOperationState, Set<StorageOperationState>> ALLOWED_TRANSITIONS = transitions();

  /**
   * Valida a próxima situação de uma operação já persistida.
   *
   * @param currentState estado atual, obrigatório
   * @param nextState estado pretendido, obrigatório
   * @return o estado pretendido quando a transição for permitida
   * @throws IllegalArgumentException quando a transição não respeitar a fila durável
   */
  public StorageOperationState transition(StorageOperationState currentState,
      StorageOperationState nextState) {
    Objects.requireNonNull(currentState, "currentState must not be null");
    Objects.requireNonNull(nextState, "nextState must not be null");
    if (!ALLOWED_TRANSITIONS.get(currentState).contains(nextState)) {
      throw new IllegalArgumentException("invalid storage operation transition: " + currentState + " -> " + nextState);
    }
    return nextState;
  }

  private static Map<StorageOperationState, Set<StorageOperationState>> transitions() {
    Map<StorageOperationState, Set<StorageOperationState>> transitions = new EnumMap<>(StorageOperationState.class);
    transitions.put(StorageOperationState.QUEUED,
        EnumSet.of(StorageOperationState.CLAIMED, StorageOperationState.CANCELLED));
    transitions.put(StorageOperationState.CLAIMED,
        EnumSet.of(StorageOperationState.RUNNING, StorageOperationState.QUEUED,
            StorageOperationState.CANCELLED));
    transitions.put(StorageOperationState.RUNNING,
        EnumSet.of(StorageOperationState.RETRY_WAIT, StorageOperationState.COMPLETED,
            StorageOperationState.FAILED_FINAL));
    transitions.put(StorageOperationState.RETRY_WAIT,
        EnumSet.of(StorageOperationState.QUEUED, StorageOperationState.FAILED_FINAL,
            StorageOperationState.CANCELLED));
    transitions.put(StorageOperationState.COMPLETED, EnumSet.noneOf(StorageOperationState.class));
    transitions.put(StorageOperationState.FAILED_FINAL, EnumSet.noneOf(StorageOperationState.class));
    transitions.put(StorageOperationState.CANCELLED, EnumSet.noneOf(StorageOperationState.class));
    return Map.copyOf(transitions);
  }
}
