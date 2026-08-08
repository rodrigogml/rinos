package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;
import br.com.rinos.app.backend.module.identity.entity.EmailFactorEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.FactorOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.repository.EmailFactorRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.EmailFactorSummaryVO;
import jakarta.persistence.EntityNotFoundException;

/**
 * Habilita o e-mail confirmado como fator sem copiar o endereço para outra tabela.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Service
@Lazy
public class EmailFactorService {
  private final UserRepository users; private final EmailFactorRepository factors;
  private final IdentityReferenceService references; private final IdentityAuditService audit;
  public EmailFactorService(UserRepository users, EmailFactorRepository factors,
      IdentityReferenceService references, IdentityAuditService audit) {
    this.users = users; this.factors = factors; this.references = references; this.audit = audit;
  }
  @Transactional
  public EmailFactorSummaryVO enable(Long userId, UUID correlationId, Instant occurredAt) {
    UserEntity user = lockUser(userId);
    java.util.Optional<EmailFactorEntity> existing = factors.findByUserIdForUpdate(userId);
    boolean changed = existing.isEmpty()
        || existing.get().getStatus()
            != br.com.rinos.app.backend.module.identity.enums.EmailFactorStatusEnum.ACTIVE;
    EmailFactorEntity factor = existing.orElseGet(() ->
        new EmailFactorEntity(user, references.generate(), occurredAt));
    if (factor.getStatus() != br.com.rinos.app.backend.module.identity.enums.EmailFactorStatusEnum.ACTIVE) factor.activate(occurredAt);
    factors.saveAndFlush(factor);
    if (changed) {
      audit(user, correlationId, IdentityEventTypeEnum.AUTHENTICATION_METHOD_ADDED, occurredAt);
    }
    return new EmailFactorSummaryVO(factor.getReference(), factor.getStatus(), factor.getActivatedAt(), factor.getLastUsedAt());
  }
  @Transactional
  public FactorOperationStatusEnum recordUse(Long userId, Instant occurredAt) {
    lockUser(userId); factor(userId).recordUse(occurredAt); return FactorOperationStatusEnum.USED;
  }
  @Transactional
  public FactorOperationStatusEnum disable(Long userId, UUID correlationId, Instant occurredAt) {
    UserEntity user = lockUser(userId); factor(userId).disable(occurredAt);
    audit(user, correlationId, IdentityEventTypeEnum.AUTHENTICATION_METHOD_REMOVED, occurredAt);
    return FactorOperationStatusEnum.DISABLED;
  }
  @Transactional(readOnly = true)
  public java.util.Optional<EmailFactorSummaryVO> inspect(Long userId) {
    return factors.findByUserId(userId).map(f -> new EmailFactorSummaryVO(f.getReference(), f.getStatus(), f.getActivatedAt(), f.getLastUsedAt()));
  }
  private UserEntity lockUser(Long userId) {
    if (userId == null || userId <= 0) throw new IllegalArgumentException("userId must be positive");
    return users.findByIdForUpdate(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));
  }
  private EmailFactorEntity factor(Long userId) { return factors.findByUserIdForUpdate(userId).orElseThrow(() -> new EntityNotFoundException("Email factor not found")); }
  private void audit(UserEntity user, UUID correlationId, IdentityEventTypeEnum type, Instant at) {
    audit.record(user, null, Objects.requireNonNull(correlationId), type, null, null,
        IdentityTransitionOriginEnum.SELF_SERVICE, "EMAIL_CODE", at);
  }
}
