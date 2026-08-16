package br.com.rinos.app.backend.module.plans.service;

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

import br.com.rinos.app.api.module.plans.dto.AssociationCapacityRequest;
import br.com.rinos.app.api.module.plans.dto.InvitationAcceptanceCapacityRequest;
import br.com.rinos.app.api.module.plans.dto.InvitationCapacityReleaseRequest;
import br.com.rinos.app.api.module.plans.dto.InvitationCapacityRequest;
import br.com.rinos.app.api.module.plans.dto.TenantUserCapacityRequest;
import br.com.rinos.app.api.module.plans.enums.TenantUserCapacityStatus;
import br.com.rinos.app.api.module.plans.facade.TenantUserCapacityFacade;
import br.com.rinos.app.api.module.plans.vo.TenantUserCapacityResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/** Autoridade transacional e cross-instance da capacidade permanente de usuários do tenant. */
@Service
@ConditionalOnBean(DataSource.class)
public class JdbcTenantUserCapacityService implements TenantUserCapacityFacade {

  private static final String CONTEXT_SQL = """
      SELECT c.idServiceContract, a.idAccount, e.quantityValue
        FROM plans_tenantContractHolder h
        JOIN plans_serviceContract c ON c.idServiceContract = h.idServiceContract
        JOIN account_account a ON a.idTenant = h.idTenant
        JOIN account_tenant t ON t.idTenant = h.idTenant
        JOIN plans_planAssignment pa ON pa.idServiceContract = c.idServiceContract
        JOIN plans_planVersion pv ON pv.idPlanVersion = pa.idPlanVersion
        JOIN plans_planVersionEntitlement e ON e.idPlanVersion = pv.idPlanVersion
        JOIN plans_entitlementDefinition d
          ON d.idEntitlementDefinition = e.idEntitlementDefinition
       WHERE h.idTenant = ? AND c.scopeType = 'TENANT' AND c.status = 'ACTIVE'
         AND c.startedAt <= ? AND (c.endedAt IS NULL OR c.endedAt > ?)
         AND a.status = 'ACTIVE' AND t.status = 'OPERATIONAL'
         AND pa.status = 'ACTIVE' AND pa.currentMarker = 1
         AND pa.startedAt <= ? AND (pa.endedAt IS NULL OR pa.endedAt > ?)
         AND pv.status = 'PUBLISHED' AND d.status = 'ACTIVE'
         AND (pv.validFrom IS NULL OR pv.validFrom <= ?)
         AND (pv.validUntil IS NULL OR pv.validUntil > ?)
         AND d.entitlementCode = 'membership.associated-users.limit'
         AND d.scopeType = 'TENANT' AND e.quantityValue IS NOT NULL
       FOR UPDATE
      """;
  private static final String COUNTS_SQL = """
      SELECT
        (SELECT COUNT(*) FROM plans_tenantUserCapacityOccupancy
          WHERE idServiceContract = ?),
        (SELECT COUNT(*) FROM plans_tenantUserCapacityReservation
          WHERE idServiceContract = ? AND capacityMarker = 1 AND expiresAt > ?)
      """;

  private final JdbcOperations jdbc;
  private final TransactionTemplate transactions;
  private final MeterRegistry metrics;

  public JdbcTenantUserCapacityService(
      DataSource dataSource,
      PlatformTransactionManager transactionManager,
      MeterRegistry metrics) {
    this(new JdbcTemplate(dataSource), new TransactionTemplate(transactionManager), metrics);
  }

  JdbcTenantUserCapacityService(
      JdbcOperations jdbc,
      TransactionTemplate transactions,
      MeterRegistry metrics) {
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.metrics = metrics;
  }

