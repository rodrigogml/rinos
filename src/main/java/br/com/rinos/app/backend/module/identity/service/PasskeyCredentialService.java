package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;
import br.com.rinos.app.backend.module.identity.entity.PasskeyCredentialEntity;
import br.com.rinos.app.backend.module.identity.entity.PasskeyUserEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.FactorOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.repository.PasskeyCredentialRepository;
import br.com.rinos.app.backend.module.identity.repository.PasskeyUserRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationMethodInventoryVO;
import br.com.rinos.app.backend.module.identity.vo.PasskeyCredentialRegistrationVO;
import br.com.rinos.app.backend.module.identity.vo.PasskeyCredentialSummaryVO;
import jakarta.persistence.EntityNotFoundException;

/**
 * Persiste material público WebAuthn e aplica invariantes antes de revogá-lo.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Service
@Lazy
public class PasskeyCredentialService {
  private final UserRepository users; private final PasskeyUserRepository passkeyUsers;
  private final PasskeyCredentialRepository credentials; private final AuthenticationMethodInventoryService inventory;
  private final IdentityReferenceService references; private final IdentityAuditService audit;
  public PasskeyCredentialService(UserRepository users, PasskeyUserRepository passkeyUsers,
      PasskeyCredentialRepository credentials, AuthenticationMethodInventoryService inventory,
      IdentityReferenceService references, IdentityAuditService audit) {
    this.users = users; this.passkeyUsers = passkeyUsers; this.credentials = credentials;
    this.inventory = inventory; this.references = references; this.audit = audit;
  }
  @Transactional
  public PasskeyCredentialSummaryVO register(Long userId, byte[] proposedUserHandle,
      PasskeyCredentialRegistrationVO material, UUID correlationId, Instant occurredAt) {
    UserEntity user = lockUser(userId); Objects.requireNonNull(material, "material must not be null");
    PasskeyUserEntity owner = passkeyUsers.findByUserIdForUpdate(userId).orElseGet(() ->
        passkeyUsers.saveAndFlush(new PasskeyUserEntity(user, proposedUserHandle)));
    PasskeyCredentialEntity credential = credentials.saveAndFlush(new PasskeyCredentialEntity(owner,
        references.generate(), material.credentialType(), material.credentialId(), material.publicKey(),
        material.signatureCount(), material.uvInitialized(), material.backupEligible(), material.backupState(),
        material.transports(), material.attestationObject(), material.attestationClientDataJson(), material.label()));
    event(user, correlationId, IdentityEventTypeEnum.AUTHENTICATION_METHOD_ADDED, occurredAt);
    return summary(credential);
  }
  @Transactional
  public FactorOperationStatusEnum recordUse(Long userId, UUID reference, long signatureCount,
      boolean backupState, Instant occurredAt) {
    lockUser(userId); locked(userId, reference).recordUse(signatureCount, backupState, occurredAt);
    return FactorOperationStatusEnum.USED;
  }
  @Transactional
  public PasskeyCredentialSummaryVO rename(
      Long userId,
      UUID reference,
      String label,
      UUID correlationId,
      Instant occurredAt) {
    UserEntity user = lockUser(userId);
    PasskeyCredentialEntity value = locked(userId, reference);
    value.rename(label);
    event(
        user,
        correlationId,
        IdentityEventTypeEnum.AUTHENTICATION_METHOD_RENAMED,
        occurredAt);
    return summary(value);
  }
  @Transactional
  public FactorOperationStatusEnum revoke(Long userId, UUID reference,
      boolean administrativeFactorRequired, UUID correlationId, Instant occurredAt) {
    UserEntity user = lockUser(userId); PasskeyCredentialEntity value = locked(userId, reference);
    AuthenticationMethodInventoryVO current = inventory.inspect(userId);
    if (current.initialMethodCount() <= 1) return FactorOperationStatusEnum.LAST_METHOD;
    if (administrativeFactorRequired && value.isUvInitialized() && current.administrativeFactorCount() <= 1)
      return FactorOperationStatusEnum.ADMIN_FACTOR_REQUIRED;
    value.revoke(occurredAt); event(user, correlationId, IdentityEventTypeEnum.AUTHENTICATION_METHOD_REMOVED, occurredAt);
    return FactorOperationStatusEnum.REVOKED;
  }
  @Transactional(readOnly = true)
  public List<PasskeyCredentialSummaryVO> list(Long userId) {
    return credentials.findByPasskeyUserUserIdOrderById(userId).stream().map(PasskeyCredentialService::summary).toList();
  }
  private UserEntity lockUser(Long userId) {
    if (userId == null || userId <= 0) throw new IllegalArgumentException("userId must be positive");
    return users.findByIdForUpdate(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));
  }
  private PasskeyCredentialEntity locked(Long userId, UUID reference) {
    return credentials.findByUserIdAndReferenceForUpdate(userId, references.encode(reference))
        .orElseThrow(() -> new EntityNotFoundException("Passkey credential not found"));
  }
  private void event(UserEntity user, UUID correlationId, IdentityEventTypeEnum type, Instant at) {
    audit.record(user, null, Objects.requireNonNull(correlationId), type, null, null,
        IdentityTransitionOriginEnum.SELF_SERVICE, "PASSKEY", at);
  }
  private static PasskeyCredentialSummaryVO summary(PasskeyCredentialEntity value) {
    return new PasskeyCredentialSummaryVO(value.getReference(), value.getLabel(), value.getStatus(),
        value.getCreatedAt(), value.getLastUsedAt());
  }
}
