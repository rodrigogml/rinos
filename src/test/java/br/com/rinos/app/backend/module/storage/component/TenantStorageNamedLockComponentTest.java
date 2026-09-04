package br.com.rinos.app.backend.module.storage.component;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.time.Duration;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import br.eng.rodrigogml.rfw.database.service.DatabaseUpdateStrategyResolverService;
import br.eng.rodrigogml.rfw.database.strategy.DatabaseUpdateLock;
import br.eng.rodrigogml.rfw.database.strategy.DatabaseUpdateStrategy;

class TenantStorageNamedLockComponentTest {

  @Test
  void executeExclusive_shouldReleaseLockAndConnection_whenOperationSucceeds() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    DatabaseUpdateStrategyResolverService resolver = mock(DatabaseUpdateStrategyResolverService.class);
    DatabaseUpdateStrategy strategy = mock(DatabaseUpdateStrategy.class);
    DatabaseUpdateLock lock = mock(DatabaseUpdateLock.class);
    Runnable operation = mock(Runnable.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(resolver.resolve(connection)).thenReturn(strategy);
    when(strategy.acquireLock(connection, Duration.ofSeconds(5))).thenReturn(lock);
    TenantStorageNamedLockComponent component = new TenantStorageNamedLockComponent(resolver);

    component.executeExclusive(dataSource, Duration.ofSeconds(5), operation);

    verify(operation).run();
    verify(lock).close();
    verify(connection).close();
  }

  @Test
  void executeExclusive_shouldReleaseLockAndConnection_whenOperationFails() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    DatabaseUpdateStrategyResolverService resolver = mock(DatabaseUpdateStrategyResolverService.class);
    DatabaseUpdateStrategy strategy = mock(DatabaseUpdateStrategy.class);
    DatabaseUpdateLock lock = mock(DatabaseUpdateLock.class);
    Runnable operation = mock(Runnable.class);
    IllegalStateException failure = new IllegalStateException("operation failed");
    when(dataSource.getConnection()).thenReturn(connection);
    when(resolver.resolve(connection)).thenReturn(strategy);
    when(strategy.acquireLock(connection, Duration.ofSeconds(5))).thenReturn(lock);
    doThrow(failure).when(operation).run();
    TenantStorageNamedLockComponent component = new TenantStorageNamedLockComponent(resolver);

    assertThatThrownBy(() -> component.executeExclusive(dataSource, Duration.ofSeconds(5), operation))
        .isSameAs(failure);

    verify(lock).close();
    verify(connection).close();
  }
}