  @Override
  public TenantUserCapacityResult reserve(InvitationCapacityRequest request) {
    return execute("reserve", () -> {
      Instant now = request.requestedAt();
      CapacityContext context = lockContext(request.tenantId(), now);
      if (context == null) {
        return unavailable();
      }
      expireReservations(context.contractId(), now);
      if (request.prospectiveUserId() != null
          && occupancy(context, request.prospectiveUserId()) != null) {
        return result(TenantUserCapacityStatus.ALREADY_OCCUPIED, context, now, null);
      }
      Invitation invitation = invitation(request.tenantId(), request.invitationId());
      if (invitation == null || invitation.accountId() != context.accountId()) {
        return rejected(context, now, "PLAN_CONTEXT_INVALID");
      }
      Reservation reservation = reservation(invitation.id());
      if (reservation != null) {
        TenantUserCapacityStatus status = "RESERVED".equals(reservation.status())
            ? TenantUserCapacityStatus.ALREADY_RESERVED
            : TenantUserCapacityStatus.REJECTED;
        return status == TenantUserCapacityStatus.REJECTED
            ? rejected(context, now, "PLAN_CONTEXT_INVALID")
            : result(status, context, now, null);
      }
      Counts counts = counts(context.contractId(), now);
      if (counts.used() >= context.limit()) {
        audit(context, "CAPACITY_RESERVATION_REJECTED", request.correlationId(),
            "PLAN_LIMIT_REACHED");
        return result(TenantUserCapacityStatus.LIMIT_REACHED, context, counts,
            "PLAN_LIMIT_REACHED");
      }
      jdbc.update("""
          INSERT INTO plans_tenantUserCapacityReservation
            (idServiceContract, idTenant, idAccount, idMembershipInvitation,
             recipientDigest, recipientKeyId, idUser, status, capacityMarker,
             expiresAt, idempotencyKey)
          VALUES (?, ?, ?, ?, ?, 'fingerprint-v1', ?, 'RESERVED', 1, ?, ?)
          """, context.contractId(), request.tenantId(), context.accountId(), invitation.id(),
          digest(request.recipientFingerprint()), request.prospectiveUserId(),
          Timestamp.from(request.expiresAt()), uuidBytes(request.invitationId()));
      auditAndPublish(context, "CAPACITY_RESERVED", request.correlationId(), "COMPLETED");
      return result(TenantUserCapacityStatus.RESERVED, context, now, null);
    });
  }

  @Override
  public TenantUserCapacityResult occupy(AssociationCapacityRequest request) {
    return execute("occupy", () -> {
      Instant now = Instant.now();
      CapacityContext context = lockContext(request.tenantId(), now);
      if (context == null) {
        return unavailable();
      }
      expireReservations(context.contractId(), now);
      if (occupancy(context, request.userId()) != null) {
        return result(TenantUserCapacityStatus.ALREADY_OCCUPIED, context, now, null);
      }
      Counts counts = counts(context.contractId(), now);
      if (counts.used() >= context.limit()) {
        audit(context, "CAPACITY_OCCUPATION_REJECTED", request.correlationId(),
            "PLAN_LIMIT_REACHED");
        return result(TenantUserCapacityStatus.LIMIT_REACHED, context, counts,
            "PLAN_LIMIT_REACHED");
      }
      insertOccupancy(context, request.tenantId(), request.userId(), request.intentionId(),
          "MANUAL", now);
      auditAndPublish(context, "CAPACITY_OCCUPIED", request.correlationId(), "COMPLETED");
      return result(TenantUserCapacityStatus.OCCUPIED, context, now, null);
    });
  }

  @Override
  public TenantUserCapacityResult convert(InvitationAcceptanceCapacityRequest request) {
    return execute("convert", () -> {
      Instant now = Instant.now();
      CapacityContext context = lockContext(request.tenantId(), now);
      if (context == null) {
        return unavailable();
      }
      expireReservations(context.contractId(), now);
      Invitation invitation = invitation(request.tenantId(), request.invitationId());
      if (invitation == null || invitation.accountId() != context.accountId()) {
        return rejected(context, now, "PLAN_CONTEXT_INVALID");
      }
      Reservation reservation = reservation(invitation.id());
      Long occupancyId = occupancy(context, request.userId());
      if (occupancyId != null) {
        if (reservation != null && "RESERVED".equals(reservation.status())) {
          convertReservation(reservation.id(), occupancyId, request.userId());
          auditAndPublish(context, "CAPACITY_CONVERTED", request.correlationId(), "COMPLETED");
        }
        return result(TenantUserCapacityStatus.ALREADY_OCCUPIED, context, now, null);
      }
      if (reservation == null || !"RESERVED".equals(reservation.status())) {
        Counts counts = counts(context.contractId(), now);
        if (counts.used() >= context.limit()) {
          return result(TenantUserCapacityStatus.LIMIT_REACHED, context, counts,
              "PLAN_LIMIT_REACHED");
        }
      }
      long createdId = insertOccupancy(context, request.tenantId(), request.userId(),
          request.invitationId(), "INVITATION", now);
      if (reservation != null && "RESERVED".equals(reservation.status())) {
        convertReservation(reservation.id(), createdId, request.userId());
      }
      auditAndPublish(context, "CAPACITY_CONVERTED", request.correlationId(), "COMPLETED");
      return result(TenantUserCapacityStatus.OCCUPIED, context, now, null);
    });
  }

