package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;
import br.com.rinos.app.backend.module.identity.entity.TotpFactorEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.FactorOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.TotpFactorStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.TotpFactorRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationMethodInventoryVO;
import br.com.rinos.app.backend.module.identity.vo.TotpFactorSummaryVO;
import jakarta.persistence.EntityNotFoundException;

/**
 * Mantém enrollment e transições TOTP sem receber segredo em claro.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Service
@Lazy
public class TotpFactorService {
  private final UserRepository users;
  private final TotpFactorRepository factors;
  private final AuthenticationMethodInventoryService inventory;
  private final IdentityReferenceService references;
  private final IdentityAuditService audit;
  public TotpFactorService(UserRepository users, TotpFactorRepository factors,
      AuthenticationMethodInventoryService inventory, IdentityReferenceService references,
      IdentityAuditService audit) {
    this.users = users; this.factors = factors; this.inventory = inventory;
    this.references = references; this.audit = audit;
  }
  @Transactional
  public TotpFactorSummaryVO enroll(Long userId, String label, byte[] encryptedSecret,
      byte[] nonce, String keyVersion) {
    UserEntity user = lockUser(userId);
    TotpFactorEntity factor = factors.saveAndFlush(new TotpFactorEntity(user,
        references.generate(), label, encryptedSecret, nonce, keyVersion));
    return summary(factor);
  }
  @Transactional
  public FactorOperationStatusEnum confirm(Long userId, UUID reference, long acceptedStep,
      UUID correlationId, Instant occurredAt) {
    UserEntity user = lockUser(userId);
    TotpFactorEntity factor = locked(userId, reference);
    factor.confirm(acceptedStep, occurredAt);
    audit(user, correlationId, IdentityEventTypeEnum.AUTHENTICATION_METHOD_ADDED, "TOTP", occurredAt);
    return FactorOperationStatusEnum.ACTIVE;
  }
  @Transactional
  public FactorOperationStatusEnum acceptStep(Long userId, UUID reference, long acceptedStep,
      Instant occurredAt) {
    lockUser(userId);
    locked(userId, reference).acceptStep(acceptedStep, occurredAt);
    return FactorOperationStatusEnum.USED;
  }
  @Transactional
  public FactorOperationStatusEnum revoke(Long userId, UUID reference,
      boolean administrativeFactorRequired, UUID correlationId, Instant occurredAt) {
    UserEntity user = lockUser(userId);
    TotpFactorEntity factor = locked(userId, reference);
    if (factor.getStatus() == TotpFactorStatusEnum.ACTIVE && administrativeFactorRequired) {
      AuthenticationMethodInventoryVO current = inventory.inspect(userId);
      if (current.administrativeFactorCount() <= 1) return FactorOperationStatusEnum.ADMIN_FACTOR_REQUIRED;
    }
    factor.revoke(occurredAt);
    if (factor.getConfirmedAt() != null) audit(user, correlationId,
        IdentityEventTypeEnum.AUTHENTICATION_METHOD_REMOVED, "TOTP", occurredAt);
    return FactorOperationStatusEnum.REVOKED;
  }
  @Transactional(readOnly = true)
  public List<TotpFactorSummaryVO> list(Long userId) {
    return factors.findByUserIdOrderById(userId).stream().map(TotpFactorService::summary).toList();
  }
  private UserEntity lockUser(Long userId) {
    if (userId == null || userId <= 0) throw new IllegalArgumentException("userId must be positive");
    return users.findByIdForUpdate(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));
  }
  private TotpFactorEntity locked(Long userId, UUID reference) {
    return factors.findByUserIdAndReferenceForUpdate(userId, references.encode(reference))
        .orElseThrow(() -> new EntityNotFoundException("TOTP factor not found"));
  }
  private void audit(UserEntity user, UUID correlationId, IdentityEventTypeEnum type,
      String reason, Instant at) {
    audit.record(user, null, Objects.requireNonNull(correlationId), type, null, null,
        IdentityTransitionOriginEnum.SELF_SERVICE, reason, at);
  }
  private static TotpFactorSummaryVO summary(TotpFactorEntity value) {
    return new TotpFactorSummaryVO(value.getReference(), value.getLabel(), value.getStatus(),
        value.getConfirmedAt(), value.getLastUsedAt());
  }
}
