package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.backend.module.identity.entity.PasskeyCredentialEntity;
import br.com.rinos.app.backend.module.identity.entity.PasskeyUserEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.PasskeyCredentialStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.PasskeyRiskReasonEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.ReauthenticationOperationEnum;
import br.com.rinos.app.backend.module.identity.repository.PasskeyCredentialRepository;
import br.com.rinos.app.backend.module.identity.repository.PasskeyUserRepository;
import br.com.rinos.app.backend.module.identity.vo.PasskeyCredentialRegistrationVO;
import br.com.rinos.app.backend.module.identity.vo.PasskeyCredentialSummaryVO;
import br.eng.rodrigogml.rfw.authentication.principal.RFWAuthenticationSessionPrincipal;

@DisplayName("Adapter Spring WebAuthn das credentials globais")
class SpringWebAuthnCredentialRepositoryAdapterTest {

  private static final Instant CREATED_AT = Instant.parse("2026-08-10T12:00:00Z");
  private static final Instant USED_AT = CREATED_AT.plusSeconds(30);
  private PasskeyUserRepository owners;
  private PasskeyCredentialRepository credentials;
  private PasskeyCredentialService passkeyService;
  private ReauthenticationService reauthenticationService;
  private PasskeyRiskAuditService riskAuditService;
  private SpringWebAuthnCredentialRepositoryAdapter adapter;
  private PasskeyUserEntity owner;