  @Override
  public TenantUserCapacityResult releaseUnaccepted(InvitationCapacityReleaseRequest request) {
    return execute("release", () -> {
      Instant now = Instant.now();
      CapacityContext context = lockContext(request.tenantId(), now);
      if (context == null) {
        return unavailable();
      }
      Invitation invitation = invitation(request.tenantId(), request.invitationId());
      Reservation reservation = invitation == null ? null : reservation(invitation.id());
      if (reservation == null || "RELEASED".equals(reservation.status())
          || "EXPIRED".equals(reservation.status())) {
        return result(TenantUserCapacityStatus.RELEASED, context, now, null);
      }
      if (!"RESERVED".equals(reservation.status())) {
        return rejected(context, now, "PLAN_CAPACITY_ALREADY_CONVERTED");
      }
      jdbc.update("""
          UPDATE plans_tenantUserCapacityReservation
             SET status = 'RELEASED', capacityMarker = NULL, version = version + 1
           WHERE idTenantUserCapacityReservation = ? AND status = 'RESERVED'
          """, reservation.id());
      auditAndPublish(context, "CAPACITY_RELEASED", request.correlationId(), "COMPLETED");
      return result(TenantUserCapacityStatus.RELEASED, context, now, null);
    });
  }

  @Override
  public TenantUserCapacityResult inspect(TenantUserCapacityRequest request) {
    return execute("inspect", () -> {
      Instant now = Instant.now();
      CapacityContext context = lockContext(request.tenantId(), now);
      if (context == null) {
        return unavailable();
      }
      expireReservations(context.contractId(), now);
      if (request.prospectiveUserId() != null
          && occupancy(context, request.prospectiveUserId()) != null) {
        return result(TenantUserCapacityStatus.ALREADY_OCCUPIED, context, now, null);
      }
      Counts counts = counts(context.contractId(), now);
      TenantUserCapacityStatus status = counts.used() < context.limit()
          ? TenantUserCapacityStatus.AVAILABLE : TenantUserCapacityStatus.LIMIT_REACHED;
      return result(status, context, counts,
          status == TenantUserCapacityStatus.AVAILABLE ? null : "PLAN_LIMIT_REACHED");
    });
  }

  private TenantUserCapacityResult execute(String operation, Operation action) {
    try {
      TenantUserCapacityResult result = transactions.execute(status -> action.execute());
      TenantUserCapacityResult safe = result == null ? unavailable() : result;
      count(operation, safe.status().name());
      return safe;
    } catch (DataAccessException | IllegalStateException failure) {
      count(operation, "SOURCE_UNAVAILABLE");
      return unavailable();
    }
  }

  private CapacityContext lockContext(long tenantId, Instant now) {
    Timestamp instant = Timestamp.from(now);
    List<CapacityContext> contexts = jdbc.query(CONTEXT_SQL, (row, number) ->
        new CapacityContext(row.getLong(1), row.getLong(2), row.getLong(3)),
        tenantId, instant, instant, instant, instant, instant, instant);
    return contexts.size() == 1 ? contexts.getFirst() : null;
  }

  private void expireReservations(long contractId, Instant now) {
    jdbc.update("""
        UPDATE plans_tenantUserCapacityReservation
           SET status = 'EXPIRED', capacityMarker = NULL, version = version + 1
         WHERE idServiceContract = ? AND status = 'RESERVED' AND expiresAt <= ?
        """, contractId, Timestamp.from(now));
  }

  private Counts counts(long contractId, Instant now) {
    return jdbc.queryForObject(COUNTS_SQL, (row, number) ->
        new Counts(row.getLong(1), row.getLong(2)),
        contractId, contractId, Timestamp.from(now));
  }

  private Long occupancy(CapacityContext context, long userId) {
    List<Long> values = jdbc.query("""
        SELECT idTenantUserCapacityOccupancy
          FROM plans_tenantUserCapacityOccupancy
         WHERE idServiceContract = ? AND idUser = ?
        """, (row, number) -> row.getLong(1), context.contractId(), userId);
    return values.isEmpty() ? null : values.getFirst();
  }

