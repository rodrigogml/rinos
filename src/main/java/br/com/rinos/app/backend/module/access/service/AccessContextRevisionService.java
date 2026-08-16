package br.com.rinos.app.backend.module.access.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.access.entity.AccessContextRevisionEntity;
import br.com.rinos.app.backend.module.access.repository.AccessContextRevisionRepository;

/** Autoridade transacional de obsolescência dos snapshots ACL. */
@Service
@org.springframework.context.annotation.Lazy
public class AccessContextRevisionService {

  private final AccessContextRevisionRepository repository;

  public AccessContextRevisionService(AccessContextRevisionRepository repository) {
    this.repository = repository;
  }

  /** Lê a revisão sem bloquear o contexto. Ausência ou erro nunca é convertido em acesso. */
  @Transactional(readOnly = true)
  public long current(AccessScope scope, Long tenantId) {
    validateContext(scope, tenantId);
    return find(scope, tenantId).getRevision();
  }

  /**
   * Garante, bloqueia e incrementa a revisão dentro da transação de mutação chamadora.
   */
  @Transactional
  public long lockAndIncrement(AccessScope scope, Long tenantId) {
    return lock(scope, tenantId).increment();
  }

  /** Serializa mutações e avaliações prospectivas sem alterar a revisão por si só. */
  @Transactional
  public AccessContextRevisionEntity lock(AccessScope scope, Long tenantId) {
    validateContext(scope, tenantId);
    repository.ensureContext(scope.name(), tenantId);
    return scope == AccessScope.GLOBAL
        ? repository.findGlobalForUpdate(scope).orElseThrow()
        : repository.findForUpdate(scope, tenantId).orElseThrow();
  }

  private AccessContextRevisionEntity find(AccessScope scope, Long tenantId) {
    return (scope == AccessScope.GLOBAL
        ? repository.findGlobal(scope)
        : repository.findByScopeAndTenantId(scope, tenantId))
        .orElseThrow(() -> new IllegalStateException("access context revision is unavailable"));
  }

  private static void validateContext(AccessScope scope, Long tenantId) {
    if (scope == null
        || scope == AccessScope.GLOBAL && tenantId != null
        || scope == AccessScope.TENANT && (tenantId == null || tenantId <= 0)) {
      throw new IllegalArgumentException("access context is inconsistent");
    }
  }
}