  @BeforeEach
  void setUp() {
    owners = mock(PasskeyUserRepository.class);
    credentials = mock(PasskeyCredentialRepository.class);
    passkeyService = mock(PasskeyCredentialService.class);
    reauthenticationService = mock(ReauthenticationService.class);
    riskAuditService = mock(PasskeyRiskAuditService.class);
    adapter = new SpringWebAuthnCredentialRepositoryAdapter(
        owners,
        credentials,
        passkeyService,
        reauthenticationService,
        riskAuditService,
        Clock.fixed(USED_AT, ZoneOffset.UTC));
    UserEntity user = new UserEntity(
        "person@example.test", "person@example.test", UserStatusEnum.ACTIVE);
    ReflectionTestUtils.setField(user, "id", 41L);
    owner = new PasskeyUserEntity(user, bytes(32, (byte) 2));
    ReflectionTestUtils.setField(owner, "id", 51L);
    UUID sessionReference = UUID.fromString("58a06f7d-c288-45fb-ab2f-7773a4abac14");
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            new SessionPrincipal(sessionReference.toString()), null, List.of()));
    when(reauthenticationService.isRecentlyAuthorized(
        41L,
        sessionReference,
        ReauthenticationOperationEnum.REGISTER_PASSKEY,
        USED_AT)).thenReturn(true);
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void findByCredentialId_shouldRoundTripEveryPersistedProtocolField() {
    PasskeyCredentialEntity entity = entity();
    when(credentials.findByCredentialId(entity.getCredentialId()))
        .thenReturn(Optional.of(entity));

    CredentialRecord result = adapter.findByCredentialId(new Bytes(entity.getCredentialId()));

    assertThat(result.getCredentialType()).isEqualTo(PublicKeyCredentialType.PUBLIC_KEY);
    assertThat(result.getCredentialId().getBytes()).containsExactly(entity.getCredentialId());
    assertThat(result.getUserEntityUserId().getBytes()).containsExactly(owner.getUserHandle());
    assertThat(result.getPublicKey().getBytes()).containsExactly(entity.getPublicKey());
    assertThat(result.getSignatureCount()).isEqualTo(7L);
    assertThat(result.isUvInitialized()).isTrue();
    assertThat(result.isBackupEligible()).isTrue();
    assertThat(result.isBackupState()).isFalse();
    assertThat(result.getTransports()).containsExactlyInAnyOrder(
        AuthenticatorTransport.HYBRID,
        AuthenticatorTransport.INTERNAL,
        AuthenticatorTransport.SMART_CARD);
    assertThat(result.getAttestationObject().getBytes())
        .containsExactly(entity.getAttestationObject());
    assertThat(result.getAttestationClientDataJSON().getBytes())
        .containsExactly(entity.getAttestationClientDataJson());
    assertThat(result.getCreated()).isEqualTo(CREATED_AT);
    assertThat(result.getLabel()).isEqualTo("Notebook");
  }

  @Test
  void findByUserId_shouldReturnOnlyActiveCredentialsFromStableHandleQuery() {
    PasskeyCredentialEntity entity = entity();
    when(credentials.findByPasskeyUserUserHandleAndStatusOrderById(
        owner.getUserHandle(), PasskeyCredentialStatusEnum.ACTIVE))
        .thenReturn(java.util.List.of(entity));

    java.util.List<CredentialRecord> result = adapter.findByUserId(
        new Bytes(owner.getUserHandle()));

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getCredentialId().getBytes())
        .containsExactly(entity.getCredentialId());
  }

  @Test
  void save_shouldPersistNewValidatedRecordWithoutDroppingFields() {
    CredentialRecord record = record(7L, false, USED_AT, bytes(4, (byte) 4));
    when(credentials.findByCredentialIdForUpdate(record.getCredentialId().getBytes()))
        .thenReturn(Optional.empty());
    when(owners.findByUserHandleForUpdate(record.getUserEntityUserId().getBytes()))
        .thenReturn(Optional.of(owner));
    UUID reference = UUID.fromString("59bece81-3eca-47ac-8472-35164cff502a");
    when(passkeyService.register(eq(41L), any(), any(), any(), eq(USED_AT)))
        .thenReturn(new PasskeyCredentialSummaryVO(
            reference, "Notebook", PasskeyCredentialStatusEnum.ACTIVE, CREATED_AT, null));

    adapter.save(record);

    ArgumentCaptor<PasskeyCredentialRegistrationVO> material =
        ArgumentCaptor.forClass(PasskeyCredentialRegistrationVO.class);
    verify(passkeyService).register(eq(41L), any(), material.capture(), any(), eq(USED_AT));
    assertThat(material.getValue().credentialId())
        .containsExactly(record.getCredentialId().getBytes());
    assertThat(material.getValue().publicKey()).containsExactly(record.getPublicKey().getBytes());
    assertThat(material.getValue().transports()).isEqualTo("hybrid,internal,smart-card");
    assertThat(material.getValue().attestationObject())
        .containsExactly(record.getAttestationObject().getBytes());
    assertThat(material.getValue().attestationClientDataJson())
        .containsExactly(record.getAttestationClientDataJSON().getBytes());
    verify(passkeyService).recordUse(41L, reference, 7L, false, USED_AT);
  }

  @Test
  void save_shouldRejectNewCredential_whenRecentAuthenticationIsMissing() {
    CredentialRecord record = record(7L, false, null, bytes(4, (byte) 4));
    when(credentials.findByCredentialIdForUpdate(record.getCredentialId().getBytes()))
        .thenReturn(Optional.empty());
    when(owners.findByUserHandleForUpdate(record.getUserEntityUserId().getBytes()))
        .thenReturn(Optional.of(owner));
    when(reauthenticationService.isRecentlyAuthorized(
        any(), any(), any(), any())).thenReturn(false);

    assertThatThrownBy(() -> adapter.save(record))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("Recent authentication");
    verify(passkeyService, never())
        .register(any(), any(), any(), any(), any());
  }

  @Test
  void save_shouldUpdateOnlyMutableAssertionState() {
    PasskeyCredentialEntity current = entity();
    CredentialRecord assertion = record(8L, true, USED_AT, current.getPublicKey());
    when(credentials.findByCredentialIdForUpdate(current.getCredentialId()))
        .thenReturn(Optional.of(current));

    adapter.save(assertion);

    assertThat(current.getSignatureCount()).isEqualTo(8L);
    assertThat(current.isBackupState()).isTrue();
    assertThat(current.getLastUsedAt()).isEqualTo(USED_AT);
  }

  @Test
  void save_shouldRejectReplacementOfImmutablePublicKey() {
    PasskeyCredentialEntity current = entity();
    when(credentials.findByCredentialIdForUpdate(current.getCredentialId()))
        .thenReturn(Optional.of(current));
    CredentialRecord replacement = record(8L, true, USED_AT, bytes(4, (byte) 9));

    assertThatThrownBy(() -> adapter.save(replacement))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("WebAuthn credential assertion was rejected");
    verify(riskAuditService).record(
        eq(41L),
        eq(PasskeyRiskReasonEnum.IMMUTABLE_MATERIAL_MISMATCH),
        any(),
        eq(USED_AT));
    assertThat(current.getStatus()).isEqualTo(PasskeyCredentialStatusEnum.ACTIVE);
  }

  @Test
  void save_shouldAuditCounterReplay_withoutRevokingIndependentCredentials() {
    PasskeyCredentialEntity current = entity();
    when(credentials.findByCredentialIdForUpdate(current.getCredentialId()))
        .thenReturn(Optional.of(current));
    CredentialRecord replay = record(7L, false, USED_AT, current.getPublicKey());

    assertThatThrownBy(() -> adapter.save(replay))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("WebAuthn credential assertion was rejected");

    verify(riskAuditService).record(
        eq(41L),
        eq(PasskeyRiskReasonEnum.SIGNATURE_COUNTER_REGRESSION),
        any(),
        eq(USED_AT));
    assertThat(current.getStatus()).isEqualTo(PasskeyCredentialStatusEnum.ACTIVE);
    assertThat(current.getSignatureCount()).isEqualTo(7L);
    verify(credentials, never()).delete(any());
  }

  @Test
  void save_shouldAuditUseOfRevokedCredential_withoutChangingItsState() {
    PasskeyCredentialEntity revoked = entity();
    revoked.revoke(USED_AT);
    when(credentials.findByCredentialIdForUpdate(revoked.getCredentialId()))
        .thenReturn(Optional.of(revoked));
    CredentialRecord assertion = record(8L, false, USED_AT, revoked.getPublicKey());

    assertThatThrownBy(() -> adapter.save(assertion))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("WebAuthn credential assertion was rejected");

    verify(riskAuditService).record(
        eq(41L),
        eq(PasskeyRiskReasonEnum.CREDENTIAL_NOT_USABLE),
        any(),
        eq(USED_AT));
    assertThat(revoked.getStatus()).isEqualTo(PasskeyCredentialStatusEnum.REVOKED);
    assertThat(revoked.getSignatureCount()).isEqualTo(7L);
  }

  @Test
  void findAndDelete_shouldNeverExposeOrPhysicallyDeleteRevokedCredential() {
    PasskeyCredentialEntity revoked = entity();
    revoked.revoke(USED_AT);
    when(credentials.findByCredentialId(revoked.getCredentialId()))
        .thenReturn(Optional.of(revoked));

    assertThat(adapter.findByCredentialId(new Bytes(revoked.getCredentialId()))).isNull();
    assertThatThrownBy(() -> adapter.delete(new Bytes(revoked.getCredentialId())))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("passkey management");
    assertThat(revoked.getStatus()).isEqualTo(PasskeyCredentialStatusEnum.REVOKED);
  }

  private PasskeyCredentialEntity entity() {
    PasskeyCredentialEntity result = new PasskeyCredentialEntity(
        owner,
        UUID.fromString("59bece81-3eca-47ac-8472-35164cff502a"),
        "public-key",
        bytes(8, (byte) 3),
        bytes(4, (byte) 4),
        7L,
        true,
        true,
        false,
        "hybrid,internal,smart-card",
        bytes(5, (byte) 5),
        bytes(6, (byte) 6),
        "Notebook");
    ReflectionTestUtils.setField(result, "id", 61L);
    ReflectionTestUtils.setField(result, "createdAt", CREATED_AT);
    return result;
  }

  private CredentialRecord record(
      long signatureCount,
      boolean backupState,
      Instant lastUsed,
      byte[] publicKey) {
    ImmutableCredentialRecord.ImmutableCredentialRecordBuilder builder =
        ImmutableCredentialRecord.builder()
            .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
            .credentialId(new Bytes(bytes(8, (byte) 3)))
            .userEntityUserId(new Bytes(owner.getUserHandle()))
            .publicKey(new ImmutablePublicKeyCose(publicKey))
            .signatureCount(signatureCount)
            .uvInitialized(true)
            .transports(Set.of(
                AuthenticatorTransport.INTERNAL,
                AuthenticatorTransport.HYBRID,
                AuthenticatorTransport.SMART_CARD))
            .backupEligible(true)
            .backupState(backupState)
            .attestationObject(new Bytes(bytes(5, (byte) 5)))
            .attestationClientDataJSON(new Bytes(bytes(6, (byte) 6)))
            .created(CREATED_AT)
            .label("Notebook");
    if (lastUsed != null) {
      builder.lastUsed(lastUsed);
    }
    return builder.build();
  }

  private static byte[] bytes(int length, byte value) {
    byte[] result = new byte[length];
    java.util.Arrays.fill(result, value);
    return result;
  }

  private record SessionPrincipal(String sessionReference)
      implements RFWAuthenticationSessionPrincipal {
  }
}
