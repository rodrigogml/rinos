package br.com.rinos.app.backend.module.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.access.entity.AccessContextRevisionEntity;
import br.com.rinos.app.backend.module.access.repository.AccessContextRevisionRepository;

class AccessContextRevisionServiceTest {

  @Test
  void lockAndIncrement_shouldEnsureLockAndAdvanceTenantRevision() {
    AccessContextRevisionRepository repository = mock(AccessContextRevisionRepository.class);
    AccessContextRevisionEntity entity = new AccessContextRevisionEntity(AccessScope.TENANT, 42L);
    when(repository.findForUpdate(AccessScope.TENANT, 42L)).thenReturn(Optional.of(entity));
    AccessContextRevisionService service = new AccessContextRevisionService(repository);

    long revision = service.lockAndIncrement(AccessScope.TENANT, 42L);

    assertThat(revision).isOne();
    InOrder order = inOrder(repository);
    order.verify(repository).ensureContext("TENANT", 42L);
    order.verify(repository).findForUpdate(AccessScope.TENANT, 42L);
  }

  @Test
  void current_shouldRejectContextWithIncompatibleTenant() {
    AccessContextRevisionService service =
        new AccessContextRevisionService(mock(AccessContextRevisionRepository.class));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> service.current(AccessScope.GLOBAL, 42L));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> service.current(AccessScope.TENANT, null));
  }
}