  private Invitation invitation(long tenantId, UUID publicId) {
    List<Invitation> values = jdbc.query("""
        SELECT i.idMembershipInvitation, i.idAccount
          FROM membership_invitation i
          JOIN account_account a ON a.idAccount = i.idAccount
         WHERE i.publicId = UUID_TO_BIN(?) AND a.idTenant = ?
        """, (row, number) -> new Invitation(row.getLong(1), row.getLong(2)),
        publicId.toString(), tenantId);
    return values.size() == 1 ? values.getFirst() : null;
  }

  private Reservation reservation(long invitationId) {
    List<Reservation> values = jdbc.query("""
        SELECT idTenantUserCapacityReservation, status
          FROM plans_tenantUserCapacityReservation
         WHERE idMembershipInvitation = ?
        """, (row, number) -> new Reservation(row.getLong(1), row.getString(2)), invitationId);
    return values.isEmpty() ? null : values.getFirst();
  }

  private long insertOccupancy(
      CapacityContext context,
      long tenantId,
      long userId,
      UUID idempotencyKey,
      String source,
      Instant now) {
    jdbc.update("""
        INSERT INTO plans_tenantUserCapacityOccupancy
          (idServiceContract, idTenant, idAccount, idUser, occupiedAt,
           sourceType, idempotencyKey)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """, context.contractId(), tenantId, context.accountId(), userId,
        Timestamp.from(now), source, uuidBytes(idempotencyKey));
    Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    if (id == null) {
      throw new IllegalStateException("capacity occupancy id unavailable");
    }
    return id;
  }

  private void convertReservation(long reservationId, long occupancyId, long userId) {
    jdbc.update("""
        UPDATE plans_tenantUserCapacityReservation
           SET status = 'CONVERTED', capacityMarker = NULL,
               convertedOccupancyId = ?, idUser = ?, version = version + 1
         WHERE idTenantUserCapacityReservation = ? AND status = 'RESERVED'
        """, occupancyId, userId, reservationId);
  }

  private TenantUserCapacityResult result(
      TenantUserCapacityStatus status,
      CapacityContext context,
      Instant now,
      String reason) {
    return result(status, context, counts(context.contractId(), now), reason);
  }

  private static TenantUserCapacityResult result(
      TenantUserCapacityStatus status,
      CapacityContext context,
      Counts counts,
      String reason) {
    return new TenantUserCapacityResult(
        status, context.limit(), counts.occupied(), counts.reserved(), reason);
  }

  private TenantUserCapacityResult rejected(
      CapacityContext context, Instant now, String reason) {
    return result(TenantUserCapacityStatus.REJECTED, context, now, reason);
  }

  private static TenantUserCapacityResult unavailable() {
    return new TenantUserCapacityResult(
        TenantUserCapacityStatus.SOURCE_UNAVAILABLE, 0, 0, 0, "PLAN_SOURCE_UNAVAILABLE");
  }

  private void auditAndPublish(
      CapacityContext context, String eventType, String correlationId, String resultCode) {
    audit(context, eventType, correlationId, resultCode);
    jdbc.update("""
        INSERT INTO plans_outboxEvent
          (eventId, aggregateType, aggregateId, eventType, payload, status)
        VALUES (UUID_TO_BIN(UUID()), 'CAPACITY', ?, ?,
                JSON_OBJECT('resultCode', ?), 'PENDING')
        """, context.contractId(), eventType, resultCode);
  }

  private void audit(
      CapacityContext context, String eventType, String correlationId, String resultCode) {
    jdbc.update("""
        INSERT INTO plans_auditEvent
          (eventType, scopeType, idServiceContract, systemOrigin,
           correlationId, safeResultCode, occurredAt)
        VALUES (?, 'TENANT', ?, 'plans-capacity', ?, ?, CURRENT_TIMESTAMP(6))
        """, eventType, context.contractId(), correlationId, resultCode);
  }

  private void count(String operation, String result) {
    Counter.builder("rinos.plans.capacity.operations")
        .tag("operation", operation)
        .tag("result", result)
        .register(metrics)
        .increment();
  }

  private static byte[] uuidBytes(UUID value) {
    var buffer = java.nio.ByteBuffer.allocate(16);
    buffer.putLong(value.getMostSignificantBits());
    buffer.putLong(value.getLeastSignificantBits());
    return buffer.array();
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
  private interface Operation {
    TenantUserCapacityResult execute();
  }

  private record CapacityContext(long contractId, long accountId, long limit) {
  }

  private record Counts(long occupied, long reserved) {
    long used() {
      return Math.addExact(occupied, reserved);
    }
  }

  private record Invitation(long id, long accountId) {
  }

  private record Reservation(long id, String status) {
  }
}
