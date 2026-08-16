package br.com.rinos.app.backend.module.plans.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.rinos.app.api.module.plans.dto.PersonalContractBootstrapRequest;
import br.com.rinos.app.api.module.plans.dto.TenantContractBootstrapRequest;
import br.com.rinos.app.api.module.plans.enums.ContractBootstrapStatus;
import br.com.rinos.app.api.module.plans.enums.ContractScope;
import br.com.rinos.app.api.module.plans.port.PersonalContractBootstrapPort;
import br.com.rinos.app.api.module.plans.port.TenantContractBootstrapPort;
import br.com.rinos.app.api.module.plans.vo.ContractBootstrapResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/** Cria ou confirma contratos FREE e suas atribuições iniciais sob lock do titular. */
@Service
@ConditionalOnBean(DataSource.class)
public class JdbcContractBootstrapService
    implements PersonalContractBootstrapPort, TenantContractBootstrapPort {

  private static final String DEFAULT_VERSION_SQL = """
      SELECT pv.idPlanVersion
        FROM plans_plan p
        JOIN plans_planVersion pv ON pv.idPlan = p.idPlan AND pv.scopeType = p.scopeType
       WHERE p.scopeType = ? AND p.defaultPlan = TRUE AND p.status = 'ACTIVE'
         AND pv.status = 'PUBLISHED'
         AND (p.availableFrom IS NULL OR p.availableFrom <= ?)
         AND (p.availableUntil IS NULL OR p.availableUntil > ?)
         AND (pv.validFrom IS NULL OR pv.validFrom <= ?)
         AND (pv.validUntil IS NULL OR pv.validUntil > ?)
      """;

  private final JdbcOperations jdbc;
  private final TransactionTemplate transactions;
  private final MeterRegistry metrics;

  public JdbcContractBootstrapService(
      DataSource dataSource,
      PlatformTransactionManager transactionManager,
      MeterRegistry metrics) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.transactions = new TransactionTemplate(transactionManager);
    this.metrics = metrics;
  }

  @Override
  public ContractBootstrapResult ensure(PersonalContractBootstrapRequest request) {
    return execute(ContractScope.PERSONAL, () -> {
      List<Long> users = jdbc.query(
          "SELECT id FROM identity_user WHERE id = ? FOR UPDATE",
          (row, number) -> row.getLong(1), request.userId());
      if (users.size() != 1) {
        return rejected(ContractScope.PERSONAL, "PLAN_CONTEXT_INVALID");
      }
      ExistingContract existing = personalContract(request.userId());
      if (existing != null) {
        return existingResult(existing, ContractScope.PERSONAL);
      }
      UUID publicId = UUID.randomUUID();
      long contractId = insertContract(
          ContractScope.PERSONAL, publicId, request.protocolId(), request.correlationId());
      jdbc.update("""
          INSERT INTO plans_personalContractHolder (idServiceContract, idUser)
          VALUES (?, ?)
          """, contractId, request.userId());
      insertAssignment(contractId, ContractScope.PERSONAL, request.protocolId());
      auditAndPublish(contractId, ContractScope.PERSONAL, request.userId(),
          "PERSONAL_CONTRACT_BOOTSTRAPPED", request.correlationId());
      return completed(ContractBootstrapStatus.COMPLETED, ContractScope.PERSONAL, publicId);
    });
  }

  @Override
  public ContractBootstrapResult ensure(TenantContractBootstrapRequest request) {
    return execute(ContractScope.TENANT, () -> {
      List<TenantContext> contexts = jdbc.query("""
          SELECT t.idTenant, a.idAccount
            FROM account_tenant t
            JOIN account_account a ON a.idTenant = t.idTenant
           WHERE t.publicId = UUID_TO_BIN(?) AND a.publicId = UUID_TO_BIN(?)
             AND a.founderUserId = ?
           FOR UPDATE
          """, (row, number) -> new TenantContext(row.getLong(1), row.getLong(2)),
          request.tenantPublicId().toString(), request.accountPublicId().toString(),
          request.founderUserId());
      if (contexts.size() != 1) {
        return rejected(ContractScope.TENANT, "PLAN_CONTEXT_INVALID");
      }
      TenantContext context = contexts.getFirst();
      ExistingContract existing = tenantContract(context.tenantId());
      if (existing != null) {
        return existingResult(existing, ContractScope.TENANT);
      }
      UUID publicId = UUID.randomUUID();
      long contractId = insertContract(
          ContractScope.TENANT, publicId, request.protocolId(), request.correlationId());
      jdbc.update("""
          INSERT INTO plans_tenantContractHolder (idServiceContract, idTenant)
          VALUES (?, ?)
          """, contractId, context.tenantId());
      insertAssignment(contractId, ContractScope.TENANT, request.protocolId());
      jdbc.update("""
          INSERT INTO plans_tenantUserCapacityOccupancy
            (idServiceContract, idTenant, idAccount, idUser, occupiedAt,
             sourceType, idempotencyKey)
          VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(6), 'FOUNDER', ?)
          """, contractId, context.tenantId(), context.accountId(), request.founderUserId(),
          uuidBytes(request.protocolId()));
      auditAndPublish(contractId, ContractScope.TENANT, request.founderUserId(),
          "TENANT_CONTRACT_BOOTSTRAPPED", request.correlationId());
      return completed(ContractBootstrapStatus.COMPLETED, ContractScope.TENANT, publicId);
    });
  }

  private ContractBootstrapResult execute(ContractScope scope, Bootstrap operation) {
    try {
      ContractBootstrapResult result = transactions.execute(status -> operation.execute());
      ContractBootstrapResult safe = result == null
          ? unavailable(scope) : result;
      count(scope, safe.status().name());
      return safe;
    } catch (DataAccessException | IllegalStateException failure) {
      count(scope, ContractBootstrapStatus.UNAVAILABLE.name());
      return unavailable(scope);
    }
  }

  private ExistingContract personalContract(long userId) {
    return existing("""
        SELECT c.publicId, c.status,
               (SELECT COUNT(*) FROM plans_planAssignment a
                 WHERE a.idServiceContract = c.idServiceContract
                   AND a.status = 'ACTIVE' AND a.currentMarker = 1)
          FROM plans_personalContractHolder h
          JOIN plans_serviceContract c ON c.idServiceContract = h.idServiceContract
         WHERE h.idUser = ?
        """, userId);
  }

  private ExistingContract tenantContract(long tenantId) {
    return existing("""
        SELECT c.publicId, c.status,
               (SELECT COUNT(*) FROM plans_planAssignment a
                 WHERE a.idServiceContract = c.idServiceContract
                   AND a.status = 'ACTIVE' AND a.currentMarker = 1)
          FROM plans_tenantContractHolder h
          JOIN plans_serviceContract c ON c.idServiceContract = h.idServiceContract
         WHERE h.idTenant = ?
        """, tenantId);
  }

  private ExistingContract existing(String sql, long holderId) {
    List<ExistingContract> values = jdbc.query(sql, (row, number) ->
        new ExistingContract(uuid(row.getBytes(1)), row.getString(2), row.getInt(3)), holderId);
    return values.size() == 1 ? values.getFirst() : null;
  }

  private ContractBootstrapResult existingResult(ExistingContract existing, ContractScope scope) {
    if (!"ACTIVE".equals(existing.status()) || existing.currentAssignments() != 1) {
      return rejected(scope, "PLAN_CONTRACT_INCONSISTENT");
    }
    return completed(ContractBootstrapStatus.ALREADY_COMPLETED, scope, existing.publicId());
  }

  private long insertContract(
      ContractScope scope,
      UUID publicId,
      UUID protocolId,
      String correlationId) {
    jdbc.update("""
        INSERT INTO plans_serviceContract
          (publicId, scopeType, status, startedAt, sourceType, idempotencyKey, correlationId)
        VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP(6), 'BOOTSTRAP', ?, ?)
        """, uuidBytes(publicId), scope.name(), digest(scope.name() + ":" + protocolId),
        correlationId);
    Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    if (id == null) {
      throw new IllegalStateException("service contract id unavailable");
    }
    return id;
  }

  private void insertAssignment(long contractId, ContractScope scope, UUID protocolId) {
    Timestamp now = Timestamp.from(Instant.now());
    List<Long> defaults = jdbc.query(DEFAULT_VERSION_SQL,
        (row, number) -> row.getLong(1), scope.name(), now, now, now, now);
    if (defaults.size() != 1) {
      throw new IllegalStateException("default plan version unavailable");
    }
    jdbc.update("""
        INSERT INTO plans_planAssignment
          (idServiceContract, idPlanVersion, scopeType, status, currentMarker,
           startedAt, sourceType, idempotencyKey)
        VALUES (?, ?, ?, 'ACTIVE', 1, CURRENT_TIMESTAMP(6), 'BOOTSTRAP', ?)
        """, contractId, defaults.getFirst(), scope.name(), uuidBytes(protocolId));
  }

  private void auditAndPublish(
      long contractId,
      ContractScope scope,
      long actorUserId,
      String eventType,
      String correlationId) {
    jdbc.update("""
        INSERT INTO plans_auditEvent
          (eventType, scopeType, idServiceContract, actorUserId,
           correlationId, safeResultCode, occurredAt)
        VALUES (?, ?, ?, ?, ?, 'COMPLETED', CURRENT_TIMESTAMP(6))
        """, eventType, scope.name(), contractId, actorUserId, correlationId);
    jdbc.update("""
        INSERT INTO plans_outboxEvent
          (eventId, aggregateType, aggregateId, eventType, payload, status)
        VALUES (UUID_TO_BIN(UUID()), 'CONTRACT', ?, ?,
                JSON_OBJECT('scope', ?), 'PENDING')
        """, contractId, eventType, scope.name());
  }

  private void count(ContractScope scope, String result) {
    Counter.builder("rinos.plans.contract.bootstrap")
        .tag("scope", scope.name())
        .tag("result", result)
        .register(metrics)
        .increment();
  }

  private static ContractBootstrapResult completed(
      ContractBootstrapStatus status, ContractScope scope, UUID publicId) {
    return new ContractBootstrapResult(status, scope, publicId, null);
  }

  private static ContractBootstrapResult rejected(ContractScope scope, String reason) {
    return new ContractBootstrapResult(ContractBootstrapStatus.REJECTED, scope, null, reason);
  }

  private static ContractBootstrapResult unavailable(ContractScope scope) {
    return new ContractBootstrapResult(
        ContractBootstrapStatus.UNAVAILABLE, scope, null, "PLAN_SOURCE_UNAVAILABLE");
  }

  private static byte[] uuidBytes(UUID value) {
    return ByteBuffer.allocate(16)
        .putLong(value.getMostSignificantBits())
        .putLong(value.getLeastSignificantBits())
        .array();
  }

  private static UUID uuid(byte[] value) {
    ByteBuffer buffer = ByteBuffer.wrap(value);
    return new UUID(buffer.getLong(), buffer.getLong());
  }

  private static byte[] digest(String value) {
    try {
      return MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }

  @FunctionalInterface
  private interface Bootstrap {
    ContractBootstrapResult execute();
  }

  private record ExistingContract(UUID publicId, String status, int currentAssignments) {
  }

  private record TenantContext(long tenantId, long accountId) {
  }
}
