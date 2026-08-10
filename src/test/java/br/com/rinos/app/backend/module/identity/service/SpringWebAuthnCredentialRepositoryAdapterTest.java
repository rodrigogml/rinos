package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
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
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.PasskeyCredentialRepository;
import br.com.rinos.app.backend.module.identity.repository.PasskeyUserRepository;

@DisplayName("Adapter Spring WebAuthn das credentials globais")
class SpringWebAuthnCredentialRepositoryAdapterTest {

  private static final Instant CREATED_AT = Instant.parse("2026-08-10T12:00:00Z");
  private static final Instant USED_AT = CREATED_AT.plusSeconds(30);
  private PasskeyUserRepository owners;
  private PasskeyCredentialRepository credentials;
  private SpringWebAuthnCredentialRepositoryAdapter adapter;
  private PasskeyUserEntity owner;

  @BeforeEach
  void setUp() {
    owners = mock(PasskeyUserRepository.class);
    credentials = mock(PasskeyCredentialRepository.class);
    adapter = new SpringWebAuthnCredentialRepositoryAdapter(
        owners,
        credentials,
        new IdentityReferenceService(),
        Clock.fixed(USED_AT, ZoneOffset.UTC));
    UserEntity user = new UserEntity(
        "person@example.test", "person@example.test", UserStatusEnum.ACTIVE);
    ReflectionTestUtils.setField(user, "id", 41L);
    owner = new PasskeyUserEntity(user, bytes(32, (byte) 2));
    ReflectionTestUtils.setField(owner, "id", 51L);
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

    adapter.save(record);

    ArgumentCaptor<PasskeyCredentialEntity> saved =
        ArgumentCaptor.forClass(PasskeyCredentialEntity.class);
    verify(credentials).saveAndFlush(saved.capture());
    PasskeyCredentialEntity value = saved.getValue();
    assertThat(value.getPasskeyUser()).isSameAs(owner);
    assertThat(value.getCredentialId()).containsExactly(record.getCredentialId().getBytes());
    assertThat(value.getPublicKey()).containsExactly(record.getPublicKey().getBytes());
    assertThat(value.getTransports()).isEqualTo("hybrid,internal,smart-card");
    assertThat(value.getAttestationObject())
        .containsExactly(record.getAttestationObject().getBytes());
    assertThat(value.getAttestationClientDataJson())
        .containsExactly(record.getAttestationClientDataJSON().getBytes());
    assertThat(value.getLastUsedAt()).isEqualTo(USED_AT);
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
        .hasMessage("WebAuthn credential immutable material changed");
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
}
