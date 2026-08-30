package br.com.rinos.app.backend.module.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.storage.enums.StorageOperationState;

@DisplayName("Transições de operações estruturais")
class StorageOperationStateTransitionServiceTest {

  private final StorageOperationStateTransitionService service = new StorageOperationStateTransitionService();

  @Test
  void transition_shouldAcceptOnlyEveryDeclaredTransition_whenStatesAreCompared() {
    Map<StorageOperationState, Set<StorageOperationState>> expected = expectedTransitions();

    for (StorageOperationState currentState : StorageOperationState.values()) {
      for (StorageOperationState nextState : StorageOperationState.values()) {
        if (expected.get(currentState).contains(nextState)) {
          assertThat(service.transition(currentState, nextState)).isEqualTo(nextState);
        } else {
          assertThatIllegalArgumentException().isThrownBy(() -> service.transition(currentState, nextState));
        }
      }
    }
  }

  private static Map<StorageOperationState, Set<StorageOperationState>> expectedTransitions() {
    Map<StorageOperationState, Set<StorageOperationState>> transitions =
        new EnumMap<>(StorageOperationState.class);
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
    return transitions;
  }
}
