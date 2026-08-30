package br.com.rinos.app.backend.module.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.storage.enums.StorageOperationStepState;

@DisplayName("Transições de etapas estruturais")
class StorageOperationStepStateTransitionServiceTest {

  private final StorageOperationStepStateTransitionService service = new StorageOperationStepStateTransitionService();

  @Test
  void transition_shouldAcceptOnlyEveryDeclaredTransition_whenStatesAreCompared() {
    Map<StorageOperationStepState, Set<StorageOperationStepState>> expected = expectedTransitions();

    for (StorageOperationStepState currentState : StorageOperationStepState.values()) {
      for (StorageOperationStepState nextState : StorageOperationStepState.values()) {
        if (expected.get(currentState).contains(nextState)) {
          assertThat(service.transition(currentState, nextState)).isEqualTo(nextState);
        } else {
          assertThatIllegalArgumentException().isThrownBy(() -> service.transition(currentState, nextState));
        }
      }
    }
  }

  private static Map<StorageOperationStepState, Set<StorageOperationStepState>> expectedTransitions() {
    Map<StorageOperationStepState, Set<StorageOperationStepState>> transitions =
        new EnumMap<>(StorageOperationStepState.class);
    transitions.put(StorageOperationStepState.PENDING, EnumSet.of(StorageOperationStepState.RUNNING));
    transitions.put(StorageOperationStepState.RUNNING,
        EnumSet.of(StorageOperationStepState.COMPLETED, StorageOperationStepState.FAILED));
    transitions.put(StorageOperationStepState.COMPLETED, EnumSet.noneOf(StorageOperationStepState.class));
    transitions.put(StorageOperationStepState.FAILED, EnumSet.noneOf(StorageOperationStepState.class));
    return transitions;
  }
}
