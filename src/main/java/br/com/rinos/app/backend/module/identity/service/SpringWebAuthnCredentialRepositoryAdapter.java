package br.com.rinos.app.backend.module.identity.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.PasskeyCredentialEntity;
import br.com.rinos.app.backend.module.identity.entity.PasskeyUserEntity;
import br.com.rinos.app.backend.module.identity.enums.PasskeyCredentialStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.ReauthenticationOperationEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.PasskeyCredentialRepository;
import br.com.rinos.app.backend.module.identity.repository.PasskeyUserRepository;
import br.com.rinos.app.backend.module.identity.vo.PasskeyCredentialRegistrationVO;
import br.com.rinos.app.backend.module.identity.vo.PasskeyCredentialSummaryVO;
import br.eng.rodrigogml.rfw.authentication.principal.RFWAuthenticationSessionPrincipal;

/**
 * Adapta os registros de credential WebAuthn validados pelo Spring ao modelo global do Rinos.
 *
 * <p>Inserções conservam todo o material público do protocolo. Saves posteriores podem atualizar
 * apenas contador, backup state e último uso; owner, ID, chave, attestation e label não podem ser
 * substituídos por esta borda técnica.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
@Service
@Lazy
public class SpringWebAuthnCredentialRepositoryAdapter implements UserCredentialRepository {

  private final PasskeyUserRepository passkeyUsers;
  private final PasskeyCredentialRepository credentials;
  private final PasskeyCredentialService passkeyService;
  private final ReauthenticationService reauthenticationService;
  private final Clock clock;

  /** Cria o adapter com relógio UTC para valores omitidos pelo update do protocolo. */
  @Autowired
  public SpringWebAuthnCredentialRepositoryAdapter(
      PasskeyUserRepository passkeyUsers,
      PasskeyCredentialRepository credentials,
      @Lazy PasskeyCredentialService passkeyService,
      @Lazy ReauthenticationService reauthenticationService) {
    this(
        passkeyUsers,
        credentials,
        passkeyService,
        reauthenticationService,
        Clock.systemUTC());
  }

  /** Cria uma instância com relógio controlável para testes. */
  SpringWebAuthnCredentialRepositoryAdapter(
      PasskeyUserRepository passkeyUsers,
      PasskeyCredentialRepository credentials,
      PasskeyCredentialService passkeyService,
      ReauthenticationService reauthenticationService,
      Clock clock) {
    this.passkeyUsers = passkeyUsers;
    this.credentials = credentials;
    this.passkeyService = passkeyService;
    this.reauthenticationService = reauthenticationService;
    this.clock = clock;
  }

  /** {@inheritDoc} */
  @Override
  @Transactional(readOnly = true)
  public CredentialRecord findByCredentialId(Bytes credentialId) {
    if (credentialId == null) {
      return null;
    }
    return credentials.findByCredentialId(credentialId.getBytes())
        .filter(SpringWebAuthnCredentialRepositoryAdapter::isUsable)
        .map(SpringWebAuthnCredentialRepositoryAdapter::view)
        .orElse(null);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional(readOnly = true)
  public List<CredentialRecord> findByUserId(Bytes userId) {
    if (userId == null) {
      return List.of();
    }
    return credentials.findByPasskeyUserUserHandleAndStatusOrderById(
        userId.getBytes(), PasskeyCredentialStatusEnum.ACTIVE).stream()
        .filter(SpringWebAuthnCredentialRepositoryAdapter::isUsable)
        .map(SpringWebAuthnCredentialRepositoryAdapter::view)
        .toList();
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void save(CredentialRecord record) {
    if (record == null || record.getCredentialId() == null
        || record.getUserEntityUserId() == null) {
      throw new IllegalArgumentException("WebAuthn credential record is required");
    }
    byte[] credentialId = record.getCredentialId().getBytes();
    PasskeyCredentialEntity current = credentials.findByCredentialIdForUpdate(credentialId)
        .orElse(null);
    if (current != null) {
      updateCurrent(current, record);
      return;
    }
    PasskeyUserEntity owner = passkeyUsers.findByUserHandleForUpdate(
        record.getUserEntityUserId().getBytes())
        .filter(candidate -> candidate.getUser().getStatus() == UserStatusEnum.ACTIVE)
        .orElseThrow(() -> new IllegalArgumentException("Active WebAuthn owner is required"));
    Instant registeredAt = clock.instant();
    UUID sessionReference = currentSessionReference();
    if (sessionReference == null || !reauthenticationService.isRecentlyAuthorized(
        owner.getUser().getId(),
        sessionReference,
        ReauthenticationOperationEnum.REGISTER_PASSKEY,
        registeredAt)) {
      throw new SecurityException("Recent authentication is required for passkey registration");
    }
    PasskeyCredentialSummaryVO registered = passkeyService.register(
        owner.getUser().getId(),
        owner.getUserHandle(),
        new PasskeyCredentialRegistrationVO(
        record.getCredentialType().getValue(),
        credentialId,
        record.getPublicKey().getBytes(),
        record.getSignatureCount(),
        record.isUvInitialized(),
        record.isBackupEligible(),
        record.isBackupState(),
        transports(record.getTransports()),
        record.getAttestationObject().getBytes(),
        record.getAttestationClientDataJSON().getBytes(),
        record.getLabel()),
        UUID.randomUUID(),
        registeredAt);
    if (record.getLastUsed() != null) {
      passkeyService.recordUse(
          owner.getUser().getId(),
          registered.reference(),
          record.getSignatureCount(),
          record.isBackupState(),
          record.getLastUsed());
    }
  }

  private static UUID currentSessionReference() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()
        || !(authentication.getPrincipal()
            instanceof RFWAuthenticationSessionPrincipal principal)) {
      return null;
    }
    try {
      return UUID.fromString(principal.sessionReference());
    } catch (NullPointerException | IllegalArgumentException invalid) {
      return null;
    }
  }

  /**
   * Rejeita exclusão técnica para preservar revogação lógica, último método e auditoria.
   *
   * @throws UnsupportedOperationException sempre; revogação usa a fachada de gestão
   */
  @Override
  public void delete(Bytes credentialId) {
    throw new UnsupportedOperationException(
        "WebAuthn credential deletion must use passkey management");
  }

  private void updateCurrent(PasskeyCredentialEntity current, CredentialRecord record) {
    if (!isUsable(current)
        || !Arrays.equals(current.getPasskeyUser().getUserHandle(),
            record.getUserEntityUserId().getBytes())
        || !current.getCredentialType().equals(record.getCredentialType().getValue())
        || !Arrays.equals(current.getPublicKey(), record.getPublicKey().getBytes())
        || current.isUvInitialized() != record.isUvInitialized()
        || current.isBackupEligible() != record.isBackupEligible()
        || !java.util.Objects.equals(current.getTransports(), transports(record.getTransports()))
        || !Arrays.equals(current.getAttestationObject(), record.getAttestationObject().getBytes())
        || !Arrays.equals(current.getAttestationClientDataJson(),
            record.getAttestationClientDataJSON().getBytes())
        || !current.getLabel().equals(record.getLabel())) {
      throw new IllegalStateException("WebAuthn credential immutable material changed");
    }
    Instant lastUsed = record.getLastUsed();
    if (record.getSignatureCount() != current.getSignatureCount()
        || record.isBackupState() != current.isBackupState()
        || lastUsed != null) {
      current.recordUse(
          record.getSignatureCount(),
          record.isBackupState(),
          lastUsed == null ? clock.instant() : lastUsed);
    }
  }

  private static boolean isUsable(PasskeyCredentialEntity credential) {
    return credential.getStatus() == PasskeyCredentialStatusEnum.ACTIVE
        && credential.getPasskeyUser().getUser().getStatus() == UserStatusEnum.ACTIVE;
  }

  private static CredentialRecord view(PasskeyCredentialEntity credential) {
    ImmutableCredentialRecord.ImmutableCredentialRecordBuilder builder =
        ImmutableCredentialRecord.builder()
            .credentialType(PublicKeyCredentialType.valueOf(credential.getCredentialType()))
            .credentialId(new Bytes(credential.getCredentialId()))
            .userEntityUserId(new Bytes(credential.getPasskeyUser().getUserHandle()))
            .publicKey(new ImmutablePublicKeyCose(credential.getPublicKey()))
            .signatureCount(credential.getSignatureCount())
            .uvInitialized(credential.isUvInitialized())
            .transports(parseTransports(credential.getTransports()))
            .backupEligible(credential.isBackupEligible())
            .backupState(credential.isBackupState())
            .attestationObject(new Bytes(credential.getAttestationObject()))
            .attestationClientDataJSON(new Bytes(credential.getAttestationClientDataJson()))
            .created(credential.getCreatedAt())
            .label(credential.getLabel());
    if (credential.getLastUsedAt() != null) {
      builder.lastUsed(credential.getLastUsedAt());
    }
    return builder.build();
  }

  private static String transports(Set<AuthenticatorTransport> values) {
    if (values == null || values.isEmpty()) {
      return null;
    }
    return values.stream()
        .map(AuthenticatorTransport::getValue)
        .sorted()
        .collect(Collectors.joining(","));
  }

  private static Set<AuthenticatorTransport> parseTransports(String values) {
    if (values == null || values.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(values.split(","))
        .map(AuthenticatorTransport::valueOf)
        .sorted(Comparator.comparing(AuthenticatorTransport::getValue))
        .collect(Collectors.toUnmodifiableSet());
  }
}
