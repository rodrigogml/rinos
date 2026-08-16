package br.com.rinos.app.backend.module.plans.component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.rinos.app.api.module.plans.dto.AssociationCapacityRequest;
import br.com.rinos.app.api.module.plans.dto.InvitationAcceptanceCapacityRequest;
import br.com.rinos.app.api.module.plans.dto.InvitationCapacityReleaseRequest;
import br.com.rinos.app.api.module.plans.dto.InvitationCapacityRequest;
import br.com.rinos.app.api.module.plans.dto.TenantUserCapacityRequest;
import br.com.rinos.app.api.module.plans.enums.TenantUserCapacityStatus;
import br.com.rinos.app.api.module.plans.facade.TenantUserCapacityFacade;
import br.com.rinos.app.api.module.plans.vo.TenantUserCapacityResult;
import br.com.rinos.app.backend.module.account.repository.AccountRepository;
import br.com.rinos.app.backend.module.membership.service.MembershipPlanCapacityDecision;
import br.com.rinos.app.backend.module.membership.service.MembershipPlanCapacityPort;

/** Adapta os identificadores internos de membership à facade pública de capacidade. */
@Component
public class MembershipPlanCapacityAdapter implements MembershipPlanCapacityPort {

  private final AccountRepository accounts;
  private final TenantUserCapacityFacade capacity;

  public MembershipPlanCapacityAdapter(
      AccountRepository accounts,
      TenantUserCapacityFacade capacity) {
    this.accounts = accounts;
    this.capacity = capacity;
  }

  @Autowired
  public MembershipPlanCapacityAdapter(
      ObjectProvider<AccountRepository> accounts,
      ObjectProvider<TenantUserCapacityFacade> capacity) {
    this.accounts = accounts.getIfAvailable();
    this.capacity = capacity.getIfAvailable();
  }

  @Override
  public MembershipPlanCapacityDecision evaluate(long accountId, long prospectiveUserId) {
    Long tenantId = tenantId(accountId);
    if (tenantId == null) {
      return MembershipPlanCapacityDecision.unavailable();
    }
    TenantUserCapacityResult result = capacity.inspect(
        new TenantUserCapacityRequest(tenantId, prospectiveUserId));
    if (result.status() == TenantUserCapacityStatus.SOURCE_UNAVAILABLE) {
      return MembershipPlanCapacityDecision.unavailable();
    }
    return result.status() == TenantUserCapacityStatus.AVAILABLE
        || result.status() == TenantUserCapacityStatus.ALREADY_OCCUPIED
        ? MembershipPlanCapacityDecision.permit()
        : new MembershipPlanCapacityDecision(true, false);
  }

  @Override
  public TenantUserCapacityResult reserve(
      long accountId,
      UUID invitationId,
      String normalizedEmail,
      Long prospectiveUserId,
      Instant requestedAt,
      Instant expiresAt,
      String correlationId) {
    Long tenantId = tenantId(accountId);
    return tenantId == null ? unavailable() : capacity.reserve(new InvitationCapacityRequest(
        tenantId, invitationId, fingerprint(normalizedEmail), prospectiveUserId,
        requestedAt, expiresAt, correlationId));
  }

  @Override
  public TenantUserCapacityResult convert(
      long accountId, long userId, UUID invitationId, String correlationId) {
    Long tenantId = tenantId(accountId);
    return tenantId == null ? unavailable() : capacity.convert(
        new InvitationAcceptanceCapacityRequest(tenantId, userId, invitationId, correlationId));
  }

  @Override
  public TenantUserCapacityResult release(
      long accountId, UUID invitationId, String correlationId) {
    Long tenantId = tenantId(accountId);
    return tenantId == null ? unavailable() : capacity.releaseUnaccepted(
        new InvitationCapacityReleaseRequest(tenantId, invitationId, correlationId));
  }

  @Override
  public TenantUserCapacityResult occupy(
      long accountId, long userId, UUID intentionId, String correlationId) {
    Long tenantId = tenantId(accountId);
    return tenantId == null ? unavailable() : capacity.occupy(
        new AssociationCapacityRequest(tenantId, userId, intentionId, correlationId));
  }

  private Long tenantId(long accountId) {
    return accounts == null || capacity == null ? null
        : accounts.findById(accountId).map(account -> account.getTenantId()).orElse(null);
  }

  private static String fingerprint(String normalizedEmail) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(normalizedEmail.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }

  private static TenantUserCapacityResult unavailable() {
    return new TenantUserCapacityResult(
        TenantUserCapacityStatus.SOURCE_UNAVAILABLE, 0, 0, 0, "PLAN_SOURCE_UNAVAILABLE");
  }
}
