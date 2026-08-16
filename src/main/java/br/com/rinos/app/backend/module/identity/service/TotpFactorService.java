package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;
import br.com.rinos.app.backend.module.identity.entity.TotpFactorEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.FactorOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.TotpEnrollmentStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.TotpFactorStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.TotpFactorRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.EncryptedAuthenticationSecretVO;
import br.com.rinos.app.backend.module.identity.vo.IssuedTotpEnrollmentVO;
import br.com.rinos.app.backend.module.identity.vo.ProtectedTotpEnrollmentVO;
import br.com.rinos.app.backend.module.identity.vo.TotpFactorSummaryVO;
import br.com.rinos.app.config.AuthenticationMfaPropertiesConfig;
import jakarta.persistence.EntityNotFoundException;

/**
 * Mantém enrollment, confirmação e consumo TOTP sob locks globais da identidade.
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
  private final TotpProtocolService protocol;
  private final AuthenticationMfaPropertiesConfig properties;
  private final AdministrativeFactorContinuityPort factorContinuity;

  /** Cria a autoridade transacional dos fatores TOTP. */
  public TotpFactorService(UserRepository users, TotpFactorRepository factors,
      AuthenticationMethodInventoryService inventory, IdentityReferenceService references,
      IdentityAuditService audit, TotpProtocolService protocol,
      AuthenticationMfaPropertiesConfig properties,
      AdministrativeFactorContinuityPort factorContinuity) {
    this.users = users; this.factors = factors; this.inventory = inventory;
    this.references = references; this.audit = audit; this.protocol = protocol;
    this.properties = properties;
    this.factorContinuity = factorContinuity;
  }

  /**
   * Inicia uma apresentação única, substituindo qualquer pendência anterior do usuário.
   *
   * @return referência, validade, URI e segredo que não poderão ser consultados novamente
   */
  @Transactional
  public IssuedTotpEnrollmentVO begin(Long userId, Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    UserEntity user = lockActiveUser(userId);
    factors.findByUserIdAndStatusForUpdate(userId, TotpFactorStatusEnum.PENDING)
        .forEach(factor -> factor.cancelPending(occurredAt));
    UUID reference = references.generate();
    ProtectedTotpEnrollmentVO protectedEnrollment = protocol.create(
        user.getId(), reference, user.getEmail());
    EncryptedAuthenticationSecretVO encrypted = protectedEnrollment.encryptedSecret();
    Instant expiresAt = occurredAt.plus(properties.challengeValidity());
    factors.saveAndFlush(new TotpFactorEntity(
        user,
        reference,
        "Aplicativo autenticador",
        encrypted.ciphertext(),
        encrypted.nonce(),
        encrypted.keyVersion(),
        expiresAt));
    return new IssuedTotpEnrollmentVO(
        reference,
        expiresAt,
        protectedEnrollment.presentation().provisioningUri(),
        protectedEnrollment.presentation().secret());
  }

  /** Confirma uma pendência ainda válida e consome o passo usado na ativação. */
  @Transactional
  public TotpEnrollmentStatusEnum confirm(Long userId, UUID reference, String code,
      UUID correlationId, Instant occurredAt) {
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    UserEntity user = lockActiveUser(userId);
    TotpFactorEntity factor = lockedOptional(userId, reference);
    if (factor == null || factor.getStatus() != TotpFactorStatusEnum.PENDING) {
      return TotpEnrollmentStatusEnum.STALE;
    }
    if (!occurredAt.isBefore(factor.getEnrollmentExpiresAt())) {
      factor.cancelPending(occurredAt);
      return TotpEnrollmentStatusEnum.EXPIRED;
    }
    OptionalLong acceptedStep = protocol.acceptedStep(
        user.getId(), reference, encrypted(factor), code, occurredAt);
    if (acceptedStep.isEmpty()) {
      return factor.rejectEnrollmentAttempt(properties.maximumAttempts(), occurredAt)
          ? TotpEnrollmentStatusEnum.ATTEMPTS_EXHAUSTED
          : TotpEnrollmentStatusEnum.REJECTED;
    }
    factor.confirm(acceptedStep.getAsLong(), occurredAt);
    audit(user, correlationId, IdentityEventTypeEnum.AUTHENTICATION_METHOD_ADDED, "TOTP", occurredAt);
    factorContinuity.afterStrongFactorEstablished(correlationId, occurredAt);
    return TotpEnrollmentStatusEnum.ACTIVE;
  }

  /** Valida qualquer fator ativo e consome atomicamente o passo exato aceito. */
  @Transactional
  public FactorOperationStatusEnum verifyActive(Long userId, String code, Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    UserEntity user = lockActiveUser(userId);
    for (TotpFactorEntity factor : factors.findByUserIdAndStatusForUpdate(
        userId, TotpFactorStatusEnum.ACTIVE)) {
      OptionalLong acceptedStep = protocol.acceptedStep(
          user.getId(), factor.getReference(), encrypted(factor), code, occurredAt);
      if (acceptedStep.isPresent()
          && (factor.getLastAcceptedStep() == null
              || acceptedStep.getAsLong() > factor.getLastAcceptedStep())) {
        factor.acceptStep(acceptedStep.getAsLong(), occurredAt);
        return FactorOperationStatusEnum.USED;
      }
    }
    return FactorOperationStatusEnum.REJECTED;
  }

  /** Invalida idempotentemente uma tentativa pendente sem afetar fator já confirmado. */
  @Transactional
  public FactorOperationStatusEnum cancel(Long userId, UUID reference, Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    lockUser(userId);
    TotpFactorEntity factor = lockedOptional(userId, reference);
    if (factor == null || factor.getStatus() == TotpFactorStatusEnum.REVOKED) {
      return FactorOperationStatusEnum.REVOKED;
    }
    return factor.cancelPending(occurredAt)
        ? FactorOperationStatusEnum.REVOKED : FactorOperationStatusEnum.REJECTED;
  }
  @Transactional
  public FactorOperationStatusEnum revoke(Long userId, UUID reference,
      boolean administrativeFactorRequired, UUID correlationId, Instant occurredAt) {
    AdministrativeFactorContinuityContext continuityContext =
        factorContinuity.lockContexts(userId);
    UserEntity user = lockUser(userId);
    TotpFactorEntity factor = locked(userId, reference);
    boolean removedAdministrativeFactor = factor.getStatus() == TotpFactorStatusEnum.ACTIVE;
    factor.revoke(occurredAt);
    if (removedAdministrativeFactor) {
      factors.flush();
      factorContinuity.validateAndRevise(continuityContext, occurredAt);
    }
    if (factor.getConfirmedAt() != null) audit(user, correlationId,
        IdentityEventTypeEnum.AUTHENTICATION_METHOD_REMOVED, "TOTP", occurredAt);
    return FactorOperationStatusEnum.REVOKED;
  }
  @Transactional(readOnly = true)
  public List<TotpFactorSummaryVO> listActive(Long userId) {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    return factors.findByUserIdAndStatusOrderById(userId, TotpFactorStatusEnum.ACTIVE)
        .stream().map(TotpFactorService::summary).toList();
  }

  private UserEntity lockUser(Long userId) {
    if (userId == null || userId <= 0) throw new IllegalArgumentException("userId must be positive");
    return users.findByIdForUpdate(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));
  }

  private UserEntity lockActiveUser(Long userId) {
    UserEntity user = lockUser(userId);
    if (user.getStatus() != br.com.rinos.app.backend.module.identity.enums.UserStatusEnum.ACTIVE) {
      throw new SecurityException("Active user is required");
    }
    return user;
  }

  private TotpFactorEntity locked(Long userId, UUID reference) {
    return factors.findByUserIdAndReferenceForUpdate(userId, references.encode(reference))
        .orElseThrow(() -> new EntityNotFoundException("TOTP factor not found"));
  }

  private TotpFactorEntity lockedOptional(Long userId, UUID reference) {
    if (reference == null) {
      return null;
    }
    return factors.findByUserIdAndReferenceForUpdate(userId, references.encode(reference))
        .orElse(null);
  }
  private void audit(UserEntity user, UUID correlationId, IdentityEventTypeEnum type,
      String reason, Instant at) {
    audit.record(user, null, Objects.requireNonNull(correlationId), type, null, null,
        IdentityTransitionOriginEnum.SELF_SERVICE, reason, at);
  }
  private static TotpFactorSummaryVO summary(TotpFactorEntity value) {
    return new TotpFactorSummaryVO(value.getReference(), value.getLabel(), value.getStatus(),
        value.getCreatedAt(), value.getConfirmedAt(), value.getLastUsedAt());
  }

  private static EncryptedAuthenticationSecretVO encrypted(TotpFactorEntity factor) {
    return new EncryptedAuthenticationSecretVO(
        factor.getEncryptedSecret(), factor.getEncryptionNonce(), factor.getKeyVersion());
  }
}
