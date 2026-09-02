package br.com.rinos.app.backend.module.storage.component;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;

import br.eng.rodrigogml.rfw.database.service.DatabaseUpdateStrategyResolverService;
import br.eng.rodrigogml.rfw.database.strategy.DatabaseUpdateLock;
import br.eng.rodrigogml.rfw.database.strategy.DatabaseUpdateStrategy;

/**
 * Serializa operações estruturais no catálogo físico de um tenant.
 *
 * <p>O RFW deriva o lock nomeado a partir do catálogo da conexão. Como cada tenant possui um catálogo exclusivo,
 * a mesma operação em instâncias distintas concorre pelo mesmo lock, sem compartilhar a exclusividade com outros
 * tenants.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
@Component
@ConditionalOnProperty(prefix = "rfw.database.update", name = "enabled", havingValue = "true")
public class TenantStorageNamedLockComponent {
  private final DatabaseUpdateStrategyResolverService strategyResolver;

  /**
   * Cria o adaptador do mecanismo de lock publicado pela RFW Platform.
   *
   * @param strategyResolver resolvedor da estratégia correspondente ao banco da conexão
   */
  public TenantStorageNamedLockComponent(DatabaseUpdateStrategyResolverService strategyResolver) {
    this.strategyResolver = Objects.requireNonNull(strategyResolver, "strategyResolver must not be null");
  }

  /**
   * Executa uma unidade estrutural enquanto mantém o lock específico do catálogo tenant.
   *
   * @param tenantDataSource datasource que seleciona exclusivamente o catálogo físico do tenant
   * @param lockTimeout prazo máximo de espera pelo lock nomeado
   * @param operation unidade síncrona a ser serializada
   * @throws DataAccessResourceFailureException quando não for possível abrir a conexão de lock
   * @throws RuntimeException quando a estratégia, a aquisição ou a operação falhar
   */
  public void executeExclusive(DataSource tenantDataSource, Duration lockTimeout, Runnable operation) {
    Objects.requireNonNull(tenantDataSource, "tenantDataSource must not be null");
    Objects.requireNonNull(lockTimeout, "lockTimeout must not be null");
    Objects.requireNonNull(operation, "operation must not be null");
    if (lockTimeout.isNegative() || lockTimeout.isZero()) {
      throw new IllegalArgumentException("lockTimeout must be positive");
    }

    try (Connection connection = tenantDataSource.getConnection()) {
      DatabaseUpdateStrategy strategy = strategyResolver.resolve(connection);
      try (DatabaseUpdateLock lock = strategy.acquireLock(connection, lockTimeout)) {
        operation.run();
      }
    } catch (SQLException exception) {
      throw new DataAccessResourceFailureException("Não foi possível abrir a conexão do tenant para lock estrutural.",
          exception);
    }
  }
}
