package br.com.rinos.app.backend.module.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.storage.enums.StorageTransitionOriginType;
import br.com.rinos.app.backend.module.storage.enums.TenantStorageState;

@DisplayName("Transições estruturais de armazenamento de tenant")
class TenantStorageStateTransitionServiceTest {

  private final TenantStorageStateTransitionService service = new TenantStorageStateTransitionService();

  @Test
  void transition_shouldAcceptOnlyEveryDeclaredSystemTransition_whenStatesAreCompared() {
    Map<TenantStorageState, Set<TenantStorageState>> expected = expectedTransitions();

    for (TenantStorageState currentState : TenantStorageState.values()) {
      for (TenantStorageState nextState : TenantStorageState.values()) {
        if (expected.get(currentState).contains(nextState)) {
          assertThat(service.transition(currentState, nextState, StorageTransitionOriginType.SYSTEM))
              .isEqualTo(nextState);
        } else {
          assertThatIllegalArgumentException().isThrownBy(
              () -> service.transition(currentState, nextState, StorageTransitionOriginType.SYSTEM));
        }
      }
    }
  }

  @Test
  void transition_shouldRejectManualPromotionToReady_whenGlobalUserRequestsIt() {
    assertThatIllegalArgumentException().isThrownBy(
        () -> service.transition(TenantStorageState.MIGRATING, TenantStorageState.READY,
            StorageTransitionOriginType.GLOBAL_USER))
        .withMessage("READY can only be reached by SYSTEM validation");
  }

  private static Map<TenantStorageState, Set<TenantStorageState>> expectedTransitions() {
    Map<TenantStorageState, Set<TenantStorageState>> transitions = new EnumMap<>(TenantStorageState.class);
    transitions.put(TenantStorageState.REQUESTED,
        EnumSet.of(TenantStorageState.PROVISIONING, TenantStorageState.FAILED,
            TenantStorageState.DEACTIVATING));
    transitions.put(TenantStorageState.PROVISIONING,
        EnumSet.of(TenantStorageState.INITIALIZING, TenantStorageState.FAILED,
            TenantStorageState.QUARANTINED, TenantStorageState.DEACTIVATING));
    transitions.put(TenantStorageState.INITIALIZING,
        EnumSet.of(TenantStorageState.MIGRATING, TenantStorageState.FAILED,
            TenantStorageState.QUARANTINED, TenantStorageState.DEACTIVATING));
    transitions.put(TenantStorageState.MIGRATING,
        EnumSet.of(TenantStorageState.READY, TenantStorageState.QUARANTINED,
            TenantStorageState.DEACTIVATING));
    transitions.put(TenantStorageState.READY,
        EnumSet.of(TenantStorageState.MIGRATING, TenantStorageState.QUARANTINED,
            TenantStorageState.DEACTIVATING));
    transitions.put(TenantStorageState.FAILED,
        EnumSet.of(TenantStorageState.QUARANTINED, TenantStorageState.DEACTIVATING));
    transitions.put(TenantStorageState.QUARANTINED, EnumSet.of(TenantStorageState.DEACTIVATING));
    transitions.put(TenantStorageState.DEACTIVATING, EnumSet.of(TenantStorageState.INACTIVE));
    transitions.put(TenantStorageState.INACTIVE, EnumSet.noneOf(TenantStorageState.class));
    return transitions;
  }
}
