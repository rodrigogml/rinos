package br.com.rinos.app.backend.module.storage.service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.storage.enums.StorageTransitionOriginType;
import br.com.rinos.app.backend.module.storage.enums.TenantStorageState;

/**
 * Centraliza as transições estruturais permitidas do armazenamento de tenant.
 *
 * <p>A promoção para pronto é exclusiva da origem sistêmica após validação física. Esta classe não grava entidades
 * nem auditoria; a operação durável que a consumir continua responsável por ambos na mesma transação global.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
@Service
public class TenantStorageStateTransitionService {

  private static final Map<TenantStorageState, Set<TenantStorageState>> ALLOWED_TRANSITIONS = transitions();

  /**
   * Valida uma troca estrutural solicitada por uma origem auditável.
   *
   * @param currentState estado persistido atual, obrigatório
   * @param nextState estado pretendido, obrigatório
   * @param originType origem auditável da troca, obrigatória
   * @return o estado pretendido quando a troca for permitida
   * @throws IllegalArgumentException quando houver regressão, salto, repetição ou promoção manual para pronto
   */
  public TenantStorageState transition(TenantStorageState currentState, TenantStorageState nextState,
      StorageTransitionOriginType originType) {
    Objects.requireNonNull(currentState, "currentState must not be null");
    Objects.requireNonNull(nextState, "nextState must not be null");
    Objects.requireNonNull(originType, "originType must not be null");
    if (nextState == TenantStorageState.READY && originType != StorageTransitionOriginType.SYSTEM) {
      throw new IllegalArgumentException("READY can only be reached by SYSTEM validation");
    }
    if (!ALLOWED_TRANSITIONS.get(currentState).contains(nextState)) {
      throw new IllegalArgumentException("invalid tenant storage transition: " + currentState + " -> " + nextState);
    }
    return nextState;
  }

  private static Map<TenantStorageState, Set<TenantStorageState>> transitions() {
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
    return Map.copyOf(transitions);
  }
}
